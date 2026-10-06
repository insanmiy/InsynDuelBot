package dev.insanmiy.practiceplugin;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;

final class ProgressStore {
  private final PracticePlugin plugin;
  private final File file;
  private final YamlConfiguration data;

  ProgressStore(PracticePlugin plugin) {
    this.plugin = plugin;
    file = new File(plugin.getDataFolder(), "stats.yml");
    data = YamlFiles.load(file);
  }

  void record(PracticeSession s) {
    if (s.totalFightTicks == 0 && s.total.hits() == 0) return;
    String p = s.owner.getUniqueId() + "." + s.mode.id() + ".";
    data.set(s.owner.getUniqueId() + ".name", s.owner.getName());
    add(p + "sessions", 1);
    add(p + "rounds-won", s.roundsWon);
    add(p + "rounds-lost", s.roundsLost);
    add(p + "series-won", s.seriesWon);
    add(p + "series-lost", s.seriesLost);
    add(p + "hits", s.total.hits());
    add(p + "apples", s.total.apples());
    add(p + "utility-projectiles", s.total.utilityUses());
    add(p + "ticks", s.totalFightTicks);
    data.set(p + "damage-dealt", data.getDouble(p + "damage-dealt") + s.total.dealt());
    data.set(p + "damage-taken", data.getDouble(p + "damage-taken") + s.total.taken());
    data.set(p + "best-combo", Math.max(data.getInt(p + "best-combo"), s.total.longest()));
    save();
  }

  private void add(String path, long value) {
    data.set(path, data.getLong(path) + value);
  }

  String summary(UUID id, dev.insanmiy.practiceplugin.model.PracticeMode mode) {
    String p = id + "." + mode.id() + ".";
    return String.format(
        java.util.Locale.ROOT,
        "%s: %d sessions | rounds %dW/%dL | %d hits | best combo %d | %.1f minutes",
        mode.id(),
        data.getLong(p + "sessions"),
        data.getLong(p + "rounds-won"),
        data.getLong(p + "rounds-lost"),
        data.getLong(p + "hits"),
        data.getInt(p + "best-combo"),
        data.getLong(p + "ticks") / 1200.0);
  }

  void save() {
    try {
      YamlFiles.write(file.toPath(), data);
    } catch (IOException e) {
      plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not save practice stats", e);
    }
  }
}
