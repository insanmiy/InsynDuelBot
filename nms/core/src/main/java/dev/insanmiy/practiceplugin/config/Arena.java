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

  public boolean overlaps(Arena other) {
    if (!playerSpawn.getWorld().equals(other.playerSpawn.getWorld())) return false;
    Location a = center(), b = other.center();

    return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ()) < radius + other.radius + 24;
  }

  public boolean contains(Location l) {
    if (!playerSpawn.getWorld().equals(l.getWorld())) return false;
    Location c = center();
    return Math.hypot(l.getX() - c.getX(), l.getZ() - c.getZ()) <= radius
        && l.getY() >= c.getY() - 5;
  }
}
