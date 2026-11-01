package dev.insanmiy.practiceplugin.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MiningProgressTest {
  @Test
  void waitsForFullMiningTimeAndDoesNotDoubleCountSameTick() {
    MiningProgress progress = new MiningProgress();
    progress.start(10);
    assertFalse(progress.advance(10, .25));
    assertFalse(progress.advance(13, .25));
    assertFalse(progress.advance(13, .25));
    assertTrue(progress.advance(14, .25));
  }

  @Test
  void accountsForSlowerMiningWhileAirborne() {
    MiningProgress progress = new MiningProgress();
    progress.start(0);
    assertFalse(progress.advance(2, .2));
    assertFalse(progress.advance(12, .04));
    assertTrue(progress.advance(17, .04));
  }

  @Test
  void changingBlocksRestartsProgress() {
    MiningProgress progress = new MiningProgress();
    progress.start(0);
    assertFalse(progress.advance(3, .25));
    progress.start(3);
    assertFalse(progress.advance(6, .25));
    assertTrue(progress.advance(7, .25));
  }

  @Test
  void invalidOrUnbreakableProgressCannotFinishMining() {
    MiningProgress progress = new MiningProgress();
    progress.start(0);
    assertFalse(progress.advance(20, 0));
    assertFalse(progress.advance(40, Double.NaN));
    assertFalse(progress.advance(60, Double.POSITIVE_INFINITY));
    assertFalse(progress.advance(80, -1));
    assertFalse(progress.advance(81, .5));
    assertTrue(progress.advance(82, .5));
  }
}
