package dev.insanmiy.practiceplugin;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class CustomKitStore {
  private final Path file;

  CustomKitStore(Path file) {
    this.file = file;
  }

  static String name(String name) {
    String result = name.toLowerCase(Locale.ROOT);
    if (!result.matches("[a-z0-9_-]{1,32}"))
      throw new IllegalArgumentException("Kit name: 1-32 letters, numbers, _ or -.");
    return result;
  }

  YamlConfiguration read() throws IOException {
    return YamlFiles.readOrEmpty(file);
  }

  static boolean editable(ConfigurationSection kit, UUID player, boolean admin) {
    return kit != null && (admin || player.toString().equals(kit.getString("owner")));
  }

  static String revision(ConfigurationSection kit) {
    if (kit == null) return null;
    var yaml = new YamlConfiguration();
    copy(kit, yaml);
    return yaml.saveToString();
  }

  static void copy(ConfigurationSection from, ConfigurationSection to) {
    for (String key : from.getKeys(false)) {
      if (from.isConfigurationSection(key))
        copy(from.getConfigurationSection(key), to.createSection(key));
      else to.set(key, from.get(key));
    }
  }

  void save(
      String name,
      ConfigurationSection candidate,
      String expected,
      UUID player,
      boolean admin,
      Set<String> reserved)
      throws IOException {
    name = name(name);
    var yaml = read();
    var old = yaml.getConfigurationSection(name);
    check(old, expected, player, admin);
    if (old == null && (reserved.contains(name) || yaml.contains(name)))
      throw new IllegalArgumentException("Name already used. Choose a new name.");
    String owner = old == null ? player.toString() : old.getString("owner");
    yaml.set(name, null);
    var target = yaml.createSection(name);
    copy(candidate, target);
    target.set("owner", owner);
    write(yaml);
  }

  void delete(String name, String expected, UUID player, boolean admin) throws IOException {
    var yaml = read();
    var old = yaml.getConfigurationSection(name(name));
    if (old == null) throw new IllegalArgumentException("Custom kit no longer exists.");
    check(old, expected, player, admin);
    yaml.set(name(name), null);
    write(yaml);
  }

  void rename(String from, String to, UUID player, boolean admin, Set<String> reserved)
      throws IOException {
    from = name(from);
    to = name(to);
    var yaml = read();
    var old = yaml.getConfigurationSection(from);
    if (!editable(old, player, admin))
      throw new IllegalArgumentException("You can only rename your own custom kits.");
    if (yaml.contains(to) || reserved.contains(to))
      throw new IllegalArgumentException("Name already used.");
    copy(old, yaml.createSection(to));
    yaml.set(from, null);
    write(yaml);
  }

  private static void check(ConfigurationSection old, String expected, UUID player, boolean admin) {
    if (!Objects.equals(expected, revision(old)))
      throw new IllegalArgumentException(
          "This kit changed since you opened it. Reopen the editor first.");
    if (old != null && !editable(old, player, admin))
      throw new IllegalArgumentException("You can only edit your own custom kits.");
  }

  private void write(YamlConfiguration yaml) throws IOException {
    YamlFiles.write(file, yaml, file.resolveSibling(file.getFileName() + ".bak"));
  }
}
