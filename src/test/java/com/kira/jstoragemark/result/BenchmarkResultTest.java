package com.kira.jstoragemark.result;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for BenchmarkResult data class.
 */
class BenchmarkResultTest {

    private static final Instant TEST_TIMESTAMP = Instant.parse("2024-01-15T10:30:00Z");

    // ==================== Constructor Tests ====================

    @Test
    @DisplayName("Constructor should set all fields correctly")
    void constructorShouldSetAllFields() {
        BenchmarkResult result = new BenchmarkResult(
                1,
                "SEQ_READ",
                1024L * 1024 * 1024,
                Duration.ofMillis(1000),
                100.5,
                10.2,
                1000.0,
                TEST_TIMESTAMP
        );

        assertThat(result.getRunId()).isEqualTo(1);
        assertThat(result.getTestType()).isEqualTo("SEQ_READ");
        assertThat(result.getBytesProcessed()).isEqualTo(1024L * 1024 * 1024);
        assertThat(result.getElapsed()).isEqualTo(Duration.ofMillis(1000));
        assertThat(result.getThroughputMBps()).isEqualTo(100.5);
        assertThat(result.getAvgLatencyMs()).isEqualTo(10.2);
        assertThat(result.getIops()).isEqualTo(1000.0);
        assertThat(result.getTimestamp()).isEqualTo(TEST_TIMESTAMP);
    }

    @Test
    @DisplayName("Constructor should accept various test types")
    void constructorShouldAcceptVariousTestTypes() {
        String[] testTypes = {"SEQ_READ", "SEQ_WRITE", "RAND_READ", "RAND_WRITE"};

        for (String type : testTypes) {
            BenchmarkResult result = new BenchmarkResult(
                    1, type, 1024, Duration.ofMillis(100),
                    100.0, 10.0, 1000.0, TEST_TIMESTAMP
            );
            assertThat(result.getTestType()).isEqualTo(type);
        }
    }

    @Test
    @DisplayName("Constructor should handle zero values")
    void constructorShouldHandleZeroValues() {
        BenchmarkResult result = new BenchmarkResult(
                0, "TEST", 0, Duration.ZERO,
                0.0, 0.0, 0.0, TEST_TIMESTAMP
        );

        assertThat(result.getRunId()).isZero();
        assertThat(result.getBytesProcessed()).isZero();
        assertThat(result.getElapsed()).isEqualTo(Duration.ZERO);
        assertThat(result.getThroughputMBps()).isZero();
    }

    @Test
    @DisplayName("Constructor should handle negative values")
    void constructorShouldHandleNegativeValues() {
        // The class allows negative values (validation is elsewhere)
        BenchmarkResult result = new BenchmarkResult(
                -1, "TEST", -100, Duration.ofMillis(-1000),
                -50.0, -10.0, -500.0, TEST_TIMESTAMP
        );

        assertThat(result.getRunId()).isNegative();
        assertThat(result.getBytesProcessed()).isNegative();
        assertThat(result.getThroughputMBps()).isNegative();
    }

    @Test
    @DisplayName("Constructor should handle large values")
    void constructorShouldHandleLargeValues() {
        BenchmarkResult result = new BenchmarkResult(
                Integer.MAX_VALUE,
                "SEQ_READ",
                Long.MAX_VALUE,
                Duration.ofMillis(Long.MAX_VALUE),
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                Double.MAX_VALUE,
                TEST_TIMESTAMP
        );

        assertThat(result.getRunId()).isEqualTo(Integer.MAX_VALUE);
        assertThat(result.getBytesProcessed()).isEqualTo(Long.MAX_VALUE);
    }

    // ==================== Null Safety Tests ====================

