package com.kira.jstoragemark.core;

import com.kira.jstoragemark.config.BenchmarkConfig;
import com.kira.jstoragemark.fs.BenchmarkPaths;
import com.kira.jstoragemark.metrics.IMetricsCollector;
import com.kira.jstoragemark.metrics.MetricsCollector;
import com.kira.jstoragemark.metrics.MetricsSnapshot;
import com.kira.jstoragemark.report.SystemInfoSnapshot;
import com.kira.jstoragemark.result.BenchmarkResult;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.HdrHistogram.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.HardwareAbstractionLayer;

/**
 * Executes storage benchmark workloads against files inside a dedicated test directory.
 *
 * <p>Supports synchronous and asynchronous I/O modes, sequential and random access patterns, and
 * optional deterministic seeding so that a fixed seed reproduces the exact operation position
 * sequence.
 */
public final class BenchmarkRunner implements IBenchmarkRunner {

  private static final Logger logger = LoggerFactory.getLogger(BenchmarkRunner.class);

  /** Multiplier used to derive an independent, deterministic seed per worker thread. */
  private static final long THREAD_SEED_MIX = 0x9E3779B97F4A7C15L;

  /** Number of attempts made to remove a session directory while files may still be closing. */
  private static final int CLEANUP_ATTEMPTS = 3;

  /** Delay between cleanup attempts while worker threads release their file handles. */
  private static final long CLEANUP_RETRY_MILLIS = 200L;

  /** Opens the asynchronous channel used for ASYNC mode; replaceable in tests. */
  @FunctionalInterface
  interface AsyncChannelFactory {
    AsynchronousFileChannel open(Path file) throws IOException;
  }

  private static final AsyncChannelFactory DEFAULT_ASYNC_CHANNEL_FACTORY =
      file ->
          AsynchronousFileChannel.open(
              file,
              StandardOpenOption.CREATE,
              StandardOpenOption.READ,
              StandardOpenOption.WRITE);

  private final BenchmarkConfig config;
  private final BenchmarkPaths paths;
  private final IMetricsCollector metricsCollector;
  private final ExecutorService ioExecutor;
  private final AsyncChannelFactory asyncChannelFactory;

  private final List<BenchmarkResult> results = Collections.synchronizedList(new ArrayList<>());

  private final ThreadLocal<ByteBuffer> bufferPool;
  private final long baseSeed;
  private final boolean useFixedSeed;

  private final Semaphore queueSemaphore;
  private final SystemInfoSnapshot systemInfo;

  /** Set by the shutdown hook so worker loops stop submitting new blocks. */
  private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

  /** Guards hook lifecycle and makes graceful versus shutdown cleanup exactly-once. */
  private final Object cleanupLock = new Object();

  private volatile Thread shutdownHook;
  private boolean sessionCleanupDone;
  private boolean sessionCleanupInProgress;

  private final AtomicLong progressBytesProcessed = new AtomicLong(0);
  private final Instant progressStartTime = Instant.now();
  private volatile BenchmarkConfig.TestType currentTestType;
  private volatile int currentIteration;
  private volatile int totalIterations;
  private ScheduledExecutorService progressExecutor;

  /**
   * Creates a runner that opens real asynchronous channels.
   *
   * @param config benchmark configuration
   * @param paths test directory helper
   */
  public BenchmarkRunner(BenchmarkConfig config, BenchmarkPaths paths) {
    this(config, paths, DEFAULT_ASYNC_CHANNEL_FACTORY);
  }

  /**
   * Creates a runner with an injectable asynchronous channel factory.
   *
   * <p>Package-private so tests can observe the exact file position handed to every asynchronous
   * read and write without touching the public API.
   *
   * @param config benchmark configuration
   * @param paths test directory helper
   * @param asyncChannelFactory factory used to open the asynchronous channel
   */
  BenchmarkRunner(
      BenchmarkConfig config, BenchmarkPaths paths, AsyncChannelFactory asyncChannelFactory) {
    this.config = Objects.requireNonNull(config, "config must not be null");
    this.paths = Objects.requireNonNull(paths, "paths must not be null");
    this.asyncChannelFactory =
        Objects.requireNonNull(asyncChannelFactory, "asyncChannelFactory must not be null");
    this.metricsCollector = new MetricsCollector(config.getMetricsPollInterval());
    this.ioExecutor = Executors.newFixedThreadPool(config.getThreads());

    this.bufferPool =
        ThreadLocal.withInitial(
            () ->
                config.isUseDirectBuffer()
                    ? ByteBuffer.allocateDirect(config.getBlockSizeBytes())
                    : ByteBuffer.allocate(config.getBlockSizeBytes()));

    Optional<Long> seedOpt = config.getRandomSeed();
    this.baseSeed = seedOpt.orElse(0L);
    this.useFixedSeed = seedOpt.isPresent();

    this.queueSemaphore = new Semaphore(config.getQueueDepth());
    this.systemInfo = captureSystemInfo();

    logger.info(
        "BenchmarkRunner initialized with {} threads, directBuffer={}, ioMode={}, queueDepth={}",
        config.getThreads(),
        config.isUseDirectBuffer(),
        config.getIoMode(),
        config.getQueueDepth());
  }

