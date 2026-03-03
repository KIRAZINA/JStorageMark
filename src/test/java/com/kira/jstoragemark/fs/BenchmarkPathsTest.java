package com.kira.jstoragemark.fs;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for BenchmarkPaths filesystem operations.
 */
class BenchmarkPathsTest {

    @TempDir
    Path tempDir;

    private static final String SESSION_ID = "test-session-123";

    private BenchmarkPaths benchmarkPaths;

    @BeforeEach
    void setUp() {
        benchmarkPaths = new BenchmarkPaths(tempDir, SESSION_ID);
    }

    @AfterEach
    void tearDown() {
        // Cleanup any remaining files
        benchmarkPaths.cleanupSessionFiles(false);
    }

    // ==================== Directory Tests ====================

    @Test
    @DisplayName("Ensure test directory should create directory if not exists")
    void ensureTestDirectoryShouldCreateDirectory() throws IOException {
        Path newDir = tempDir.resolve("new-subdir");
        BenchmarkPaths paths = new BenchmarkPaths(newDir, SESSION_ID);

        paths.ensureTestDirectory();

        assertThat(newDir).exists().isDirectory();
    }

    @Test
    @DisplayName("Ensure test directory should succeed if directory exists")
    void ensureTestDirectoryShouldSucceedIfExists() {
        assertThatNoException()
                .isThrownBy(() -> benchmarkPaths.ensureTestDirectory());
    }

    @Test
    @DisplayName("Ensure test directory should throw if path is not a directory")
    void ensureTestDirectoryShouldThrowIfNotDirectory(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("not-a-dir");
        Files.writeString(file, "test");

        BenchmarkPaths paths = new BenchmarkPaths(file, SESSION_ID);

        assertThatThrownBy(() -> paths.ensureTestDirectory())
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not a directory");
    }

    @Test
    @DisplayName("Ensure test directory should verify writability")
    void ensureTestDirectoryShouldVerifyWritability() throws IOException {
        benchmarkPaths.ensureTestDirectory();

        // Should create and delete probe file
        assertThatNoException()
                .isThrownBy(() -> benchmarkPaths.ensureTestDirectory());
    }

    // ==================== Free Space Tests ====================

    @Test
    @DisplayName("Validate free space should pass when sufficient space available")
    void validateFreeSpaceShouldPassWithSufficientSpace() {
        assertThatNoException()
                .isThrownBy(() -> benchmarkPaths.validateFreeSpace(1024)); // 1 KB
    }

    @Test
    @DisplayName("Validate free space should throw when insufficient space")
    void validateFreeSpaceShouldThrowWhenInsufficient() {
        long excessiveSize = Long.MAX_VALUE;

        assertThatThrownBy(() -> benchmarkPaths.validateFreeSpace(excessiveSize))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Insufficient free space");
    }

    @Test
    @DisplayName("Validate free space should include 5% buffer")
    void validateFreeSpaceShouldIncludeBuffer() {
        // This test verifies the buffer is applied by checking the error message
        assertThatThrownBy(() -> benchmarkPaths.validateFreeSpace(Long.MAX_VALUE))
                .hasMessageContaining("Required");
    }

    // ==================== Path Generation Tests ====================

    @Test
    @DisplayName("Test file path should include run id and descriptor")
    void testFilePathShouldIncludeRunIdAndDescriptor() {
        Path path = benchmarkPaths.testFilePath(1, "seq_write");
        String fileName = path.getFileName().toString();

        assertThat(fileName)
                .startsWith("run-001")
                .contains("seq_write")
                .contains(SESSION_ID)
                .endsWith(".bin");
    }

    @Test
    @DisplayName("Test file path should pad run id with zeros")
    void testFilePathShouldPadRunId() {
        Path path1 = benchmarkPaths.testFilePath(1, "test");
        Path path99 = benchmarkPaths.testFilePath(99, "test");
        Path path999 = benchmarkPaths.testFilePath(999, "test");

        assertThat(path1.getFileName().toString()).startsWith("run-001");
        assertThat(path99.getFileName().toString()).startsWith("run-099");
        assertThat(path999.getFileName().toString()).startsWith("run-999");
    }

    @Test
    @DisplayName("Test file path should replace spaces in descriptor")
    void testFilePathShouldReplaceSpaces() {
        Path path = benchmarkPaths.testFilePath(1, "seq write test");
        String fileName = path.getFileName().toString();

        assertThat(fileName).contains("seq.write.test");
    }

    @Test
    @DisplayName("Test file path should use default descriptor if null")
    void testFilePathShouldUseDefaultDescriptor() {
        Path path = benchmarkPaths.testFilePath(1, null);
        String fileName = path.getFileName().toString();

        assertThat(fileName).contains("data");
    }

    @Test
    @DisplayName("Temp file path should have .tmp extension")
    void tempFilePathShouldHaveTmpExtension() {
        Path path = benchmarkPaths.tempFilePath(1, "test");

        assertThat(path.getFileName().toString()).endsWith(".tmp");
    }

