package dev.insanmiy.practiceplugin.bot;

import java.util.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

public final class TacticalPathfinder {

  public enum ActionType {
    WALK,
    JUMP_UP,
    DROP,
    PARKOUR,
    SWIM
  }

  public record Waypoint(int x, int y, int z, ActionType action) {
    public Location toLocation(World world) {
      return new Location(world, x + 0.5, y, z + 0.5);
    }
  }

  public record Path(List<Waypoint> waypoints) {
    public boolean isEmpty() {
      return waypoints == null || waypoints.isEmpty();
    }

    public int size() {
      return waypoints == null ? 0 : waypoints.size();
    }

    public Waypoint get(int index) {
      return waypoints.get(index);
    }
  }

  private static final int[][] CARDINALS = {
    {1, 0}, {-1, 0}, {0, 1}, {0, -1}
  };

  private static final int[][] DIAGONALS = {
    {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
  };

  private static final class Node implements Comparable<Node> {
    final int x, y, z;
    final ActionType action;
    final Node parent;
    final double gScore;
    final double fScore;
    final double hScore;

    Node(int x, int y, int z, ActionType action, Node parent, double gScore, double fScore, double hScore) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.action = action;
      this.parent = parent;
      this.gScore = gScore;
      this.fScore = fScore;
      this.hScore = hScore;
    }

    @Override
    public int compareTo(Node o) {
      return Double.compare(this.fScore, o.fScore);
    }
  }

  private TacticalPathfinder() {}

  public static boolean hasWalkablePath(World world, Location start, Location destination) {
    Path p = findPath(world, start, destination, 400);
    return p != null && !p.isEmpty();
  }

  public static Path findPath(World world, Location start, Location destination, int maxNodes) {
    if (world == null || start == null || destination == null) return null;
    if (!world.equals(destination.getWorld())) return null;

    int sx = start.getBlockX();
    int sy = findStandingY(world, sx, Location.locToBlock(start.getY()), start.getBlockZ());
    int sz = start.getBlockZ();

    int dx = destination.getBlockX();
    int dy = findStandingY(world, dx, Location.locToBlock(destination.getY()), destination.getBlockZ());
    int dz = destination.getBlockZ();

    // Already at goal
    if (sx == dx && sy == dy && sz == dz) {
      return new Path(List.of(new Waypoint(dx, dy, dz, ActionType.WALK)));
    }

    PriorityQueue<Node> openSet = new PriorityQueue<>();
    Map<Long, Double> gScores = new HashMap<>();

    double initialH = heuristic(sx, sy, sz, dx, dy, dz);
    Node startNode = new Node(sx, sy, sz, ActionType.WALK, null, 0.0, initialH, initialH);
    openSet.add(startNode);
    gScores.put(pack(sx, sy, sz), 0.0);

    Node bestNode = startNode;
    int expansions = 0;

    while (!openSet.isEmpty() && expansions < maxNodes) {
      Node current = openSet.poll();
      expansions++;

      long currentKey = pack(current.x, current.y, current.z);
      Double recordedG = gScores.get(currentKey);
      if (recordedG != null && current.gScore > recordedG + 0.0001) {
        continue;
      }

      if (current.hScore < bestNode.hScore) {
        bestNode = current;
      }

      // Check if goal reached (or adjacent with line of sight)
      if (current.x == dx && current.y == dy && current.z == dz) {
        return buildPath(world, current);
      }
      if (Math.abs(current.x - dx) <= 1 && Math.abs(current.z - dz) <= 1 && Math.abs(current.y - dy) <= 1) {
        if (canDirectStep(world, current.x, current.y, current.z, dx, dy, dz)) {
          Node goalNode = new Node(dx, dy, dz, ActionType.WALK, current, current.gScore + 1.0, 0.0, 0.0);
          return buildPath(world, goalNode);
        }
      }

      // Expand cardinal directions
      for (int[] dir : CARDINALS) {
        expandDirection(world, current, dir[0], dir[1], false, dx, dy, dz, openSet, gScores);
      }

      // Expand diagonal directions: require both orthogonal neighbors to be passable at feet and head
      for (int[] dir : DIAGONALS) {
        Block c1Feet = TacticalMovement.getBlock(world, current.x + dir[0], current.y, current.z);
        Block c1Head = TacticalMovement.getBlock(world, current.x + dir[0], current.y + 1, current.z);
        Block c2Feet = TacticalMovement.getBlock(world, current.x, current.y, current.z + dir[1]);
        Block c2Head = TacticalMovement.getBlock(world, current.x, current.y + 1, current.z + dir[1]);
        if (!isPassable(c1Feet) || !isPassable(c1Head) || !isPassable(c2Feet) || !isPassable(c2Head)) {
          continue;
        }
        expandDirection(world, current, dir[0], dir[1], true, dx, dy, dz, openSet, gScores);
      }
    }

    // If goal not strictly reached, return best partial path if it made progress towards the target
    if (bestNode != startNode && (bestNode.hScore < initialH - 0.75 || bestNode.y != startNode.y)) {
      return buildPath(world, bestNode);
    }

    return null;
  }

