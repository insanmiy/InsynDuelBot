package dev.insanmiy.practiceplugin;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;

final class MenuAnimation implements Runnable {
  private final PracticePlugin plugin;
  private int frame;
  private final ItemStack[] panes = new ItemStack[3];

  private ItemStack pane(int variant) {
    if (panes[variant] == null) {
      panes[variant] =
          new ItemStack(
              variant == 0
                  ? Material.LIGHT_BLUE_STAINED_GLASS_PANE
                  : variant == 1 ? Material.CYAN_STAINED_GLASS_PANE : Material.BLUE_STAINED_GLASS_PANE);
      ItemMetadata.edit(panes[variant], meta -> TextUI.name(meta, Component.text(" ")));
    }
    return panes[variant];
  }

  MenuAnimation(PracticePlugin plugin) {
    this.plugin = plugin;
  }

  public void run() {
    if (!plugin.getConfig().getBoolean("menu-animations", true)) return;
    frame++;
    for (var player : Bukkit.getOnlinePlayers()) {
      var inventory = player.getOpenInventory().getTopInventory();
      if (!(inventory.getHolder() instanceof AnimatedMenu menu)) continue;
      if (!plugin.settings().flag(player.getUniqueId(), "animations")) continue;
      int[] slots = menu.decorativeSlots();
      for (int i = 0; i < slots.length; i++) {
        inventory.setItem(slots[i], pane((i + frame) % 8 < 2 ? 0 : (i + frame) % 8 < 4 ? 1 : 2));
      }
      if (menu.pulseSlot() >= 0) {
        ItemStack button = inventory.getItem(menu.pulseSlot());
        if (button != null)
          dev.insanmiy.practiceplugin.ItemMetadata.edit(button, meta -> meta.setEnchantmentGlintOverride(frame % 8 < 4));
      }
    }
  }
}
