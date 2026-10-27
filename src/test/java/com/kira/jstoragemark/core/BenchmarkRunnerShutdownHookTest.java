package com.kira.jstoragemark.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.kira.jstoragemark.config.BenchmarkConfig;
import com.kira.jstoragemark.fs.BenchmarkPaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the JVM shutdown-hook lifecycle in {@link BenchmarkRunner}.
 *
 * <p>The in-process cases exercise the hook body deterministically. The forked-process cases prove
 * that the hook is actually installed on the JVM and that a real {@code System.exit} reclaims the
 * session payload, which is the behavior required for Ctrl+C / SIGTERM handling.
 */
class BenchmarkRunnerShutdownHookTest {

  private static final long ONE_GB = 1024L * 1024 * 1024;
  private static final String SESSION_ID = "shutdown-probe";

  @TempDir Path tempDir;

  private BenchmarkConfig config(boolean retainTestFiles) {
    return new BenchmarkConfig.Builder()
        .testDirectory(tempDir)
        .addTestType(BenchmarkConfig.TestType.SEQ_WRITE)
        .fileSizeBytes(ONE_GB)
        .blockSizeBytes(64 * 1024)
        .threads(1)
        .iterations(1)
        .warmupIterations(0)
        .queueDepth(1)
        .retainTestFiles(retainTestFiles)
        .sessionId(SESSION_ID)
        .build();
  }

  @Test
  @DisplayName("Shutdown hook reclaims the session payload but leaves base-directory files")
  void shutdownHookReclaimsSessionPayload() throws Exception {
    BenchmarkPaths paths = new BenchmarkPaths(tempDir, SESSION_ID);
    paths.ensureTestDirectory();
    Path payload = paths.testFilePath(1, "hook");
    Files.writeString(payload, "orphaned benchmark data");
    Path userFile = tempDir.resolve("user-notes.txt");
    Files.writeString(userFile, "keep me");
    Path sibling = tempDir.resolve("session-other").resolve("run-001.keep.bin");
    Files.createDirectories(sibling.getParent());
    Files.writeString(sibling, "keep me too");

    BenchmarkRunner runner = new BenchmarkRunner(config(false), paths);
    try {
      runner.registerShutdownHook();
      assertThat(runner.isShutdownHookRegistered()).isTrue();

      runner.handleJvmShutdown();

      assertThat(paths.getSessionDir()).doesNotExist();
      assertThat(payload).doesNotExist();
      assertThat(userFile).exists();
      assertThat(sibling).exists();
      assertThat(tempDir).exists();
    } finally {
      runner.unregisterShutdownHook();
    }
  }

  @Test
  @DisplayName("Shutdown hook honors retainTestFiles")
  void shutdownHookHonorsRetainFlag() throws Exception {
    BenchmarkPaths paths = new BenchmarkPaths(tempDir, SESSION_ID);
    paths.ensureTestDirectory();
    Path payload = paths.testFilePath(1, "hook");
    Files.writeString(payload, "retained benchmark data");

    BenchmarkRunner runner = new BenchmarkRunner(config(true), paths);
    try {
      runner.registerShutdownHook();
      runner.handleJvmShutdown();

      assertThat(payload).exists();
      assertThat(paths.getSessionDir()).exists();
    } finally {
      runner.unregisterShutdownHook();
      paths.cleanupSessionFiles(false);
    }
  }

  @Test
  @DisplayName("Shutdown cleanup is idempotent and hook registration can be removed")
  void shutdownCleanupIsIdempotent() throws Exception {
    BenchmarkPaths paths = new BenchmarkPaths(tempDir, SESSION_ID);
    paths.ensureTestDirectory();
    Files.writeString(paths.testFilePath(1, "hook"), "data");

    BenchmarkRunner runner = new BenchmarkRunner(config(false), paths);
    try {
      runner.registerShutdownHook();
      runner.unregisterShutdownHook();
      assertThat(runner.isShutdownHookRegistered()).isFalse();

      // A late hook invocation after unregistration must still be safe and exactly-once.
      runner.handleJvmShutdown();
      runner.handleJvmShutdown();

      assertThat(paths.getSessionDir()).doesNotExist();
    } finally {
      runner.unregisterShutdownHook();
    }
  }

  @Test
  @DisplayName("Graceful completion removes the hook and cleans the session payload exactly once")
  void gracefulCompletionRemovesHook() throws Exception {
    BenchmarkPaths paths = new BenchmarkPaths(tempDir, SESSION_ID);
    BenchmarkRunner runner = new BenchmarkRunner(config(false), paths);

    runner.runAll();

    assertThat(runner.isShutdownHookRegistered()).isFalse();
    assertThat(paths.getSessionDir()).doesNotExist();
    assertThat(tempDir).exists();
  }

  @Test
  @DisplayName("Real JVM exit runs the installed hook and reclaims orphaned .bin files")
  void realJvmExitRunsShutdownHook() throws Exception {
    ProbeResult result = runProbe("registered");

    assertThat(result.exitCode).as("probe output: %s", result.output).isZero();
    assertThat(tempDir.resolve("session-" + SESSION_ID)).doesNotExist();
    assertThat(tempDir.resolve("keep.txt")).exists();
  }

  @Test
  @DisplayName("Unregistered hook leaves session files in place on JVM exit")
  void unregisteredHookLeavesFiles() throws Exception {
    ProbeResult result = runProbe("unregistered");

    assertThat(result.exitCode).as("probe output: %s", result.output).isZero();
    assertThat(tempDir.resolve("session-" + SESSION_ID)).exists();
    assertThat(tempDir.resolve("session-" + SESSION_ID)
            .resolve("run-001.probe." + SESSION_ID + ".bin"))
        .exists();
  }

  @Test
  @DisplayName("Retained session survives a real JVM exit")
  void retainedSessionSurvivesJvmExit() throws Exception {
    ProbeResult result = runProbe("retained");

    assertThat(result.exitCode).as("probe output: %s", result.output).isZero();
    assertThat(tempDir.resolve("session-" + SESSION_ID)).exists();
  }

  private ProbeResult runProbe(String mode) throws IOException, InterruptedException {
    Path java = Path.of(
        System.getProperty("java.home"),
        "bin",
        System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
            ? "java.exe"
            : "java");
    String classpath = System.getProperty(
        "surefire.test.class.path", System.getProperty("java.class.path"));

    ProcessBuilder builder = new ProcessBuilder(
        java.toString(),
        "-cp",
        classpath,
        ShutdownHookProbe.class.getName(),
        tempDir.toString(),
        mode);
    builder.redirectErrorStream(true);
    Process process = builder.start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    if (!process.waitFor(120, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      fail("Shutdown probe timed out. Output: " + output);
    }
    return new ProbeResult(process.exitValue(), output);
  }

  private record ProbeResult(int exitCode, String output) {}
}
