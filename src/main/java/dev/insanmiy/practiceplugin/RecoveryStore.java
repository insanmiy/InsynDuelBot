package dev.insanmiy.practiceplugin;

import java.io.*;
import java.nio.file.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

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
                .equals(YamlConfiguration.loadConfiguration(file).getString("uuid"));
  }

  public boolean pending() {
    File[] players =
        new File(plugin.getDataFolder(), "recovery")
            .listFiles((directory, name) -> name.endsWith(".yml"));
    return file.exists() || players != null && players.length > 0;
  }

  public void capture(Player p) throws IOException {
    if (pending(p)) throw new IOException("An earlier recovery for this player is pending.");
    File file = playerFile(p);
    Files.createDirectories(file.toPath().getParent());
    YamlConfiguration c = new YamlConfiguration();
    c.set("uuid", p.getUniqueId().toString());
    c.set("location", p.getLocation());
    c.set("health", p.getHealth());
    c.set(
        "max-health-base",
        p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue());
    c.set("food", p.getFoodLevel());
    c.set("saturation", p.getSaturation());
    c.set("exhaustion", p.getExhaustion());
    c.set("fire", p.getFireTicks());
    c.set("xp-level", p.getLevel());
    c.set("xp-progress", p.getExp());
    c.set("xp-total", p.getTotalExperience());
    c.set("pearl-cooldown", p.getCooldown(Material.ENDER_PEARL));
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
    YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
    if (!p.getUniqueId().toString().equals(c.getString("uuid"))) return false;
    if (p.isDead()) return false;
    try {
      Location location = c.getLocation("location");
      if (location == null || location.getWorld() == null)
        throw new IOException("Recovery world unavailable");
      p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
      p.setAbsorptionAmount(0);
      p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)
          .setBaseValue(c.getDouble("max-health-base", 20));
      p.setHealth(
          Math.max(
              .5,
              Math.min(
                  c.getDouble("health"),
                  p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue())));
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
      if (!p.teleport(location)) throw new IOException("Return teleport was cancelled");
      p.saveData();
      Files.delete(file.toPath());
      return true;
    } catch (Exception e) {
      plugin.getLogger().severe("Recovery retained for " + p.getName() + ": " + e.getMessage());
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
