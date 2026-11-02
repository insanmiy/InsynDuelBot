package dev.insanmiy.practiceplugin;

import static org.junit.jupiter.api.Assertions.*;

import dev.insanmiy.practiceplugin.model.BotOptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerSettingsTest {
  @TempDir Path folder;

  @Test
  void presetsSurviveReloadAndRestoreAbilitiesAndTuning() throws IOException {
    UUID owner = UUID.randomUUID();
    PlayerSettings settings = settings();
    settings.set(owner, "healing", false);
    settings.set(owner, "counters", false);
    settings.set(owner, "bot-aim", 2);
    settings.set(owner, "bot-reaction", 3);
    BotOptions expected = settings.options(owner);
    settings.savePreset(owner, "Sword_Practice");
    settings.set(owner, "healing", true);
    settings.set(owner, "counters", true);
    settings.set(owner, "bot-aim", 0);
    settings.set(owner, "countdown", 9);
    settings.set(owner, "hud", false);

    PlayerSettings reloaded = settings();
    reloaded.loadPreset(owner, "SWORD_PRACTICE");
    assertEquals(expected, reloaded.options(owner));
    assertEquals(9, reloaded.number(owner, "countdown"));
    assertFalse(reloaded.flag(owner, "hud"));
    assertEquals(expected, settings().options(owner));
    assertEquals(List.of("sword_practice"), reloaded.presets(owner));
  }

  @Test
  void presetsRemainPrivateToTheirOwner() throws IOException {
    PlayerSettings settings = settings();
    UUID owner = UUID.randomUUID();
    UUID other = UUID.randomUUID();
    settings.savePreset(owner, "duel");
    assertTrue(settings.presets(other).isEmpty());
    assertThrows(IllegalArgumentException.class, () -> settings.loadPreset(other, "duel"));
    assertThrows(IllegalArgumentException.class, () -> settings.deletePreset(other, "duel"));
    assertEquals(List.of("duel"), settings.presets(owner));
  }

  @Test
  void savingExistingNameReplacesSnapshotWithoutLinkingCurrentSettings() throws IOException {
    PlayerSettings settings = settings();
    UUID owner = UUID.randomUUID();
    settings.savePreset(owner, "duel");
    settings.set(owner, "bot-aim", 3);
    settings.savePreset(owner, "duel");
    settings.set(owner, "bot-aim", 0);
    settings.loadPreset(owner, "duel");
    assertEquals(3, settings.botSetting(owner, "bot-aim"));
    settings.set(owner, "bot-aim", 5);
    settings.loadPreset(owner, "duel");
    assertEquals(3, settings.botSetting(owner, "bot-aim"));
    assertEquals(List.of("duel"), settings.presets(owner));
  }

  @Test
  void defaultPresetClearsOverridesAndDeletionPersists() throws IOException {
    PlayerSettings settings = settings();
    UUID owner = UUID.randomUUID();
    settings.savePreset(owner, "defaults");
    settings.set(owner, "bot-aim", 3);
    settings.set(owner, "utilities", false);
    settings.loadPreset(owner, "defaults");
    assertFalse(settings.hasBotOverrides(owner));
    settings.deletePreset(owner, "defaults");
    assertTrue(settings().presets(owner).isEmpty());
  }

  @Test
  void rejectsInvalidNamesAndLimitsNewPresetsButAllowsReplacement() throws IOException {
    PlayerSettings settings = settings();
    UUID owner = UUID.randomUUID();
    for (String name : List.of("", "a.b", "../other", "two words", "x".repeat(33))) {
      assertThrows(IllegalArgumentException.class, () -> settings.savePreset(owner, name));
    }
    for (int index = 0; index < PlayerSettings.MAX_PRESETS; index++) {
      settings.savePreset(owner, "preset" + index);
    }
    assertThrows(IllegalArgumentException.class, () -> settings.savePreset(owner, "extra"));
    settings.savePreset(owner, "preset0");
    assertEquals(PlayerSettings.MAX_PRESETS, settings().presets(owner).size());
  }

  @Test
  void failedWritesDoNotChangeCurrentSettingsOrSavedPresets() throws IOException {
    PlayerSettings settings = settings();
    UUID owner = UUID.randomUUID();
    settings.savePreset(owner, "defaults");
    settings.set(owner, "bot-aim", 3);
    Path file = folder.resolve("settings.yml");
    Files.delete(file);
    Files.createDirectory(file);
    Files.writeString(file.resolve("block-replacement"), "Blocked");

    assertThrows(IOException.class, () -> settings.loadPreset(owner, "defaults"));
    assertEquals(3, settings.botSetting(owner, "bot-aim"));
    assertThrows(IOException.class, () -> settings.savePreset(owner, "new"));
    assertEquals(List.of("defaults"), settings.presets(owner));
    assertThrows(IOException.class, () -> settings.savePreset(owner, "defaults"));
    assertThrows(IOException.class, () -> settings.deletePreset(owner, "defaults"));
    assertEquals(List.of("defaults"), settings.presets(owner));

    Files.delete(file.resolve("block-replacement"));
    Files.delete(file);
    settings.loadPreset(owner, "defaults");
    assertFalse(settings.hasBotOverrides(owner));
  }

  private PlayerSettings settings() {
    return new PlayerSettings(folder.resolve("settings.yml").toFile(), new YamlConfiguration());
  }
}
