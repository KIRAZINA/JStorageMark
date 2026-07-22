package com.kira.jstoragemark.core;

import com.kira.jstoragemark.config.BenchmarkConfig;
import com.kira.jstoragemark.fs.BenchmarkPaths;
import com.kira.jstoragemark.result.BenchmarkResult;
import com.kira.jstoragemark.metrics.MetricsSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class BenchmarkRunnerUnitTest {

    @TempDir
    Path tempDir;

    private BenchmarkConfig config;
    private BenchmarkPaths paths;

    private static final long ONE_GB = 1024L * 1024 * 1024;

    @BeforeEach
    void setUp() throws IOException {
        config = new BenchmarkConfig.Builder()
                .testDirectory(tempDir)
                .addTestType(BenchmarkConfig.TestType.SEQ_WRITE)
                .fileSizeBytes(ONE_GB)
                .blockSizeBytes(64 * 1024)
                .threads(2)
                .iterations(2)
                .queueDepth(4)
                .retainTestFiles(false)
                .build();
        paths = new BenchmarkPaths(tempDir, config.getSessionId());
        Files.createDirectories(tempDir);
    }

    @AfterEach
    void tearDown() {
        if (paths != null) {
            paths.cleanupSessionFiles(false);
        }
    }

    @Test
    @DisplayName("RunAll should complete successfully with basic config")
    void runAllShouldCompleteSuccessfully() throws Exception {
        BenchmarkRunner runner = new BenchmarkRunner(config, paths);

        List<BenchmarkResult> results = runner.runAll();

        assertThat(results).isNotEmpty();
        assertThat(results).hasSize(config.getIterations());
    }

    @Test
    @DisplayName("RunAll should produce valid results")
    void runAllShouldProduceValidResults() throws Exception {
        BenchmarkRunner runner = new BenchmarkRunner(config, paths);

        List<BenchmarkResult> results = runner.runAll();

        for (BenchmarkResult result : results) {
            assertThat(result.runId()).isNotNull().isNotEmpty();
            assertThat(result.testType()).isNotEmpty();
            assertThat(result.throughputMBps()).isGreaterThan(0);
            assertThat(result.elapsed().toMillis()).isGreaterThan(0);
            assertThat(result.iops()).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("RunAll should handle sequential write test")
    void runAllShouldHandleSequentialWrite() throws Exception {
        BenchmarkConfig writeConfig = new BenchmarkConfig.Builder()
                .testDirectory(tempDir)
                .addTestType(BenchmarkConfig.TestType.SEQ_WRITE)
                .fileSizeBytes(ONE_GB)
                .blockSizeBytes(32 * 1024)
                .threads(1)
                .iterations(1)
                .build();
        BenchmarkPaths writePaths = new BenchmarkPaths(tempDir, writeConfig.getSessionId());
        BenchmarkRunner runner = new BenchmarkRunner(writeConfig, writePaths);

        List<BenchmarkResult> results = runner.runAll();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).testType()).isEqualTo("SEQ_WRITE");
    }

    @Test
    @DisplayName("RunAll should handle random write test")
    void runAllShouldHandleRandomWrite() throws Exception {
        BenchmarkConfig randConfig = new BenchmarkConfig.Builder()
                .testDirectory(tempDir)
                .addTestType(BenchmarkConfig.TestType.RAND_WRITE)
                .fileSizeBytes(ONE_GB)
                .blockSizeBytes(16 * 1024)
                .threads(1)
                .iterations(1)
                .build();
        BenchmarkPaths randPaths = new BenchmarkPaths(tempDir, randConfig.getSessionId());
        BenchmarkRunner runner = new BenchmarkRunner(randConfig, randPaths);

        List<BenchmarkResult> results = runner.runAll();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).testType()).isEqualTo("RAND_WRITE");
    }

    @Test
    @DisplayName("StartMetricsPolling should begin collecting metrics")
    void startMetricsPollingShouldCollectMetrics() throws Exception {
        BenchmarkRunner runner = new BenchmarkRunner(config, paths);

        runner.startMetricsPolling();
        Thread.sleep(100);

        List<MetricsSnapshot> metrics = runner.getMetricsLog();

        assertThat(metrics).isNotEmpty();
    }

    @Test
    @DisplayName("GetMetricsLog should return unmodifiable list")
    void getMetricsLogShouldReturnUnmodifiableList() throws Exception {
        BenchmarkRunner runner = new BenchmarkRunner(config, paths);
        runner.startMetricsPolling();
        Thread.sleep(50);

        List<MetricsSnapshot> metrics = runner.getMetricsLog();

        assertThatThrownBy(() -> metrics.add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Should work with single thread")
    void shouldWorkWithSingleThread() throws Exception {
        BenchmarkConfig singleConfig = new BenchmarkConfig.Builder()
                .testDirectory(tempDir)
                .addTestType(BenchmarkConfig.TestType.SEQ_WRITE)
                .fileSizeBytes(ONE_GB)
                .threads(1)
                .iterations(1)
                .build();
        BenchmarkPaths singlePaths = new BenchmarkPaths(tempDir, singleConfig.getSessionId());
        BenchmarkRunner runner = new BenchmarkRunner(singleConfig, singlePaths);

        List<BenchmarkResult> results = runner.runAll();

        assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("Should use random seed when provided")
    void shouldUseRandomSeed() throws Exception {
        BenchmarkConfig seedConfig = new BenchmarkConfig.Builder()
                .testDirectory(tempDir)
                .addTestType(BenchmarkConfig.TestType.RAND_WRITE)
                .fileSizeBytes(ONE_GB)
                .threads(1)
                .iterations(1)
                .randomSeed(12345L)
                .build();
        BenchmarkPaths seedPaths = new BenchmarkPaths(tempDir, seedConfig.getSessionId());
        BenchmarkRunner runner = new BenchmarkRunner(seedConfig, seedPaths);

        List<BenchmarkResult> results = runner.runAll();

        assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("Constructor should throw on null config")
    void constructorShouldThrowOnNullConfig() {
        assertThatThrownBy(() -> new BenchmarkRunner(null, paths))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Constructor should throw on null paths")
    void constructorShouldThrowOnNullPaths() {
        assertThatThrownBy(() -> new BenchmarkRunner(config, null))
                .isInstanceOf(NullPointerException.class);
    }
}
