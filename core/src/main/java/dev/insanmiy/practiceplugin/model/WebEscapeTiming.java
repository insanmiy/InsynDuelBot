package dev.insanmiy.practiceplugin.model;

public final class WebEscapeTiming {
  private static final int REACTION_TICKS = 12;
  private int trappedSince = -1;

  public void observe(int tick, boolean trapped) {
    if (trapped == false) {
      reset();
      return;
    }
    if (trappedSince < 0) {
      trappedSince = tick;
    }
  }

  public boolean ready(int tick) {
    return trappedSince >= 0 && tick - trappedSince >= REACTION_TICKS;
  }

  public void reset() {
    trappedSince = -1;
  }
}
