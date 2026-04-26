package com.kira.jstoragemark.metrics;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for MetricsSnapshot data class.
 */
class MetricsSnapshotTest {

    private static final Instant TEST_TIMESTAMP = Instant.parse("2024-01-15T10:30:00Z");

    // ==================== Constructor Tests ====================

    @Test
    @DisplayName("Constructor should set all fields correctly")
    void constructorShouldSetAllFields() {
        MetricsSnapshot snapshot = new MetricsSnapshot(
                TEST_TIMESTAMP,
                45.5,
                60.0,
                1000L,
                500L,
                1024L * 1024,
                512L * 1024,
                65.0
        );

        assertThat(snapshot.getTimestamp()).isEqualTo(TEST_TIMESTAMP);
        assertThat(snapshot.getCpuUsagePercent()).isEqualTo(45.5);
        assertThat(snapshot.getRamUsagePercent()).isEqualTo(60.0);
        assertThat(snapshot.getDiskReads()).isEqualTo(1000L);
        assertThat(snapshot.getDiskWrites()).isEqualTo(500L);
        assertThat(snapshot.getDiskReadBytes()).isEqualTo(1024L * 1024);
        assertThat(snapshot.getDiskWriteBytes()).isEqualTo(512L * 1024);
        assertThat(snapshot.getDiskTemperatureC()).isEqualTo(65.0);
    }

    @Test
    @DisplayName("Constructor should accept null temperature")
    void constructorShouldAcceptNullTemperature() {
        MetricsSnapshot snapshot = new MetricsSnapshot(
                TEST_TIMESTAMP,
                45.5,
                60.0,
                1000L,
                500L,
                1024L * 1024,
                512L * 1024,
                null
        );

        assertThat(snapshot.getDiskTemperatureC()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "0.0, 0.0",
            "100.0, 100.0",
            "50.0, 50.0",
            "25.5, 75.5"
    })
    @DisplayName("Constructor should accept various percentage values")
    void constructorShouldAcceptVariousPercentages(double cpu, double ram) {
        MetricsSnapshot snapshot = new MetricsSnapshot(
                TEST_TIMESTAMP, cpu, ram, 1000L, 500L, 1024L * 1024, 512L * 1024, null
        );

        assertThat(snapshot.getCpuUsagePercent()).isEqualTo(cpu);
        assertThat(snapshot.getRamUsagePercent()).isEqualTo(ram);
        assertThat(snapshot.getDiskReads()).isEqualTo(1000L);
        assertThat(snapshot.getDiskWrites()).isEqualTo(500L);
    }

    @Test
    @DisplayName("Constructor should handle boundary values")
    void constructorShouldHandleBoundaryValues() {
        MetricsSnapshot minValues = new MetricsSnapshot(
                TEST_TIMESTAMP, 0.0, 0.0, 0L, 0L, 0L, 0L, -273.15
        );
        MetricsSnapshot maxValues = new MetricsSnapshot(
                TEST_TIMESTAMP, 100.0, 100.0, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, 1000.0
        );

        assertThat(minValues.getCpuUsagePercent()).isZero();
        assertThat(maxValues.getCpuUsagePercent()).isEqualTo(100.0);
        assertThat(minValues.getDiskTemperatureC()).isEqualTo(-273.15);
        assertThat(maxValues.getDiskTemperatureC()).isEqualTo(1000.0);
    }

    @Test
    @DisplayName("Constructor should handle extreme double values")
    void constructorShouldHandleExtremeValues() {
        MetricsSnapshot snapshot = new MetricsSnapshot(
                TEST_TIMESTAMP,
                Double.MAX_VALUE,
                Double.MIN_VALUE,
                Long.MAX_VALUE,
                Long.MAX_VALUE,
                Long.MAX_VALUE,
                Long.MAX_VALUE,
                Double.NEGATIVE_INFINITY
        );

        assertThat(snapshot.getCpuUsagePercent()).isEqualTo(Double.MAX_VALUE);
        assertThat(snapshot.getRamUsagePercent()).isEqualTo(Double.MIN_VALUE);
        assertThat(snapshot.getDiskReads()).isEqualTo(Long.MAX_VALUE);
        assertThat(snapshot.getDiskTemperatureC()).isNegative();
    }

    @Test
    @DisplayName("Constructor should handle NaN values")
    void constructorShouldHandleNaN() {
        MetricsSnapshot snapshot = new MetricsSnapshot(
                TEST_TIMESTAMP,
                Double.NaN,
                Double.NaN,
                0L,
                0L,
                0L,
                0L,
                Double.NaN
        );

        assertThat(snapshot.getCpuUsagePercent()).isNaN();
        assertThat(snapshot.getRamUsagePercent()).isNaN();
        assertThat(snapshot.getDiskTemperatureC()).isNaN();
    }

    // ==================== Timestamp Tests ====================

