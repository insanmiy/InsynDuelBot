package dev.insanmiy.practiceplugin.model;

public final class AttackSequence {
  private AttackSequence() {}

  public static void execute(Runnable attack, Runnable animate, Runnable resetCooldown) {
    try {
      attack.run();
      animate.run();
    } finally {

      resetCooldown.run();
    }
  }
}
