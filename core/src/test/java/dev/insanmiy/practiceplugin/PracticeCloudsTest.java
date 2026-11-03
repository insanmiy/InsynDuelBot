package dev.insanmiy.practiceplugin;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

class PracticeCloudsTest {
  private final NamespacedKey marker = NamespacedKey.fromString("practiceplugin:practice_gear");
  private final PracticeClouds clouds = new PracticeClouds(marker);

  @Test
  void cloudOnlyAffectsItsOwnPlayerAndBotWhileOtherSessionsContinue() {
    UUID firstOwner = UUID.randomUUID();
    UUID secondOwner = UUID.randomUUID();
    Cloud first = new Cloud();
    Cloud second = new Cloud();
    clouds.register(firstOwner, first.entity);
    clouds.register(secondOwner, second.entity);
    LivingEntity firstPlayer = fighter();
    LivingEntity firstBot = fighter();
    LivingEntity secondPlayer = fighter();
    LivingEntity secondBot = fighter();
    LivingEntity outsider = fighter();
    Map<LivingEntity, UUID> owners = Map.of(
        firstPlayer, firstOwner, firstBot, firstOwner,
        secondPlayer, secondOwner, secondBot, secondOwner);
    List<LivingEntity> everyone = List.of(firstPlayer, firstBot, secondPlayer, secondBot, outsider);

    var firstEvent = event(first, everyone);
    clouds.filterTargets(firstEvent, true, owners::get);
    assertEquals(List.of(firstPlayer, firstBot), firstEvent.getAffectedEntities());
    var secondEvent = event(second, everyone);
    clouds.filterTargets(secondEvent, true, owners::get);
    assertEquals(List.of(secondPlayer, secondBot), secondEvent.getAffectedEntities());
  }

  @Test
  void closingOneSessionKeepsTheOtherSessionsClouds() {
    UUID firstOwner = UUID.randomUUID();
    UUID secondOwner = UUID.randomUUID();
    Cloud first = new Cloud();
    Cloud second = new Cloud();
    Cloud unrelated = new Cloud();
    clouds.register(firstOwner, first.entity);
    clouds.register(secondOwner, second.entity);

    clouds.clear(firstOwner);

    assertFalse(first.valid);
    assertNull(clouds.owner(first.entity));
    assertTrue(second.valid);
    assertEquals(secondOwner, clouds.owner(second.entity));
    assertTrue(unrelated.valid);
    assertFalse(first.persistent);
    assertFalse(second.persistent);
  }

  @Test
  void anOldCloudCannotAffectAnOwnersNextMatch() {
    UUID owner = UUID.randomUUID();
    Cloud old = new Cloud();
    clouds.register(owner, old.entity);
    clouds.clear(owner);
    Cloud current = new Cloud();
    clouds.register(owner, current.entity);
    LivingEntity player = fighter();

    var oldEvent = event(old, List.of(player));
    clouds.filterTargets(oldEvent, true, entity -> owner);
    assertTrue(oldEvent.getAffectedEntities().isEmpty());
    var currentEvent = event(current, List.of(player));
    clouds.filterTargets(currentEvent, true, entity -> owner);
    assertEquals(List.of(player), currentEvent.getAffectedEntities());
  }

  @Test
  void ordinaryCloudsAffectOutsidersButNotPracticeParticipants() {
    Cloud ordinary = new Cloud();
    LivingEntity player = fighter();
    LivingEntity outsider = fighter();
    UUID owner = UUID.randomUUID();
    var event = event(ordinary, List.of(player, outsider));

    clouds.filterTargets(event, false, entity -> entity == player ? owner : null);

    assertEquals(List.of(outsider), event.getAffectedEntities());
  }

  @Test
  void pausedAndOrphanedPracticeCloudsCannotAffectAnyone() {
    Cloud cloud = new Cloud();
    UUID owner = UUID.randomUUID();
    clouds.register(owner, cloud.entity);
    LivingEntity player = fighter();
    var event = event(cloud, List.of(player));
    clouds.filterTargets(event, false, entity -> owner);
    assertTrue(event.getAffectedEntities().isEmpty());

    PracticeClouds restarted = new PracticeClouds(marker);
    var orphanedEvent = event(cloud, List.of(player));
    restarted.filterTargets(orphanedEvent, true, entity -> owner);
    assertTrue(orphanedEvent.getAffectedEntities().isEmpty());
  }

  @Test
  void expiredCloudsAreReleasedAndShutdownClearsAllRemainingClouds() {
    Cloud expired = new Cloud();
    Cloud active = new Cloud();
    clouds.register(UUID.randomUUID(), expired.entity);
    clouds.register(UUID.randomUUID(), active.entity);
    expired.valid = false;

    clouds.prune();
    assertNull(clouds.owner(expired.entity));
    assertNotNull(clouds.owner(active.entity));
    clouds.clearAll();
    assertFalse(active.valid);
    assertNull(clouds.owner(active.entity));
  }

  private static AreaEffectCloudApplyEvent event(Cloud cloud, List<LivingEntity> targets) {
    return new AreaEffectCloudApplyEvent(cloud.entity, new ArrayList<>(targets));
  }

  private static LivingEntity fighter() {
    UUID id = UUID.randomUUID();
    return proxy(LivingEntity.class, (instance, method, arguments) -> switch (method.getName()) {
      case "getUniqueId" -> id;
      case "equals" -> instance == arguments[0];
      case "hashCode" -> System.identityHashCode(instance);
      case "toString" -> id.toString();
      default -> throw new UnsupportedOperationException(method.getName());
    });
  }

  private static final class Cloud {
    private final UUID id = UUID.randomUUID();
    private final Map<NamespacedKey, Object> markers = new HashMap<>();
    private boolean valid = true;
    private boolean persistent = true;
    private final PersistentDataContainer data = proxy(PersistentDataContainer.class,
        (instance, method, arguments) -> switch (method.getName()) {
          case "has" -> markers.containsKey(arguments[0]);
          case "set" -> {
            markers.put((NamespacedKey) arguments[0], arguments[2]);
            yield null;
          }
          default -> throw new UnsupportedOperationException(method.getName());
        });
    private final AreaEffectCloud entity = proxy(AreaEffectCloud.class,
        (instance, method, arguments) -> switch (method.getName()) {
          case "getUniqueId" -> id;
          case "getPersistentDataContainer" -> data;
          case "isValid" -> valid;
          case "setPersistent" -> {
            persistent = (boolean) arguments[0];
            yield null;
          }
          case "remove" -> {
            valid = false;
            yield null;
          }
          default -> throw new UnsupportedOperationException(method.getName());
        });
  }

  private static <T> T proxy(Class<T> type, InvocationHandler handler) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
  }
}
