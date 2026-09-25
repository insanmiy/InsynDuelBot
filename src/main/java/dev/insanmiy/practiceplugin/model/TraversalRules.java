package dev.insanmiy.practiceplugin.model;

public final class TraversalRules {
  private TraversalRules() {}

  public static boolean supported(
      boolean solidFloor, boolean hazardousFloor, boolean waterAtFeet, boolean waterBelow) {
    return !hazardousFloor && (solidFloor || waterAtFeet || waterBelow);
  }
}
