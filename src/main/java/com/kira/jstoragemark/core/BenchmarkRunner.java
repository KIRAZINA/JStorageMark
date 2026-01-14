package com.kira.jstoragemark.core;

import com.kira.jstoragemark.config.BenchmarkConfig;
import com.kira.jstoragemark.fs.BenchmarkPaths;
import com.kira.jstoragemark.result.BenchmarkResult;
import com.kira.jstoragemark.metrics.MetricsSnapshot;

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
 *  - Poll system metrics at fixed intervals.
 *  - Aggregate results into BenchmarkResult objects.
 *
 * Notes:
 *  - Uses ExecutorService for concurrency.
 *  - FileChannel.force(true) ensures data is flushed to disk.
 *  - Random workloads use seeded Random for reproducibility.
 */
public final class BenchmarkRunner {

    private final BenchmarkConfig config;
    private final BenchmarkPaths paths;
    private final ScheduledExecutorService metricsPoller;
    private final ExecutorService ioExecutor;

    private final List<MetricsSnapshot> metricsLog = new ArrayList<>();
    private final List<BenchmarkResult> results = new ArrayList<>();

    public BenchmarkRunner(BenchmarkConfig config, BenchmarkPaths paths) {
        this.config = Objects.requireNonNull(config);
        this.paths = Objects.requireNonNull(paths);
        this.metricsPoller = Executors.newSingleThreadScheduledExecutor();
        this.ioExecutor = Executors.newFixedThreadPool(config.getThreads());
    }

    /**
     * Executes all configured test types sequentially.
     */
    public List<BenchmarkResult> runAll() throws IOException {
        paths.ensureTestDirectory();
        paths.validateFreeSpace(config.getFileSizeBytes());

        int runId = 1;
        for (BenchmarkConfig.TestType type : config.getTestTypes()) {
            for (int i = 0; i < config.getIterations(); i++) {
                BenchmarkResult result = runSingle(runId++, type);
                results.add(result);
            }
        }
        shutdownExecutors();
        return Collections.unmodifiableList(results);
    }

    /**
     * Executes a single benchmark run of the given type.
     */
    private BenchmarkResult runSingle(int runId, BenchmarkConfig.TestType type) throws IOException {
        Path file = paths.testFilePath(runId, type.name().toLowerCase());

        // Prepare buffer
        ByteBuffer buffer = ByteBuffer.allocate(config.getBlockSizeBytes());
        Random random = config.getRandomSeed().isPresent()
                ? new Random(config.getRandomSeed().get())
                : new Random();

        Instant start = Instant.now();
        long totalOps = 0;

        try (FileChannel channel = FileChannel.open(file,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE)) {

            long bytesProcessed = 0;
            long fileSize = config.getFileSizeBytes();

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
                    long pos = (Math.abs(random.nextLong()) % fileSize);
                    channel.position(pos);
                }

                bytesProcessed += config.getBlockSizeBytes();
                totalOps++;
            }

            channel.force(true); // flush to disk
        }

        Instant end = Instant.now();
        Duration elapsed = Duration.between(start, end);

        double throughputMBps = (config.getFileSizeBytes() / (1024.0 * 1024.0)) /
                (elapsed.toMillis() / 1000.0);
        double avgLatencyMs = (double) elapsed.toMillis() / totalOps;
        double iops = totalOps / (elapsed.toMillis() / 1000.0);

        return new BenchmarkResult(runId,
                type.name(),
                config.getFileSizeBytes(),
                elapsed,
                throughputMBps,
                avgLatencyMs,
                iops,
                end);
    }

    /**
     * Starts periodic polling of system metrics.
     * (Stub implementation — integrate OSHI later).
     */
    public void startMetricsPolling() {
        metricsPoller.scheduleAtFixedRate(() -> {
            Instant now = Instant.now();
            // Placeholder values — integrate OSHI for real metrics
            MetricsSnapshot snapshot = new MetricsSnapshot(
                    now,
                    Math.random() * 50, // CPU usage
                    Math.random() * 70, // RAM usage
                    Math.random() * 80, // Disk utilization
                    null                // Temperature optional
            );
            metricsLog.add(snapshot);
        }, 0, config.getMetricsPollInterval().toMillis(), TimeUnit.MILLISECONDS);
    }

    public List<MetricsSnapshot> getMetricsLog() {
        return Collections.unmodifiableList(metricsLog);
    }

    private void shutdownExecutors() {
        metricsPoller.shutdown();
        ioExecutor.shutdown();
    }
}
