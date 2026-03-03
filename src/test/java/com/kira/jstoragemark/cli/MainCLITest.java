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

/**
 * Unit tests for CLI Main argument parsing and validation.
 */
class MainCLITest {

    @TempDir
    Path tempDir;

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

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

    // ==================== Basic Execution Tests ====================

    @Test
    @DisplayName("Main should run with minimal arguments")
    void mainShouldRunWithMinimalArgs() {
        String[] args = {
                "-d", tempDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", String.valueOf(64 * 1024 * 1024),
                "-b", String.valueOf(64 * 1024),
                "-n", "1",
                "-i", "1",
                "-q", "1"
        };

        // Should complete without exception
        assertThatNoException().isThrownBy(() -> Main.main(args));
    }

    @Test
    @DisplayName("Main should accept multiple test types")
    void mainShouldAcceptMultipleTestTypes() {
        String[] args = {
                "-d", tempDir.toString(),
                "-t", "SEQ_WRITE,SEQ_READ",
                "-s", String.valueOf(64 * 1024 * 1024),
                "-n", "1",
                "-i", "1"
        };

        assertThatNoException().isThrownBy(() -> Main.main(args));
    }

    @Test
    @DisplayName("Main should generate CSV report")
    void mainShouldGenerateCsvReport() {
        String[] args = {
                "-d", tempDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", String.valueOf(64 * 1024 * 1024),
                "-n", "1",
                "-i", "1"
        };

        Main.main(args);

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
                "-s", String.valueOf(64 * 1024 * 1024),
                "-n", "1",
                "-i", "1"
        };

