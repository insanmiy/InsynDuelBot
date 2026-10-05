package dev.insanmiy.practiceplugin.bot;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

public final class TacticalMovement {
  private static final double[] OFFSETS = {-.3, .3};
  private static final double[] ANGLES = {
    0, Math.PI / 6, -Math.PI / 6, Math.PI / 3, -Math.PI / 3, Math.PI / 2, -Math.PI / 2
  };

  private TacticalMovement() {}

  public static boolean clearPath(Location from, Vector direction, double distance) {
    World world = from.getWorld();
    if (world == null) return false;
    double fromX = from.getX(), fromY = from.getY(), fromZ = from.getZ();
    double dirX = direction.getX(), dirY = direction.getY(), dirZ = direction.getZ();

    for (double step = .35; step <= distance + .01; step += .35) {
      double atX = fromX + dirX * step;
      double atY = fromY + dirY * step;
      double atZ = fromZ + dirZ * step;
      for (double x : OFFSETS) {
        for (double z : OFFSETS) {
          double edgeX = atX + x;
          double edgeY = atY;
          double edgeZ = atZ + z;

          Block edgeBlock =
              world.getBlockAt(
                  Location.locToBlock(edgeX),
                  Location.locToBlock(edgeY),
                  Location.locToBlock(edgeZ));
          if (!clear(edgeBlock)) return false;

          Block aboveBlock =
              world.getBlockAt(
                  Location.locToBlock(edgeX),
                  Location.locToBlock(edgeY + 1.0),
                  Location.locToBlock(edgeZ));
          if (!clear(aboveBlock)) return false;

          Block floorBlock =
              world.getBlockAt(
                  Location.locToBlock(edgeX),
                  Location.locToBlock(edgeY - 0.15),
                  Location.locToBlock(edgeZ));
          Material floorType = floorBlock.getType();
          if (!dev.insanmiy.practiceplugin.model.TraversalRules.supported(
              floorType.isSolid(), hazardous(floorType), water(edgeBlock.getType()), water(floorType))) {
            return false;
          }
        }
      }
    }
    return true;
  }

  private static boolean clear(Block block) {
    Material type = block.getType();
    return (water(type) || block.isPassable()) && !hazardous(type);
  }

  private static boolean hazardous(Material material) {
    return material == Material.COBWEB
        || material == Material.LAVA
        || material == Material.FIRE
        || material == Material.SOUL_FIRE
        || material == Material.MAGMA_BLOCK
        || material == Material.CAMPFIRE
        || material == Material.SOUL_CAMPFIRE
        || material == Material.SWEET_BERRY_BUSH
        || material == Material.POWDER_SNOW
        || material == Material.CACTUS;
  }

  private static boolean water(Material material) {
    return material == Material.WATER
        || material == Material.SEAGRASS
        || material == Material.TALL_SEAGRASS
        || material == Material.KELP
        || material == Material.KELP_PLANT;
  }

  public static Vector retreatDirection(Location self, Location target) {
    Vector away = self.toVector().subtract(target.toVector()).setY(0);
    if (away.lengthSquared() < .001) away = self.getDirection().setY(0).multiply(-1);
    if (away.lengthSquared() < .001) away = new Vector(1, 0, 0);
    away.normalize();
    Vector best = null;
    double bestScore = -Double.MAX_VALUE;
    Location eyeTarget = target.clone().add(0, 1.5, 0);
    Vector eyeTargetVec = eyeTarget.toVector();
    double selfTargetDist = self.distance(target);
    World world = self.getWorld();

    for (double angle : ANGLES) {
      Vector candidate = away.clone().rotateAroundY(angle);
      if (!clearPath(self, candidate, 2.8)) continue;
      Location end = self.clone().add(candidate.clone().multiply(2.8));
      Location endEye = end.clone().add(0, 1.5, 0);
      Vector sight = eyeTargetVec.clone().subtract(endEye.toVector());
      double sightLenSq = sight.lengthSquared();
      boolean cover =
          sightLenSq > .01
              && world != null
              && world.rayTraceBlocks(
                          endEye,
                          sight.clone().normalize(),
                          Math.sqrt(sightLenSq),
                          org.bukkit.FluidCollisionMode.NEVER,
                          true)
                  != null;
      double score =
          (end.distance(target) - selfTargetDist) * 4
              + (cover ? 5 : 0)
              + (clearPath(self, candidate, 4.2) ? 1 : 0);
      if (score > bestScore) {
        bestScore = score;
        best = candidate;
      }
    }
    return best;
  }
}
