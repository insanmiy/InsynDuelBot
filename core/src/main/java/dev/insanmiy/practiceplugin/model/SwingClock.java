package dev.insanmiy.practiceplugin.model;

public final class SwingClock {
  private long lastAttempt = Long.MIN_VALUE / 2;

  public void reset(long tick) {
    lastAttempt = tick;
  }

  public void attempted(long tick) {
    lastAttempt = tick;
  }

  public boolean ready(long tick, double attackSpeed, double vanillaCharge) {
    return ready(tick, attackSpeed, vanillaCharge, 1.0);
  }

  public boolean ready(long tick, double attackSpeed, double vanillaCharge, double minCharge) {
    if (!Double.isFinite(attackSpeed) || attackSpeed <= 0 || !Double.isFinite(vanillaCharge))
      return false;
    double req = Double.isFinite(minCharge) && minCharge >= 0.2 ? minCharge : 1.0;
    long interval = Math.max(1, (long) Math.ceil((20.0 / attackSpeed) * req));
    return tick - lastAttempt >= interval && vanillaCharge >= req;
  }
}
