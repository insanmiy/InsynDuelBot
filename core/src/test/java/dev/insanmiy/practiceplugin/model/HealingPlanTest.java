package dev.insanmiy.practiceplugin.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HealingPlanTest {

  @Test
  void testGoldenAppleChainingWhileRegenerating() {
    HealingPlan.State state =
        new HealingPlan.State(
            4.0, 20.0, 4.0, 20, 10.0f, 6.0, 0.0, 0.5,
            false, true, false, true, true, true, false, 0);

    assertEquals(HealingPlan.Action.GOLDEN, HealingPlan.choose(state));
  }

  @Test
  void testPostTotemImmediateAppleWhenUrgent() {
    HealingPlan.State state =
        new HealingPlan.State(
            2.0, 20.0, 4.0, 20, 10.0f, 2.5, 4.0, 0.5,
            false, true, false, true, true, true, false, 0);

    assertTrue(HealingPlan.urgent(state));
    assertEquals(HealingPlan.Action.GOLDEN, HealingPlan.choose(state));
  }

  @Test
  void testGoldenAppleRefreshesDepletedAbsorption() {
    HealingPlan.State state =
        new HealingPlan.State(
            19.0, 20.0, 0.0, 20, 10.0f, 6.0, 0.0, 0.5,
            false, true, false, false, true, true, false, 0);

    assertEquals(HealingPlan.Action.GOLDEN, HealingPlan.choose(state));
  }

  @Test
  void testDoesNotWasteAppleWhenFullHealthAndAbsorption() {
    HealingPlan.State state =
        new HealingPlan.State(
            20.0, 20.0, 4.0, 20, 10.0f, 6.0, 0.0, 0.5,
            false, true, false, false, true, true, false, 0);

    assertEquals(HealingPlan.Action.NONE, HealingPlan.choose(state));
  }

  @Test
  void testDoesNotEatAppleInMeleeWhenHitTwiceAtHighHealth() {
    HealingPlan.State state =
        new HealingPlan.State(
            14.0, 20.0, 0.0, 20, 10.0f, 2.5, 24.0, 0.5,
            false, true, false, false, true, true, false, 0);

    assertFalse(HealingPlan.urgent(state));
    assertNotEquals(HealingPlan.Action.GOLDEN, HealingPlan.choose(state));
  }

  @Test
  void testDoesNotEatAppleWhenCorneredInMeleeAtNonCriticalHealth() {
    HealingPlan.State state =
        new HealingPlan.State(
            12.0, 20.0, 0.0, 20, 10.0f, 2.5, 20.0, 0.5,
            false, true, false, false, true, false, false, 0);

    assertFalse(HealingPlan.urgent(state));
    assertEquals(HealingPlan.Action.NONE, HealingPlan.choose(state));
  }

  @Test
  void testUrgentOnlyWhenCriticalOrImminentlyLethal() {
    HealingPlan.State highHealth =
        new HealingPlan.State(
            16.0, 20.0, 0.0, 20, 10.0f, 2.5, 30.0, 0.5,
            false, true, false, false, true, true, false, 0);
    assertFalse(HealingPlan.urgent(highHealth));

    HealingPlan.State criticalHealth =
        new HealingPlan.State(
            5.0, 20.0, 0.0, 20, 10.0f, 2.5, 10.0, 0.5,
            false, true, false, false, true, true, false, 0);
    assertTrue(HealingPlan.urgent(criticalHealth));
  }
}
