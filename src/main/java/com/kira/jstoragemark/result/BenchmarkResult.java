package com.kira.jstoragemark.result;

import java.time.Duration;
import java.time.Instant;

/**
 * Immutable result of one benchmark run.
 *
 * <p>Besides the combined throughput and IOPS, the record keeps direction-aware metrics so mixed
 * workloads can show how much of the work was reads and how much was writes:
 * {@link #readThroughputMBps()}, {@link #writeThroughputMBps()}, {@link #readIops()}, and
 * {@link #writeIops()} .
 *
 * @param runId run identifier, for example {@code run-001-thread-00}
 * @param testType benchmark type name
 * @param bytesProcessed total bytes read plus written
 * @param elapsed wall-clock duration of the run
 * @param elapsedNanos duration in nanoseconds
 * @param throughputMBps combined throughput in MiB/s
 * @param avgLatencyMs average latency in milliseconds
 * @param avgLatencyNs average latency in nanoseconds
 * @param iops combined operations per second
 * @param timestamp completion timestamp
 * @param p50LatencyNs median latency in nanoseconds
 * @param p95LatencyNs 95th percentile latency in nanoseconds
 * @param p99LatencyNs 99th percentile latency in nanoseconds
 * @param p999LatencyNs 99.9th percentile latency in nanoseconds
 * @param maxLatencyNs maximum observed latency in nanoseconds
 * @param readThroughputMBps read throughput in MiB/s
 * @param writeThroughputMBps write throughput in MiB/s
 * @param readIops read operations per second
 * @param writeIops write operations per second
 */
