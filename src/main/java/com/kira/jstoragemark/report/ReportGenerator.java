package com.kira.jstoragemark.report;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
                    "P50LatencyNs", "P95LatencyNs", "P99LatencyNs", "P999LatencyNs", "MaxLatencyNs",
                    "ReadThroughputMBps", "WriteThroughputMBps", "ReadIOPS", "WriteIOPS"
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
                        String.valueOf(r.maxLatencyNs()),
                        String.format(Locale.ROOT, "%.2f", r.readThroughputMBps()),
                        String.format(Locale.ROOT, "%.2f", r.writeThroughputMBps()),
                        String.format(Locale.ROOT, "%.2f", r.readIops()),
                        String.format(Locale.ROOT, "%.2f", r.writeIops())
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
                    "<th>Read (MB/s)</th><th>Write (MB/s)</th>" +
                    "<th>Latency (ms)</th><th>IOPS</th><th>Read IOPS</th><th>Write IOPS</th>" +
                    "<th>p50 (ns)</th><th>p95 (ns)</th><th>p99 (ns)</th><th>p99.9 (ns)</th><th>Max (ns)</th></tr>\n");

            for (BenchmarkResult r : results) {
                writer.write("<tr><td>" + StringEscapeUtils.escapeHtml4(r.runId()) + "</td><td>" +
                        StringEscapeUtils.escapeHtml4(r.testType()) +
                        "</td><td>" + String.format(Locale.ROOT, "%.2f", r.throughputMBps()) +
                        "</td><td>" + String.format(Locale.ROOT, "%.2f", r.readThroughputMBps()) +
                        "</td><td>" + String.format(Locale.ROOT, "%.2f", r.writeThroughputMBps()) +
                        "</td><td>" + String.format(Locale.ROOT, "%.2f", r.avgLatencyMs()) +
                        "</td><td>" + String.format(Locale.ROOT, "%.2f", r.iops()) +
                        "</td><td>" + String.format(Locale.ROOT, "%.2f", r.readIops()) +
                        "</td><td>" + String.format(Locale.ROOT, "%.2f", r.writeIops()) +
                        "</td><td>" + String.format(Locale.ROOT, "%.0f", r.p50LatencyNs()) +
                        "</td><td>" + String.format(Locale.ROOT, "%.0f", r.p95LatencyNs()) +
                        "</td><td>" + String.format(Locale.ROOT, "%.0f", r.p99LatencyNs()) +
                        "</td><td>" + String.format(Locale.ROOT, "%.0f", r.p999LatencyNs()) +
                        "</td><td>" + String.valueOf(r.maxLatencyNs()) +
                        "</td></tr>\n");
            }
            writer.write("</table>\n");

            writer.write("<h2>Latency Distribution Chart</h2>\n");
            writer.write(buildLatencyChart(results));
            writer.write("<h2>Throughput &amp; IOPS Comparison Chart</h2>\n");
            writer.write(buildThroughputIopsChart(results));

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

    /**
     * Writes the block-size sweep report from one flat result list.
     *
     * <p>Results are grouped by run id. {@code BenchmarkRunner} restarts run numbering at 1 for
     * every block-size run, so a run number that does not advance marks the start of the next
     * block. Results from the threads of one run share the same run number and therefore stay
     * together.
     *
     * <p>A flat list carries no explicit block identity. If a block failed entirely, the remaining
     * groups cannot be located with certainty, so callers that collect results per block should use
     * {@link #writeSweepReport(List, Map)} instead. This overload remains for backward
     * compatibility and never throws due to uneven input.
     *
     * @param results flat result list in execution order
     * @param blockSizes requested block sizes in execution order
     * @throws IOException when the report cannot be written
     */
    @Override
    public void writeSweepReport(List<BenchmarkResult> results, List<Integer> blockSizes)
            throws IOException {
        writeSweepReport(blockSizes, groupResultsByBlockSize(results, blockSizes));
    }

    /**
     * Writes the block-size sweep report from results grouped explicitly by block size.
     *
     * <p>Each requested block size contributes at least one row: one data row per result when
     * results exist, otherwise a single {@code N/A} row. Columns therefore remain aligned when a
     * block size failed, produced no results, or is missing from the map entirely.
     *
     * @param blockSizes requested block sizes in execution order
     * @param resultsByBlockSize results keyed by block size; absent keys are treated as empty
     * @throws IOException when the report cannot be written
     */
    @Override
    public void writeSweepReport(List<Integer> blockSizes,
                                 Map<Integer, List<BenchmarkResult>> resultsByBlockSize)
            throws IOException {
        Path csvPath = paths.reportFilePath("sweep.csv");
        try (CSVWriter writer = new CSVWriter(
                new OutputStreamWriter(Files.newOutputStream(csvPath), StandardCharsets.UTF_8))) {

            writer.writeNext(new String[]{
                    "BlockSize", "ThroughputMBps", "IOPS", "AvgLatencyNs", "P50LatencyNs",
                    "P95LatencyNs", "P99LatencyNs", "P999LatencyNs", "MaxLatencyNs"
            });

            for (Integer blockSize : distinctBlockSizes(blockSizes)) {
                List<BenchmarkResult> blockResults = resultsByBlockSize == null
                        ? null
                        : resultsByBlockSize.get(blockSize);
                if (blockResults == null || blockResults.isEmpty()) {
                    writer.writeNext(missingBlockRow(blockSize));
                } else {
                    boolean wroteAny = false;
                    for (BenchmarkResult result : blockResults) {
                        if (result != null) {
                            writer.writeNext(sweepRow(blockSize, result));
                            wroteAny = true;
                        }
                    }
                    if (!wroteAny) {
                        writer.writeNext(missingBlockRow(blockSize));
                    }
                }
            }
            logger.info("Sweep report written to {}", csvPath);
        } catch (IOException e) {
            logger.error("Failed to write sweep report", e);
            throw e;
        }
    }

    /**
     * Returns the requested block sizes in order, without duplicates or nulls.
     *
     * @param blockSizes configured block sizes; may be null
     * @return distinct block sizes in execution order
     */
    private static List<Integer> distinctBlockSizes(List<Integer> blockSizes) {
        List<Integer> distinct = new ArrayList<>();
        if (blockSizes == null) {
            return distinct;
        }
        for (Integer blockSize : blockSizes) {
            if (blockSize != null && !distinct.contains(blockSize)) {
                distinct.add(blockSize);
            }
        }
        return distinct;
    }

    /**
     * Builds the CSV row used when a block size produced no benchmark results.
     *
     * @param blockSize block size that failed or was skipped
     * @return a full-width row of {@code N/A} values
     */
    private static String[] missingBlockRow(Integer blockSize) {
        return new String[]{
                String.valueOf(blockSize),
                "N/A", "N/A", "N/A", "N/A", "N/A", "N/A", "N/A", "N/A"
        };
    }

    /**
     * Builds one sweep CSV row, keeping the existing column order.
     *
     * @param blockSize block size associated with the result
     * @param result measured result
     * @return the formatted row
     */
    private static String[] sweepRow(Integer blockSize, BenchmarkResult result) {
        return new String[]{
                String.valueOf(blockSize),
                String.format(Locale.ROOT, "%.2f", result.throughputMBps()),
                String.format(Locale.ROOT, "%.2f", result.iops()),
                String.format(Locale.ROOT, "%.0f", result.avgLatencyNs()),
                String.format(Locale.ROOT, "%.0f", result.p50LatencyNs()),
                String.format(Locale.ROOT, "%.0f", result.p95LatencyNs()),
                String.format(Locale.ROOT, "%.0f", result.p99LatencyNs()),
                String.format(Locale.ROOT, "%.0f", result.p999LatencyNs()),
                String.valueOf(result.maxLatencyNs())
        };
    }

    /**
     * Groups a legacy flat result list into per-block buckets using run-id boundaries.
     *
     * <p>Within one block-size run, iteration numbers increase and thread numbers increase. A new
     * block therefore starts when the run number decreases, or when the run number stays the same
     * but the thread number does not increase (the next block restarts at thread 0). This works for
     * single-iteration sweeps as well as multi-iteration ones.
     *
     * <p>The first group maps to the first requested block size. If a flat list contains more run
     * groups than block sizes, extra results stay attached to the last block size rather than being
     * discarded; a warning records the inconsistency.
     *
     * @param results flat result list in execution order; may be null
     * @param blockSizes requested block sizes in execution order
     * @return ordered map with one bucket per requested block size
     */
    private static Map<Integer, List<BenchmarkResult>> groupResultsByBlockSize(
            List<BenchmarkResult> results, List<Integer> blockSizes) {
        List<Integer> orderedBlockSizes = distinctBlockSizes(blockSizes);
        Map<Integer, List<BenchmarkResult>> grouped = new LinkedHashMap<>();
        for (Integer blockSize : orderedBlockSizes) {
            grouped.put(blockSize, new ArrayList<>());
        }
        if (results == null || results.isEmpty() || orderedBlockSizes.isEmpty()) {
            return grouped;
        }

        int blockIndex = 0;
        int previousRunNumber = Integer.MIN_VALUE;
        int previousThreadNumber = Integer.MIN_VALUE;
        boolean overflowLogged = false;

        for (BenchmarkResult result : results) {
            if (result == null) {
                continue;
            }
            String runId = result.runId();
            int runNumber = parseRunNumber(runId);
            int threadNumber = parseThreadNumber(runId);
            boolean startsNewBlock = runNumber >= 0
                    && previousRunNumber != Integer.MIN_VALUE
                    && (runNumber < previousRunNumber
                        || (runNumber == previousRunNumber
                            && threadNumber <= previousThreadNumber));
            if (startsNewBlock) {
                blockIndex++;
            }
            if (runNumber >= 0) {
                previousRunNumber = runNumber;
                previousThreadNumber = threadNumber;
            }

            if (blockIndex >= orderedBlockSizes.size()) {
                if (!overflowLogged) {
                    logger.warn("Sweep results contain more run groups than block sizes; "
                            + "extra rows are attached to the last block size");
                    overflowLogged = true;
                }
                blockIndex = orderedBlockSizes.size() - 1;
            }
            grouped.get(orderedBlockSizes.get(blockIndex)).add(result);
        }
        return grouped;
    }

    /**
     * Extracts the numeric run counter from ids such as {@code run-003-thread-01}.
     *
     * @param runId result run id
     * @return parsed run number, or {@code -1} when the id is absent or unexpected
     */
    private static int parseRunNumber(String runId) {
        if (runId == null || !runId.startsWith("run-")) {
            return -1;
        }
        int start = "run-".length();
        int end = runId.indexOf('-', start);
        String digits = end < 0 ? runId.substring(start) : runId.substring(start, end);
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Extracts the thread counter from ids such as {@code run-003-thread-01}.
     *
     * @param runId result run id
     * @return parsed thread number, or {@code -1} when the id has no thread suffix
     */
    private static int parseThreadNumber(String runId) {
        if (runId == null) {
            return -1;
        }
        String marker = "-thread-";
        int index = runId.indexOf(marker);
        if (index < 0) {
            return -1;
        }
        String digits = runId.substring(index + marker.length());
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return -1;
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

    private static String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String buildLatencyChart(List<BenchmarkResult> results) {
        List<String> types = new ArrayList<>();
        for (BenchmarkResult r : results) {
            String t = r.testType() == null ? "" : r.testType();
            if (!types.contains(t)) {
                types.add(t);
            }
        }
        if (types.isEmpty()) {
            return "<p>No latency data available.</p>\n";
        }

        // Aggregate per test type.
        double[] p50 = new double[types.size()];
        double[] p95 = new double[types.size()];
        double[] p99 = new double[types.size()];
        double[] p999 = new double[types.size()];
        double[] max = new double[types.size()];
        int[] counts = new int[types.size()];
        for (BenchmarkResult r : results) {
            String t = r.testType() == null ? "" : r.testType();
            int idx = types.indexOf(t);
            p50[idx] += r.p50LatencyNs();
            p95[idx] += r.p95LatencyNs();
            p99[idx] += r.p99LatencyNs();
            p999[idx] += r.p999LatencyNs();
            max[idx] += r.maxLatencyNs();
            counts[idx]++;
        }
        double maxVal = 1.0;
        for (int i = 0; i < types.size(); i++) {
            if (counts[i] > 0) {
                p50[i] /= counts[i];
                p95[i] /= counts[i];
                p99[i] /= counts[i];
                p999[i] /= counts[i];
                max[i] /= counts[i];
            }
            maxVal = Math.max(maxVal, Math.max(p50[i], Math.max(p95[i],
                    Math.max(p99[i], Math.max(p999[i], max[i])))));
        }

        int width = 760;
        int height = 360;
        int marginL = 60;
        int marginB = 60;
        int marginT = 30;
        int marginR = 20;
        int plotW = width - marginL - marginR;
        int plotH = height - marginT - marginB;
        int groupW = plotW / types.size();
        int barW = Math.max(4, groupW / 6);

        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width)
                .append("\" height=\"").append(height).append("\" viewBox=\"0 0 ").append(width)
                .append(" ").append(height).append("\" role=\"img\" aria-label=\"Latency distribution chart\">\n");
        // axes
        svg.append("<line x1=\"").append(marginL).append("\" y1=\"").append(marginT)
                .append("\" x2=\"").append(marginL).append("\" y2=\"").append(height - marginB)
                .append("\" stroke=\"#333\"/>\n");
        svg.append("<line x1=\"").append(marginL).append("\" y1=\"").append(height - marginB)
                .append("\" x2=\"").append(width - marginR).append("\" y2=\"").append(height - marginB)
                .append("\" stroke=\"#333\"/>\n");

        String[] labels = {"P50", "P95", "P99", "P99.9", "Max"};
        String[] colors = {"#4CAF50", "#2196F3", "#FF9800", "#9C27B0", "#F44336"};
        double[] vals = new double[5];

        for (int i = 0; i < types.size(); i++) {
            vals[0] = p50[i]; vals[1] = p95[i]; vals[2] = p99[i]; vals[3] = p999[i]; vals[4] = max[i];
            int gx = marginL + i * groupW + groupW / 2;
            for (int j = 0; j < 5; j++) {
                int bh = (int) Math.round(vals[j] / maxVal * plotH);
                int x = gx - (5 * barW) / 2 + j * barW;
                int y = height - marginB - bh;
                svg.append("<rect x=\"").append(x).append("\" y=\"").append(y)
                        .append("\" width=\"").append(barW - 1).append("\" height=\"").append(bh)
                        .append("\" fill=\"").append(colors[j]).append("\"/>\n");
            }
            svg.append("<text x=\"").append(gx).append("\" y=\"").append(height - marginB + 15)
                    .append("\" text-anchor=\"middle\" font-size=\"11\">")
                    .append(escapeXml(types.get(i))).append("</text>\n");
        }

        // legend
        int lx = marginL;
        int ly = marginT - 15;
        for (int j = 0; j < 5; j++) {
            svg.append("<rect x=\"").append(lx + j * 120).append("\" y=\"").append(ly)
                    .append("\" width=\"12\" height=\"12\" fill=\"").append(colors[j]).append("\"/>\n");
            svg.append("<text x=\"").append(lx + j * 120 + 16).append("\" y=\"").append(ly + 11)
                    .append("\" font-size=\"11\">").append(labels[j]).append("</text>\n");
        }
        svg.append("<text x=\"").append(marginL - 10).append("\" y=\"").append(marginT - 5)
                .append("\" text-anchor=\"end\" font-size=\"10\">ns</text>\n");
        svg.append("</svg>\n");
        return svg.toString();
    }

    private String buildThroughputIopsChart(List<BenchmarkResult> results) {
        List<String> types = new ArrayList<>();
        for (BenchmarkResult r : results) {
            String t = r.testType() == null ? "" : r.testType();
            if (!types.contains(t)) {
                types.add(t);
            }
        }
        if (types.isEmpty()) {
            return "<p>No throughput data available.</p>\n";
        }

        double[] tRead = new double[types.size()];
        double[] tWrite = new double[types.size()];
        double[] tComb = new double[types.size()];
        double[] iRead = new double[types.size()];
        double[] iWrite = new double[types.size()];
        double[] iComb = new double[types.size()];
        int[] counts = new int[types.size()];
        for (BenchmarkResult r : results) {
            String t = r.testType() == null ? "" : r.testType();
            int idx = types.indexOf(t);
            tRead[idx] += r.readThroughputMBps();
            tWrite[idx] += r.writeThroughputMBps();
            tComb[idx] += r.throughputMBps();
            iRead[idx] += r.readIops();
            iWrite[idx] += r.writeIops();
            iComb[idx] += r.iops();
            counts[idx]++;
        }
        double maxT = 1.0;
        double maxI = 1.0;
        for (int i = 0; i < types.size(); i++) {
            if (counts[i] > 0) {
                tRead[i] /= counts[i];
                tWrite[i] /= counts[i];
                tComb[i] /= counts[i];
                iRead[i] /= counts[i];
                iWrite[i] /= counts[i];
                iComb[i] /= counts[i];
            }
            maxT = Math.max(maxT, Math.max(tRead[i], Math.max(tWrite[i], tComb[i])));
            maxI = Math.max(maxI, Math.max(iRead[i], Math.max(iWrite[i], iComb[i])));
        }

        int width = 760;
        int height = 420;
        int marginL = 60;
        int marginR = 20;
        int topH = 200;
        int botTop = 230;
        int botH = 160;
        int plotW = width - marginL - marginR;
        int groupW = plotW / types.size();
        int barW = Math.max(4, groupW / 4);

        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width)
                .append("\" height=\"").append(height).append("\" viewBox=\"0 0 ").append(width)
                .append(" ").append(height).append("\" role=\"img\" aria-label=\"Throughput and IOPS comparison chart\">\n");

        // Throughput panel
        svg.append("<text x=\"").append(marginL).append("\" y=\"15\" font-size=\"13\" font-weight=\"bold\">Throughput (MB/s)</text>\n");
        svg.append("<line x1=\"").append(marginL).append("\" y1=\"20\" x2=\"").append(marginL)
                .append("\" y2=\"").append(topH).append("\" stroke=\"#333\"/>\n");
        svg.append("<line x1=\"").append(marginL).append("\" y1=\"").append(topH)
                .append("\" x2=\"").append(width - marginR).append("\" y2=\"").append(topH)
                .append("\" stroke=\"#333\"/>\n");
        String[] tLabels = {"Read", "Write", "Combined"};
        String[] tColors = {"#4CAF50", "#F44336", "#2196F3"};
        double[] tVals = new double[3];
        for (int i = 0; i < types.size(); i++) {
            tVals[0] = tRead[i]; tVals[1] = tWrite[i]; tVals[2] = tComb[i];
            int gx = marginL + i * groupW + groupW / 2;
            for (int j = 0; j < 3; j++) {
                int bh = (int) Math.round(tVals[j] / maxT * (topH - 25));
                int x = gx - (3 * barW) / 2 + j * barW;
                int y = topH - bh;
                svg.append("<rect x=\"").append(x).append("\" y=\"").append(y)
                        .append("\" width=\"").append(barW - 1).append("\" height=\"").append(bh)
                        .append("\" fill=\"").append(tColors[j]).append("\"/>\n");
            }
            svg.append("<text x=\"").append(gx).append("\" y=\"").append(topH + 14)
                    .append("\" text-anchor=\"middle\" font-size=\"10\">")
                    .append(escapeXml(types.get(i))).append("</text>\n");
        }

        // IOPS panel
        svg.append("<text x=\"").append(marginL).append("\" y=\"").append(botTop - 5)
                .append("\" font-size=\"13\" font-weight=\"bold\">IOPS</text>\n");
        svg.append("<line x1=\"").append(marginL).append("\" y1=\"").append(botTop)
                .append("\" x2=\"").append(marginL).append("\" y2=\"").append(botTop + botH)
                .append("\" stroke=\"#333\"/>\n");
        svg.append("<line x1=\"").append(marginL).append("\" y1=\"").append(botTop + botH)
                .append("\" x2=\"").append(width - marginR).append("\" y2=\"").append(botTop + botH)
                .append("\" stroke=\"#333\"/>\n");
        double[] iVals = new double[3];
        for (int i = 0; i < types.size(); i++) {
            iVals[0] = iRead[i]; iVals[1] = iWrite[i]; iVals[2] = iComb[i];
            int gx = marginL + i * groupW + groupW / 2;
            for (int j = 0; j < 3; j++) {
                int bh = (int) Math.round(iVals[j] / maxI * (botH - 10));
                int x = gx - (3 * barW) / 2 + j * barW;
                int y = botTop + botH - bh;
                svg.append("<rect x=\"").append(x).append("\" y=\"").append(y)
                        .append("\" width=\"").append(barW - 1).append("\" height=\"").append(bh)
                        .append("\" fill=\"").append(tColors[j]).append("\"/>\n");
            }
            svg.append("<text x=\"").append(gx).append("\" y=\"").append(botTop + botH + 14)
                    .append("\" text-anchor=\"middle\" font-size=\"10\">")
                    .append(escapeXml(types.get(i))).append("</text>\n");
        }

        // legend
        int ly = height - 10;
        for (int j = 0; j < 3; j++) {
            svg.append("<rect x=\"").append(marginL + j * 120).append("\" y=\"").append(ly - 10)
                    .append("\" width=\"12\" height=\"12\" fill=\"").append(tColors[j]).append("\"/>\n");
            svg.append("<text x=\"").append(marginL + j * 120 + 16).append("\" y=\"").append(ly)
                    .append("\" font-size=\"11\">").append(tLabels[j]).append("</text>\n");
        }
        svg.append("</svg>\n");
        return svg.toString();
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
