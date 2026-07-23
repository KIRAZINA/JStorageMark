package com.kira.jstoragemark.report;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
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

public final class ReportGenerator implements IReportGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ReportGenerator.class);

    private final BenchmarkConfig config;
    private final BenchmarkPaths paths;

    public ReportGenerator(BenchmarkConfig config, BenchmarkPaths paths) {
        this.config = config;
        this.paths = paths;
    }

    public void writeCsv(List<BenchmarkResult> results) throws IOException {
        Path csvPath = paths.reportFilePath("csv");
        try (CSVWriter writer = new CSVWriter(
                new OutputStreamWriter(Files.newOutputStream(csvPath), StandardCharsets.UTF_8))) {

            writer.writeNext(new String[]{
                    "RunId", "TestType", "BytesProcessed", "ElapsedMs", "ElapsedNs",
                    "ThroughputMBps", "AvgLatencyMs", "AvgLatencyNs", "IOPS", "Timestamp",
                    "P50LatencyNs", "P95LatencyNs", "P99LatencyNs", "P999LatencyNs", "MaxLatencyNs"
            });

            for (BenchmarkResult r : results) {
                writer.writeNext(new String[]{
                        r.runId(),
                        r.testType(),
                        String.valueOf(r.bytesProcessed()),
                        String.valueOf(r.elapsed().toMillis()),
                        String.valueOf(r.elapsedNanos()),
                        String.format(Locale.ROOT, "%.2f", r.throughputMBps()),
                        String.format(Locale.ROOT, "%.2f", r.avgLatencyMs()),
                        String.format(Locale.ROOT, "%.2f", r.avgLatencyNs()),
                        String.format(Locale.ROOT, "%.2f", r.iops()),
                        r.timestamp().toString(),
                        String.format(Locale.ROOT, "%.0f", r.p50LatencyNs()),
                        String.format(Locale.ROOT, "%.0f", r.p95LatencyNs()),
                        String.format(Locale.ROOT, "%.0f", r.p99LatencyNs()),
                        String.format(Locale.ROOT, "%.0f", r.p999LatencyNs()),
                        String.valueOf(r.maxLatencyNs())
                });
            }
            logger.info("CSV report written to {}", csvPath);
        } catch (IOException e) {
            logger.error("Failed to write CSV report", e);
            throw e;
        }
    }

    public void writeJson(List<BenchmarkResult> results,
                          List<MetricsSnapshot> metrics,
                          SystemInfoSnapshot systemInfo) throws IOException {
        Path jsonPath = paths.reportFilePath("json");
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

        try {
            ReportPayload payload = new ReportPayload(results, metrics, config.getSessionId(), systemInfo);
            mapper.writeValue(jsonPath.toFile(), payload);
            logger.info("JSON report written to {}", jsonPath);
        } catch (IOException e) {
            logger.error("Failed to write JSON report", e);
            throw e;
        }
    }

    public void writeHtml(List<BenchmarkResult> results,
                          List<MetricsSnapshot> metrics,
                          SystemInfoSnapshot systemInfo) throws IOException {
        if (!config.getReportFormats().contains(BenchmarkConfig.ReportFormat.HTML)) {
            return;
        }

        Path htmlPath = paths.reportFilePath("html");
        try (OutputStreamWriter writer = new OutputStreamWriter(
                Files.newOutputStream(htmlPath), StandardCharsets.UTF_8)) {

            writer.write("<!DOCTYPE html>\n<html>\n<head>\n");
            writer.write("<meta charset=\"UTF-8\">\n");
            writer.write("<title>JStorageMark Report</title>\n");
            writer.write("<style>\n");
            writer.write("body { font-family: Arial, sans-serif; margin: 20px; }\n");
            writer.write("table { border-collapse: collapse; width: 100%; margin-bottom: 20px; }\n");
            writer.write("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }\n");
            writer.write("th { background-color: #4CAF50; color: white; }\n");
            writer.write("tr:nth-child(even) { background-color: #f2f2f2; }\n");
            writer.write("</style>\n</head>\n<body>\n");

            writer.write("<h1>Benchmark Report - Session " +
                    StringEscapeUtils.escapeHtml4(config.getSessionId()) + "</h1>\n");

            writer.write("<h2>System Information</h2><table>\n");
            writer.write("<tr><th>Property</th><th>Value</th></tr>\n");
            writer.write("<tr><td>OS</td><td>" + StringEscapeUtils.escapeHtml4(systemInfo.osName()) + "</td></tr>\n");
            writer.write("<tr><td>Java Version</td><td>" + StringEscapeUtils.escapeHtml4(systemInfo.javaVersion()) + "</td></tr>\n");
            writer.write("<tr><td>CPU</td><td>" + StringEscapeUtils.escapeHtml4(systemInfo.cpuModel()) + "</td></tr>\n");
            writer.write("<tr><td>Total RAM</td><td>" + formatBytes(systemInfo.totalRamBytes()) + "</td></tr>\n");
            writer.write("</table>\n");

            writer.write("<h2>Results</h2><table>\n");
            writer.write("<tr><th>RunId</th><th>TestType</th><th>Throughput (MB/s)</th>" +
                    "<th>Latency (ms)</th><th>IOPS</th>" +
                    "<th>p50 (ns)</th><th>p95 (ns)</th><th>p99 (ns)</th><th>p99.9 (ns)</th><th>Max (ns)</th></tr>\n");

            for (BenchmarkResult r : results) {
                writer.write("<tr><td>" + StringEscapeUtils.escapeHtml4(r.runId()) + "</td><td>" +
                        StringEscapeUtils.escapeHtml4(r.testType()) +
                        "</td><td>" + String.format(Locale.ROOT, "%.2f", r.throughputMBps()) +
                        "</td><td>" + String.format(Locale.ROOT, "%.2f", r.avgLatencyMs()) +
                        "</td><td>" + String.format(Locale.ROOT, "%.2f", r.iops()) +
                        "</td><td>" + String.format(Locale.ROOT, "%.0f", r.p50LatencyNs()) +
                        "</td><td>" + String.format(Locale.ROOT, "%.0f", r.p95LatencyNs()) +
                        "</td><td>" + String.format(Locale.ROOT, "%.0f", r.p99LatencyNs()) +
                        "</td><td>" + String.format(Locale.ROOT, "%.0f", r.p999LatencyNs()) +
                        "</td><td>" + String.valueOf(r.maxLatencyNs()) +
                        "</td></tr>\n");
            }
            writer.write("</table>\n");

            writer.write("<h2>System Metrics</h2><table>\n");
            writer.write("<tr><th>Timestamp</th><th>CPU (%)</th><th>RAM (%)</th>" +
                    "<th>Disk Reads</th><th>Disk Writes</th><th>Disk Read MB</th><th>Disk Write MB</th></tr>\n");

            for (MetricsSnapshot m : metrics) {
                writer.write("<tr><td>" + StringEscapeUtils.escapeHtml4(m.timestamp().toString()) +
                        "</td><td>" + String.format(Locale.ROOT, "%.2f", m.cpuUsagePercent()) +
                        "</td><td>" + String.format(Locale.ROOT, "%.2f", m.ramUsagePercent()) +
                        "</td><td>" + StringEscapeUtils.escapeHtml4(String.valueOf(m.diskReads())) +
                        "</td><td>" + StringEscapeUtils.escapeHtml4(String.valueOf(m.diskWrites())) +
                        "</td><td>" + String.format(Locale.ROOT, "%.2f", m.diskReadBytes() / (1024.0 * 1024.0)) +
                        "</td><td>" + String.format(Locale.ROOT, "%.2f", m.diskWriteBytes() / (1024.0 * 1024.0)) +
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

    public void writeSweepReport(List<BenchmarkResult> results, List<Integer> blockSizes) throws IOException {
        Path csvPath = paths.reportFilePath("sweep.csv");
        try (CSVWriter writer = new CSVWriter(
                new OutputStreamWriter(Files.newOutputStream(csvPath), StandardCharsets.UTF_8))) {

            writer.writeNext(new String[]{
                    "BlockSize", "ThroughputMBps", "IOPS", "AvgLatencyNs", "P50LatencyNs",
                    "P95LatencyNs", "P99LatencyNs", "P999LatencyNs", "MaxLatencyNs"
            });

            for (int i = 0; i < results.size(); i++) {
                BenchmarkResult r = results.get(i);
                int blockSize = blockSizes.get(i / Math.max(1, results.size() / blockSizes.size()));
                writer.writeNext(new String[]{
                        String.valueOf(blockSize),
                        String.format(Locale.ROOT, "%.2f", r.throughputMBps()),
                        String.format(Locale.ROOT, "%.2f", r.iops()),
                        String.format(Locale.ROOT, "%.0f", r.avgLatencyNs()),
                        String.format(Locale.ROOT, "%.0f", r.p50LatencyNs()),
                        String.format(Locale.ROOT, "%.0f", r.p95LatencyNs()),
                        String.format(Locale.ROOT, "%.0f", r.p99LatencyNs()),
                        String.format(Locale.ROOT, "%.0f", r.p999LatencyNs()),
                        String.valueOf(r.maxLatencyNs())
                });
            }
            logger.info("Sweep report written to {}", csvPath);
        } catch (IOException e) {
            logger.error("Failed to write sweep report", e);
            throw e;
        }
    }

    public void writeDiffReport(List<BenchmarkResult> baseline, List<BenchmarkResult> current) throws IOException {
        Path htmlPath = paths.reportFilePath("diff.html");
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(htmlPath, StandardCharsets.UTF_8))) {
            writer.println("<!DOCTYPE html>");
            writer.println("<html><head>");
            writer.println("<meta charset=\"UTF-8\">");
            writer.println("<title>JStorageMark Comparison Report</title>");
            writer.println("<style>");
            writer.println("body { font-family: Arial, sans-serif; margin: 20px; }");
            writer.println("table { border-collapse: collapse; width: 100%; margin-bottom: 20px; }");
            writer.println("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }");
            writer.println("th { background-color: #4CAF50; color: white; }");
            writer.println("tr:nth-child(even) { background-color: #f2f2f2; }");
            writer.println(".improvement { color: green; font-weight: bold; }");
            writer.println(".regression { color: red; font-weight: bold; }");
            writer.println(".neutral { color: gray; }");
            writer.println("</style></head><body>");
            writer.println("<h1>Benchmark Comparison Report</h1>");
            writer.println("<table>");
            writer.println("<tr><th>RunId</th><th>TestType</th><th>Metric</th>" +
                    "<th>Baseline</th><th>Current</th><th>Change (%)</th></tr>");

            int size = Math.max(baseline.size(), current.size());
            for (int i = 0; i < size; i++) {
                BenchmarkResult b = i < baseline.size() ? baseline.get(i) : null;
                BenchmarkResult c = i < current.size() ? current.get(i) : null;
                String runId = c != null ? c.runId() : (b != null ? b.runId() : "");
                String testType = c != null ? c.testType() : (b != null ? b.testType() : "");

                writeDiffRow(writer, runId, testType, "Throughput (MB/s)",
                        b != null ? b.throughputMBps() : 0,
                        c != null ? c.throughputMBps() : 0, true);
                writeDiffRow(writer, runId, testType, "IOPS",
                        b != null ? b.iops() : 0,
                        c != null ? c.iops() : 0, true);
                writeDiffRow(writer, runId, testType, "Avg Latency (ns)",
                        b != null ? b.avgLatencyNs() : 0,
                        c != null ? c.avgLatencyNs() : 0, false);
                writeDiffRow(writer, runId, testType, "p99 Latency (ns)",
                        b != null ? b.p99LatencyNs() : 0,
                        c != null ? c.p99LatencyNs() : 0, false);
            }

            writer.println("</table>");
            writer.println("<p><span class='improvement'>&#9650; Improvement</span> | " +
                    "<span class='regression'>&#9660; Regression</span></p>");
            writer.println("</body></html>");

            logger.info("Diff report written to {}", htmlPath);
        } catch (IOException e) {
            logger.error("Failed to write diff report", e);
            throw e;
        }
    }

    private void writeDiffRow(PrintWriter writer, String runId, String testType,
                               String metric, double baselineVal, double currentVal,
                               boolean higherIsBetter) {
        double change = baselineVal != 0
                ? ((currentVal - baselineVal) / Math.abs(baselineVal)) * 100
                : (currentVal != 0 ? 100 : 0);

        String cssClass;
        if (Math.abs(change) < 0.5) {
            cssClass = "neutral";
        } else if (higherIsBetter) {
            cssClass = change > 0 ? "improvement" : "regression";
        } else {
            cssClass = change < 0 ? "improvement" : "regression";
        }

        writer.printf(Locale.ROOT, "<tr><td>%s</td><td>%s</td><td>%s</td><td>%.2f</td><td>%.2f</td>" +
                "<td class='%s'>%+.1f%%</td></tr>%n",
                StringEscapeUtils.escapeHtml4(runId),
                StringEscapeUtils.escapeHtml4(testType),
                StringEscapeUtils.escapeHtml4(metric),
                baselineVal, currentVal, cssClass, change);
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format(Locale.ROOT, "%.2f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private record ReportPayload(
        List<BenchmarkResult> results,
        List<MetricsSnapshot> metrics,
        String sessionId,
        SystemInfoSnapshot systemInfo
    ) {}
}
