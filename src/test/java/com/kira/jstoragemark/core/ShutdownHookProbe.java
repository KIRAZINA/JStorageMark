package com.kira.jstoragemark.core;

import com.kira.jstoragemark.config.BenchmarkConfig;
import com.kira.jstoragemark.fs.BenchmarkPaths;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Child-JVM probe used by {@link BenchmarkRunnerShutdownHookTest}.
 *
 * <p>Writes a small artifact into the session directory, optionally installs the runner's shutdown
 * hook, then calls {@link System#exit(int)} so the JVM executes the hook exactly as it would for
 * Ctrl+C or SIGTERM.
 */
public final class ShutdownHookProbe {

  private static final long ONE_GB = 1024L * 1024 * 1024;
  private static final String SESSION_ID = "shutdown-probe";

  private ShutdownHookProbe() {}

  /**
   * Runs the probe.
   *
   * @param args base directory followed by one of {@code registered}, {@code unregistered}, or
   *     {@code retained}
   * @throws Exception when setup fails
   */
  public static void main(String[] args) throws Exception {
    Path baseDir = Path.of(args[0]);
    String mode = args.length > 1 ? args[1] : "registered";

    BenchmarkConfig config =
        new BenchmarkConfig.Builder()
            .testDirectory(baseDir)
            .addTestType(BenchmarkConfig.TestType.SEQ_WRITE)
            .fileSizeBytes(ONE_GB)
            .blockSizeBytes(64 * 1024)
            .threads(1)
            .iterations(1)
            .warmupIterations(0)
            .queueDepth(1)
            .retainTestFiles("retained".equals(mode))
            .sessionId(SESSION_ID)
            .build();

    BenchmarkPaths paths = new BenchmarkPaths(baseDir, SESSION_ID);
    paths.ensureTestDirectory();
    Files.writeString(paths.testFilePath(1, "probe"), "orphaned benchmark data");
    Files.writeString(baseDir.resolve("keep.txt"), "base-directory file");

    BenchmarkRunner runner = new BenchmarkRunner(config, paths);
    runner.registerShutdownHook();
    if ("unregistered".equals(mode)) {
      runner.unregisterShutdownHook();
    }

    System.exit(0);
  }
}
