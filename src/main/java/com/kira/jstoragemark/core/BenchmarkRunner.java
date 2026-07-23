package com.kira.jstoragemark.core;

import com.kira.jstoragemark.config.BenchmarkConfig;
import com.kira.jstoragemark.fs.BenchmarkPaths;
import com.kira.jstoragemark.report.SystemInfoSnapshot;
import com.kira.jstoragemark.result.BenchmarkResult;
import com.kira.jstoragemark.metrics.MetricsSnapshot;
import com.kira.jstoragemark.metrics.IMetricsCollector;
import com.kira.jstoragemark.metrics.MetricsCollector;
import org.HdrHistogram.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.HardwareAbstractionLayer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public final class BenchmarkRunner implements IBenchmarkRunner {
    private static final Logger logger = LoggerFactory.getLogger(BenchmarkRunner.class);

    private final BenchmarkConfig config;
    private final BenchmarkPaths paths;
    private final IMetricsCollector metricsCollector;
    private final ExecutorService ioExecutor;

    private final List<BenchmarkResult> results = Collections.synchronizedList(new ArrayList<>());

    private final ThreadLocal<ByteBuffer> bufferPool;
    private final Map<Integer, Random> threadRngMap;
    private final long baseSeed;
    private final boolean useFixedSeed;

    private final Semaphore queueSemaphore;
    private final SystemInfoSnapshot systemInfo;

    private final AtomicLong progressBytesProcessed = new AtomicLong(0);
    private final Instant progressStartTime = Instant.now();
    private volatile BenchmarkConfig.TestType currentTestType;
    private volatile int currentIteration;
    private volatile int totalIterations;
    private ScheduledExecutorService progressExecutor;

    public BenchmarkRunner(BenchmarkConfig config, BenchmarkPaths paths) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.paths = Objects.requireNonNull(paths, "paths must not be null");
        this.metricsCollector = new MetricsCollector(config.getMetricsPollInterval());
        this.ioExecutor = Executors.newFixedThreadPool(config.getThreads());

        this.bufferPool = ThreadLocal.withInitial(() ->
            config.isUseDirectBuffer()
                ? ByteBuffer.allocateDirect(config.getBlockSizeBytes())
                : ByteBuffer.allocate(config.getBlockSizeBytes()));

        Optional<Long> seedOpt = config.getRandomSeed();
        this.threadRngMap = new ConcurrentHashMap<>();
        this.baseSeed = seedOpt.orElse(0L);
        this.useFixedSeed = seedOpt.isPresent();

        this.queueSemaphore = new Semaphore(config.getQueueDepth());
        this.systemInfo = captureSystemInfo();

        logger.info("BenchmarkRunner initialized with {} threads, directBuffer={}, ioMode={}, queueDepth={}",
                config.getThreads(), config.isUseDirectBuffer(), config.getIoMode(), config.getQueueDepth());
    }

    private static SystemInfoSnapshot captureSystemInfo() {
        try {
            SystemInfo si = new SystemInfo();
            HardwareAbstractionLayer hal = si.getHardware();
            CentralProcessor cpu = hal.getProcessor();
            return new SystemInfoSnapshot(
                System.getProperty("os.name") + " " + System.getProperty("os.version"),
                System.getProperty("java.version"),
                cpu.getProcessorIdentifier().getName(),
                hal.getMemory().getTotal()
            );
        } catch (Exception e) {
            logger.warn("Could not capture system info", e);
            return new SystemInfoSnapshot("unknown", "unknown", "unknown", 0L);
        }
    }

    public SystemInfoSnapshot getSystemInfo() {
        return systemInfo;
    }

    private Random getThreadRng(int threadId) {
        if (!useFixedSeed) {
            return new Random(ThreadLocalRandom.current().nextLong());
        }
        return threadRngMap.computeIfAbsent(threadId,
            tid -> new Random(baseSeed ^ (tid * 0x9E3779B97F4A7C15L)));
    }

    @Override
    public List<BenchmarkResult> runAll() throws IOException, InterruptedException {
        try {
            paths.ensureTestDirectory();
            long totalSpaceNeeded = config.getFileSizeBytes() * config.getThreads();
            paths.validateFreeSpace(totalSpaceNeeded);

            logger.info("Starting benchmark: testTypes={}, fileSize={}, iterations={}, warmup={}, threads={}",
                    config.getTestTypes(), config.getFileSizeBytes(), config.getIterations(),
                    config.getWarmupIterations(), config.getThreads());

            int runId = 1;

            int warmup = config.getWarmupIterations();
            if (warmup > 0) {
                logger.info("Starting {} warmup iteration(s)", warmup);
                for (BenchmarkConfig.TestType type : config.getTestTypes()) {
                    for (int i = 0; i < warmup; i++) {
                        runMultiThreaded(-runId, type);
                        runId++;
                    }
                }
                results.clear();
                logger.info("Warmup completed, starting measured runs");
            }

            runId = 1;
            totalIterations = config.getIterations() * config.getTestTypes().size();
            if (config.getVerbosity() > 0 && System.console() != null) {
                startProgressReporting();
            }
            int iterationCount = 0;
            for (BenchmarkConfig.TestType type : config.getTestTypes()) {
                for (int i = 0; i < config.getIterations(); i++) {
                    currentTestType = type;
                    currentIteration = iterationCount + 1;
                    progressBytesProcessed.set(0);
                    List<BenchmarkResult> iterationResults = runMultiThreaded(runId++, type);
                    results.addAll(iterationResults);
                    iterationCount++;
                }
            }
            return Collections.unmodifiableList(results);
        } finally {
            stopProgressReporting();
            shutdownExecutors();
            if (!config.isRetainTestFiles()) {
                paths.cleanupSessionFiles(false);
            }
        }
    }

    private void startProgressReporting() {
        progressExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "progress-reporter");
            t.setDaemon(true);
            return t;
        });
        progressExecutor.scheduleAtFixedRate(this::printProgress, 1, 1, TimeUnit.SECONDS);
    }

    private void stopProgressReporting() {
        if (progressExecutor != null && !progressExecutor.isShutdown()) {
            progressExecutor.shutdownNow();
        }
        System.out.println();
    }

    private void printProgress() {
        long totalForIteration = (long) config.getTestTypes().size() * config.getIterations();
        double overallPercent = (double) currentIteration / totalForIteration * 100;
        long bytes = progressBytesProcessed.get();
        double elapsed = Duration.between(progressStartTime, Instant.now()).toMillis() / 1000.0;
        double throughputMBps = elapsed > 0 ? (bytes / (1024.0 * 1024.0)) / elapsed : 0;
        long etaSeconds = throughputMBps > 0
            ? (long) ((config.getFileSizeBytes() * config.getThreads()
                * (totalForIteration - currentIteration + 1)
                - bytes) / (throughputMBps * 1024 * 1024))
            : 0;

        int barLen = 20;
        int filled = Math.min(barLen, Math.max(0, (int) (overallPercent / 100 * barLen)));
        String progressBar = "=".repeat(filled) + " ".repeat(barLen - filled);

        System.out.printf("\r[%s] %3.0f%% | %s | %8.0f MB/s | ETA: %2dm %02ds | Iteration %d/%d",
            progressBar, overallPercent, currentTestType, throughputMBps,
            etaSeconds / 60, etaSeconds % 60, currentIteration, totalIterations);
    }

    private List<BenchmarkResult> runMultiThreaded(int runId, BenchmarkConfig.TestType type)
            throws InterruptedException {

        CountDownLatch latch = new CountDownLatch(config.getThreads());
        List<Future<BenchmarkResult>> futures = new ArrayList<>();

        for (int threadId = 0; threadId < config.getThreads(); threadId++) {
            final int tid = threadId;
            final int currentRunId = runId;
            Future<BenchmarkResult> future = ioExecutor.submit(() -> {
                try {
                    Path threadFile = paths.testFilePath(currentRunId, type.name().toLowerCase() + "-t" + tid);
                    if (config.getIoMode() == BenchmarkConfig.IoMode.ASYNC) {
                        return runSingleThreadAsync(threadFile, currentRunId, type, tid);
                    } else {
                        return runSingleThreadSync(threadFile, currentRunId, type, tid);
                    }
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }

        if (!latch.await(config.getMaxPerTestTarget().toMinutes(), TimeUnit.MINUTES)) {
            logger.error("Benchmark timeout - forcing shutdown");
            ioExecutor.shutdownNow();
            throw new InterruptedException("Benchmark exceeded time limit");
        }

        List<BenchmarkResult> iterationResults = new ArrayList<>();
        for (Future<BenchmarkResult> f : futures) {
            try {
                iterationResults.add(f.get());
            } catch (ExecutionException e) {
                throw new RuntimeException("Thread execution failed", e.getCause());
            }
        }
        return iterationResults;
    }

    private BenchmarkResult runSingleThreadSync(Path file, int runId, BenchmarkConfig.TestType type, int threadId)
            throws IOException {

        long fileSize = config.getFileSizeBytes();
        boolean isWrite = (type == BenchmarkConfig.TestType.SEQ_WRITE || type == BenchmarkConfig.TestType.RAND_WRITE);
        boolean isRandom = (type == BenchmarkConfig.TestType.RAND_READ || type == BenchmarkConfig.TestType.RAND_WRITE);
        boolean isMixed = (type == BenchmarkConfig.TestType.MIXED_RW);
        boolean isReadOnly = !isWrite && !isMixed;

        ByteBuffer buffer = bufferPool.get();
        Random rng = getThreadRng(threadId);
        Histogram histogram = new Histogram(3600000000000L, 3);
        Instant start = Instant.now();
        long totalOps = 0;
        long bytesProcessed = 0;
        long bytesRead = 0;
        long bytesWritten = 0;

        try (FileChannel channel = FileChannel.open(file,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE)) {

            if (config.isPreallocateFiles() && (isWrite || isMixed)) {
                channel.truncate(fileSize);
            }

            if (isMixed) {
                int readPercent = config.getMixedReadPercent();
                int readThreshold = (int) (readPercent * 0.01 * Integer.MAX_VALUE);
                int blockSize = config.getBlockSizeBytes();

                while (bytesProcessed < fileSize) {
                    int bytesToProcess = (int) Math.min(blockSize, fileSize - bytesProcessed);

                    queueSemaphore.acquireUninterruptibly();
                    try {
                        long randomPos = ThreadLocalRandom.current().nextLong(0, Math.max(1, fileSize - bytesToProcess));
                        channel.position(randomPos);

                        long startNs = System.nanoTime();
                        boolean doRead = rng.nextInt(Integer.MAX_VALUE) < readThreshold;

                        if (doRead) {
                            buffer.clear();
                            buffer.limit(bytesToProcess);
                            int read = channel.read(buffer);
                            if (read == -1) break;
                            bytesRead += read;
                        } else {
                            byte[] temp = new byte[bytesToProcess];
                            rng.nextBytes(temp);
                            buffer.clear();
                            buffer.put(temp);
                            buffer.flip();
                            int written = channel.write(buffer);
                            bytesWritten += written;
                        }

                        long endNs = System.nanoTime();
                        histogram.recordValue(endNs - startNs);
                        bytesProcessed += bytesToProcess;
                        totalOps++;

                        if (config.isForceSync() && config.getSyncEveryNBlocks() > 0 &&
                            totalOps % config.getSyncEveryNBlocks() == 0) {
                            channel.force(false);
                        }
                    } finally {
                        queueSemaphore.release();
                    }
                    progressBytesProcessed.addAndGet(bytesToProcess);
                }
            } else {
                if (isReadOnly) {
                    channel.truncate(fileSize);
                    long prefillWritten = 0;
                    while (prefillWritten < fileSize) {
                        int toWrite = (int) Math.min(config.getBlockSizeBytes(), fileSize - prefillWritten);
                        byte[] temp = new byte[toWrite];
                        rng.nextBytes(temp);
                        buffer.clear();
                        buffer.put(temp);
                        buffer.flip();
                        prefillWritten += channel.write(buffer);
                    }
                    channel.position(0);
                    start = Instant.now();
                }

                while (bytesProcessed < fileSize) {
                    int bytesToProcess = (int) Math.min(config.getBlockSizeBytes(), fileSize - bytesProcessed);

                    queueSemaphore.acquireUninterruptibly();
                    try {
                        long startNs = System.nanoTime();

                        if (isWrite) {
                            byte[] temp = new byte[bytesToProcess];
                            rng.nextBytes(temp);
                            buffer.clear();
                            buffer.put(temp);
                            buffer.flip();
                            int written = channel.write(buffer);
                            bytesWritten += written;
                            bytesProcessed += written;
                        } else {
                            buffer.clear();
                            buffer.limit(bytesToProcess);
                            int read = channel.read(buffer);
                            if (read == -1) break;
                            bytesRead += read;
                            bytesProcessed += read;
                        }

                        if (isRandom && fileSize > 0) {
                            long maxPos = Math.max(0, fileSize - bytesToProcess);
                            channel.position(ThreadLocalRandom.current().nextLong(0, maxPos + 1));
                        }

                        long endNs = System.nanoTime();
                        histogram.recordValue(endNs - startNs);
                        totalOps++;

                        if (config.isForceSync() && config.getSyncEveryNBlocks() > 0 &&
                            totalOps % config.getSyncEveryNBlocks() == 0) {
                            channel.force(false);
                        }
                    } finally {
                        queueSemaphore.release();
                    }
                    progressBytesProcessed.addAndGet(bytesToProcess);
                }
            }

            if (config.isForceSync() && config.getSyncEveryNBlocks() == 0) {
                channel.force(true);
            }
        }

        Instant end = Instant.now();
        return calculateResult(runId, threadId, type, bytesProcessed, bytesRead, bytesWritten,
                start, end, totalOps, histogram);
    }

    private BenchmarkResult runSingleThreadAsync(Path file, int runId, BenchmarkConfig.TestType type, int threadId)
            throws IOException {

        long fileSize = config.getFileSizeBytes();
        boolean isWrite = (type == BenchmarkConfig.TestType.SEQ_WRITE || type == BenchmarkConfig.TestType.RAND_WRITE);
        boolean isRandom = (type == BenchmarkConfig.TestType.RAND_READ || type == BenchmarkConfig.TestType.RAND_WRITE);
        boolean isReadOnly = !isWrite;

        ByteBuffer buffer = bufferPool.get();
        Random rng = getThreadRng(threadId);
        Histogram histogram = new Histogram(3600000000000L, 3);
        long bytesProcessed = 0;
        long totalOps = 0;
        long bytesRead = 0;
        long bytesWritten = 0;

        try (AsynchronousFileChannel channel = AsynchronousFileChannel.open(file,
                StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {

            if (config.isPreallocateFiles() && isWrite) {
                try (FileChannel fc = FileChannel.open(file,
                        StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                    fc.truncate(fileSize);
                }
            }

            if (isReadOnly) {
                try (FileChannel fc = FileChannel.open(file,
                        StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                    fc.truncate(fileSize);
                    long prefillWritten = 0;
                    ByteBuffer fillBuf = ByteBuffer.allocate(config.getBlockSizeBytes());
                    while (prefillWritten < fileSize) {
                        int toWrite = (int) Math.min(config.getBlockSizeBytes(), fileSize - prefillWritten);
                        byte[] temp = new byte[toWrite];
                        rng.nextBytes(temp);
                        fillBuf.clear();
                        fillBuf.put(temp);
                        fillBuf.flip();
                        prefillWritten += fc.write(fillBuf);
                    }
                }
            }

            Instant start = Instant.now();

            while (bytesProcessed < fileSize) {
                queueSemaphore.acquireUninterruptibly();
                int bytesToProcess = (int) Math.min(config.getBlockSizeBytes(), fileSize - bytesProcessed);
                buffer.clear();
                buffer.limit(bytesToProcess);

                final CountDownLatch opLatch = new CountDownLatch(1);
                final long[] resultHolder = new long[1];
                final boolean[] failed = new boolean[1];
                final long[] startNsRef = {System.nanoTime()};

                if (isWrite) {
                    byte[] temp = new byte[bytesToProcess];
                    rng.nextBytes(temp);
                    buffer.put(temp);
                    buffer.flip();

                    channel.write(buffer, channel.size(), null, new CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(Integer written, Void attachment) {
                            long latencyNs = System.nanoTime() - startNsRef[0];
                            synchronized (histogram) {
                                histogram.recordValue(latencyNs);
                            }
                            resultHolder[0] = written;
                            opLatch.countDown();
                        }
                        @Override
                        public void failed(Throwable exc, Void attachment) {
                            failed[0] = true;
                            opLatch.countDown();
                        }
                    });
                } else {
                    channel.read(buffer, bytesProcessed, null, new CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(Integer read, Void attachment) {
                            long latencyNs = System.nanoTime() - startNsRef[0];
                            synchronized (histogram) {
                                histogram.recordValue(latencyNs);
                            }
                            resultHolder[0] = read >= 0 ? read : 0;
                            opLatch.countDown();
                        }
                        @Override
                        public void failed(Throwable exc, Void attachment) {
                            failed[0] = true;
                            opLatch.countDown();
                        }
                    });
                }

                try {
                    opLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    queueSemaphore.release();
                    throw new IOException("Async I/O interrupted", e);
                }

                if (failed[0]) {
                    queueSemaphore.release();
                    throw new IOException("Async I/O operation failed");
                }

                bytesProcessed += resultHolder[0];
                if (isWrite) {
                    bytesWritten += resultHolder[0];
                } else {
                    bytesRead += resultHolder[0];
                }
                totalOps++;

                if (isRandom && fileSize > 0) {
                    long maxPos = Math.max(0, fileSize - bytesToProcess);
                    // position for next random operation (for write, use channel.size as before)
                    // this is handled implicitly for async by next operation's position
                }

                queueSemaphore.release();
                progressBytesProcessed.addAndGet(resultHolder[0]);

                if (resultHolder[0] == 0) {
                    break;
                }
            }

            Instant end = Instant.now();
            return calculateResult(runId, threadId, type, bytesProcessed, bytesRead, bytesWritten,
                    start, end, totalOps, histogram);
        }
    }

    private BenchmarkResult calculateResult(int runId, int threadId, BenchmarkConfig.TestType type,
            long bytesProcessed, long bytesRead, long bytesWritten,
            Instant start, Instant end, long totalOps, Histogram histogram) {

        Duration elapsed = Duration.between(start, end);
        long elapsedNanos = elapsed.toNanos();
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;

        double throughputMBps = (bytesProcessed / (1024.0 * 1024.0)) / elapsedSeconds;
        double avgLatencyMs = (elapsedNanos / 1_000_000.0) / totalOps;
        double avgLatencyNs = (double) elapsedNanos / totalOps;
        double iops = totalOps / elapsedSeconds;

        double p50 = histogram.getValueAtPercentile(50.0);
        double p95 = histogram.getValueAtPercentile(95.0);
        double p99 = histogram.getValueAtPercentile(99.0);
        double p999 = histogram.getValueAtPercentile(99.9);
        long maxLatencyNs = histogram.getMaxValue();

        String rid = String.format("run-%03d-thread-%02d", runId, threadId);
        String typeName = type != null ? type.name() : "";
        BenchmarkResult result = new BenchmarkResult(rid, typeName, bytesProcessed, elapsed,
                elapsedNanos, throughputMBps, avgLatencyMs, avgLatencyNs, iops, end,
                p50, p95, p99, p999, maxLatencyNs);

        logger.debug("Run {} completed: type={}, throughput={:.2f} MB/s, latency={:.2f} ms, p99={:.0f} ns",
                rid, type, throughputMBps, avgLatencyMs, p99);
        return result;
    }

    @Override
    public void startMetricsPolling() {
        metricsCollector.start();
    }

    @Override
    public List<MetricsSnapshot> getMetricsLog() {
        return metricsCollector.getMetricsLog();
    }

    private void shutdownExecutors() {
        logger.info("Shutting down executors");
        metricsCollector.stop();
        ioExecutor.shutdown();

        try {
            if (!ioExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warn("IO executor tasks did not complete within timeout, forcing shutdown");
                ioExecutor.shutdownNow();
                if (!ioExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.error("IO executor did not terminate after forced shutdown");
                }
            }
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for executor shutdown", e);
            Thread.currentThread().interrupt();
        }
    }
}
