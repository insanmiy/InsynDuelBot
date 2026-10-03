package dev.insanmiy.practiceplugin.model;

public final class AttackGate {
  private AttackGate() {}

  public static boolean ready(double charge, boolean switchedWeapon, boolean hasWeapon) {
    return ready(charge, switchedWeapon, hasWeapon, 1.0);
  }

  public static boolean ready(
      double charge, boolean switchedWeapon, boolean hasWeapon, double minCharge) {
    double req = Double.isFinite(minCharge) && minCharge >= 0.2 ? minCharge : 1.0;
    return hasWeapon && !switchedWeapon && Double.isFinite(charge) && charge >= req;
  }
}
