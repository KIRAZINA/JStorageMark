package com.kira.jstoragemark.metrics;

import java.time.Instant;

/**
 * Snapshot of system metrics during a benchmark run.
 * Captures CPU, RAM, disk utilization, and optional temperature.
 *
 * Notes:
 * - Lightweight POJO for periodic sampling.
 * - Designed to be polled at fixed intervals (e.g., 500 ms).
 * - Can be aggregated into averages or time-series charts.
 */
public final class MetricsSnapshot {

    private final Instant timestamp;
    private final double cpuUsagePercent;       // 0–100
    private final double ramUsagePercent;       // 0–100
    private final double diskUtilizationPercent;// 0–100
    private final Double diskTemperatureC;      // Optional, may be null

    public MetricsSnapshot(Instant timestamp,
                           double cpuUsagePercent,
                           double ramUsagePercent,
                           double diskUtilizationPercent,
                           Double diskTemperatureC) {
        this.timestamp = timestamp;
        this.cpuUsagePercent = cpuUsagePercent;
        this.ramUsagePercent = ramUsagePercent;
        this.diskUtilizationPercent = diskUtilizationPercent;
        this.diskTemperatureC = diskTemperatureC;
    }

    public Instant getTimestamp() { return timestamp; }
    public double getCpuUsagePercent() { return cpuUsagePercent; }
    public double getRamUsagePercent() { return ramUsagePercent; }
    public double getDiskUtilizationPercent() { return diskUtilizationPercent; }
    public Double getDiskTemperatureC() { return diskTemperatureC; }

    @Override
    public String toString() {
        return "MetricsSnapshot{" +
                "timestamp=" + timestamp +
                ", cpuUsage=" + cpuUsagePercent +
                ", ramUsage=" + ramUsagePercent +
                ", diskUtilization=" + diskUtilizationPercent +
                ", diskTemperatureC=" + diskTemperatureC +
                '}';
    }
}
