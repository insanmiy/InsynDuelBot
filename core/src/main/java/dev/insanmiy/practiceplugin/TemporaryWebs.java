package dev.insanmiy.practiceplugin;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

final class TemporaryWebs {
  private record Entry(
      UUID owner,
      UUID world,
      int x,
      int y,
      int z,
      String original,
      Material expected,
      long expires) {}

  private final File file;
  private final Map<String, Entry> entries = new LinkedHashMap<>();
  private TemporaryWater water;

  void waterJournal(TemporaryWater water) {
    this.water = water;
  }

  TemporaryWebs(PracticePlugin plugin) {
    file = new File(plugin.getDataFolder(), "temporary-webs.yml");
    var data = YamlConfiguration.loadConfiguration(file);
    for (String key : data.getKeys(false)) {
      var c = data.getConfigurationSection(key);
      if (c == null) continue;
      entries.put(
          key,
          new Entry(
              c.getString("owner") == null ? null : UUID.fromString(c.getString("owner")),
              UUID.fromString(c.getString("world")),
              c.getInt("x"),
              c.getInt("y"),
              c.getInt("z"),
              c.getString("original", "minecraft:air"),
              Material.valueOf(c.getString("expected", "COBWEB")),
              0));
    }
  }

  private static String key(Block b) {
    return b.getWorld().getUID() + "_" + b.getX() + "_" + b.getY() + "_" + b.getZ();
  }

  UUID owner(Block b) {
    Entry entry = entries.get(key(b));
    return entry == null ? null : entry.owner();
  }

  boolean owns(Block b) {
    return entries.containsKey(key(b));
  }

  String original(Block block) {
    Entry entry = entries.get(key(block));
    return entry == null ? null : entry.original();
  }

  boolean record(Block b, org.bukkit.block.BlockState original, UUID owner) throws IOException {
    return record(b, original, owner, Material.COBWEB);
  }

  boolean recordFire(Block b, UUID owner) throws IOException {
    if (!b.getType().isAir()) return false;
    return record(b, b.getState(), owner, Material.FIRE);
  }

  boolean fire(Block b) {
    Entry entry = entries.get(key(b));
    return entry != null && entry.expected() == Material.FIRE;
  }

  private boolean record(
      Block b, org.bukkit.block.BlockState original, UUID owner, Material expected)
      throws IOException {
    if (owns(b) && !Objects.equals(owner(b), owner)) return false;
    if (original instanceof org.bukkit.block.TileState
        || entries.values().stream().filter(e -> Objects.equals(e.owner(), owner)).count() >= 128
            && !owns(b)) return false;
    String key = key(b);
    Entry previous = entries.get(key);

    if (water != null && water.owns(b) && !Objects.equals(water.owner(b), owner)) return false;
    String baseline =
        dev.insanmiy.practiceplugin.model.ReplacementHistory.baseline(
            original.getBlockData().getAsString(),
            previous == null ? null : previous.original(),
            water != null
                    && (original.getType() == Material.WATER || original.getType() == Material.LAVA)
                ? water.original(b)
                : null);
    entries.put(
        key,
        new Entry(
            owner,
            b.getWorld().getUID(),
            b.getX(),
            b.getY(),
            b.getZ(),
            baseline,
            expected,
            (long) ServerFeatures.tick() + 300));
    try {
      save();
    } catch (IOException e) {
      if (previous == null) entries.remove(key);
      else entries.put(key, previous);
      throw e;
    }
    return true;
  }

  void tick() throws IOException {
    if (ServerFeatures.tick() % 20 == 0) restore(false, null);
  }

  boolean restoreAll() throws IOException {
    restore(true, null);
    return entries.isEmpty();
  }

  void restoreOwner(UUID owner) throws IOException {
    restore(true, owner);
  }

  private void restore(boolean all, UUID owner) throws IOException {
    boolean changed = false;
    var iterator = entries.entrySet().iterator();
    while (iterator.hasNext()) {
      Entry e = iterator.next().getValue();
      if (owner != null && !owner.equals(e.owner())) continue;
      if (!all && e.expires() > ServerFeatures.tick()) continue;
      World world = Bukkit.getWorld(e.world());
      if (world == null) continue;
      Block block = world.getBlockAt(e.x(), e.y(), e.z());

      if (block.getType() == e.expected() || block.getType().isAir()) {
        var original = Bukkit.createBlockData(e.original());

        if (Set.of(
                    Material.TALL_GRASS,
                    Material.LARGE_FERN,
                    Material.SUNFLOWER,
                    Material.LILAC,
                    Material.ROSE_BUSH,
                    Material.PEONY,
                    Material.TALL_SEAGRASS,
                    Material.SMALL_DRIPLEAF,
                    Material.PITCHER_PLANT)
                .contains(original.getMaterial())
            && original instanceof org.bukkit.block.data.Bisected plant
            && plant.getHalf() == org.bukkit.block.data.Bisected.Half.BOTTOM) {
          Block above = block.getRelative(org.bukkit.block.BlockFace.UP);
          if (above.getType().isAir()) {
            var top = (org.bukkit.block.data.Bisected) original.clone();
            top.setHalf(org.bukkit.block.data.Bisected.Half.TOP);
            above.setBlockData(top, false);
          } else if (above.getType() != original.getMaterial()) {
            original = Bukkit.createBlockData(Material.AIR);
          }
        }
        block.setBlockData(original, false);
      }
      iterator.remove();
      changed = true;
    }
    if (changed) save();
  }

  private void save() throws IOException {
    var data = new YamlConfiguration();
    entries.forEach(
        (key, e) -> {
          data.set(key + ".owner", e.owner() == null ? null : e.owner().toString());
          data.set(key + ".world", e.world().toString());
          data.set(key + ".x", e.x());
          data.set(key + ".y", e.y());
          data.set(key + ".z", e.z());
          data.set(key + ".original", e.original());
          data.set(key + ".expected", e.expected().name());
        });
    YamlFiles.write(file.toPath(), data);
  }
}
