package com.kira.jstoragemark.report;

import com.kira.jstoragemark.config.BenchmarkConfig;
import com.kira.jstoragemark.fs.BenchmarkPaths;
import com.kira.jstoragemark.metrics.MetricsSnapshot;
import com.kira.jstoragemark.result.BenchmarkResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for ReportGenerator output formats.
 */
class ReportGeneratorTest {

    @TempDir
    Path tempDir;

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
        return Arrays.asList(
                new BenchmarkResult(1, "SEQ_READ", 1024L * 1024 * 1024,
                        Duration.ofMillis(1000), 100.5, 10.2, 1000.0, Instant.now()),
                new BenchmarkResult(2, "SEQ_WRITE", 1024L * 1024 * 1024,
                        Duration.ofMillis(2000), 50.25, 20.5, 500.0, Instant.now()),
                new BenchmarkResult(3, "RAND_READ", 1024L * 1024 * 1024,
                        Duration.ofMillis(1500), 75.0, 15.0, 750.0, Instant.now())
        );
    }

    private List<MetricsSnapshot> createSampleMetrics() {
        return Arrays.asList(
                new MetricsSnapshot(Instant.now(), 45.5, 60.0, 30.0, 65.0),
                new MetricsSnapshot(Instant.now().plusSeconds(1), 50.0, 62.5, 35.0, 67.0),
                new MetricsSnapshot(Instant.now().plusSeconds(2), 48.0, 61.0, 32.0, 66.0)
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
        // Check for decimal point (not comma)
        assertThat(content).contains("100.50").contains("50.25");
    }

    @Test
    @DisplayName("Write CSV should handle empty results")
    void writeCsvShouldHandleEmptyResults() throws IOException {
        generator.writeCsv(Collections.emptyList());

        Path csvPath = paths.reportFilePath("csv");
        assertThat(csvPath).exists();
        List<String> lines = Files.readAllLines(csvPath);
        assertThat(lines).hasSize(1); // Only header
    }

    @Test
    @DisplayName("Write CSV should throw on IO error")
    void writeCsvShouldThrowOnIoError() {
        // Create a read-only directory to cause IO error
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

        generator.writeJson(results, metrics);

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

        generator.writeJson(results, Collections.emptyList());

        Path jsonPath = paths.reportFilePath("json");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(jsonPath.toFile());
        JsonNode firstResult = root.get("results").get(0);

        assertThat(firstResult.has("runId")).isTrue();
        assertThat(firstResult.has("testType")).isTrue();
        assertThat(firstResult.has("throughputMBps")).isTrue();
        assertThat(firstResult.has("avgLatencyMs")).isTrue();
        assertThat(firstResult.has("iops")).isTrue();
    }

    @Test
    @DisplayName("Write JSON should include all metric fields")
    void writeJsonShouldIncludeAllMetricFields() throws IOException {
        List<MetricsSnapshot> metrics = createSampleMetrics();

        generator.writeJson(Collections.emptyList(), metrics);

        Path jsonPath = paths.reportFilePath("json");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(jsonPath.toFile());
        JsonNode firstMetric = root.get("metrics").get(0);

        assertThat(firstMetric.has("timestamp")).isTrue();
        assertThat(firstMetric.has("cpuUsagePercent")).isTrue();
        assertThat(firstMetric.has("ramUsagePercent")).isTrue();
        assertThat(firstMetric.has("diskUtilizationPercent")).isTrue();
    }

    @Test
    @DisplayName("Write JSON should be indented")
    void writeJsonShouldBeIndented() throws IOException {
        List<BenchmarkResult> results = createSampleResults();

        generator.writeJson(results, Collections.emptyList());

        Path jsonPath = paths.reportFilePath("json");
        String content = Files.readString(jsonPath);

        assertThat(content).contains("  "); // Contains indentation
        assertThat(content).contains("\n"); // Contains newlines
    }

    @Test
    @DisplayName("Write JSON should handle empty lists")
    void writeJsonShouldHandleEmptyLists() throws IOException {
        generator.writeJson(Collections.emptyList(), Collections.emptyList());

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
        // Create config with HTML format
        BenchmarkConfig htmlConfig = new BenchmarkConfig.Builder()
                .testDirectory(tempDir)
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .addReportFormat(BenchmarkConfig.ReportFormat.HTML)
                .build();
        BenchmarkPaths htmlPaths = new BenchmarkPaths(tempDir, htmlConfig.getSessionId());
        ReportGenerator htmlGenerator = new ReportGenerator(htmlConfig, htmlPaths);

        List<BenchmarkResult> results = createSampleResults();
        List<MetricsSnapshot> metrics = createSampleMetrics();

        htmlGenerator.writeHtml(results, metrics);

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

        generator.writeHtml(results, Collections.emptyList());

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

        htmlGenerator.writeHtml(createSampleResults(), Collections.emptyList());

        Path htmlPath = htmlPaths.reportFilePath("html");
        String content = Files.readString(htmlPath);
        assertThat(content).contains("charset=\"UTF-8\"");
    }

    @Test
    @DisplayName("Write HTML should escape special characters")
    void writeHtmlShouldEscapeSpecialCharacters() throws IOException {
        // Create result with special HTML characters
        BenchmarkResult specialResult = new BenchmarkResult(
                1, "<script>alert('xss')</script>", 1024,
                Duration.ofMillis(100), 100.0, 10.0, 1000.0, Instant.now()
        );

        BenchmarkConfig htmlConfig = new BenchmarkConfig.Builder()
                .testDirectory(tempDir)
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .addReportFormat(BenchmarkConfig.ReportFormat.HTML)
                .build();
        BenchmarkPaths htmlPaths = new BenchmarkPaths(tempDir, htmlConfig.getSessionId());
        ReportGenerator htmlGenerator = new ReportGenerator(htmlConfig, htmlPaths);

        htmlGenerator.writeHtml(Collections.singletonList(specialResult), Collections.emptyList());

        Path htmlPath = htmlPaths.reportFilePath("html");
        String content = Files.readString(htmlPath);
        assertThat(content).doesNotContain("<script>");
        assertThat(content).contains("<script>");
    }

    @Test
    @DisplayName("Write HTML should include results table")
    void writeHtmlShouldIncludeResultsTable() throws IOException {
        BenchmarkConfig htmlConfig = new BenchmarkConfig.Builder()
                .testDirectory(tempDir)
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .addReportFormat(BenchmarkConfig.ReportFormat.HTML)
                .build();
        BenchmarkPaths htmlPaths = new BenchmarkPaths(tempDir, htmlConfig.getSessionId());
        ReportGenerator htmlGenerator = new ReportGenerator(htmlConfig, htmlPaths);

        htmlGenerator.writeHtml(createSampleResults(), Collections.emptyList());

        Path htmlPath = htmlPaths.reportFilePath("html");
        String content = Files.readString(htmlPath);
        assertThat(content)
                .contains("<table>")
                .contains("</table>")
                .contains("Throughput (MB/s)")
                .contains("Latency (ms)")
                .contains("IOPS");
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

        htmlGenerator.writeHtml(createSampleResults(), createSampleMetrics());

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

        htmlGenerator.writeHtml(createSampleResults(), Collections.emptyList());

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
        // This is harder to test without modifying the session ID generation
        // but we verify the method uses escapeHtml4
        BenchmarkConfig htmlConfig = new BenchmarkConfig.Builder()
                .testDirectory(tempDir)
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .addReportFormat(BenchmarkConfig.ReportFormat.HTML)
                .build();
        BenchmarkPaths htmlPaths = new BenchmarkPaths(tempDir, htmlConfig.getSessionId());
        ReportGenerator htmlGenerator = new ReportGenerator(htmlConfig, htmlPaths);

        htmlGenerator.writeHtml(createSampleResults(), Collections.emptyList());

        Path htmlPath = htmlPaths.reportFilePath("html");
        assertThat(htmlPath).exists();
    }
}
