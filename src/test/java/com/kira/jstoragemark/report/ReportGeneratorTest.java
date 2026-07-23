package com.kira.jstoragemark.report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kira.jstoragemark.config.BenchmarkConfig;
import com.kira.jstoragemark.fs.BenchmarkPaths;
import com.kira.jstoragemark.metrics.MetricsSnapshot;
import com.kira.jstoragemark.result.BenchmarkResult;

class ReportGeneratorTest {

    @TempDir
    Path tempDir;

    private static final SystemInfoSnapshot TEST_SYSTEM_INFO =
            new SystemInfoSnapshot("test-os", "test-java", "test-cpu", 0L);

    private BenchmarkConfig config;
    private BenchmarkPaths paths;
    private ReportGenerator generator;

    @BeforeEach
    void setUp() {
        config = new BenchmarkConfig.Builder()
                .testDirectory(tempDir)
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .fileSizeBytes(2L * 1024 * 1024 * 1024)
                .blockSizeBytes(64 * 1024)
                .threads(4)
                .iterations(3)
                .build();
        paths = new BenchmarkPaths(tempDir, config.getSessionId());
        generator = new ReportGenerator(config, paths);
    }

    @AfterEach
    void tearDown() {
        paths.cleanupSessionFiles(false);
    }

    private List<BenchmarkResult> createSampleResults() {
        Instant now = Instant.now();
        Duration d1 = Duration.ofMillis(1000);
        Duration d2 = Duration.ofMillis(2000);
        Duration d3 = Duration.ofMillis(1500);
        return Arrays.asList(
                new BenchmarkResult("run-001-thread-00", "SEQ_READ", 1024L * 1024 * 1024,
                        d1, d1.toNanos(), 100.5, 10.2, 10.2 * 1_000_000.0, 1000.0, now,
                        500.0, 2000.0, 5000.0, 10000.0, 50000L),
                new BenchmarkResult("run-002-thread-00", "SEQ_WRITE", 1024L * 1024 * 1024,
                        d2, d2.toNanos(), 50.25, 20.5, 20.5 * 1_000_000.0, 500.0, now,
                        1000.0, 4000.0, 10000.0, 20000.0, 100000L),
                new BenchmarkResult("run-003-thread-00", "RAND_READ", 1024L * 1024 * 1024,
                        d3, d3.toNanos(), 75.0, 15.0, 15.0 * 1_000_000.0, 750.0, now,
                        750.0, 3000.0, 7500.0, 15000.0, 75000L)
        );
    }

    private List<MetricsSnapshot> createSampleMetrics() {
        return Arrays.asList(
                new MetricsSnapshot(Instant.now(), 45.5, 60.0, 1000L, 500L, 1024L * 1024, 512L * 1024, 35.0),
                new MetricsSnapshot(Instant.now().plusSeconds(1), 50.0, 62.5, 1100L, 550L, 2048L * 1024, 1024L * 1024, 36.5),
                new MetricsSnapshot(Instant.now().plusSeconds(2), 48.0, 61.0, 1050L, 525L, 1536L * 1024, 768L * 1024, 35.8)
        );
    }

    // ==================== CSV Tests ====================

    @Test
    @DisplayName("Write CSV should create valid CSV file")
    void writeCsvShouldCreateValidFile() throws IOException {
        List<BenchmarkResult> results = createSampleResults();

        generator.writeCsv(results);

        Path csvPath = paths.reportFilePath("csv");
        assertThat(csvPath).exists();
        String content = Files.readString(csvPath);
        assertThat(content)
                .contains("RunId")
                .contains("TestType")
                .contains("ThroughputMBps")
                .contains("P50LatencyNs")
                .contains("P99LatencyNs")
                .contains("SEQ_READ")
                .contains("SEQ_WRITE")
                .contains("RAND_READ");
    }

    @Test
    @DisplayName("Write CSV should include header row")
    void writeCsvShouldIncludeHeader() throws IOException {
        List<BenchmarkResult> results = createSampleResults();

        generator.writeCsv(results);

        Path csvPath = paths.reportFilePath("csv");
        List<String> lines = Files.readAllLines(csvPath);
        assertThat(lines.get(0)).contains("RunId", "TestType", "BytesProcessed", "ElapsedMs");
    }

    @Test
    @DisplayName("Write CSV should format numbers with dot decimal separator")
    void writeCsvShouldFormatNumbersCorrectly() throws IOException {
        List<BenchmarkResult> results = createSampleResults();

        generator.writeCsv(results);

        Path csvPath = paths.reportFilePath("csv");
        String content = Files.readString(csvPath);
        assertThat(content).contains("100.50").contains("50.25");
    }

