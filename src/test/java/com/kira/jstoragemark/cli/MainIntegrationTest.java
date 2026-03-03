package com.kira.jstoragemark.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for the full CLI workflow.
 * Runs Main with arguments, then checks that reports are generated.
 */
class MainIntegrationTest {

    @TempDir
    Path testDir;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(testDir);
    }

    @AfterEach
    void tearDown() {
        // Cleanup test files
        File[] files = testDir.toFile().listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
    }

    @Test
    @DisplayName("Full workflow should generate CSV and JSON reports")
    void fullWorkflowShouldGenerateReports() throws Exception {
        String[] args = {
                "-d", testDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", String.valueOf(1024L * 1024 * 1024), // 1 GB
                "-b", String.valueOf(4 * 1024),             // 4 KB
                "-n", "1",
                "-i", "3",
                "-q", "1"
        };

        Main.main(args);

        File[] files = testDir.toFile().listFiles();
        assertThat(files).isNotNull();
        assertThat(files)
                .anyMatch(f -> f.getName().endsWith(".csv"))
                .anyMatch(f -> f.getName().endsWith(".json"));
    }

    @Test
    @DisplayName("Full workflow should generate all report types")
    void fullWorkflowShouldGenerateAllReportTypes() throws Exception {
        String[] args = {
                "-d", testDir.toString(),
                "-t", "SEQ_WRITE,SEQ_READ",
                "-s", String.valueOf(512L * 1024 * 1024),  // 512 MB
                "-b", String.valueOf(64 * 1024),           // 64 KB
                "-n", "2",
                "-i", "2",
                "-q", "4",
                "-html"
        };

        Main.main(args);

        File[] files = testDir.toFile().listFiles();
        assertThat(files).isNotNull();
        assertThat(files)
                .anyMatch(f -> f.getName().endsWith(".csv"))
                .anyMatch(f -> f.getName().endsWith(".json"))
                .anyMatch(f -> f.getName().endsWith(".html"));
    }

    @Test
    @DisplayName("Full workflow with random tests should complete")
    void fullWorkflowWithRandomTests() throws Exception {
        String[] args = {
                "-d", testDir.toString(),
                "-t", "RAND_WRITE,RAND_READ",
                "-s", String.valueOf(256L * 1024 * 1024),  // 256 MB
                "-b", String.valueOf(16 * 1024),           // 16 KB
                "-n", "1",
                "-i", "2",
                "-q", "2"
        };

        Main.main(args);

        File[] csvFiles = testDir.toFile().listFiles((dir, name) -> name.endsWith(".csv"));
        assertThat(csvFiles).isNotNull();
        assertThat(csvFiles.length).isGreaterThan(0);
    }

    @Test
    @DisplayName("Full workflow with all test types should complete")
    void fullWorkflowWithAllTestTypes() throws Exception {
        String[] args = {
                "-d", testDir.toString(),
                "-t", "SEQ_WRITE,SEQ_READ,RAND_WRITE,RAND_READ",
                "-s", String.valueOf(128L * 1024 * 1024),  // 128 MB
                "-b", String.valueOf(32 * 1024),           // 32 KB
                "-n", "1",
                "-i", "1",
                "-q", "1",
                "-r"  // Retain test files
        };

        Main.main(args);

        // Should have CSV and JSON reports
        File[] csvFiles = testDir.toFile().listFiles((dir, name) -> name.endsWith(".csv"));
        File[] jsonFiles = testDir.toFile().listFiles((dir, name) -> name.endsWith(".json"));

        assertThat(csvFiles).isNotNull();
        assertThat(jsonFiles).isNotNull();
        assertThat(csvFiles.length).isGreaterThan(0);
        assertThat(jsonFiles.length).isGreaterThan(0);
    }

    @Test
    @DisplayName("CSV report should contain correct data")
    void csvReportShouldContainCorrectData() throws Exception {
        String[] args = {
                "-d", testDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", String.valueOf(256L * 1024 * 1024),
                "-n", "1",
                "-i", "2"
        };

        Main.main(args);

        File[] csvFiles = testDir.toFile().listFiles((dir, name) -> name.endsWith(".csv"));
        assertThat(csvFiles).isNotNull();
        assertThat(csvFiles.length).isGreaterThan(0);

        String content = Files.readString(csvFiles[0].toPath());

        assertThat(content)
                .contains("RunId")
                .contains("TestType")
                .contains("SEQ_WRITE")
                .contains("ThroughputMBps")
                .contains("IOPS");
    }

    @Test
    @DisplayName("JSON report should be valid JSON")
    void jsonReportShouldBeValid() throws Exception {
        String[] args = {
                "-d", testDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", String.valueOf(256L * 1024 * 1024),
                "-n", "1",
                "-i", "2"
        };

        Main.main(args);

        File[] jsonFiles = testDir.toFile().listFiles((dir, name) -> name.endsWith(".json"));
        assertThat(jsonFiles).isNotNull();
        assertThat(jsonFiles.length).isGreaterThan(0);

        String content = Files.readString(jsonFiles[0].toPath());

        // Basic JSON validation
        assertThat(content).startsWith("{").endsWith("}");
        assertThat(content).contains("\"results\"");
        assertThat(content).contains("\"metrics\"");
        assertThat(content).contains("\"sessionId\"");
    }

    @Test
    @DisplayName("HTML report should be valid HTML when requested")
    void htmlReportShouldBeValid() throws Exception {
        String[] args = {
                "-d", testDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", String.valueOf(256L * 1024 * 1024),
                "-n", "1",
                "-i", "1",
                "-html"
        };

        Main.main(args);

        File[] htmlFiles = testDir.toFile().listFiles((dir, name) -> name.endsWith(".html"));
        assertThat(htmlFiles).isNotNull();
        assertThat(htmlFiles.length).isGreaterThan(0);

        String content = Files.readString(htmlFiles[0].toPath());

        assertThat(content)
                .contains("<!DOCTYPE html>")
                .contains("<html>")
                .contains("</html>")
                .contains("<table>");
    }

    @Test
    @DisplayName("Workflow should output benchmark results to console")
    void workflowShouldOutputResultsToConsole() throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.PrintStream originalOut = System.out;
        System.setOut(new java.io.PrintStream(out));

        try {
            String[] args = {
                    "-d", testDir.toString(),
                    "-t", "SEQ_WRITE",
                    "-s", String.valueOf(128L * 1024 * 1024),
                    "-n", "1",
                    "-i", "1"
            };

            Main.main(args);

            String output = out.toString();
            assertThat(output)
                    .contains("JStorageMark")
                    .contains("Test directory")
                    .contains("Benchmark Results");
        } finally {
            System.setOut(originalOut);
        }
    }
}
