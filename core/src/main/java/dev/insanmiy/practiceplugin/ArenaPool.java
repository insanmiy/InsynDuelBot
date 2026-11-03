package dev.insanmiy.practiceplugin;

import dev.insanmiy.practiceplugin.config.Arena;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class ArenaPool {
  enum Status {
    AVAILABLE,
    OCCUPIED,
    OVERLAPPING
  }

  record Selection(Map.Entry<String, Arena> arena, List<String> errors) {
    Selection {
      errors = List.copyOf(errors);
    }
  }

  private ArenaPool() {}

  static Status status(String name, Arena candidate, Map<String, Arena> occupied) {
    if (occupied.containsKey(name)) return Status.OCCUPIED;
    if (occupied.values().stream().anyMatch(candidate::overlaps)) return Status.OVERLAPPING;
    return Status.AVAILABLE;
  }

  static Selection select(
      Iterable<String> names, Function<String, Arena> load, Map<String, Arena> occupied) {
    List<String> errors = new ArrayList<>();
    for (String name : names) {
      if (occupied.containsKey(name)) continue;
      try {
        Arena candidate = load.apply(name);
        if (status(name, candidate, occupied) == Status.AVAILABLE)
          return new Selection(Map.entry(name, candidate), errors);
      } catch (IllegalArgumentException failure) {
        errors.add(name + ": " + failure.getMessage());
      }
    }
    return new Selection(null, errors);
  }
}
