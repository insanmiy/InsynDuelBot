package dev.insanmiy.practiceplugin;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecoveryStoreTest {
  @TempDir Path folder;
  private final UUID owner = UUID.randomUUID();
  private final AtomicInteger inventoryReads = new AtomicInteger();

  @Test
  void corruptRecoveryStaysPendingAndDoesNotTouchInventory() throws IOException {
    Path journal = journal();
    Files.writeString(journal, "inventory: [Unclosed");
    RecoveryStore store = store();
    assertFalse(store.restore(player(false)));
    assertTrue(Files.exists(journal));
    assertTrue(store.pending(player(false)));
    assertEquals(0, inventoryReads.get());
    assertEquals("inventory: [Unclosed", Files.readString(journal));
  }

  @Test
  void unavailableWorldRetainsJournalWithoutChangingInventory() throws IOException {
    Path journal = journal();
    Files.writeString(journal, "uuid: " + owner + "\n");
    assertFalse(store().restore(player(false)));
    assertTrue(Files.exists(journal));
    assertEquals(0, inventoryReads.get());
  }

  @Test
  void deadPlayerKeepsRecoveryUntilRespawn() throws IOException {
    Path journal = journal();
    Files.writeString(journal, "uuid: " + owner + "\n");
    assertFalse(store().restore(player(true)));
    assertTrue(Files.exists(journal));
    assertEquals(0, inventoryReads.get());
  }

  @Test
  void corruptLegacyRecoveryAlsoRemainsPending() throws IOException {
    Path journal = folder.resolve("recovery.yml");
    Files.writeString(journal, "uuid: [Unclosed");
    RecoveryStore store = store();
    assertTrue(store.pending(player(false)));
    assertFalse(store.restore(player(false)));
    assertTrue(Files.exists(journal));
    assertEquals(0, inventoryReads.get());
  }

  @Test
  void invalidInventorySlotsAndItemDataCannotBeSilentlyDiscarded() {
    var data = new org.bukkit.configuration.file.YamlConfiguration();
    data.set("inventory.99", "Data");
    assertThrows(IOException.class, () -> RecoveryStore.readInventory(data, 41));
    data.set("inventory", null);
    data.set("inventory.invalid", "Data");
    assertThrows(IOException.class, () -> RecoveryStore.readInventory(data, 41));
    data.set("inventory", null);
    data.set("inventory.0", "Not Base64!");
    assertThrows(IOException.class, () -> RecoveryStore.readInventory(data, 41));
    data.set("inventory", "Invalid");
    assertThrows(IOException.class, () -> RecoveryStore.readInventory(data, 41));
  }

  private Path journal() throws IOException {
    Path journal = folder.resolve("recovery/" + owner + ".yml");
    Files.createDirectories(journal.getParent());
    return journal;
  }

  private RecoveryStore store() {
    return new RecoveryStore(folder.toFile(), NamespacedKey.minecraft("test"), Logger.getAnonymousLogger());
  }

  private Player player(boolean dead) {
    PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(
        getClass().getClassLoader(), new Class<?>[] {PlayerInventory.class}, (proxy, method, args) -> {
          if (method.getName().equals("getSize")) return 0;
          throw new UnsupportedOperationException(method.getName());
        });
    return (Player) Proxy.newProxyInstance(
        getClass().getClassLoader(), new Class<?>[] {Player.class}, (proxy, method, args) -> switch (method.getName()) {
          case "getUniqueId" -> owner;
          case "getName" -> "TestPlayer";
          case "isDead" -> dead;
          case "getInventory" -> { inventoryReads.incrementAndGet(); yield inventory; }
          case "getItemOnCursor" -> null;
          default -> throw new UnsupportedOperationException(method.getName());
        });
  }
}