    @Test
    @DisplayName("Constructor should throw on null test type")
    void constructorShouldThrowOnNullTestType() {
        assertThatThrownBy(() -> new BenchmarkResult(
                1, null, 1024, Duration.ofMillis(100),
                100.0, 10.0, 1000.0, TEST_TIMESTAMP
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Constructor should throw on null elapsed")
    void constructorShouldThrowOnNullElapsed() {
        assertThatThrownBy(() -> new BenchmarkResult(
                1, "SEQ_READ", 1024, null,
                100.0, 10.0, 1000.0, TEST_TIMESTAMP
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Constructor should throw on null timestamp")
    void constructorShouldThrowOnNullTimestamp() {
        assertThatThrownBy(() -> new BenchmarkResult(
                1, "SEQ_READ", 1024, Duration.ofMillis(100),
                100.0, 10.0, 1000.0, null
        )).isInstanceOf(NullPointerException.class);
    }

    // ==================== Getter Tests ====================

    @Test
    @DisplayName("All getters should return correct values")
    void allGettersShouldWork() {
        Duration elapsed = Duration.ofSeconds(5);
        BenchmarkResult result = new BenchmarkResult(
                42, "SEQ_WRITE", 2048L, elapsed,
                200.0, 5.5, 2000.0, TEST_TIMESTAMP
        );

        assertThat(result.getRunId()).isEqualTo(42);
        assertThat(result.getTestType()).isEqualTo("SEQ_WRITE");
        assertThat(result.getBytesProcessed()).isEqualTo(2048L);
        assertThat(result.getElapsed()).isEqualTo(elapsed);
        assertThat(result.getThroughputMBps()).isEqualTo(200.0);
        assertThat(result.getAvgLatencyMs()).isEqualTo(5.5);
        assertThat(result.getIops()).isEqualTo(2000.0);
        assertThat(result.getTimestamp()).isEqualTo(TEST_TIMESTAMP);
    }

    // ==================== ToString Tests ====================

    @Test
    @DisplayName("ToString should contain all field values")
    void toStringShouldContainAllFields() {
        BenchmarkResult result = new BenchmarkResult(
                1, "SEQ_READ", 1024, Duration.ofMillis(100),
                100.0, 10.0, 1000.0, TEST_TIMESTAMP
        );

        String str = result.toString();

        assertThat(str)
                .contains("BenchmarkResult")
                .contains("runId=1")
                .contains("testType='SEQ_READ'")
                .contains("bytesProcessed=1024")
                .contains("elapsed=100ms")
                .contains("throughputMBps=100.0")
                .contains("avgLatencyMs=10.0")
                .contains("iops=1000.0")
                .contains("timestamp=" + TEST_TIMESTAMP);
    }

    @Test
    @DisplayName("ToString should handle special characters in test type")
    void toStringShouldHandleSpecialCharacters() {
        BenchmarkResult result = new BenchmarkResult(
                1, "TEST_TYPE-123", 1024, Duration.ofMillis(100),
                100.0, 10.0, 1000.0, TEST_TIMESTAMP
        );

        String str = result.toString();

        assertThat(str).contains("TEST_TYPE-123");
    }

    // ==================== Calculation Verification Tests ====================

    @ParameterizedTest
    @CsvSource({
            "1000, 1000, 1000.0",      // 1 second for 1 GB = ~1000 MB/s
            "2000, 1000, 500.0",       // 2 seconds for 1 GB = ~500 MB/s
            "500, 1000, 2000.0"        // 0.5 seconds for 1 GB = ~2000 MB/s
    })
    @DisplayName("Throughput calculation should be correct")
    void throughputCalculationShouldBeCorrect(long elapsedMs, long bytes, double expectedThroughput) {
        // Note: This is a documentation test showing expected values
        // Actual calculation is done in BenchmarkRunner
        Duration elapsed = Duration.ofMillis(elapsedMs);
        double actualThroughput = (bytes / (1024.0 * 1024.0)) / (elapsedMs / 1000.0);

        BenchmarkResult result = new BenchmarkResult(
                1, "TEST", bytes, elapsed,
                actualThroughput, 10.0, 1000.0, TEST_TIMESTAMP
        );

        assertThat(result.getThroughputMBps()).isCloseTo(expectedThroughput, within(0.1));
    }

    @ParameterizedTest
    @CsvSource({
            "1000, 100, 10.0",         // 1000ms / 100 ops = 10ms per op
            "500, 100, 5.0",           // 500ms / 100 ops = 5ms per op
            "2000, 50, 40.0"           // 2000ms / 50 ops = 40ms per op
    })
    @DisplayName("Latency calculation should be correct")
    void latencyCalculationShouldBeCorrect(long elapsedMs, long ops, double expectedLatency) {
        Duration elapsed = Duration.ofMillis(elapsedMs);
        double actualLatency = (double) elapsedMs / ops;

        BenchmarkResult result = new BenchmarkResult(
                1, "TEST", 1024, elapsed,
                100.0, actualLatency, ops, TEST_TIMESTAMP
        );

        assertThat(result.getAvgLatencyMs()).isEqualTo(expectedLatency);
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Should handle very long durations")
    void shouldHandleVeryLongDurations() {
        Duration longDuration = Duration.ofHours(1);
        BenchmarkResult result = new BenchmarkResult(
                1, "SEQ_READ", 1024, longDuration,
                100.0, 10.0, 1000.0, TEST_TIMESTAMP
        );

        assertThat(result.getElapsed()).isEqualTo(longDuration);
        assertThat(result.toString()).contains("3600000ms"); // 1 hour in ms
    }

    @Test
    @DisplayName("Should handle fractional milliseconds")
    void shouldHandleFractionalMilliseconds() {
        // Duration doesn't support fractions, but we can test nanos
        Duration preciseDuration = Duration.ofNanos(1500000); // 1.5ms
        BenchmarkResult result = new BenchmarkResult(
                1, "SEQ_READ", 1024, preciseDuration,
                100.0, 1.5, 1000.0, TEST_TIMESTAMP
        );

        assertThat(result.getElapsed().toNanos()).isEqualTo(1500000);
    }

    @Test
    @DisplayName("Should handle empty test type string")
    void shouldHandleEmptyTestType() {
        BenchmarkResult result = new BenchmarkResult(
                1, "", 1024, Duration.ofMillis(100),
                100.0, 10.0, 1000.0, TEST_TIMESTAMP
        );

        assertThat(result.getTestType()).isEmpty();
    }

    @Test
    @DisplayName("Should preserve nanosecond precision in timestamp")
    void shouldPreserveTimestampPrecision() {
        Instant preciseTimestamp = Instant.parse("2024-01-15T10:30:00.123456789Z");
        BenchmarkResult result = new BenchmarkResult(
                1, "SEQ_READ", 1024, Duration.ofMillis(100),
                100.0, 10.0, 1000.0, preciseTimestamp
        );

        assertThat(result.getTimestamp()).isEqualTo(preciseTimestamp);
        assertThat(result.getTimestamp().getNano()).isEqualTo(123456789);
    }
}
