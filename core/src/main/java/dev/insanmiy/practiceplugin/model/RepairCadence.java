package dev.insanmiy.practiceplugin.model;

public final class RepairCadence {
  private boolean active, working;
  private int nextThrow, nextBurst, shots, sessions, workTicks, lastUpdate = -1, failedImpacts;
  private int unconfirmedShots, unconfirmedSince, stoppedSince = -1;

  public boolean active() {
    return active;
  }

  public boolean working() {
    return active && working;
  }

  public void reset() {
    active = working = false;
    nextThrow = nextBurst = shots = sessions = workTicks = failedImpacts = 0;
    unconfirmedShots = unconfirmedSince = 0;
    lastUpdate = -1;
    stoppedSince = -1;
  }

  public boolean wantsRepair(int tick, double damage, boolean hasBottles) {
    return hasBottles
        && damage > .15
        && (active || tick >= nextBurst && damage >= .45);
  }

  public boolean update(int tick, double worstDamageFraction, boolean safe, boolean hasBottles) {
    if (active == true && working == true && lastUpdate >= 0) {
      workTicks += Math.max(0, tick - lastUpdate);
    }
    lastUpdate = tick;
    if (hasBottles == false
        || worstDamageFraction <= .15
        || active == true
            && (workTicks >= 160 || unconfirmedShots >= 8 && tick - unconfirmedSince >= 50)) {
      if (active == true) {
        finish(tick);
      }
      return false;
    }
    if (safe == false) {
      if (active == true) {
        finish(tick);
      } else {
        stop(tick);
      }
      return false;
    }
    stoppedSince = -1;
    if (active == false && tick >= nextBurst && worstDamageFraction >= .45) {
      active = true;
      shots = 0;
      workTicks = failedImpacts = 0;
      unconfirmedShots = 0;
      nextThrow = Math.max(nextThrow, tick);
      sessions++;
    }
    working = active;
    return active;
  }

  public boolean ready(int tick) {
    return active && working && tick >= nextThrow;
  }

  public void thrown(int tick) {
    if (unconfirmedShots++ == 0) {
      unconfirmedSince = tick;
    }
    nextThrow = tick + 4;

    if (++shots >= 40) {
      finish(tick);
    }
  }

  public void landed(int tick, boolean repaired) {
    if (active == false) {
      return;
    }
    unconfirmedShots = 0;
    failedImpacts = repaired ? 0 : failedImpacts + 1;
    if (failedImpacts >= 5) {
      finish(tick);
    }
  }

  public void stop(int tick) {
    if (active == true) {
      nextThrow = Math.max(nextThrow, tick + 6);
    }
    working = false;
  }

  public void cancel(int tick) {
    finish(tick);
  }

  private void finish(int tick) {
    active = working = false;
    stoppedSince = -1;
    nextBurst = tick + 240;
    nextThrow = tick + 10;
  }
}
