package com.kira.jstoragemark.result;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents the outcome of a single benchmark run.
 * Captures throughput, latency, IOPS, and metadata for reporting.
 *
 * Notes:
 * - Immutable once constructed.
 * - Designed for easy serialization to CSV/JSON.
 * - Duration fields allow precise measurement of elapsed time.
 */
public final class BenchmarkResult {

    private final int runId;                // Sequential run number
    private final String testType;          // SEQ_READ, RAND_WRITE, etc.
    private final long bytesProcessed;      // Total bytes read/written
    private final Duration elapsed;         // Total time taken
    private final long elapsedNanos;        // High precision elapsed time in nanoseconds
    private final double throughputMBps;    // MB/s
    private final double avgLatencyMs;      // Average latency per operation (milliseconds)
    private final double avgLatencyNs;      // Average latency per operation (nanoseconds)
    private final double iops;              // I/O operations per second
    private final Instant timestamp;        // When run completed

    public BenchmarkResult(int runId,
                           String testType,
                           long bytesProcessed,
                           Duration elapsed,
                           double throughputMBps,
                           double avgLatencyMs,
                           double iops,
                           Instant timestamp,
                           long elapsedNanos,
                           double avgLatencyNs) {
        this.runId = runId;
        this.testType = Objects.requireNonNull(testType);
        this.bytesProcessed = bytesProcessed;
        this.elapsed = Objects.requireNonNull(elapsed);
        this.elapsedNanos = elapsedNanos;
        this.throughputMBps = throughputMBps;
        this.avgLatencyMs = avgLatencyMs;
        this.avgLatencyNs = avgLatencyNs;
        this.iops = iops;
        this.timestamp = Objects.requireNonNull(timestamp);
    }

    public int getRunId() { return runId; }
    public String getTestType() { return testType; }
    public long getBytesProcessed() { return bytesProcessed; }
    public Duration getElapsed() { return elapsed; }
    public long getElapsedNanos() { return elapsedNanos; }
    public double getThroughputMBps() { return throughputMBps; }
    public double getAvgLatencyMs() { return avgLatencyMs; }
    public double getAvgLatencyNs() { return avgLatencyNs; }
    public double getIops() { return iops; }
    public Instant getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return "BenchmarkResult{" +
                "runId=" + runId +
                ", testType='" + testType + '\'' +
                ", bytesProcessed=" + bytesProcessed +
                ", elapsed=" + elapsed.toMillis() + "ms" +
                ", elapsedNanos=" + elapsedNanos + "ns" +
                ", throughputMBps=" + throughputMBps +
                ", avgLatencyMs=" + avgLatencyMs +
                ", avgLatencyNs=" + avgLatencyNs +
                ", iops=" + iops +
                ", timestamp=" + timestamp +
                '}';
    }
}
