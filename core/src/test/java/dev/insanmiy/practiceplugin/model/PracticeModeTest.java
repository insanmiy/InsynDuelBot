package dev.insanmiy.practiceplugin.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PracticeModeTest {

  @Test
  void testStandardModes() {
    PracticeMode match = PracticeMode.parse("match");
    assertEquals(PracticeMode.MATCH, match);
    assertEquals("match", match.id());
    assertEquals(1, match.botCount());
    assertFalse(match.isOutnumbered());

    PracticeMode duel = PracticeMode.parse("duel");
    assertEquals(PracticeMode.DUEL, duel);
    assertEquals("duel", duel.id());
    assertEquals(1, duel.botCount());
    assertFalse(duel.isOutnumbered());

    PracticeMode endless = PracticeMode.parse("endless");
    assertEquals(PracticeMode.ENDLESS, endless);
    assertEquals("endless", endless.id());
    assertEquals(1, endless.botCount());
    assertFalse(endless.isOutnumbered());
  }

  @Test
  void testOutnumberedModes() {
    PracticeMode v21 = PracticeMode.parse("2v1");
    assertEquals(PracticeMode.V2_1, v21);
    assertEquals("2v1", v21.id());
    assertEquals(2, v21.botCount());
    assertTrue(v21.isOutnumbered());

    PracticeMode v31 = PracticeMode.parse("3v1");
    assertEquals(PracticeMode.V3_1, v31);
    assertEquals("3v1", v31.id());
    assertEquals(3, v31.botCount());
    assertTrue(v31.isOutnumbered());
  }

  @Test
  void testCaseInsensitiveAndAliases() {
    assertEquals(PracticeMode.V2_1, PracticeMode.parse("2V1"));
    assertEquals(PracticeMode.V2_1, PracticeMode.parse("V2_1"));
    assertEquals(PracticeMode.V2_1, PracticeMode.parse("2-1"));
    assertEquals(PracticeMode.V2_1, PracticeMode.parse("2vs1"));

    assertEquals(PracticeMode.V3_1, PracticeMode.parse("3V1"));
    assertEquals(PracticeMode.V3_1, PracticeMode.parse("V3_1"));
    assertEquals(PracticeMode.V3_1, PracticeMode.parse("3-1"));
    assertEquals(PracticeMode.V3_1, PracticeMode.parse("3vs1"));
  }

  @Test
  void testInvalidMode() {
    assertThrows(IllegalArgumentException.class, () -> PracticeMode.parse("invalid"));
    assertThrows(IllegalArgumentException.class, () -> PracticeMode.parse(null));
    assertThrows(IllegalArgumentException.class, () -> PracticeMode.parse(""));
  }
}
