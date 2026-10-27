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

    /**
     * Returns the isolated directory that holds this session's test payload.
     *
     * <p>Defaults to the base directory for backward compatibility with existing implementations.
     * The standard {@link BenchmarkPaths} implementation always returns a dedicated child
     * directory, so cleanup can never touch unrelated files in the base directory.
     *
     * @return directory containing only files created by this session
     */
    default Path getSessionDir() {
        return getBaseDir();
    }

    String getSessionId();
}
