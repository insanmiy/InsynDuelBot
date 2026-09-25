package dev.insanmiy.practiceplugin.model;

public final class Match {
  public enum Phase {
    COUNTDOWN,
    FIGHT,
    BREAK,
    FINISHED
  }

  private Phase phase = Phase.COUNTDOWN;
  private int remaining, playerWins, botWins, round = 1;
  private final int countdown, intermission;
  private final int winsRequired;
  private boolean paused;

  public Match(int countdown, int intermission) {
    this(countdown, intermission, 5);
  }

  public Match(int countdown, int intermission, int bestOf) {
    if (countdown < 1 || intermission < 1)
      throw new IllegalArgumentException("Timings must be positive");
    this.countdown = countdown;
    this.intermission = intermission;
    if (bestOf < 0 || bestOf > 99 || bestOf != 0 && bestOf % 2 == 0)
      throw new IllegalArgumentException("Best-of must be odd (1-99), or 0 for endless.");
    winsRequired = bestOf == 0 ? 0 : bestOf / 2 + 1;
    remaining = countdown;
  }

  public boolean tick() {
    if (paused || phase == Phase.FIGHT || phase == Phase.FINISHED) return false;
    if (--remaining > 0) return false;
    if (phase == Phase.COUNTDOWN) phase = Phase.FIGHT;
    else {
      phase = Phase.COUNTDOWN;
      remaining = countdown;
      round++;
    }
    return true;
  }

  public boolean win(boolean player) {
    if (paused || phase != Phase.FIGHT) return false;
    if (player) playerWins++;
    else botWins++;
    phase =
        winsRequired > 0 && (playerWins >= winsRequired || botWins >= winsRequired)
            ? Phase.FINISHED
            : Phase.BREAK;
    remaining = intermission;
    return true;
  }

  public Phase phase() {
    return phase;
  }

  public void restartSeries() {
    if (phase != Phase.FINISHED) throw new IllegalStateException("Series is not finished");
    playerWins = 0;
    botWins = 0;
    round = 1;
    phase = Phase.COUNTDOWN;
    remaining = countdown;
  }

  public boolean paused() {
    return paused;
  }

  public void setPaused(boolean paused) {
    this.paused = paused;
  }

  public int winsRequired() {
    return winsRequired;
  }

  public int remaining() {
    return remaining;
  }

  public int playerWins() {
    return playerWins;
  }

  public int botWins() {
    return botWins;
  }

  public int round() {
    return round;
  }
}
