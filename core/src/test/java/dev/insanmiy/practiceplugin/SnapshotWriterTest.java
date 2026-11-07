package dev.insanmiy.practiceplugin;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotWriterTest {
  @TempDir Path folder;

  @Test
  void closePersistsLatestSnapshotForEveryFileAndRejectsLateWrites() throws IOException {
    Path settings = folder.resolve("settings.yml");
    Path stats = folder.resolve("stats.yml");
    SnapshotWriter writer = new SnapshotWriter((file, failure) -> {});
    writer.submit(settings, "Value: 1\n");
    writer.submit(stats, "Hits: 4\n");
    writer.submit(settings, "Value: 2\n");
    writer.close();
    assertEquals("Value: 2\n", Files.readString(settings));
    assertEquals("Hits: 4\n", Files.readString(stats));
    assertThrows(IOException.class, () -> writer.submit(settings, "Late"));
  }

  @Test
  void failedSavesAreRetriedAndCannotOverwriteNewerSnapshots() throws IOException {
    Path parent = folder.resolve("blocked");
    Files.writeString(parent, "Blocked");
    Path file = parent.resolve("settings.yml");
    try (SnapshotWriter writer = new SnapshotWriter((path, failure) -> {})) {
      writer.submit(file, "Value: 1\n");
      assertThrows(IOException.class, writer::flush);
      writer.submit(file, "Value: 2\n");
      Files.delete(parent);
      writer.flush();
      assertEquals("Value: 2\n", Files.readString(file));
    }
  }

  @Test
  void gameplayCanQueueANewerSnapshotWhileAnOlderWriteIsBlocked()
      throws IOException, InterruptedException, ExecutionException, TimeoutException {
    Path file = folder.resolve("settings.yml");
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger attempts = new AtomicInteger();
    Thread caller = Thread.currentThread();
    try (SnapshotWriter writer = new SnapshotWriter((path, failure) -> {}, (path, snapshot) -> {
      assertNotSame(caller, Thread.currentThread());
      if (attempts.getAndIncrement() == 0) {
        started.countDown();
        try {
          if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Test Write Timed Out");
        } catch (InterruptedException failure) {
          Thread.currentThread().interrupt();
          throw new IOException(failure);
        }
        throw new IOException("First Write Failed");
      }
      YamlFiles.write(path, snapshot);
    })) {
      writer.submit(file, "Value: 1\n");
      CompletableFuture<Void> firstFlush = CompletableFuture.runAsync(() -> {
        try {
          writer.flush();
        } catch (IOException expected) {
          // A Later Flush Retries The Newest Pending Snapshot.
        }
      });
      try {
        assertTrue(started.await(5, TimeUnit.SECONDS));
        writer.submit(file, "Value: 2\n");
      } finally {
        release.countDown();
      }
      firstFlush.get(5, TimeUnit.SECONDS);
      writer.flush();
      assertEquals("Value: 2\n", Files.readString(file));
    }
  }
}
