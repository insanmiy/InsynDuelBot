package dev.insanmiy.practiceplugin.bot;

import java.util.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
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

      // Expand diagonal directions
      for (int[] dir : DIAGONALS) {
        // Prevent corner-cutting through solid blocks
        Block c1 = world.getBlockAt(current.x + dir[0], current.y, current.z);
        Block c2 = world.getBlockAt(current.x, current.y, current.z + dir[1]);
        if (isSolid(c1) && isSolid(c2)) continue;
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
    if (isPassable(world.getBlockAt(nx, cy, nz))
        && isPassable(world.getBlockAt(nx, cy + 1, nz))
        && isFloor(world.getBlockAt(nx, cy - 1, nz))) {
      double cost = current.gScore + (isDiagonal ? 1.414 : 1.0);
      tryAddNode(current, nx, cy, nz, ActionType.WALK, cost, targetX, targetY, targetZ, openSet, gScores);
    }

    // 2. Step up 1 block (cy + 1)
    Block stepFloor = world.getBlockAt(nx, cy, nz);
    if (isSolid(stepFloor)
        && isPassable(world.getBlockAt(nx, cy + 1, nz))
        && isPassable(world.getBlockAt(nx, cy + 2, nz))
        && isPassable(world.getBlockAt(current.x, cy + 2, current.z))) {
      double cost = current.gScore + (isDiagonal ? 1.75 : 1.35);
      tryAddNode(current, nx, cy + 1, nz, ActionType.JUMP_UP, cost, targetX, targetY, targetZ, openSet, gScores);
    }

    // 3. Safe drop down (cy - 1 to cy - 5)
    if (isPassable(world.getBlockAt(nx, cy, nz))
        && isPassable(world.getBlockAt(nx, cy + 1, nz))) {
      for (int drop = 1; drop <= 5; drop++) {
        int dropY = cy - drop;
        if (!isPassable(world.getBlockAt(nx, dropY, nz))) break;
        Block landingFloor = world.getBlockAt(nx, dropY - 1, nz);
        if (TacticalMovement.water(landingFloor.getType())) {
          double cost = current.gScore + (isDiagonal ? 1.414 : 1.0) + 1.2;
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
      Block gapAir = world.getBlockAt(nx, cy - 1, nz);
      if (!isFloor(gapAir) && isPassable(world.getBlockAt(nx, cy, nz)) && isPassable(world.getBlockAt(nx, cy + 1, nz))) {
        int landingX = current.x + dx * 2;
        int landingZ = current.z + dz * 2;
        if (isFloor(world.getBlockAt(landingX, cy - 1, landingZ))
            && isPassable(world.getBlockAt(landingX, cy, landingZ))
            && isPassable(world.getBlockAt(landingX, cy + 1, landingZ))) {
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
    double dx = tx - x;
    double dy = ty - y;
    double dz = tz - z;
    // Extra weight if target is above us; lower weight if target is below because dropping down is easier
    double verticalMultiplier = dy > 0 ? 1.35 : 0.85;
    return Math.sqrt(dx * dx + dz * dz) + Math.abs(dy) * verticalMultiplier;
  }

  public static boolean isFloor(Block block) {
    Material type = block.getType();
    if (TacticalMovement.hazardous(type)) return false;
    if (type.isSolid()) return true;
    if (TacticalMovement.water(type)) return true;
    if (block.getBlockData() instanceof Slab slab && slab.getType() == Slab.Type.BOTTOM) return true;
    return false;
  }

  public static boolean isSolid(Block block) {
    Material type = block.getType();
    if (TacticalMovement.hazardous(type)) return false;
    return type.isSolid() || block.getBlockData() instanceof Slab || block.getBlockData() instanceof Stairs;
  }

  public static boolean isPassable(Block block) {
    Material type = block.getType();
    if (TacticalMovement.hazardous(type)) return false;
    return block.isPassable() || TacticalMovement.water(type);
  }

  public static int findStandingY(World world, int x, int y, int z) {
    // Check around given y to find safe standing position
    for (int offset = 0; offset <= 4; offset++) {
      int testY = y + offset;
      if (isFloor(world.getBlockAt(x, testY - 1, z))
          && isPassable(world.getBlockAt(x, testY, z))
          && isPassable(world.getBlockAt(x, testY + 1, z))) {
        return testY;
      }
    }
    for (int offset = 1; offset <= 8; offset++) {
      int testY = y - offset;
      if (isFloor(world.getBlockAt(x, testY - 1, z))
          && isPassable(world.getBlockAt(x, testY, z))
          && isPassable(world.getBlockAt(x, testY + 1, z))) {
        return testY;
      }
    }
    return y;
  }

  private static boolean canDirectStep(World world, int x1, int y1, int z1, int x2, int y2, int z2) {
    return isPassable(world.getBlockAt(x2, y2, z2))
        && isPassable(world.getBlockAt(x2, y2 + 1, z2))
        && isFloor(world.getBlockAt(x2, y2 - 1, z2));
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
