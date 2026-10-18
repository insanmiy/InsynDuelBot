package dev.insanmiy.practiceplugin.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HealingPlanTest {

  @Test
  void testGoldenAppleChainingWhileRegenerating() {
    // When regenerating (e.g. from previous apple or totem pop) and at low health,
    // the bot must continue to eat golden apples.
    HealingPlan.State state =
        new HealingPlan.State(
            4.0,  // health
            20.0, // maximum
            4.0,  // absorption
            20,   // hunger
            10.0f,// saturation
            6.0,  // distance (safe)
            0.0,  // incoming
            0.5,  // threshold
            false,// splash
            true, // golden
            false,// food
            true, // regenerating (from totem or prior apple)
            true, // naturalRegeneration
            true, // escape
            false,// covered
            0     // seekingTicks
        );

    assertEquals(HealingPlan.Action.GOLDEN, HealingPlan.choose(state));
  }

  @Test
  void testPostTotemImmediateAppleWhenUrgent() {
    // After totem pops, health is critical (1.0 or 2.0 HP) and regenerating is true.
    // Even if opponent is close (distance = 2.5), it should immediately eat golden apple.
    HealingPlan.State state =
        new HealingPlan.State(
            2.0,  // health
            20.0, // maximum
            4.0,  // absorption
            20,   // hunger
            10.0f,// saturation
            2.5,  // distance (close)
            4.0,  // incoming
            0.5,  // threshold
            false,// splash
            true, // golden
            false,// food
            true, // regenerating
            true, // naturalRegeneration
            true, // escape
            false,// covered
            0     // seekingTicks
        );

    assertTrue(HealingPlan.urgent(state));
    assertEquals(HealingPlan.Action.GOLDEN, HealingPlan.choose(state));
  }

  @Test
  void testGoldenAppleRefreshesDepletedAbsorption() {
    // Health is 19/20 (fraction = 0.95), but absorption was stripped (0.0).
    // The bot should eat an apple to restore absorption hearts.
    HealingPlan.State state =
        new HealingPlan.State(
            19.0, // health
            20.0, // maximum
            0.0,  // absorption
            20,   // hunger
            10.0f,// saturation
            6.0,  // distance (safe)
            0.0,  // incoming
            0.5,  // threshold
            false,// splash
            true, // golden
            false,// food
            false,// regenerating
            true, // naturalRegeneration
            true, // escape
            false,// covered
            0     // seekingTicks
        );

    assertEquals(HealingPlan.Action.GOLDEN, HealingPlan.choose(state));
  }

  @Test
  void testDoesNotWasteAppleWhenFullHealthAndAbsorption() {
    // Health 20/20 and 4.0 absorption: should NOT eat golden apple.
    HealingPlan.State state =
        new HealingPlan.State(
            20.0, // health
            20.0, // maximum
            4.0,  // absorption
            20,   // hunger
            10.0f,// saturation
            6.0,  // distance
            0.0,  // incoming
            0.5,  // threshold
            false,// splash
            true, // golden
            false,// food
            false,// regenerating
            true, // naturalRegeneration
            true, // escape
            false,// covered
            0     // seekingTicks
        );

    assertEquals(HealingPlan.Action.NONE, HealingPlan.choose(state));
  }
}
