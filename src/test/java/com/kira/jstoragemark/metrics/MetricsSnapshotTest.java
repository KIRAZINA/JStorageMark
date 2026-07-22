package com.kira.jstoragemark.metrics;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MetricsSnapshotTest {

    private static final Instant TEST_TIMESTAMP = Instant.parse("2024-01-15T10:30:00Z");

    @Test
    @DisplayName("Constructor should set all fields correctly")
    void constructorShouldSetAllFields() {
        MetricsSnapshot snapshot = new MetricsSnapshot(
                TEST_TIMESTAMP, 45.5, 60.0, 1000L, 500L, 1024L * 1024, 512L * 1024, 65.0
        );

        assertThat(snapshot.timestamp()).isEqualTo(TEST_TIMESTAMP);
        assertThat(snapshot.cpuUsagePercent()).isEqualTo(45.5);
        assertThat(snapshot.ramUsagePercent()).isEqualTo(60.0);
        assertThat(snapshot.diskReads()).isEqualTo(1000L);
        assertThat(snapshot.diskWrites()).isEqualTo(500L);
        assertThat(snapshot.diskReadBytes()).isEqualTo(1024L * 1024);
        assertThat(snapshot.diskWriteBytes()).isEqualTo(512L * 1024);
        assertThat(snapshot.diskTemperatureC()).isEqualTo(65.0);
    }

    @Test
    @DisplayName("Constructor should accept null temperature")
    void constructorShouldAcceptNullTemperature() {
        MetricsSnapshot snapshot = new MetricsSnapshot(
                TEST_TIMESTAMP, 45.5, 60.0, 1000L, 500L, 1024L * 1024, 512L * 1024, null
        );

        assertThat(snapshot.diskTemperatureC()).isNull();
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

        assertThat(snapshot.cpuUsagePercent()).isEqualTo(cpu);
        assertThat(snapshot.ramUsagePercent()).isEqualTo(ram);
        assertThat(snapshot.diskReads()).isEqualTo(1000L);
        assertThat(snapshot.diskWrites()).isEqualTo(500L);
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

        assertThat(minValues.cpuUsagePercent()).isZero();
        assertThat(maxValues.cpuUsagePercent()).isEqualTo(100.0);
        assertThat(minValues.diskTemperatureC()).isEqualTo(-273.15);
        assertThat(maxValues.diskTemperatureC()).isEqualTo(1000.0);
    }

    @Test
    @DisplayName("ToString should contain all field values")
    void toStringShouldContainAllFields() {
        MetricsSnapshot snapshot = new MetricsSnapshot(
                TEST_TIMESTAMP, 45.5, 60.0, 1000L, 500L, 1024L * 1024, 512L * 1024, 65.0
        );

        String str = snapshot.toString();
        assertThat(str)
                .contains("MetricsSnapshot")
                .contains("timestamp=" + TEST_TIMESTAMP)
                .contains("cpuUsagePercent=")
                .contains("ramUsagePercent=")
                .contains("diskReads=1000")
                .contains("diskWrites=500")
                .contains("diskTemperatureC=65.0");
    }

    @Test
    @DisplayName("ToString should handle null temperature")
    void toStringShouldHandleNullTemperature() {
        MetricsSnapshot snapshot = new MetricsSnapshot(
                TEST_TIMESTAMP, 45.5, 60.0, 1000L, 500L, 1024L * 1024, 512L * 1024, null
        );

        String str = snapshot.toString();
        assertThat(str).contains("diskTemperatureC=null");
    }

    @Test
    @DisplayName("All accessors should return correct values")
    void allAccessorsShouldWork() {
        Instant customTimestamp = Instant.parse("2024-06-01T12:00:00Z");
        MetricsSnapshot snapshot = new MetricsSnapshot(
                customTimestamp, 75.5, 80.0, 1000L, 500L, 1024L * 1024, 512L * 1024, 55.5
        );

        assertThat(snapshot.timestamp()).isEqualTo(customTimestamp);
        assertThat(snapshot.cpuUsagePercent()).isEqualTo(75.5);
        assertThat(snapshot.ramUsagePercent()).isEqualTo(80.0);
        assertThat(snapshot.diskReads()).isEqualTo(1000L);
        assertThat(snapshot.diskTemperatureC()).isEqualTo(55.5);
    }

    @Test
    @DisplayName("Should represent idle system state")
    void shouldRepresentIdleState() {
        MetricsSnapshot idle = new MetricsSnapshot(
                Instant.now(), 2.0, 30.0, 100L, 50L, 1024L * 100, 512L * 100, 35.0
        );

        assertThat(idle.cpuUsagePercent()).isLessThan(5.0);
        assertThat(idle.diskTemperatureC()).isLessThan(40.0);
    }

    @Test
    @DisplayName("Should represent high load system state")
    void shouldRepresentHighLoadState() {
        MetricsSnapshot highLoad = new MetricsSnapshot(
                Instant.now(), 95.0, 85.0, 5000L, 2500L, 1024L * 1024 * 100, 512L * 1024 * 100, 75.0
        );

        assertThat(highLoad.cpuUsagePercent()).isGreaterThan(90.0);
        assertThat(highLoad.ramUsagePercent()).isGreaterThan(80.0);
    }

    @Test
    @DisplayName("Should handle temperature variations")
    void shouldHandleTemperatureVariations() {
        MetricsSnapshot cold = new MetricsSnapshot(Instant.now(), 50.0, 60.0, 1000L, 500L, 1024L * 1024, 512L * 1024, 10.0);
        MetricsSnapshot normal = new MetricsSnapshot(Instant.now(), 50.0, 60.0, 1000L, 500L, 1024L * 1024, 512L * 1024, 50.0);
        MetricsSnapshot hot = new MetricsSnapshot(Instant.now(), 50.0, 60.0, 1000L, 500L, 1024L * 1024, 512L * 1024, 90.0);

        assertThat(cold.diskTemperatureC()).isEqualTo(10.0);
        assertThat(normal.diskTemperatureC()).isEqualTo(50.0);
        assertThat(hot.diskTemperatureC()).isEqualTo(90.0);
    }
}
