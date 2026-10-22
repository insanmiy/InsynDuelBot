package dev.insanmiy.practiceplugin.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MenuPagingTest {

  @Test
  void testSlotMapping() {
    assertEquals(10, MenuPaging.slot(0));
    assertEquals(16, MenuPaging.slot(6));
    assertEquals(19, MenuPaging.slot(7));
    assertEquals(28, MenuPaging.slot(14));
    assertEquals(34, MenuPaging.slot(20));
  }

  @Test
  void testIndexMapping() {
    assertEquals(0, MenuPaging.index(10));
    assertEquals(6, MenuPaging.index(16));
    assertEquals(7, MenuPaging.index(19));
    assertEquals(14, MenuPaging.index(28));
    assertEquals(20, MenuPaging.index(34));

    assertEquals(-1, MenuPaging.index(0));
    assertEquals(-1, MenuPaging.index(9));
    assertEquals(-1, MenuPaging.index(17));
    assertEquals(-1, MenuPaging.index(45));
  }

  @Test
  void testPagesAndClamping() {
    assertEquals(1, MenuPaging.pages(0));
    assertEquals(1, MenuPaging.pages(10));
    assertEquals(1, MenuPaging.pages(21));
    assertEquals(2, MenuPaging.pages(22));
    assertEquals(3, MenuPaging.pages(43));

    assertEquals(0, MenuPaging.clamp(-1, 50));
    assertEquals(0, MenuPaging.clamp(0, 50));
    assertEquals(1, MenuPaging.clamp(1, 50));
    assertEquals(2, MenuPaging.clamp(10, 50));
  }
}
