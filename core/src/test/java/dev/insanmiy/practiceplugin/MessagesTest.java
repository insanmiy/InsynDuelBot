package dev.insanmiy.practiceplugin;

import static org.junit.jupiter.api.Assertions.*;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

class MessagesTest {

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
