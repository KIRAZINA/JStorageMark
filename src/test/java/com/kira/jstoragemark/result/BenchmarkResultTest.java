package com.kira.jstoragemark.result;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BenchmarkResultTest {

    private static final Instant TEST_TIMESTAMP = Instant.parse("2024-01-15T10:30:00Z");

    @Test
    @DisplayName("Constructor should set all fields correctly")
    void constructorShouldSetAllFields() {
        BenchmarkResult result = new BenchmarkResult(
                "run-001-thread-00",
                "SEQ_READ",
                1024L * 1024 * 1024,
                Duration.ofMillis(1000),
                Duration.ofMillis(1000).toNanos(),
                100.5,
                10.2,
                10.2 * 1_000_000.0,
                1000.0,
                TEST_TIMESTAMP
        );

        assertThat(result.runId()).isEqualTo("run-001-thread-00");
        assertThat(result.testType()).isEqualTo("SEQ_READ");
        assertThat(result.bytesProcessed()).isEqualTo(1024L * 1024 * 1024);
        assertThat(result.elapsed()).isEqualTo(Duration.ofMillis(1000));
        assertThat(result.throughputMBps()).isEqualTo(100.5);
        assertThat(result.avgLatencyMs()).isEqualTo(10.2);
        assertThat(result.iops()).isEqualTo(1000.0);
        assertThat(result.timestamp()).isEqualTo(TEST_TIMESTAMP);
    }

    @Test
    @DisplayName("Constructor should accept various test types")
    void constructorShouldAcceptVariousTestTypes() {
        String[] testTypes = {"SEQ_READ", "SEQ_WRITE", "RAND_READ", "RAND_WRITE"};

        for (String type : testTypes) {
            BenchmarkResult result = new BenchmarkResult(
                    "run-001-thread-00", type, 1024L, Duration.ofMillis(100),
                    Duration.ofMillis(100).toNanos(),
                    100.0, 10.0, 10.0 * 1_000_000.0, 1000.0, TEST_TIMESTAMP
            );
            assertThat(result.testType()).isEqualTo(type);
        }
    }

    @Test
    @DisplayName("Constructor should handle zero values")
    void constructorShouldHandleZeroValues() {
        BenchmarkResult result = new BenchmarkResult(
                "run-000-thread-00", "TEST", 0L, Duration.ZERO,
                Duration.ZERO.toNanos(),
                0.0, 0.0, 0.0, 0.0, TEST_TIMESTAMP
        );

        assertThat(result.bytesProcessed()).isZero();
        assertThat(result.elapsed()).isEqualTo(Duration.ZERO);
        assertThat(result.throughputMBps()).isZero();
    }

    @Test
    @DisplayName("Constructor should handle negative values")
    void constructorShouldHandleNegativeValues() {
        BenchmarkResult result = new BenchmarkResult(
                "run-000-thread-00", "TEST", -100L, Duration.ofMillis(-1000),
                Duration.ofMillis(-1000).toNanos(),
                -50.0, -10.0, -10.0 * 1_000_000.0, -500.0, TEST_TIMESTAMP
        );

        assertThat(result.bytesProcessed()).isNegative();
        assertThat(result.throughputMBps()).isNegative();
    }

    @Test
    @DisplayName("Constructor should handle large values")
    void constructorShouldHandleLargeValues() {
        Duration largeDuration = Duration.ofSeconds(1_000_000_000);
        BenchmarkResult result = new BenchmarkResult(
                "run-999-thread-99",
                "SEQ_READ",
                Long.MAX_VALUE,
                largeDuration,
                largeDuration.toNanos(),
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                TEST_TIMESTAMP
        );

        assertThat(result.bytesProcessed()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("Constructor accepts null test type")
    void constructorAcceptsNullTestType() {
        BenchmarkResult result = new BenchmarkResult(
                "run-001-thread-00", null, 1024L, Duration.ofMillis(100),
                Duration.ofMillis(100).toNanos(),
                100.0, 10.0, 10.0 * 1_000_000.0, 1000.0, TEST_TIMESTAMP
        );
        assertThat(result.testType()).isNull();
    }

    @Test
    @DisplayName("Constructor accepts null elapsed")
    void constructorAcceptsNullElapsed() {
        BenchmarkResult result = new BenchmarkResult(
                "run-001-thread-00", "SEQ_READ", 1024L, null,
                Duration.ofMillis(100).toNanos(),
                100.0, 10.0, 10.0 * 1_000_000.0, 1000.0, TEST_TIMESTAMP
        );
        assertThat(result.elapsed()).isNull();
    }

    @Test
    @DisplayName("Constructor accepts null timestamp")
    void constructorAcceptsNullTimestamp() {
        BenchmarkResult result = new BenchmarkResult(
                "run-001-thread-00", "SEQ_READ", 1024L, Duration.ofMillis(100),
                Duration.ofMillis(100).toNanos(),
                100.0, 10.0, 10.0 * 1_000_000.0, 1000.0, null
        );
        assertThat(result.timestamp()).isNull();
    }

    @Test
    @DisplayName("All accessors should return correct values")
    void allAccessorsShouldWork() {
        Duration elapsed = Duration.ofSeconds(5);
        BenchmarkResult result = new BenchmarkResult(
                "run-042-thread-00", "SEQ_WRITE", 2048L, elapsed,
                elapsed.toNanos(),
                200.0, 5.5, 5.5 * 1_000_000.0, 2000.0, TEST_TIMESTAMP
        );

        assertThat(result.runId()).isEqualTo("run-042-thread-00");
        assertThat(result.testType()).isEqualTo("SEQ_WRITE");
        assertThat(result.bytesProcessed()).isEqualTo(2048L);
        assertThat(result.elapsed()).isEqualTo(elapsed);
        assertThat(result.throughputMBps()).isEqualTo(200.0);
        assertThat(result.avgLatencyMs()).isEqualTo(5.5);
        assertThat(result.iops()).isEqualTo(2000.0);
        assertThat(result.timestamp()).isEqualTo(TEST_TIMESTAMP);
    }

    @Test
    @DisplayName("ToString should contain all field values")
    void toStringShouldContainAllFields() {
        BenchmarkResult result = new BenchmarkResult(
                "run-001-thread-00", "SEQ_READ", 1024L, Duration.ofMillis(100),
                Duration.ofMillis(100).toNanos(),
                100.0, 10.0, 10.0 * 1_000_000.0, 1000.0, TEST_TIMESTAMP
        );

        String str = result.toString();
        assertThat(str)
                .contains("BenchmarkResult")
                .contains("runId=run-001-thread-00")
                .contains("testType=SEQ_READ")
                .contains("bytesProcessed=1024")
                .contains("throughputMBps=100.0")
                .contains("avgLatencyMs=10.0")
                .contains("iops=1000.0")
                .contains("timestamp=" + TEST_TIMESTAMP);
    }

    @ParameterizedTest
    @CsvSource({
            "1000, 104857600, 100.0",
            "2000, 104857600, 50.0",
            "500, 52428800, 100.0"
    })
    @DisplayName("Throughput calculation should be correct")
    void throughputCalculationShouldBeCorrect(long elapsedMs, long bytes, double expectedThroughput) {
        Duration elapsed = Duration.ofMillis(elapsedMs);
        double actualThroughput = (bytes / (1024.0 * 1024.0)) / (elapsedMs / 1000.0);

        BenchmarkResult result = new BenchmarkResult(
                "run-001-thread-00", "TEST", bytes, elapsed,
                elapsed.toNanos(),
                actualThroughput, 10.0, 10.0 * 1_000_000.0, 1000.0, TEST_TIMESTAMP
        );

        assertThat(result.throughputMBps()).isCloseTo(expectedThroughput, within(0.1));
    }

    @ParameterizedTest
    @CsvSource({
            "1000, 100, 10.0",
            "500, 100, 5.0",
            "2000, 50, 40.0"
    })
    @DisplayName("Latency calculation should be correct")
    void latencyCalculationShouldBeCorrect(long elapsedMs, long ops, double expectedLatency) {
        Duration elapsed = Duration.ofMillis(elapsedMs);
        double actualLatency = (double) elapsedMs / ops;

        BenchmarkResult result = new BenchmarkResult(
                "run-001-thread-00", "TEST", 1024L, elapsed,
                elapsed.toNanos(),
                100.0, actualLatency, actualLatency * 1_000_000.0, (double) ops, TEST_TIMESTAMP
        );

        assertThat(result.avgLatencyMs()).isEqualTo(expectedLatency);
    }

    @Test
    @DisplayName("Should handle very long durations")
    void shouldHandleVeryLongDurations() {
        Duration longDuration = Duration.ofHours(1);
        BenchmarkResult result = new BenchmarkResult(
                "run-001-thread-00", "SEQ_READ", 1024L, longDuration,
                longDuration.toNanos(),
                100.0, 10.0, 10.0 * 1_000_000.0, 1000.0, TEST_TIMESTAMP
        );

        assertThat(result.elapsed()).isEqualTo(longDuration);
    }

    @Test
    @DisplayName("Should handle fractional milliseconds")
    void shouldHandleFractionalMilliseconds() {
        Duration preciseDuration = Duration.ofNanos(1500000);
        BenchmarkResult result = new BenchmarkResult(
                "run-001-thread-00", "SEQ_READ", 1024L, preciseDuration,
                preciseDuration.toNanos(),
                100.0, 1.5, 1.5 * 1_000_000.0, 1000.0, TEST_TIMESTAMP
        );

        assertThat(result.elapsed().toNanos()).isEqualTo(1500000);
    }

    @Test
    @DisplayName("Should handle empty test type string")
    void shouldHandleEmptyTestType() {
        BenchmarkResult result = new BenchmarkResult(
                "run-001-thread-00", "", 1024L, Duration.ofMillis(100),
                Duration.ofMillis(100).toNanos(),
                100.0, 10.0, 10.0 * 1_000_000.0, 1000.0, TEST_TIMESTAMP
        );

        assertThat(result.testType()).isEmpty();
    }

    @Test
    @DisplayName("Should preserve nanosecond precision in timestamp")
    void shouldPreserveTimestampPrecision() {
        Instant preciseTimestamp = Instant.parse("2024-01-15T10:30:00.123456789Z");
        BenchmarkResult result = new BenchmarkResult(
                "run-001-thread-00", "SEQ_READ", 1024L, Duration.ofMillis(100),
                Duration.ofMillis(100).toNanos(),
                100.0, 10.0, 10.0 * 1_000_000.0, 1000.0, preciseTimestamp
        );

        assertThat(result.timestamp()).isEqualTo(preciseTimestamp);
        assertThat(result.timestamp().getNano()).isEqualTo(123456789);
    }
}
