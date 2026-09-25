package dev.insanmiy.practiceplugin.model;

public final class CombatStats {
  private int swings, contacts, hits, combo, longest, apples;
  private long lastHit = -1000;
  private double dealt, taken;
  private int utility;

  public void utility() {
    utility++;
  }

  public int utilityUses() {
    return utility;
  }

  public void swing() {
    swings++;
  }

  public void contact() {
    contacts++;
  }

  public void hit(double damage, long tick) {
    if (damage <= 0) return;
    combo = tick - lastHit > 40 ? 1 : combo + 1;
    lastHit = tick;
    longest = Math.max(longest, combo);
    hits++;
    dealt += damage;
  }

  public void hurt(double damage) {
    if (damage > 0) {
      taken += damage;
      combo = 0;
    }
  }

  public void apple() {
    apples++;
  }

  public int combo(long tick) {
    return tick - lastHit > 40 ? 0 : combo;
  }

  public double accuracy() {
    return swings == 0 ? 0 : Math.min(100, contacts * 100.0 / swings);
  }

  public int hits() {
    return hits;
  }

  public int longest() {
    return longest;
  }

  public double dealt() {
    return dealt;
  }

  public double taken() {
    return taken;
  }

  public int apples() {
    return apples;
  }

  public String summary(long ticks) {
    return String.format(
        java.util.Locale.ROOT,
        "%d hits | %.1f/%.1f damage dealt/taken | %.0f%% contact accuracy | %d best combo | %d"
            + " apples | %d projectiles | %.1fs",
        hits,
        dealt,
        taken,
        accuracy(),
        longest,
        apples,
        utility,
        ticks / 20.0);
  }
}