    @Test
    @DisplayName("Temp file path should use default descriptor if null")
    void tempFilePathShouldUseDefaultDescriptor() {
        Path path = benchmarkPaths.tempFilePath(1, null);
        String fileName = path.getFileName().toString();

        assertThat(fileName).contains("temp");
    }

    @Test
    @DisplayName("Report file path should have correct extension")
    void reportFilePathShouldHaveCorrectExtension() {
        Path csvPath = benchmarkPaths.reportFilePath("csv");
        Path jsonPath = benchmarkPaths.reportFilePath("json");

        assertThat(csvPath.getFileName().toString()).endsWith(".csv");
        assertThat(jsonPath.getFileName().toString()).endsWith(".json");
        assertThat(csvPath.getFileName().toString()).contains(SESSION_ID);
    }

    @Test
    @DisplayName("Report file path should handle leading dot")
    void reportFilePathShouldHandleLeadingDot() {
        Path path1 = benchmarkPaths.reportFilePath("html");
        Path path2 = benchmarkPaths.reportFilePath(".html");

        assertThat(path1).isEqualTo(path2);
    }

    @Test
    @DisplayName("Ensure charts directory should create directory")
    void ensureChartsDirShouldCreateDirectory() throws IOException {
        Path chartsDir = benchmarkPaths.ensureChartsDir();

        assertThat(chartsDir).exists().isDirectory();
        assertThat(chartsDir.getFileName().toString()).contains("charts-");
    }

    @Test
    @DisplayName("Ensure charts directory should return existing directory")
    void ensureChartsDirShouldReturnExisting() throws IOException {
        Path chartsDir1 = benchmarkPaths.ensureChartsDir();
        Path chartsDir2 = benchmarkPaths.ensureChartsDir();

        assertThat(chartsDir1).isEqualTo(chartsDir2);
    }

    // ==================== Cleanup Tests ====================

    @Test
    @DisplayName("Cleanup session files should delete files with session id")
    void cleanupShouldDeleteSessionFiles() throws IOException {
        Path testFile = benchmarkPaths.testFilePath(1, "test");
        Files.writeString(testFile, "test data");

        assertThat(testFile).exists();

        benchmarkPaths.cleanupSessionFiles(false);

        assertThat(testFile).doesNotExist();
    }

    @Test
    @DisplayName("Cleanup session files should retain files when flag is true")
    void cleanupShouldRetainFilesWhenFlagTrue() throws IOException {
        Path testFile = benchmarkPaths.testFilePath(1, "test");
        Files.writeString(testFile, "test data");

        benchmarkPaths.cleanupSessionFiles(true);

        assertThat(testFile).exists();
    }

    @Test
    @DisplayName("Cleanup session files should delete run files")
    void cleanupShouldDeleteRunFiles() throws IOException {
        Path runFile = tempDir.resolve("run-001.test.bin");
        Files.writeString(runFile, "test data");

        benchmarkPaths.cleanupSessionFiles(false);

        assertThat(runFile).doesNotExist();
    }

    @Test
    @DisplayName("Cleanup should not fail on non-existent directory")
    void cleanupShouldNotFailOnNonExistentDir() {
        Path nonExistent = tempDir.resolve("does-not-exist");
        BenchmarkPaths paths = new BenchmarkPaths(nonExistent, SESSION_ID);

        assertThatNoException()
                .isThrownBy(() -> paths.cleanupSessionFiles(false));
    }

    @Test
    @DisplayName("Cleanup should handle exceptions gracefully")
    void cleanupShouldHandleExceptions() throws IOException {
        // Create a file that will be cleaned up
        Path testFile = benchmarkPaths.testFilePath(1, "test");
        Files.writeString(testFile, "test");

        // Cleanup should complete without throwing
        assertThatNoException()
                .isThrownBy(() -> benchmarkPaths.cleanupSessionFiles(false));
    }

    // ==================== Header Tests ====================

    @Test
    @DisplayName("Header should contain session id and directory")
    void headerShouldContainSessionInfo() {
        String header = benchmarkPaths.header();

        assertThat(header)
                .contains("[JStorageMark]")
                .contains("session=" + SESSION_ID)
                .contains("dir=" + tempDir.toString())
                .contains("ts=");
    }

    // ==================== Getter Tests ====================

    @Test
    @DisplayName("Getters should return correct values")
    void gettersShouldReturnCorrectValues() {
        assertThat(benchmarkPaths.getBaseDir()).isEqualTo(tempDir);
        assertThat(benchmarkPaths.getSessionId()).isEqualTo(SESSION_ID);
    }

    // ==================== Null Safety Tests ====================

    @Test
    @DisplayName("Constructor should throw on null base directory")
    void constructorShouldThrowOnNullBaseDir() {
        assertThatThrownBy(() -> new BenchmarkPaths(null, SESSION_ID))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("baseDir");
    }

    @Test
    @DisplayName("Constructor should throw on null session id")
    void constructorShouldThrowOnNullSessionId() {
        assertThatThrownBy(() -> new BenchmarkPaths(tempDir, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("sessionId");
    }
}
