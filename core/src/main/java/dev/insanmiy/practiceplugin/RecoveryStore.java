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
  private final PracticePlugin plugin;
  private final File file;

  public RecoveryStore(PracticePlugin plugin) {
    this.plugin = plugin;
    file = new File(plugin.getDataFolder(), "recovery.yml");
  }

  private File playerFile(Player p) {
    return new File(plugin.getDataFolder(), "recovery/" + p.getUniqueId() + ".yml");
  }

  public boolean pending(Player p) {
    return playerFile(p).exists()
        || file.exists()
            && p.getUniqueId()
                .toString()
                .equals(YamlFiles.load(file).getString("uuid"));
  }

  public boolean pending() {
    File[] players =
        new File(plugin.getDataFolder(), "recovery")
            .listFiles((directory, name) -> name.endsWith(".yml"));
    return file.exists() || players != null && players.length > 0;
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
    clearGear(p);
    if (!pending(p)) return true;
    File file = playerFile(p).exists() ? playerFile(p) : this.file;
    YamlConfiguration c = YamlFiles.load(file);
    if (!p.getUniqueId().toString().equals(c.getString("uuid"))) return false;
    if (p.isDead()) return false;
    try {
      Location location = c.getLocation("location");
      if (location == null || location.getWorld() == null)
        throw new IOException("Recovery World Unavailable");
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

      if (c.contains("gamemode")) {
        try {
          p.setGameMode(GameMode.valueOf(c.getString("gamemode")));
        } catch (Exception ignored) {}
      }
      if (c.contains("absorption")) {
        p.setAbsorptionAmount(c.getDouble("absorption", 0.0));
      }
      if (c.contains("potion-effects")) {
        for (Map<?, ?> map : c.getMapList("potion-effects")) {
          try {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) map;
            p.addPotionEffect(new PotionEffect(cast));
          } catch (Exception ignored) {}
        }
      }

      if (c.contains("inventory")) {
        p.getInventory().clear();
        var invSec = c.getConfigurationSection("inventory");
        if (invSec != null) {
          for (String key : invSec.getKeys(false)) {
            try {
              int slot = Integer.parseInt(key);
              byte[] bytes = Base64.getDecoder().decode(invSec.getString(key));
              p.getInventory().setItem(slot, ItemStack.deserializeBytes(bytes));
            } catch (Exception ignored) {}
          }
        }
      }
      if (c.contains("cursor")) {
        try {
          byte[] bytes = Base64.getDecoder().decode(c.getString("cursor"));
          p.setItemOnCursor(ItemStack.deserializeBytes(bytes));
        } catch (Exception ignored) {}
      }

      if (!p.teleport(location)) throw new IOException("Return Teleport Was Cancelled");
      p.saveData();
      Files.delete(file.toPath());
      return true;
    } catch (Exception e) {
      plugin.getLogger().severe("Recovery Retained For " + p.getName() + ": " + e.getMessage());
      return false;
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
            .has(plugin.gearKey(), PersistentDataType.BYTE);
  }
}
