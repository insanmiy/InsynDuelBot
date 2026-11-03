package dev.insanmiy.practiceplugin;

import static org.junit.jupiter.api.Assertions.*;

import dev.insanmiy.practiceplugin.config.Arena;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class ArenaPoolTest {
  @Test
  void independentMatchesReserveDifferentArenasAndOnlyReleaseTheirOwnSlot() {
    World world = world();
    Map<String, Arena> configured = new LinkedHashMap<>();
    configured.put("first", arena(world, 0));
    configured.put("second", arena(world, 300));
    Map<String, Arena> occupied = new LinkedHashMap<>();

    var first = ArenaPool.select(configured.keySet(), configured::get, occupied).arena();
    assertEquals("first", first.getKey());
    occupied.put(first.getKey(), first.getValue());
    var second = ArenaPool.select(configured.keySet(), configured::get, occupied).arena();
    assertEquals("second", second.getKey());
    occupied.put(second.getKey(), second.getValue());
    assertNull(ArenaPool.select(configured.keySet(), configured::get, occupied).arena());

    occupied.remove(first.getKey());
    var next = ArenaPool.select(configured.keySet(), configured::get, occupied).arena();
    assertEquals(first, next);
    assertEquals(Map.of(second.getKey(), second.getValue()), occupied);
  }

  @Test
  void overlappingArenaIsNotOfferedWhileAnotherMatchIsActive() {
    World world = world();
    Arena active = arena(world, 0);
    Map<String, Arena> configured = Map.of(
        "nearby", arena(world, 20), "far", arena(world, 300));
    Map<String, Arena> occupied = Map.of("active", active);

    var choice = ArenaPool.select(List.of("active", "nearby", "far"), name -> {
      assertNotEquals("active", name);
      return configured.get(name);
    }, occupied);

    assertEquals("far", choice.arena().getKey());
    assertEquals(ArenaPool.Status.OCCUPIED, ArenaPool.status("active", active, occupied));
    assertEquals(ArenaPool.Status.OVERLAPPING, ArenaPool.status("nearby", configured.get("nearby"), occupied));
    assertEquals(ArenaPool.Status.AVAILABLE, ArenaPool.status("far", configured.get("far"), occupied));
  }

  @Test
  void arenasAtTheSameCoordinatesInDifferentWorldsCanRunTogether() {
    Arena active = arena(world(), 0);
    Arena otherWorld = arena(world(), 0);
    var selection = ArenaPool.select(List.of("other-world"), name -> otherWorld, Map.of("active", active));
    assertEquals(otherWorld, selection.arena().getValue());
  }

  @Test
  void invalidArenaDoesNotPreventAnotherPlayerFromUsingAValidArena() {
    Arena valid = arena(world(), 0);
    var selection = ArenaPool.select(List.of("invalid", "valid"), name -> {
      if (name.equals("invalid")) throw new IllegalArgumentException("Spawn World Is Unavailable");
      return valid;
    }, Map.of());

    assertEquals("valid", selection.arena().getKey());
    assertEquals(List.of("invalid: Spawn World Is Unavailable"), selection.errors());
    assertThrows(UnsupportedOperationException.class, () -> selection.errors().clear());
  }

  @Test
  void noUsableArenaReturnsSetupErrorsWithoutChangingActiveMatches() {
    Map<String, Arena> occupied = Map.of("active", arena(world(), 0));
    var selection = ArenaPool.select(List.of("active", "invalid"), name -> {
      throw new IllegalArgumentException("Missing Spawn");
    }, occupied);
    assertNull(selection.arena());
    assertEquals(List.of("invalid: Missing Spawn"), selection.errors());
    assertEquals(1, occupied.size());
  }

  private static Arena arena(World world, double positionX) {
    return new Arena(new Location(world, positionX, 64, 0), new Location(world, positionX + 8, 64, 0), 32);
  }

  private static World world() {
    return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
        (instance, method, arguments) -> switch (method.getName()) {
          case "equals" -> instance == arguments[0];
          case "hashCode" -> System.identityHashCode(instance);
          case "toString" -> "Arena World";
          default -> throw new UnsupportedOperationException(method.getName());
        });
  }
}
