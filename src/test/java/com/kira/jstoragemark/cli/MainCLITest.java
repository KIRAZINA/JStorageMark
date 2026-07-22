package com.kira.jstoragemark.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class MainCLITest {

    @TempDir
    Path tempDir;

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    private static final long ONE_GB = 1024L * 1024 * 1024;

    @BeforeEach
    void setUp() throws Exception {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
        Files.createDirectories(tempDir);
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    @DisplayName("Main should run with minimal arguments")
    void mainShouldRunWithMinimalArgs() {
        String[] args = {
                "-d", tempDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", String.valueOf(ONE_GB),
                "-b", String.valueOf(64 * 1024),
                "-n", "1",
                "-i", "1",
                "-q", "1"
        };

        assertThat(Main.run(args)).isEqualTo(0);
    }

    @Test
    @DisplayName("Main should accept multiple test types")
    void mainShouldAcceptMultipleTestTypes() {
        String[] args = {
                "-d", tempDir.toString(),
                "-t", "SEQ_WRITE,SEQ_READ",
                "-s", String.valueOf(ONE_GB),
                "-n", "1",
                "-i", "1"
        };

        assertThat(Main.run(args)).isEqualTo(0);
    }

    @Test
    @DisplayName("Main should generate CSV report")
    void mainShouldGenerateCsvReport() {
        String[] args = {
                "-d", tempDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", String.valueOf(ONE_GB),
                "-n", "1",
                "-i", "1"
        };

        assertThat(Main.run(args)).isEqualTo(0);

        File[] files = tempDir.toFile().listFiles((dir, name) -> name.endsWith(".csv"));
        assertThat(files).isNotNull();
        assertThat(files.length).isGreaterThan(0);
    }

    @Test
    @DisplayName("Main should generate JSON report")
    void mainShouldGenerateJsonReport() {
        String[] args = {
                "-d", tempDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", String.valueOf(ONE_GB),
                "-n", "1",
                "-i", "1"
        };

        assertThat(Main.run(args)).isEqualTo(0);

        File[] files = tempDir.toFile().listFiles((dir, name) -> name.endsWith(".json"));
        assertThat(files).isNotNull();
        assertThat(files.length).isGreaterThan(0);
    }

    @Test
    @DisplayName("Main should generate HTML report when requested")
    void mainShouldGenerateHtmlReport() {
        String[] args = {
                "-d", tempDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", String.valueOf(ONE_GB),
                "-n", "1",
                "-i", "1",
                "-html"
        };

        assertThat(Main.run(args)).isEqualTo(0);

        File[] files = tempDir.toFile().listFiles((dir, name) -> name.endsWith(".html"));
        assertThat(files).isNotNull();
        assertThat(files.length).isGreaterThan(0);
    }

    @Test
    @DisplayName("Main should use defaults for optional arguments")
    void mainShouldUseDefaults() {
        String[] args = {
                "-d", tempDir.toString(),
                "-t", "SEQ_WRITE"
        };

        assertThat(Main.run(args)).isEqualTo(0);
    }

    @Test
    @DisplayName("Main should handle different file sizes")
    void mainShouldHandleDifferentFileSizes() {
        long[] sizes = {
                ONE_GB,
                2L * 1024 * 1024 * 1024,
                5L * 1024 * 1024 * 1024
        };

        for (long size : sizes) {
            String[] args = {
                    "-d", tempDir.toString(),
                    "-t", "SEQ_WRITE",
                    "-s", String.valueOf(size),
                    "-n", "1",
                    "-i", "1"
            };

            assertThat(Main.run(args))
                    .as("Should handle file size: " + size)
                    .isEqualTo(0);
        }
    }

    @Test
    @DisplayName("Main should handle different block sizes")
    void mainShouldHandleDifferentBlockSizes() {
        int[] blockSizes = {4096, 16384, 65536, 131072};

        for (int blockSize : blockSizes) {
            String[] args = {
                    "-d", tempDir.toString(),
                    "-t", "SEQ_WRITE",
                    "-s", String.valueOf(ONE_GB),
                    "-b", String.valueOf(blockSize),
                    "-n", "1",
                    "-i", "1"
            };

            assertThat(Main.run(args))
                    .as("Should handle block size: " + blockSize)
                    .isEqualTo(0);
        }
    }

    @Test
    @DisplayName("Main should handle different thread counts")
    void mainShouldHandleDifferentThreadCounts() {
        int[] threadCounts = {1, 2, 4, 8};

        for (int threads : threadCounts) {
            String[] args = {
                    "-d", tempDir.toString(),
                    "-t", "SEQ_WRITE",
                    "-s", String.valueOf(ONE_GB),
                    "-n", String.valueOf(threads),
                    "-i", "1"
            };

            assertThat(Main.run(args))
                    .as("Should handle thread count: " + threads)
                    .isEqualTo(0);
        }
    }

    @Test
    @DisplayName("Main should handle different iteration counts")
    void mainShouldHandleDifferentIterationCounts() {
        int[] iterationCounts = {1, 3, 5};

        for (int iterations : iterationCounts) {
            String[] args = {
                    "-d", tempDir.toString(),
                    "-t", "SEQ_WRITE",
                    "-s", String.valueOf(ONE_GB),
                    "-n", "1",
                    "-i", String.valueOf(iterations)
            };

            assertThat(Main.run(args))
                    .as("Should handle iteration count: " + iterations)
                    .isEqualTo(0);
        }
    }

    @Test
    @DisplayName("Main should return error for non-existent directory")
    void mainShouldReturnErrorForNonExistentDirectory() {
        String[] args = {
                "-d", "/nonexistent/directory/path",
                "-t", "SEQ_WRITE"
        };

        assertThat(Main.run(args)).isNotEqualTo(0);
    }

    @Test
    @DisplayName("Main should return error for invalid test type")
    void mainShouldReturnErrorForInvalidTestType() {
        String[] args = {
                "-d", tempDir.toString(),
                "-t", "INVALID_TYPE"
        };

        assertThat(Main.run(args)).isNotEqualTo(0);
    }

    @Test
    @DisplayName("Main should return error for negative file size")
    void mainShouldReturnErrorForNegativeFileSize() {
        String[] args = {
                "-d", tempDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", "-1000"
        };

        assertThat(Main.run(args)).isNotEqualTo(0);
    }

    @Test
    @DisplayName("Main should return error for invalid block size")
    void mainShouldReturnErrorForInvalidBlockSize() {
        String[] args = {
                "-d", tempDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", String.valueOf(ONE_GB),
                "-b", "100"
        };

        assertThat(Main.run(args)).isNotEqualTo(0);
    }

    @Test
    @DisplayName("Main should return error for zero threads")
    void mainShouldReturnErrorForZeroThreads() {
        String[] args = {
                "-d", tempDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", String.valueOf(ONE_GB),
                "-n", "0"
        };

        assertThat(Main.run(args)).isNotEqualTo(0);
    }

    @Test
    @DisplayName("Main should print help with no arguments")
    void mainShouldPrintHelpWithNoArgs() {
        String[] args = {};

        assertThat(Main.run(args)).isNotEqualTo(0);
        assertThat(errContent.toString()).containsIgnoringCase("usage");
    }

    @Test
    @DisplayName("Main should retain test files when -r flag is used")
    void mainShouldRetainFilesWithFlag() {
        String[] args = {
                "-d", tempDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", String.valueOf(ONE_GB),
                "-n", "1",
                "-i", "1",
                "-r"
        };

        assertThat(Main.run(args)).isEqualTo(0);
    }

    @Test
    @DisplayName("Main should handle verbosity level 0")
    void mainShouldHandleVerbosity0() {
        String[] args = {
                "-d", tempDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", String.valueOf(ONE_GB),
                "-v", "0",
                "-n", "1",
                "-i", "1"
        };

        assertThat(Main.run(args)).isEqualTo(0);
    }

    @Test
    @DisplayName("Main should handle verbosity level 2")
    void mainShouldHandleVerbosity2() {
        String[] args = {
                "-d", tempDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", String.valueOf(ONE_GB),
                "-v", "2",
                "-n", "1",
                "-i", "1"
        };

        assertThat(Main.run(args)).isEqualTo(0);
    }

    @Test
    @DisplayName("--version should print version and exit with code 0")
    void versionFlagShouldPrintVersion() {
        String[] args = {"--version"};
        assertThat(Main.run(args)).isEqualTo(0);
        assertThat(outContent.toString()).contains("1.1.0");
    }

    @Test
    @DisplayName("parseFileSize should handle G suffix")
    void parseFileSizeShouldHandleGigabyteSuffix() {
        assertThat(Main.parseFileSize("1G")).isEqualTo(1024L * 1024 * 1024);
        assertThat(Main.parseFileSize("5G")).isEqualTo(5L * 1024 * 1024 * 1024);
    }

    @Test
    @DisplayName("parseFileSize should handle M suffix")
    void parseFileSizeShouldHandleMegabyteSuffix() {
        assertThat(Main.parseFileSize("1024M")).isEqualTo(1024L * 1024 * 1024);
    }

    @Test
    @DisplayName("parseFileSize should handle raw bytes")
    void parseFileSizeShouldHandleRawBytes() {
        assertThat(Main.parseFileSize("1073741824")).isEqualTo(1024L * 1024 * 1024);
    }

    @Test
    @DisplayName("parseFileSize should handle K suffix")
    void parseFileSizeShouldHandleKilobyteSuffix() {
        assertThat(Main.parseFileSize("1048576K")).isEqualTo(1024L * 1024 * 1024);
    }

    @Test
    @DisplayName("parseFileSize should throw on out-of-range values")
    void parseFileSizeShouldThrowOnOutOfRange() {
        assertThatThrownBy(() -> Main.parseFileSize("500M"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Main.parseFileSize("20G"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