  private static SystemInfoSnapshot captureSystemInfo() {
    try {
      SystemInfo si = new SystemInfo();
      HardwareAbstractionLayer hal = si.getHardware();
      CentralProcessor cpu = hal.getProcessor();
      return new SystemInfoSnapshot(
          System.getProperty("os.name") + " " + System.getProperty("os.version"),
          System.getProperty("java.version"),
          cpu.getProcessorIdentifier().getName(),
          hal.getMemory().getTotal());
    } catch (Exception e) {
      logger.warn("Could not capture system info", e);
      return new SystemInfoSnapshot("unknown", "unknown", "unknown", 0L);
    }
  }

  /**
   * Returns the captured host information for this session.
   *
   * @return immutable system snapshot
   */
  public SystemInfoSnapshot getSystemInfo() {
    return systemInfo;
  }

  /**
   * Resolves the file position for a single block operation.
   *
   * <p>Sequential workloads advance with the running byte offset. Random workloads draw a block
   * aligned start position from {@code [0, fileSize - bytesToProcess]} using the supplied
   * thread-bound generator, so the draw is reproducible when the generator is seeded.
   *
   * @param random whether the workload is a random access pattern
   * @param rng thread-bound generator, consumed only for random workloads
   * @param fileSize total size of the test file in bytes
   * @param sequentialOffset running byte offset used by sequential workloads
   * @param bytesToProcess size of the block about to be transferred
   * @return absolute byte position for the operation
   */
  static long resolvePosition(
      boolean random, Random rng, long fileSize, long sequentialOffset, int bytesToProcess) {
    if (!random) {
      return sequentialOffset;
    }
    long maxPosition = Math.max(0L, fileSize - bytesToProcess);
    return rng.nextLong(maxPosition + 1);
  }

  /**
   * Returns the generator bound to a worker thread for a single run.
   *
   * <p>When a fixed seed is configured the generator is rebuilt from {@code baseSeed} mixed with
   * the thread id at the start of every run, so repeated runs replay an identical position
   * sequence. Without a fixed seed the generator is seeded from {@link ThreadLocalRandom}.
   *
   * @param threadId zero-based worker index
   * @return generator for this run
   */
  Random getThreadRng(int threadId) {
    if (!useFixedSeed) {
      return new Random(ThreadLocalRandom.current().nextLong());
    }
    return new Random(baseSeed ^ (threadId * THREAD_SEED_MIX));
  }

  @Override
  public List<BenchmarkResult> runAll() throws IOException, InterruptedException {
    registerShutdownHook();
    try {
      paths.ensureTestDirectory();
      long totalSpaceNeeded = config.getFileSizeBytes() * config.getThreads();
      paths.validateFreeSpace(totalSpaceNeeded);

      logger.info(
          "Starting benchmark: testTypes={}, fileSize={}, iterations={}, warmup={}, threads={}",
          config.getTestTypes(),
          config.getFileSizeBytes(),
          config.getIterations(),
          config.getWarmupIterations(),
          config.getThreads());

      int runId = 1;

      int warmup = config.getWarmupIterations();
      if (warmup > 0) {
        logger.info("Starting {} warmup iteration(s)", warmup);
        for (BenchmarkConfig.TestType type : config.getTestTypes()) {
          for (int i = 0; i < warmup; i++) {
            runMultiThreaded(-runId, type);
            runId++;
          }
        }
        results.clear();
        logger.info("Warmup completed, starting measured runs");
      }

      runId = 1;
      totalIterations = config.getIterations() * config.getTestTypes().size();
      if (config.getVerbosity() > 0 && System.console() != null) {
        startProgressReporting();
      }
      int iterationCount = 0;
      for (BenchmarkConfig.TestType type : config.getTestTypes()) {
        for (int i = 0; i < config.getIterations(); i++) {
          currentTestType = type;
          currentIteration = iterationCount + 1;
          progressBytesProcessed.set(0);
          List<BenchmarkResult> iterationResults = runMultiThreaded(runId++, type);
          results.addAll(iterationResults);
          iterationCount++;
        }
      }
      return Collections.unmodifiableList(results);
    } finally {
      stopProgressReporting();
      shutdownExecutors();
      // Remove the hook first so graceful completion cannot race a second cleanup. If the JVM is
      // already shutting down, removal throws and the hook becomes the single cleanup owner.
      unregisterShutdownHook();
      if (!config.isRetainTestFiles()) {
        cleanupSessionFilesOnce();
      }
    }
  }

