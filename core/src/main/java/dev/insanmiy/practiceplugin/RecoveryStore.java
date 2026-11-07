package dev.insanmiy.practiceplugin;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

public final class RecoveryStore {
  private final File folder;
  private final NamespacedKey gearKey;
  private final java.util.logging.Logger logger;
  private final File file;

  public RecoveryStore(PracticePlugin plugin) {
    this(plugin.getDataFolder(), plugin.gearKey(), plugin.getLogger());
  }

  RecoveryStore(File folder, NamespacedKey gearKey, java.util.logging.Logger logger) {
    this.folder = folder;
    this.gearKey = gearKey;
    this.logger = logger;
    file = new File(folder, "recovery.yml");
  }

  private File playerFile(Player p) {
    return new File(folder, "recovery/" + p.getUniqueId() + ".yml");
  }

  public boolean pending(Player p) {
    if (playerFile(p).exists()) return true;
    if (!file.exists()) return false;
    try {
      return p.getUniqueId().toString().equals(YamlFiles.read(file.toPath()).getString("uuid"));
    } catch (IOException failure) {
      logger.warning("Legacy Recovery Is Unreadable; Repair recovery.yml Before Starting Practice");
      return true;
    }
  }

  public void capture(Player p) throws IOException {
    if (pending(p)) throw new IOException("An Earlier Recovery For This Player Is Pending.");
    File file = playerFile(p);
    Files.createDirectories(file.toPath().getParent());
    YamlConfiguration c = new YamlConfiguration();
    c.set("uuid", p.getUniqueId().toString());
    c.set("location", p.getLocation());
    c.set("health", p.getHealth());
    var maxHealth = Attributes.get(p, Attributes.MAX_HEALTH);
    c.set("max-health-base", maxHealth != null ? maxHealth.getBaseValue() : 20.0);
    if (maxHealth != null) c.set("max-health-modifiers", new java.util.ArrayList<>(maxHealth.getModifiers()));
    c.set("food", p.getFoodLevel());
    c.set("saturation", p.getSaturation());
    c.set("exhaustion", p.getExhaustion());
    c.set("fire", p.getFireTicks());
    c.set("xp-level", p.getLevel());
    c.set("xp-progress", p.getExp());
    c.set("xp-total", p.getTotalExperience());
    c.set("pearl-cooldown", p.getCooldown(Material.ENDER_PEARL));
    c.set("gamemode", p.getGameMode().name());
    c.set("invulnerable", p.isInvulnerable());
    if (p.getVehicle() != null) c.set("vehicle", p.getVehicle().getUniqueId().toString());
    c.set("absorption", p.getAbsorptionAmount());

    List<Map<String, Object>> effectsList = new ArrayList<>();
    for (PotionEffect effect : p.getActivePotionEffects()) {
      effectsList.add(effect.serialize());
    }
    c.set("potion-effects", effectsList);

    ItemStack[] contents = p.getInventory().getContents();
    for (int i = 0; i < contents.length; i++) {
      ItemStack item = contents[i];
      if (item != null && !item.getType().isAir()) {
        c.set("inventory." + i, Base64.getEncoder().encodeToString(item.serializeAsBytes()));
      }
    }
    ItemStack cursor = p.getItemOnCursor();
    if (cursor != null && !cursor.getType().isAir()) {
      c.set("cursor", Base64.getEncoder().encodeToString(cursor.serializeAsBytes()));
    }

    Path temporary = file.toPath().resolveSibling(file.getName() + ".tmp");
    c.save(temporary.toFile());
    try {
      Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(temporary, file.toPath());
    }
  }

