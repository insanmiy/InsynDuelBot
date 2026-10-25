package dev.insanmiy.practiceplugin.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EditorRulesTest {

  @Test
  void testEquipmentFitsArmorSlots() {
    // 36: Boots
    assertTrue(EditorRules.equipmentFits(36, "DIAMOND_BOOTS"));
    assertTrue(EditorRules.equipmentFits(36, "NETHERITE_BOOTS"));
    assertFalse(EditorRules.equipmentFits(36, "DIAMOND_CHESTPLATE"));
    assertFalse(EditorRules.equipmentFits(36, "DIAMOND_SWORD"));

    // 37: Leggings
    assertTrue(EditorRules.equipmentFits(37, "DIAMOND_LEGGINGS"));
    assertTrue(EditorRules.equipmentFits(37, "IRON_LEGGINGS"));
    assertFalse(EditorRules.equipmentFits(37, "DIAMOND_BOOTS"));

    // 38: Chestplate / Elytra
    assertTrue(EditorRules.equipmentFits(38, "DIAMOND_CHESTPLATE"));
    assertTrue(EditorRules.equipmentFits(38, "ELYTRA"));
    assertFalse(EditorRules.equipmentFits(38, "DIAMOND_LEGGINGS"));

    // 39: Helmet / Pumpkin / Heads
    assertTrue(EditorRules.equipmentFits(39, "DIAMOND_HELMET"));
    assertTrue(EditorRules.equipmentFits(39, "NETHERITE_HELMET"));
    assertTrue(EditorRules.equipmentFits(39, "TURTLE_HELMET"));
    assertTrue(EditorRules.equipmentFits(39, "CARVED_PUMPKIN"));
    assertTrue(EditorRules.equipmentFits(39, "ZOMBIE_HEAD"));
    assertTrue(EditorRules.equipmentFits(39, "SKELETON_SKULL"));
    assertFalse(EditorRules.equipmentFits(39, "DIAMOND_CHESTPLATE"));
  }

  @Test
  void testEquipmentFitsStorageAndOffhandSlots() {
    // Hotbar (0..8) and Storage (9..35) accept any item
    for (int slot = 0; slot < 36; slot++) {
      assertTrue(EditorRules.equipmentFits(slot, "DIAMOND_SWORD"));
      assertTrue(EditorRules.equipmentFits(slot, "DIAMOND_HELMET"));
      assertTrue(EditorRules.equipmentFits(slot, "GOLDEN_APPLE"));
    }

    // Offhand (40) accepts any item
    assertTrue(EditorRules.equipmentFits(40, "SHIELD"));
    assertTrue(EditorRules.equipmentFits(40, "TOTEM_OF_UNDYING"));
    assertTrue(EditorRules.equipmentFits(40, "DIAMOND_SWORD"));
  }

  @Test
  void testEquipmentFitsBoundaryAndNull() {
    assertFalse(EditorRules.equipmentFits(-1, "DIAMOND_SWORD"));
    assertFalse(EditorRules.equipmentFits(41, "DIAMOND_SWORD"));
    assertFalse(EditorRules.equipmentFits(0, null));
    assertFalse(EditorRules.equipmentFits(36, null));
  }

  @Test
  void testAmountClamping() {
    assertEquals(1, EditorRules.amount(0, 64));
    assertEquals(16, EditorRules.amount(16, 64));
    assertEquals(16, EditorRules.amount(64, 16));
    assertEquals(1, EditorRules.amount(-5, 64));
  }

  @Test
  void testAppendName() {
    assertEquals("test_kit", EditorRules.appendName("test_ki", 't'));
    assertEquals("test_kit", EditorRules.appendName("test_kit", '!')); // invalid char
    String longName = "a".repeat(32);
    assertEquals(longName, EditorRules.appendName(longName, 'b')); // max 32 chars
  }
}
