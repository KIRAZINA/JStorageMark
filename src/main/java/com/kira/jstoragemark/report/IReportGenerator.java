package com.kira.jstoragemark.report;

import com.kira.jstoragemark.metrics.MetricsSnapshot;
import com.kira.jstoragemark.result.BenchmarkResult;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface IReportGenerator {
    void writeCsv(List<BenchmarkResult> results) throws IOException;
    void writeJson(List<BenchmarkResult> results, List<MetricsSnapshot> metrics,
                   SystemInfoSnapshot systemInfo) throws IOException;
    void writeHtml(List<BenchmarkResult> results, List<MetricsSnapshot> metrics,
                   SystemInfoSnapshot systemInfo) throws IOException;
    void writeSweepReport(List<BenchmarkResult> results, List<Integer> blockSizes) throws IOException;

    /**
     * Writes a block-size sweep report from results grouped explicitly by block size.
     *
     * <p>Missing or empty block-size groups are rendered as {@code N/A} rows so columns stay
     * aligned even when part of a sweep failed.
     *
     * @param blockSizes requested block sizes in execution order
     * @param resultsByBlockSize results keyed by block size
     * @throws IOException when the report cannot be written
     */
    void writeSweepReport(List<Integer> blockSizes,
                          Map<Integer, List<BenchmarkResult>> resultsByBlockSize) throws IOException;

    void writeDiffReport(List<BenchmarkResult> baseline, List<BenchmarkResult> current) throws IOException;
}
