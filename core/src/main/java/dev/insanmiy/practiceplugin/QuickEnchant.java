package dev.insanmiy.practiceplugin;

import java.util.*;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

final class QuickEnchant {
  private QuickEnchant() {}

  static int apply(ItemStack item, boolean mending) {
    int applied = 0;
    for (String key :
        mending
            ? List.of("unbreaking", "mending")
            : List.of(
                "sharpness",
                "protection",
                "power",
                "efficiency",
                "unbreaking",
                "feather_falling",
                "respiration",
                "aqua_affinity",
                "depth_strider")) {
      Enchantment enchant =
          org.bukkit.Registry.ENCHANTMENT
              .get(NamespacedKey.minecraft(key));
      if (enchant == null
          || !enchant.canEnchantItem(item)
          || item.getEnchantmentLevel(enchant) >= enchant.getMaxLevel()) continue;
      boolean conflict =
          item.getEnchantments().keySet().stream()
              .anyMatch(
                  e ->
                      !e.equals(enchant) && (e.conflictsWith(enchant) || enchant.conflictsWith(e)));
      if (conflict) continue;
      item.addUnsafeEnchantment(enchant, enchant.getMaxLevel());
      applied++;
    }
    return applied;
  }
}
