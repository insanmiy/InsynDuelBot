package dev.insanmiy.practiceplugin.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CombatStatsTest {

  @Test
  void testDamageAndHits() {
    CombatStats stats = new CombatStats();
    stats.hit(7.5, 100);
    stats.hit(8.0, 115);
    stats.hurt(5.0);

    assertEquals(15.5, stats.dealt());
    assertEquals(5.0, stats.taken());
    assertEquals(2, stats.hits());
    assertEquals(2, stats.longest());
    assertEquals(0, stats.combo(120)); // hurt resets current combo
  }

  @Test
  void testComboProgressionAndTimeout() {
    CombatStats stats = new CombatStats();
    stats.hit(5.0, 10);
    stats.hit(5.0, 20);
    assertEquals(2, stats.combo(25));

    // After combo timeout (> 40 ticks without hit)
    assertEquals(0, stats.combo(70));
    // Longest combo remains 2
    assertEquals(2, stats.longest());
  }

  @Test
  void testAccuracyCalculations() {
    CombatStats stats = new CombatStats();
    stats.swing();
    stats.swing();
    stats.contact(); // 1 of 2 swings connected

    assertEquals(50.0, stats.accuracy());
  }
}
