package dev.insanmiy.practiceplugin;

import static org.junit.jupiter.api.Assertions.*;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

class MessagesTest {

  private static org.bukkit.configuration.file.YamlConfiguration defaults() throws java.io.IOException {
    try (var stream = java.util.Objects.requireNonNull(MessagesTest.class.getResourceAsStream("/messages.yml"));
        var reader = new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
      return org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(reader);
    }
  }

  @AfterEach
  void restoreDefaultText() throws java.io.IOException {
    Messages.configureText(defaults());
  }

  @Test
  void reloadedTextUpdatesModeAndCachedSettingDefinitions() throws ReflectiveOperationException, java.io.IOException {
    var configuration = defaults();
    Messages.configureText(configuration);
    var definitions = SettingsMenu.class.getDeclaredField("BOT_SETTINGS");
    definitions.setAccessible(true);
    var settings = (java.util.Map<?, ?>) definitions.get(null);
    Object timing = settings.get(10);
    var displayName = timing.getClass().getDeclaredMethod("displayName");
    displayName.setAccessible(true);
    assertEquals("Attack timing", displayName.invoke(timing));
    assertEquals("Best of series", dev.insanmiy.practiceplugin.model.PracticeMode.MATCH.description());

    configuration.set("text.settings-menu.attack-timing", "Custom attack label");
    configuration.set("text.practice-mode.best-of-series", "Custom mode label");
    Messages.configureText(configuration);
    assertEquals("Custom attack label", displayName.invoke(timing));
    assertEquals("Custom mode label", dev.insanmiy.practiceplugin.model.PracticeMode.MATCH.description());
  }

  @Test
  void textKeepsCustomCaseSpacingPlaceholdersAndDisabledLabels() throws java.io.IOException {
    var configuration = defaults();
    configuration.set("hud.single", "  %prefix%%server% CUSTOM %health%  ");
    configuration.set("prefix", "Prefix ");
    configuration.set("placeholders.%server%", "Server");
    configuration.set("titles.victory", "");
    Messages.configureText(configuration);
    assertEquals("  Prefix Server CUSTOM 20  ", Messages.text("hud.single", "%health%", "20"));
    assertEquals("", Messages.text("titles.victory"));
    assertEquals("None", Messages.text("text.practice-placeholder-expansion.none"));
  }

  @Test
  void generatedNamesCanBeOverriddenWithoutChangingTheirIdentifiers() throws java.io.IOException {
    var configuration = defaults();
    configuration.set("names.diamond_sword", "Training blade");
    Messages.configureText(configuration);
    assertEquals("Training blade", Messages.displayName("DIAMOND_SWORD"));
    assertEquals("Training blade", Messages.displayName("diamond-sword"));
    assertEquals("Iron axe", Messages.displayName("IRON_AXE"));
  }

  @Test
  void menuComponentsSupportConfiguredColorsAndMiniMessage() {
    Component component = Messages.component("<red>Custom label</red>", NamedTextColor.AQUA);
    assertTrue(component.toString().contains("Custom label"));
    assertEquals(Messages.toComponent("<red>Custom label</red>"), component);
    assertEquals(Messages.toComponent("&aCustom label"), Messages.component("&aCustom label", NamedTextColor.GRAY));
  }

  @Test
  void everyReferencedTextEntryExistsInTheDefaultConfiguration() throws java.io.IOException {
    var configuration = defaults();
    var keys = java.util.regex.Pattern.compile("\"((?:text|hud|titles)\\.[a-z0-9.-]+)\"");
    for (var root : java.util.List.of(java.nio.file.Path.of("src/main/java"), java.nio.file.Path.of("../nms/shared/src/main/java"))) {
      try (var files = java.nio.file.Files.walk(root)) {
        for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
          var matcher = keys.matcher(java.nio.file.Files.readString(file));
          while (matcher.find()) {
            String key = matcher.group(1);
            if (key.endsWith(".") || key.endsWith("-")) continue;
            assertTrue(configuration.isString(key), file + ": " + key);
          }
        }
      }
    }
  }

  @Test
  void testLegacyColors() {
    String formatted = Messages.color("&aHello &bWorld");
    assertEquals("\u00A7aHello \u00A7bWorld", formatted);
  }

  @Test
  void testHexColors() {
    String hex1 = Messages.color("&#55FF55Green");
    assertTrue(hex1.equalsIgnoreCase("\u00A7x\u00A75\u00A75\u00A7f\u00A7f\u00A75\u00A75Green"));

    String hex2 = Messages.color("<#FF5555>Red");
    assertTrue(hex2.equalsIgnoreCase("\u00A7x\u00A7f\u00A7f\u00A75\u00A75\u00A75\u00A75Red"));
  }

  @Test
  void testEmptyAndNull() {
    assertEquals("", Messages.color(""));
    assertEquals("", Messages.color(null));
    assertEquals(Component.empty(), Messages.toComponent(""));
    assertEquals(Component.empty(), Messages.toComponent(null));
  }

  @Test
  void testMiniMessageComponent() {
    Component comp = Messages.toComponent("<red>Fight!</red>");
    assertNotNull(comp);
    assertFalse(comp.equals(Component.empty()));
  }

  @Test
  void testMixedLegacyAndMiniMessage() {
    Component comp = Messages.toComponent("&c&lFight! <gray>/practice Stop To Finish.</gray>");
    assertNotNull(comp);
    assertFalse(comp.equals(Component.empty()));
  }

  @Test
  void testInvalidTagFallback() {
    // Should not throw, should fall back to legacy deserializer
    Component comp = Messages.toComponent("&cInvalid <not_a_valid_tag> Text");
    assertNotNull(comp);
    assertFalse(comp.equals(Component.empty()));
  }

  @Test
  void testOutnumberedMessagesFormatting() {
    String eliminated = Messages.color("&9InsynPracticeBot &r&eA Bot Was Eliminated! &7(&a2&7 Remaining)");
    assertNotNull(eliminated);
    assertTrue(eliminated.contains("\u00A7eA Bot Was Eliminated!"));

    Component finishComp = Messages.toComponent("&9InsynPracticeBot &r&62v1 Match Finished! You Won The 2v1 Match 3-1!");
    assertNotNull(finishComp);
    assertFalse(finishComp.equals(Component.empty()));
  }
}
