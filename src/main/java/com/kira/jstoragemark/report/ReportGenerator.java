package com.kira.jstoragemark.report;

import com.kira.jstoragemark.result.BenchmarkResult;
import com.kira.jstoragemark.metrics.MetricsSnapshot;
import com.kira.jstoragemark.config.BenchmarkConfig;
import com.kira.jstoragemark.fs.BenchmarkPaths;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.opencsv.CSVWriter;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates benchmark reports in CSV, JSON, and optionally HTML.
 *
 * Notes:
 * - CSV: simple tabular format for spreadsheets.
 * - JSON: structured format for programmatic analysis.
 * - HTML: optional, can embed charts (e.g., JFreeChart).
 */
public final class ReportGenerator {

    private final BenchmarkConfig config;
    private final BenchmarkPaths paths;

    public ReportGenerator(BenchmarkConfig config, BenchmarkPaths paths) {
        this.config = config;
        this.paths = paths;
    }

    /**
     * Writes results to CSV file.
     */
    public void writeCsv(List<BenchmarkResult> results) throws IOException {
        Path csvPath = paths.reportFilePath("csv");
        try (CSVWriter writer = new CSVWriter(new FileWriter(csvPath.toFile()))) {
            // Header
            writer.writeNext(new String[]{
                    "RunId", "TestType", "BytesProcessed", "ElapsedMs",
                    "ThroughputMBps", "AvgLatencyMs", "IOPS", "Timestamp"
            });

            // Rows
            for (BenchmarkResult r : results) {
                writer.writeNext(new String[]{
                        String.valueOf(r.getRunId()),
                        r.getTestType(),
                        String.valueOf(r.getBytesProcessed()),
                        String.valueOf(r.getElapsed().toMillis()),
                        String.format("%.2f", r.getThroughputMBps()),
                        String.format("%.2f", r.getAvgLatencyMs()),
                        String.format("%.2f", r.getIops()),
                        r.getTimestamp().toString()
                });
            }
        }
    }

    /**
     * Writes results to JSON file.
     */
    public void writeJson(List<BenchmarkResult> results,
                          List<MetricsSnapshot> metrics) throws IOException {
        Path jsonPath = paths.reportFilePath("json");
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        // Register JavaTimeModule to handle Duration, Instant, etc.
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());


        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        ReportPayload payload = new ReportPayload(results, metrics, config.getSessionId());
        mapper.writeValue(jsonPath.toFile(), payload);
    }

    /**
     * Writes optional HTML report (stub).
     * In a full implementation, integrate JFreeChart for charts.
     */
    public void writeHtml(List<BenchmarkResult> results,
                          List<MetricsSnapshot> metrics) throws IOException {
        if (!config.getReportFormats().contains(BenchmarkConfig.ReportFormat.HTML)) {
            return; // skip if HTML not requested
        }

        Path htmlPath = paths.reportFilePath("html");
        try (FileWriter writer = new FileWriter(htmlPath.toFile())) {
            writer.write("<html><head><title>JStorageMark Report</title></head><body>\n");
            writer.write("<h1>Benchmark Report - Session " + config.getSessionId() + "</h1>\n");

            writer.write("<h2>Results</h2><table border='1'>\n");
            writer.write("<tr><th>RunId</th><th>TestType</th><th>Throughput MB/s</th><th>Latency ms</th><th>IOPS</th></tr>\n");
            for (BenchmarkResult r : results) {
                writer.write("<tr><td>" + r.getRunId() + "</td><td>" + r.getTestType() +
                        "</td><td>" + String.format("%.2f", r.getThroughputMBps()) +
                        "</td><td>" + String.format("%.2f", r.getAvgLatencyMs()) +
                        "</td><td>" + String.format("%.2f", r.getIops()) + "</td></tr>\n");
            }
            writer.write("</table>\n");

            writer.write("<h2>Metrics Snapshots</h2><ul>\n");
            for (MetricsSnapshot m : metrics) {
                writer.write("<li>" + m.toString() + "</li>\n");
            }
            writer.write("</ul>\n");

            writer.write("</body></html>");
        }
    }

    /**
     * Simple wrapper class for JSON payload.
     */
    private static final class ReportPayload {
        public final List<BenchmarkResult> results;
        public final List<MetricsSnapshot> metrics;
        public final String sessionId;

        public ReportPayload(List<BenchmarkResult> results,
                             List<MetricsSnapshot> metrics,
                             String sessionId) {
            this.results = results;
            this.metrics = metrics;
            this.sessionId = sessionId;
        }
    }
}
