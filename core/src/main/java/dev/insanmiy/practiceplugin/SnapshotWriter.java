package dev.insanmiy.practiceplugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

final class SnapshotWriter implements AutoCloseable {
  @FunctionalInterface
  interface Output {
    void write(Path file, String snapshot) throws IOException;
  }

  private final Map<Path, String> pending = new LinkedHashMap<>();
  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
    Thread thread = new Thread(task, "Practice-Saves");
    thread.setDaemon(true);
    return thread;
  });
  private final ScheduledFuture<?> periodic;
  private final BiConsumer<Path, IOException> failures;
  private final Output output;
  private boolean closed;

  SnapshotWriter(BiConsumer<Path, IOException> failures) {
    this(failures, YamlFiles::write);
  }

  SnapshotWriter(BiConsumer<Path, IOException> failures, Output output) {
    this.failures = Objects.requireNonNull(failures);
    this.output = Objects.requireNonNull(output);
    periodic = executor.scheduleWithFixedDelay(() -> drain(true), 1, 1, TimeUnit.SECONDS);
  }

  synchronized void submit(Path file, String snapshot) throws IOException {
    if (closed) throw new IOException("Practice Save Writer Is Closed");
    pending.put(file.toAbsolutePath().normalize(), Objects.requireNonNull(snapshot));
  }

  private IOException drain(boolean report) {
    Map<Path, String> batch;
    synchronized (this) {
      batch = new LinkedHashMap<>(pending);
      pending.clear();
    }
    IOException result = null;
    for (var entry : batch.entrySet()) {
      try {
        output.write(entry.getKey(), entry.getValue());
      } catch (IOException failure) {
        synchronized (this) {
          pending.putIfAbsent(entry.getKey(), entry.getValue());
        }
        if (report) failures.accept(entry.getKey(), failure);
        if (result == null) result = new IOException("Could Not Flush Practice Saves");
        result.addSuppressed(failure);
      }
    }
    return result;
  }

  void flush() throws IOException {
    try {
      IOException failure = executor.submit(() -> drain(false)).get(30, TimeUnit.SECONDS);
      if (failure != null) throw failure;
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted While Flushing Practice Saves", failure);
    } catch (ExecutionException | TimeoutException failure) {
      throw new IOException("Could Not Finish Flushing Practice Saves", failure);
    }
  }

  @Override
  public void close() throws IOException {
    synchronized (this) {
      if (closed) return;
      closed = true;
    }
    periodic.cancel(false);
    try {
      flush();
    } finally {
      executor.shutdown();
    }
  }
}
