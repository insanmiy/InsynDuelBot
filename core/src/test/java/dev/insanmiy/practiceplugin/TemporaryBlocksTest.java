package dev.insanmiy.practiceplugin;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemporaryBlocksTest {
  @TempDir Path folder;

  @Test
  void failedJournalFlushIsRetriedAndUnloadedWorldKeepsItsEntries() throws IOException {
    Path journal = folder.resolve("blocks.yml");
    UUID worldId = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    World world = proxy(World.class, (method) -> switch (method) {
      case "getUID" -> worldId;
      default -> throw new UnsupportedOperationException(method);
    });
    Block block = proxy(Block.class, (method) -> switch (method) {
      case "getWorld" -> world;
      case "getX", "getY", "getZ" -> 0;
      case "getType" -> Material.COBWEB;
      default -> throw new UnsupportedOperationException(method);
    });
    BlockData original = proxy(BlockData.class, (method) -> {
      if (method.equals("getAsString")) return "minecraft:air";
      throw new UnsupportedOperationException(method);
    });
    BlockState state = proxy(BlockState.class, (method) -> {
      if (method.equals("getBlockData")) return original;
      throw new UnsupportedOperationException(method);
    });
    TemporaryBlocks blocks = new TemporaryBlocks(journal.toFile(), ignored -> null);
    assertTrue(blocks.record(block, state, owner));
    Files.createDirectory(journal);
    Path obstruction = journal.resolve("blocked");
    Files.writeString(obstruction, "Blocked");
    assertThrows(IOException.class, blocks::flush);
    Files.delete(obstruction);
    Files.delete(journal);
    blocks.flush();
    assertTrue(Files.readString(journal).contains(owner.toString()));
    TemporaryBlocks reloaded = new TemporaryBlocks(journal.toFile(), ignored -> null);
    assertFalse(reloaded.restoreAll());
    assertTrue(reloaded.owns(block));
    assertEquals(owner, reloaded.owner(block));
    assertTrue(Files.readString(journal).contains(worldId.toString()));
  }

  @Test
  void corruptJournalCannotBeReplacedWithAnEmptyJournal() throws IOException {
    Path journal = folder.resolve("blocks.yml");
    Files.writeString(journal, "blocks: [Unclosed");
    assertThrows(IllegalStateException.class, () -> new TemporaryBlocks(journal.toFile(), ignored -> null));
    assertEquals("blocks: [Unclosed", Files.readString(journal));
  }

  @Test
  void malformedEntryCannotBeSilentlyDropped() throws IOException {
    Path journal = folder.resolve("blocks.yml");
    Files.writeString(journal, "Invalid: Entry\n");
    assertThrows(IllegalStateException.class, () -> new TemporaryBlocks(journal.toFile(), ignored -> null));
    assertEquals("Invalid: Entry\n", Files.readString(journal));
  }

  private static <T> T proxy(Class<T> type, java.util.function.Function<String, Object> methods) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
        (instance, method, args) -> methods.apply(method.getName())));
  }
}
