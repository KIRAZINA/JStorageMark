package com.kira.jstoragemark.report;

import com.kira.jstoragemark.config.BenchmarkConfig;
import com.kira.jstoragemark.fs.BenchmarkPaths;
import com.kira.jstoragemark.result.BenchmarkResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kira.jstoragemark.result.BenchmarkResult;

import static org.assertj.core.api.Assertions.*;

class DiffReportTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Diff report should show positive change as improvement")
    void diffReportShowsImprovement() throws IOException {
        BenchmarkConfig config = new BenchmarkConfig.Builder()
                .testDirectory(tempDir)
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .build();
        BenchmarkPaths paths = new BenchmarkPaths(tempDir, config.getSessionId());
        ReportGenerator generator = new ReportGenerator(config, paths);

        Instant now = Instant.now();
        List<BenchmarkResult> baseline = Arrays.asList(
                new BenchmarkResult("run-001", "SEQ_READ", 1024L, Duration.ofSeconds(1),
                        Duration.ofSeconds(1).toNanos(), 100.0, 10.0, 10_000_000.0, 1000.0, now,
                        5000.0, 20000.0, 50000.0, 100000.0, 200000L)
        );
        List<BenchmarkResult> current = Arrays.asList(
                new BenchmarkResult("run-001", "SEQ_READ", 1024L, Duration.ofSeconds(1),
                        Duration.ofSeconds(1).toNanos(), 120.0, 8.0, 8_000_000.0, 1200.0, now,
                        4000.0, 16000.0, 40000.0, 80000.0, 160000L)
        );

        generator.writeDiffReport(baseline, current);

        Path htmlPath = paths.reportFilePath("diff.html");
        assertThat(htmlPath).exists();
        String content = Files.readString(htmlPath);
        assertThat(content)
                .contains("improvement")
                .contains("+20.0%");
    }

    @Test
    @DisplayName("Diff report should show negative change as regression")
    void diffReportShowsRegression() throws IOException {
        BenchmarkConfig config = new BenchmarkConfig.Builder()
                .testDirectory(tempDir)
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .build();
        BenchmarkPaths paths = new BenchmarkPaths(tempDir, config.getSessionId());
        ReportGenerator generator = new ReportGenerator(config, paths);

        Instant now = Instant.now();
        List<BenchmarkResult> baseline = Arrays.asList(
                new BenchmarkResult("run-001", "SEQ_READ", 1024L, Duration.ofSeconds(1),
                        Duration.ofSeconds(1).toNanos(), 100.0, 10.0, 10_000_000.0, 1000.0, now,
                        5000.0, 20000.0, 50000.0, 100000.0, 200000L)
        );
        List<BenchmarkResult> current = Arrays.asList(
                new BenchmarkResult("run-001", "SEQ_READ", 1024L, Duration.ofSeconds(1),
                        Duration.ofSeconds(1).toNanos(), 80.0, 12.5, 12_500_000.0, 800.0, now,
                        6000.0, 24000.0, 60000.0, 120000.0, 240000L)
        );

        generator.writeDiffReport(baseline, current);

        Path htmlPath = paths.reportFilePath("diff.html");
        assertThat(htmlPath).exists();
        String content = Files.readString(htmlPath);
        assertThat(content)
                .contains("regression")
                .contains("-20.0%");
    }

    @Test
    @DisplayName("Diff report should handle empty baseline gracefully")
    void diffReportHandlesEmptyBaseline() throws IOException {
        BenchmarkConfig config = new BenchmarkConfig.Builder()
                .testDirectory(tempDir)
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .build();
        BenchmarkPaths paths = new BenchmarkPaths(tempDir, config.getSessionId());
        ReportGenerator generator = new ReportGenerator(config, paths);

        Instant now = Instant.now();
        List<BenchmarkResult> current = Arrays.asList(
                new BenchmarkResult("run-001", "SEQ_READ", 1024L, Duration.ofSeconds(1),
                        Duration.ofSeconds(1).toNanos(), 100.0, 10.0, 10_000_000.0, 1000.0, now,
                        5000.0, 20000.0, 50000.0, 100000.0, 200000L)
        );

        generator.writeDiffReport(List.of(), current);

        Path htmlPath = paths.reportFilePath("diff.html");
        assertThat(htmlPath).exists();
    }

    @Test
    @DisplayName("LoadBaseline should parse valid JSON")
    void loadBaselineShouldParseValidJson() throws IOException {
        // Write a valid baseline JSON
        Path jsonPath = tempDir.resolve("baseline.json");
        String json = """
                {
                    "results": [{
                        "runId": "run-001",
                        "testType": "SEQ_READ",
                        "bytesProcessed": 1024,
                        "elapsed": "PT1S",
                        "elapsedNanos": 1000000000,
                        "throughputMBps": 100.0,
                        "avgLatencyMs": 10.0,
                        "avgLatencyNs": 10000000.0,
                        "iops": 1000.0,
                        "timestamp": "2024-01-15T10:30:00Z",
                        "p50LatencyNs": 5000.0,
                        "p95LatencyNs": 20000.0,
                        "p99LatencyNs": 50000.0,
                        "p999LatencyNs": 100000.0,
                        "maxLatencyNs": 200000
                    }],
                    "metrics": [],
                    "sessionId": "test-session"
                }
                """;
        Files.writeString(jsonPath, json);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        var jsonNode = mapper.readTree(jsonPath.toFile());
        var resultsNode = jsonNode.get("results");
        List<BenchmarkResult> parsed = new java.util.ArrayList<>();
        for (var node : resultsNode) {
            parsed.add(mapper.treeToValue(node, BenchmarkResult.class));
        }
        assertThat(parsed).hasSize(1);
        assertThat(parsed.get(0).runId()).isEqualTo("run-001");
        assertThat(parsed.get(0).throughputMBps()).isEqualTo(100.0);
        assertThat(parsed.get(0).p99LatencyNs()).isEqualTo(50000.0);
    }
}
