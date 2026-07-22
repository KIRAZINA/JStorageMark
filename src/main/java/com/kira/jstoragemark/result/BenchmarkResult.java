package com.kira.jstoragemark.result;

import java.time.Duration;
import java.time.Instant;

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
    Instant timestamp
) {}
