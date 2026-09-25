package dev.insanmiy.practiceplugin.model;

public final class ReplacementHistory {
  private ReplacementHistory() {}

  public static String baseline(String replaced, String previousWeb, String temporaryWater) {
    if (previousWeb != null) return previousWeb;
    return temporaryWater != null ? temporaryWater : replaced;
  }
}
