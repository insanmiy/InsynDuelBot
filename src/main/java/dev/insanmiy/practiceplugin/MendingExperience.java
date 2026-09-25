package dev.insanmiy.practiceplugin;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

final class MendingExperience {
  private MendingExperience() {}

  static void award(Player player, int amount) {
    int remaining = Math.max(0, amount);

    for (int pass = 0; pass < 6 && remaining > 0; pass++) {
      long before = equippedDamage(player);
      int next = dev.insanmiy.practiceplugin.bot.NativeExperience.mend(player, remaining);
      if (next < 0 || next > remaining) break;
      remaining = next;
      if (equippedDamage(player) >= before) break;
    }
    player.giveExp(remaining);
  }

  private static long equippedDamage(Player p) {
    long damage = armorDamage(p);
    return damage
        + damage(p.getInventory().getItemInMainHand())
        + damage(p.getInventory().getItemInOffHand());
  }

  static long armorDamage(Player p) {
    long damage = 0;
    for (ItemStack item : p.getInventory().getArmorContents()) damage += damage(item);
    return damage;
  }

  private static int damage(ItemStack item) {
    return item != null && item.getItemMeta() instanceof Damageable d ? d.getDamage() : 0;
  }
}
