package dev.insanmiy.practiceplugin;

import java.io.IOException;
import java.util.Objects;
import java.util.function.BiConsumer;

final class CleanupSteps {
  @FunctionalInterface
  interface Action {
    void run() throws IOException;
  }

  private final BiConsumer<String, Throwable> failures;

  CleanupSteps(BiConsumer<String, Throwable> failures) {
    this.failures = Objects.requireNonNull(failures);
  }

  void run(String description, Action action) {
    try {
      action.run();
    } catch (IOException | RuntimeException | LinkageError failure) {
      failures.accept(description, failure);
    }
  }
}
