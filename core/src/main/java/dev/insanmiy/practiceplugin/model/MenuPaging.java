package dev.insanmiy.practiceplugin.model;

public final class MenuPaging {
  public static final int PAGE_SIZE = 21;

  private MenuPaging() {}

  public static int pages(int count) {
    return Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE);
  }

  public static int clamp(int page, int count) {
    return Math.max(0, Math.min(page, pages(count) - 1));
  }

  public static int slot(int index) {
    return 10 + (index / 7) * 9 + index % 7;
  }

  public static int index(int slot) {
    int row = slot / 9, column = slot % 9;
    return row >= 1 && row <= 3 && column >= 1 && column <= 7 ? (row - 1) * 7 + column - 1 : -1;
  }
}
