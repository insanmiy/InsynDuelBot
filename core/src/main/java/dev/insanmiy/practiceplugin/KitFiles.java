package dev.insanmiy.practiceplugin;

import java.io.IOException;
import java.nio.file.*;
import org.bukkit.configuration.file.YamlConfiguration;

final class KitFiles {
  private KitFiles() {}

  static void initialize(PracticePlugin plugin) throws IOException {
    migrate(plugin.getDataFolder().toPath());
    for (String name : new String[] {"kits.yml", "customkits.yml"}) {
      if (!Files.exists(plugin.getDataFolder().toPath().resolve(name)))
        plugin.saveResource(name, false);
    }
  }

  static void migrate(Path folder) throws IOException {
    Path configFile = folder.resolve("config.yml");
    Path kitsFile = folder.resolve("kits.yml");
    Path customFile = folder.resolve("customkits.yml");
    Path legacyCustom = folder.resolve("custom-kits.yml");
    var config = YamlFiles.read(configFile);

    if (Files.exists(kitsFile)) YamlFiles.read(kitsFile);
    if (Files.exists(customFile)) YamlFiles.read(customFile);
    if (Files.exists(legacyCustom)) YamlFiles.read(legacyCustom);
    if (!Files.exists(customFile) && Files.exists(legacyCustom))
      Files.copy(legacyCustom, customFile);
    if (config.isConfigurationSection("kits")) {
      if (!Files.exists(kitsFile)) {
        var kits = new YamlConfiguration();
        CustomKitStore.copy(config.getConfigurationSection("kits"), kits.createSection("kits"));
        YamlFiles.write(kitsFile, kits);
        config.set("kits", null);
        YamlFiles.write(configFile, config, folder.resolve("config-before-kit-split.yml.bak"));
      }

    }
  }
}
