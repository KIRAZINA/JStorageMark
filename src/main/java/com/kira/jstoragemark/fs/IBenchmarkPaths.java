package com.kira.jstoragemark.fs;

import java.io.IOException;
import java.nio.file.Path;

public interface IBenchmarkPaths {
    void ensureTestDirectory() throws IOException;
    void validateFreeSpace(long requiredBytes) throws IOException;
    Path testFilePath(int runId, String descriptor);
    Path testFilePath(int runId, String descriptor, int threadId);
    Path tempFilePath(int runId, String descriptor);
    Path reportFilePath(String extension);
    Path ensureChartsDir() throws IOException;
    void cleanupSessionFiles(boolean retain);
    String header();
    Path getBaseDir();
    String getSessionId();
}
