package dev.insanmiy.practiceplugin.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MovementRecoveryTest {
  @Test
  void detectsStallsAtDifferentDecisionIntervals() {
    for (int interval : new int[] {1, 2, 4, 6, 10}) {
      MovementRecovery recovery = new MovementRecovery();
      assertFalse(recovery.update(0, 0, 1, 0, true));
      for (int tick = interval; tick < 25; tick += interval) {
        assertFalse(recovery.update(tick, 0, 1, 0, true));
      }
      assertTrue(recovery.update(((25 + interval - 1) / interval) * interval, 0, 1, 0, true));
    }
  }

  @Test
  void ignoresNormalTravelAndClimbing() {
    MovementRecovery recovery = new MovementRecovery();
    for (int tick = 0; tick < 100; tick += 5) {
      assertFalse(recovery.update(tick, tick * .06, 1, 0, true));
    }
    recovery.reset();
    for (int tick = 0; tick < 100; tick += 10) {
      assertFalse(recovery.update(tick, 0, 1 + tick * .1, 0, true));
    }
  }

  @Test
  void smallRepeatedMovementDoesNotHideAStall() {
    MovementRecovery recovery = new MovementRecovery();
    for (int tick = 0; tick < 25; tick++) {
      assertFalse(recovery.update(tick, tick % 2 == 0 ? .1 : -.1, 1, 0, true));
    }
    assertTrue(recovery.update(25, .1, 1, 0, true));
  }

  @Test
  void jumpingInPlaceDoesNotHideAStall() {
    MovementRecovery recovery = new MovementRecovery();
    for (int tick = 0; tick < 25; tick++) {
      double height = tick % 12 < 6 ? 1 : 2.2;
      assertFalse(recovery.update(tick, 0, height, 0, true));
    }
    assertTrue(recovery.update(25, 0, 1, 0, true));
  }

  @Test
  void pausingOrResettingStartsANewObservationWindow() {
    MovementRecovery recovery = new MovementRecovery();
    recovery.update(0, 0, 1, 0, true);
    assertFalse(recovery.update(100, 0, 1, 0, false));
    assertFalse(recovery.update(101, 0, 1, 0, true));
    assertFalse(recovery.update(125, 0, 1, 0, true));
    recovery.reset();
    assertFalse(recovery.update(200, 0, 1, 0, true));
    assertTrue(recovery.update(225, 0, 1, 0, true));
  }
}
