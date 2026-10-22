package dev.insanmiy.practiceplugin.config;

import static org.junit.jupiter.api.Assertions.*;

import dev.insanmiy.practiceplugin.model.BotOptions;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class DifficultyTest {

  @Test
  void testFormatName() {
    assertEquals("LT4", Difficulty.formatName("lt4"));
    assertEquals("HT1", Difficulty.formatName("ht1"));
    assertEquals("Diamond Sword", Difficulty.formatName("diamond_sword"));
    assertEquals("Custom Kit", Difficulty.formatName("custom-kit"));
  }

  @Test
  void testPrettyDisplayName() {
    Difficulty dWithCustom =
        new Difficulty(
            "lt4", 4, 3, 4.5, 1.0, 0.45, true, 0.45, 0.55, 0.48, 7, true, "LT4 · Competent", "Description", Material.IRON_SWORD);
    assertEquals("LT4 · Competent", dWithCustom.prettyDisplayName());
    assertEquals(Material.IRON_SWORD, dWithCustom.effectiveIcon());
    assertEquals(0.50, dWithCustom.critChance());

    Difficulty dDefault =
        new Difficulty(
            "lt4", 4, 3, 4.5, 1.0, 0.45, true, 0.45, 0.55, 0.48, 7, true);
    assertEquals("LT4", dDefault.prettyDisplayName());
    assertEquals(0.50, dDefault.critChance());
  }

  @Test
  void testDefaultCritChance() {
    assertEquals(0.98, Difficulty.defaultCritChance(1));
    assertEquals(0.85, Difficulty.defaultCritChance(2));
    assertEquals(0.85, Difficulty.defaultCritChance(3));
    assertEquals(0.50, Difficulty.defaultCritChance(4));
    assertEquals(0.50, Difficulty.defaultCritChance(6));
    assertEquals(0.20, Difficulty.defaultCritChance(10));
  }

  @Test
  void testWithOptionsOverrides() {
    Difficulty base =
        new Difficulty(
            "lt4", 4, 3, 4.5, 1.0, 0.45, true, 0.45, 0.55, 0.48, 7, true);

    BotOptions override =
        new BotOptions(
            true, true, true, true, true, 0.5, 2, 1, 0.95, 0.8, 0.7, 0.9, 0.6, true);

    Difficulty modified = base.withOptions(override);
    assertEquals(2, modified.decisionTicks());
    assertEquals(1, modified.perceptionTicks());
    assertEquals(0.5, modified.aimError());
    assertEquals(0.95, modified.attackCharge());
    assertEquals(0.7, modified.shieldChance());
    assertEquals(0.50, modified.critChance());

    BotOptions noCrits = new BotOptions(true, true, false, true, true);
    Difficulty disabledCrits = base.withOptions(noCrits);
    assertEquals(0.0, disabledCrits.critChance());
  }

  @Test
  void testValidationThrows() {
    assertThrows(IllegalArgumentException.class, () ->
        new Difficulty("invalid", 0, 2, 4.0, 1.0, 0.5, false, 0.5));
    assertThrows(IllegalArgumentException.class, () ->
        new Difficulty("invalid", 4, -1, 4.0, 1.0, 0.5, false, 0.5));
    assertThrows(IllegalArgumentException.class, () ->
        new Difficulty("invalid", 4, 2, 50.0, 1.0, 0.5, false, 0.5));
    assertThrows(IllegalArgumentException.class, () ->
        new Difficulty("invalid", 4, 2, 4.0, 1.0, 0.5, false, 0.5, 0.65, 0.5, 6, true, -0.1, null, null, null));
    assertThrows(IllegalArgumentException.class, () ->
        new Difficulty("invalid", 4, 2, 4.0, 1.0, 0.5, false, 0.5, 0.65, 0.5, 6, true, 1.5, null, null, null));
  }
}
