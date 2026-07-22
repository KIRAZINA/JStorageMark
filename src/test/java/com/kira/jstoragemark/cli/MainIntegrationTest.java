package com.kira.jstoragemark.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class MainIntegrationTest {

    @TempDir
    Path testDir;

    private static final long ONE_GB = 1024L * 1024 * 1024;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(testDir);
    }

    @AfterEach
    void tearDown() {
        File[] files = testDir.toFile().listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
    }

    @Test
    @DisplayName("Full workflow should generate CSV and JSON reports")
    void fullWorkflowShouldGenerateReports() {
        String[] args = {
                "-d", testDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", String.valueOf(ONE_GB),
                "-b", String.valueOf(4 * 1024),
                "-n", "1",
                "-i", "3",
                "-q", "1"
        };

        assertThat(Main.run(args)).isEqualTo(0);

        File[] files = testDir.toFile().listFiles();
        assertThat(files).isNotNull();
        assertThat(files)
                .anyMatch(f -> f.getName().endsWith(".csv"))
                .anyMatch(f -> f.getName().endsWith(".json"));
    }

    @Test
    @DisplayName("Full workflow should generate all report types")
    void fullWorkflowShouldGenerateAllReportTypes() {
        String[] args = {
                "-d", testDir.toString(),
                "-t", "SEQ_WRITE,SEQ_READ",
                "-s", String.valueOf(ONE_GB),
                "-b", String.valueOf(64 * 1024),
                "-n", "2",
                "-i", "2",
                "-q", "4",
                "-html"
        };

        assertThat(Main.run(args)).isEqualTo(0);

        File[] files = testDir.toFile().listFiles();
        assertThat(files).isNotNull();
        assertThat(files)
                .anyMatch(f -> f.getName().endsWith(".csv"))
                .anyMatch(f -> f.getName().endsWith(".json"))
                .anyMatch(f -> f.getName().endsWith(".html"));
    }

    @Test
    @DisplayName("Full workflow with random tests should complete")
    void fullWorkflowWithRandomTests() {
        String[] args = {
                "-d", testDir.toString(),
                "-t", "RAND_WRITE,RAND_READ",
                "-s", String.valueOf(ONE_GB),
                "-b", String.valueOf(16 * 1024),
                "-n", "1",
                "-i", "2",
                "-q", "2"
        };

        assertThat(Main.run(args)).isEqualTo(0);

        File[] csvFiles = testDir.toFile().listFiles((dir, name) -> name.endsWith(".csv"));
        assertThat(csvFiles).isNotNull();
        assertThat(csvFiles.length).isGreaterThan(0);
    }

    @Test
    @DisplayName("Full workflow with all test types should complete")
    void fullWorkflowWithAllTestTypes() {
        String[] args = {
                "-d", testDir.toString(),
                "-t", "SEQ_WRITE,SEQ_READ,RAND_WRITE,RAND_READ",
                "-s", String.valueOf(ONE_GB),
                "-b", String.valueOf(32 * 1024),
                "-n", "1",
                "-i", "1",
                "-q", "1",
                "-r"
        };

        assertThat(Main.run(args)).isEqualTo(0);

        File[] csvFiles = testDir.toFile().listFiles((dir, name) -> name.endsWith(".csv"));
        File[] jsonFiles = testDir.toFile().listFiles((dir, name) -> name.endsWith(".json"));

        assertThat(csvFiles).isNotNull();
        assertThat(jsonFiles).isNotNull();
        assertThat(csvFiles.length).isGreaterThan(0);
        assertThat(jsonFiles.length).isGreaterThan(0);
    }

    @Test
    @DisplayName("CSV report should contain correct data")
    void csvReportShouldContainCorrectData() throws IOException {
        String[] args = {
                "-d", testDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", String.valueOf(ONE_GB),
                "-n", "1",
                "-i", "2"
        };

        assertThat(Main.run(args)).isEqualTo(0);

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
    void jsonReportShouldBeValid() throws IOException {
        String[] args = {
                "-d", testDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", String.valueOf(ONE_GB),
                "-n", "1",
                "-i", "2"
        };

        assertThat(Main.run(args)).isEqualTo(0);

        File[] jsonFiles = testDir.toFile().listFiles((dir, name) -> name.endsWith(".json"));
        assertThat(jsonFiles).isNotNull();
        assertThat(jsonFiles.length).isGreaterThan(0);

        String content = Files.readString(jsonFiles[0].toPath());

        assertThat(content).startsWith("{").endsWith("}");
        assertThat(content).contains("\"results\"");
        assertThat(content).contains("\"metrics\"");
        assertThat(content).contains("\"sessionId\"");
    }

    @Test
    @DisplayName("HTML report should be valid HTML when requested")
    void htmlReportShouldBeValid() throws IOException {
        String[] args = {
                "-d", testDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", String.valueOf(ONE_GB),
                "-n", "1",
                "-i", "1",
                "-html"
        };

        assertThat(Main.run(args)).isEqualTo(0);

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
    void workflowShouldOutputResultsToConsole() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(out));

        try {
            String[] args = {
                    "-d", testDir.toString(),
                    "-t", "SEQ_WRITE",
                    "-s", String.valueOf(ONE_GB),
                    "-n", "1",
                    "-i", "1"
            };

            assertThat(Main.run(args)).isEqualTo(0);

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
