package com.kira.jstoragemark.core;

import com.kira.jstoragemark.config.BenchmarkConfig;
import com.kira.jstoragemark.fs.BenchmarkPaths;
import com.kira.jstoragemark.result.BenchmarkResult;
import com.kira.jstoragemark.metrics.MetricsSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Coordinates execution of benchmark workloads with true multithreading.
 * Responsibilities:
 *  - Prepare test files in dedicated directory.
 *  - Launch threads according to config (one thread per file for true concurrency).
 *  - Collect throughput, latency, IOPS metrics with nanosecond precision.
 *  - Poll system metrics at fixed intervals (using OSHI).
 *  - Aggregate results into BenchmarkResult objects.
 *
 * Notes:
 *  - Uses ExecutorService for true concurrent I/O operations.
 *  - ThreadLocalRandom for thread-safe random number generation.
 *  - Configurable sync strategy via forceSync and syncEveryNBlocks.
 *  - DirectByteBuffer support for improved performance.
 *  - Fixed ByteBuffer handling and unbiased random positioning.
 *  - OSHI provides real system metrics without external dependencies.
 */
public final class BenchmarkRunner {
    private static final Logger logger = LoggerFactory.getLogger(BenchmarkRunner.class);

    private final BenchmarkConfig config;
    private final BenchmarkPaths paths;
    private final ScheduledExecutorService metricsPoller;
    private final ExecutorService ioExecutor;
    private final Random random;  // Shared Random instance for reproducible seeds

    private final List<MetricsSnapshot> metricsLog = new CopyOnWriteArrayList<>();
    private final List<BenchmarkResult> results = Collections.synchronizedList(new ArrayList<>());
    
    // Thread-local buffer pool to avoid contention
    private final ThreadLocal<ByteBuffer> bufferPool;

    // OSHI components for real metrics
    private final SystemInfo systemInfo = new SystemInfo();
    private final HardwareAbstractionLayer hardware = systemInfo.getHardware();

