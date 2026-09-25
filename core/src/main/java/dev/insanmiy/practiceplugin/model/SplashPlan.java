package dev.insanmiy.practiceplugin.model;

public final class SplashPlan {
  private SplashPlan() {}

  public static boolean turnComplete(int ticks) {
    return ticks >= 3;
  }

  public static boolean shouldThrow(
      double distance, double healthFraction, int preparingTicks, boolean escapeAvailable) {
    return distance >= 4.5 || healthFraction <= .3 || preparingTicks >= 24 || !escapeAvailable;
  }
}
