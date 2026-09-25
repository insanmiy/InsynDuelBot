package dev.insanmiy.practiceplugin.model;

public final class RepairCadence {
  private boolean active, working;
  private int nextThrow, nextBurst, shots, sessions, workTicks, lastUpdate = -1, failedImpacts;
  private int unconfirmedShots, unconfirmedSince;

  public boolean active() {
    return active;
  }

  public void reset() {
    active = working = false;
    nextThrow = nextBurst = shots = sessions = workTicks = failedImpacts = 0;
    unconfirmedShots = unconfirmedSince = 0;
    lastUpdate = -1;
  }

  public boolean wantsRepair(int tick, double damage, boolean hasBottles) {
    return hasBottles
        && damage > .15
        && (active || sessions < 2 && tick >= nextBurst && damage >= .45);
  }

  public boolean update(int tick, double worstDamageFraction, boolean safe, boolean hasBottles) {

    if (active && working && lastUpdate >= 0) workTicks += Math.max(0, tick - lastUpdate);
    lastUpdate = tick;
    if (!hasBottles
        || worstDamageFraction <= .15
        || active
            && (workTicks >= 800 || unconfirmedShots >= 12 && tick - unconfirmedSince >= 80)) {
      if (active) finish(tick);
      return false;
    }
    if (!safe) {
      stop(tick);
      return false;
    }
    if (!active && sessions < 2 && tick >= nextBurst && worstDamageFraction >= .45) {
      active = true;
      shots = 0;
      workTicks = failedImpacts = 0;
      unconfirmedShots = 0;
      sessions++;
    }
    working = active;
    return active;
  }

  public boolean ready(int tick) {
    return active && working && tick >= nextThrow;
  }

  public void thrown(int tick) {
    if (unconfirmedShots++ == 0) unconfirmedSince = tick;
    nextThrow = tick + 4;

    if (++shots >= 160) finish(tick);
  }

  public void landed(int tick, boolean repaired) {
    if (!active) return;
    unconfirmedShots = 0;
    failedImpacts = repaired ? 0 : failedImpacts + 1;
    if (failedImpacts >= 8) finish(tick);
  }

  public void stop(int tick) {

    nextThrow = Math.max(nextThrow, tick + 20);
    working = false;
  }

  private void finish(int tick) {
    active = working = false;
    nextBurst = tick + 400;
    nextThrow = tick + 20;
  }
}
