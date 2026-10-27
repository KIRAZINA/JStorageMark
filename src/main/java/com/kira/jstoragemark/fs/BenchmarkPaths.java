package com.kira.jstoragemark.fs;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filesystem safety and utility helpers for dedicated test directory
 * management, free-space checks, and deterministic file naming.
 *
 * <p>Session isolation: every session owns a child directory named
 * {@code session-<sessionId>} below the configured base directory. All benchmark payloads are
 * written there, and cleanup removes that tree only. Files elsewhere below the base directory are
 * never touched, even if their names contain the session id or start with {@code run-}.
 *
 * <p>Reports remain in the base directory so they survive payload cleanup and stay easy to find.
 */
public final class BenchmarkPaths implements IBenchmarkPaths {

    private static final Logger logger = LoggerFactory.getLogger(BenchmarkPaths.class);

    private static final String SESSION_DIR_PREFIX = "session-";
    private static final int MAX_SESSION_ID_LENGTH = 64;

    private final Path baseDir;
    private final String sessionId;
    private final String safeSessionId;
    private final Path sessionDir;

    public BenchmarkPaths(Path baseDir, String sessionId) {
        this.baseDir = Objects.requireNonNull(baseDir, "baseDir");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.safeSessionId = sanitizeSessionId(sessionId);
        this.sessionDir = resolveSessionDir(baseDir, safeSessionId);
    }

    /**
     * Sanitizes a session id so it can be used as one path segment.
     *
     * <p>Path separators, traversal markers, whitespace, and shell metacharacters are replaced
     * rather than echoed into the filesystem. Normal generated ids ({@code jsm-<hex>}) are
     * unchanged.
     *
     * @param rawSessionId configured session id
     * @return a single, safe path segment
     */
    private static String sanitizeSessionId(String rawSessionId) {
        String safe = rawSessionId.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        safe = safe.replaceAll("\\.+$", "_");
        if (safe.isEmpty() || safe.equals(".") || safe.equals("..")) {
            safe = "session";
        }
        return safe.length() > MAX_SESSION_ID_LENGTH
                ? safe.substring(0, MAX_SESSION_ID_LENGTH)
                : safe;
    }

    /**
     * Resolves the session directory and proves it cannot escape the base directory.
     *
     * @param baseDir configured base directory
     * @param safeSessionId already sanitized session id
     * @return normalized child directory
     */
    private static Path resolveSessionDir(Path baseDir, String safeSessionId) {
        Path session = baseDir.resolve(SESSION_DIR_PREFIX + safeSessionId).normalize();
        Path baseAbsolute = baseDir.toAbsolutePath().normalize();
        Path sessionAbsolute = session.toAbsolutePath().normalize();
        if (!sessionAbsolute.startsWith(baseAbsolute) || sessionAbsolute.equals(baseAbsolute)) {
            throw new IllegalArgumentException("sessionId must not escape the test directory");
        }
        return session;
    }

