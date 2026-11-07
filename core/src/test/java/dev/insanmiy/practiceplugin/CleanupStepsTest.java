package dev.insanmiy.practiceplugin;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CleanupStepsTest {
  @Test
  void failedJournalAndAdapterDoNotPreventInventoryOrSaveCleanup() {
    List<String> failures = new ArrayList<>();
    List<String> completed = new ArrayList<>();
    CleanupSteps cleanup = new CleanupSteps((step, failure) -> failures.add(step));
    cleanup.run("Blocks", () -> { throw new IOException("Disk Full"); });
    cleanup.run("Bot", () -> { throw new NoSuchMethodError("Adapter Mismatch"); });
    cleanup.run("Inventory", () -> completed.add("Inventory"));
    cleanup.run("Saves", () -> completed.add("Saves"));
    assertEquals(List.of("Blocks", "Bot"), failures);
    assertEquals(List.of("Inventory", "Saves"), completed);
  }
}
