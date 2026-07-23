package com.kira.jstoragemark.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class MixedWorkloadTest {

    @Test
    @DisplayName("MIXED_RW test type should be valid")
    void mixedRWTestTypeShouldBeValid() {
        BenchmarkConfig config = new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.MIXED_RW)
                .build();

        assertThat(config.getTestTypes()).contains(BenchmarkConfig.TestType.MIXED_RW);
        assertThat(config.hasRandomWorkloads()).isTrue();
    }

    @Test
    @DisplayName("Mixed read percent should default to 70")
    void mixedReadPercentShouldDefaultTo70() {
        BenchmarkConfig config = new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.MIXED_RW)
                .build();

        assertThat(config.getMixedReadPercent()).isEqualTo(70);
    }

    @Test
    @DisplayName("Mixed read percent should be configurable")
    void mixedReadPercentShouldBeConfigurable() {
        BenchmarkConfig config = new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.MIXED_RW)
                .mixedReadPercent(30)
                .build();

        assertThat(config.getMixedReadPercent()).isEqualTo(30);
    }

    @Test
    @DisplayName("Mixed read percent should reject out-of-range values")
    void mixedReadPercentShouldRejectOutOfRange() {
        assertThatThrownBy(() -> new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.MIXED_RW)
                .mixedReadPercent(-1)
                .build())
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.MIXED_RW)
                .mixedReadPercent(101)
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Mixed workload config toString includes mixedReadPercent")
    void mixedWorkloadToString() {
        BenchmarkConfig config = new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.MIXED_RW)
                .mixedReadPercent(50)
                .build();

        String str = config.toString();
        assertThat(str).contains("mixedReadPercent=50");
    }
}
