package com.kira.jstoragemark.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/**
 * Immutable configuration for a single benchmark session.
 * Covers workload customization, safety, logging verbosity, and reporting toggles.
 *
 * Design notes:
 * - Builder enforces required fields and sensible defaults.
 * - Validation ensures safety: bounds on sizes/threads/iterations, directory presence,
 *   and queue depth semantics aligned with thread pool usage.
 * - Supports multiple test types in one session to streamline reporting.
 */
public final class BenchmarkConfig {

    // ---- Enumerations -------------------------------------------------------

    /** I/O mode: synchronous (classic stream/RandomAccessFile) vs. asynchronous (NIO) */
    public enum IoMode {
        SYNC,
        ASYNC
    }

    /** Benchmark test types as per TS */
    public enum TestType {
        SEQ_READ,
        SEQ_WRITE,
        RAND_READ,
        RAND_WRITE
    }

    /** Reporting formats */
    public enum ReportFormat {
        CSV,
        JSON,
        HTML
    }

    // ---- Required core fields ----------------------------------------------

    private final Path testDirectory;              // Dedicated directory for safe testing
    private final Set<TestType> testTypes;         // Selected workloads to run

    // ---- Workload customization --------------------------------------------

    private final long fileSizeBytes;              // Default 1–10 GB (configurable)
    private final int blockSizeBytes;              // 4 KB–1 MB
    private final int threads;                     // 1–32
    private final int iterations;                  // 3–10 (statistical averaging)
    private final int warmupIterations;            // Optional warmup to stabilize results
    private final IoMode ioMode;                   // SYNC/ASYNC
    private final int queueDepth;                  // Simulated concurrency via thread pool/semaphore
    private final Long randomSeed;                 // Optional fixed seed for reproducibility

    // ---- Safety / device handling ------------------------------------------

    private final boolean allowRawDeviceAccess;    // If true, caller accepts permission risks
    private final boolean retainTestFiles;         // If true, keep generated files for inspection

    // ---- Monitoring / logging ----------------------------------------------

    private final int verbosity;                   // 0=min, 1=info, 2=debug (mapped by CLI)
    private final boolean collectSystemMetrics;    // CPU/RAM/utilization via OSHI (if available)
    private final Duration metricsPollInterval;    // Sampling cadence during runs

    // ---- Reporting ----------------------------------------------------------

    private final Set<ReportFormat> reportFormats; // CSV/JSON mandatory; HTML optional
    private final boolean embedCharts;             // If HTML selected, embed PNG/SVG charts

    // ---- Non-functional guides ---------------------------------------------

    private final Duration maxPerTestTarget;       // Soft guideline to keep test under 5–10 minutes

    // ---- Derived / runtime aids --------------------------------------------

    private final String sessionId;                // For file naming & report grouping

    private BenchmarkConfig(Builder b) {
        this.testDirectory = Objects.requireNonNull(b.testDirectory, "testDirectory");
        this.testTypes = Collections.unmodifiableSet(new LinkedHashSet<>(b.testTypes));

        this.fileSizeBytes = b.fileSizeBytes;
        this.blockSizeBytes = b.blockSizeBytes;
        this.threads = b.threads;
        this.iterations = b.iterations;
        this.warmupIterations = b.warmupIterations;
        this.ioMode = Objects.requireNonNull(b.ioMode, "ioMode");
        this.queueDepth = b.queueDepth;
        this.randomSeed = b.randomSeed;

        this.allowRawDeviceAccess = b.allowRawDeviceAccess;
        this.retainTestFiles = b.retainTestFiles;

        this.verbosity = b.verbosity;
        this.collectSystemMetrics = b.collectSystemMetrics;
        this.metricsPollInterval = b.metricsPollInterval;

        this.reportFormats = Collections.unmodifiableSet(new LinkedHashSet<>(b.reportFormats));
        this.embedCharts = b.embedCharts;

        this.maxPerTestTarget = b.maxPerTestTarget;

        this.sessionId = b.sessionId != null ? b.sessionId : defaultSessionId();
        validate();
    }

    // ---- Validation aligned to TS ------------------------------------------

