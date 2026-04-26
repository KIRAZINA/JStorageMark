package com.kira.jstoragemark.metrics;

import java.time.Instant;

/**
 * Snapshot of system metrics during a benchmark run.
 * Captures CPU, RAM, raw disk I/O counters, and optional temperature.
 *
 * Notes:
 * - Lightweight POJO for periodic sampling.
 * - Designed to be polled at fixed intervals (e.g., 500 ms).
 * - Disk counters are raw values (not percentages) for accurate tracking.
 * - Can be aggregated into averages or time-series charts.
 */
public final class MetricsSnapshot {

    private final Instant timestamp;
    private final double cpuUsagePercent;       // 0–100
    private final double ramUsagePercent;       // 0–100
    private final long diskReads;              // Raw read operations counter
    private final long diskWrites;              // Raw write operations counter
    private final long diskReadBytes;           // Total bytes read
    private final long diskWriteBytes;          // Total bytes written
    private final Double diskTemperatureC;      // Optional, may be null

    public MetricsSnapshot(Instant timestamp,
                           double cpuUsagePercent,
                           double ramUsagePercent,
                           long diskReads,
                           long diskWrites,
                           long diskReadBytes,
                           long diskWriteBytes,
                           Double diskTemperatureC) {
        this.timestamp = timestamp;
        this.cpuUsagePercent = cpuUsagePercent;
        this.ramUsagePercent = ramUsagePercent;
        this.diskReads = diskReads;
        this.diskWrites = diskWrites;
        this.diskReadBytes = diskReadBytes;
        this.diskWriteBytes = diskWriteBytes;
        this.diskTemperatureC = diskTemperatureC;
    }

    public Instant getTimestamp() { return timestamp; }
    public double getCpuUsagePercent() { return cpuUsagePercent; }
    public double getRamUsagePercent() { return ramUsagePercent; }
    public long getDiskReads() { return diskReads; }
    public long getDiskWrites() { return diskWrites; }
    public long getDiskReadBytes() { return diskReadBytes; }
    public long getDiskWriteBytes() { return diskWriteBytes; }
    public Double getDiskTemperatureC() { return diskTemperatureC; }

    @Override
    public String toString() {
        return "MetricsSnapshot{" +
                "timestamp=" + timestamp +
                ", cpuUsage=" + cpuUsagePercent +
                ", ramUsage=" + ramUsagePercent +
                ", diskReads=" + diskReads +
                ", diskWrites=" + diskWrites +
                ", diskReadBytes=" + diskReadBytes +
                ", diskWriteBytes=" + diskWriteBytes +
                ", diskTemperatureC=" + diskTemperatureC +
                '}';
    }
}
