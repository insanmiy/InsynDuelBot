package dev.insanmiy.practiceplugin.model;

public record BotOptions(
    boolean healing,
    boolean utilities,
    boolean criticals,
    boolean shields,
    boolean strafing,
    double aimError,
    int decisionTicks,
    int perceptionTicks,
    double attackCharge,
    double sprintResetChance,
    double shieldChance,
    double strafeStrength,
    double healThreshold,
    boolean counters) {

  public BotOptions(
      boolean healing,
      boolean utilities,
      boolean criticals,
      boolean shields,
      boolean strafing) {
    this(
        healing,
        utilities,
        criticals,
        shields,
        strafing,
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
    return new BotOptions(true, true, true, true, true);
  }

  public boolean useHealing() {
    return healing;
  }

  public boolean useUtilities() {
    return utilities;
  }
}
