package dev.insanmiy.practiceplugin;

import dev.insanmiy.practiceplugin.config.Kit;
import dev.insanmiy.practiceplugin.model.KitLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;

final class KitLayoutStore {
  private final PracticePlugin plugin;
  private final Path file;
  private final Map<String, KitLayout> memory = new HashMap<>();

  KitLayoutStore(PracticePlugin plugin) {
    this.plugin = plugin;
    this.file = plugin.getDataFolder().toPath().resolve("layouts.yml");
    load();
  }

  KitLayoutStore(Path file) {
    this.plugin = null;
    this.file = file;
    load();
  }

  private static String key(UUID playerId, String difficulty, String kitName) {
    return playerId + ":" + (difficulty == null ? "" : difficulty) + ":" + kitName;
  }

  private void load() {
    if (file == null) return;
    try {
      YamlConfiguration yaml = YamlFiles.readOrEmpty(file);
      for (String uuidStr : yaml.getKeys(false)) {
        UUID playerId;
        try {
          playerId = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
          continue;
        }
        ConfigurationSection diffSection = yaml.getConfigurationSection(uuidStr);
        if (diffSection == null) continue;
        for (String diff : diffSection.getKeys(false)) {
          ConfigurationSection kitSection = diffSection.getConfigurationSection(diff);
          if (kitSection == null) continue;
          for (String kitName : kitSection.getKeys(false)) {
            ConfigurationSection layoutSection = kitSection.getConfigurationSection(kitName);
            if (layoutSection == null) continue;
            int held = layoutSection.getInt("held", 0);
            ConfigurationSection slotsSec = layoutSection.getConfigurationSection("slots");
            Map<Integer, Integer> slots = new HashMap<>();
            if (slotsSec != null) {
              for (String slotKey : slotsSec.getKeys(false)) {
                try {
                  int orig = Integer.parseInt(slotKey);
                  int target = slotsSec.getInt(slotKey);
                  slots.put(orig, target);
                } catch (NumberFormatException ignored) {}
              }
            }
            KitLayout layout = new KitLayout(slots, held);
            String difficultyKey = diff.equals("__any__") ? "" : diff;
            memory.put(key(playerId, difficultyKey, kitName), layout);
          }
        }
      }
    } catch (IOException e) {
      if (plugin != null) {
        plugin.getLogger().log(Level.WARNING, "Failed to load kit layouts from " + file, e);
      }
    }
  }

  synchronized KitLayout getLayout(UUID playerId, String difficulty, String kitName) {
    KitLayout layout = memory.get(key(playerId, difficulty, kitName));
    if (layout != null) return layout;
    return memory.get(key(playerId, "", kitName));
  }

  synchronized void saveLayout(UUID playerId, String difficulty, String kitName, KitLayout layout) {
    if (layout == null || layout.slots().isEmpty()) return;
    String specificKey = key(playerId, difficulty, kitName);
    String genericKey = key(playerId, "", kitName);
    memory.put(specificKey, layout);
    memory.put(genericKey, layout);

    if (file != null) {
      try {
        YamlConfiguration yaml = YamlFiles.readOrEmpty(file);
        saveToSection(yaml, playerId, difficulty, kitName, layout);
        saveToSection(yaml, playerId, "__any__", kitName, layout);
        YamlFiles.write(file, yaml);
      } catch (IOException e) {
        if (plugin != null) {
          plugin.getLogger().log(Level.WARNING, "Failed to persist kit layout to " + file, e);
        }
      }
    }
  }

  private void saveToSection(
      YamlConfiguration yaml, UUID playerId, String diff, String kitName, KitLayout layout) {
    String path = playerId + "." + (diff.isEmpty() ? "__any__" : diff) + "." + kitName;
    yaml.set(path + ".held", layout.heldSlot());
    for (Map.Entry<Integer, Integer> entry : layout.slots().entrySet()) {
      yaml.set(path + ".slots." + entry.getKey(), entry.getValue());
    }
  }