    private void validate() {
        if (testTypes.isEmpty()) {
            throw new IllegalArgumentException("At least one TestType must be selected.");
        }

        // File size: 1–10 GB recommended; allow broader but warn in docs.
        long minBytes = 1L * 1024 * 1024 * 1024;           // 1 GB
        long maxBytes = 10L * 1024 * 1024 * 1024;          // 10 GB
        if (fileSizeBytes < minBytes || fileSizeBytes > maxBytes) {
            throw new IllegalArgumentException("fileSizeBytes must be between 1 GB and 10 GB for typical workloads.");
        }

        // Block size: 4 KB–1 MB
        int minBlock = 4 * 1024;
        int maxBlock = 1 * 1024 * 1024;
        if (blockSizeBytes < minBlock || blockSizeBytes > maxBlock) {
            throw new IllegalArgumentException("blockSizeBytes must be between 4 KB and 1 MB.");
        }

        // Threads: 1–32 (cap to keep JVM overhead reasonable)
        if (threads < 1 || threads > 32) {
            throw new IllegalArgumentException("threads must be between 1 and 32.");
        }

        // Iterations: 3–10
        if (iterations < 1) {
            throw new IllegalArgumentException("iterations must be >= 1.");
        }
        if (iterations < 3 || iterations > 10) {
            // Allow but warn—keep strict by default
            throw new IllegalArgumentException("iterations should be between 3 and 10 for stable averaging.");
        }

        // Warmup: non-negative and reasonably small
        if (warmupIterations < 0 || warmupIterations > 5) {
            throw new IllegalArgumentException("warmupIterations must be between 0 and 5.");
        }

        // Queue depth: >= 1; typical upper cap ~threads * 2
        if (queueDepth < 1 || queueDepth > threads * 2) {
            throw new IllegalArgumentException("queueDepth must be >= 1 and <= threads * 2.");
        }

        // Verbosity: 0–2
        if (verbosity < 0 || verbosity > 2) {
            throw new IllegalArgumentException("verbosity must be 0 (quiet), 1 (info), or 2 (debug).");
        }

        // Metrics poll interval: reasonable range 100ms–5s to avoid overhead
        long pollMillis = metricsPollInterval.toMillis();
        if (pollMillis < 100 || pollMillis > 5000) {
            throw new IllegalArgumentException("metricsPollInterval must be between 100 ms and 5 s.");
        }

        // Report formats: at least CSV and JSON by TS requirement
        if (!reportFormats.contains(ReportFormat.CSV) || !reportFormats.contains(ReportFormat.JSON)) {
            throw new IllegalArgumentException("Report formats must include both CSV and JSON. HTML is optional.");
        }

        // HTML charts only meaningful if HTML selected
        if (embedCharts && !reportFormats.contains(ReportFormat.HTML)) {
            throw new IllegalArgumentException("embedCharts=true requires HTML report format.");
        }
    }

