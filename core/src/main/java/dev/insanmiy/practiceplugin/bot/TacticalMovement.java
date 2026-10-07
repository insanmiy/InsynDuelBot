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
    double dirX = direction.getX(), dirZ = direction.getZ();

    double currentY = fromY;

    for (double step = .35; step <= distance + .01; step += .35) {
      double atX = fromX + dirX * step;
      double atZ = fromZ + dirZ * step;
      double maxSupportedY = -Double.MAX_VALUE;

      for (double x : OFFSETS) {
        for (double z : OFFSETS) {
          double edgeX = atX + x;
          double edgeZ = atZ + z;

          Double cornerY = evaluateStep(world, edgeX, currentY, edgeZ, fromY);
          if (cornerY == null) return false;
          if (cornerY > maxSupportedY) {
            maxSupportedY = cornerY;
          }
        }
      }
      currentY = maxSupportedY;
    }
    return true;
  }

  private static Double evaluateStep(
      World world, double edgeX, double currentY, double edgeZ, double fromY) {
    int blockX = Location.locToBlock(edgeX);
    int blockZ = Location.locToBlock(edgeZ);

    Block edgeBlock = world.getBlockAt(blockX, Location.locToBlock(currentY), blockZ);

    // Check if there is an obstacle at feet level
    if (!clear(edgeBlock)) {
      // Check if this obstacle is a safe 1-block step-up that can be jumped onto
      Material edgeType = edgeBlock.getType();
      if (edgeType.isSolid() && !hazardous(edgeType)) {
        Block stepFeet = world.getBlockAt(blockX, Location.locToBlock(currentY + 1.0), blockZ);
        Block stepHead = world.getBlockAt(blockX, Location.locToBlock(currentY + 2.0), blockZ);
        Block jumpHeadroom = world.getBlockAt(blockX, Location.locToBlock(fromY + 2.0), blockZ);
        if (clear(stepFeet) && clear(stepHead) && clear(jumpHeadroom)) {
          return currentY + 1.0;
        }
      }
      // Wall > 1 block high, hazardous block, or obstructed headroom
      return null;
    }

    // Feet level is clear; ensure eye level is also clear
    Block aboveBlock = world.getBlockAt(blockX, Location.locToBlock(currentY + 1.0), blockZ);
    if (!clear(aboveBlock)) return null;

    // Check immediate floor at current elevation
    Block floorBlock = world.getBlockAt(blockX, Location.locToBlock(currentY - 0.15), blockZ);
    Material floorType = floorBlock.getType();
    if (hazardous(floorType)) return null;
    if (dev.insanmiy.practiceplugin.model.TraversalRules.supported(
        floorType.isSolid(), false, water(edgeBlock.getType()), water(floorType))) {
      return currentY;
    }

    // Floor is air/unsupported: check for safe ledge drops (1 to 3 blocks down)
    for (int drop = 1; drop <= 3; drop++) {
      Block dropAir = world.getBlockAt(blockX, Location.locToBlock(currentY - drop + 0.85), blockZ);
      if (!clear(dropAir)) return null;

      Block landing = world.getBlockAt(blockX, Location.locToBlock(currentY - drop - 0.15), blockZ);
      Material landingType = landing.getType();
      if (hazardous(landingType)) return null;
      if (dev.insanmiy.practiceplugin.model.TraversalRules.supported(
          landingType.isSolid(), false, water(dropAir.getType()), water(landingType))) {
        return currentY - drop;
      }
    }

    // Drop > 3 blocks (cliff or void)
    return null;
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
