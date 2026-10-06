package dev.insanmiy.practiceplugin.bot;

import org.bukkit.Bukkit;

// Resolves the BotAdapter for the running Minecraft version
public final class BotAdapters {

  private static volatile BotAdapter activeAdapter;

  // Get active BotAdapter
  public static BotAdapter getAdapter() {
    if (activeAdapter == null) {
      activeAdapter = resolveAdapter();
    }
    return activeAdapter;
  }

  private static BotAdapter resolveAdapter() {
    String bukkitVersion = Bukkit.getBukkitVersion();
    String version = bukkitVersion.split("-")[0];

    String adapterClassName = getAdapterClassName(version);
    if (adapterClassName != null) {
      try {
        Class<?> clazz = Class.forName(adapterClassName);
        return (BotAdapter) clazz.getConstructor().newInstance();
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("Failed to initialize NMS adapter: " + adapterClassName, e);
      }
    }

    throw new UnsupportedOperationException(
        "Unsupported Minecraft version: " + version + " (" + bukkitVersion + "). "
            + "Supported versions: 1.20.6 to 1.21.11");
  }

  private static String getAdapterClassName(String version) {
    if ("1.20.5".equals(version) || "1.20.6".equals(version)) {
      return "dev.insanmiy.practiceplugin.nms.v1_20_R4.Adapter_1_20_R4";
    }

    if ("1.21".equals(version) || "1.21.0".equals(version) || "1.21.1".equals(version)) {
      return "dev.insanmiy.practiceplugin.nms.v1_21_R1.Adapter_1_21_R1";
    }

    if (version.startsWith("1.21.")) {
      try {
        int patch = Integer.parseInt(version.substring("1.21.".length()));
        if (patch > 4)
          Bukkit.getLogger().warning(
              "[InsynDuelBot] The 1.21.2+ adapter is only built and tested against 1.21.4; "
                  + version + " is unverified.");
        if (patch >= 2 && patch <= 11) {
          return "dev.insanmiy.practiceplugin.nms.v1_21_R3.Adapter_1_21_R3";
        }
      } catch (NumberFormatException ignored) {
        // Ignore non numeric suffixes
      }
    }

    return null;
  }

  private BotAdapters() {}
}
