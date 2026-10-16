package dev.insanmiy.practiceplugin.bot;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public final class TacticalMovement {
  private static final double[] OFFSETS = {-.3, .3};
  private static final double[] ANGLES = {
    0,
    Math.PI / 6, -Math.PI / 6,
    Math.PI / 3, -Math.PI / 3,
    Math.PI / 2, -Math.PI / 2,
    2 * Math.PI / 3, -2 * Math.PI / 3,
    5 * Math.PI / 6, -5 * Math.PI / 6
  };

  private TacticalMovement() {}

  public static Block getBlock(World world, int x, int y, int z) {
    if (world == null || y < world.getMinHeight() || y >= world.getMaxHeight()) return null;
    if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
    return world.getBlockAt(x, y, z);
  }

  public static double getCollisionMaxY(Block block) {
    if (block == null || block.isPassable()) return 0.0;
    try {
      List<BoundingBox> boxes = block.getCollisionShape().getBoundingBoxes().stream().toList();
      if (boxes.isEmpty()) return 0.0;
      double max = 0.0;
      for (BoundingBox bb : boxes) {
        if (bb.getMaxY() > max) max = bb.getMaxY();
      }
      return max;
    } catch (Exception e) {
      return block.getType().isSolid() ? 1.0 : 0.0;
    }
  }

  public static boolean clearPath(Location from, Vector direction, double distance) {
    World world = from.getWorld();
    if (world == null) return false;
    double fromX = from.getX(), fromY = from.getY(), fromZ = from.getZ();

    Vector dir = direction.clone().setY(0);
    if (dir.lengthSquared() < 0.0001) return false;
    dir.normalize();
    double dirX = dir.getX(), dirZ = dir.getZ();

    double currentY = fromY;

    for (double step = .35; step <= distance + .01; step += .35) {
      double atX = fromX + dirX * step;
      double atZ = fromZ + dirZ * step;
      double maxSupportedY = -Double.MAX_VALUE;

      int lastBx = Integer.MIN_VALUE, lastBz = Integer.MIN_VALUE;
      for (double x : OFFSETS) {
        for (double z : OFFSETS) {
          int bx = Location.locToBlock(atX + x);
          int bz = Location.locToBlock(atZ + z);
          if (bx == lastBx && bz == lastBz) continue;
          lastBx = bx;
          lastBz = bz;

          Double cornerY = evaluateStep(world, atX + x, currentY, atZ + z, fromY);
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

    Block edgeBlock = getBlock(world, blockX, Location.locToBlock(currentY), blockZ);
    if (edgeBlock == null) return null;

    double edgeMaxY = getCollisionMaxY(edgeBlock);
    // Fence/wall (height 1.5) cannot be jumped over
    if (edgeMaxY > 1.0) return null;

    if (edgeMaxY > 0.0) {
      if (hazardous(edgeBlock.getType())) return null;
      if (edgeMaxY <= 0.6) {
        // Step-up without jump (e.g. carpet, slab)
        Block headroom = getBlock(world, blockX, Location.locToBlock(currentY + 1.0), blockZ);
        if (headroom != null && clear(headroom)) {
          return currentY + edgeMaxY;
        }
        return null;
      } else {
        // Needs 1-block jump
        Block stepFeet = getBlock(world, blockX, Location.locToBlock(currentY + 1.0), blockZ);
        Block stepHead = getBlock(world, blockX, Location.locToBlock(currentY + 2.0), blockZ);
        Block jumpHeadroom = getBlock(world, blockX, Location.locToBlock(fromY + 2.0), blockZ);
        if (stepFeet != null && clear(stepFeet)
            && stepHead != null && clear(stepHead)
            && jumpHeadroom != null && clear(jumpHeadroom)) {
          return currentY + 1.0;
        }
        return null;
      }
    }

    // Feet level is clear; ensure eye level is clear
    Block aboveBlock = getBlock(world, blockX, Location.locToBlock(currentY + 1.0), blockZ);
    if (aboveBlock == null || !clear(aboveBlock)) return null;

    // Check immediate floor at current elevation
    Block floorBlock = getBlock(world, blockX, Location.locToBlock(currentY - 0.15), blockZ);
    if (floorBlock == null || hazardous(floorBlock.getType())) return null;
    double floorMaxY = getCollisionMaxY(floorBlock);
    boolean solidFloor = floorMaxY > 0.0;
    if (dev.insanmiy.practiceplugin.model.TraversalRules.supported(
        solidFloor, false, water(edgeBlock), water(floorBlock))) {
      return currentY;
    }

    // Ledge drops (1 to 3 blocks down)
    for (int drop = 1; drop <= 3; drop++) {
      Block dropAir = getBlock(world, blockX, Location.locToBlock(currentY - drop + 0.85), blockZ);
      if (dropAir == null || !clear(dropAir)) return null;

      Block landing = getBlock(world, blockX, Location.locToBlock(currentY - drop - 0.15), blockZ);
      if (landing == null || hazardous(landing.getType())) return null;
      double landingMaxY = getCollisionMaxY(landing);
      boolean solidLanding = landingMaxY > 0.0;
      if (dev.insanmiy.practiceplugin.model.TraversalRules.supported(
          solidLanding, false, water(dropAir), water(landing))) {
        return currentY - drop;
      }
    }

    return null;
  }

  public static boolean clear(Block block) {
    if (block == null) return false;
    Material type = block.getType();
    return (water(block) || block.isPassable()) && !hazardous(type);
  }

  public static boolean hazardous(Material material) {
    return material == Material.COBWEB
        || material == Material.LAVA
        || material == Material.FIRE
        || material == Material.SOUL_FIRE
        || material == Material.MAGMA_BLOCK
        || material == Material.CAMPFIRE
        || material == Material.SOUL_CAMPFIRE
        || material == Material.SWEET_BERRY_BUSH
        || material == Material.POWDER_SNOW
        || material == Material.CACTUS
        || material == Material.POINTED_DRIPSTONE
        || material == Material.WITHER_ROSE
        || material == Material.SOUL_SAND
        || material == Material.HONEY_BLOCK
        || material == Material.TNT
        || material == Material.LAVA_CAULDRON;
  }

  public static boolean water(Block block) {
    if (block == null) return false;
    Material type = block.getType();
    if (water(type)) return true;
    return block.getBlockData() instanceof Waterlogged wl && wl.isWaterlogged();
  }

  public static boolean water(Material material) {
    return material == Material.WATER
        || material == Material.SEAGRASS
        || material == Material.TALL_SEAGRASS
        || material == Material.KELP
        || material == Material.KELP_PLANT
        || material == Material.BUBBLE_COLUMN;
  }

  public static Vector retreatDirection(Location self, Location target) {
    Vector away = self.toVector().subtract(target.toVector()).setY(0);
    if (away.lengthSquared() < .001) away = self.getDirection().setY(0).multiply(-1);
    if (away.lengthSquared() < .001) away = new Vector(1, 0, 0);
    away.normalize();
    Vector best = null;
    double bestScore = -Double.MAX_VALUE;

    Location eyeTarget = target.clone();
    eyeTarget.setY(target.getY() + 1.5);
    Vector eyeTargetVec = eyeTarget.toVector();
    double selfTargetDist = self.distance(target);
    World world = self.getWorld();

    for (double angle : ANGLES) {
      Vector candidate = away.clone().rotateAroundY(angle);
      if (!clearPath(self, candidate, 2.8)) continue;
      Location end = self.clone().add(candidate.clone().multiply(2.8));
      Location endEye = end.clone();
      endEye.setY(end.getY() + 1.5);
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
    return best != null ? best : away;
  }
}
