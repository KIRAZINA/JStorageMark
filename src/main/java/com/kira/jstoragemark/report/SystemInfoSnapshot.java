package com.kira.jstoragemark.report;

public record SystemInfoSnapshot(
    String osName,
    String javaVersion,
    String cpuModel,
    long totalRamBytes
) {}
