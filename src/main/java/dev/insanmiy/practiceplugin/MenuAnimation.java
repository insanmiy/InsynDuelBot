package dev.insanmiy.practiceplugin;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;

final class MenuAnimation implements Runnable {
  private final PracticePlugin plugin;
  private int frame;

  MenuAnimation(PracticePlugin plugin) {
    this.plugin = plugin;
  }

  public void run() {
    if (!plugin.getConfig().getBoolean("menu-animations", true)) return;
    frame++;
    for (var player : Bukkit.getOnlinePlayers()) {
      if (!plugin.settings().flag(player.getUniqueId(), "animations")) continue;
      var inventory = player.getOpenInventory().getTopInventory();
      if (!(inventory.getHolder() instanceof AnimatedMenu menu)) continue;
      int[] slots = menu.decorativeSlots();
      for (int i = 0; i < slots.length; i++) {
        Material color =
            (i + frame) % 8 < 2
                ? Material.LIGHT_BLUE_STAINED_GLASS_PANE
                : (i + frame) % 8 < 4
                    ? Material.CYAN_STAINED_GLASS_PANE
                    : Material.BLUE_STAINED_GLASS_PANE;
        ItemStack pane = new ItemStack(color);
        dev.insanmiy.practiceplugin.ItemMetadata.edit(pane, meta -> TextUI.name(meta, Component.text(" ")));
        inventory.setItem(slots[i], pane);
      }
      if (menu.pulseSlot() >= 0) {
        ItemStack button = inventory.getItem(menu.pulseSlot());
        if (button != null)
          dev.insanmiy.practiceplugin.ItemMetadata.edit(button, meta -> meta.setEnchantmentGlintOverride(frame % 8 < 4));
      }
    }
  }
}
