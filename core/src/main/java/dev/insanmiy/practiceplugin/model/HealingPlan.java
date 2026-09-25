package dev.insanmiy.practiceplugin.model;

public final class HealingPlan {
  public enum Action {
    NONE,
    SPLASH,
    GOLDEN,
    FOOD,
    RETREAT
  }

  public record State(
      double health,
      double maximum,
      double absorption,
      int hunger,
      float saturation,
      double distance,
      double incoming,
      double threshold,
      boolean splash,
      boolean golden,
      boolean food,
      boolean regenerating,
      boolean naturalRegeneration,
      boolean escape,
      boolean covered,
      int seekingTicks) {}

  private HealingPlan() {}

  public static boolean urgent(State s) {
    return s.health() <= s.maximum() * .3
        || s.health() + s.absorption() <= s.incoming() + s.maximum() * .12;
  }

  public static Action choose(State s) {
    double fraction = s.health() / s.maximum();
    boolean healingNeeded = fraction <= Math.max(.5, s.threshold());
    if (s.splash() && s.maximum() - s.health() >= 2 && urgent(s)) return Action.SPLASH;
    boolean food =
        s.food()
            && s.hunger() < 20
            && (s.hunger() <= 18 || s.saturation() < 4 || s.naturalRegeneration() && fraction < .9);
    boolean golden =
        s.golden()
            && ((!s.regenerating() && fraction <= Math.min(.85, s.threshold() + .2))
                || !s.food() && s.hunger() <= 6);
    boolean safe =
        (s.distance() >= 5 || s.covered() && s.distance() >= 3.5)
            && s.health() + s.absorption() > s.incoming() * 1.5;
    if (safe) {
      if (golden && (fraction < .65 || s.incoming() > s.maximum() * .12 || !food))
        return Action.GOLDEN;
      if (food) return Action.FOOD;
      if (golden) return Action.GOLDEN;
      if (healingNeeded && s.splash() && !s.regenerating()) return Action.SPLASH;
    } else if (healingNeeded && s.splash()) return Action.SPLASH;
    if ((food || golden) && s.escape() && s.seekingTicks() < 24) return Action.RETREAT;
    if (golden && (!s.splash() || urgent(s))) return Action.GOLDEN;
    if (food && (s.hunger() <= 6 || s.seekingTicks() >= 24)) return Action.FOOD;
    return Action.NONE;
  }
}