    private String defaultSessionId() {
        return "jsm-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    // ---- Accessors ----------------------------------------------------------

    public Path getTestDirectory() { return testDirectory; }
    public Set<TestType> getTestTypes() { return testTypes; }
    public long getFileSizeBytes() { return fileSizeBytes; }
    public int getBlockSizeBytes() { return blockSizeBytes; }
    public int getThreads() { return threads; }
    public int getIterations() { return iterations; }
    public int getWarmupIterations() { return warmupIterations; }
    public IoMode getIoMode() { return ioMode; }
    public int getQueueDepth() { return queueDepth; }
    public Optional<Long> getRandomSeed() { return Optional.ofNullable(randomSeed); }
    public boolean isAllowRawDeviceAccess() { return allowRawDeviceAccess; }
    public boolean isRetainTestFiles() { return retainTestFiles; }
    public int getVerbosity() { return verbosity; }
    public boolean isCollectSystemMetrics() { return collectSystemMetrics; }
    public Duration getMetricsPollInterval() { return metricsPollInterval; }
    public Set<ReportFormat> getReportFormats() { return reportFormats; }
    public boolean isEmbedCharts() { return embedCharts; }
    public Duration getMaxPerTestTarget() { return maxPerTestTarget; }
    public String getSessionId() { return sessionId; }

    // ---- Derived helpers ----------------------------------------------------

    /** Convenience: true if random workloads selected. */
    public boolean hasRandomWorkloads() {
        return testTypes.contains(TestType.RAND_READ) || testTypes.contains(TestType.RAND_WRITE);
    }

    /** Convenience: true if sequential workloads selected. */
    public boolean hasSequentialWorkloads() {
        return testTypes.contains(TestType.SEQ_READ) || testTypes.contains(TestType.SEQ_WRITE);
    }

    /** Total blocks per file (rounded up) */
    public long blocksPerFile() {
        long blocks = fileSizeBytes / blockSizeBytes;
        return (fileSizeBytes % blockSizeBytes == 0) ? blocks : (blocks + 1);
    }

    // ---- Builder ------------------------------------------------------------

    public static final class Builder {
        private Path testDirectory;
        private final Set<TestType> testTypes = new LinkedHashSet<>();

        private long fileSizeBytes = 5L * 1024 * 1024 * 1024; // 5 GB default
        private int blockSizeBytes = 128 * 1024;              // 128 KB default
        private int threads = 4;                              // 4 default
        private int iterations = 5;                           // 5 default
        private int warmupIterations = 1;                     // 1 warmup

        private IoMode ioMode = IoMode.SYNC;
        private int queueDepth = 8;
        private Long randomSeed = null;

        private boolean allowRawDeviceAccess = false;
        private boolean retainTestFiles = false;

        private int verbosity = 1;
        private boolean collectSystemMetrics = true;
        private Duration metricsPollInterval = Duration.ofMillis(500);

        private final Set<ReportFormat> reportFormats =
                new LinkedHashSet<>(Arrays.asList(ReportFormat.CSV, ReportFormat.JSON));
        private boolean embedCharts = false;

        private Duration maxPerTestTarget = Duration.ofMinutes(10);
        private String sessionId = null;

        public Builder testDirectory(Path dir) {
            this.testDirectory = dir;
            return this;
        }

        public Builder addTestType(TestType type) {
            this.testTypes.add(Objects.requireNonNull(type));
            return this;
        }

        public Builder testTypes(Collection<TestType> types) {
            this.testTypes.clear();
            this.testTypes.addAll(Objects.requireNonNull(types));
            return this;
        }

        public Builder fileSizeBytes(long bytes) { this.fileSizeBytes = bytes; return this; }
        public Builder blockSizeBytes(int bytes) { this.blockSizeBytes = bytes; return this; }
        public Builder threads(int threads) { this.threads = threads; return this; }
        public Builder iterations(int iterations) { this.iterations = iterations; return this; }
        public Builder warmupIterations(int warmup) { this.warmupIterations = warmup; return this; }

        public Builder ioMode(IoMode mode) { this.ioMode = mode; return this; }
        public Builder queueDepth(int qd) { this.queueDepth = qd; return this; }
        public Builder randomSeed(Long seed) { this.randomSeed = seed; return this; }

        public Builder allowRawDeviceAccess(boolean allow) { this.allowRawDeviceAccess = allow; return this; }
        public Builder retainTestFiles(boolean retain) { this.retainTestFiles = retain; return this; }

        public Builder verbosity(int level) { this.verbosity = level; return this; }
        public Builder collectSystemMetrics(boolean collect) { this.collectSystemMetrics = collect; return this; }
        public Builder metricsPollInterval(Duration interval) { this.metricsPollInterval = interval; return this; }

        public Builder reportFormats(Collection<ReportFormat> formats) {
            this.reportFormats.clear();
            this.reportFormats.addAll(Objects.requireNonNull(formats));
            return this;
        }

        public Builder addReportFormat(ReportFormat format) {
            this.reportFormats.add(Objects.requireNonNull(format));
            return this;
        }

        public Builder embedCharts(boolean embed) { this.embedCharts = embed; return this; }
        public Builder maxPerTestTarget(Duration d) { this.maxPerTestTarget = d; return this; }
        public Builder sessionId(String id) { this.sessionId = id; return this; }

        public BenchmarkConfig build() {
            // Ensure at least one test type chosen by default if user forgot.
            if (this.testTypes.isEmpty()) {
                this.testTypes.add(TestType.SEQ_READ);
                this.testTypes.add(TestType.SEQ_WRITE);
            }
            return new BenchmarkConfig(this);
        }
    }

    @Override
    public String toString() {
        return "BenchmarkConfig{" +
                "testDirectory=" + testDirectory +
                ", testTypes=" + testTypes +
                ", fileSizeBytes=" + fileSizeBytes +
                ", blockSizeBytes=" + blockSizeBytes +
                ", threads=" + threads +
                ", iterations=" + iterations +
                ", warmupIterations=" + warmupIterations +
                ", ioMode=" + ioMode +
                ", queueDepth=" + queueDepth +
                ", randomSeed=" + randomSeed +
                ", allowRawDeviceAccess=" + allowRawDeviceAccess +
                ", retainTestFiles=" + retainTestFiles +
                ", verbosity=" + verbosity +
                ", collectSystemMetrics=" + collectSystemMetrics +
                ", metricsPollInterval=" + metricsPollInterval +
                ", reportFormats=" + reportFormats +
                ", embedCharts=" + embedCharts +
                ", maxPerTestTarget=" + maxPerTestTarget +
                ", sessionId='" + sessionId + '\'' +
                '}';
    }
}
