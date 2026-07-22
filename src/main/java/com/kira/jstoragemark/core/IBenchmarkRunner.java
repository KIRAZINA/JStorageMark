package com.kira.jstoragemark.core;

import com.kira.jstoragemark.metrics.MetricsSnapshot;
import com.kira.jstoragemark.result.BenchmarkResult;

import java.io.IOException;
import java.util.List;

public interface IBenchmarkRunner {
    List<BenchmarkResult> runAll() throws IOException, InterruptedException;
    void startMetricsPolling();
    List<MetricsSnapshot> getMetricsLog();
}
