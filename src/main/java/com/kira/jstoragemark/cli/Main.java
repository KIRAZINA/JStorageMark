package com.kira.jstoragemark.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kira.jstoragemark.config.AppConstants;
import com.kira.jstoragemark.config.BenchmarkConfig;
import com.kira.jstoragemark.core.BenchmarkRunner;
import com.kira.jstoragemark.fs.BenchmarkPaths;
import com.kira.jstoragemark.metrics.MetricsSnapshot;
import com.kira.jstoragemark.report.ReportGenerator;
import com.kira.jstoragemark.result.BenchmarkResult;

public final class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        System.exit(run(args));
    }

    public static int run(String[] args) {
        Options options = new Options();

        options.addOption("d", "directory", true, "Test directory path");
        options.addOption("t", "test", true, "Test type(s): SEQ_READ, SEQ_WRITE, RAND_READ, RAND_WRITE, MIXED_RW (comma-separated)");
        options.addOption("s", "size", true, "File size (e.g. 5G, 500M, 1073741824; range 1GB-10GB)");
        options.addOption("b", "block", true, "Block size in bytes (default 128KB)");
        options.addOption("n", "threads", true, "Number of threads (default 4)");
        options.addOption("i", "iterations", true, "Number of iterations (default 5)");
        options.addOption("q", "queue", true, "Queue depth (default 8)");
        options.addOption("v", "verbosity", true, "Verbosity level: 0,1,2");
        options.addOption("r", "retain", false, "Retain test files after run");
        options.addOption("html", "htmlReport", false, "Generate HTML report");
        options.addOption("fs", "force-sync", false, "Force fsync after writes (default: true)");
        options.addOption("nfs", "no-force-sync", false, "Disable fsync after writes");
        options.addOption("se", "sync-every", true, "Sync every N blocks (0 = only at end)");
        options.addOption("np", "no-preallocate", false, "Disable file preallocation");
        options.addOption("hb", "heap-buffer", false, "Use heap buffer instead of direct buffer");
        options.addOption("mr", "mixed-ratio", true, "Read percentage for MIXED_RW workload (0-100, default 70)");
        options.addOption(null, "block-sweep", true, "Comma-separated block sizes to sweep (e.g. 4K,8K,16K,32K,64K,128K,256K,512K,1M)");
        options.addOption(null, "compare", true, "Compare current run with baseline JSON report file");
        options.addOption(null, "version", false, "Print version and exit");

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();

        try {
            CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption("version")) {
                System.out.println("JStorageMark v" + AppConstants.VERSION);
                return 0;
            }

            String dirPath = cmd.getOptionValue("d", AppConstants.DEFAULT_TEST_DIR);
            Path dir = Path.of(dirPath);
            if (!Files.exists(dir)) {
                System.err.println("Error: Test directory does not exist: " + dirPath);
                return 1;
            }
            if (!Files.isDirectory(dir)) {
                System.err.println("Error: Path is not a directory: " + dirPath);
                return 1;
            }

            long fileSize = parseFileSize(cmd.getOptionValue("s", String.valueOf(5L * 1024 * 1024 * 1024)));
            int blockSize = parseInt("block size", cmd.getOptionValue("b", String.valueOf(128 * 1024)),
                    4 * 1024, 1 * 1024 * 1024);
            int threads = parseInt("threads", cmd.getOptionValue("n", "4"), 1, 32);
            int iterations = parseInt("iterations", cmd.getOptionValue("i", "5"), 1, 100);
            int queueDepth = parseInt("queue depth", cmd.getOptionValue("q", "8"), 1, 1024);

            if (queueDepth > threads * 2) {
                System.err.println("Warning: queue depth exceeds threads*2, capping at " + (threads * 2));
                queueDepth = threads * 2;
            }

            BenchmarkConfig.Builder builder = new BenchmarkConfig.Builder()
                    .testDirectory(dir)
                    .fileSizeBytes(fileSize)
                    .blockSizeBytes(blockSize)
                    .threads(threads)
                    .iterations(iterations)
                    .queueDepth(queueDepth)
                    .verbosity(parseInt("verbosity", cmd.getOptionValue("v", "1"), 0, 2))
                    .retainTestFiles(cmd.hasOption("r"))
                    .forceSync(!cmd.hasOption("nfs"))
                    .syncEveryNBlocks(parseInt("sync every", cmd.getOptionValue("se", "0"), 0, 10000))
                    .preallocateFiles(!cmd.hasOption("np"))
                    .useDirectBuffer(!cmd.hasOption("hb"));

            if (cmd.hasOption("mr")) {
                builder.mixedReadPercent(parseInt("mixed ratio", cmd.getOptionValue("mr"), 0, 100));
            }

            String[] testTypes = cmd.getOptionValue("t", "SEQ_READ,SEQ_WRITE").split(",");
            for (String tt : testTypes) {
                tt = tt.trim().toUpperCase(Locale.ROOT);
                try {
                    builder.addTestType(BenchmarkConfig.TestType.valueOf(tt));
                } catch (IllegalArgumentException e) {
                    System.err.println("Error: Unknown test type: " + tt);
                    return 1;
                }
            }

            builder.addReportFormat(BenchmarkConfig.ReportFormat.CSV);
            builder.addReportFormat(BenchmarkConfig.ReportFormat.JSON);
            if (cmd.hasOption("html")) {
                builder.addReportFormat(BenchmarkConfig.ReportFormat.HTML);
                builder.embedCharts(true);
            }

            // Handle --compare (load baseline before running to fail fast)
            List<BenchmarkResult> baselineResults = null;
            if (cmd.hasOption("compare")) {
                baselineResults = loadBaseline(cmd.getOptionValue("compare"));
            }

            // Handle --block-sweep
            boolean isSweep = cmd.hasOption("block-sweep");
            if (isSweep) {
                BenchmarkConfig config = builder.build();
                List<Integer> blockSizes = parseBlockSweep(cmd.getOptionValue("block-sweep"));
                return runBlockSweep(dir, config, blockSizes, baselineResults);
            }

            BenchmarkConfig config = builder.build();
            BenchmarkPaths paths = new BenchmarkPaths(config.getTestDirectory(), config.getSessionId());

            logger.info("Starting benchmark with configuration: {}", config);
            System.out.println("Starting JStorageMark benchmark...");
            System.out.println("Test directory: " + config.getTestDirectory());
            System.out.println("Test types: " + config.getTestTypes());
            System.out.println("File size: " + formatBytes(config.getFileSizeBytes()));
            System.out.println("Threads: " + config.getThreads());
            System.out.println("Iterations: " + config.getIterations());
            System.out.println();

            BenchmarkRunner runner = new BenchmarkRunner(config, paths);
            runner.startMetricsPolling();

            long startTime = System.currentTimeMillis();
            List<BenchmarkResult> results = runner.runAll();
            long duration = System.currentTimeMillis() - startTime;
            List<MetricsSnapshot> metrics = runner.getMetricsLog();

            ReportGenerator generator = new ReportGenerator(config, paths);
            generator.writeCsv(results);
            generator.writeJson(results, metrics, runner.getSystemInfo());
            generator.writeHtml(results, metrics, runner.getSystemInfo());

            System.out.println("\n=== Benchmark Results ===");
            System.out.println("Total time: " + (duration / 1000.0) + " seconds");
            System.out.println("Total runs: " + results.size());
            if (!results.isEmpty()) {
                double avgThroughput = results.stream()
                        .mapToDouble(BenchmarkResult::throughputMBps)
                        .average()
                        .orElse(0);
                double avgLatency = results.stream()
                        .mapToDouble(BenchmarkResult::avgLatencyMs)
                        .average()
                        .orElse(0);
                System.out.printf(Locale.ROOT, "Average throughput: %.2f MB/s%n", avgThroughput);
                System.out.printf(Locale.ROOT, "Average latency: %.2f ms%n", avgLatency);
            }
            System.out.println("Reports saved in: " + config.getTestDirectory());
            logger.info("Benchmark completed successfully in {} seconds", duration / 1000.0);

            // Generate diff report if baseline was provided
            if (baselineResults != null) {
                generator.writeDiffReport(baselineResults, results);
                System.out.println("Comparison report saved in: " + config.getTestDirectory());
            }

            return 0;

        } catch (ParseException e) {
            System.err.println("Error parsing command line arguments: " + e.getMessage());
            formatter.printHelp("JStorageMark", options);
            System.out.println();
            System.out.println("Examples:");
            System.out.println("  # Quick sequential write test (5GB, 4 threads)");
            System.out.println("  java -jar jstoragemark.jar -t SEQ_WRITE -s 5G -n 4");
            System.out.println();
            System.out.println("  # Random read/write with retained files and HTML report");
            System.out.println("  java -jar jstoragemark.jar -t RAND_READ,RAND_WRITE -s 1G -n 8 -r --htmlReport");
            System.out.println();
            System.out.println("  # Mixed workload with 70% reads");
            System.out.println("  java -jar jstoragemark.jar -t MIXED_RW -s 1G -n 4 --mixed-ratio 70");
            System.out.println();
            System.out.println("  # Block size sweep");
            System.out.println("  java -jar jstoragemark.jar -t SEQ_READ --block-sweep 4K,64K,1M -s 1G -n 1 -i 1");
            System.out.println();
            System.out.println("  # Compare with baseline");
            System.out.println("  java -jar jstoragemark.jar -t SEQ_READ -s 1G --compare baseline.json");
            return 1;
        } catch (NumberFormatException e) {
            System.err.println("Error: Invalid number format - " + e.getMessage());
            return 1;
        } catch (IllegalArgumentException e) {
            System.err.println("Error: Configuration error - " + e.getMessage());
            return 1;
        } catch (IOException e) {
            System.err.println("Error: IO error - " + e.getMessage());
            logger.error("IO error during benchmark", e);
            return 1;
        } catch (InterruptedException e) {
            System.err.println("Error: Benchmark was interrupted");
            logger.error("Benchmark interrupted", e);
            Thread.currentThread().interrupt();
            return 1;
        } catch (Exception e) {
            System.err.println("Error: Unexpected error - " + e.getMessage());
            logger.error("Unexpected error", e);
            return 1;
        }
    }

    private static int runBlockSweep(Path dir, BenchmarkConfig baseConfig,
                                      List<Integer> blockSizes,
                                      List<BenchmarkResult> baselineResults) throws Exception {
        List<BenchmarkResult> allResults = new ArrayList<>();
        ReportGenerator lastGenerator = null;

        for (int bSize : blockSizes) {
            System.out.println("=== Testing block size: " + formatBytes(bSize) + " ===");
            BenchmarkConfig config = new BenchmarkConfig.Builder()
                    .testDirectory(baseConfig.getTestDirectory())
                    .testTypes(baseConfig.getTestTypes())
                    .fileSizeBytes(baseConfig.getFileSizeBytes())
                    .blockSizeBytes(bSize)
                    .threads(baseConfig.getThreads())
                    .iterations(baseConfig.getIterations())
                    .queueDepth(baseConfig.getQueueDepth())
                    .verbosity(baseConfig.getVerbosity())
                    .retainTestFiles(baseConfig.isRetainTestFiles())
                    .forceSync(baseConfig.isForceSync())
                    .syncEveryNBlocks(baseConfig.getSyncEveryNBlocks())
                    .preallocateFiles(baseConfig.isPreallocateFiles())
                    .useDirectBuffer(baseConfig.isUseDirectBuffer())
                    .mixedReadPercent(baseConfig.getMixedReadPercent())
                    .reportFormats(baseConfig.getReportFormats())
                    .build();

            BenchmarkPaths paths = new BenchmarkPaths(config.getTestDirectory(), config.getSessionId());
            BenchmarkRunner runner = new BenchmarkRunner(config, paths);
            runner.startMetricsPolling();
            allResults.addAll(runner.runAll());
            lastGenerator = new ReportGenerator(config, paths);
        }

        if (lastGenerator != null) {
            lastGenerator.writeSweepReport(allResults, blockSizes);
            System.out.println("\nSweep report saved in: " + baseConfig.getTestDirectory());
        }

        if (baselineResults != null && lastGenerator != null) {
            lastGenerator.writeDiffReport(baselineResults, allResults);
            System.out.println("Comparison report saved in: " + baseConfig.getTestDirectory());
        }

        return 0;
    }

    static List<Integer> parseBlockSweep(String input) {
        return Arrays.stream(input.split(","))
                .map(String::trim)
                .map(v -> {
                    String upper = v.toUpperCase(Locale.ROOT);
                    long multiplier = 1L;
                    if (upper.endsWith("K")) {
                        multiplier = 1024L;
                        upper = upper.substring(0, upper.length() - 1);
                    } else if (upper.endsWith("M")) {
                        multiplier = 1024L * 1024;
                        upper = upper.substring(0, upper.length() - 1);
                    }
                    long size = Long.parseLong(upper.trim()) * multiplier;
                    if (size < 512 || size > 64L * 1024 * 1024) {
                        throw new IllegalArgumentException("Block size must be between 512B and 64MB: " + v);
                    }
                    return (int) size;
                })
                .toList();
    }

    static List<BenchmarkResult> loadBaseline(String path) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        var jsonNode = mapper.readTree(Path.of(path).toFile());
        var resultsNode = jsonNode.get("results");
        if (resultsNode == null || !resultsNode.isArray()) {
            throw new IllegalArgumentException("Baseline JSON must contain a 'results' array");
        }
        List<BenchmarkResult> results = new ArrayList<>();
        for (var node : resultsNode) {
            results.add(mapper.treeToValue(node, BenchmarkResult.class));
        }
        return results;
    }

    static long parseFileSize(String value) {
        String input = value.trim().toUpperCase(Locale.ROOT);
        long multiplier = 1L;
        if (input.endsWith("T")) {
            multiplier = 1024L * 1024 * 1024 * 1024;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("G")) {
            multiplier = 1024L * 1024 * 1024;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("M")) {
            multiplier = 1024L * 1024;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("K")) {
            multiplier = 1024L;
            input = input.substring(0, input.length() - 1);
        }
        long size = Long.parseLong(input.trim()) * multiplier;
        long minBytes = 1L * 1024 * 1024 * 1024;
        long maxBytes = 10L * 1024 * 1024 * 1024;
        if (size < minBytes || size > maxBytes) {
            throw new IllegalArgumentException("File size must be between 1 GB and 10 GB");
        }
        return size;
    }

    private static int parseInt(String name, String value, int min, int max) {
        int intValue = Integer.parseInt(value);
        if (intValue < min || intValue > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
        }
        return intValue;
    }

    static String formatBytes(long bytes) {
        if (bytes <= 0) return "0";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format(Locale.ROOT, "%.2f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
