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

/**
 * Coordinates execution of benchmark workloads.
 * Responsibilities:
 *  - Prepare test files in dedicated directory.
 *  - Launch threads according to config (sequential/random I/O).
 *  - Collect throughput, latency, IOPS metrics.
 *  - Poll system metrics at fixed intervals (using OSHI).
 *  - Aggregate results into BenchmarkResult objects.
 *
 * Notes:
 *  - Uses ExecutorService for concurrency with proper shutdown.
 *  - Single Random instance per session for better quality and performance.
 *  - FileChannel.force(true) ensures data is flushed to disk.
 *  - OSHI provides real system metrics without external dependencies.
 */
public final class BenchmarkRunner {
    private static final Logger logger = LoggerFactory.getLogger(BenchmarkRunner.class);

    private final BenchmarkConfig config;
    private final BenchmarkPaths paths;
    private final ScheduledExecutorService metricsPoller;
    private final ExecutorService ioExecutor;
    private final Random random;  // Shared Random instance

    private final List<MetricsSnapshot> metricsLog = new ArrayList<>();
    private final List<BenchmarkResult> results = new ArrayList<>();

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
        // Initialize single Random instance with optional seed
        this.random = config.getRandomSeed().isPresent()
                ? new Random(config.getRandomSeed().get())
                : new Random();
        logger.info("BenchmarkRunner initialized with {} threads", config.getThreads());
    }

    /**
     * Executes all configured test types sequentially with proper cleanup.
     */
    public List<BenchmarkResult> runAll() throws IOException, InterruptedException {
        try {
            paths.ensureTestDirectory();
            paths.validateFreeSpace(config.getFileSizeBytes());
            logger.info("Starting benchmark: testTypes={}, fileSize={}, iterations={}",
                    config.getTestTypes(), config.getFileSizeBytes(), config.getIterations());

            int runId = 1;
            for (BenchmarkConfig.TestType type : config.getTestTypes()) {
                for (int i = 0; i < config.getIterations(); i++) {
                    BenchmarkResult result = runSingle(runId++, type);
                    results.add(result);
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
     * Executes a single benchmark run of the given type.
     */
    private BenchmarkResult runSingle(int runId, BenchmarkConfig.TestType type) throws IOException {
        Path file = paths.testFilePath(runId, type.name().toLowerCase());
        ByteBuffer buffer = ByteBuffer.allocate(config.getBlockSizeBytes());

        Instant start = Instant.now();
        long totalOps = 0;
        long bytesProcessed = 0;
        long fileSize = config.getFileSizeBytes();

        try (FileChannel channel = FileChannel.open(file,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE)) {

            while (bytesProcessed < fileSize) {
                buffer.clear();

                if (type == BenchmarkConfig.TestType.SEQ_WRITE || type == BenchmarkConfig.TestType.RAND_WRITE) {
                    random.nextBytes(buffer.array());
                    buffer.flip();
                    channel.write(buffer);
                } else {
                    channel.read(buffer);
                }

                if (type == BenchmarkConfig.TestType.RAND_READ || type == BenchmarkConfig.TestType.RAND_WRITE) {
                    // Safe absolute value computation using nextLong masked to positive
                    long pos = (random.nextLong() & Long.MAX_VALUE) % fileSize;
                    channel.position(pos);
                }

                bytesProcessed += config.getBlockSizeBytes();
                totalOps++;
            }

            channel.force(true); // flush to disk
        }

        Instant end = Instant.now();
        Duration elapsed = Duration.between(start, end);

        double elapsedSeconds = elapsed.toMillis() / 1000.0;
        double throughputMBps = (fileSize / (1024.0 * 1024.0)) / elapsedSeconds;
        double avgLatencyMs = (double) elapsed.toMillis() / totalOps;
        double iops = totalOps / elapsedSeconds;

        BenchmarkResult result = new BenchmarkResult(runId, type.name(), fileSize, elapsed,
                throughputMBps, avgLatencyMs, iops, end);
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
                    
                    // Get real disk utilization for test directory
                    double diskUtilization = 0;
                    try {
                        java.util.List<oshi.hardware.HWDiskStore> diskStores = hardware.getDiskStores();
                        if (!diskStores.isEmpty()) {
                            diskUtilization = (diskStores.get(0).getWrites() / 
                                              (double) diskStores.get(0).getReads()) * 100;
                            diskUtilization = Math.min(100, diskUtilization); // cap at 100%
                        }
                    } catch (Exception e) {
                        logger.debug("Could not retrieve disk utilization", e);
                    }
                    
                    MetricsSnapshot snapshot = new MetricsSnapshot(
                            now,
                            cpuLoad,
                            ramUsage,
                            diskUtilization,
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
