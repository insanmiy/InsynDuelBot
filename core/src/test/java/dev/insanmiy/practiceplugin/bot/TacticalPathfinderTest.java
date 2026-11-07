package dev.insanmiy.practiceplugin.bot;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.bukkit.util.VoxelShape;
import org.junit.jupiter.api.Test;

class TacticalPathfinderTest {
  @Test
  void findsRouteAroundWallWithoutCuttingCorners() {
    Grid grid = new Grid();
    for (int z = -2; z <= 2; z++) {
      grid.wall(2, z);
    }
    TacticalPathfinder.Path path = grid.path(0, 1, 0, 5, 1, 0);
    assertNotNull(path);
    assertTrue(path.reachesGoal());
    assertTrue(path.waypoints().stream().anyMatch(point -> Math.abs(point.z()) >= 3));
    for (int index = 1; index < path.size(); index++) {
      Location from = path.get(index - 1).toLocation(grid.world);
      Location to = path.get(index).toLocation(grid.world);
      Vector direction = to.toVector().subtract(from.toVector());
      assertTrue(TacticalMovement.clearPath(from, direction, direction.length()));
    }
  }

  @Test
  void adjacentGoalDoesNotBypassBlockedDiagonal() {
    Grid grid = new Grid();
    grid.wall(1, 0);
    grid.wall(0, 1);
    TacticalPathfinder.Path path = grid.path(0, 1, 0, 1, 1, 1);
    assertNotNull(path);
    assertTrue(path.reachesGoal());
    assertTrue(path.size() > 2);
  }

  @Test
  void partialRouteDoesNotClaimEnclosedTargetIsReachable() {
    Grid grid = new Grid();
    for (int x = 5; x <= 7; x++) {
      for (int z = -1; z <= 1; z++) {
        if (x != 6 || z != 0) grid.wall(x, z);
      }
    }
    Location start = grid.at(0, 1, 0);
    Location target = grid.at(6, 1, 0);
    TacticalPathfinder.Path path = TacticalPathfinder.findPath(grid.world, start, target, 400);
    assertNotNull(path);
    assertFalse(path.reachesGoal());
    assertFalse(TacticalPathfinder.hasWalkablePath(grid.world, start, target));
  }

  @Test
  void keepsJumpInstructionForRaisedAdjacentGoal() {
    Grid grid = new Grid();
    grid.set(1, 1, 0, Material.STONE);
    TacticalPathfinder.Path path = grid.path(0, 1, 0, 1, 2, 0);
    assertNotNull(path);
    assertTrue(path.reachesGoal());
    assertEquals(TacticalPathfinder.ActionType.JUMP_UP, path.get(path.size() - 1).action());
  }

  @Test
  void preservesParkourAndRejectsLowCeilingOverGap() {
    Grid grid = new Grid();
    grid.set(1, 0, 0, Material.AIR);
    TacticalPathfinder.Path path = grid.path(0, 1, 0, 2, 1, 0);
    assertNotNull(path);
    assertTrue(path.waypoints().stream()
        .anyMatch(point -> point.action() == TacticalPathfinder.ActionType.PARKOUR));
    grid.set(1, 3, 0, Material.STONE);
    TacticalPathfinder.Path covered = grid.path(0, 1, 0, 2, 1, 0);
    assertNotNull(covered);
    assertTrue(covered.reachesGoal());
    assertTrue(covered.waypoints().stream()
        .noneMatch(point -> point.action() == TacticalPathfinder.ActionType.PARKOUR));
  }

  @Test
  void checksEndOfShortMovementAndAvoidsWebs() {
    Grid grid = new Grid();
    grid.wall(1, 0);
    Location edge = new Location(grid.world, .65, 1, .5);
    assertFalse(TacticalMovement.clearPath(edge, new Vector(1, 0, 0), .15));
    grid.set(1, 1, 0, Material.COBWEB);
    grid.set(1, 2, 0, Material.AIR);
    assertFalse(TacticalMovement.clearPath(grid.at(0, 1, 0), new Vector(1, 0, 0), 1));
    Vector approach = TacticalMovement.approachDirection(grid.at(0, 1, 0), grid.at(4, 1, 0));
    assertNotNull(approach);
    assertTrue(TacticalMovement.clearPath(grid.at(0, 1, 0), approach, 1.2));
    assertTrue(Math.abs(approach.getZ()) > .1);
  }

  @Test
  void findsDropFromRaisedPlatform() {
    Grid grid = new Grid();
    grid.set(0, 4, 0, Material.STONE);
    TacticalPathfinder.Path path = grid.path(0, 5, 0, 1, 1, 0);
    assertNotNull(path);
    assertTrue(path.reachesGoal());
    assertTrue(path.waypoints().stream().anyMatch(point -> point.action() == TacticalPathfinder.ActionType.DROP));
  }

