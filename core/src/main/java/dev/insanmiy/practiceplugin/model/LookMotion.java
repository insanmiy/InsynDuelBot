package dev.insanmiy.practiceplugin.model;

public final class LookMotion {
  private LookMotion() {}

  public static float delta(float from, float to) {
    return ((to - from) % 360 + 540) % 360 - 180;
  }

  public static float turn(float from, float to, float maximum) {
    return from + Math.max(-maximum, Math.min(maximum, delta(from, to)));
  }

  public static float glance(int tick, double distance) {

    int phase = Math.floorMod(tick, 160);
    return distance > 6 && phase < 24 ? (float) (12 * Math.sin(Math.PI * phase / 24)) : 0;
  }
}
