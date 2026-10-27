package com.kira.jstoragemark.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.kira.jstoragemark.config.BenchmarkConfig;
import com.kira.jstoragemark.fs.BenchmarkPaths;
import com.kira.jstoragemark.result.BenchmarkResult;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the two benchmark execution fixes:
 *
 * <ol>
 *   <li>ASYNC random workloads pass explicit random positions to {@link
 *       java.nio.channels.AsynchronousFileChannel} instead of sequential offsets or implicit
 *       channel sizing.
 *   <li>A fixed seed reproduces an identical operation position sequence across runs.
 * </ol>
 */
class BenchmarkRunnerRandomPositionTest {

  private static final long ONE_GB = 1024L * 1024 * 1024;
  private static final int ONE_MB = 1024 * 1024;
  private static final long FIXED_SEED = 424242L;

  @TempDir Path tempDir;

  private BenchmarkConfig.Builder baseBuilder(BenchmarkConfig.TestType type) {
    return new BenchmarkConfig.Builder()
        .testDirectory(tempDir)
        .addTestType(type)
        .fileSizeBytes(ONE_GB)
        .blockSizeBytes(ONE_MB)
        .threads(1)
        .iterations(1)
        .warmupIterations(0)
        .queueDepth(1)
        .retainTestFiles(false)
        .forceSync(false)
        .preallocateFiles(false)
        .ioMode(BenchmarkConfig.IoMode.ASYNC);
  }

  private List<Long> runWithRecordingChannel(BenchmarkConfig config, BenchmarkConfig.TestType type)
      throws Exception {
    RecordingAsyncFileChannel channel = new RecordingAsyncFileChannel(config.getFileSizeBytes());
    BenchmarkPaths paths = new BenchmarkPaths(tempDir, config.getSessionId());
    BenchmarkRunner runner = new BenchmarkRunner(config, paths, file -> channel);
    List<BenchmarkResult> results = runner.runAll();

    assertThat(results).hasSize(1);
    assertThat(results.get(0).testType()).isEqualTo(type.name());

    List<Long> positions =
        type == BenchmarkConfig.TestType.RAND_WRITE
            ? channel.writePositions()
            : channel.readPositions();
    assertThat(positions).isNotEmpty();
    return positions;
  }

  @Test
  @DisplayName("ASYNC RAND_READ passes explicit random offsets to the channel")
  void asyncRandomReadUsesExplicitRandomOffsets() throws Exception {
    BenchmarkConfig config =
        baseBuilder(BenchmarkConfig.TestType.RAND_READ).randomSeed(FIXED_SEED).build();

    List<Long> positions = runWithRecordingChannel(config, BenchmarkConfig.TestType.RAND_READ);

    assertPositionsAreRandomWithinBounds(positions, config);
  }

  @Test
  @DisplayName("ASYNC RAND_WRITE passes explicit random offsets to the channel")
  void asyncRandomWriteUsesExplicitRandomOffsets() throws Exception {
    BenchmarkConfig config =
        baseBuilder(BenchmarkConfig.TestType.RAND_WRITE).randomSeed(FIXED_SEED).build();

    List<Long> positions = runWithRecordingChannel(config, BenchmarkConfig.TestType.RAND_WRITE);

    assertPositionsAreRandomWithinBounds(positions, config);
  }

  @Test
  @DisplayName("ASYNC MIXED_RW passes explicit random offsets to the channel")
  void asyncMixedRwUsesExplicitRandomOffsets() throws Exception {
    BenchmarkConfig config =
        baseBuilder(BenchmarkConfig.TestType.MIXED_RW)
            .randomSeed(FIXED_SEED)
            .mixedReadPercent(50)
            .build();

    RecordingAsyncFileChannel channel = new RecordingAsyncFileChannel(config.getFileSizeBytes());
    BenchmarkPaths paths = new BenchmarkPaths(tempDir, config.getSessionId());
    new BenchmarkRunner(config, paths, file -> channel).runAll();

    List<Long> positions = new ArrayList<>(channel.readPositions());
    positions.addAll(channel.writePositions());
    assertThat(positions).isNotEmpty();
    assertPositionsAreRandomWithinBounds(positions, config);
    assertThat(channel.readPositions()).isNotEmpty();
    assertThat(channel.writePositions()).isNotEmpty();
  }

  @Test
  @DisplayName("ASYNC SEQ_READ keeps sequential offsets (no regression)")
  void asyncSequentialReadKeepsSequentialOffsets() throws Exception {
    BenchmarkConfig config =
        baseBuilder(BenchmarkConfig.TestType.SEQ_READ).randomSeed(FIXED_SEED).build();

    List<Long> positions = runWithRecordingChannel(config, BenchmarkConfig.TestType.SEQ_READ);

    long expected = 0;
    for (long position : positions) {
      assertThat(position).isEqualTo(expected);
      expected += ONE_MB;
    }
  }

  @Test
  @DisplayName("Two ASYNC runs with the same seed produce identical position sequences")
  void asyncRunsWithSameSeedProduceIdenticalPositions() throws Exception {
    BenchmarkConfig config =
        baseBuilder(BenchmarkConfig.TestType.RAND_WRITE).randomSeed(FIXED_SEED).build();

    List<Long> first = runWithRecordingChannel(config, BenchmarkConfig.TestType.RAND_WRITE);
    List<Long> second = runWithRecordingChannel(config, BenchmarkConfig.TestType.RAND_WRITE);

    assertThat(second).isEqualTo(first);
  }

