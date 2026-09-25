package dev.insanmiy.practiceplugin;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;

final class TextUI {

  private static final LegacyComponentSerializer SERIALIZER =
      LegacyComponentSerializer.legacySection();

  private TextUI() {}

  static String legacy(Component text) {
    return SERIALIZER.serialize(text);
  }

  static Inventory inventory(InventoryHolder holder, int size, Component title) {
    return Bukkit.createInventory(holder, size, title);
  }

  static void name(ItemMeta meta, Component text) {
    meta.displayName(text);
  }

  static List<Component> lore(ItemMeta meta) {
    return meta.lore();
  }

  static void lore(ItemMeta meta, List<? extends Component> lines) {
    meta.lore(lines);
  }

  static void actionBar(Player player, Component text) {
    player.sendActionBar(text);
  }
}
