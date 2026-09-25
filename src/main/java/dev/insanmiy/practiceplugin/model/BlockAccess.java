package dev.insanmiy.practiceplugin.model;

public final class BlockAccess {
  private BlockAccess() {}

  public static boolean shouldCancel(boolean participant, boolean fighting) {
    return participant && !fighting;
  }
}
