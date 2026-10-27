package dev.insanmiy.practiceplugin.papi;

import dev.insanmiy.practiceplugin.PracticePlugin;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public final class PapiHook {
  private static boolean enabled = false;
  private static PracticePlaceholderExpansion expansion;

  public static void init(PracticePlugin plugin) {
    if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
      enabled = true;
      try {
        expansion = new PracticePlaceholderExpansion(plugin);
        expansion.register();
        plugin.getLogger().info("Successfully Hooked Into PlaceholderAPI.");
      } catch (Throwable t) {
        plugin.getLogger().warning("Failed To Register PlaceholderAPI Expansion: " + t.getMessage());
      }
    }
  }

  public static void shutdown() {
    if (enabled) {
      if (expansion != null) {
        try {
          expansion.unregister();
        } catch (Throwable ignored) {
        }
        expansion = null;
      }
      enabled = false;
    }
  }

  public static boolean isEnabled() {
    return enabled;
  }

  public static String parse(OfflinePlayer player, String text) {
    if (!enabled || text == null || text.isEmpty()) {
      return text;
    }
    try {
      return PlaceholderAPI.setPlaceholders(player, text);
    } catch (Throwable t) {
      return text;
    }
  }
}
