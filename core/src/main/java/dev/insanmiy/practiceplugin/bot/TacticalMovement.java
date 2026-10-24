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
        if (clear(headroom)) {
          return currentY + edgeMaxY;
        }
        return null;
      } else {
        // Needs 1-block jump
        Block stepFeet = getBlock(world, blockX, Location.locToBlock(currentY + 1.0), blockZ);
        Block stepHead = getBlock(world, blockX, Location.locToBlock(currentY + 2.0), blockZ);
        Block jumpHeadroom = getBlock(world, blockX, Location.locToBlock(fromY + 2.0), blockZ);
        if (clear(stepFeet)
            && clear(stepHead)
            && clear(jumpHeadroom)) {
          return currentY + 1.0;
        }
        // When swimming in 2+ deep water near the surface, feet may be just below the upper water
        // block while the shoreline bank is at currentY + 1.0 with dry air at currentY + 2.0..3.0.
        if (stepFeet != null
            && getCollisionMaxY(stepFeet) <= 1.0
            && !hazardous(stepFeet.getType())
            && clear(stepHead)
            && clear(getBlock(world, blockX, Location.locToBlock(currentY + 3.0), blockZ))) {
          return currentY + 2.0;
        }
        return null;
      }
    }

    // Feet level is clear; ensure eye level is clear
    Block aboveBlock = getBlock(world, blockX, Location.locToBlock(currentY + 1.0), blockZ);
    if (!clear(aboveBlock)) return null;

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
      if (!clear(dropAir)) return null;

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

  public static boolean isDryStandable(World world, int x, int y, int z) {
    Block floor = getBlock(world, x, y - 1, z);
    Block feet = getBlock(world, x, y, z);
    Block head = getBlock(world, x, y + 1, z);
    if (floor == null || feet == null || head == null) return false;
    if (water(floor) || water(feet) || water(head)) return false;
    if (hazardous(floor.getType()) || hazardous(feet.getType()) || hazardous(head.getType()))
      return false;
    double floorMaxY = getCollisionMaxY(floor);
    return floorMaxY > 0.0 && floorMaxY <= 1.0 && feet.isPassable() && head.isPassable();
  }

  public static Location findWaterExit(Location self, Location target) {
    World world = self.getWorld();
    if (world == null) return null;
    int sx = self.getBlockX();
    int sz = self.getBlockZ();
    int surfaceY = Location.locToBlock(self.getY());
    while (surfaceY < world.getMaxHeight() - 3
        && water(getBlock(world, sx, surfaceY + 1, sz))) {
      surfaceY++;
    }

    Location best = null;
    double bestScore = -Double.MAX_VALUE;
    int[][] cardinals = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    for (int radius = 1; radius <= 12; radius++) {
      for (int dx = -radius; dx <= radius; dx++) {
        for (int dz = -radius; dz <= radius; dz++) {
          if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
          int bx = sx + dx;
          int bz = sz + dz;
          for (int dy = -2; dy <= 2; dy++) {
            int by = surfaceY + dy;
            if (!isDryStandable(world, bx, by, bz)) continue;

            // Check that this dry block can be stepped/jumped onto from an adjacent block
            boolean reachableShore = false;
            int dryNeighbors = 0;
            for (int[] c : cardinals) {
              int nx = bx + c[0];
              int nz = bz + c[1];
              if (isDryStandable(world, nx, by, nz)) {
                dryNeighbors++;
              }
              Block adjWaterOrAir = getBlock(world, nx, by - 1, nz);
              Block adjFeet = getBlock(world, nx, by, nz);
              Block adjHead = getBlock(world, nx, by + 1, nz);
              if (adjWaterOrAir != null
                  && (water(adjWaterOrAir) || water(adjFeet))
                  && clear(adjFeet)
                  && clear(adjHead)) {
                reachableShore = true;
              }
            }
            if (!reachableShore && radius > 2) continue;

            Location candidate = new Location(world, bx + 0.5, by, bz + 0.5);
            double distToSelf = Math.hypot(candidate.getX() - self.getX(), candidate.getZ() - self.getZ());
            double distToTarget =
                target != null && world.equals(target.getWorld())
                    ? Math.hypot(candidate.getX() - target.getX(), candidate.getZ() - target.getZ())
                    : 0.0;

            double score =
                -distToSelf * 3.5
                    - Math.abs(by - (surfaceY + 1)) * 1.5
                    + dryNeighbors * 0.8
                    - distToTarget * 0.25;
            if (score > bestScore) {
              bestScore = score;
              best = candidate;
            }
          }
        }
      }
      // Once we found a close reachable shore within 2 blocks, take the best nearby exit immediately
      if (best != null && radius >= 3) break;
    }
    return best;
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
      boolean endsInWater =
          water(end.getBlock()) || water(end.clone().subtract(0, 0.5, 0).getBlock());
      double score =
          (end.distance(target) - selfTargetDist) * 4
              + (cover ? 5 : 0)
              + (clearPath(self, candidate, 4.2) ? 1 : 0)
              - (endsInWater ? 12 : 0);
      if (score > bestScore) {
        bestScore = score;
        best = candidate;
      }
    }
    return best != null ? best : away;
  }
}
