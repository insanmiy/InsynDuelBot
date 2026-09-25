package dev.insanmiy.practiceplugin;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

final class TemporaryWater {
  private record Entry(
      UUID owner,
      UUID world,
      int x,
      int y,
      int z,
      int originX,
      int originY,
      int originZ,
      String original,
      Material expected,
      long expires) {}

  private final File file;
  private final TemporaryWebs webs;
  private final Map<String, Entry> entries = new LinkedHashMap<>();

  TemporaryWater(PracticePlugin plugin, TemporaryWebs webs) {
    this.webs = webs;
    webs.waterJournal(this);
    file = new File(plugin.getDataFolder(), "temporary-water.yml");
    var data = YamlConfiguration.loadConfiguration(file);
    for (String key : data.getKeys(false)) {
      var c = data.getConfigurationSection(key);
      if (c == null) continue;
      int x = c.getInt("x"), y = c.getInt("y"), z = c.getInt("z");
      entries.put(
          key,
          new Entry(
              c.getString("owner") == null ? null : UUID.fromString(c.getString("owner")),
              UUID.fromString(c.getString("world")),
              x,
              y,
              z,
              x,
              y,
              z,
              c.getString("original", "minecraft:air"),
              Material.valueOf(c.getString("expected", "WATER")),
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

  String original(Block b) {
    Entry entry = entries.get(key(b));
    return entry == null ? null : entry.original();
  }

  boolean record(Block block, Block from, UUID owner) throws IOException {
    Entry parent = from == null ? null : entries.get(key(from));
    return record(block, from, owner, parent == null ? Material.WATER : parent.expected());
  }

  boolean record(Block block, Block from, UUID owner, Material fluid) throws IOException {
    if (fluid != Material.WATER && fluid != Material.LAVA) return false;
    Entry previous = entries.get(key(block));
    if (previous != null && !Objects.equals(previous.owner(), owner)) return false;
    if (previous != null && previous.expected() == fluid) return true;
    if (previous == null
            && entries.values().stream().filter(e -> Objects.equals(e.owner(), owner)).count()
                >= 256
        || !(block.getType().isAir() || block.getType() == Material.COBWEB)) return false;
    Entry parent = from == null ? null : entries.get(key(from));
    if (from != null && (parent == null || !Objects.equals(parent.owner(), owner))) return false;
    if (webs.owns(block) && !Objects.equals(webs.owner(block), owner)) return false;
    int x = parent == null ? block.getX() : parent.originX();
    int y = parent == null ? block.getY() : parent.originY();
    int z = parent == null ? block.getZ() : parent.originZ();
    if (Math.abs(block.getX() - x) > 8
        || Math.abs(block.getZ() - z) > 8
        || Math.abs(block.getY() - y) > 12) return false;
    String original =
        previous != null
            ? previous.original()
            : block.getType() == Material.COBWEB ? webs.original(block) : null;
    if (original == null) original = block.getBlockData().getAsString();
    Entry entry =
        new Entry(
            owner,
            block.getWorld().getUID(),
            block.getX(),
            block.getY(),
            block.getZ(),
            x,
            y,
            z,
            original,
            fluid,
            parent == null ? ServerFeatures.tick() + 300L : parent.expires());
    entries.put(key(block), entry);
    try {
      save();
    } catch (IOException e) {
      if (previous == null) entries.remove(key(block));
      else entries.put(key(block), previous);
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

      if (block.getType() == Material.COBWEB && webs.owns(block)) continue;
      if (block.getType() == e.expected() || block.getType().isAir())
        block.setBlockData(Bukkit.createBlockData(e.original()), false);
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
