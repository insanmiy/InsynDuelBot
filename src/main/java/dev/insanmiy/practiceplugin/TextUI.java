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
  private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacySection();
  private TextUI() {}

  static String legacy(Component text) { return SERIALIZER.serialize(text); }
  static Inventory inventory(InventoryHolder holder, int size, Component title) {
    return Bukkit.createInventory(holder, size, legacy(title));
  }
  static void name(ItemMeta meta, Component text) { meta.setDisplayName(legacy(text)); }
  static List<Component> lore(ItemMeta meta) {
    var lines = meta.getLore();
    return lines == null ? null : lines.stream().<Component>map(SERIALIZER::deserialize).toList();
  }
  static void lore(ItemMeta meta, List<? extends Component> lines) {
    meta.setLore(lines.stream().map(TextUI::legacy).toList());
  }
  static void actionBar(Player player, Component text) {
    player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
        net.md_5.bungee.api.chat.TextComponent.fromLegacyText(legacy(text)));
  }
}
