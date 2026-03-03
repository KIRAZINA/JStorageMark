package com.kira.jstoragemark;

import com.kira.jstoragemark.cli.Main;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * End-to-end integration tests covering complete user workflows.
 */
class EndToEndTest {

    @TempDir
    Path testDir;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
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
    @DisplayName("Complete benchmark workflow: SEQ_WRITE only")
    void completeWorkflowSeqWriteOnly() throws Exception {
        String[] args = {
                "-d", testDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", String.valueOf(256L * 1024 * 1024),  // 256 MB
                "-b", String.valueOf(64 * 1024),            // 64 KB blocks
                "-n", "2",                                  // 2 threads
                "-i", "3",                                  // 3 iterations
                "-q", "4"                                   // queue depth 4
        };

        Main.main(args);

        // Verify reports exist
        File[] csvFiles = testDir.toFile().listFiles((d, n) -> n.endsWith(".csv"));
        File[] jsonFiles = testDir.toFile().listFiles((d, n) -> n.endsWith(".json"));

        assertThat(csvFiles).isNotNull().hasSize(1);
        assertThat(jsonFiles).isNotNull().hasSize(1);

        // Verify CSV content
        String csvContent = Files.readString(csvFiles[0].toPath());
        assertThat(csvContent).contains("SEQ_WRITE");
        assertThat(csvContent.split("\n")).hasSize(5); // header + 3 results + 1 empty line

        // Verify JSON structure
        JsonNode json = objectMapper.readTree(jsonFiles[0]);
        assertThat(json.get("results")).hasSize(3);
        assertThat(json.get("sessionId")).isNotNull();
    }

    @Test
    @DisplayName("Complete benchmark workflow: All test types with HTML report")
    void completeWorkflowAllTestTypesWithHtml() throws Exception {
        String[] args = {
                "-d", testDir.toString(),
                "-t", "SEQ_WRITE,SEQ_READ,RAND_WRITE,RAND_READ",
                "-s", String.valueOf(128L * 1024 * 1024),   // 128 MB
                "-b", String.valueOf(32 * 1024),            // 32 KB blocks
                "-n", "1",
                "-i", "2",
                "-html"
        };

        Main.main(args);

        // Verify all report types exist
        File[] csvFiles = testDir.toFile().listFiles((d, n) -> n.endsWith(".csv"));
        File[] jsonFiles = testDir.toFile().listFiles((d, n) -> n.endsWith(".json"));
        File[] htmlFiles = testDir.toFile().listFiles((d, n) -> n.endsWith(".html"));

        assertThat(csvFiles).isNotNull().hasSize(1);
        assertThat(jsonFiles).isNotNull().hasSize(1);
        assertThat(htmlFiles).isNotNull().hasSize(1);

        // Verify JSON has 8 results (4 types * 2 iterations)
        JsonNode json = objectMapper.readTree(jsonFiles[0]);
        assertThat(json.get("results")).hasSize(8);

        // Verify HTML content
        String htmlContent = Files.readString(htmlFiles[0].toPath());
        assertThat(htmlContent).contains("SEQ_WRITE");
        assertThat(htmlContent).contains("SEQ_READ");
        assertThat(htmlContent).contains("RAND_WRITE");
        assertThat(htmlContent).contains("RAND_READ");
    }

