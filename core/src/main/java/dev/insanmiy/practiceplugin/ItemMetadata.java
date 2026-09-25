package dev.insanmiy.practiceplugin;

import java.util.function.Consumer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ItemMetadata {
  private ItemMetadata() {}
  public static void edit(ItemStack item, Consumer<ItemMeta> editor) {
    ItemMeta meta = item.getItemMeta();
    if (meta == null) return;
    editor.accept(meta);
    item.setItemMeta(meta);
  }
}
