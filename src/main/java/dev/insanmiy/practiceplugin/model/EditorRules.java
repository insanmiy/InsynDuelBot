package dev.insanmiy.practiceplugin.model;

public final class EditorRules {
  private EditorRules() {}

  public static int page(int requested, int count) {
    return Math.max(0, Math.min(requested, Math.max(0, (count - 1) / 45)));
  }

  public static int amount(int requested, int maximum) {
    return Math.max(1, Math.min(maximum, requested));
  }

  public static String appendName(String current, char character) {
    if (current.length() >= 32 || "abcdefghijklmnopqrstuvwxyz0123456789_-".indexOf(character) < 0)
      return current;
    return current + character;
  }

  public static boolean equipmentFits(int slot, String material) {
    return slot >= 0 && slot <= 40;
  }
}
