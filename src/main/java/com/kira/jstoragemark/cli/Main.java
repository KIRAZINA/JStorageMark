package com.kira.jstoragemark.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kira.jstoragemark.config.BenchmarkConfig;
import com.kira.jstoragemark.core.BenchmarkRunner;
import com.kira.jstoragemark.fs.BenchmarkPaths;
import com.kira.jstoragemark.metrics.MetricsSnapshot;
import com.kira.jstoragemark.report.ReportGenerator;
import com.kira.jstoragemark.result.BenchmarkResult;

/**
 * Command-line entry point for JStorageMark.
 * Parses arguments, builds configuration, runs benchmarks, and generates reports.
 *
 * Example usage:
 *   java -jar jstoragemark.jar -d /tmp/testdir -t SEQ_WRITE -s 2147483648 -b 65536 -n 4 -i 3
 */
public final class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        Options options = new Options();

        options.addOption("d", "directory", true, "Test directory path");
        options.addOption("t", "test", true, "Test type(s): SEQ_READ, SEQ_WRITE, RAND_READ, RAND_WRITE (comma-separated)");
        options.addOption("s", "size", true, "File size in bytes (default 5GB)");
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

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();

        try {
            CommandLine cmd = parser.parse(options, args);

            // Validate and get directory
            String dirPath = cmd.getOptionValue("d", "./jstoragemark-tests");
            Path dir = Path.of(dirPath);
            if (!Files.exists(dir)) {
                System.err.println("Error: Test directory does not exist: " + dirPath);
                System.exit(1);
            }
            if (!Files.isDirectory(dir)) {
                System.err.println("Error: Path is not a directory: " + dirPath);
                System.exit(1);
            }

            BenchmarkConfig.Builder builder = new BenchmarkConfig.Builder()
                    .testDirectory(dir)
                    .fileSizeBytes(parseFileSize(cmd.getOptionValue("s", String.valueOf(5L * 1024 * 1024 * 1024))))
                    .blockSizeBytes(parseInt("block size", cmd.getOptionValue("b", String.valueOf(128 * 1024)), 
                            512, 16 * 1024 * 1024))
                    .threads(parseInt("threads", cmd.getOptionValue("n", "4"), 1, 128))
                    .iterations(parseInt("iterations", cmd.getOptionValue("i", "5"), 1, 100))
                    .queueDepth(parseInt("queue depth", cmd.getOptionValue("q", "8"), 1, 1024))
                    .verbosity(parseInt("verbosity", cmd.getOptionValue("v", "1"), 0, 2))
                    .retainTestFiles(cmd.hasOption("r"))
                    .forceSync(!cmd.hasOption("nfs"))
                    .syncEveryNBlocks(parseInt("sync every", cmd.getOptionValue("se", "0"), 0, 10000))
                    .preallocateFiles(!cmd.hasOption("np"))
                    .useDirectBuffer(!cmd.hasOption("hb"));

            // Parse test types
            String[] testTypes = cmd.getOptionValue("t", "SEQ_READ,SEQ_WRITE").split(",");
            for (String tt : testTypes) {
                tt = tt.trim().toUpperCase(Locale.ROOT);
                try {
                    builder.addTestType(BenchmarkConfig.TestType.valueOf(tt));
                } catch (IllegalArgumentException e) {
                    System.err.println("Error: Unknown test type: " + tt);
                    System.exit(1);
                }
            }

            // Add report formats
            builder.addReportFormat(BenchmarkConfig.ReportFormat.CSV);
            builder.addReportFormat(BenchmarkConfig.ReportFormat.JSON);
            if (cmd.hasOption("html")) {
                builder.addReportFormat(BenchmarkConfig.ReportFormat.HTML);
                builder.embedCharts(true);
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
            generator.writeJson(results, metrics);
            generator.writeHtml(results, metrics);

            System.out.println("\n=== Benchmark Results ===");
            System.out.println("Total time: " + (duration / 1000.0) + " seconds");
            System.out.println("Total runs: " + results.size());
            if (!results.isEmpty()) {
                double avgThroughput = results.stream()
                        .mapToDouble(BenchmarkResult::getThroughputMBps)
                        .average()
                        .orElse(0);
                double avgLatency = results.stream()
                        .mapToDouble(BenchmarkResult::getAvgLatencyMs)
                        .average()
                        .orElse(0);
                System.out.printf(Locale.ROOT, "Average throughput: %.2f MB/s%n", avgThroughput);
                System.out.printf(Locale.ROOT, "Average latency: %.2f ms%n", avgLatency);
            }
            System.out.println("Reports saved in: " + config.getTestDirectory());
            logger.info("Benchmark completed successfully in {} seconds", duration / 1000.0);

        } catch (ParseException e) {
            System.err.println("Error parsing command line arguments: " + e.getMessage());
            formatter.printHelp("JStorageMark", options);
            System.exit(1);
        } catch (NumberFormatException e) {
            System.err.println("Error: Invalid number format - " + e.getMessage());
            System.exit(1);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: Configuration error - " + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Error: IO error - " + e.getMessage());
            logger.error("IO error during benchmark", e);
            System.exit(1);
        } catch (InterruptedException e) {
            System.err.println("Error: Benchmark was interrupted");
            logger.error("Benchmark interrupted", e);
            Thread.currentThread().interrupt();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Error: Unexpected error - " + e.getMessage());
            logger.error("Unexpected error", e);
            System.exit(1);
        }
    }

    private static long parseFileSize(String value) throws NumberFormatException {
        long size = Long.parseLong(value);
        if (size <= 0) {
            throw new IllegalArgumentException("File size must be positive");
        }
        if (size < 1024 * 1024) {
            throw new IllegalArgumentException("File size must be at least 1 MB");
        }
        return size;
    }

    private static int parseInt(String name, String value, int min, int max) throws NumberFormatException {
        int intValue = Integer.parseInt(value);
        if (intValue < min || intValue > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
        }
        return intValue;
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) return "0";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format(Locale.ROOT, "%.2f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
