package dev.insanmiy.practiceplugin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.insanmiy.practiceplugin.model.KitLayout;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KitLayoutStoreTest {
  @Test
  void removedSlotMappingDoesNotReturnAfterReload(@TempDir Path tempDir) {
    Path file = tempDir.resolve("layouts.yml");
    UUID player = UUID.randomUUID();
    KitLayoutStore store = new KitLayoutStore(file);
    store.saveLayout(player, "hard", "testkit", new KitLayout(Map.of(0, 0, 1, 1), 0));
    KitLayout updated = new KitLayout(Map.of(1, 0), 0);
    store.saveLayout(player, "hard", "testkit", updated);

    KitLayoutStore reloaded = new KitLayoutStore(file);
    assertEquals(updated, reloaded.getLayout(player, "hard", "testkit"));
    assertEquals(updated, reloaded.getLayout(player, "other", "testkit"));
  }
}