  @Test
  @DisplayName("Two ASYNC RAND_READ runs with the same seed produce identical position sequences")
  void asyncReadRunsWithSameSeedProduceIdenticalPositions() throws Exception {
    BenchmarkConfig config =
        baseBuilder(BenchmarkConfig.TestType.RAND_READ).randomSeed(FIXED_SEED).build();

    List<Long> first = runWithRecordingChannel(config, BenchmarkConfig.TestType.RAND_READ);
    List<Long> second = runWithRecordingChannel(config, BenchmarkConfig.TestType.RAND_READ);

    assertThat(second).isEqualTo(first);
  }

  @Test
  @DisplayName("Different seeds produce different position sequences")
  void differentSeedsProduceDifferentPositions() throws Exception {
    BenchmarkConfig first =
        baseBuilder(BenchmarkConfig.TestType.RAND_WRITE).randomSeed(FIXED_SEED).build();
    BenchmarkConfig second =
        baseBuilder(BenchmarkConfig.TestType.RAND_WRITE).randomSeed(FIXED_SEED + 1).build();

    List<Long> firstPositions =
        runWithRecordingChannel(first, BenchmarkConfig.TestType.RAND_WRITE);
    List<Long> secondPositions =
        runWithRecordingChannel(second, BenchmarkConfig.TestType.RAND_WRITE);

    assertThat(secondPositions).isNotEqualTo(firstPositions);
  }

  @Test
  @DisplayName("SYNC position sequence is reproducible for a fixed seed")
  void syncPositionSequenceIsReproducible() {
    List<Long> first = syncPositionSequence(FIXED_SEED);
    List<Long> second = syncPositionSequence(FIXED_SEED);

    assertThat(second).isEqualTo(first);
    assertThat(first).hasSize((int) (ONE_GB / ONE_MB));
    assertThat(first).isNotEqualTo(syncPositionSequence(FIXED_SEED + 1));
  }

  @Test
  @DisplayName("Position resolver clamps to [0, fileSize - bytesToProcess]")
  void positionResolverClampsToValidRange() {
    Random rng = new Random(FIXED_SEED);
    long fileSize = 10 * ONE_MB;
    int blockSize = ONE_MB;

    for (int i = 0; i < 10_000; i++) {
      long position = BenchmarkRunner.resolvePosition(true, rng, fileSize, 0L, blockSize);
      assertThat(position).isBetween(0L, fileSize - blockSize);
    }

    assertThat(BenchmarkRunner.resolvePosition(false, rng, fileSize, 4096L, blockSize))
        .isEqualTo(4096L);
  }

  @Test
  @DisplayName("Position resolver handles a block larger than the file")
  void positionResolverHandlesOversizedBlock() {
    Random rng = new Random(FIXED_SEED);
    assertThat(BenchmarkRunner.resolvePosition(true, rng, 1024L, 0L, 4096)).isZero();
  }

  /**
   * Rebuilds the exact position sequence the SYNC loop would emit, using the same thread-bound
   * generator and resolver the sync path calls.
   */
  private List<Long> syncPositionSequence(long seed) {
    BenchmarkConfig config =
        baseBuilder(BenchmarkConfig.TestType.RAND_WRITE).randomSeed(seed).build();
    BenchmarkPaths paths = new BenchmarkPaths(tempDir, config.getSessionId());
    BenchmarkRunner runner = new BenchmarkRunner(config, paths);

    Random rng = runner.getThreadRng(0);
    long fileSize = config.getFileSizeBytes();
    int blockSize = config.getBlockSizeBytes();
    List<Long> positions = new ArrayList<>();
    long bytesProcessed = 0;
    while (bytesProcessed < fileSize) {
      int bytesToProcess = (int) Math.min(blockSize, fileSize - bytesProcessed);
      positions.add(BenchmarkRunner.resolvePosition(true, rng, fileSize, bytesProcessed,
          bytesToProcess));
      bytesProcessed += bytesToProcess;
    }
    return positions;
  }

  private void assertPositionsAreRandomWithinBounds(List<Long> positions, BenchmarkConfig config) {
    long fileSize = config.getFileSizeBytes();
    int blockSize = config.getBlockSizeBytes();
    long maxPosition = fileSize - blockSize;

    for (long position : positions) {
      assertThat(position).isBetween(0L, maxPosition);
    }

    // A sequential run would emit exactly 0, blockSize, 2*blockSize, ... in order.
    boolean sequential = true;
    long expected = 0;
    for (long position : positions) {
      if (position != expected) {
        sequential = false;
        break;
      }
      expected += blockSize;
    }
    assertThat(sequential)
        .as("random workload must not emit sequential offsets")
        .isFalse();

    // Random offsets should reach the far half of the file at least once.
    assertThat(positions.stream().anyMatch(p -> p > maxPosition / 2))
        .as("random offsets should span the file, not cluster at the start")
        .isTrue();
  }
}
