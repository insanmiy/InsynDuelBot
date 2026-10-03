package dev.insanmiy.practiceplugin.model;

public final class CriticalWindow {
  public enum Action {
    NONE,
    JUMP,
    WAIT,
    STRIKE
  }

  private long jumped = -1000, hurt = -1000, nextJump;

  public void reset() {
    jumped = hurt = -1000;
    nextJump = 0;
  }

  public void opponentHit(long tick) {
    hurt = tick;
  }

  public void attacked() {
    jumped = hurt = -1000;
  }

  public boolean tracking(long tick) {
    return tick - jumped <= 20 || tick - hurt <= 20;
  }

  public Action decide(
      long tick,
      boolean grounded,
      boolean falling,
      boolean eligible,
      boolean charged,
      boolean inRange,
      boolean chooseJump) {
    if (!eligible) {
      attacked();
      return Action.NONE;
    }
    if (!grounded && tracking(tick))
      return falling && charged && inRange ? Action.STRIKE : Action.WAIT;

    if (grounded && tick - jumped <= 1) return Action.WAIT;
    if (grounded && tick - jumped > 1) jumped = -1000;
    if (grounded && charged && inRange && tick >= nextJump && chooseJump) {
      jumped = tick;
      nextJump = tick + 14;
      return Action.JUMP;
    }
    return Action.NONE;
  }
}