  private void startProgressReporting() {
    progressExecutor =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "progress-reporter");
              t.setDaemon(true);
              return t;
            });
    progressExecutor.scheduleAtFixedRate(this::printProgress, 1, 1, TimeUnit.SECONDS);
  }

  private void stopProgressReporting() {
    if (progressExecutor != null && !progressExecutor.isShutdown()) {
      progressExecutor.shutdownNow();
    }
    System.out.println();
  }

  private void printProgress() {
    long totalForIteration = (long) config.getTestTypes().size() * config.getIterations();
    double overallPercent = (double) currentIteration / totalForIteration * 100;
    long bytes = progressBytesProcessed.get();
    double elapsed = Duration.between(progressStartTime, Instant.now()).toMillis() / 1000.0;
    double throughputMbps = elapsed > 0 ? (bytes / (1024.0 * 1024.0)) / elapsed : 0;
    long etaSeconds =
        throughputMbps > 0
            ? (long)
                ((config.getFileSizeBytes() * config.getThreads()
                        * (totalForIteration - currentIteration + 1)
                        - bytes)
                    / (throughputMbps * 1024 * 1024))
            : 0;

    int barLen = 20;
    int filled = Math.min(barLen, Math.max(0, (int) (overallPercent / 100 * barLen)));
    String progressBar = "=".repeat(filled) + " ".repeat(barLen - filled);

    System.out.printf(
        "\r[%s] %3.0f%% | %s | %8.0f MB/s | ETA: %2dm %02ds | Iteration %d/%d",
        progressBar,
        overallPercent,
        currentTestType,
        throughputMbps,
        etaSeconds / 60,
        etaSeconds % 60,
        currentIteration,
        totalIterations);
  }

  private List<BenchmarkResult> runMultiThreaded(int runId, BenchmarkConfig.TestType type)
      throws InterruptedException {

    CountDownLatch latch = new CountDownLatch(config.getThreads());
    List<Future<BenchmarkResult>> futures = new ArrayList<>();

    for (int threadId = 0; threadId < config.getThreads(); threadId++) {
      final int tid = threadId;
      final int currentRunId = runId;
      Future<BenchmarkResult> future =
          ioExecutor.submit(
              () -> {
                try {
                  Path threadFile =
                      paths.testFilePath(
                          currentRunId, type.name().toLowerCase(Locale.ROOT) + "-t" + tid);
                  if (config.getIoMode() == BenchmarkConfig.IoMode.ASYNC) {
                    return runSingleThreadAsync(threadFile, currentRunId, type, tid);
                  } else {
                    return runSingleThreadSync(threadFile, currentRunId, type, tid);
                  }
                } finally {
                  latch.countDown();
                }
              });
      futures.add(future);
    }

    if (!latch.await(config.getMaxPerTestTarget().toMinutes(), TimeUnit.MINUTES)) {
      logger.error("Benchmark timeout - forcing shutdown");
      ioExecutor.shutdownNow();
      throw new InterruptedException("Benchmark exceeded time limit");
    }

    List<BenchmarkResult> iterationResults = new ArrayList<>();
    for (Future<BenchmarkResult> f : futures) {
      try {
        iterationResults.add(f.get());
      } catch (ExecutionException e) {
        throw new RuntimeException("Thread execution failed", e.getCause());
      }
    }
    return iterationResults;
  }

  private BenchmarkResult runSingleThreadSync(
      Path file, int runId, BenchmarkConfig.TestType type, int threadId) throws IOException {

    long fileSize = config.getFileSizeBytes();
    int blockSize = config.getBlockSizeBytes();
    boolean isWrite =
        type == BenchmarkConfig.TestType.SEQ_WRITE || type == BenchmarkConfig.TestType.RAND_WRITE;
    boolean isRandom =
        type == BenchmarkConfig.TestType.RAND_READ || type == BenchmarkConfig.TestType.RAND_WRITE;
    boolean isMixed = type == BenchmarkConfig.TestType.MIXED_RW;
    boolean isReadOnly = !isWrite && !isMixed;
    int readThreshold = (int) (config.getMixedReadPercent() * 0.01 * Integer.MAX_VALUE);

    ByteBuffer buffer = bufferPool.get();
    Random rng = getThreadRng(threadId);
    Histogram histogram = new Histogram(3600000000000L, 3);
    Instant start = Instant.now();
    long totalOps = 0;
    long readOps = 0;
    long writeOps = 0;
    long bytesProcessed = 0;
    long bytesRead = 0;
    long bytesWritten = 0;

    try (FileChannel channel =
        FileChannel.open(
            file,
            StandardOpenOption.CREATE,
            StandardOpenOption.READ,
            StandardOpenOption.WRITE)) {

      if (config.isPreallocateFiles() && (isWrite || isMixed)) {
        channel.truncate(fileSize);
      }

      if (isMixed) {
        while (bytesProcessed < fileSize && !shutdownRequested.get()) {
          int bytesToProcess = (int) Math.min(blockSize, fileSize - bytesProcessed);

          queueSemaphore.acquireUninterruptibly();
          try {
            boolean doRead = rng.nextInt(Integer.MAX_VALUE) < readThreshold;
            long position = resolvePosition(true, rng, fileSize, bytesProcessed, bytesToProcess);
            channel.position(position);

            long startNs = System.nanoTime();

            if (doRead) {
              buffer.clear();
              buffer.limit(bytesToProcess);
              int read = channel.read(buffer);
              if (read == -1) {
                break;
              }
              bytesRead += read;
              readOps++;
            } else {
              byte[] temp = new byte[bytesToProcess];
              rng.nextBytes(temp);
              buffer.clear();
              buffer.put(temp);
              buffer.flip();
              int written = channel.write(buffer);
              bytesWritten += written;
              writeOps++;
            }

            long endNs = System.nanoTime();
            histogram.recordValue(Math.max(1L, endNs - startNs));
            bytesProcessed += bytesToProcess;
            totalOps++;

            if (config.isForceSync()
                && config.getSyncEveryNBlocks() > 0
                && totalOps % config.getSyncEveryNBlocks() == 0) {
              channel.force(false);
            }
          } finally {
            queueSemaphore.release();
          }
          progressBytesProcessed.addAndGet(bytesToProcess);
        }
      } else {
        if (isReadOnly) {
          channel.truncate(fileSize);
          long prefillWritten = 0;
          while (prefillWritten < fileSize && !shutdownRequested.get()) {
            int toWrite = (int) Math.min(blockSize, fileSize - prefillWritten);
            byte[] temp = new byte[toWrite];
            rng.nextBytes(temp);
            buffer.clear();
            buffer.put(temp);
            buffer.flip();
            prefillWritten += channel.write(buffer);
          }
          channel.position(0);
          start = Instant.now();
        }

        while (bytesProcessed < fileSize && !shutdownRequested.get()) {
          int bytesToProcess = (int) Math.min(blockSize, fileSize - bytesProcessed);

          queueSemaphore.acquireUninterruptibly();
          try {
            long position =
                resolvePosition(isRandom, rng, fileSize, bytesProcessed, bytesToProcess);
            channel.position(position);

            long startNs = System.nanoTime();

            if (isWrite) {
              byte[] temp = new byte[bytesToProcess];
              rng.nextBytes(temp);
              buffer.clear();
              buffer.put(temp);
              buffer.flip();
              int written = channel.write(buffer);
              bytesWritten += written;
              bytesProcessed += written;
              writeOps++;
            } else {
              buffer.clear();
              buffer.limit(bytesToProcess);
              int read = channel.read(buffer);
              if (read == -1) {
                break;
              }
              bytesRead += read;
              bytesProcessed += read;
              readOps++;
            }

            long endNs = System.nanoTime();
            histogram.recordValue(Math.max(1L, endNs - startNs));
            totalOps++;

            if (config.isForceSync()
                && config.getSyncEveryNBlocks() > 0
                && totalOps % config.getSyncEveryNBlocks() == 0) {
              channel.force(false);
            }
          } finally {
            queueSemaphore.release();
          }
          progressBytesProcessed.addAndGet(bytesToProcess);
        }
      }

      if (config.isForceSync() && config.getSyncEveryNBlocks() == 0) {
        channel.force(true);
      }
    }

    Instant end = Instant.now();
    return calculateResult(
        runId, threadId, type, bytesProcessed, bytesRead, bytesWritten, readOps, writeOps, start, end, totalOps,
        histogram);
  }

  private BenchmarkResult runSingleThreadAsync(
      Path file, int runId, BenchmarkConfig.TestType type, int threadId) throws IOException {

    long fileSize = config.getFileSizeBytes();
    int blockSize = config.getBlockSizeBytes();
    boolean isWrite =
        type == BenchmarkConfig.TestType.SEQ_WRITE || type == BenchmarkConfig.TestType.RAND_WRITE;
    boolean isMixed = type == BenchmarkConfig.TestType.MIXED_RW;
    boolean isRandom =
        type == BenchmarkConfig.TestType.RAND_READ
            || type == BenchmarkConfig.TestType.RAND_WRITE
            || isMixed;
    boolean isReadOnly = !isWrite && !isMixed;
    int readThreshold = (int) (config.getMixedReadPercent() * 0.01 * Integer.MAX_VALUE);

    Random rng = getThreadRng(threadId);
    Histogram histogram = new Histogram(3600000000000L, 3);
    AtomicLong bytesReadAcc = new AtomicLong();
    AtomicLong bytesWrittenAcc = new AtomicLong();
    AtomicLong readOpsAcc = new AtomicLong();
    AtomicLong writeOpsAcc = new AtomicLong();
    AtomicLong opsAcc = new AtomicLong();

    try (AsynchronousFileChannel channel = asyncChannelFactory.open(file)) {

      if (config.isPreallocateFiles() && (isWrite || isMixed)) {
        truncateFile(file, fileSize);
      }

      if (isReadOnly) {
        prefillFile(file, fileSize, blockSize, rng);
      }

      // One phaser party belongs to the dispatcher. Every submitted operation registers its own
      // party, so awaiting the phase transition blocks only until the whole submitted batch is
      // quiescent - never per individual operation.
      final Instant start = Instant.now();
      Phaser completion = new Phaser(1);
      AtomicReference<Throwable> failure = new AtomicReference<>();
      BlockingQueue<ByteBuffer> freeBuffers = new ArrayBlockingQueue<>(config.getQueueDepth());

      long offset = 0;
      while (offset < fileSize) {
        if (shutdownRequested.get() || failure.get() != null) {
          break;
        }

        // Backpressure only: when a slot is free this returns immediately. The dispatcher keeps
        // queueDepth operations in flight instead of waiting for each completion.
        try {
          queueSemaphore.acquire();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          failure.compareAndSet(null, e);
          break;
        }

        if (failure.get() != null) {
          queueSemaphore.release();
          break;
        }

        int bytesToProcess = (int) Math.min(blockSize, fileSize - offset);
        long position = resolvePosition(isRandom, rng, fileSize, offset, bytesToProcess);
        boolean doWrite = isWrite || (isMixed && rng.nextInt(Integer.MAX_VALUE) >= readThreshold);

        // Each in-flight operation owns its buffer: a shared rotating buffer would be overwritten
        // while the channel still reads from or writes into it.
        ByteBuffer buffer = freeBuffers.poll();
        if (buffer == null) {
          buffer = allocateBuffer(blockSize);
        }
        buffer.clear();
        buffer.limit(bytesToProcess);
        if (doWrite) {
          byte[] payload = new byte[bytesToProcess];
          rng.nextBytes(payload);
          buffer.put(payload);
          buffer.flip();
        }

        completion.register();
        AsyncOpHandler handler =
            new AsyncOpHandler(
                channel,
                completion,
                queueSemaphore,
                histogram,
                freeBuffers,
                failure,
                buffer,
                position,
                doWrite,
                bytesReadAcc,
                bytesWrittenAcc,
                readOpsAcc,
                writeOpsAcc,
                opsAcc,
                progressBytesProcessed,
                System.nanoTime());

        try {
          if (doWrite) {
            channel.write(buffer, position, null, handler);
          } else {
            channel.read(buffer, position, null, handler);
          }
        } catch (RuntimeException e) {
          // Dispatch failed before the channel accepted the operation. The handler undoes exactly
          // the bookkeeping it owns (permit, phaser party, buffer) and records the failure.
          handler.abort(e);
          break;
        }

        offset += bytesToProcess;
      }

      // Drop the dispatcher's own party, then block only until every submitted op has completed.
      int phase = completion.arriveAndDeregister();
      awaitCompletion(completion, phase, failure);

      Throwable error = failure.get();
      if (error != null) {
        throw new IOException("Async I/O operation failed", error);
      }

      // Count bytes that actually moved. Short reads/writes are retried inside the handler, so a
      // successful run always accounts for the full file size.
      long bytesProcessed = bytesReadAcc.get() + bytesWrittenAcc.get();
      Instant end = Instant.now();
      return calculateResult(
          runId,
          threadId,
          type,
          bytesProcessed,
          bytesReadAcc.get(),
          bytesWrittenAcc.get(),
          readOpsAcc.get(),
          writeOpsAcc.get(),
          start,
          end,
          opsAcc.get(),
          histogram);
    }
  }

  /**
   * Blocks until every operation submitted to the run has completed.
   *
   * <p>Interrupts are recorded as a run failure but do not abandon in-flight operations, so the
   * queue permits and phaser parties are always reclaimed before the thread exits.
   *
   * @param completion phaser that tracks the dispatcher and every submitted operation
   * @param phase dispatcher phase to await
   * @param failure sink that receives an interrupt so the caller can fail the run
   */
  private static void awaitCompletion(
      Phaser completion, int phase, AtomicReference<Throwable> failure) {
    boolean interrupted = false;
    while (completion.getPhase() == phase) {
      try {
        completion.awaitAdvanceInterruptibly(phase);
      } catch (InterruptedException e) {
        interrupted = true;
        failure.compareAndSet(null, e);
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  private ByteBuffer allocateBuffer(int capacity) {
    return config.isUseDirectBuffer()
        ? ByteBuffer.allocateDirect(capacity)
        : ByteBuffer.allocate(capacity);
  }

  /**
   * Returns a buffer to the free pool, tolerating a full pool.
   *
   * <p>A full pool simply means enough buffers are already cached; dropping the surplus buffer is
   * correct and lets it be collected.
   *
   * @param freeBuffers pool to return to
   * @param buffer buffer to return
   */
  private static void returnBuffer(BlockingQueue<ByteBuffer> freeBuffers, ByteBuffer buffer) {
    if (!freeBuffers.offer(buffer)) {
      logger.trace("Buffer pool full, discarding surplus buffer");
    }
  }

  /**
   * Completion callback for one logical asynchronous operation.
   *
   * <p>Records latency, publishes progress, returns the buffer to the free pool, releases the queue
   * slot, and deregisters from the run phaser. Short reads and writes are re-dispatched for the
   * remaining bytes while retaining the same permit and phaser party. A single atomic latch
   * guarantees that every terminal path releases exactly one semaphore permit and one phaser party,
   * so neither a failure nor a duplicate callback can leak or double-release resources.
   */
  private static final class AsyncOpHandler implements CompletionHandler<Integer, Void> {

    private final AsynchronousFileChannel channel;
    private final Phaser completion;
    private final Semaphore queueSemaphore;
    private final Histogram histogram;
    private final BlockingQueue<ByteBuffer> freeBuffers;
    private final AtomicReference<Throwable> failure;
    private final ByteBuffer buffer;
    private final boolean write;
    private final AtomicLong bytesRead;
    private final AtomicLong bytesWritten;
    private final AtomicLong readOps;
    private final AtomicLong writeOps;
    private final AtomicLong ops;
    private final AtomicLong progress;
    private final long startNs;
    private final AtomicBoolean finished = new AtomicBoolean();

    private long position;
    private int remaining;

    AsyncOpHandler(
        AsynchronousFileChannel channel,
        Phaser completion,
        Semaphore queueSemaphore,
        Histogram histogram,
        BlockingQueue<ByteBuffer> freeBuffers,
        AtomicReference<Throwable> failure,
        ByteBuffer buffer,
        long position,
        boolean write,
        AtomicLong bytesRead,
        AtomicLong bytesWritten,
        AtomicLong readOps,
        AtomicLong writeOps,
        AtomicLong ops,
        AtomicLong progress,
        long startNs) {
      this.channel = channel;
      this.completion = completion;
      this.queueSemaphore = queueSemaphore;
      this.histogram = histogram;
      this.freeBuffers = freeBuffers;
      this.failure = failure;
      this.buffer = buffer;
      this.position = position;
      this.remaining = buffer.remaining();
      this.write = write;
      this.bytesRead = bytesRead;
      this.bytesWritten = bytesWritten;
      this.readOps = readOps;
      this.writeOps = writeOps;
      this.ops = ops;
      this.progress = progress;
      this.startNs = startNs;
    }

    @Override
    public synchronized void completed(Integer count, Void attachment) {
      if (finished.get()) {
        return;
      }
      try {
        handleCompletion(count);
      } catch (Throwable t) {
        fail(t);
      }
    }

    @Override
    public synchronized void failed(Throwable exc, Void attachment) {
      fail(exc != null ? exc : new IOException("unknown async failure"));
    }

    /**
     * Cancels an operation whose dispatch call itself threw before completion was delivered.
     *
     * @param exc dispatch failure
     */
    synchronized void abort(Throwable exc) {
      failure.compareAndSet(null, exc != null ? exc : new IOException("async dispatch failed"));
      finish();
    }

    private void handleCompletion(Integer count) {
      int moved = count == null ? 0 : count;
      if (moved <= 0) {
        fail(new IOException("Async I/O made no progress at position " + position));
        return;
      }

      int consumed = Math.min(moved, remaining);
      if (write) {
        bytesWritten.addAndGet(consumed);
      } else {
        bytesRead.addAndGet(consumed);
      }
      progress.addAndGet(consumed);
      position += consumed;
      remaining -= consumed;

      if (remaining > 0) {
        // Retry the tail at the advanced position. The queue slot and phaser party stay checked
        // out, so the retry cannot exceed queueDepth or be lost from the completion barrier.
        if (write) {
          channel.write(buffer, position, null, this);
        } else {
          channel.read(buffer, position, null, this);
        }
        return;
      }

      long latencyNs = Math.max(1L, System.nanoTime() - startNs);
      synchronized (histogram) {
        histogram.recordValue(latencyNs);
      }
      ops.incrementAndGet();
      if (write) {
        writeOps.incrementAndGet();
      } else {
        readOps.incrementAndGet();
      }
      finish();
    }

    private void fail(Throwable exc) {
      failure.compareAndSet(null, exc);
      finish();
    }

    private void finish() {
      if (finished.compareAndSet(false, true)) {
        returnBuffer(freeBuffers, buffer);
        queueSemaphore.release();
        completion.arriveAndDeregister();
      }
    }
  }

  private static void truncateFile(Path file, long fileSize) throws IOException {
    try (FileChannel fc =
        FileChannel.open(
            file,
            StandardOpenOption.CREATE,
            StandardOpenOption.READ,
            StandardOpenOption.WRITE)) {
      fc.truncate(fileSize);
    }
  }

  private static void prefillFile(Path file, long fileSize, int blockSize, Random rng)
      throws IOException {
    try (FileChannel fc =
        FileChannel.open(
            file,
            StandardOpenOption.CREATE,
            StandardOpenOption.READ,
            StandardOpenOption.WRITE)) {
      fc.truncate(fileSize);
      long prefillWritten = 0;
      ByteBuffer fillBuffer = ByteBuffer.allocate(blockSize);
      while (prefillWritten < fileSize) {
        int toWrite = (int) Math.min(blockSize, fileSize - prefillWritten);
        byte[] temp = new byte[toWrite];
        rng.nextBytes(temp);
        fillBuffer.clear();
        fillBuffer.put(temp);
        fillBuffer.flip();
        prefillWritten += fc.write(fillBuffer);
      }
    }
  }

  private BenchmarkResult calculateResult(
      int runId,
      int threadId,
      BenchmarkConfig.TestType type,
      long bytesProcessed,
      long bytesRead,
      long bytesWritten,
      long readOps,
      long writeOps,
      Instant start,
      Instant end,
      long totalOps,
      Histogram histogram) {

    Duration elapsed = Duration.between(start, end);

    double p50 = histogram.getValueAtPercentile(50.0);
    double p95 = histogram.getValueAtPercentile(95.0);
    double p99 = histogram.getValueAtPercentile(99.0);
    double p999 = histogram.getValueAtPercentile(99.9);
    long maxLatencyNs = histogram.getMaxValue();

    String rid = String.format("run-%03d-thread-%02d", runId, threadId);
    String typeName = type != null ? type.name() : "";
    BenchmarkResult result =
        BenchmarkResult.fromCounts(
            rid,
            typeName,
            bytesRead,
            bytesWritten,
            readOps,
            writeOps,
            elapsed,
            end,
            p50,
            p95,
            p99,
            p999,
            maxLatencyNs);

    double throughputMbps = result.throughputMBps();
    double avgLatencyMs = result.avgLatencyMs();

    logger.debug(
        "Run {} completed: type={}, throughput={} MB/s, latency={} ms, p99={} ns",
        rid,
        type,
        throughputMbps,
        avgLatencyMs,
        p99);
    return result;
  }

  @Override
  public void startMetricsPolling() {
    metricsCollector.start();
  }

  @Override
  public List<MetricsSnapshot> getMetricsLog() {
    return metricsCollector.getMetricsLog();
  }

  /**
   * Registers a JVM shutdown hook that reclaims this session's payload on SIGINT/SIGTERM.
   *
   * <p>Package-private so tests can exercise lifecycle transitions without starting a full
   * benchmark. Registration is idempotent and tolerant of a JVM that is already shutting down.
   */
  void registerShutdownHook() {
    synchronized (cleanupLock) {
      if (shutdownHook != null) {
        return;
      }
      Thread hook =
          new Thread(this::handleJvmShutdown, "jstoragemark-shutdown-" + config.getSessionId());
      hook.setDaemon(false);
      try {
        Runtime.getRuntime().addShutdownHook(hook);
        shutdownHook = hook;
        logger.debug("Registered JVM shutdown hook for session {}", config.getSessionId());
      } catch (IllegalStateException | SecurityException e) {
        logger.warn(
            "Could not register JVM shutdown hook; interrupted runs may leave session files behind",
            e);
      }
    }
  }

  /**
   * Removes the shutdown hook after a graceful run.
   *
   * <p>When the JVM is already shutting down removal is impossible; the hook then owns cleanup and
   * {@link #cleanupSessionFilesOnce()} still guarantees a single deletion pass.
   */
  void unregisterShutdownHook() {
    Thread hook;
    synchronized (cleanupLock) {
      hook = shutdownHook;
      shutdownHook = null;
    }
    if (hook == null) {
      return;
    }
    try {
      Runtime.getRuntime().removeShutdownHook(hook);
      logger.debug("Removed JVM shutdown hook for session {}", config.getSessionId());
    } catch (IllegalStateException e) {
      logger.debug("JVM shutdown already in progress; shutdown hook will reclaim session files");
    } catch (SecurityException e) {
      logger.warn("Not permitted to remove JVM shutdown hook", e);
    }
  }

  /**
   * Returns whether a shutdown hook is currently registered (used by lifecycle tests).
   *
   * @return true when this runner owns a registered hook
   */
  boolean isShutdownHookRegistered() {
    synchronized (cleanupLock) {
      return shutdownHook != null;
    }
  }

  /**
   * Shutdown-hook body: stop accepting new I/O, let workers release file handles, then delete the
   * session tree when the user did not ask to retain it.
   */
  void handleJvmShutdown() {
    if (config.isRetainTestFiles()) {
      logger.info(
          "JVM shutdown detected; retaining session files in {}", paths.getSessionDir());
      return;
    }
    logger.warn(
        "JVM shutdown detected; reclaiming session files in {}", paths.getSessionDir());
    shutdownRequested.set(true);
    abortExecutorsForShutdown();
    cleanupSessionFilesOnce();
  }

  /**
   * Stops workers quickly during JVM shutdown.
   *
   * <p>Unlike {@link #shutdownExecutors()}, this path favors speed over a long graceful drain
   * because the process is already terminating.
   */
  private void abortExecutorsForShutdown() {
    metricsCollector.stop();
    ioExecutor.shutdownNow();
    try {
      if (!ioExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        logger.warn("Worker threads still running during shutdown; cleanup will retry");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Deletes the session payload exactly once, retrying briefly for files that are still closing.
   *
   * <p>Retries matter on Windows, where an open handle prevents deletion until the worker thread
   * has fully unwound. Reports live outside the session directory and are never removed here.
   */
  private void cleanupSessionFilesOnce() {
    synchronized (cleanupLock) {
      if (sessionCleanupDone) {
        return;
      }
      // If another thread (graceful completion or the hook) already owns cleanup, wait for it.
      // Object.wait releases the lock, so no sleeping happens while holding it.
      while (sessionCleanupInProgress) {
        try {
          cleanupLock.wait(TimeUnit.SECONDS.toMillis(1));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
        if (sessionCleanupDone) {
          return;
        }
      }
      if (sessionCleanupDone) {
        return;
      }
      sessionCleanupInProgress = true;
    }

    try {
      for (int attempt = 0; attempt < CLEANUP_ATTEMPTS; attempt++) {
        paths.cleanupSessionFiles(false);
        if (Files.notExists(paths.getSessionDir())) {
          break;
        }
        if (attempt + 1 < CLEANUP_ATTEMPTS) {
          try {
            Thread.sleep(CLEANUP_RETRY_MILLIS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
          }
        }
      }
      if (Files.exists(paths.getSessionDir())) {
        logger.warn(
            "Session directory still present after cleanup attempts: {}", paths.getSessionDir());
      }
    } finally {
      synchronized (cleanupLock) {
        sessionCleanupDone = true;
        sessionCleanupInProgress = false;
        cleanupLock.notifyAll();
      }
    }
  }

  private void shutdownExecutors() {
    logger.info("Shutting down executors");
    metricsCollector.stop();
    ioExecutor.shutdown();

    try {
      if (!ioExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
        logger.warn("IO executor tasks did not complete within timeout, forcing shutdown");
        ioExecutor.shutdownNow();
        if (!ioExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
          logger.error("IO executor did not terminate after forced shutdown");
        }
      }
    } catch (InterruptedException e) {
      logger.error("Interrupted while waiting for executor shutdown", e);
      Thread.currentThread().interrupt();
    }
  }
}