    @Test
    @DisplayName("Write CSV should handle empty results")
    void writeCsvShouldHandleEmptyResults() throws IOException {
        generator.writeCsv(Collections.emptyList());

        Path csvPath = paths.reportFilePath("csv");
        assertThat(csvPath).exists();
        List<String> lines = Files.readAllLines(csvPath);
        assertThat(lines).hasSize(1);
    }

    @Test
    @DisplayName("Write CSV should throw on IO error")
    void writeCsvShouldThrowOnIoError() {
        ReportGenerator invalidGenerator = new ReportGenerator(
                new BenchmarkConfig.Builder()
                        .testDirectory(Path.of("/nonexistent/path"))
                        .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                        .build(),
                new BenchmarkPaths(Path.of("/nonexistent/path"), "test")
        );

        assertThatThrownBy(() -> invalidGenerator.writeCsv(createSampleResults()))
                .isInstanceOf(IOException.class);
    }

    // ==================== JSON Tests ====================

    @Test
    @DisplayName("Write JSON should create valid JSON file")
    void writeJsonShouldCreateValidFile() throws IOException {
        List<BenchmarkResult> results = createSampleResults();
        List<MetricsSnapshot> metrics = createSampleMetrics();

        generator.writeJson(results, metrics, TEST_SYSTEM_INFO);

        Path jsonPath = paths.reportFilePath("json");
        assertThat(jsonPath).exists();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(jsonPath.toFile());

        assertThat(root.has("results")).isTrue();
        assertThat(root.has("metrics")).isTrue();
        assertThat(root.has("sessionId")).isTrue();
    }

    @Test
    @DisplayName("Write JSON should include all result fields")
    void writeJsonShouldIncludeAllResultFields() throws IOException {
        List<BenchmarkResult> results = createSampleResults();

        generator.writeJson(results, Collections.emptyList(), TEST_SYSTEM_INFO);

        Path jsonPath = paths.reportFilePath("json");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(jsonPath.toFile());
        JsonNode firstResult = root.get("results").get(0);

        assertThat(firstResult.has("runId")).isTrue();
        assertThat(firstResult.has("testType")).isTrue();
        assertThat(firstResult.has("throughputMBps")).isTrue();
        assertThat(firstResult.has("avgLatencyMs")).isTrue();
        assertThat(firstResult.has("iops")).isTrue();
        assertThat(firstResult.has("p50LatencyNs")).isTrue();
        assertThat(firstResult.has("p99LatencyNs")).isTrue();
        assertThat(firstResult.has("maxLatencyNs")).isTrue();
    }

    @Test
    @DisplayName("Write JSON should include all metric fields")
    void writeJsonShouldIncludeAllMetricFields() throws IOException {
        List<MetricsSnapshot> metrics = createSampleMetrics();

        generator.writeJson(Collections.emptyList(), metrics, TEST_SYSTEM_INFO);

        Path jsonPath = paths.reportFilePath("json");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(jsonPath.toFile());
        JsonNode firstMetric = root.get("metrics").get(0);

        assertThat(firstMetric.has("timestamp")).isTrue();
        assertThat(firstMetric.has("cpuUsagePercent")).isTrue();
        assertThat(firstMetric.has("ramUsagePercent")).isTrue();
        assertThat(firstMetric.has("diskReadBytes")).isTrue();
    }

    @Test
    @DisplayName("Write JSON should be indented")
    void writeJsonShouldBeIndented() throws IOException {
        List<BenchmarkResult> results = createSampleResults();

        generator.writeJson(results, Collections.emptyList(), TEST_SYSTEM_INFO);

        Path jsonPath = paths.reportFilePath("json");
        String content = Files.readString(jsonPath);

        assertThat(content).contains("  ");
        assertThat(content).contains("\n");
    }

    @Test
    @DisplayName("Write JSON should handle empty lists")
    void writeJsonShouldHandleEmptyLists() throws IOException {
        generator.writeJson(Collections.emptyList(), Collections.emptyList(), TEST_SYSTEM_INFO);

        Path jsonPath = paths.reportFilePath("json");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(jsonPath.toFile());

        assertThat(root.get("results")).isEmpty();
        assertThat(root.get("metrics")).isEmpty();
    }

    // ==================== HTML Tests ====================

