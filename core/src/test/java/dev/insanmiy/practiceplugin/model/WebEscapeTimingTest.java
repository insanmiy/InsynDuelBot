package dev.insanmiy.practiceplugin.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WebEscapeTimingTest {
  @Test
  void waitsForReactionBeforeUsingWater() {
    WebEscapeTiming timing = new WebEscapeTiming();
    assertFalse(timing.ready(100));
    timing.observe(100, true);
    assertFalse(timing.ready(100));
    assertFalse(timing.ready(111));
    assertTrue(timing.ready(112));
  }

  @Test
  void repeatedChecksDoNotRestartOrShortenReaction() {
    WebEscapeTiming timing = new WebEscapeTiming();
    timing.observe(10, true);
    timing.observe(10, true);
    timing.observe(18, true);
    assertFalse(timing.ready(21));
    timing.observe(22, true);
    assertTrue(timing.ready(22));
  }

  @Test
  void leavingWebRequiresFreshReactionOnReentry() {
    WebEscapeTiming timing = new WebEscapeTiming();
    timing.observe(10, true);
    assertTrue(timing.ready(22));
    timing.observe(23, false);
    assertFalse(timing.ready(23));
    timing.observe(24, true);
    assertFalse(timing.ready(35));
    assertTrue(timing.ready(36));
  }

  @Test
  void resetClearsReactionBetweenRounds() {
    WebEscapeTiming timing = new WebEscapeTiming();
    timing.observe(100, true);
    timing.reset();
    assertFalse(timing.ready(200));
    timing.observe(0, true);
    assertFalse(timing.ready(11));
    assertTrue(timing.ready(12));
  }
}
