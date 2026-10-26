package dev.insanmiy.practiceplugin;

import static org.junit.jupiter.api.Assertions.*;

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
  }
}