  private static void expandDirection(
      World world,
      Node current,
      int dx,
      int dz,
      boolean isDiagonal,
      int targetX,
      int targetY,
      int targetZ,
      PriorityQueue<Node> openSet,
      Map<Long, Double> gScores) {

    int nx = current.x + dx;
    int nz = current.z + dz;
    int cy = current.y;

    // 1. Same level walk (cy)
    Block nextFeet = TacticalMovement.getBlock(world, nx, cy, nz);
    Block nextHead = TacticalMovement.getBlock(world, nx, cy + 1, nz);
    Block nextFloor = TacticalMovement.getBlock(world, nx, cy - 1, nz);
    if (isPassable(nextFeet) && isPassable(nextHead) && isFloor(nextFloor)) {
      boolean inWater = TacticalMovement.water(nextFeet) || TacticalMovement.water(nextFloor);
      double cost = current.gScore + (isDiagonal ? 1.414 : 1.0) + (inWater ? 2.0 : 0.0);
      tryAddNode(
          current,
          nx,
          cy,
          nz,
          inWater ? ActionType.SWIM : ActionType.WALK,
          cost,
          targetX,
          targetY,
          targetZ,
          openSet,
          gScores);
    }

    // 2. Step up 1 block (cy + 1) onto solid ground or upward through water
    Block stepFloor = TacticalMovement.getBlock(world, nx, cy, nz);
    if ((isSolid(stepFloor) && TacticalMovement.getCollisionMaxY(stepFloor) <= 1.0
            || TacticalMovement.water(stepFloor))
        && isPassable(TacticalMovement.getBlock(world, nx, cy + 1, nz))
        && isPassable(TacticalMovement.getBlock(world, nx, cy + 2, nz))
        && isPassable(TacticalMovement.getBlock(world, current.x, cy + 2, current.z))) {
      boolean intoWater = TacticalMovement.water(TacticalMovement.getBlock(world, nx, cy + 1, nz));
      double cost = current.gScore + (isDiagonal ? 1.75 : 1.35) + (intoWater ? 1.5 : 0.0);
      tryAddNode(current, nx, cy + 1, nz, ActionType.JUMP_UP, cost, targetX, targetY, targetZ, openSet, gScores);
    }

    // 3. Safe drop down (cy - 1 to cy - 5)
    if (isPassable(TacticalMovement.getBlock(world, nx, cy, nz))
        && isPassable(TacticalMovement.getBlock(world, nx, cy + 1, nz))) {
      for (int drop = 1; drop <= 5; drop++) {
        int dropY = cy - drop;
        Block dropBlock = TacticalMovement.getBlock(world, nx, dropY, nz);
        if (!isPassable(dropBlock)) break;
        Block landingFloor = TacticalMovement.getBlock(world, nx, dropY - 1, nz);
        if (TacticalMovement.water(landingFloor)) {
          double cost = current.gScore + (isDiagonal ? 1.414 : 1.0) + 2.5;
          tryAddNode(current, nx, dropY, nz, ActionType.SWIM, cost, targetX, targetY, targetZ, openSet, gScores);
          break;
        }
        if (isFloor(landingFloor)) {
          double cost = current.gScore + (isDiagonal ? 1.414 : 1.0) + (drop * 0.25);
          tryAddNode(current, nx, dropY, nz, ActionType.DROP, cost, targetX, targetY, targetZ, openSet, gScores);
          break; // Stop at first landing
        }
      }
    }

    // 4. Parkour jump across 1-block gap (only on cardinals)
    if (!isDiagonal) {
      Block gapAir = TacticalMovement.getBlock(world, nx, cy - 1, nz);
      if (!isFloor(gapAir)
          && isPassable(TacticalMovement.getBlock(world, nx, cy, nz))
          && isPassable(TacticalMovement.getBlock(world, nx, cy + 1, nz))) {
        int landingX = current.x + dx * 2;
        int landingZ = current.z + dz * 2;
        if (isFloor(TacticalMovement.getBlock(world, landingX, cy - 1, landingZ))
            && isPassable(TacticalMovement.getBlock(world, landingX, cy, landingZ))
            && isPassable(TacticalMovement.getBlock(world, landingX, cy + 1, landingZ))) {
          double cost = current.gScore + 2.2;
          tryAddNode(current, landingX, cy, landingZ, ActionType.PARKOUR, cost, targetX, targetY, targetZ, openSet, gScores);
        }
      }
    }
  }