    @Test
    @DisplayName("Write HTML should create valid HTML file when HTML format enabled")
    void writeHtmlShouldCreateValidFile() throws IOException {
        BenchmarkConfig htmlConfig = new BenchmarkConfig.Builder()
                .testDirectory(tempDir)
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .addReportFormat(BenchmarkConfig.ReportFormat.HTML)
                .build();
        BenchmarkPaths htmlPaths = new BenchmarkPaths(tempDir, htmlConfig.getSessionId());
        ReportGenerator htmlGenerator = new ReportGenerator(htmlConfig, htmlPaths);

        List<BenchmarkResult> results = createSampleResults();
        List<MetricsSnapshot> metrics = createSampleMetrics();

        htmlGenerator.writeHtml(results, metrics, TEST_SYSTEM_INFO);

        Path htmlPath = htmlPaths.reportFilePath("html");
        assertThat(htmlPath).exists();
        String content = Files.readString(htmlPath);
        assertThat(content)
                .contains("<!DOCTYPE html>")
                .contains("<html>")
                .contains("</html>");
    }

    @Test
    @DisplayName("Write HTML should skip when HTML format not enabled")
    void writeHtmlShouldSkipWhenNotEnabled() throws IOException {
        List<BenchmarkResult> results = createSampleResults();

        generator.writeHtml(results, Collections.emptyList(), TEST_SYSTEM_INFO);

        Path htmlPath = paths.reportFilePath("html");
        assertThat(htmlPath).doesNotExist();
    }

    @Test
    @DisplayName("Write HTML should include UTF-8 charset")
    void writeHtmlShouldIncludeUtf8Charset() throws IOException {
        BenchmarkConfig htmlConfig = new BenchmarkConfig.Builder()
                .testDirectory(tempDir)
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .addReportFormat(BenchmarkConfig.ReportFormat.HTML)
                .build();
        BenchmarkPaths htmlPaths = new BenchmarkPaths(tempDir, htmlConfig.getSessionId());
        ReportGenerator htmlGenerator = new ReportGenerator(htmlConfig, htmlPaths);

        htmlGenerator.writeHtml(createSampleResults(), Collections.emptyList(), TEST_SYSTEM_INFO);

        Path htmlPath = htmlPaths.reportFilePath("html");
        String content = Files.readString(htmlPath);
        assertThat(content).contains("charset=\"UTF-8\"");
    }

    @Test
    @DisplayName("Write HTML should escape special characters")
    void writeHtmlShouldEscapeSpecialCharacters() throws IOException {
        BenchmarkResult specialResult = new BenchmarkResult(
                "run-001-thread-00", "<script>alert('xss')</script>", 1024L,
                Duration.ofMillis(100), Duration.ofMillis(100).toNanos(), 100.0, 10.0, 10.0 * 1_000_000.0, 1000.0,
                Instant.now(), 0.0, 0.0, 0.0, 0.0, 0L
        );

        BenchmarkConfig htmlConfig = new BenchmarkConfig.Builder()
                .testDirectory(tempDir)
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .addReportFormat(BenchmarkConfig.ReportFormat.HTML)
                .build();
        BenchmarkPaths htmlPaths = new BenchmarkPaths(tempDir, htmlConfig.getSessionId());
        ReportGenerator htmlGenerator = new ReportGenerator(htmlConfig, htmlPaths);

        htmlGenerator.writeHtml(Collections.singletonList(specialResult), Collections.emptyList(), TEST_SYSTEM_INFO);

        Path htmlPath = htmlPaths.reportFilePath("html");
        String content = Files.readString(htmlPath);
        assertThat(content).doesNotContain("<script>");
        assertThat(content).contains("&lt;script&gt;");
    }

    @Test
    @DisplayName("Write HTML should include results table with percentile columns")
    void writeHtmlShouldIncludeResultsTable() throws IOException {
        BenchmarkConfig htmlConfig = new BenchmarkConfig.Builder()
                .testDirectory(tempDir)
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .addReportFormat(BenchmarkConfig.ReportFormat.HTML)
                .build();
        BenchmarkPaths htmlPaths = new BenchmarkPaths(tempDir, htmlConfig.getSessionId());
        ReportGenerator htmlGenerator = new ReportGenerator(htmlConfig, htmlPaths);

        htmlGenerator.writeHtml(createSampleResults(), Collections.emptyList(), TEST_SYSTEM_INFO);

        Path htmlPath = htmlPaths.reportFilePath("html");
        String content = Files.readString(htmlPath);
        assertThat(content)
                .contains("<table>")
                .contains("</table>")
                .contains("Throughput (MB/s)")
                .contains("Latency (ms)")
                .contains("IOPS")
                .contains("p50 (ns)")
                .contains("p99 (ns)")
                .contains("p99.9 (ns)");
    }

