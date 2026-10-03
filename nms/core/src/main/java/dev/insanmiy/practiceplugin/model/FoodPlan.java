package dev.insanmiy.practiceplugin.model;

public final class FoodPlan {
  private FoodPlan() {}

  public static boolean eatOrdinary(
      int hunger, float saturation, double healthFraction, double distance) {
    return hunger < 20
        && (hunger <= 18 || saturation < 4 || healthFraction < .9)
        && (distance > 3 || hunger <= 6);
  }

  public static boolean eatGolden(
      double healthFraction, double healThreshold, boolean regenerating, double distance) {
    return !regenerating
        && healthFraction <= Math.min(.85, healThreshold + .15)
        && (distance >= 3.5 || healthFraction <= .3);
  }
}
