package com.kira.jstoragemark.cli;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the full CLI workflow.
 * Runs Main with arguments, then checks that reports are generated.
 */
class MainIntegrationTest {

    @Test
    void fullWorkflowShouldGenerateReports() throws Exception {
        Path testDir = Path.of("./itest-output");
        if (!testDir.toFile().exists()) {
            testDir.toFile().mkdirs();
        }

        String[] args = {
                "-d", testDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", String.valueOf(1024L * 1024 * 1024), // 1 GB
                "-b", String.valueOf(4 * 1024),             // 4 KB
                "-n", "1",
                "-i", "3",
                "-q", "1"
        };

        // Run the CLI
        Main.main(args);

        // Check that CSV and JSON reports exist
        File[] files = testDir.toFile().listFiles();
        assertThat(files).isNotNull();
        assertThat(files)
                .anyMatch(f -> f.getName().endsWith(".csv"))
                .anyMatch(f -> f.getName().endsWith(".json"));
    }
}
