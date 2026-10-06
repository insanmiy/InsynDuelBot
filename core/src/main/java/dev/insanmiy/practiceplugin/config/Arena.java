package dev.insanmiy.practiceplugin.config;

import org.bukkit.*;

public record Arena(Location playerSpawn, Location botSpawn, double radius) {
  public Arena {
    playerSpawn = playerSpawn.clone();
    botSpawn = botSpawn.clone();
  }

  public String error() {
    if (playerSpawn.getWorld() == null || !playerSpawn.getWorld().equals(botSpawn.getWorld()))
      return "Spawns must be in the same loaded world.";
    double distance = playerSpawn.distance(botSpawn);
    if (distance < 4 || distance > Math.max(12.0, radius * 2.0 - 4.0)) return "Spawns must be at least 4 blocks apart and inside the arena.";
    if (!Double.isFinite(radius) || radius < 1)
      return "Arena radius must be at least 1 block.";
    if (!safe(playerSpawn) || !safe(botSpawn))
      return "Spawns need solid floors and two clear blocks of headroom.";
    return null;
  }

  public static boolean safe(Location l) {
    return l.getBlock().isPassable()
        && !l.getBlock().isLiquid()
        && l.clone().add(0, 1, 0).getBlock().isPassable()
        && l.clone().subtract(0, 1, 0).getBlock().getType().isSolid();
  }

  public Location center() {
    return playerSpawn.clone().add(botSpawn.toVector()).multiply(.5);
  }

  private boolean sameWorld(World world) {
    return world != null && world.equals(playerSpawn.getWorld());
  }

  private double centerX() {
    return (playerSpawn.getX() + botSpawn.getX()) / 2;
  }

  private double centerZ() {
    return (playerSpawn.getZ() + botSpawn.getZ()) / 2;
  }

  public boolean overlaps(Arena other) {
    if (!sameWorld(other.playerSpawn.getWorld())) return false;
    double dx = centerX() - other.centerX(), dz = centerZ() - other.centerZ();
    double reach = radius + other.radius + 24;
    return dx * dx + dz * dz < reach * reach;
  }

  public boolean contains(Location l) {
    if (!sameWorld(l.getWorld())) return false;
    double dx = l.getX() - centerX(), dz = l.getZ() - centerZ();
    return dx * dx + dz * dz <= radius * radius
        && l.getY() >= (playerSpawn.getY() + botSpawn.getY()) / 2 - 5;
  }
}
