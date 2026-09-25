package dev.insanmiy.practiceplugin.model;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public record KitLayout(Map<Integer, Integer> slots, int heldSlot) {
  public KitLayout {
    slots = slots == null ? Collections.emptyMap() : Collections.unmodifiableMap(new TreeMap<>(slots));
  }

  public KitLayout(Map<Integer, Integer> slots) {
    this(slots, 0);
  }
}
