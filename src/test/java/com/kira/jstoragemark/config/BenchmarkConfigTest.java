package com.kira.jstoragemark.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class BenchmarkConfigTest {

    @Test
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
                .build();

        assertThat(config.getFileSizeBytes()).isEqualTo(2L * 1024 * 1024 * 1024);
        assertThat(config.getBlockSizeBytes()).isEqualTo(64 * 1024);
        assertThat(config.getThreads()).isEqualTo(4);
        assertThat(config.getIterations()).isEqualTo(5);
        assertThat(config.getTestTypes()).contains(BenchmarkConfig.TestType.SEQ_WRITE);
    }

    @Test
    void invalidBlockSizeShouldThrowException() {
        assertThatThrownBy(() -> new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .blockSizeBytes(2) // too small
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blockSizeBytes");
    }

    @Test
    void invalidIterationsShouldThrowException() {
        assertThatThrownBy(() -> new BenchmarkConfig.Builder()
                .testDirectory(Path.of("./testdir"))
                .addTestType(BenchmarkConfig.TestType.SEQ_READ)
                .iterations(1) // less than 3
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("iterations");
    }
}