  public boolean restore(Player p) {
    if (!pending(p)) {
      if (!p.isDead()) clearGear(p);
      return true;
    }
    if (p.isDead()) return false;
    File file = playerFile(p).exists() ? playerFile(p) : this.file;
    try {
      YamlConfiguration c = YamlFiles.read(file.toPath());
      if (!p.getUniqueId().toString().equals(c.getString("uuid")))
        throw new IOException("Recovery Owner Does Not Match The Player");
      Location location = c.getLocation("location");
      if (location == null || location.getWorld() == null)
        throw new IOException("Recovery World Unavailable");
      ItemStack[] inventory = readInventory(c, p.getInventory().getSize());
      ItemStack cursor = c.contains("cursor") ? readItem(c.getString("cursor")) : null;
      GameMode gameMode = c.contains("gamemode") ? GameMode.valueOf(c.getString("gamemode")) : null;
      List<PotionEffect> effects = new ArrayList<>();
      for (Map<?, ?> effect : c.getMapList("potion-effects")) {
        Map<String, Object> values = new HashMap<>();
        for (var entry : effect.entrySet()) values.put(String.valueOf(entry.getKey()), entry.getValue());
        effects.add(new PotionEffect(values));
      }
      p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
      p.setAbsorptionAmount(0);
      var maxHealthInstance = Attributes.get(p, Attributes.MAX_HEALTH);
      if (maxHealthInstance != null) {
        maxHealthInstance.setBaseValue(c.getDouble("max-health-base", 20));
        for (Object m : c.getList("max-health-modifiers", java.util.List.of()))
          if (m instanceof org.bukkit.attribute.AttributeModifier mod
              && !maxHealthInstance.getModifiers().contains(mod)) maxHealthInstance.addModifier(mod);
      }
      double maxAllowedHealth = Attributes.getValue(p, Attributes.MAX_HEALTH, 20.0);
      p.setHealth(Math.max(.5, Math.min(c.getDouble("health"), maxAllowedHealth)));
      p.setFoodLevel(c.getInt("food"));
      p.setSaturation((float) c.getDouble("saturation"));
      p.setExhaustion((float) c.getDouble("exhaustion"));
      p.setFireTicks(c.getInt("fire"));
      p.setLevel(c.getInt("xp-level"));
      p.setExp((float) c.getDouble("xp-progress"));
      p.setTotalExperience(c.getInt("xp-total"));
      p.setFallDistance(0);
      p.setVelocity(new org.bukkit.util.Vector());
      p.setCooldown(Material.SHIELD, 0);
      p.setCooldown(Material.ENDER_PEARL, c.getInt("pearl-cooldown", 0));

      if (gameMode != null) p.setGameMode(gameMode);
      p.setAbsorptionAmount(c.getDouble("absorption", 0.0));
      for (PotionEffect effect : effects) p.addPotionEffect(effect);
      p.getInventory().setContents(inventory);
      p.setItemOnCursor(cursor);

      if (!p.teleport(location)) throw new IOException("Return Teleport Was Cancelled");
      p.setInvulnerable(c.getBoolean("invulnerable", false));
      String vehicleId = c.getString("vehicle");
      if (vehicleId != null) {
        try {
          org.bukkit.entity.Entity vehicle = Bukkit.getEntity(UUID.fromString(vehicleId));
          if (vehicle != null && vehicle.isValid() && vehicle.getWorld().equals(p.getWorld()))
            vehicle.addPassenger(p);
        } catch (IllegalArgumentException ignored) {
          // A missing or invalid vehicle must not prevent player recovery.
        }
      }
      p.saveData();
      Files.delete(file.toPath());
      return true;
    } catch (IOException | RuntimeException e) {
      logger.severe("Recovery Retained For " + p.getName() + ": " + e.getMessage());
      return false;
    }
  }

  static ItemStack[] readInventory(YamlConfiguration data, int size) throws IOException {
    ItemStack[] inventory = new ItemStack[size];
    var section = data.getConfigurationSection("inventory");
    if (section == null) {
      if (data.contains("inventory")) throw new IOException("Recovery Inventory Must Be A Section");
      return inventory;
    }
    Set<Integer> occupied = new HashSet<>();
    for (String key : section.getKeys(false)) {
      int slot;
      try {
        slot = Integer.parseInt(key);
      } catch (NumberFormatException failure) {
        throw new IOException("Invalid Recovery Inventory Slot: " + key, failure);
      }
      if (slot < 0 || slot >= size || !occupied.add(slot))
        throw new IOException("Invalid Or Duplicate Recovery Inventory Slot: " + key);
      inventory[slot] = readItem(section.getString(key));
    }
    return inventory;
  }

  private static ItemStack readItem(String encoded) throws IOException {
    if (encoded == null) throw new IOException("Recovery Item Data Is Missing");
    try {
      return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
    } catch (RuntimeException failure) {
      throw new IOException("Recovery Item Is Unreadable; Repair The Journal Before Retrying", failure);
    }
  }

  public void clearGear(Player p) {
    for (int i = 0; i < p.getInventory().getSize(); i++)
      if (tagged(p.getInventory().getItem(i))) p.getInventory().setItem(i, null);
    if (tagged(p.getItemOnCursor())) p.setItemOnCursor(null);
  }

  private boolean tagged(ItemStack item) {
    return item != null
        && item.hasItemMeta()
        && item.getItemMeta()
            .getPersistentDataContainer()
            .has(gearKey, PersistentDataType.BYTE);
  }
}
