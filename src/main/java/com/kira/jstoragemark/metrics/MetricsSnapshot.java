package com.kira.jstoragemark.metrics;

import java.time.Instant;

public record MetricsSnapshot(
    Instant timestamp,
    double cpuUsagePercent,
    double ramUsagePercent,
    long diskReads,
    long diskWrites,
    long diskReadBytes,
    long diskWriteBytes,
    Double diskTemperatureC
) {}
