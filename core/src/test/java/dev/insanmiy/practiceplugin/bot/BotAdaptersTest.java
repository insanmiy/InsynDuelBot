package dev.insanmiy.practiceplugin.bot;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class BotAdaptersTest {
  @Test
  void publishedReleasesHaveAdaptersWithoutLoadingServerClasses() {
    for (String version : List.of("1.20.5", "1.20.6", "1.21", "1.21.1", "1.21.3", "1.21.4",
        "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11")) {
      assertNotNull(BotAdapters.getAdapterClassName(version), version);
    }
    assertEquals(BotAdapters.getAdapterClassName("1.21"), BotAdapters.getAdapterClassName("1.21.0"));
  }

  @Test
  void unpublishedVersionsAndPrereleasesCannotSilentlyReuseAnAdapter() {
    for (String version : List.of("1.21.2", "1.21.12", "1.22", "26.1", "1.21.4-pre1", "1.21.04")) {
      assertNull(BotAdapters.getAdapterClassName(version), version);
    }
  }
}
