package com.kira.jstoragemark.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kira.jstoragemark.config.BenchmarkConfig;
import com.kira.jstoragemark.fs.BenchmarkPaths;
import com.kira.jstoragemark.result.BenchmarkResult;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the non-blocking asynchronous pipeline in {@code runSingleThreadAsync}.
 *
 * <p>Completions are delivered from a separate executor with artificial latency, so a blocking
 * implementation (one op in flight) and a pipelined one (up to {@code queueDepth} in flight) are
 * distinguishable by the observed concurrency high-water mark.
 */
class BenchmarkRunnerAsyncPipelineTest {

  private static final long FILE_SIZE = 1024L * 1024 * 1024;
  private static final int BLOCK_SIZE = 1024 * 1024;
  private static final int BLOCKS = (int) (FILE_SIZE / BLOCK_SIZE);

  @TempDir Path tempDir;

  private final ExecutorService completer = Executors.newCachedThreadPool();

  @AfterEach
  void tearDown() {
    completer.shutdownNow();
  }

  private BenchmarkConfig config(BenchmarkConfig.TestType type, int queueDepth) {
    return config(type, queueDepth, Math.max(1, (queueDepth + 1) / 2));
  }

  private BenchmarkConfig config(BenchmarkConfig.TestType type, int queueDepth, int threads) {
    return new BenchmarkConfig.Builder()
        .testDirectory(tempDir)
        .addTestType(type)
        .fileSizeBytes(FILE_SIZE)
        .blockSizeBytes(BLOCK_SIZE)
        .threads(threads)
        .iterations(1)
        .warmupIterations(0)
        .queueDepth(queueDepth)
        .retainTestFiles(false)
        .forceSync(false)
        .preallocateFiles(false)
        .ioMode(BenchmarkConfig.IoMode.ASYNC)
        .build();
  }

  /** Opens one tracking channel per worker thread and aggregates the observed concurrency. */
  private static final class ChannelTracker implements BenchmarkRunner.AsyncChannelFactory {

    private final long fileSize;
    private final ExecutorService completer;
    private final long delayMillis;
    private final int failOnOperation;
    private final int maxChunkBytes;
    private final List<ConcurrencyTrackingAsyncFileChannel> channels =
        Collections.synchronizedList(new ArrayList<>());

    ChannelTracker(
        long fileSize, ExecutorService completer, long delayMillis, int failOnOperation) {
      this(fileSize, completer, delayMillis, failOnOperation, 0);
    }

    ChannelTracker(
        long fileSize,
        ExecutorService completer,
        long delayMillis,
        int failOnOperation,
        int maxChunkBytes) {
      this.fileSize = fileSize;
      this.completer = completer;
      this.delayMillis = delayMillis;
      this.failOnOperation = failOnOperation;
      this.maxChunkBytes = maxChunkBytes;
    }

    @Override
    public java.nio.channels.AsynchronousFileChannel open(Path file) {
      ConcurrencyTrackingAsyncFileChannel channel =
          new ConcurrencyTrackingAsyncFileChannel(
              fileSize, completer, delayMillis, failOnOperation, maxChunkBytes);
      channels.add(channel);
      return channel;
    }

    int maxInFlight() {
      synchronized (channels) {
        return channels.stream()
            .mapToInt(ConcurrencyTrackingAsyncFileChannel::maxInFlight)
            .max()
            .orElse(0);
      }
    }

    int operationCount() {
      synchronized (channels) {
        return channels.stream()
            .mapToInt(ConcurrencyTrackingAsyncFileChannel::operationCount)
            .sum();
      }
    }
  }

  private List<BenchmarkResult> run(BenchmarkConfig config, ChannelTracker tracker)
      throws Exception {
    BenchmarkPaths paths = new BenchmarkPaths(tempDir, config.getSessionId());
    BenchmarkRunner runner = new BenchmarkRunner(config, paths, tracker);
    List<BenchmarkResult> results = runner.runAll();
    assertThat(results).hasSize(config.getThreads());
    return results;
  }

  @Test
  @DisplayName("queueDepth=2 with one thread keeps at most two operations in flight")
  void queueDepthTwoBoundsConcurrency() throws Exception {
    BenchmarkConfig cfg = config(BenchmarkConfig.TestType.SEQ_WRITE, 2, 1);
    ChannelTracker tracker = new ChannelTracker(FILE_SIZE, completer, 2, 0);

    run(cfg, tracker);

    assertThat(tracker.maxInFlight()).isEqualTo(2);
  }

  @Test
  @DisplayName("A deeper queue pipelines multiple operations concurrently")
  void deeperQueuePipelinesOperations() throws Exception {
    BenchmarkConfig cfg = config(BenchmarkConfig.TestType.SEQ_WRITE, 8, 4);
    ChannelTracker tracker = new ChannelTracker(FILE_SIZE, completer, 5, 0);

    run(cfg, tracker);

    assertThat(tracker.maxInFlight())
        .as("pipeline should keep multiple operations in flight")
        .isGreaterThan(1)
        .isLessThanOrEqualTo(8);
  }

  @Test
  @DisplayName("Max in-flight never exceeds queueDepth")
  void inFlightNeverExceedsQueueDepth() throws Exception {
    for (int queueDepth : new int[] {1, 2, 4, 8}) {
      int threads = Math.max(1, (queueDepth + 1) / 2);
      BenchmarkConfig cfg = config(BenchmarkConfig.TestType.SEQ_WRITE, queueDepth, threads);
      ChannelTracker tracker = new ChannelTracker(FILE_SIZE, completer, 3, 0);

      run(cfg, tracker);

      assertThat(tracker.maxInFlight())
          .as("queueDepth=%d threads=%d", queueDepth, threads)
          .isLessThanOrEqualTo(queueDepth);
    }
  }

