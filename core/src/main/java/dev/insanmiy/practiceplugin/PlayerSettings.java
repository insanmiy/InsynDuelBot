package dev.insanmiy.practiceplugin;

import dev.insanmiy.practiceplugin.model.BotOptions;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;

final class PlayerSettings {
  private final java.util.function.Supplier<org.bukkit.configuration.ConfigurationSection> defaults;
  private final File file;
  private final YamlConfiguration data;

  PlayerSettings(PracticePlugin plugin) {
    this(new File(plugin.getDataFolder(), "player-settings.yml"), plugin::getConfig);
  }

  PlayerSettings(File file, org.bukkit.configuration.ConfigurationSection defaults) {
    this(file, () -> defaults);
  }

  private PlayerSettings(
      File file,
      java.util.function.Supplier<org.bukkit.configuration.ConfigurationSection> defaults) {
    this.defaults = defaults;
    this.file = file;
    data = YamlConfiguration.loadConfiguration(file);
  }

  boolean flag(UUID id, String key) {
    boolean fallback =
        key.equals("animations")
            ? defaults.get().getBoolean("menu-animations", true)
            : key.equals("hud") ? defaults.get().getBoolean("hud", true) : true;
    return data.getBoolean(id + "." + key, fallback);
  }

  int number(UUID id, String key) {
    int fallback =
        switch (key) {
          case "countdown" -> defaults.get().getInt("session.countdown-seconds", 3);
          case "between-rounds" -> defaults.get().getInt("session.between-round-seconds", 2);
          case "saturation" -> defaults.get().getInt("session.starting-saturation", 20);
          default -> 0;
        };
    return Math.max(
        key.equals("saturation") ? 0 : 1,
        Math.min(key.equals("saturation") ? 20 : 600, data.getInt(id + "." + key, fallback)));
  }

  int botSetting(UUID id, String key) {
    return data.getInt(id + "." + key, -1);
  }

  void resetBotSettings(UUID id) throws IOException {
    String[] botKeys = {
      "bot-aim",
      "bot-reaction",
      "bot-attack-charge",
      "bot-sprint-reset",
      "bot-shield-chance",
      "bot-strafe-speed",
      "bot-heal-threshold"
    };
    for (String k : botKeys) data.set(id + "." + k, null);
    YamlFiles.write(file.toPath(), data);
  }

  BotOptions options(UUID id) {
    BotOptions.Drill drill;
    try {
      drill = BotOptions.Drill.valueOf(data.getString(id + ".drill", "FULL_COMBAT"));
    } catch (IllegalArgumentException e) {
      drill = BotOptions.Drill.FULL_COMBAT;
    }
    int aimLevel = botSetting(id, "bot-aim");
    double aimError =
        switch (aimLevel) {
          case 0 -> 0.0;
          case 1 -> 0.2;
          case 2 -> 1.0;
          case 3 -> 2.5;
          case 4 -> 5.0;
          case 5 -> 10.0;
          default -> -1.0;
        };

    int reactionLevel = botSetting(id, "bot-reaction");
    int decisionTicks =
        switch (reactionLevel) {
          case 0 -> 1;
          case 1 -> 2;
          case 2 -> 3;
          case 3 -> 4;
          case 4 -> 6;
          case 5 -> 10;
          default -> -1;
        };
    int perceptionTicks =
        switch (reactionLevel) {
          case 0 -> 0;
          case 1 -> 1;
          case 2 -> 2;
          case 3 -> 3;
          case 4 -> 5;
          case 5 -> 8;
          default -> -1;
        };

    int chargeLevel = botSetting(id, "bot-attack-charge");
    double attackCharge =
        switch (chargeLevel) {
          case 0 -> 1.0;
          case 1 -> 0.95;
          case 2 -> 0.90;
          case 3 -> 0.85;
          default -> -1.0;
        };

    int sprintLevel = botSetting(id, "bot-sprint-reset");
    double sprintResetChance =
        switch (sprintLevel) {
          case 0 -> 1.0;
          case 1 -> 0.80;
          case 2 -> 0.50;
          case 3 -> 0.20;
          case 4 -> 0.0;
          default -> -1.0;
        };

    int shieldLevel = botSetting(id, "bot-shield-chance");
    double shieldChance =
        switch (shieldLevel) {
          case 0 -> 1.0;
          case 1 -> 0.85;
          case 2 -> 0.60;
          case 3 -> 0.30;
          case 4 -> 0.0;
          default -> -1.0;
        };

    int strafeLevel = botSetting(id, "bot-strafe-speed");
    double strafeStrength =
        switch (strafeLevel) {
          case 0 -> 1.0;
          case 1 -> 0.80;
          case 2 -> 0.60;
          case 3 -> 0.35;
          case 4 -> 0.0;
          default -> -1.0;
        };

    int healLevel = botSetting(id, "bot-heal-threshold");
    double healThreshold =
        switch (healLevel) {
          case 0 -> 0.75;
          case 1 -> 0.65;
          case 2 -> 0.50;
          case 3 -> 0.35;
          default -> -1.0;
        };

    return new BotOptions(
        flag(id, "healing"),
        flag(id, "utilities"),
        flag(id, "criticals"),
        flag(id, "shields"),
        flag(id, "strafing"),
        drill,
        aimError,
        decisionTicks,
        perceptionTicks,
        attackCharge,
        sprintResetChance,
        shieldChance,
        strafeStrength,
        healThreshold,
        flag(id, "counters"));
  }

  void set(UUID id, String key, Object value) throws IOException {
    String path = id + "." + key;
    Object previous = data.get(path);
    data.set(path, value);
    try {
      YamlFiles.write(file.toPath(), data);
    } catch (IOException e) {
      data.set(path, previous);
      throw e;
    }
  }
}
