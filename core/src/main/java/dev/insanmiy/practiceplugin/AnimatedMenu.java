package dev.insanmiy.practiceplugin;

import org.bukkit.inventory.InventoryHolder;

interface AnimatedMenu extends InventoryHolder {
  int[] decorativeSlots();

  default int pulseSlot() {
    return -1;
  }
}
