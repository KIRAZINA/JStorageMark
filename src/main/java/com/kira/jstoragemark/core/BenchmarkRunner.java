package com.kira.jstoragemark.core;

import com.kira.jstoragemark.config.BenchmarkConfig;
import com.kira.jstoragemark.fs.BenchmarkPaths;
import com.kira.jstoragemark.report.SystemInfoSnapshot;
import com.kira.jstoragemark.result.BenchmarkResult;
import com.kira.jstoragemark.metrics.MetricsSnapshot;
import com.kira.jstoragemark.metrics.IMetricsCollector;
import com.kira.jstoragemark.metrics.MetricsCollector;
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
            for (BenchmarkConfig.TestType type : config.getTestTypes()) {
                for (int i = 0; i < config.getIterations(); i++) {
                    List<BenchmarkResult> iterationResults = runMultiThreaded(runId++, type);
                    results.addAll(iterationResults);
                }
            }
            return Collections.unmodifiableList(results);
        } finally {
            shutdownExecutors();
            if (!config.isRetainTestFiles()) {
                paths.cleanupSessionFiles(false);
            }
        }
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

        ByteBuffer buffer = bufferPool.get();
        Random rng = getThreadRng(threadId);
        Instant start = Instant.now();
        long totalOps = 0;
        long bytesProcessed = 0;
        long fileSize = config.getFileSizeBytes();

        try (FileChannel channel = FileChannel.open(file,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE)) {

            if (config.isPreallocateFiles() &&
                (type == BenchmarkConfig.TestType.SEQ_WRITE || type == BenchmarkConfig.TestType.RAND_WRITE)) {
                channel.truncate(fileSize);
            }

            while (bytesProcessed < fileSize) {
                queueSemaphore.acquireUninterruptibly();
                try {
                    int bytesToProcess = (int) Math.min(config.getBlockSizeBytes(), fileSize - bytesProcessed);

                    buffer.clear();
                    buffer.limit(bytesToProcess);

                    if (type == BenchmarkConfig.TestType.SEQ_WRITE || type == BenchmarkConfig.TestType.RAND_WRITE) {
                        byte[] temp = new byte[bytesToProcess];
                        rng.nextBytes(temp);
                        buffer.put(temp);
                        buffer.flip();
                        int written = channel.write(buffer);
                        bytesProcessed += written;
                    } else {
                        int read = channel.read(buffer);
                        if (read == -1) {
                            break;
                        }
                        bytesProcessed += read;
                    }

                    if (type == BenchmarkConfig.TestType.RAND_READ || type == BenchmarkConfig.TestType.RAND_WRITE) {
                        long maxPos = Math.max(0, fileSize - bytesToProcess);
                        long pos = ThreadLocalRandom.current().nextLong(0, maxPos + 1);
                        channel.position(pos);
                    }

                    totalOps++;

                    if (config.isForceSync() && config.getSyncEveryNBlocks() > 0 &&
                        totalOps % config.getSyncEveryNBlocks() == 0) {
                        channel.force(false);
                    }
                } finally {
                    queueSemaphore.release();
                }
            }

            if (config.isForceSync() && config.getSyncEveryNBlocks() == 0) {
                channel.force(true);
            }
        }

        Instant end = Instant.now();
        return calculateResult(runId, threadId, type, fileSize, start, end, totalOps);
    }

    private BenchmarkResult runSingleThreadAsync(Path file, int runId, BenchmarkConfig.TestType type, int threadId)
            throws IOException {

        ByteBuffer buffer = bufferPool.get();
        Random rng = getThreadRng(threadId);
        long fileSize = config.getFileSizeBytes();
        long bytesProcessed = 0;
        long totalOps = 0;
        boolean isWrite = (type == BenchmarkConfig.TestType.SEQ_WRITE || type == BenchmarkConfig.TestType.RAND_WRITE);

        try (AsynchronousFileChannel channel = AsynchronousFileChannel.open(file,
                StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {

            if (config.isPreallocateFiles() && isWrite) {
                try (FileChannel fc = FileChannel.open(file,
                        StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                    fc.truncate(fileSize);
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

                if (isWrite) {
                    byte[] temp = new byte[bytesToProcess];
                    rng.nextBytes(temp);
                    buffer.put(temp);
                    buffer.flip();

                    channel.write(buffer, channel.size(), null, new CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(Integer written, Void attachment) {
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
                totalOps++;

                if (isWrite && type == BenchmarkConfig.TestType.RAND_WRITE) {
                    long maxPos = Math.max(0, fileSize - bytesToProcess);
                    long pos = ThreadLocalRandom.current().nextLong(0, maxPos + 1);
                }

                queueSemaphore.release();

                if (resultHolder[0] == 0) {
                    break;
                }
            }

            Instant end = Instant.now();
            return calculateResult(runId, threadId, type, bytesProcessed, start, end, totalOps);
        }
    }

    private BenchmarkResult calculateResult(int runId, int threadId, BenchmarkConfig.TestType type,
            long bytesProcessed, Instant start, Instant end, long totalOps) {

        Duration elapsed = Duration.between(start, end);
        long elapsedNanos = elapsed.toNanos();
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;

        double throughputMBps = (bytesProcessed / (1024.0 * 1024.0)) / elapsedSeconds;
        double avgLatencyMs = (elapsedNanos / 1_000_000.0) / totalOps;
        double avgLatencyNs = (double) elapsedNanos / totalOps;
        double iops = totalOps / elapsedSeconds;

        String rid = String.format("run-%03d-thread-%02d", runId, threadId);
        BenchmarkResult result = new BenchmarkResult(rid, type.name(), bytesProcessed, elapsed,
                elapsedNanos, throughputMBps, avgLatencyMs, avgLatencyNs, iops, end);

        logger.debug("Run {} completed: type={}, throughput={:.2f} MB/s, latency={:.2f} ms",
                rid, type, throughputMBps, avgLatencyMs);
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
