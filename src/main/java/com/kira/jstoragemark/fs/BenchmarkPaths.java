package com.kira.jstoragemark.fs;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Objects;

/**
 * Filesystem safety and utility helpers for dedicated test directory
 * management, free-space checks, and deterministic file naming.
 *
 * Notes:
 * - All test operations must occur under a single directory (TS safety).
 * - Provides a simple free-space validation using FileStore.
 * - Naming scheme uses sessionId + runId to avoid collisions and aids cleanup.
 */
public final class BenchmarkPaths {

    private final Path baseDir;
    private final String sessionId;

    public BenchmarkPaths(Path baseDir, String sessionId) {
        this.baseDir = Objects.requireNonNull(baseDir, "baseDir");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
    }

    /** Ensures the base directory exists and is writable. */
    public void ensureTestDirectory() throws IOException {
        if (Files.notExists(baseDir)) {
            Files.createDirectories(baseDir);
        }
        if (!Files.isDirectory(baseDir)) {
            throw new IOException("Test path exists but is not a directory: " + baseDir);
        }
        // Basic write probe: create/delete a tiny marker file
        Path probe = baseDir.resolve(".write_probe_" + sessionId);
        try {
            Files.write(probe, new byte[]{0x0}, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        } finally {
            try {
                Files.deleteIfExists(probe);
            } catch (IOException ignored) {
                // Non-fatal; directory is still usable.
            }
        }
    }

    /**
     * Validates free space for the given required bytes.
     * Adds a conservative overhead buffer (5%) to account for metadata and file system fragmentation.
     */
    public void validateFreeSpace(long requiredBytes) throws IOException {
        FileStore store = Files.getFileStore(baseDir);
        long usable = store.getUsableSpace();
        long requiredWithBuffer = (long) (requiredBytes * 1.05); // +5% overhead
        if (usable < requiredWithBuffer) {
            throw new IOException("Insufficient free space. Required ~" + requiredWithBuffer +
                    " bytes, available " + usable + " bytes on " + store.name());
        }
    }

    /**
     * Returns a deterministic test file path for a given run id and optional suffix.
     * Examples:
     *  - jsm-abc123/run-001.seq.write.bin
     *  - jsm-abc123/run-002.rand.read.bin
     */
    public Path testFilePath(int runId, String descriptor) {
        String safeDesc = descriptor == null ? "data" : descriptor.replaceAll("\\s+", ".");
        String fname = String.format("run-%03d.%s.%s.bin", runId, safeDesc, sessionId);
        return baseDir.resolve(fname);
    }

    /**
     * Returns a per-run temp file path (e.g., for async/NIO staging).
     */
    public Path tempFilePath(int runId, String descriptor) {
        String safeDesc = descriptor == null ? "temp" : descriptor.replaceAll("\\s+", ".");
        String fname = String.format("run-%03d.%s.%s.tmp", runId, safeDesc, sessionId);
        return baseDir.resolve(fname);
    }

    /**
     * Returns a report file path with the given extension.
     * Example: jsm-abc123/report.session.json
     */
    public Path reportFilePath(String extension) {
        String ext = extension.startsWith(".") ? extension.substring(1) : extension;
        return baseDir.resolve("report." + sessionId + "." + ext);
    }

    /** Creates a subdirectory for charts if needed (HTML reports). */
    public Path ensureChartsDir() throws IOException {
        Path charts = baseDir.resolve("charts-" + sessionId);
        if (Files.notExists(charts)) {
            Files.createDirectories(charts);
        }
        return charts;
    }

    /**
     * Cleanup helper: deletes files created in this session (best-effort).
     * If retain is true, it skips deletion.
     */
    public void cleanupSessionFiles(boolean retain) {
        if (retain) return;
        try {
            if (Files.exists(baseDir)) {
                Files.walkFileTree(baseDir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String name = file.getFileName().toString();
                        if (name.contains(sessionId) || name.startsWith("run-")) {
                            Files.deleteIfExists(file);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (IOException ignored) {
            // Log at debug level in actual app; non-fatal cleanup.
        }
    }

    /** Returns a human-friendly header string for logs. */
    public String header() {
        return "[JStorageMark] session=" + sessionId + " dir=" + baseDir + " ts=" + Instant.now();
    }

    public Path getBaseDir() { return baseDir; }
    public String getSessionId() { return sessionId; }
}
