package com.kira.jstoragemark.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class MainUtilsTest {

    @TempDir
    Path tempDir;

    // ==================== formatBytes ====================

    @Test
    @DisplayName("formatBytes should return 0 for zero bytes")
    void formatBytesShouldReturnZero() {
        assertThat(Main.formatBytes(0)).isEqualTo("0");
    }

    @Test
    @DisplayName("formatBytes should return 0 for negative bytes")
    void formatBytesShouldReturnZeroForNegative() {
        assertThat(Main.formatBytes(-1)).isEqualTo("0");
    }

    @Test
    @DisplayName("formatBytes should format bytes in B")
    void formatBytesShouldFormatBytes() {
        assertThat(Main.formatBytes(500)).isEqualTo("500.00 B");
    }

    @Test
    @DisplayName("formatBytes should format bytes in KB")
    void formatBytesShouldFormatKB() {
        assertThat(Main.formatBytes(2048)).isEqualTo("2.00 KB");
    }

    @Test
    @DisplayName("formatBytes should format bytes in MB")
    void formatBytesShouldFormatMB() {
        assertThat(Main.formatBytes(5L * 1024 * 1024)).isEqualTo("5.00 MB");
    }

    @Test
    @DisplayName("formatBytes should format bytes in GB")
    void formatBytesShouldFormatGB() {
        assertThat(Main.formatBytes(3L * 1024 * 1024 * 1024)).isEqualTo("3.00 GB");
    }

    @Test
    @DisplayName("formatBytes should format bytes in TB")
    void formatBytesShouldFormatTB() {
        assertThat(Main.formatBytes(2L * 1024 * 1024 * 1024 * 1024)).isEqualTo("2.00 TB");
    }

    // ==================== loadBaseline ====================

    @Test
    @DisplayName("loadBaseline should parse valid baseline JSON")
    void loadBaselineShouldParseValidJson() throws Exception {
        Path baselineFile = tempDir.resolve("baseline.json");
        String json = """
                {
                    "results": [
                        {
                            "runId": "run-001-thread-00",
                            "testType": "SEQ_WRITE",
                            "bytesProcessed": 1073741824,
                            "elapsed": "PT10S",
                            "elapsedNanos": 10000000000,
                            "throughputMBps": 102.4,
                            "avgLatencyMs": 0.5,
                            "avgLatencyNs": 500000.0,
                            "iops": 1000.0,
                            "timestamp": "2025-01-01T00:00:00Z",
                            "p50LatencyNs": 1000.0,
                            "p95LatencyNs": 2000.0,
                            "p99LatencyNs": 3000.0,
                            "p999LatencyNs": 4000.0,
                            "maxLatencyNs": 5000
                        }
                    ]
                }
                """;
        Files.writeString(baselineFile, json, StandardCharsets.UTF_8);

        var results = Main.loadBaseline(baselineFile.toString());
        assertThat(results).hasSize(1);
        assertThat(results.get(0).runId()).isEqualTo("run-001-thread-00");
        assertThat(results.get(0).throughputMBps()).isEqualTo(102.4);
    }

    @Test
    @DisplayName("loadBaseline should throw on malformed JSON")
    void loadBaselineShouldThrowOnMalformedJson() {
        Path badFile = tempDir.resolve("bad.json");
        assertThatThrownBy(() -> Main.loadBaseline(badFile.toString()))
                .isInstanceOf(java.io.IOException.class);
    }

    @Test
    @DisplayName("loadBaseline should throw when results is missing")
    void loadBaselineShouldThrowWhenResultsMissing() throws Exception {
        Path baselineFile = tempDir.resolve("no_results.json");
        String json = "{}";
        Files.writeString(baselineFile, json, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> Main.loadBaseline(baselineFile.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("results");
    }

    @Test
    @DisplayName("loadBaseline should throw when results is not an array")
    void loadBaselineShouldThrowWhenResultsNotArray() throws Exception {
        Path baselineFile = tempDir.resolve("results_not_array.json");
        String json = """
                {"results": "not an array"}
                """;
        Files.writeString(baselineFile, json, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> Main.loadBaseline(baselineFile.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("results");
    }

    // ==================== Exception handler paths via Main.run ====================

    @Test
    @DisplayName("Main.run should catch NumberFormatException with non-numeric value")
    void mainRunShouldCatchNumberFormatException() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        PrintStream origOut = System.out;
        PrintStream origErr = System.err;
        try {
            System.setOut(new PrintStream(out));
            System.setErr(new PrintStream(err));

            int exitCode = Main.run(new String[]{
                    "-d", tempDir.toString(),
                    "-t", "SEQ_WRITE",
                    "-s", "1G",
                    "-b", "abc"
            });

            assertThat(exitCode).isEqualTo(1);
            String errStr = err.toString(StandardCharsets.UTF_8);
            assertThat(errStr).contains("Invalid number format");
        } finally {
            System.setOut(origOut);
            System.setErr(origErr);
        }
    }

    @Test
    @DisplayName("Main.run should catch IllegalArgumentException for out-of-range values")
    void mainRunShouldCatchIllegalArgumentException() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        PrintStream origOut = System.out;
        PrintStream origErr = System.err;
        try {
            System.setOut(new PrintStream(out));
            System.setErr(new PrintStream(err));

            int exitCode = Main.run(new String[]{
                    "-d", tempDir.toString(),
                    "-t", "SEQ_WRITE",
                    "-s", "500M"
            });

            assertThat(exitCode).isEqualTo(1);
            String errStr = err.toString(StandardCharsets.UTF_8);
            assertThat(errStr).contains("File size must be between 1 GB and 10 GB");
        } finally {
            System.setOut(origOut);
            System.setErr(origErr);
        }
    }

    @Test
    @DisplayName("Main.run should handle --compare flag with valid baseline")
    void mainRunShouldHandleCompareFlag() throws Exception {
        Path baselineFile = tempDir.resolve("baseline.json");
        String json = """
                {
                    "results": [
                        {
                            "runId": "run-001-thread-00",
                            "testType": "SEQ_WRITE",
                            "bytesProcessed": 1073741824,
                            "elapsed": "PT10S",
                            "elapsedNanos": 10000000000,
                            "throughputMBps": 102.4,
                            "avgLatencyMs": 0.5,
                            "avgLatencyNs": 500000.0,
                            "iops": 1000.0,
                            "timestamp": "2025-01-01T00:00:00Z",
                            "p50LatencyNs": 1000.0,
                            "p95LatencyNs": 2000.0,
                            "p99LatencyNs": 3000.0,
                            "p999LatencyNs": 4000.0,
                            "maxLatencyNs": 5000
                        }
                    ]
                }
                """;
        Files.writeString(baselineFile, json, StandardCharsets.UTF_8);

        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        PrintStream origOut = System.out;
        PrintStream origErr = System.err;
        try {
            System.setOut(new PrintStream(out));
            System.setErr(new PrintStream(err));

            int exitCode = Main.run(new String[]{
                    "-d", tempDir.toString(),
                    "-t", "SEQ_WRITE",
                    "-s", String.valueOf(1024L * 1024 * 1024),
                    "-n", "1",
                    "-i", "1",
                    "--compare", baselineFile.toString()
            });

            assertThat(exitCode)
                    .as("stderr: " + err.toString(StandardCharsets.UTF_8))
                    .isEqualTo(0);
            String outStr = out.toString(StandardCharsets.UTF_8);
            assertThat(outStr).contains("Starting JStorageMark");
        } finally {
            System.setOut(origOut);
            System.setErr(origErr);
        }
    }
}