  KitLayout capture(Player player, Kit kit, NamespacedKey slotKey, KitLayout existing) {
    Map<Integer, Integer> captured = new HashMap<>();
    Set<Integer> claimedKitSlots = new HashSet<>();
    Set<Integer> occupiedSlots = new HashSet<>();

    for (int slot = 0; slot < 36; slot++) {
      ItemStack item = player.getInventory().getItem(slot);
      if (item == null || item.getType().isAir()) continue;
      if (item.hasItemMeta()
          && item.getItemMeta().getPersistentDataContainer().has(slotKey, PersistentDataType.INTEGER)) {
        int kitSlot =
            item.getItemMeta().getPersistentDataContainer().get(slotKey, PersistentDataType.INTEGER);
        if (kitSlot >= 0 && (kit.items().containsKey(kitSlot) || kitSlot == 40)) {
          captured.put(kitSlot, slot);
          claimedKitSlots.add(kitSlot);
          occupiedSlots.add(slot);
        }
      }
    }
    ItemStack off = player.getInventory().getItemInOffHand();
    if (off != null && !off.getType().isAir() && off.hasItemMeta()) {
      if (off.getItemMeta().getPersistentDataContainer().has(slotKey, PersistentDataType.INTEGER)) {
        int kitSlot =
            off.getItemMeta().getPersistentDataContainer().get(slotKey, PersistentDataType.INTEGER);
        if (kitSlot >= 0 && (kit.items().containsKey(kitSlot) || kitSlot == 40)) {
          captured.put(kitSlot, 40);
          claimedKitSlots.add(kitSlot);
          occupiedSlots.add(40);
        }
      }
    }

    for (int slot = 0; slot <= 40; slot++) {
      if (slot > 35 && slot < 40) continue;
      if (occupiedSlots.contains(slot)) continue;
      ItemStack item =
          (slot == 40)
              ? player.getInventory().getItemInOffHand()
              : player.getInventory().getItem(slot);
      if (item == null || item.getType().isAir()) continue;

      for (Map.Entry<Integer, ItemStack> entry : kit.items().entrySet()) {
        int kitSlot = entry.getKey();
        if (claimedKitSlots.contains(kitSlot)) continue;
        if (matchesItem(entry.getValue(), item)) {
          captured.put(kitSlot, slot);
          claimedKitSlots.add(kitSlot);
          occupiedSlots.add(slot);
          break;
        }
      }
      if (!occupiedSlots.contains(slot)
          && kit.offhand() != null
          && !kit.offhand().getType().isAir()
          && !claimedKitSlots.contains(40)) {
        if (matchesItem(kit.offhand(), item)) {
          captured.put(40, slot);
          claimedKitSlots.add(40);
          occupiedSlots.add(slot);
        }
      }
    }

    if (existing != null && existing.slots() != null) {
      for (Map.Entry<Integer, Integer> entry : existing.slots().entrySet()) {
        int kitSlot = entry.getKey();
        int prevSlot = entry.getValue();
        if (!claimedKitSlots.contains(kitSlot) && !occupiedSlots.contains(prevSlot)) {
          captured.put(kitSlot, prevSlot);
          claimedKitSlots.add(kitSlot);
          occupiedSlots.add(prevSlot);
        }
      }
    }

    int held = player.getInventory().getHeldItemSlot();
    return new KitLayout(captured, held);
  }

  private static boolean matchesItem(ItemStack kitItem, ItemStack playerItem) {
    if (kitItem == null || playerItem == null) return false;
    if (kitItem.getType() == playerItem.getType()) {
      if (kitItem.getItemMeta() instanceof PotionMeta kp
          && playerItem.getItemMeta() instanceof PotionMeta pp) {
        return Objects.equals(kp.getBasePotionType(), pp.getBasePotionType());
      }
      return true;
    }
    if (playerItem.getType() == Material.BUCKET
        && (kitItem.getType() == Material.WATER_BUCKET
            || kitItem.getType() == Material.LAVA_BUCKET)) {
      return true;
    }
    if (playerItem.getType() == Material.GLASS_BOTTLE
        && kitItem.getItemMeta() instanceof PotionMeta) {
      return true;
    }
    return false;
  }
}