    @Test
    @DisplayName("Complete benchmark workflow: Large file with multiple threads")
    void completeWorkflowLargeFileMultipleThreads() throws Exception {
        String[] args = {
                "-d", testDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", String.valueOf(512L * 1024 * 1024),   // 512 MB
                "-b", String.valueOf(128 * 1024),           // 128 KB blocks
                "-n", "4",                                  // 4 threads
                "-i", "2",
                "-q", "8"
        };

        Main.main(args);

        File[] files = testDir.toFile().listFiles((d, n) -> 
                n.endsWith(".csv") || n.endsWith(".json"));
        assertThat(files).hasSize(2);

        // Verify results are valid
        JsonNode json = objectMapper.readTree(
                testDir.toFile().listFiles((d, n) -> n.endsWith(".json"))[0]
        );
        
        for (JsonNode result : json.get("results")) {
            double throughput = result.get("throughputMBps").asDouble();
            double iops = result.get("iops").asDouble();
            
            assertThat(throughput).isGreaterThan(0);
            assertThat(iops).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("Complete benchmark workflow: Small blocks random I/O")
    void completeWorkflowSmallBlocksRandomIO() throws Exception {
        String[] args = {
                "-d", testDir.toString(),
                "-t", "RAND_READ,RAND_WRITE",
                "-s", String.valueOf(64L * 1024 * 1024),    // 64 MB
                "-b", String.valueOf(4 * 1024),             // 4 KB blocks (small)
                "-n", "2",
                "-i", "2"
        };

        Main.main(args);

        File[] csvFiles = testDir.toFile().listFiles((d, n) -> n.endsWith(".csv"));
        assertThat(csvFiles).hasSize(1);

        String csvContent = Files.readString(csvFiles[0].toPath());
        assertThat(csvContent).contains("RAND_READ");
        assertThat(csvContent).contains("RAND_WRITE");
    }

    @Test
    @DisplayName("Complete benchmark workflow: With file retention")
    void completeWorkflowWithFileRetention() throws Exception {
        String[] args = {
                "-d", testDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", String.valueOf(64L * 1024 * 1024),
                "-n", "1",
                "-i", "1",
                "-r"  // Retain files
        };

        Main.main(args);

        // Reports should exist
        File[] reports = testDir.toFile().listFiles((d, n) -> 
                n.endsWith(".csv") || n.endsWith(".json"));
        assertThat(reports).hasSize(2);
    }

    @Test
    @DisplayName("Complete benchmark workflow: JSON metrics validation")
    void completeWorkflowJsonMetricsValidation() throws Exception {
        String[] args = {
                "-d", testDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", String.valueOf(128L * 1024 * 1024),
                "-n", "1",
                "-i", "2"
        };

        Main.main(args);

        File[] jsonFiles = testDir.toFile().listFiles((d, n) -> n.endsWith(".json"));
        assertThat(jsonFiles).hasSize(1);

        JsonNode json = objectMapper.readTree(jsonFiles[0]);
        
        // Validate result structure
        for (JsonNode result : json.get("results")) {
            assertThat(result.has("runId")).isTrue();
            assertThat(result.has("testType")).isTrue();
            assertThat(result.has("bytesProcessed")).isTrue();
            assertThat(result.has("throughputMBps")).isTrue();
            assertThat(result.has("avgLatencyMs")).isTrue();
            assertThat(result.has("iops")).isTrue();
            assertThat(result.has("timestamp")).isTrue();
            
            // Validate data types
            assertThat(result.get("runId").isInt()).isTrue();
            assertThat(result.get("throughputMBps").isDouble()).isTrue();
            assertThat(result.get("iops").isDouble()).isTrue();
        }

        // Validate metrics
        assertThat(json.has("metrics")).isTrue();
        assertThat(json.get("metrics").isArray()).isTrue();
    }

    @Test
    @DisplayName("Complete benchmark workflow: CSV format validation")
    void completeWorkflowCsvFormatValidation() throws Exception {
        String[] args = {
                "-d", testDir.toString(),
                "-t", "SEQ_WRITE,SEQ_READ",
                "-s", String.valueOf(128L * 1024 * 1024),
                "-n", "1",
                "-i", "2"
        };

        Main.main(args);

        File[] csvFiles = testDir.toFile().listFiles((d, n) -> n.endsWith(".csv"));
        assertThat(csvFiles).hasSize(1);

        String csvContent = Files.readString(csvFiles[0].toPath());
        String[] lines = csvContent.split("\n");
        
        // Validate header
        assertThat(lines[0]).contains("RunId", "TestType", "ThroughputMBps", "IOPS");
        
        // Validate data rows (4 results: 2 types * 2 iterations)
        assertThat(lines.length).isGreaterThanOrEqualTo(5); // header + 4 data rows
        
        // Validate numeric format (decimal point, not comma)
        for (int i = 1; i < lines.length && i < 5; i++) {
            assertThat(lines[i]).containsPattern("\\d+\\.\\d+"); // matches X.XX format
        }
    }

    @Test
    @DisplayName("Complete benchmark workflow: HTML escaping validation")
    void completeWorkflowHtmlEscapingValidation() throws Exception {
        String[] args = {
                "-d", testDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", String.valueOf(64L * 1024 * 1024),
                "-n", "1",
                "-i", "1",
                "-html"
        };

        Main.main(args);

        File[] htmlFiles = testDir.toFile().listFiles((d, n) -> n.endsWith(".html"));
        assertThat(htmlFiles).hasSize(1);

        String htmlContent = Files.readString(htmlFiles[0].toPath());
        
        // Verify HTML structure
        assertThat(htmlContent).contains("<!DOCTYPE html>");
        assertThat(htmlContent).contains("<html>");
        assertThat(htmlContent).contains("<head>");
        assertThat(htmlContent).contains("<body>");
        
        // Verify tables
        assertThat(htmlContent).contains("<table>");
        assertThat(htmlContent).contains("</table>");
        assertThat(htmlContent).contains("<th>");
        assertThat(htmlContent).contains("<td>");
        
        // Verify meta charset
        assertThat(htmlContent).contains("charset=\"UTF-8\"");
    }

    @Test
    @DisplayName("Multiple consecutive runs should produce separate reports")
    void multipleConsecutiveRunsShouldProduceSeparateReports() throws Exception {
        // First run
        String[] args1 = {
                "-d", testDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", String.valueOf(64L * 1024 * 1024),
                "-n", "1",
                "-i", "1"
        };
        Main.main(args1);

        // Second run
        String[] args2 = {
                "-d", testDir.toString(),
                "-t", "SEQ_READ",
                "-s", String.valueOf(64L * 1024 * 1024),
                "-n", "1",
                "-i", "1"
        };
        Main.main(args2);

        // Should have 2 sets of reports (different session IDs)
        File[] csvFiles = testDir.toFile().listFiles((d, n) -> n.endsWith(".csv"));
        File[] jsonFiles = testDir.toFile().listFiles((d, n) -> n.endsWith(".json"));

        assertThat(csvFiles).hasSize(2);
        assertThat(jsonFiles).hasSize(2);

        // Verify different session IDs
        JsonNode json1 = objectMapper.readTree(jsonFiles[0]);
        JsonNode json2 = objectMapper.readTree(jsonFiles[1]);
        
        String sessionId1 = json1.get("sessionId").asText();
        String sessionId2 = json2.get("sessionId").asText();
        
        assertThat(sessionId1).isNotEqualTo(sessionId2);
    }
}
