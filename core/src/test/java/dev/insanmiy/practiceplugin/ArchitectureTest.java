package dev.insanmiy.practiceplugin;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
  @Test
  void coreDoesNotImportVersionSpecificServerClasses() throws IOException {
    Pattern forbidden = Pattern.compile("(?m)^import\\s+(?:static\\s+)?(?:net\\.minecraft|org\\.bukkit\\.craftbukkit|com\\.mojang)\\.");
    try (var files = Files.walk(Path.of("src/main/java"))) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        assertFalse(forbidden.matcher(Files.readString(file)).find(), "Server Import In " + file);
      }
    }
  }
}