    public BenchmarkRunner(BenchmarkConfig config, BenchmarkPaths paths) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.paths = Objects.requireNonNull(paths, "paths must not be null");
        this.metricsPoller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BenchmarkMetricsPoller");
            t.setDaemon(true);
            return t;
        });
        this.ioExecutor = Executors.newFixedThreadPool(config.getThreads());
        // Initialize single Random instance with optional seed for reproducibility
        this.random = config.getRandomSeed().isPresent()
                ? new Random(config.getRandomSeed().get())
                : null; // Use ThreadLocalRandom instead
                
        // Thread-local buffer pool for performance
        this.bufferPool = ThreadLocal.withInitial(() -> 
            config.isUseDirectBuffer() 
                ? ByteBuffer.allocateDirect(config.getBlockSizeBytes())
                : ByteBuffer.allocate(config.getBlockSizeBytes()));
        
        logger.info("BenchmarkRunner initialized with {} threads, directBuffer={}", 
                config.getThreads(), config.isUseDirectBuffer());
    }

    /**
     * Executes all configured test types with true multithreaded I/O.
     * Each iteration runs with multiple threads, each on its own file.
     */
    public List<BenchmarkResult> runAll() throws IOException, InterruptedException {
        try {
            paths.ensureTestDirectory();
            // Calculate total space needed: threads * fileSize per iteration
            long totalSpaceNeeded = config.getFileSizeBytes() * config.getThreads();
            paths.validateFreeSpace(totalSpaceNeeded);
            
            logger.info("Starting benchmark: testTypes={}, fileSize={}, iterations={}, threads={}",
                    config.getTestTypes(), config.getFileSizeBytes(), config.getIterations(), config.getThreads());

            int runId = 1;
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

    /**
     * Run a single iteration with multiple threads.
     * Each thread gets its own file to avoid position contention.
     */
    private List<BenchmarkResult> runMultiThreaded(int runId, BenchmarkConfig.TestType type) 
            throws InterruptedException {
        
        CountDownLatch latch = new CountDownLatch(config.getThreads());
        List<Future<BenchmarkResult>> futures = new ArrayList<>();
        
        for (int threadId = 0; threadId < config.getThreads(); threadId++) {
            final int tid = threadId;
            Future<BenchmarkResult> future = ioExecutor.submit(() -> {
                try {
                    // Each thread works on its own file
                    Path threadFile = paths.testFilePath(runId, type.name().toLowerCase() + "-t" + tid);
                    return runSingleThread(threadFile, runId, type, tid);
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }
        
        // Wait for all threads with timeout
        if (!latch.await(config.getMaxPerTestTarget().toMinutes(), TimeUnit.MINUTES)) {
            logger.error("Benchmark timeout - forcing shutdown");
            ioExecutor.shutdownNow();
            throw new InterruptedException("Benchmark exceeded time limit");
        }
        
        // Collect results
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

    /**
     * Single thread execution on its assigned file.
     * Fixed ByteBuffer handling and unbiased random positioning.
     */
    private BenchmarkResult runSingleThread(Path file, int runId, BenchmarkConfig.TestType type, int threadId) 
            throws IOException {
        
        ByteBuffer buffer = bufferPool.get();
        Instant start = Instant.now();
        long totalOps = 0;
        long bytesProcessed = 0;
        long fileSize = config.getFileSizeBytes();
        
        // Use ThreadLocalRandom for thread safety
        ThreadLocalRandom tlr = ThreadLocalRandom.current();
        // Use seeded Random if provided for reproducibility
        Random rng = (random != null) ? random : new Random(tlr.nextLong());

        try (FileChannel channel = FileChannel.open(file,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE)) {
            
            // Preallocate file for accurate sequential write performance
            if (config.isPreallocateFiles() && 
                (type == BenchmarkConfig.TestType.SEQ_WRITE || type == BenchmarkConfig.TestType.RAND_WRITE)) {
                channel.truncate(fileSize);
            }

            while (bytesProcessed < fileSize) {
                // Determine actual bytes to process (last block may be smaller)
                int bytesToProcess = (int) Math.min(config.getBlockSizeBytes(), fileSize - bytesProcessed);
                
                buffer.clear();  // Reset: position=0, limit=capacity
                buffer.limit(bytesToProcess);  // Adjust for last block

                if (type == BenchmarkConfig.TestType.SEQ_WRITE || type == BenchmarkConfig.TestType.RAND_WRITE) {
                    // FIXED: Proper buffer handling for write operations
                    byte[] temp = new byte[bytesToProcess];
                    rng.nextBytes(temp);
                    buffer.put(temp);
                    buffer.flip();  // Prepare for write: limit=position, position=0
                    int written = channel.write(buffer);
                    bytesProcessed += written;
                    
                } else {
                    // Read path
                    int read = channel.read(buffer);
                    if (read == -1) {
                        break; // EOF
                    }
                    bytesProcessed += read;
                }

                // FIXED: Unbiased random positioning for random I/O
                if (type == BenchmarkConfig.TestType.RAND_READ || type == BenchmarkConfig.TestType.RAND_WRITE) {
                    // Ensure we don't seek past EOF during write
                    long maxPos = Math.max(0, fileSize - bytesToProcess);
                    long pos = tlr.nextLong(0, maxPos + 1);  // Java 17+ unbiased method
                    channel.position(pos);
                }

                totalOps++;
                
                // Configurable sync strategy
                if (config.isForceSync() && config.getSyncEveryNBlocks() > 0 && 
                    totalOps % config.getSyncEveryNBlocks() == 0) {
                    channel.force(false); // Sync metadata only
                }
            }

            // Final sync if configured
            if (config.isForceSync() && config.getSyncEveryNBlocks() == 0) {
                channel.force(true); // Sync data and metadata at end
            }
        }

        Instant end = Instant.now();
        return calculateResult(runId * 1000 + threadId, type, fileSize, start, end, totalOps);
    }
    
    /**
     * Calculate benchmark result with nanosecond precision.
     */
    private BenchmarkResult calculateResult(int runId, BenchmarkConfig.TestType type,
            long bytesProcessed, Instant start, Instant end, long totalOps) {
        
        Duration elapsed = Duration.between(start, end);
        long elapsedNanos = elapsed.toNanos();  // High precision
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        
        // High precision calculations
        double throughputMBps = (bytesProcessed / (1024.0 * 1024.0)) / elapsedSeconds;
        double avgLatencyMs = (elapsedNanos / 1_000_000.0) / totalOps;  // Convert to ms
        double avgLatencyNs = (double) elapsedNanos / totalOps;  // Keep in nanoseconds
        double iops = totalOps / elapsedSeconds;

        BenchmarkResult result = new BenchmarkResult(runId, type.name(), bytesProcessed, elapsed,
                throughputMBps, avgLatencyMs, iops, end, elapsedNanos, avgLatencyNs);
        
        logger.debug("Run {} completed: type={}, throughput={:.2f} MB/s, latency={:.2f} ms",
                runId, type, throughputMBps, avgLatencyMs);
        return result;
    }

    /**
     * Starts periodic polling of real system metrics using OSHI.
     */
    public void startMetricsPolling() {
        long pollIntervalMs = config.getMetricsPollInterval().toMillis();
        metricsPoller.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    Instant now = Instant.now();
                    
                    // Get CPU usage as a percentage (0-100)
                    // Using ProcessCpuLoad from OperatingSystemMXBean
                    com.sun.management.OperatingSystemMXBean osBean = 
                            (com.sun.management.OperatingSystemMXBean) 
                            java.lang.management.ManagementFactory.getOperatingSystemMXBean();
                    double cpuLoad = Math.max(0, osBean.getProcessCpuLoad() * 100);
                    
                    // Get real RAM usage
                    long totalMemory = hardware.getMemory().getTotal();
                    long availableMemory = hardware.getMemory().getAvailable();
                    double ramUsage = ((totalMemory - availableMemory) / (double) totalMemory) * 100;
                    
                    // Get raw disk I/O counters (removed incorrect utilization %)
                    long diskReads = 0;
                    long diskWrites = 0;
                    long diskReadBytes = 0;
                    long diskWriteBytes = 0;
                    try {
                        java.util.List<oshi.hardware.HWDiskStore> diskStores = hardware.getDiskStores();
                        if (!diskStores.isEmpty()) {
                            oshi.hardware.HWDiskStore disk = diskStores.get(0);
                            diskReads = disk.getReads();
                            diskWrites = disk.getWrites();
                            diskReadBytes = disk.getReadBytes();
                            diskWriteBytes = disk.getWriteBytes();
                        }
                    } catch (Exception e) {
                        logger.debug("Could not retrieve disk counters", e);
                    }
                    
                    MetricsSnapshot snapshot = new MetricsSnapshot(
                            now,
                            cpuLoad,
                            ramUsage,
                            diskReads,
                            diskWrites,
                            diskReadBytes,
                            diskWriteBytes,
                            null // Temperature is platform-specific and may not be available
                    );
                    metricsLog.add(snapshot);
                } catch (Exception e) {
                    logger.warn("Error collecting metrics", e);
                }
            }
        }, 0, pollIntervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Returns an unmodifiable list of collected metrics.
     */
    public List<MetricsSnapshot> getMetricsLog() {
        return Collections.unmodifiableList(metricsLog);
    }

    /**
     * Properly shuts down all executor services with timeout.
     */
    private void shutdownExecutors() {
        logger.info("Shutting down executors");
        
        metricsPoller.shutdown();
        ioExecutor.shutdown();

        try {
            // Wait up to 30 seconds for tasks to complete
            if (!ioExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warn("IO executor tasks did not complete within timeout, forcing shutdown");
                ioExecutor.shutdownNow();
                if (!ioExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.error("IO executor did not terminate after forced shutdown");
                }
            }
            
            if (!metricsPoller.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("Metrics poller did not complete within timeout, forcing shutdown");
                metricsPoller.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for executor shutdown", e);
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }
}
