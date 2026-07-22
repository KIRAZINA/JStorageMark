package com.kira.jstoragemark.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for BenchmarkConfig validation and builder pattern.
 */
class BenchmarkConfigTest {

    // ==================== Builder Success Tests ====================

    @Test
    @DisplayName("Valid config with all fields should build successfully")
    void validConfigShouldBuildSuccessfully() {
        BenchmarkConfig config = new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.SEQ_WRITE)
                .fileSizeBytes(2L * 1024 * 1024 * 1024) // 2 GB
                .blockSizeBytes(64 * 1024)              // 64 KB
                .threads(4)
                .iterations(5)
                .warmupIterations(1)
                .ioMode(BenchmarkConfig.IoMode.SYNC)
                .queueDepth(8)
                .metricsPollInterval(Duration.ofMillis(500))
                .randomSeed(12345L)
                .retainTestFiles(true)
                .collectSystemMetrics(true)
                .verbosity(2)
                .embedCharts(true)
                .addReportFormat(BenchmarkConfig.ReportFormat.HTML)
                .build();

        assertThat(config.getFileSizeBytes()).isEqualTo(2L * 1024 * 1024 * 1024);
        assertThat(config.getBlockSizeBytes()).isEqualTo(64 * 1024);
        assertThat(config.getThreads()).isEqualTo(4);
        assertThat(config.getIterations()).isEqualTo(5);
        assertThat(config.getWarmupIterations()).isEqualTo(1);
        assertThat(config.getQueueDepth()).isEqualTo(8);
        assertThat(config.getTestTypes()).contains(BenchmarkConfig.TestType.SEQ_WRITE);
        assertThat(config.getIoMode()).isEqualTo(BenchmarkConfig.IoMode.SYNC);
        assertThat(config.getRandomSeed()).isPresent().hasValue(12345L);
        assertThat(config.isRetainTestFiles()).isTrue();
        assertThat(config.isCollectSystemMetrics()).isTrue();
        assertThat(config.getVerbosity()).isEqualTo(2);
        assertThat(config.isEmbedCharts()).isTrue();
        assertThat(config.getReportFormats()).contains(BenchmarkConfig.ReportFormat.HTML);
        assertThat(config.getSessionId()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Default values should be applied correctly")
    void defaultValuesShouldBeApplied() {
        BenchmarkConfig config = new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .build();

        assertThat(config.getFileSizeBytes()).isEqualTo(5L * 1024 * 1024 * 1024); // 5 GB default
        assertThat(config.getBlockSizeBytes()).isEqualTo(128 * 1024); // 128 KB default
        assertThat(config.getThreads()).isEqualTo(4);
        assertThat(config.getIterations()).isEqualTo(5);
        assertThat(config.getWarmupIterations()).isEqualTo(1);
        assertThat(config.getQueueDepth()).isEqualTo(8);
        assertThat(config.getIoMode()).isEqualTo(BenchmarkConfig.IoMode.SYNC);
        assertThat(config.getRandomSeed()).isEmpty();
        assertThat(config.isRetainTestFiles()).isFalse();
        assertThat(config.getVerbosity()).isEqualTo(1);
        assertThat(config.isEmbedCharts()).isFalse();
        assertThat(config.getMetricsPollInterval()).isEqualTo(Duration.ofMillis(500));
    }

    @Test
    @DisplayName("Multiple test types should be supported")
    void multipleTestTypesShouldBeSupported() {
        BenchmarkConfig config = new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .testTypes(Arrays.asList(
                        BenchmarkConfig.TestType.SEQ_READ,
                        BenchmarkConfig.TestType.SEQ_WRITE,
                        BenchmarkConfig.TestType.RAND_READ,
                        BenchmarkConfig.TestType.RAND_WRITE
                ))
                .build();

        assertThat(config.getTestTypes()).hasSize(4);
        assertThat(config.hasRandomWorkloads()).isTrue();
        assertThat(config.hasSequentialWorkloads()).isTrue();
    }

    @Test
    @DisplayName("Empty test types should trigger defaults")
    void emptyTestTypesShouldTriggerDefaults() {
        BenchmarkConfig config = new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .build();

        assertThat(config.getTestTypes()).contains(
                BenchmarkConfig.TestType.SEQ_READ,
                BenchmarkConfig.TestType.SEQ_WRITE
        );
    }

    @Test
    @DisplayName("Test types collection should not be modifiable")
    void testTypesShouldBeUnmodifiable() {
        BenchmarkConfig config = new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .build();

        assertThatThrownBy(() -> config.getTestTypes().add(BenchmarkConfig.TestType.SEQ_WRITE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Blocks per file calculation should be correct")
    void blocksPerFileCalculationShouldBeCorrect() {
        BenchmarkConfig config = new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .fileSizeBytes(1024L * 1024 * 1024) // 1 GB
                .blockSizeBytes(64 * 1024)          // 64 KB
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .build();

        assertThat(config.blocksPerFile()).isEqualTo(16384);
    }

    @Test
    @DisplayName("Blocks per file should round up correctly")
    void blocksPerFileShouldRoundUp() {
        BenchmarkConfig config = new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .fileSizeBytes(1024L * 1024 * 1024) // 1 GB (not evenly divisible by 64KB)
                .blockSizeBytes(64 * 1024)
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .build();

        assertThat(config.blocksPerFile()).isEqualTo(16384);
    }

    // ==================== Validation Failure Tests ====================

    @Test
    @DisplayName("Empty test types should apply defaults")
    void emptyTestTypesShouldApplyDefaults() {
        BenchmarkConfig config = new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .testTypes(Collections.emptySet())
                .build();
        assertThat(config.getTestTypes()).contains(
                BenchmarkConfig.TestType.SEQ_READ,
                BenchmarkConfig.TestType.SEQ_WRITE
        );
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 512 * 1024 * 1024, 15L * 1024 * 1024 * 1024})
    @DisplayName("Invalid file size should throw exception")
    void invalidFileSizeShouldThrowException(long fileSize) {
        assertThatThrownBy(() -> new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .fileSizeBytes(fileSize)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fileSizeBytes");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 512, 2 * 1024 * 1024})
    @DisplayName("Invalid block size should throw exception")
    void invalidBlockSizeShouldThrowException(int blockSize) {
        assertThatThrownBy(() -> new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .blockSizeBytes(blockSize)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blockSizeBytes");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 33, -1})
    @DisplayName("Invalid thread count should throw exception")
    void invalidThreadCountShouldThrowException(int threads) {
        assertThatThrownBy(() -> new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .threads(threads)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("threads");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101})
    @DisplayName("Invalid iterations should throw exception")
    void invalidIterationsShouldThrowException(int iterations) {
        assertThatThrownBy(() -> new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .iterations(iterations)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("iterations");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 6, 10})
    @DisplayName("Invalid warmup iterations should throw exception")
    void invalidWarmupIterationsShouldThrowException(int warmup) {
        assertThatThrownBy(() -> new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .warmupIterations(warmup)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("warmupIterations");
    }

    @Test
    @DisplayName("Queue depth exceeding threads*2 should throw exception")
    void excessiveQueueDepthShouldThrowException() {
        assertThatThrownBy(() -> new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .threads(4)
                .queueDepth(10) // > 4 * 2
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("queueDepth");
    }

    @Test
    @DisplayName("Zero queue depth should throw exception")
    void zeroQueueDepthShouldThrowException() {
        assertThatThrownBy(() -> new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .queueDepth(0)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("queueDepth");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 3, 5})
    @DisplayName("Invalid verbosity should throw exception")
    void invalidVerbosityShouldThrowException(int verbosity) {
        assertThatThrownBy(() -> new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .verbosity(verbosity)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verbosity");
    }

    @ParameterizedTest
    @CsvSource({"50", "6000"})
    @DisplayName("Invalid metrics poll interval should throw exception")
    void invalidPollIntervalShouldThrowException(long millis) {
        assertThatThrownBy(() -> new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .metricsPollInterval(Duration.ofMillis(millis))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("metricsPollInterval");
    }

    @Test
    @DisplayName("Missing CSV report format should throw exception")
    void missingCsvFormatShouldThrowException() {
        assertThatThrownBy(() -> new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .reportFormats(Collections.singleton(BenchmarkConfig.ReportFormat.JSON))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CSV");
    }

    @Test
    @DisplayName("Missing JSON report format should throw exception")
    void missingJsonFormatShouldThrowException() {
        assertThatThrownBy(() -> new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .reportFormats(Collections.singleton(BenchmarkConfig.ReportFormat.CSV))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON");
    }

    @Test
    @DisplayName("Embed charts without HTML should throw exception")
    void embedChartsWithoutHtmlShouldThrowException() {
        assertThatThrownBy(() -> new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .embedCharts(true)
                .reportFormats(Arrays.asList(BenchmarkConfig.ReportFormat.CSV, BenchmarkConfig.ReportFormat.JSON))
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embedCharts");
    }

    // ==================== Null Safety Tests ====================

    @Test
    @DisplayName("Null test directory should throw exception")
    void nullTestDirectoryShouldThrowException() {
        assertThatThrownBy(() -> new BenchmarkConfig.Builder()
                .testDirectory(null)
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Null test type should throw exception")
    void nullTestTypeShouldThrowException() {
        assertThatThrownBy(() -> new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(null)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Null IO mode should throw exception")
    void nullIoModeShouldThrowException() {
        assertThatThrownBy(() -> new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .ioMode(null)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    // ==================== Helper Methods Tests ====================

    @Test
    @DisplayName("hasRandomWorkloads should return true only for random types")
    void hasRandomWorkloadsShouldWorkCorrectly() {
        BenchmarkConfig seqConfig = new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .build();

        BenchmarkConfig randConfig = new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.RAND_READ)
                .build();

        assertThat(seqConfig.hasRandomWorkloads()).isFalse();
        assertThat(randConfig.hasRandomWorkloads()).isTrue();
    }

    @Test
    @DisplayName("hasSequentialWorkloads should return true only for sequential types")
    void hasSequentialWorkloadsShouldWorkCorrectly() {
        BenchmarkConfig randConfig = new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.RAND_WRITE)
                .build();

        BenchmarkConfig seqConfig = new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.SEQ_WRITE)
                .build();

        assertThat(randConfig.hasSequentialWorkloads()).isFalse();
        assertThat(seqConfig.hasSequentialWorkloads()).isTrue();
    }

    @Test
    @DisplayName("toString should include all fields")
    void toStringShouldIncludeAllFields() {
        BenchmarkConfig config = new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .build();

        String str = config.toString();
        assertThat(str).contains("BenchmarkConfig");
        assertThat(str).contains("testDirectory");
        assertThat(str).contains("fileSizeBytes");
        assertThat(str).contains("sessionId");
    }
}
