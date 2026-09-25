package dev.insanmiy.practiceplugin.bot;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;

final class TacticalMovement {
  private TacticalMovement() {}

  static boolean clearPath(Location from, Vector direction, double distance) {
    for (double step = .35; step <= distance + .01; step += .35) {
      Location at = from.clone().add(direction.clone().multiply(step));
      for (double x : new double[] {-.3, .3})
        for (double z : new double[] {-.3, .3}) {
          Location edge = at.clone().add(x, 0, z);
          if (!clear(edge) || !clear(edge.clone().add(0, 1, 0))) return false;
          Material floor = edge.clone().subtract(0, .15, 0).getBlock().getType();
          if (!dev.insanmiy.practiceplugin.model.TraversalRules.supported(
              floor.isSolid(), hazardous(floor), water(edge.getBlock().getType()), water(floor)))
            return false;
        }
    }
    return true;
  }

  private static boolean clear(Location at) {
    return (water(at.getBlock().getType()) || at.getBlock().isPassable())
        && !hazardous(at.getBlock().getType());
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

  static Vector retreatDirection(Location self, Location target) {
    Vector away = self.toVector().subtract(target.toVector()).setY(0);
    if (away.lengthSquared() < .001) away = self.getDirection().setY(0).multiply(-1);
    if (away.lengthSquared() < .001) away = new Vector(1, 0, 0);
    away.normalize();
    Vector best = null;
    double bestScore = -Double.MAX_VALUE;
    for (double angle :
        new double[] {
          0, Math.PI / 6, -Math.PI / 6, Math.PI / 3, -Math.PI / 3, Math.PI / 2, -Math.PI / 2
        }) {
      Vector candidate = away.clone().rotateAroundY(angle);
      if (!clearPath(self, candidate, 2.8)) continue;
      Location end = self.clone().add(candidate.clone().multiply(2.8));
      Vector sight =
          target.clone().add(0, 1.5, 0).toVector().subtract(end.clone().add(0, 1.5, 0).toVector());
      boolean cover =
          sight.lengthSquared() > .01
              && self.getWorld()
                      .rayTraceBlocks(
                          end.clone().add(0, 1.5, 0),
                          sight.clone().normalize(),
                          sight.length(),
                          org.bukkit.FluidCollisionMode.NEVER,
                          true)
                  != null;
      double score =
          (end.distance(target) - self.distance(target)) * 4
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
