package dev.insanmiy.practiceplugin.bot;

import dev.insanmiy.practiceplugin.Messages;

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
        throw new IllegalStateException(Messages.text("text.bot-adapters.failed-to-initialize-nms-adapter") + adapterClassName, e);
      }
    }

    throw new UnsupportedOperationException(
        Messages.text("text.bot-adapters.unsupported-minecraft-version") + version + " (" + bukkitVersion + "). "
            + Messages.text("text.bot-adapters.supported-paper-releases-1-20-5"));
  }

  static String getAdapterClassName(String version) {
    return switch (version) {
      case "1.20.5", "1.20.6" ->
          "dev.insanmiy.practiceplugin.nms.v1_20_R4.Adapter_1_20_R4";
      case "1.21", "1.21.0", "1.21.1" ->
          "dev.insanmiy.practiceplugin.nms.v1_21_R1.Adapter_1_21_R1";
      case "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8",
          "1.21.9", "1.21.10", "1.21.11" ->
          "dev.insanmiy.practiceplugin.nms.v1_21_R3.Adapter_1_21_R3";
      default -> null;
    };
  }

  private BotAdapters() {}
}

