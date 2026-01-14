package com.kira.jstoragemark.cli;

import com.kira.jstoragemark.config.BenchmarkConfig;
import com.kira.jstoragemark.fs.BenchmarkPaths;
import com.kira.jstoragemark.core.BenchmarkRunner;
import com.kira.jstoragemark.report.ReportGenerator;
import com.kira.jstoragemark.result.BenchmarkResult;
import com.kira.jstoragemark.metrics.MetricsSnapshot;

import org.apache.commons.cli.*;

import java.nio.file.Path;
import java.util.List;

/**
 * Command-line entry point for JStorageMark.
 * Parses arguments, builds configuration, runs benchmarks, and generates reports.
 *
 * Example usage:
 *   java -jar jstoragemark.jar -d /tmp/testdir -t SEQ_WRITE -s 2147483648 -b 65536 -n 4 -i 3
 */
public final class Main {

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

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();

        try {
            CommandLine cmd = parser.parse(options, args);

            Path dir = Path.of(cmd.getOptionValue("d", "./jstoragemark-tests"));

            BenchmarkConfig.Builder builder = new BenchmarkConfig.Builder()
                    .testDirectory(dir)
                    .fileSizeBytes(Long.parseLong(cmd.getOptionValue("s", String.valueOf(5L * 1024 * 1024 * 1024))))
                    .blockSizeBytes(Integer.parseInt(cmd.getOptionValue("b", String.valueOf(128 * 1024))))
                    .threads(Integer.parseInt(cmd.getOptionValue("n", "4")))
                    .iterations(Integer.parseInt(cmd.getOptionValue("i", "5")))
                    .queueDepth(Integer.parseInt(cmd.getOptionValue("q", "8")))
                    .verbosity(Integer.parseInt(cmd.getOptionValue("v", "1")))
                    .retainTestFiles(cmd.hasOption("r"));

            // Parse test types
            String[] testTypes = cmd.getOptionValue("t", "SEQ_READ,SEQ_WRITE").split(",");
            for (String tt : testTypes) {
                builder.addTestType(BenchmarkConfig.TestType.valueOf(tt.trim().toUpperCase()));
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

            BenchmarkRunner runner = new BenchmarkRunner(config, paths);
            runner.startMetricsPolling();

            List<BenchmarkResult> results = runner.runAll();
            List<MetricsSnapshot> metrics = runner.getMetricsLog();

            ReportGenerator generator = new ReportGenerator(config, paths);
            generator.writeCsv(results);
            generator.writeJson(results, metrics);
            generator.writeHtml(results, metrics);

            System.out.println("Benchmark completed. Reports saved in: " + config.getTestDirectory());

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            formatter.printHelp("JStorageMark", options);
            System.exit(1);
        }
    }
}