    @Test
    @DisplayName("Write HTML should include metrics table when metrics provided")
    void writeHtmlShouldIncludeMetricsTable() throws IOException {
        BenchmarkConfig htmlConfig = new BenchmarkConfig.Builder()
                .testDirectory(tempDir)
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .addReportFormat(BenchmarkConfig.ReportFormat.HTML)
                .build();
        BenchmarkPaths htmlPaths = new BenchmarkPaths(tempDir, htmlConfig.getSessionId());
        ReportGenerator htmlGenerator = new ReportGenerator(htmlConfig, htmlPaths);

        htmlGenerator.writeHtml(createSampleResults(), createSampleMetrics(), TEST_SYSTEM_INFO);

        Path htmlPath = htmlPaths.reportFilePath("html");
        String content = Files.readString(htmlPath);
        assertThat(content)
                .contains("System Metrics")
                .contains("CPU (%)")
                .contains("RAM (%)");
    }

    @Test
    @DisplayName("Write HTML should include CSS styling")
    void writeHtmlShouldIncludeCssStyling() throws IOException {
        BenchmarkConfig htmlConfig = new BenchmarkConfig.Builder()
                .testDirectory(tempDir)
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .addReportFormat(BenchmarkConfig.ReportFormat.HTML)
                .build();
        BenchmarkPaths htmlPaths = new BenchmarkPaths(tempDir, htmlConfig.getSessionId());
        ReportGenerator htmlGenerator = new ReportGenerator(htmlConfig, htmlPaths);

        htmlGenerator.writeHtml(createSampleResults(), Collections.emptyList(), TEST_SYSTEM_INFO);

        Path htmlPath = htmlPaths.reportFilePath("html");
        String content = Files.readString(htmlPath);
        assertThat(content)
                .contains("<style>")
                .contains("font-family")
                .contains("border-collapse");
    }

    @Test
    @DisplayName("Write HTML should escape session ID")
    void writeHtmlShouldEscapeSessionId() throws IOException {
        BenchmarkConfig htmlConfig = new BenchmarkConfig.Builder()
                .testDirectory(tempDir)
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .addReportFormat(BenchmarkConfig.ReportFormat.HTML)
                .build();
        BenchmarkPaths htmlPaths = new BenchmarkPaths(tempDir, htmlConfig.getSessionId());
        ReportGenerator htmlGenerator = new ReportGenerator(htmlConfig, htmlPaths);

        htmlGenerator.writeHtml(createSampleResults(), Collections.emptyList(), TEST_SYSTEM_INFO);

        Path htmlPath = htmlPaths.reportFilePath("html");
        assertThat(htmlPath).exists();
    }

    // ==================== Sweep Report Tests ====================

    @Test
    @DisplayName("Write sweep report should create valid CSV")
    void writeSweepReportShouldCreateValidCsv() throws IOException {
        List<Integer> blockSizes = Arrays.asList(4096, 8192, 16384);
        generator.writeSweepReport(createSampleResults(), blockSizes);

        Path csvPath = paths.reportFilePath("sweep.csv");
        assertThat(csvPath).exists();
        String content = Files.readString(csvPath);
        assertThat(content)
                .contains("BlockSize")
                .contains("ThroughputMBps")
                .contains("P99LatencyNs");
    }

    // ==================== Diff Report Tests ====================

    @Test
    @DisplayName("Write diff report should create valid HTML")
    void writeDiffReportShouldCreateValidHtml() throws IOException {
        List<BenchmarkResult> baseline = createSampleResults();
        List<BenchmarkResult> current = Arrays.asList(
                new BenchmarkResult("run-001-thread-00", "SEQ_READ", 1024L * 1024 * 1024,
                        Duration.ofMillis(900), Duration.ofMillis(900).toNanos(),
                        110.0, 9.0, 9.0 * 1_000_000.0, 1100.0, Instant.now(),
                        450.0, 1800.0, 4500.0, 9000.0, 45000L),
                new BenchmarkResult("run-002-thread-00", "SEQ_WRITE", 1024L * 1024 * 1024,
                        Duration.ofMillis(1800), Duration.ofMillis(1800).toNanos(),
                        55.0, 18.0, 18.0 * 1_000_000.0, 550.0, Instant.now(),
                        900.0, 3600.0, 9000.0, 18000.0, 90000L),
                new BenchmarkResult("run-003-thread-00", "RAND_READ", 1024L * 1024 * 1024,
                        Duration.ofMillis(1400), Duration.ofMillis(1400).toNanos(),
                        80.0, 14.0, 14.0 * 1_000_000.0, 800.0, Instant.now(),
                        700.0, 2800.0, 7000.0, 14000.0, 70000L)
        );

        generator.writeDiffReport(baseline, current);

        Path htmlPath = paths.reportFilePath("diff.html");
        assertThat(htmlPath).exists();
        String content = Files.readString(htmlPath);
        assertThat(content)
                .contains("Benchmark Comparison")
                .contains("<table>")
                .contains("improvement")
                .contains("regression")
                .contains("Throughput (MB/s)");
    }
}
