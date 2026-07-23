package com.kira.jstoragemark.core;

import com.kira.jstoragemark.config.BenchmarkConfig;
import com.kira.jstoragemark.fs.BenchmarkPaths;
import com.kira.jstoragemark.result.BenchmarkResult;

import org.HdrHistogram.Histogram;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class LatencyPercentileTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Histogram correctly calculates percentiles for known distribution")
    void histogramCalculatesPercentilesCorrectly() {
        Histogram histogram = new Histogram(3600000000000L, 3);
        for (int i = 1; i <= 1000; i++) {
            histogram.recordValue(i * 1000); // 1000, 2000, ... 1000000 ns
        }

        double p50 = histogram.getValueAtPercentile(50.0);
        double p95 = histogram.getValueAtPercentile(95.0);
        double p99 = histogram.getValueAtPercentile(99.0);
        double p999 = histogram.getValueAtPercentile(99.9);

        assertThat(p50).isBetween(450_000.0, 600_000.0);
        assertThat(p95).isBetween(900_000.0, 999_000.0);
        assertThat(p99).isBetween(950_000.0, 1_100_000.0);
        assertThat(p999).isBetween(980_000.0, 1_100_000.0);
    }

    @Test
    @DisplayName("Benchmark produces latency percentiles in results")
    void benchmarkProducesLatencyPercentiles() throws Exception {
        BenchmarkConfig config = new BenchmarkConfig.Builder()
                .testDirectory(tempDir)
                .addTestType(BenchmarkConfig.TestType.SEQ_WRITE)
                .fileSizeBytes(1024L * 1024 * 1024)
                .blockSizeBytes(64 * 1024)
                .threads(1)
                .iterations(1)
                .queueDepth(1)
                .retainTestFiles(false)
                .build();

        BenchmarkPaths paths = new BenchmarkPaths(tempDir, config.getSessionId());
        BenchmarkRunner runner = new BenchmarkRunner(config, paths);

        List<BenchmarkResult> results = runner.runAll();

        assertThat(results).isNotEmpty();
        for (BenchmarkResult r : results) {
            assertThat(r.p50LatencyNs()).isGreaterThan(0);
            assertThat(r.p95LatencyNs()).isGreaterThanOrEqualTo(r.p50LatencyNs());
            assertThat(r.p99LatencyNs()).isGreaterThanOrEqualTo(r.p95LatencyNs());
            assertThat(r.p999LatencyNs()).isGreaterThanOrEqualTo(r.p99LatencyNs());
            assertThat(r.maxLatencyNs()).isGreaterThanOrEqualTo((long) r.p999LatencyNs());
        }
    }

    @Test
    @DisplayName("Percentile ordering is monotonic")
    void percentileOrderingIsMonotonic() {
        Histogram histogram = new Histogram(3600000000000L, 3);
        for (int i = 0; i < 10000; i++) {
            histogram.recordValue((long) (Math.random() * 1_000_000));
        }

        double p50 = histogram.getValueAtPercentile(50.0);
        double p95 = histogram.getValueAtPercentile(95.0);
        double p99 = histogram.getValueAtPercentile(99.0);
        double p999 = histogram.getValueAtPercentile(99.9);

        assertThat(p50).isLessThanOrEqualTo(p95);
        assertThat(p95).isLessThanOrEqualTo(p99);
        assertThat(p99).isLessThanOrEqualTo(p999);
    }

    @Test
    @DisplayName("Single value histogram has correct percentiles")
    void singleValueHistogram() {
        Histogram histogram = new Histogram(3600000000000L, 3);
        histogram.recordValue(4242);

        double p50 = histogram.getValueAtPercentile(50.0);
        double p999 = histogram.getValueAtPercentile(99.9);

        assertThat(p50).isBetween(4000.0, 4300.0);
        assertThat(p999).isBetween(4000.0, 4300.0);
        assertThat(histogram.getMaxValue()).isGreaterThanOrEqualTo(4242L);
    }
}
