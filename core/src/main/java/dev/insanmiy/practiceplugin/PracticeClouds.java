package dev.insanmiy.practiceplugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.persistence.PersistentDataType;

final class PracticeClouds {
  private record Entry(UUID owner, AreaEffectCloud cloud) {}

  private final NamespacedKey key;
  private final Map<UUID, Entry> entries = new HashMap<>();

  PracticeClouds(NamespacedKey key) {
    this.key = Objects.requireNonNull(key, "Cloud Marker Is Required");
  }

  void register(UUID owner, AreaEffectCloud cloud) {
    Objects.requireNonNull(owner, "Session Owner Is Required");
    Objects.requireNonNull(cloud, "Potion Cloud Is Required");
    cloud.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
    cloud.setPersistent(false);
    entries.put(cloud.getUniqueId(), new Entry(owner, cloud));
  }

  UUID owner(AreaEffectCloud cloud) {
    Entry entry = entries.get(cloud.getUniqueId());
    return entry == null ? null : entry.owner();
  }

  boolean marked(AreaEffectCloud cloud) {
    return cloud.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
  }

  void filterTargets(
      AreaEffectCloudApplyEvent event,
      boolean fighting,
      Function<LivingEntity, UUID> sessionOwner) {
    UUID owner = owner(event.getEntity());
    if (owner == null && !marked(event.getEntity())) {
      event.getAffectedEntities().removeIf(entity -> sessionOwner.apply(entity) != null);
      return;
    }
    event.getAffectedEntities().removeIf(
        entity -> owner == null || !fighting || !owner.equals(sessionOwner.apply(entity)));
  }

  void prune() {
    entries.values().removeIf(entry -> !entry.cloud().isValid());
  }

  void clear(UUID owner) {
    var removed = new ArrayList<AreaEffectCloud>();
    entries.values().removeIf(entry -> {
      if (!entry.owner().equals(owner)) return false;
      removed.add(entry.cloud());
      return true;
    });
    removed.forEach(AreaEffectCloud::remove);
  }

  void clearAll() {
    var removed = entries.values().stream().map(Entry::cloud).toList();
    entries.clear();
    removed.forEach(AreaEffectCloud::remove);
  }
}
