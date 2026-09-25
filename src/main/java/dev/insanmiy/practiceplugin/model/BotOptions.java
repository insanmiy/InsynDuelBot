package dev.insanmiy.practiceplugin.model;

public record BotOptions(
    boolean healing,
    boolean utilities,
    boolean criticals,
    boolean shields,
    boolean strafing,
    Drill drill,
    double aimError,
    int decisionTicks,
    int perceptionTicks,
    double attackCharge,
    double sprintResetChance,
    double shieldChance,
    double strafeStrength,
    double healThreshold,
    boolean counters) {
  public enum Drill {
    FULL_COMBAT,
    MELEE_ONLY,
    NO_HEALING,
    SHIELD_PRESSURE
  }

  public BotOptions(
      boolean healing,
      boolean utilities,
      boolean criticals,
      boolean shields,
      boolean strafing,
      Drill drill) {
    this(
        healing,
        utilities,
        criticals,
        shields,
        strafing,
        drill,
        -1.0,
        -1,
        -1,
        -1.0,
        -1.0,
        -1.0,
        -1.0,
        -1.0,
        true);
  }

  public static BotOptions defaults() {
    return new BotOptions(true, true, true, true, true, Drill.FULL_COMBAT);
  }

  public boolean useHealing() {
    return healing && drill != Drill.NO_HEALING;
  }

  public boolean useUtilities() {
    return utilities && drill != Drill.MELEE_ONLY && drill != Drill.SHIELD_PRESSURE;
  }
}