    /** Ensures the base directory and the isolated session directory exist and are writable. */
    @Override
    public void ensureTestDirectory() throws IOException {
        if (Files.notExists(baseDir)) {
            Files.createDirectories(baseDir);
        }
        if (!Files.isDirectory(baseDir)) {
            throw new IOException("Test path exists but is not a directory: " + baseDir);
        }

        if (Files.isSymbolicLink(sessionDir)) {
            throw new IOException("Session directory must not be a symbolic link: " + sessionDir);
        }
        Files.createDirectories(sessionDir);
        if (!Files.isDirectory(sessionDir)) {
            throw new IOException("Session path exists but is not a directory: " + sessionDir);
        }

        // Basic write probe: create/delete a tiny marker file inside the session directory.
        Path probe = sessionDir.resolve(".write_probe_" + safeSessionId);
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
    @Override
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
     *  - jstoragemark-tests/session-jsm-abc123/run-001.seq.write.jsm-abc123.bin
     *  - jstoragemark-tests/session-jsm-abc123/run-002.rand.read.jsm-abc123.bin
     */
    @Override
    public Path testFilePath(int runId, String descriptor) {
        String safeDesc = descriptor == null ? "data" : descriptor.replaceAll("\\s+", ".");
        String fname = String.format("run-%03d.%s.%s.bin", runId, safeDesc, safeSessionId);
        return sessionDir.resolve(fname);
    }

    /**
     * Returns a thread-specific test file path for multithreaded benchmarks.
     * Each thread gets its own file to avoid position contention.
     * Examples:
     *  - jstoragemark-tests/session-jsm-abc123/run-001.seq_write-t0.jsm-abc123.bin
     *  - jstoragemark-tests/session-jsm-abc123/run-001.seq_write-t1.jsm-abc123.bin
     */
    @Override
    public Path testFilePath(int runId, String descriptor, int threadId) {
        String safeDesc = descriptor == null ? "data" : descriptor.replaceAll("\\s+", ".");
        String fname = String.format("run-%03d.%s-t%d.%s.bin", runId, safeDesc, threadId, safeSessionId);
        return sessionDir.resolve(fname);
    }

    /**
     * Returns a per-run temp file path (e.g., for async/NIO staging).
     */
    @Override
    public Path tempFilePath(int runId, String descriptor) {
        String safeDesc = descriptor == null ? "temp" : descriptor.replaceAll("\\s+", ".");
        String fname = String.format("run-%03d.%s.%s.tmp", runId, safeDesc, safeSessionId);
        return sessionDir.resolve(fname);
    }

    /**
     * Returns a report file path with the given extension.
     *
     * <p>Reports live in the base directory, outside the disposable session payload, so they are
     * never removed by session cleanup or by the shutdown hook.
     *
     * <p>Example: jstoragemark-tests/report.jsm-abc123.json
     */
    @Override
    public Path reportFilePath(String extension) {
        String ext = extension.startsWith(".") ? extension.substring(1) : extension;
        return baseDir.resolve("report." + safeSessionId + "." + ext);
    }

    /** Creates a subdirectory for charts if needed (HTML reports). */
    @Override
    public Path ensureChartsDir() throws IOException {
        Path charts = sessionDir.resolve("charts-" + safeSessionId);
        Files.createDirectories(charts);
        return charts;
    }

    /**
     * Deletes the isolated session directory tree (best-effort).
     *
     * <p>If {@code retain} is true this method does nothing. Otherwise it removes only
     * {@code <base>/session-<id>} and its contents; the base directory, sibling sessions, user
     * files, and reports are left untouched. The safety check is structural rather than
     * name-based, so a file called {@code run-001.bin} outside the session directory survives.
     *
     * @param retain when true, keep all files for inspection
     */
    @Override
    public void cleanupSessionFiles(boolean retain) {
        if (retain) {
            logger.debug("Retaining session directory {}", sessionDir);
            return;
        }
        if (!isSafeSessionDirectory()) {
            logger.warn("Refusing to clean up unsafe session directory: {}", sessionDir);
            return;
        }
        if (Files.notExists(sessionDir, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }

        try {
            Files.walkFileTree(sessionDir, new SessionTreeDeleter());
            logger.info("Cleaned up benchmark session directory {}", sessionDir);
        } catch (IOException e) {
            logger.warn("Could not fully clean up session directory {}", sessionDir, e);
        }
    }

    /**
     * Deletes every entry below the session directory and then the directory itself.
     *
     * <p>Symbolic links are not followed: a link is deleted as a file, so cleanup can never
     * traverse into a target outside the session tree.
     */
    private static final class SessionTreeDeleter extends SimpleFileVisitor<Path> {

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.deleteIfExists(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            logger.debug("Skipping unreadable path during cleanup: {}", file, exc);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            if (exc != null) {
                logger.debug("Directory traversal issue during cleanup: {}", dir, exc);
            }
            Files.deleteIfExists(dir);
            return FileVisitResult.CONTINUE;
        }
    }

    /**
     * Verifies that cleanup can only ever target the dedicated session child directory.
     *
     * @return true when the configured session directory is a safe child of the base directory
     */
    private boolean isSafeSessionDirectory() {
        Path baseAbsolute = baseDir.toAbsolutePath().normalize();
        Path sessionAbsolute = sessionDir.toAbsolutePath().normalize();
        Path name = sessionDir.getFileName();
        return name != null
                && name.toString().startsWith(SESSION_DIR_PREFIX)
                && !sessionAbsolute.equals(baseAbsolute)
                && sessionAbsolute.startsWith(baseAbsolute);
    }

    /** Returns a human-friendly header string for logs. */
    @Override
    public String header() {
        return "[JStorageMark] session=" + sessionId
                + " dir=" + baseDir
                + " sessionDir=" + sessionDir
                + " ts=" + Instant.now();
    }

    @Override
    public Path getBaseDir() { return baseDir; }

    @Override
    public Path getSessionDir() { return sessionDir; }

    @Override
    public String getSessionId() { return sessionId; }
}
