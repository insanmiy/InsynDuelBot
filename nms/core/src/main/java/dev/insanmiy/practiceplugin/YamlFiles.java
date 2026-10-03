package dev.insanmiy.practiceplugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

final class YamlFiles {
  private YamlFiles() {}

  static YamlConfiguration read(Path file) throws IOException {
    var yaml = new YamlConfiguration();
    try {
      yaml.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
    } catch (InvalidConfigurationException e) {
      throw new IOException(
          "Invalid YAML in " + file.getFileName() + "; repair it before saving.", e);
    }
    return yaml;
  }

  static YamlConfiguration readOrEmpty(Path file) throws IOException {

    try {
      return read(file);
    } catch (NoSuchFileException e) {
      return new YamlConfiguration();
    }
  }

  static void write(Path file, YamlConfiguration yaml) throws IOException {
    write(file, yaml, null);
  }

  static void write(Path file, YamlConfiguration yaml, Path backup) throws IOException {
    Path target = file.toAbsolutePath().normalize();
    if (backup != null && target.equals(backup.toAbsolutePath().normalize()))
      throw new IllegalArgumentException("Backup must differ from the target.");
    Files.createDirectories(target.getParent());

    Path temporary = Files.createTempFile(target.getParent(), target.getFileName() + "-", ".tmp");
    try {
      Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
      if (backup != null && Files.exists(target))
        Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
      try {
        Files.move(
            temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}
