package com.kira.jstoragemark.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link AsynchronousFileChannel} that completes operations out of band on a separate executor and
 * records how many operations were in flight at once.
 *
 * <p>Completions are deliberately deferred rather than invoked inline: an inline completion would
 * cap observed concurrency at one and hide whether the runner actually pipelines requests.
 */
final class ConcurrencyTrackingAsyncFileChannel extends AsynchronousFileChannel {

  private final long fileSize;
  private final ExecutorService completer;
  private final long delayMillis;
  private final int failOnOperation;
  private final int maxChunkBytes;

  private final AtomicInteger inFlight = new AtomicInteger();
  private final AtomicInteger maxInFlight = new AtomicInteger();
  private final AtomicInteger operationCount = new AtomicInteger();
  private final List<Long> positions = Collections.synchronizedList(new ArrayList<>());
  private volatile boolean open = true;

  /**
   * Creates a tracking channel.
   *
   * @param fileSize reported size of the backing file
   * @param completer executor that delivers completions
   * @param delayMillis artificial per-operation latency
   * @param failOnOperation 1-based operation index to fail, or 0 to never fail
   */
  ConcurrencyTrackingAsyncFileChannel(
      long fileSize, ExecutorService completer, long delayMillis, int failOnOperation) {
    this(fileSize, completer, delayMillis, failOnOperation, 0);
  }

  /**
   * Creates a tracking channel that may also deliver short completions.
   *
   * @param maxChunkBytes maximum bytes acknowledged per completion, or 0 for no limit
   */
  ConcurrencyTrackingAsyncFileChannel(
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

  int maxInFlight() {
    return maxInFlight.get();
  }

  int operationCount() {
    return operationCount.get();
  }

  List<Long> positions() {
    synchronized (positions) {
      return new ArrayList<>(positions);
    }
  }

  @Override
  public long size() {
    return fileSize;
  }

  @Override
  public AsynchronousFileChannel truncate(long size) {
    return this;
  }

  @Override
  public void force(boolean metaData) {
    // Nothing is persisted.
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public void close() {
    open = false;
  }

  @Override
  public <A> void read(
      ByteBuffer dst, long position, A attachment, CompletionHandler<Integer, ? super A> handler) {
    dispatch(dst, position, attachment, handler, false);
  }

  @Override
  public <A> void write(
      ByteBuffer src, long position, A attachment, CompletionHandler<Integer, ? super A> handler) {
    dispatch(src, position, attachment, handler, true);
  }

  private <A> void dispatch(
      ByteBuffer buffer,
      long position,
      A attachment,
      CompletionHandler<Integer, ? super A> handler,
      boolean write) {

    if (!open) {
      handler.failed(new IOException("channel closed"), attachment);
      return;
    }

    int operationIndex = operationCount.incrementAndGet();
    positions.add(position);
    int concurrent = inFlight.incrementAndGet();
    maxInFlight.accumulateAndGet(concurrent, Math::max);

    completer.execute(
        () -> {
          try {
            if (delayMillis > 0) {
              Thread.sleep(delayMillis);
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            inFlight.decrementAndGet();
          }

          if (failOnOperation > 0 && operationIndex == failOnOperation) {
            handler.failed(new IOException("injected failure at operation " + operationIndex),
                attachment);
            return;
          }

          int count = buffer.remaining();
          if (maxChunkBytes > 0) {
            count = Math.min(count, maxChunkBytes);
          }
          if (!write) {
            for (int i = 0; i < count; i++) {
              buffer.put((byte) 0x5A);
            }
          } else {
            buffer.position(buffer.position() + count);
          }
          handler.completed(count, attachment);
        });
  }

  @Override
  public Future<Integer> read(ByteBuffer dst, long position) {
    throw new UnsupportedOperationException("not used by BenchmarkRunner");
  }

  @Override
  public Future<Integer> write(ByteBuffer src, long position) {
    throw new UnsupportedOperationException("not used by BenchmarkRunner");
  }

  @Override
  public FileLock tryLock(long position, long size, boolean shared) {
    throw new UnsupportedOperationException("not used by BenchmarkRunner");
  }

  @Override
  public <A> void lock(
      long position,
      long size,
      boolean shared,
      A attachment,
      CompletionHandler<FileLock, ? super A> handler) {
    handler.failed(new UnsupportedOperationException("not used by BenchmarkRunner"), attachment);
  }

  @Override
  public Future<FileLock> lock(long position, long size, boolean shared) {
    throw new UnsupportedOperationException("not used by BenchmarkRunner");
  }
}
