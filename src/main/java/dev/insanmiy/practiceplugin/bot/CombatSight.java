package dev.insanmiy.practiceplugin.bot;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public final class CombatSight {
  private CombatSight() {}

  public static Block obstruction(Player attacker, Vector direction, double distance) {
    Location eye = attacker.getEyeLocation();
    var solid =
        eye.getWorld().rayTraceBlocks(eye, direction, distance, FluidCollisionMode.NEVER, false);
    Block closest = solid == null ? null : solid.getHitBlock();
    double nearest = solid == null ? distance : solid.getHitPosition().distance(eye.toVector());
    var blocks =
        new BlockIterator(eye.getWorld(), eye.toVector(), direction, 0, (int) Math.ceil(distance));
    while (blocks.hasNext()) {
      Block block = blocks.next();
      if (block.getType() != Material.COBWEB) continue;
      double hit =
          webIntersection(
              eye.toVector(), direction, distance, block.getX(), block.getY(), block.getZ());
      if (hit <= nearest) {
        nearest = hit;
        closest = block;
      }
    }
    return closest;
  }

  public static double webIntersection(
      Vector origin, Vector direction, double distance, int x, int y, int z) {
    BoundingBox box = new BoundingBox(x, y, z, x + 1, y + 1, z + 1);
    if (box.contains(origin)) return 0;
    var hit = box.rayTrace(origin, direction, distance);
    return hit == null ? Double.POSITIVE_INFINITY : hit.getHitPosition().distance(origin);
  }
}
