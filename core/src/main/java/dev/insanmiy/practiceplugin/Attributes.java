package dev.insanmiy.practiceplugin;

import java.util.Locale;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;

public final class Attributes {

  public static final Attribute MAX_HEALTH = resolve("GENERIC_MAX_HEALTH", "MAX_HEALTH");
  public static final Attribute ATTACK_DAMAGE = resolve("GENERIC_ATTACK_DAMAGE", "ATTACK_DAMAGE");
  public static final Attribute ATTACK_SPEED = resolve("GENERIC_ATTACK_SPEED", "ATTACK_SPEED");
  public static final Attribute ARMOR = resolve("GENERIC_ARMOR", "ARMOR");
  public static final Attribute ARMOR_TOUGHNESS = resolve("GENERIC_ARMOR_TOUGHNESS", "ARMOR_TOUGHNESS");
  public static final Attribute KNOCKBACK_RESISTANCE = resolve("GENERIC_KNOCKBACK_RESISTANCE", "KNOCKBACK_RESISTANCE");
  public static final Attribute MOVEMENT_SPEED = resolve("GENERIC_MOVEMENT_SPEED", "MOVEMENT_SPEED");
  public static final Attribute ATTACK_KNOCKBACK = resolve("GENERIC_ATTACK_KNOCKBACK", "ATTACK_KNOCKBACK");

  private Attributes() {}

  // Get attribute instance for entity
  public static AttributeInstance get(LivingEntity entity, Attribute attribute) {
    if (entity == null || attribute == null) {
      return null;
    }
    return entity.getAttribute(attribute);
  }

  // Get attribute value or fallback
  public static double getValue(LivingEntity entity, Attribute attribute, double fallback) {
    AttributeInstance instance = get(entity, attribute);
    return instance != null ? instance.getValue() : fallback;
  }

  private static Attribute resolve(String legacyName, String modernName) {
    // Try modern registry lookup
    String key = modernName.toLowerCase(Locale.ROOT);
    Attribute found = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
    if (found != null) {
      return found;
    }

    // Try legacy registry lookup
    found = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic." + key));
    if (found != null) {
      return found;
    }

    // Fallback to reflective field lookup
    try {
      return (Attribute) Attribute.class.getField(legacyName).get(null);
    } catch (ReflectiveOperationException ignored) {
      try {
        return (Attribute) Attribute.class.getField(modernName).get(null);
      } catch (ReflectiveOperationException ex) {
        return null;
      }
    }
  }
}
