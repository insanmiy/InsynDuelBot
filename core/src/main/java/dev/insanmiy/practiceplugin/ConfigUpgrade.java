package dev.insanmiy.practiceplugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class ConfigUpgrade {

  private static final Set<String> RETIRED =
      Set.of(
          "nodebuff-full",
          "debuff-full",
          "netheritepot-full",
          "webpot-full",
          "smp-full",
          "smpheavy-full",
          "sword",
          "axe",
          "hybrid",
          "classic",
          "diamond",
          "netherite",
          "tank",
          "scout",
          "gapple",
          "noheal",
          "shieldbreaker",
          "quick",
          "nodebuff",
          "debuff",
          "netheritepot",
          "speedpot",
          "strengthpot",
          "web",
          "webaxe",
          "webpot",
          "webarcher",
          "smp",
          "smpheavy",
          "repair",
          "archer",
          "ranger",
          "pearl",
          "totem");

  static void apply(PracticePlugin plugin) throws IOException {
    YamlConfiguration defaults;
    try (var reader =
        new InputStreamReader(
            Objects.requireNonNull(plugin.getResource("config.yml")), StandardCharsets.UTF_8)) {
      defaults = YamlConfiguration.loadConfiguration(reader);
    }
    try (var reader = new InputStreamReader(
        Objects.requireNonNull(plugin.getResource("kits.yml")), StandardCharsets.UTF_8)) {
      CustomKitStore.copy(YamlConfiguration.loadConfiguration(reader).getConfigurationSection("kits"),
          defaults.createSection("kits"));
    }
    if (upgrade(plugin.getDataFolder().toPath(), defaults)) {
      plugin.reloadConfig();
      plugin
          .getLogger()
          .info(
              "Installed PvP tier difficulties. Old config backed up; custom kits and settings"
                  + " preserved.");
    }
  }

  static boolean upgrade(Path folder, YamlConfiguration defaults) throws IOException {
    Path file = folder.resolve("config.yml");
    var actual = YamlFiles.read(file);
    int version = actual.getInt("config-version", 1);
    if (version >= 7) return false;

    var custom =
        Files.exists(folder.resolve("custom-kits.yml"))
            ? YamlFiles.read(folder.resolve("custom-kits.yml"))
            : new YamlConfiguration();
    Path backup = Files.createTempFile(folder, "config-before-v7-", ".yml");
    Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
    upgradeDifficulties(actual, defaults);
    if (version < 6) replaceCatalog(actual, defaults, custom.getKeys(false));
    actual.set("config-version", 7);
    YamlFiles.write(file, actual);
    return true;
  }

  static void replaceCatalog(
      ConfigurationSection actual, ConfigurationSection defaults, Set<String> customNames) {
    for (String name : RETIRED) actual.set("kits." + name, null);
    merge(actual, defaults);
    String selected = actual.getString("session.default-kit", "");
    if (!actual.isConfigurationSection("kits." + selected) && !customNames.contains(selected))
      actual.set("session.default-kit", defaults.getString("session.default-kit"));
    actual.set("config-version", 6);
  }

  private static void upgradeDifficulties(
      ConfigurationSection actual, ConfigurationSection defaults) {
    Map<String, String> renamed = Map.of(
        "rookie", "lt5", "easy", "ht5", "medium", "lt3", "hard", "ht3",
        "expert", "lt2", "master", "ht2", "nightmare", "ht1");
    var previous = new YamlConfiguration();
    ConfigurationSection old = actual.getConfigurationSection("difficulties");
    if (old != null) CustomKitStore.copy(old, previous);
    ConfigurationSection tiers = actual.createSection("difficulties");
    ConfigurationSection shipped = defaults.getConfigurationSection("difficulties");
    for (String name : shipped.getKeys(false)) {
      ConfigurationSection target = tiers.createSection(name);
      CustomKitStore.copy(shipped.getConfigurationSection(name), target);
      renamed.forEach((legacy, tier) -> {
        if (tier.equals(name) && previous.isConfigurationSection(legacy))
          CustomKitStore.copy(previous.getConfigurationSection(legacy), target);
      });
      if (previous.isConfigurationSection(name))
        CustomKitStore.copy(previous.getConfigurationSection(name), target);
    }
    for (String name : previous.getKeys(false)) {
      if (!renamed.containsKey(name) && !tiers.contains(name))
        CustomKitStore.copy(previous.getConfigurationSection(name), tiers.createSection(name));
    }
    String selected = actual.getString("session.default-difficulty", "");
    actual.set("session.default-difficulty", renamed.getOrDefault(selected,
        tiers.isConfigurationSection(selected) ? selected : defaults.getString("session.default-difficulty")));
  }

  static void merge(ConfigurationSection actual, ConfigurationSection defaults) {
    for (String group : List.of("kits", "difficulties")) {
      ConfigurationSection section = defaults.getConfigurationSection(group);
      if (section == null) continue;
      for (String name : section.getKeys(false)) {
        String path = group + "." + name;
        if (!actual.contains(path, true))
          CustomKitStore.copy(section.getConfigurationSection(name), actual.createSection(path));
      }
    }
    for (String path : defaults.getKeys(true)) {
      if (path.equals("kits")
          || path.startsWith("kits.")
          || path.equals("difficulties")
          || path.startsWith("difficulties.")
          || defaults.isConfigurationSection(path)) continue;
      if (!actual.contains(path, true)) actual.set(path, defaults.get(path));
    }
  }
}