  @Test
  void partialRoutePreservesVerticalProgressAwayFromTargetColumn() {
    Grid grid = new Grid();
    for (int x = -20; x <= 20; x++) {
      for (int z = -20; z <= 20; z++) {
        grid.set(x, 3, z, Material.STONE);
      }
    }
    grid.set(1, 3, 0, Material.AIR);
    TacticalPathfinder.Path path = TacticalPathfinder.findPath(
        grid.world, grid.at(0, 4, 0), grid.at(0, 1, 0), 20);
    assertNotNull(path);
    assertTrue(path.waypoints().stream().anyMatch(point -> point.y() == 1));
  }

  @Test
  void rejectsInvalidWorldOrSearchBudget() {
    Grid grid = new Grid();
    Grid other = new Grid();
    assertNull(TacticalPathfinder.findPath(grid.world, other.at(0, 1, 0), grid.at(1, 1, 0), 50));
    assertNull(TacticalPathfinder.findPath(grid.world, grid.at(0, 1, 0), grid.at(1, 1, 0), 0));
  }

  @Test
  void recoveryBacksOutOfDeadEndWithoutWalkingThroughWalls() {
    Grid grid = new Grid();
    grid.wall(1, 0);
    for (int x = -3; x <= 1; x++) {
      grid.wall(x, 1);
      grid.wall(x, -1);
    }
    Location self = grid.at(0, 1, 0);
    Location target = grid.at(5, 1, 0);
    var arena = new dev.insanmiy.practiceplugin.config.Arena(self, target, 20);
    Vector recovery = TacticalMovement.recoveryDirection(self, target, arena, 1);
    assertNotNull(recovery);
    assertTrue(recovery.getX() < -.9);
    assertTrue(TacticalMovement.clearPath(self, recovery, 2));
  }

  @Test
  void recoveryRejectsHazardsAndStaysInsideArena() {
    Grid grid = new Grid();
    Location self = grid.at(0, 1, 0);
    Location target = grid.at(5, 1, 0);
    var arena = new dev.insanmiy.practiceplugin.config.Arena(self, target, 20);
    grid.set(0, 1, -1, Material.LAVA);
    grid.set(0, 1, 1, Material.COBWEB);
    Vector recovery = TacticalMovement.recoveryDirection(self, target, arena, -1);
    assertNotNull(recovery);
    assertTrue(TacticalMovement.clearPath(self, recovery, 2));
    assertTrue(arena.contains(self.clone().add(recovery.clone().multiply(2))));
    var tinyArena = new dev.insanmiy.practiceplugin.config.Arena(self, self, .5);
    assertNull(TacticalMovement.recoveryDirection(self, target, tinyArena, 1));
    for (int x = -1; x <= 1; x++) {
      for (int z = -1; z <= 1; z++) {
        if (x != 0 || z != 0) grid.wall(x, z);
      }
    }
    assertNull(TacticalMovement.recoveryDirection(self, target, arena, 1));
  }

  private static final class Grid {
    private final Map<String, Material> blocks = new HashMap<>();
    private final World world = proxy(World.class, (instance, method, arguments) -> switch (method.getName()) {
      case "getMinHeight" -> -64;
      case "getMaxHeight" -> 320;
      case "isChunkLoaded" -> true;
      case "getBlockAt" -> block((int) arguments[0], (int) arguments[1], (int) arguments[2]);
      case "equals" -> instance == arguments[0];
      case "hashCode" -> System.identityHashCode(instance);
      case "toString" -> "Test World";
      default -> throw new UnsupportedOperationException(method.getName());
    });

    private Location at(int x, int y, int z) {
      return new Location(world, x + .5, y, z + .5);
    }

    private TacticalPathfinder.Path path(int x, int y, int z, int targetX, int targetY, int targetZ) {
      return TacticalPathfinder.findPath(world, at(x, y, z), at(targetX, targetY, targetZ), 1200);
    }

    private void wall(int x, int z) {
      set(x, 1, z, Material.STONE);
      set(x, 2, z, Material.STONE);
    }

    private void set(int x, int y, int z, Material type) {
      blocks.put(x + ":" + y + ":" + z, type);
    }

    private Block block(int x, int y, int z) {
      Material type = blocks.getOrDefault(x + ":" + y + ":" + z, y <= 0 ? Material.STONE : Material.AIR);
      VoxelShape shape = proxy(VoxelShape.class, (instance, method, arguments) -> {
        if (method.getName().equals("getBoundingBoxes")) {
          return type == Material.STONE ? List.of(new BoundingBox(0, 0, 0, 1, 1, 1)) : List.of();
        }
        throw new UnsupportedOperationException(method.getName());
      });
      return proxy(Block.class, (instance, method, arguments) -> switch (method.getName()) {
        case "getType" -> type;
        case "isPassable" -> type != Material.STONE;
        case "getBlockData" -> null;
        case "getCollisionShape" -> shape;
        case "getX" -> x;
        case "getY" -> y;
        case "getZ" -> z;
        default -> throw new UnsupportedOperationException(method.getName());
      });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
      return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
  }
}