    @Test
    @DisplayName("Should accept various timestamp formats")
    void shouldAcceptVariousTimestamps() {
        Instant[] timestamps = {
                Instant.EPOCH,
                Instant.now(),
                Instant.parse("2024-01-15T10:30:00.123456789Z")
        };

        for (Instant ts : timestamps) {
            MetricsSnapshot snapshot = new MetricsSnapshot(
                    ts, 50.0, 50.0, 1000L, 500L, 1024L * 1024, 512L * 1024, null
            );
            assertThat(snapshot.getTimestamp()).isEqualTo(ts);
        }
    }

    @Test
    @DisplayName("Should preserve nanosecond precision")
    void shouldPreserveNanosecondPrecision() {
        Instant precise = Instant.parse("2024-01-15T10:30:00.123456789Z");
        MetricsSnapshot snapshot = new MetricsSnapshot(
                precise, 50.0, 50.0, 1000L, 500L, 1024L * 1024, 512L * 1024, null
        );

        assertThat(snapshot.getTimestamp().getNano()).isEqualTo(123456789);
    }

    // ==================== ToString Tests ====================

    @Test
    @DisplayName("ToString should contain all field values")
    void toStringShouldContainAllFields() {
        MetricsSnapshot snapshot = new MetricsSnapshot(
                TEST_TIMESTAMP,
                45.5,
                60.0,
                1000L,
                500L,
                1024L * 1024,
                512L * 1024,
                65.0
        );

        String str = snapshot.toString();

        assertThat(str)
                .contains("MetricsSnapshot")
                .contains("timestamp=" + TEST_TIMESTAMP)
                .contains("cpuUsage=45.5")
                .contains("ramUsage=60.0")
                .contains("diskReads=1000")
                .contains("diskWrites=500")
                .contains("diskTemperatureC=65.0");
    }

    @Test
    @DisplayName("ToString should handle null temperature")
    void toStringShouldHandleNullTemperature() {
        MetricsSnapshot snapshot = new MetricsSnapshot(
                TEST_TIMESTAMP,
                45.5,
                60.0,
                1000L,
                500L,
                1024L * 1024,
                512L * 1024,
                null
        );

        String str = snapshot.toString();

        assertThat(str).contains("diskTemperatureC=null");
    }

    // ==================== Getter Tests ====================

    @Test
    @DisplayName("All getters should return correct values")
    void allGettersShouldWork() {
        Instant customTimestamp = Instant.parse("2024-06-01T12:00:00Z");
        MetricsSnapshot snapshot = new MetricsSnapshot(
                customTimestamp,
                75.5,
                80.0,
                1000L,
                500L,
                1024L * 1024,
                512L * 1024,
                55.5
        );

        assertThat(snapshot.getTimestamp()).isEqualTo(customTimestamp);
        assertThat(snapshot.getCpuUsagePercent()).isEqualTo(75.5);
        assertThat(snapshot.getRamUsagePercent()).isEqualTo(80.0);
        assertThat(snapshot.getDiskReads()).isEqualTo(1000L);
        assertThat(snapshot.getDiskTemperatureC()).isEqualTo(55.5);
    }

    // ==================== Real-world Scenarios ====================

    @Test
    @DisplayName("Should represent idle system state")
    void shouldRepresentIdleState() {
        MetricsSnapshot idle = new MetricsSnapshot(
                Instant.now(),
                2.0,
                30.0,
                100L,
                50L,
                1024L * 100,
                512L * 100,
                35.0
        );

        assertThat(idle.getCpuUsagePercent()).isLessThan(5.0);
        assertThat(idle.getDiskTemperatureC()).isLessThan(40.0);
    }

    @Test
    @DisplayName("Should represent high load system state")
    void shouldRepresentHighLoadState() {
        MetricsSnapshot highLoad = new MetricsSnapshot(
                Instant.now(),
                95.0,
                85.0,
                5000L,
                2500L,
                1024L * 1024 * 100,
                512L * 1024 * 100,
                75.0
        );

        assertThat(highLoad.getCpuUsagePercent()).isGreaterThan(90.0);
        assertThat(highLoad.getRamUsagePercent()).isGreaterThan(80.0);
    }

    @Test
    @DisplayName("Should handle temperature variations")
    void shouldHandleTemperatureVariations() {
        MetricsSnapshot cold = new MetricsSnapshot(
                Instant.now(), 50.0, 60.0, 1000L, 500L, 1024L * 1024, 512L * 1024, 10.0
        );
        MetricsSnapshot normal = new MetricsSnapshot(
                Instant.now(), 50.0, 60.0, 1000L, 500L, 1024L * 1024, 512L * 1024, 50.0
        );
        MetricsSnapshot hot = new MetricsSnapshot(
                Instant.now(), 50.0, 60.0, 1000L, 500L, 1024L * 1024, 512L * 1024, 90.0
        );

        assertThat(cold.getDiskTemperatureC()).isEqualTo(10.0);
        assertThat(normal.getDiskTemperatureC()).isEqualTo(50.0);
        assertThat(hot.getDiskTemperatureC()).isEqualTo(90.0);
    }
}