  private static void tryAddNode(
      Node parent,
      int x,
      int y,
      int z,
      ActionType action,
      double gScore,
      int tx,
      int ty,
      int tz,
      PriorityQueue<Node> openSet,
      Map<Long, Double> gScores) {
    long key = pack(x, y, z);
    Double existing = gScores.get(key);
    if (existing != null && gScore >= existing - 0.0001) {
      return;
    }
    gScores.put(key, gScore);
    double h = heuristic(x, y, z, tx, ty, tz);
    openSet.add(new Node(x, y, z, action, parent, gScore, gScore + h, h));
  }

  private static double heuristic(int x, int y, int z, int tx, int ty, int tz) {
    int dx = Math.abs(tx - x);
    int dz = Math.abs(tz - z);
    // Octile distance for 8-direction movement
    double hDist = (dx + dz) + (1.414 - 2.0) * Math.min(dx, dz);
    double dy = ty - y;
    double verticalMultiplier = dy > 0 ? 1.35 : 0.85;
    return hDist + Math.abs(dy) * verticalMultiplier;
  }

  public static boolean isFloor(Block block) {
    if (block == null) return false;
    Material type = block.getType();
    if (TacticalMovement.hazardous(type)) return false;
    if (TacticalMovement.water(block)) return true;
    return TacticalMovement.getCollisionMaxY(block) > 0.0;
  }

  public static boolean isSolid(Block block) {
    if (block == null) return true;
    Material type = block.getType();
    if (TacticalMovement.hazardous(type)) return false;
    return TacticalMovement.getCollisionMaxY(block) > 0.0;
  }

  public static boolean isPassable(Block block) {
    if (block == null) return false;
    return TacticalMovement.clear(block);
  }

  public static int findStandingY(World world, int x, int y, int z) {
    Block at = TacticalMovement.getBlock(world, x, y, z);
    if (TacticalMovement.water(at)) {
      int surfaceY = y;
      for (int offset = 0; offset <= 12; offset++) {
        Block above = TacticalMovement.getBlock(world, x, y + offset + 1, z);
        if (TacticalMovement.water(above)) {
          surfaceY = y + offset + 1;
        } else {
          break;
        }
      }
      return surfaceY;
    }
    for (int offset = 0; offset <= 4; offset++) {
      int testY = y + offset;
      if (isFloor(TacticalMovement.getBlock(world, x, testY - 1, z))
          && isPassable(TacticalMovement.getBlock(world, x, testY, z))
          && isPassable(TacticalMovement.getBlock(world, x, testY + 1, z))) {
        return testY;
      }
    }
    for (int offset = 1; offset <= 8; offset++) {
      int testY = y - offset;
      if (isFloor(TacticalMovement.getBlock(world, x, testY - 1, z))
          && isPassable(TacticalMovement.getBlock(world, x, testY, z))
          && isPassable(TacticalMovement.getBlock(world, x, testY + 1, z))) {
        return testY;
      }
    }
    return y;
  }

  private static boolean canDirectStep(World world, int x1, int y1, int z1, int x2, int y2, int z2) {
    return isPassable(TacticalMovement.getBlock(world, x2, y2, z2))
        && isPassable(TacticalMovement.getBlock(world, x2, y2 + 1, z2))
        && isFloor(TacticalMovement.getBlock(world, x2, y2 - 1, z2));
  }

  private static Path buildPath(World world, Node endNode) {
    List<Waypoint> list = new ArrayList<>();
    Node curr = endNode;
    while (curr != null) {
      list.add(new Waypoint(curr.x, curr.y, curr.z, curr.action));
      curr = curr.parent;
    }
    Collections.reverse(list);

    // Path smoothing: remove redundant intermediate waypoints with clear line of sight
    List<Waypoint> smoothed = smoothPath(world, list);
    return new Path(smoothed);
  }

  private static List<Waypoint> smoothPath(World world, List<Waypoint> raw) {
    if (raw.size() <= 2) return raw;
    List<Waypoint> smoothed = new ArrayList<>();
    smoothed.add(raw.get(0));
    int current = 0;
    while (current < raw.size() - 1) {
      int furthest = current + 1;
      for (int test = Math.min(raw.size() - 1, current + 4); test > current + 1; test--) {
        Waypoint from = raw.get(current);
        Waypoint to = raw.get(test);
        if (Math.abs(from.y() - to.y()) <= 1) {
          Location fromLoc = from.toLocation(world);
          Location toLoc = to.toLocation(world);
          Vector dir = toLoc.toVector().subtract(fromLoc.toVector());
          double dist = dir.length();
          if (dist > 0.1 && TacticalMovement.clearPath(fromLoc, dir.normalize(), dist)) {
            furthest = test;
            break;
          }
        }
      }
      smoothed.add(raw.get(furthest));
      current = furthest;
    }
    return smoothed;
  }

  public static long pack(int x, int y, int z) {
    return (((long) x & 0x3FFFFFF) << 38) | (((long) z & 0x3FFFFFF) << 12) | ((long) y & 0xFFF);
  }
}
