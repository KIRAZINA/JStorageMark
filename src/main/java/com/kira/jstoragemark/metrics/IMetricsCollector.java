package com.kira.jstoragemark.metrics;

import java.util.List;

public interface IMetricsCollector {
    void start();
    void stop();
    List<MetricsSnapshot> getMetricsLog();
}
