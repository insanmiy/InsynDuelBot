package dev.insanmiy.practiceplugin.model;

public final class MovementRecovery {
  private int anchorTick = -1;
  private double anchorX, anchorY, anchorZ;

  public void reset() {
    anchorTick = -1;
  }

  public boolean update(int tick, double positionX, double positionY, double positionZ, boolean moving) {
    if (!moving) {
      reset();
      return false;
    }
    double deltaX = positionX - anchorX;
    double deltaY = positionY - anchorY;
    double deltaZ = positionZ - anchorZ;
    // Ordinary Jumps In Place Do Not Count As Navigation Progress
    if (anchorTick < 0 || tick < anchorTick
        || deltaX * deltaX + deltaZ * deltaZ >= .25 || Math.abs(deltaY) >= 2.0) {
      anchorTick = tick;
      anchorX = positionX;
      anchorY = positionY;
      anchorZ = positionZ;
      return false;
    }
    if (tick - anchorTick < 25) return false;
    reset();
    return true;
  }
}
