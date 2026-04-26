package com.kira.jstoragemark.report;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.kira.jstoragemark.config.BenchmarkConfig;
import com.kira.jstoragemark.fs.BenchmarkPaths;
import com.kira.jstoragemark.metrics.MetricsSnapshot;
import com.kira.jstoragemark.result.BenchmarkResult;
import com.opencsv.CSVWriter;

/**
 * Generates benchmark reports in CSV, JSON, and optionally HTML.
 * All output uses UTF-8 encoding for cross-platform compatibility.
 *
 * Notes:
 * - CSV: simple tabular format for spreadsheets with explicit UTF-8 encoding.
 * - JSON: structured format for programmatic analysis.
 * - HTML: optional, with properly escaped data to prevent XSS.
 */
public final class ReportGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ReportGenerator.class);

    private final BenchmarkConfig config;
    private final BenchmarkPaths paths;

    public ReportGenerator(BenchmarkConfig config, BenchmarkPaths paths) {
        this.config = config;
        this.paths = paths;
    }

    /**
     * Writes results to CSV file with UTF-8 encoding.
     */
    public void writeCsv(List<BenchmarkResult> results) throws IOException {
        Path csvPath = paths.reportFilePath("csv");
        try (CSVWriter writer = new CSVWriter(
                new OutputStreamWriter(Files.newOutputStream(csvPath), StandardCharsets.UTF_8))) {
            
            // Header
            writer.writeNext(new String[]{
                    "RunId", "TestType", "BytesProcessed", "ElapsedMs", "ElapsedNs",
                    "ThroughputMBps", "AvgLatencyMs", "AvgLatencyNs", "IOPS", "Timestamp"
            });

            // Rows with Locale-independent formatting
            for (BenchmarkResult r : results) {
                writer.writeNext(new String[]{
                        String.valueOf(r.getRunId()),
                        r.getTestType(),
                        String.valueOf(r.getBytesProcessed()),
                        String.valueOf(r.getElapsed().toMillis()),
                        String.valueOf(r.getElapsedNanos()),
                        String.format(Locale.ROOT, "%.2f", r.getThroughputMBps()),
                        String.format(Locale.ROOT, "%.2f", r.getAvgLatencyMs()),
                        String.format(Locale.ROOT, "%.2f", r.getAvgLatencyNs()),
                        String.format(Locale.ROOT, "%.2f", r.getIops()),
                        r.getTimestamp().toString()
                });
            }
            logger.info("CSV report written to {}", csvPath);
        } catch (IOException e) {
            logger.error("Failed to write CSV report", e);
            throw e;
        }
    }

    /**
     * Writes results to JSON file with UTF-8 encoding.
     */
    public void writeJson(List<BenchmarkResult> results,
                          List<MetricsSnapshot> metrics) throws IOException {
        Path jsonPath = paths.reportFilePath("json");
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

        try {
            ReportPayload payload = new ReportPayload(results, metrics, config.getSessionId());
            mapper.writeValue(jsonPath.toFile(), payload);
            logger.info("JSON report written to {}", jsonPath);
        } catch (IOException e) {
            logger.error("Failed to write JSON report", e);
            throw e;
        }
    }

    /**
     * Writes optional HTML report with properly escaped data.
     * In a full implementation, integrate JFreeChart for charts.
     */
    public void writeHtml(List<BenchmarkResult> results,
                          List<MetricsSnapshot> metrics) throws IOException {
        if (!config.getReportFormats().contains(BenchmarkConfig.ReportFormat.HTML)) {
            return; // skip if HTML not requested
        }

        Path htmlPath = paths.reportFilePath("html");
        try (OutputStreamWriter writer = new OutputStreamWriter(
                Files.newOutputStream(htmlPath), StandardCharsets.UTF_8)) {
            
            writer.write("<!DOCTYPE html>\n<html>\n<head>\n");
            writer.write("<meta charset=\"UTF-8\">\n");
            writer.write("<title>JStorageMark Report</title>\n");
            writer.write("<style>\n");
            writer.write("body { font-family: Arial, sans-serif; margin: 20px; }\n");
            writer.write("table { border-collapse: collapse; width: 100%; }\n");
            writer.write("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }\n");
            writer.write("th { background-color: #4CAF50; color: white; }\n");
            writer.write("tr:nth-child(even) { background-color: #f2f2f2; }\n");
            writer.write("</style>\n</head>\n<body>\n");
            
            writer.write("<h1>Benchmark Report - Session " + 
                    StringEscapeUtils.escapeHtml4(config.getSessionId()) + "</h1>\n");

            writer.write("<h2>Results</h2><table>\n");
            writer.write("<tr><th>RunId</th><th>TestType</th><th>Throughput (MB/s)</th>" +
                    "<th>Latency (ms)</th><th>IOPS</th></tr>\n");
            
            for (BenchmarkResult r : results) {
                writer.write("<tr><td>" + r.getRunId() + "</td><td>" + 
                        StringEscapeUtils.escapeHtml4(r.getTestType()) +
                        "</td><td>" + String.format(Locale.ROOT, "%.2f", r.getThroughputMBps()) +
                        "</td><td>" + String.format(Locale.ROOT, "%.2f", r.getAvgLatencyMs()) +
                        "</td><td>" + String.format(Locale.ROOT, "%.2f", r.getIops()) + 
                        "</td></tr>\n");
            }
            writer.write("</table>\n");

            writer.write("<h2>System Metrics</h2><table>\n");
            writer.write("<tr><th>Timestamp</th><th>CPU (%)</th><th>RAM (%)</th>" +
                    "<th>Disk Reads</th><th>Disk Writes</th><th>Disk Read MB</th><th>Disk Write MB</th></tr>\n");
            
            for (MetricsSnapshot m : metrics) {
                writer.write("<tr><td>" + StringEscapeUtils.escapeHtml4(m.getTimestamp().toString()) +
                        "</td><td>" + String.format(Locale.ROOT, "%.2f", m.getCpuUsagePercent()) +
                        "</td><td>" + String.format(Locale.ROOT, "%.2f", m.getRamUsagePercent()) +
                        "</td><td>" + StringEscapeUtils.escapeHtml4(String.valueOf(m.getDiskReads())) +
                        "</td><td>" + StringEscapeUtils.escapeHtml4(String.valueOf(m.getDiskWrites())) +
                        "</td><td>" + String.format(Locale.ROOT, "%.2f", m.getDiskReadBytes() / (1024.0 * 1024.0)) +
                        "</td><td>" + String.format(Locale.ROOT, "%.2f", m.getDiskWriteBytes() / (1024.0 * 1024.0)) +
                        "</td></tr>\n");
            }
            writer.write("</table>\n");
            writer.write("</body>\n</html>");
            
            logger.info("HTML report written to {}", htmlPath);
        } catch (IOException e) {
            logger.error("Failed to write HTML report", e);
            throw e;
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
