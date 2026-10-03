package dev.insanmiy.practiceplugin;

import java.io.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

final class TemporaryBlocks {
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
      Material expected) {}

  private final File file;
  private final Map<String, Entry> entries = new LinkedHashMap<>();

  TemporaryBlocks(PracticePlugin plugin) {
    file = new File(plugin.getDataFolder(), "temporary-blocks.yml");
    var data = YamlConfiguration.loadConfiguration(file);
    for (String key : data.getKeys(false)) {
      var c = data.getConfigurationSection(key);
      if (c == null){
        continue;
      }
      int x = c.getInt("x"), y = c.getInt("y"), z = c.getInt("z");
      entries.put(
          key,
          new Entry(
              c.getString("owner") == null ? null : UUID.fromString(c.getString("owner")),
              UUID.fromString(c.getString("world")),
              x,
              y,
              z,
              c.getInt("originX", x),
              c.getInt("originY", y),
              c.getInt("originZ", z),
              c.getString("original", "minecraft:air"),
              Material.valueOf(c.getString("expected", "AIR"))));
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

  boolean fire(Block b) {
    Entry entry = entries.get(key(b));
    return entry != null && entry.expected() == Material.FIRE;
  }

  boolean record(Block b, org.bukkit.block.BlockState original, UUID owner) throws IOException {
    return record(b, original, owner, b.getType());
  }

  boolean recordFire(Block b, UUID owner) throws IOException {
    if (!b.getType().isAir()){
      return false;
    }
    return record(b, b.getState(), owner, Material.FIRE);
  }

  private boolean record(
      Block b, org.bukkit.block.BlockState original, UUID owner, Material expected)
      throws IOException {
    if (owns(b) && !Objects.equals(owner(b), owner)){
      return false;
    }
    if (original instanceof org.bukkit.block.TileState
        || entries.values().stream().filter(e -> Objects.equals(e.owner(), owner)).count() >= 1024
            && !owns(b)){
      return false;
    }
    String key = key(b);
    Entry previous = entries.get(key);

    String baseline =
        dev.insanmiy.practiceplugin.model.ReplacementHistory.baseline(
            original.getBlockData().getAsString(),
            previous == null ? null : previous.original(),
            null);
    entries.put(
        key,
        new Entry(
            owner,
            b.getWorld().getUID(),
            b.getX(),
            b.getY(),
            b.getZ(),
            b.getX(),
            b.getY(),
            b.getZ(),
            baseline,
            expected));
    try {
      save();
    } catch (IOException e) {
      if (previous == null){
        entries.remove(key);
      }
      else {
        entries.put(key, previous);
      }
      throw e;
    }
    return true;
  }

  boolean recordFluid(Block block, Block from, UUID owner) throws IOException {
    Entry parent = from == null ? null : entries.get(key(from));
    return recordFluid(block, from, owner, parent == null ? Material.WATER : parent.expected());
  }

  boolean recordFluid(Block block, Block from, UUID owner, Material fluid) throws IOException {
    if (fluid != Material.WATER && fluid != Material.LAVA){
      return false;
    }
    Entry previous = entries.get(key(block));
    if (previous != null && !Objects.equals(previous.owner(), owner)){
      return false;
    }
    if (previous != null && previous.expected() == fluid){
      return true;
    }
    if (previous == null
            && entries.values().stream().filter(e -> Objects.equals(e.owner(), owner)).count()
                >= 1024
        || !(block.getType().isAir() || owns(block))){
      return false;
    }
    Entry parent = from == null ? null : entries.get(key(from));
    if (from != null && (parent == null || !Objects.equals(parent.owner(), owner))){
      return false;
    }
    int x = parent == null ? block.getX() : parent.originX();
    int y = parent == null ? block.getY() : parent.originY();
    int z = parent == null ? block.getZ() : parent.originZ();
    if (Math.abs(block.getX() - x) > 8
        || Math.abs(block.getZ() - z) > 8
        || Math.abs(block.getY() - y) > 12){
      return false;
    }
    String original = previous != null ? previous.original() : block.getBlockData().getAsString();
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
            fluid);
    entries.put(key(block), entry);
    try {
      save();
    } catch (IOException e) {
      if (previous == null){
        entries.remove(key(block));
      }
      else {
        entries.put(key(block), previous);
      }
      throw e;
    }
    return true;
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
      if (owner != null && !owner.equals(e.owner())){
        continue;
      }
      World world = Bukkit.getWorld(e.world());
      if (world == null){
        continue;
      }
      Block block = world.getBlockAt(e.x(), e.y(), e.z());

      if (block.getType() == e.expected() || block.getType().isAir()){
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
            && plant.getHalf() == org.bukkit.block.data.Bisected.Half.BOTTOM){
          Block above = block.getRelative(org.bukkit.block.BlockFace.UP);
          if (above.getType().isAir()){
            var top = (org.bukkit.block.data.Bisected) original.clone();
            top.setHalf(org.bukkit.block.data.Bisected.Half.TOP);
            above.setBlockData(top, false);
          }
          else if (above.getType() != original.getMaterial()){
            original = Bukkit.createBlockData(Material.AIR);
          }
        }
        block.setBlockData(original, false);
      }
      iterator.remove();
      changed = true;
    }
    if (changed){
      save();
    }
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
          data.set(key + ".originX", e.originX());
          data.set(key + ".originY", e.originY());
          data.set(key + ".originZ", e.originZ());
          data.set(key + ".original", e.original());
          data.set(key + ".expected", e.expected().name());
        });
    YamlFiles.write(file.toPath(), data);
  }
}
