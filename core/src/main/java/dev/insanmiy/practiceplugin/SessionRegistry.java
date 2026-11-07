package dev.insanmiy.practiceplugin;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class SessionRegistry<T> {
  private final Map<UUID, T> owners = new LinkedHashMap<>();
  private final Map<UUID, T> bots = new HashMap<>();

  T get(UUID owner) {
    return owners.get(owner);
  }

  T participant(UUID id) {
    T owned = owners.get(id);
    return owned != null ? owned : bots.get(id);
  }

  void put(UUID owner, T session) {
    if (owners.putIfAbsent(Objects.requireNonNull(owner), Objects.requireNonNull(session)) != null)
      throw new IllegalStateException("Player Already Has A Session");
  }

  void registerBot(UUID id, T session) {
    bots.put(Objects.requireNonNull(id), session);
  }

  void unregisterBot(UUID id) {
    bots.remove(id);
  }

  boolean isBot(UUID id) {
    return bots.containsKey(id);
  }

  boolean containsKey(UUID owner) {
    return owners.containsKey(owner);
  }

  void remove(UUID owner, T session) {
    if (!owners.remove(owner, session)) return;
    bots.values().removeIf(registered -> registered == session);
  }

  Collection<T> values() {
    return Collections.unmodifiableCollection(owners.values());
  }

  int size() {
    return owners.size();
  }

  boolean isEmpty() {
    return owners.isEmpty();
  }
}
