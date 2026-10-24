package dev.insanmiy.practiceplugin;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class CustomKitStore {
  static final int MAX_PER_PLAYER = 50;
  private final Path file;

  CustomKitStore(Path file) {
    this.file = file;
  }

  static String name(String name) {
    String result = name.toLowerCase(Locale.ROOT);
    if (!result.matches("[a-z0-9_-]{1,32}"))
      throw new IllegalArgumentException("Kit Name: 1 To 32 Letters, Numbers, _ Or -.");
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

  static void copy(ConfigurationSection from, ConfigurationSection to) { if (from == null || to == null) return;
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
      throw new IllegalArgumentException("Name Already Used. Choose A New Name.");
    if (old == null
        && !admin
        && yaml.getKeys(false).stream()
                .filter(k -> player.toString().equals(yaml.getString(k + ".owner")))
                .count()
            >= MAX_PER_PLAYER)
      throw new IllegalArgumentException("Custom Kit Limit Reached (" + MAX_PER_PLAYER + ").");
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
    if (old == null) throw new IllegalArgumentException("Custom Kit No Longer Exists.");
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
      throw new IllegalArgumentException("You Can Only Rename Your Own Custom Kits.");
    if (yaml.contains(to) || reserved.contains(to))
      throw new IllegalArgumentException("Name Already Used.");
    copy(old, yaml.createSection(to));
    yaml.set(from, null);
    write(yaml);
  }

  private static void check(ConfigurationSection old, String expected, UUID player, boolean admin) {
    if (!Objects.equals(expected, revision(old)))
      throw new IllegalArgumentException(
          "This Kit Changed Since You Opened It. Reopen The Editor First.");
    if (old != null && !editable(old, player, admin))
      throw new IllegalArgumentException("You Can Only Edit Your Own Custom Kits.");
  }

  private void write(YamlConfiguration yaml) throws IOException {
    YamlFiles.write(file, yaml, file.resolveSibling(file.getFileName() + ".bak"));
  }
}