  @Test
  @DisplayName("Pipeline processes exactly fileSizeBytes per thread")
  void pipelineProcessesEntireFile() throws Exception {
    BenchmarkConfig cfg = config(BenchmarkConfig.TestType.SEQ_WRITE, 8, 4);
    ChannelTracker tracker = new ChannelTracker(FILE_SIZE, completer, 1, 0);

    List<BenchmarkResult> results = run(cfg, tracker);

    for (BenchmarkResult result : results) {
      assertThat(result.bytesProcessed()).isEqualTo(FILE_SIZE);
      assertThat(result.iops()).isGreaterThan(0);
      assertThat(result.throughputMBps()).isGreaterThan(0);
    }
    assertThat(tracker.operationCount()).isEqualTo(BLOCKS * cfg.getThreads());
  }

  @Test
  @DisplayName("Short completions are retried until exactly fileSizeBytes is transferred")
  void shortCompletionsTransferEntireFile() throws Exception {
    BenchmarkConfig cfg = config(BenchmarkConfig.TestType.SEQ_WRITE, 4, 2);
    ChannelTracker tracker = new ChannelTracker(FILE_SIZE, completer, 0, 0, 64 * 1024);

    List<BenchmarkResult> results = run(cfg, tracker);

    for (BenchmarkResult result : results) {
      assertThat(result.bytesProcessed()).isEqualTo(FILE_SIZE);
      assertThat(result.iops()).isGreaterThan(0);
    }
    assertThat(tracker.maxInFlight()).isLessThanOrEqualTo(4);
  }

  @Test
  @DisplayName("RAND_READ pipeline dispatches every block exactly once")
  void randomReadPipelineDispatchesEveryBlock() throws Exception {
    BenchmarkConfig cfg = config(BenchmarkConfig.TestType.RAND_READ, 4, 2);
    ChannelTracker tracker = new ChannelTracker(FILE_SIZE, completer, 1, 0);

    List<BenchmarkResult> results = run(cfg, tracker);

    for (BenchmarkResult result : results) {
      assertThat(result.bytesProcessed()).isEqualTo(FILE_SIZE);
    }
    assertThat(tracker.operationCount()).isEqualTo(BLOCKS * cfg.getThreads());
    assertThat(tracker.maxInFlight()).isGreaterThan(1).isLessThanOrEqualTo(4);
  }

  @Test
  @DisplayName("MIXED_RW pipeline dispatches every block exactly once")
  void mixedRwPipelineDispatchesEveryBlock() throws Exception {
    BenchmarkConfig cfg = config(BenchmarkConfig.TestType.MIXED_RW, 4, 2);
    ChannelTracker tracker = new ChannelTracker(FILE_SIZE, completer, 1, 0);

    List<BenchmarkResult> results = run(cfg, tracker);

    for (BenchmarkResult result : results) {
      assertThat(result.bytesProcessed()).isEqualTo(FILE_SIZE);
    }
    assertThat(tracker.operationCount()).isEqualTo(BLOCKS * cfg.getThreads());
  }

  @Test
  @DisplayName("A failed operation fails the run instead of hanging")
  void failedOperationFailsTheRun() {
    BenchmarkConfig cfg = config(BenchmarkConfig.TestType.SEQ_WRITE, 4, 2);
    ChannelTracker tracker = new ChannelTracker(FILE_SIZE, completer, 1, 3);

    BenchmarkPaths paths = new BenchmarkPaths(tempDir, cfg.getSessionId());
    BenchmarkRunner runner = new BenchmarkRunner(cfg, paths, tracker);

    assertThatThrownBy(runner::runAll)
        .isInstanceOf(RuntimeException.class)
        .hasRootCauseInstanceOf(IOException.class)
        .rootCause()
        .hasMessageContaining("injected failure");
  }

  @Test
  @DisplayName("A failed operation does not leak queue permits")
  void failedOperationDoesNotLeakPermits() throws Exception {
    BenchmarkConfig cfg = config(BenchmarkConfig.TestType.SEQ_WRITE, 2, 1);
    BenchmarkPaths paths = new BenchmarkPaths(tempDir, cfg.getSessionId());

    ChannelTracker failing = new ChannelTracker(FILE_SIZE, completer, 1, 2);
    BenchmarkRunner failingRunner = new BenchmarkRunner(cfg, paths, failing);
    assertThatThrownBy(failingRunner::runAll).isInstanceOf(RuntimeException.class);

    // A leaked permit would starve this run and hang it rather than fail it.
    ChannelTracker healthy = new ChannelTracker(FILE_SIZE, completer, 0, 0);
    BenchmarkRunner healthyRunner = new BenchmarkRunner(cfg, paths, healthy);
    List<BenchmarkResult> results = healthyRunner.runAll();

    assertThat(results).hasSize(1);
    assertThat(results.get(0).bytesProcessed()).isEqualTo(FILE_SIZE);
  }

  @Test
  @DisplayName("Pipeline completes within a bounded time (no deadlock)")
  void pipelineCompletesWithoutDeadlock() throws Exception {
    BenchmarkConfig cfg = config(BenchmarkConfig.TestType.SEQ_WRITE, 8, 4);
    ChannelTracker tracker = new ChannelTracker(FILE_SIZE, completer, 0, 0);

    BenchmarkPaths paths = new BenchmarkPaths(tempDir, cfg.getSessionId());
    BenchmarkRunner runner = new BenchmarkRunner(cfg, paths, tracker);

    long startNs = System.nanoTime();
    runner.runAll();
    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);

    assertThat(elapsedMs).as("run should finish promptly").isLessThan(30_000L);
  }
}