package dev.insanmiy.practiceplugin.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MatchTest {

  @Test
  void testCountdownTransitionsToFight() {
    Match match = new Match(3, 2, 3);
    assertEquals(Match.Phase.COUNTDOWN, match.phase());
    assertEquals(3, match.remaining());

    assertFalse(match.tick());
    assertEquals(2, match.remaining());

    assertFalse(match.tick());
    assertEquals(1, match.remaining());

    assertTrue(match.tick());
    assertEquals(Match.Phase.FIGHT, match.phase());
  }

  @Test
  void testBestOfThreeSeriesWin() {
    Match match = new Match(1, 1, 3);
    assertEquals(2, match.winsRequired());

    match.tick();
    assertEquals(Match.Phase.FIGHT, match.phase());

    assertTrue(match.win(true));
    assertEquals(1, match.playerWins());
    assertEquals(0, match.botWins());
    assertEquals(Match.Phase.BREAK, match.phase());

    assertTrue(match.tick());
    assertEquals(Match.Phase.COUNTDOWN, match.phase());
    assertEquals(2, match.round());

    assertTrue(match.tick());
    assertEquals(Match.Phase.FIGHT, match.phase());

    assertTrue(match.win(true));
    assertEquals(2, match.playerWins());
    assertEquals(Match.Phase.FINISHED, match.phase());
  }

  @Test
  void testPauseFreezesTicks() {
    Match match = new Match(5, 5, 3);
    match.setPaused(true);
    assertTrue(match.paused());
    assertFalse(match.tick());
    assertEquals(5, match.remaining());
    match.setPaused(false);
    assertFalse(match.paused());
  }

  @Test
  void testInvalidConstructorParameters() {
    assertThrows(IllegalArgumentException.class, () -> new Match(0, 5, 3));
    assertThrows(IllegalArgumentException.class, () -> new Match(5, 0, 3));
    assertThrows(IllegalArgumentException.class, () -> new Match(5, 5, 4));
    assertThrows(IllegalArgumentException.class, () -> new Match(5, 5, -1));
  }
}
