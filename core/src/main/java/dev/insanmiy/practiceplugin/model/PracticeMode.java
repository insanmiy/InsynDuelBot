package dev.insanmiy.practiceplugin.model;

import java.util.Locale;

public enum PracticeMode {
  MATCH("Best Of Series"),
  DUEL("Single 1v1 Duel"),
  ENDLESS("Endless Sparring");
  private final String description;

  PracticeMode(String description) {
    this.description = description;
  }

  public String id() {
    return name().toLowerCase(Locale.ROOT);
  }

  public String description() {
    return description;
  }

  public static PracticeMode parse(String name) {
    try {
      return valueOf(name.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Mode Must Be Match, Duel, Or Endless.");
    }
  }
}
