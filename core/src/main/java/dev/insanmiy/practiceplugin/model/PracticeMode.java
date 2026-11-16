package dev.insanmiy.practiceplugin.model;

import dev.insanmiy.practiceplugin.Messages;

import java.util.Locale;

public enum PracticeMode {
  MATCH("match", "text.practice-mode.best-of-series", 1),
  DUEL("duel", "text.practice-mode.single-1v1-duel", 1),
  ENDLESS("endless", "text.practice-mode.endless-sparring", 1),
  V2_1("2v1", "text.practice-mode.2v1-outnumbered-series", 2),
  V3_1("3v1", "text.practice-mode.3v1-outnumbered-series", 3);

  private final String id;
  private final String description;
  private final int botCount;

  PracticeMode(String id, String description, int botCount) {
    this.id = id;
    this.description = description;
    this.botCount = botCount;
  }

  public String id() {
    return id;
  }

  public String description() {
    return Messages.text(description);
  }

  public int botCount() {
    return botCount;
  }

  public boolean isOutnumbered() {
    return botCount > 1;
  }

  public static PracticeMode parse(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException(Messages.text("text.practice-mode.mode-must-be-match-duel-endless"));
    }
    String normalized = name.trim().toLowerCase(Locale.ROOT);
    for (PracticeMode mode : values()) {
      if (mode.id.equalsIgnoreCase(normalized) || mode.name().equalsIgnoreCase(normalized)) {
        return mode;
      }
    }
    if (normalized.equals("2-1") || normalized.equals("2_1") || normalized.equals("2vs1")) {
      return V2_1;
    }
    if (normalized.equals("3-1") || normalized.equals("3_1") || normalized.equals("3vs1")) {
      return V3_1;
    }
    throw new IllegalArgumentException(Messages.text("text.practice-mode.mode-must-be-match-duel-endless"));
  }
}
