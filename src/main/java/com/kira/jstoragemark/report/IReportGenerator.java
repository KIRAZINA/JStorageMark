package com.kira.jstoragemark.report;

import com.kira.jstoragemark.metrics.MetricsSnapshot;
import com.kira.jstoragemark.result.BenchmarkResult;

import java.io.IOException;
import java.util.List;

public interface IReportGenerator {
    void writeCsv(List<BenchmarkResult> results) throws IOException;
    void writeJson(List<BenchmarkResult> results, List<MetricsSnapshot> metrics,
                   SystemInfoSnapshot systemInfo) throws IOException;
    void writeHtml(List<BenchmarkResult> results, List<MetricsSnapshot> metrics,
                   SystemInfoSnapshot systemInfo) throws IOException;
}
