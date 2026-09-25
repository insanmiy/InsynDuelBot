package dev.insanmiy.practiceplugin.model;

public final class WebCadence {
  private int nextAttempt;

  public void reset() {
    nextAttempt = 0;
  }

  public boolean ready(int tick) {
    return tick >= nextAttempt;
  }

  public boolean shouldPlace(int tick, double distance, boolean nearbyWeb, boolean opportunity) {
    return ready(tick) && distance > 1.8 && distance < 3.5 && !nearbyWeb && opportunity;
  }

  public void attempted(int tick, boolean placed) {
    nextAttempt = tick + (placed ? 160 : 40);
  }
}
