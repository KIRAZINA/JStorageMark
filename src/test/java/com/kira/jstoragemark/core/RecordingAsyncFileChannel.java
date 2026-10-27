package com.kira.jstoragemark.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

/**
 * In-memory {@link AsynchronousFileChannel} that records the absolute file position of every read
 * and write it is handed.
 *
 * <p>Used by tests to prove that random workloads pass real, block-aligned random offsets to the
 * asynchronous channel instead of relying on sequential offsets or implicit channel sizing.
 */
final class RecordingAsyncFileChannel extends AsynchronousFileChannel {

  private final long fileSize;
  private final List<Long> readPositions = Collections.synchronizedList(new ArrayList<>());
  private final List<Long> writePositions = Collections.synchronizedList(new ArrayList<>());
  private volatile boolean open = true;

  RecordingAsyncFileChannel(long fileSize) {
    this.fileSize = fileSize;
  }

  List<Long> readPositions() {
    synchronized (readPositions) {
      return new ArrayList<>(readPositions);
    }
  }

  List<Long> writePositions() {
    synchronized (writePositions) {
      return new ArrayList<>(writePositions);
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
    // No-op: nothing is persisted.
  }

  @Override
  public <A> void read(
      ByteBuffer dst, long position, A attachment, CompletionHandler<Integer, ? super A> handler) {
    if (!open) {
      handler.failed(new IOException("channel closed"), attachment);
      return;
    }
    readPositions.add(position);
    int count = dst.remaining();
    for (int i = 0; i < count; i++) {
      dst.put((byte) 0x5A);
    }
    handler.completed(count, attachment);
  }

  @Override
  public <A> void write(
      ByteBuffer src, long position, A attachment, CompletionHandler<Integer, ? super A> handler) {
    if (!open) {
      handler.failed(new IOException("channel closed"), attachment);
      return;
    }
    writePositions.add(position);
    int count = src.remaining();
    src.position(src.position() + count);
    handler.completed(count, attachment);
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
  public java.nio.channels.FileLock tryLock(long position, long size, boolean shared) {
    throw new UnsupportedOperationException("not used by BenchmarkRunner");
  }

  @Override
  public <A> void lock(
      long position,
      long size,
      boolean shared,
      A attachment,
      CompletionHandler<java.nio.channels.FileLock, ? super A> handler) {
    handler.failed(new UnsupportedOperationException("not used by BenchmarkRunner"), attachment);
  }

  @Override
  public Future<java.nio.channels.FileLock> lock(long position, long size, boolean shared) {
    throw new UnsupportedOperationException("not used by BenchmarkRunner");
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public void close() {
    open = false;
  }
}
