package dev.insanmiy.practiceplugin.model;

public final class HandTiming {
  private String item;
  private int readyAt, heldUntil;

  public void equip(String identity, int tick) {
    if (!identity.equals(item)) {
      item = identity;
      readyAt = tick + 3;
    }
  }

  public boolean ready(int tick) {
    return item != null && tick >= readyAt;
  }

  public boolean waiting(int tick) {
    return item != null && tick < readyAt;
  }

  public void used(int tick) {
    heldUntil = tick + 3;
  }

  public boolean settling(int tick) {
    return tick < heldUntil;
  }

  public void reset() {
    item = null;
    readyAt = heldUntil = 0;
  }
}
