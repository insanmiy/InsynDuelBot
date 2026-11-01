package dev.insanmiy.practiceplugin;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CustomKitStoreTest {

  @Test
  void testSaveAndDeleteKit(@TempDir Path tempDir) throws IOException {
    Path file = tempDir.resolve("customkits.yml");
    CustomKitStore store = new CustomKitStore(file);

    UUID player = UUID.randomUUID();
    YamlConfiguration candidate = new YamlConfiguration();
    candidate.set("description", "Test kit");
    candidate.set("health", 20.0);

    // Initial save
    store.save("testkit", candidate, null, player, false, Set.of());

    YamlConfiguration loaded = store.read();
    assertNotNull(loaded.getConfigurationSection("testkit"));
    assertEquals(player.toString(), loaded.getString("testkit.owner"));

    // Overwrite existing kit
    String revision = CustomKitStore.revision(loaded.getConfigurationSection("testkit"));
    candidate.set("health", 40.0);
    store.save("testkit", candidate, revision, player, false, Set.of());
    assertEquals(40.0, store.read().getDouble("testkit.health"));

    // Non-owner cannot delete
    UUID stranger = UUID.randomUUID();
    assertThrows(IllegalArgumentException.class, () -> store.delete("testkit", null, stranger, false));

    // Admin CAN delete
    store.delete("testkit", null, stranger, true);
    assertNull(store.read().getConfigurationSection("testkit"));
  }

  @Test
  void testUnownedKitEditableAndDeletable(@TempDir Path tempDir) throws IOException {
    Path file = tempDir.resolve("customkits.yml");
    CustomKitStore store = new CustomKitStore(file);

    YamlConfiguration yaml = new YamlConfiguration();
    yaml.createSection("legacykit");
    yaml.set("legacykit.description", "No owner kit");
    YamlFiles.write(file, yaml);

    UUID player = UUID.randomUUID();
    assertTrue(CustomKitStore.editable(store.read().getConfigurationSection("legacykit"), player, false));

    store.delete("legacykit", null, player, false);
    assertNull(store.read().getConfigurationSection("legacykit"));
  }

  @Test
  void staleEditorCannotOverwriteOrDeleteChangedKit(@TempDir Path tempDir) throws IOException {
    CustomKitStore store = new CustomKitStore(tempDir.resolve("customkits.yml"));
    UUID player = UUID.randomUUID();
    YamlConfiguration candidate = new YamlConfiguration();
    candidate.set("health", 20.0);
    store.save("testkit", candidate, null, player, false, Set.of());

    String staleRevision = CustomKitStore.revision(store.read().getConfigurationSection("testkit"));
    candidate.set("health", 40.0);
    store.save("testkit", candidate, staleRevision, player, false, Set.of());

    candidate.set("health", 60.0);
    assertThrows(IllegalArgumentException.class,
        () -> store.save("testkit", candidate, null, player, false, Set.of()));
    assertThrows(IllegalArgumentException.class,
        () -> store.save("testkit", candidate, staleRevision, player, false, Set.of()));
    assertThrows(IllegalArgumentException.class,
        () -> store.delete("testkit", staleRevision, player, false));
    assertEquals(40.0, store.read().getDouble("testkit.health"));

    String latestRevision = CustomKitStore.revision(store.read().getConfigurationSection("testkit"));
    store.delete("testkit", latestRevision, player, false);
    assertThrows(IllegalArgumentException.class,
        () -> store.save("testkit", candidate, latestRevision, player, false, Set.of()));
    assertNull(store.read().getConfigurationSection("testkit"));
  }
}
