package dev.insanmiy.practiceplugin;

import dev.insanmiy.practiceplugin.model.BotOptions;
import java.io.*;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class PlayerSettings {
  static final int MAX_PRESETS = 21;
  private static final List<String> BOT_SETTINGS = List.of(
      "bot-aim", "bot-reaction", "bot-attack-charge", "bot-sprint-reset",
      "bot-shield-chance", "bot-strafe-speed", "bot-heal-threshold");
  private static final List<String> BOT_FLAGS =
      List.of("healing", "utilities", "criticals", "shields", "strafing", "counters");
  private final java.util.function.Supplier<org.bukkit.configuration.ConfigurationSection> defaults;
  private final File file;
  private YamlConfiguration data;

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
    data = YamlFiles.load(file);
  }

  boolean flag(UUID id, String key) {
    boolean fallback =
        key.equals("animations")
            ? defaults.get().getBoolean("menu-animations", true)
            : !key.equals("hud") || defaults.get().getBoolean("hud", false);
    return data.getBoolean(id + "." + key, fallback);
  }

  int number(UUID id, String key) {
    int fallback =
        switch (key) {
          case "countdown" -> defaults.get().getInt("session.countdown-seconds", 3);
          case "between-rounds" -> defaults.get().getInt("session.between-round-seconds", 3);
          case "saturation" -> defaults.get().getInt("session.starting-saturation", 20);
          default -> 0;
        };
    return Math.clamp(
        data.getInt(id + "." + key, fallback),
        key.equals("saturation") ? 0 : 1,
        key.equals("saturation") ? 20 : 600);
  }

  int botSetting(UUID id, String key) {
    return data.getInt(id + "." + key, -1);
  }

  boolean hasBotOverrides(UUID id) {
    String[] botKeys = {
      "bot-aim",
      "bot-reaction",
      "bot-attack-charge",
      "bot-sprint-reset",
      "bot-shield-chance",
      "bot-strafe-speed",
      "bot-heal-threshold"
    };
    for (String k : botKeys) {
      if (data.contains(id + "." + k) && data.getInt(id + "." + k, -1) != -1) {
        return true;
      }
    }
    String[] toggleKeys = {"healing", "utilities", "criticals", "shields", "strafing", "counters"};
    for (String k : toggleKeys) {
      if (data.contains(id + "." + k) && !data.getBoolean(id + "." + k, true)) {
        return true;
      }
    }
    return false;
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

  List<String> presets(UUID id) {
    ConfigurationSection section = data.getConfigurationSection(id + ".presets");
    if (section == null) return List.of();
    return section.getKeys(false).stream().filter(section::isConfigurationSection).sorted().toList();
  }

  static String presetName(String name) {
    String normalized = Objects.requireNonNull(name, "Preset Name Is Required").toLowerCase(Locale.ROOT);
    if (!normalized.matches("[a-z0-9_-]{1,32}"))
      throw new IllegalArgumentException("Preset Names Must Be 1 To 32 Letters, Numbers, Underscores, Or Hyphens.");
    return normalized;
  }

  void savePreset(UUID id, String name) throws IOException {
    String normalized = presetName(name);
    List<String> saved = presets(id);
    if (!saved.contains(normalized) && saved.size() >= MAX_PRESETS)
      throw new IllegalArgumentException("You Can Save Up To " + MAX_PRESETS + " Bot Presets. Delete One First.");
    YamlConfiguration candidate = copySettings();
    ConfigurationSection preset = candidate.createSection(id + ".presets." + normalized);
    for (String key : BOT_SETTINGS) preset.set(key, botSetting(id, key));
    for (String key : BOT_FLAGS) preset.set(key, flag(id, key));
    writeSettings(candidate);
  }

  void loadPreset(UUID id, String name) throws IOException {
    ConfigurationSection preset = requirePreset(id, name);
    YamlConfiguration candidate = copySettings();
    for (String key : BOT_SETTINGS) candidate.set(id + "." + key, preset.getInt(key, -1));
    for (String key : BOT_FLAGS) candidate.set(id + "." + key, preset.getBoolean(key, true));
    writeSettings(candidate);
  }

  void deletePreset(UUID id, String name) throws IOException {
    requirePreset(id, name);
    YamlConfiguration candidate = copySettings();
    candidate.set(id + ".presets." + presetName(name), null);
    writeSettings(candidate);
  }

  private ConfigurationSection requirePreset(UUID id, String name) {
    ConfigurationSection preset = data.getConfigurationSection(id + ".presets." + presetName(name));
    if (preset == null) throw new IllegalArgumentException("Unknown Bot Preset. Use /practice Preset List.");
    return preset;
  }

  private YamlConfiguration copySettings() {
    YamlConfiguration candidate = new YamlConfiguration();
    CustomKitStore.copy(data, candidate);
    return candidate;
  }

  private void writeSettings(YamlConfiguration candidate) throws IOException {
    YamlFiles.write(file.toPath(), candidate);
    data = candidate;
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