public record BenchmarkResult(
    String runId,
    String testType,
    long bytesProcessed,
    Duration elapsed,
    long elapsedNanos,
    double throughputMBps,
    double avgLatencyMs,
    double avgLatencyNs,
    double iops,
    Instant timestamp,
    double p50LatencyNs,
    double p95LatencyNs,
    double p99LatencyNs,
    double p999LatencyNs,
    long maxLatencyNs,
    double readThroughputMBps,
    double writeThroughputMBps,
    double readIops,
    double writeIops
) {

    /**
     * Backward-compatible constructor without direction-aware metrics.
     *
     * <p>Pure read and pure write workloads infer their direction from the test type. Mixed
     * workloads constructed through this legacy path report zero directional values because the
     * read/write split is unknown; use {@link #fromCounts} when the exact split is available.
     *
     * @param runId run identifier
     * @param testType benchmark type name
     * @param bytesProcessed total bytes processed
     * @param elapsed wall-clock duration
     * @param elapsedNanos duration in nanoseconds
     * @param throughputMBps combined throughput
     * @param avgLatencyMs average latency in milliseconds
     * @param avgLatencyNs average latency in nanoseconds
     * @param iops combined operations per second
     * @param timestamp completion timestamp
     * @param p50LatencyNs median latency
     * @param p95LatencyNs 95th percentile latency
     * @param p99LatencyNs 99th percentile latency
     * @param p999LatencyNs 99.9th percentile latency
     * @param maxLatencyNs maximum latency
     */
    public BenchmarkResult(
            String runId,
            String testType,
            long bytesProcessed,
            Duration elapsed,
            long elapsedNanos,
            double throughputMBps,
            double avgLatencyMs,
            double avgLatencyNs,
            double iops,
            Instant timestamp,
            double p50LatencyNs,
            double p95LatencyNs,
            double p99LatencyNs,
            double p999LatencyNs,
            long maxLatencyNs) {
        this(
                runId,
                testType,
                bytesProcessed,
                elapsed,
                elapsedNanos,
                throughputMBps,
                avgLatencyMs,
                avgLatencyNs,
                iops,
                timestamp,
                p50LatencyNs,
                p95LatencyNs,
                p99LatencyNs,
                p999LatencyNs,
                maxLatencyNs,
                isReadOnly(testType) ? throughputMBps : 0.0,
                isWriteOnly(testType) ? throughputMBps : 0.0,
                isReadOnly(testType) ? iops : 0.0,
                isWriteOnly(testType) ? iops : 0.0);
    }

    /**
     * Builds a result from the exact read/write byte and operation counts.
     *
     * <p>Combined metrics are derived from the sum of both directions, while the four directional
     * metrics use only their own counters. This is the path used by {@code BenchmarkRunner}, so a
     * 70/30 mixed workload reports 70% of its throughput and IOPS as reads and 30% as writes.
     *
     * @param runId run identifier
     * @param testType benchmark type name
     * @param bytesRead bytes actually read
     * @param bytesWritten bytes actually written
     * @param readOps completed read operations
     * @param writeOps completed write operations
     * @param elapsed wall-clock duration
     * @param timestamp completion timestamp
     * @param p50LatencyNs median latency
     * @param p95LatencyNs 95th percentile latency
     * @param p99LatencyNs 99th percentile latency
     * @param p999LatencyNs 99.9th percentile latency
     * @param maxLatencyNs maximum latency
     * @return populated result with direction-aware metrics
     */
    public static BenchmarkResult fromCounts(
            String runId,
            String testType,
            long bytesRead,
            long bytesWritten,
            long readOps,
            long writeOps,
            Duration elapsed,
            Instant timestamp,
            double p50LatencyNs,
            double p95LatencyNs,
            double p99LatencyNs,
            double p999LatencyNs,
            long maxLatencyNs) {

        long bytesProcessed = bytesRead + bytesWritten;
        long totalOps = readOps + writeOps;
        long elapsedNanos = elapsed.toNanos();
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;

        return new BenchmarkResult(
                runId,
                testType,
                bytesProcessed,
                elapsed,
                elapsedNanos,
                throughputMBps(bytesProcessed, elapsedSeconds),
                totalOps > 0 ? (elapsedNanos / 1_000_000.0) / totalOps : 0.0,
                totalOps > 0 ? (double) elapsedNanos / totalOps : 0.0,
                perSecond(totalOps, elapsedSeconds),
                timestamp,
                p50LatencyNs,
                p95LatencyNs,
                p99LatencyNs,
                p999LatencyNs,
                maxLatencyNs,
                throughputMBps(bytesRead, elapsedSeconds),
                throughputMBps(bytesWritten, elapsedSeconds),
                perSecond(readOps, elapsedSeconds),
                perSecond(writeOps, elapsedSeconds));
    }

    /**
     * Returns whether the test type measures reads only.
     *
     * @param testType benchmark type name
     * @return true for read-only workloads
     */
    private static boolean isReadOnly(String testType) {
        return testType != null
                && testType.contains("READ")
                && !testType.contains("WRITE");
    }

    /**
     * Returns whether the test type measures writes only.
     *
     * @param testType benchmark type name
     * @return true for write-only workloads
     */
    private static boolean isWriteOnly(String testType) {
        return testType != null
                && testType.contains("WRITE")
                && !testType.contains("READ");
    }

    /**
     * Converts bytes and an elapsed duration into MiB/s.
     *
     * @param bytes bytes transferred
     * @param elapsedSeconds elapsed time in seconds
     * @return throughput, or zero when no time elapsed
     */
    private static double throughputMBps(long bytes, double elapsedSeconds) {
        return elapsedSeconds > 0
                ? (bytes / (1024.0 * 1024.0)) / elapsedSeconds
                : 0.0;
    }

    /**
     * Converts an operation count and an elapsed duration into operations per second.
     *
     * @param ops completed operations
     * @param elapsedSeconds elapsed time in seconds
     * @return IOPS, or zero when no time elapsed
     */
    private static double perSecond(long ops, double elapsedSeconds) {
        return elapsedSeconds > 0 ? ops / elapsedSeconds : 0.0;
    }
}
