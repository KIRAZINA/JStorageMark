package com.kira.jstoragemark.report;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

class SystemInfoSnapshotTest {

    @Test
    @DisplayName("Record should store all fields")
    void recordShouldStoreAllFields() {
        var snapshot = new SystemInfoSnapshot(
                "Windows 11", "17.0.9", "Intel Core i7", 17179869184L
        );

        assertThat(snapshot.osName()).isEqualTo("Windows 11");
        assertThat(snapshot.javaVersion()).isEqualTo("17.0.9");
        assertThat(snapshot.cpuModel()).isEqualTo("Intel Core i7");
        assertThat(snapshot.totalRamBytes()).isEqualTo(17179869184L);
    }

    @Test
    @DisplayName("Record should accept null fields")
    void recordShouldAcceptNullFields() {
        var snapshot = new SystemInfoSnapshot(null, null, null, 0L);

        assertThat(snapshot.osName()).isNull();
        assertThat(snapshot.javaVersion()).isNull();
        assertThat(snapshot.cpuModel()).isNull();
        assertThat(snapshot.totalRamBytes()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "Linux, 17, ARM, 8589934592",
            "macOS, 21, Apple M3, 0",
            "'Windows 10', '1.8', 'unknown', 1"
    })
    @DisplayName("Record should handle various system configurations")
    void recordShouldHandleVariousConfigs(String os, String java, String cpu, long ram) {
        var snapshot = new SystemInfoSnapshot(os, java, cpu, ram);

        assertThat(snapshot.osName()).isEqualTo(os);
        assertThat(snapshot.javaVersion()).isEqualTo(java);
        assertThat(snapshot.cpuModel()).isEqualTo(cpu);
        assertThat(snapshot.totalRamBytes()).isEqualTo(ram);
    }

    @Test
    @DisplayName("toString should include all fields")
    void toStringShouldIncludeAllFields() {
        var snapshot = new SystemInfoSnapshot(
                "Windows 11", "17.0.9", "Intel Core i7", 17179869184L
        );

        String str = snapshot.toString();
        assertThat(str).contains("Windows 11");
        assertThat(str).contains("17.0.9");
        assertThat(str).contains("Intel Core i7");
        assertThat(str).contains("17179869184");
    }
}
