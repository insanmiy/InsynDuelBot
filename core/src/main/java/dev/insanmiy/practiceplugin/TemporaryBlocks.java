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
  private final Map<UUID, Integer> ownerCounts = new HashMap<>();
  private boolean dirty;

  TemporaryBlocks(PracticePlugin plugin) {
    file = new File(plugin.getDataFolder(), "temporary-blocks.yml");
    var data = YamlFiles.load(file);
    for (String key : data.getKeys(false)) {
      try {
        read(key, data.getConfigurationSection(key));
      } catch (RuntimeException e) {
        plugin.getLogger().warning("Skipping bad journal entry " + key + ": " + e);
      }
    }
  }

  private void read(String key, org.bukkit.configuration.ConfigurationSection c) {
      if (c == null){
        return;
      }
      int x = c.getInt("x"), y = c.getInt("y"), z = c.getInt("z");
      UUID owner = c.getString("owner") == null ? null : UUID.fromString(c.getString("owner"));
      if (owner != null) {
        ownerCounts.put(owner, ownerCounts.getOrDefault(owner, 0) + 1);
      }
      entries.put(
          key,
          new Entry(
              owner,
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

  private static final Set<Material> TALL_PLANTS =
      Set.of(
          Material.TALL_GRASS,
          Material.LARGE_FERN,
          Material.SUNFLOWER,
          Material.LILAC,
          Material.ROSE_BUSH,
          Material.PEONY,
          Material.TALL_SEAGRASS,
          Material.SMALL_DRIPLEAF,
          Material.PITCHER_PLANT);

  private Entry at(Block b) {
    return entries.isEmpty() ? null : entries.get(key(b));
  }

  private static String key(Block b) {
    return b.getWorld().getUID() + "_" + b.getX() + "_" + b.getY() + "_" + b.getZ();
  }

  UUID owner(Block b) {
    Entry entry = at(b);
    return entry == null ? null : entry.owner();
  }

  boolean owns(Block b) {
    return at(b) != null;
  }

  String original(Block b) {
    Entry entry = at(b);
    return entry == null ? null : entry.original();
  }

  boolean fire(Block b) {
    Entry entry = at(b);
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
        || (owner != null && ownerCounts.getOrDefault(owner, 0) >= 1024 && !owns(b))){
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
    if (previous == null && owner != null) {
      ownerCounts.put(owner, ownerCounts.getOrDefault(owner, 0) + 1);
    }
    dirty = true;
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
            && owner != null
            && ownerCounts.getOrDefault(owner, 0) >= 1024
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
    if (previous == null && owner != null) {
      ownerCounts.put(owner, ownerCounts.getOrDefault(owner, 0) + 1);
    }
    dirty = true;
    return true;
  }

  boolean restoreAll() throws IOException {
    restore(true, null);
    return entries.isEmpty();
  }

  void restoreOwner(UUID owner) throws IOException {
    restore(true, owner);
  }

  void flush() throws IOException {
    if (dirty) {
      save();
    }
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
      if (e.owner() != null) {
        int count = ownerCounts.getOrDefault(e.owner(), 1) - 1;
        if (count <= 0) ownerCounts.remove(e.owner());
        else ownerCounts.put(e.owner(), count);
      }
      Block block = world.getBlockAt(e.x(), e.y(), e.z());

      if (block.getType() == e.expected() || block.getType().isAir()){
        var original = Bukkit.createBlockData(e.original());

        if (TALL_PLANTS.contains(original.getMaterial())
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
    if (changed || dirty){
      save();
    }
  }

  private void save() throws IOException {
    dirty = false;
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
