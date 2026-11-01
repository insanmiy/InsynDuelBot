package dev.insanmiy.practiceplugin.model;

public final class MiningProgress {
  private int previousTick;
  private double progress;

  public void start(int tick) {
    previousTick = tick;
    progress = 0;
  }

  public boolean advance(int tick, double progressPerTick) {
    int elapsed = Math.max(0, tick - previousTick);
    previousTick = Math.max(previousTick, tick);
    if (Double.isFinite(progressPerTick) && progressPerTick > 0) {
      progress += elapsed * progressPerTick;
    }
    return progress >= 1.0 - .000001;
  }
}