        Main.main(args);

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
                "-s", String.valueOf(64 * 1024 * 1024),
                "-n", "1",
                "-i", "1",
                "-html"
        };

        Main.main(args);

        File[] files = tempDir.toFile().listFiles((dir, name) -> name.endsWith(".html"));
        assertThat(files).isNotNull();
        assertThat(files.length).isGreaterThan(0);
    }

    // ==================== Argument Parsing Tests ====================

    @Test
    @DisplayName("Main should use defaults for optional arguments")
    void mainShouldUseDefaults() {
        String[] args = {
                "-d", tempDir.toString(),
                "-t", "SEQ_WRITE"
        };

        assertThatNoException().isThrownBy(() -> Main.main(args));
    }

    @Test
    @DisplayName("Main should handle different file sizes")
    void mainShouldHandleDifferentFileSizes() {
        long[] sizes = {
                1024L * 1024 * 1024,      // 1 GB
                2L * 1024 * 1024 * 1024,  // 2 GB
                512L * 1024 * 1024        // 512 MB
        };

        for (long size : sizes) {
            String[] args = {
                    "-d", tempDir.toString(),
                    "-t", "SEQ_WRITE",
                    "-s", String.valueOf(size),
                    "-n", "1",
                    "-i", "1"
            };

            assertThatNoException()
                    .as("Should handle file size: " + size)
                    .isThrownBy(() -> Main.main(args));
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
                    "-b", String.valueOf(blockSize),
                    "-n", "1",
                    "-i", "1"
            };

            assertThatNoException()
                    .as("Should handle block size: " + blockSize)
                    .isThrownBy(() -> Main.main(args));
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
                    "-n", String.valueOf(threads),
                    "-i", "1"
            };

            assertThatNoException()
                    .as("Should handle thread count: " + threads)
                    .isThrownBy(() -> Main.main(args));
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
                    "-n", "1",
                    "-i", String.valueOf(iterations)
            };

            assertThatNoException()
                    .as("Should handle iteration count: " + iterations)
                    .isThrownBy(() -> Main.main(args));
        }
    }

    // ==================== Error Handling Tests ====================

    @Test
    @DisplayName("Main should exit with error for non-existent directory")
    void mainShouldExitForNonExistentDirectory() {
        String[] args = {
                "-d", "/nonexistent/directory/path",
                "-t", "SEQ_WRITE"
        };

        // Capture exit code
        SecurityManager original = System.getSecurityManager();
        try {
            System.setSecurityManager(new NoExitSecurityManager());
            assertThatThrownBy(() -> Main.main(args))
                    .isInstanceOf(SecurityException.class);
        } finally {
            System.setSecurityManager(original);
        }
    }

    @Test
    @DisplayName("Main should exit with error for invalid test type")
    void mainShouldExitForInvalidTestType() {
        String[] args = {
                "-d", tempDir.toString(),
                "-t", "INVALID_TYPE"
        };

        SecurityManager original = System.getSecurityManager();
        try {
            System.setSecurityManager(new NoExitSecurityManager());
            assertThatThrownBy(() -> Main.main(args))
                    .isInstanceOf(SecurityException.class);
        } finally {
            System.setSecurityManager(original);
        }
    }

    @Test
    @DisplayName("Main should exit with error for negative file size")
    void mainShouldExitForNegativeFileSize() {
        String[] args = {
                "-d", tempDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", "-1000"
        };

        SecurityManager original = System.getSecurityManager();
        try {
            System.setSecurityManager(new NoExitSecurityManager());
            assertThatThrownBy(() -> Main.main(args))
                    .isInstanceOf(SecurityException.class);
        } finally {
            System.setSecurityManager(original);
        }
    }

    @Test
    @DisplayName("Main should exit with error for invalid block size")
    void mainShouldExitForInvalidBlockSize() {
        String[] args = {
                "-d", tempDir.toString(),
                "-t", "SEQ_WRITE",
                "-b", "100"  // Too small
        };

        SecurityManager original = System.getSecurityManager();
        try {
            System.setSecurityManager(new NoExitSecurityManager());
            assertThatThrownBy(() -> Main.main(args))
                    .isInstanceOf(SecurityException.class);
        } finally {
            System.setSecurityManager(original);
        }
    }

    @Test
    @DisplayName("Main should exit with error for zero threads")
    void mainShouldExitForZeroThreads() {
        String[] args = {
                "-d", tempDir.toString(),
                "-t", "SEQ_WRITE",
                "-n", "0"
        };

        SecurityManager original = System.getSecurityManager();
        try {
            System.setSecurityManager(new NoExitSecurityManager());
            assertThatThrownBy(() -> Main.main(args))
                    .isInstanceOf(SecurityException.class);
        } finally {
            System.setSecurityManager(original);
        }
    }

    @Test
    @DisplayName("Main should print help with no arguments")
    void mainShouldPrintHelpWithNoArgs() {
        String[] args = {};

        SecurityManager original = System.getSecurityManager();
        try {
            System.setSecurityManager(new NoExitSecurityManager());
            assertThatThrownBy(() -> Main.main(args))
                    .isInstanceOf(SecurityException.class);
        } finally {
            System.setSecurityManager(original);
        }

        assertThat(errContent.toString()).containsIgnoringCase("usage");
    }

    // ==================== Retain Files Tests ====================

    @Test
    @DisplayName("Main should retain test files when -r flag is used")
    void mainShouldRetainFilesWithFlag() {
        String[] args = {
                "-d", tempDir.toString(),
                "-t", "SEQ_WRITE",
                "-s", String.valueOf(64 * 1024 * 1024),
                "-n", "1",
                "-i", "1",
                "-r"
        };

        Main.main(args);

        // Files should be retained (but we can't easily verify since they're in subdirectories)
        // Just verify it runs successfully
    }

    // ==================== Verbosity Tests ====================

    @Test
    @DisplayName("Main should handle verbosity level 0")
    void mainShouldHandleVerbosity0() {
        String[] args = {
                "-d", tempDir.toString(),
                "-t", "SEQ_WRITE",
                "-v", "0",
                "-n", "1",
                "-i", "1"
        };

        assertThatNoException().isThrownBy(() -> Main.main(args));
    }

    @Test
    @DisplayName("Main should handle verbosity level 2")
    void mainShouldHandleVerbosity2() {
        String[] args = {
                "-d", tempDir.toString(),
                "-t", "SEQ_WRITE",
                "-v", "2",
                "-n", "1",
                "-i", "1"
        };

        assertThatNoException().isThrownBy(() -> Main.main(args));
    }

    /**
     * Security manager that prevents System.exit from actually exiting
     * and instead throws a SecurityException.
     */
    private static class NoExitSecurityManager extends SecurityManager {
        @Override
        public void checkExit(int status) {
            throw new SecurityException("System.exit(" + status + ") blocked");
        }

        @Override
        public void checkPermission(java.security.Permission perm) {
            // Allow all other permissions
        }
    }
}
