package dev.insanmiy.practiceplugin;

import dev.insanmiy.practiceplugin.model.PracticeMode;

record Selection(String kit, String difficulty, PracticeMode mode, int bestOf) {
  Selection {
    if (bestOf < 1 || bestOf > 99 || bestOf % 2 == 0)
      throw new IllegalArgumentException("Best-of must be odd, from 1 to 99.");
  }
}
