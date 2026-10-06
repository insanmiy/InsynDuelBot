package dev.insanmiy.practiceplugin.config;

import dev.insanmiy.practiceplugin.Attributes;
import dev.insanmiy.practiceplugin.model.KitLayout;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

public final class Kit {
  private final String name, description;
  private final List<ItemStack> armor;
  private final Map<Integer, ItemStack> items;
  private final ItemStack offhand;
  private final double health;
  private final boolean regeneration;

  private Kit(
      String name,
      String description,
      List<ItemStack> armor,
      Map<Integer, ItemStack> items,
      ItemStack offhand,
      double health,
      boolean regeneration) {
    if (!Double.isFinite(health) || health < 2 || health > 100)
      throw new IllegalArgumentException("Kit health must be 2..100 HP.");
    this.name = name;
    this.description = description;
    this.armor = armor.stream().map(ItemStack::clone).toList();
    TreeMap<Integer, ItemStack> copies = new TreeMap<>();
    items.forEach((slot, item) -> copies.put(slot, item.clone()));
    this.items = Collections.unmodifiableMap(copies);
    this.offhand = offhand.clone();
    this.health = health;
    this.regeneration = regeneration;
  }

  public String name() {
    return name;
  }

  public String description() {
    return description;
  }

  public double health() {
    return health;
  }

  public boolean regeneration() {
    return regeneration;
  }

  public Map<Integer, ItemStack> items() {
    return items;
  }

  public ItemStack offhand() {
    return offhand.clone();
  }

  public Map<Integer, ItemStack> previewItems() {
    Map<Integer, ItemStack> result = new TreeMap<>();
    items.forEach((slot, item) -> result.put(slot, item.clone()));
    for (int i = 0; i < armor.size(); i++) result.put(36 + i, armor.get(i).clone());
    result.put(40, offhand.clone());
    return result;
  }

  public String category() {
    if (count(Material.EXPERIENCE_BOTTLE) > 0
        || count(Material.TOTEM_OF_UNDYING) > 0
        || offhand.getType() == Material.TOTEM_OF_UNDYING) return "smp";
    if (count(Material.BOW) > 0) return "ranged";
    if (count(Material.COBWEB) > 0) return "web";
    if (count(Material.POTION) + count(Material.SPLASH_POTION) > 0) return "potion";
    return "melee";
  }

  public static boolean foodRegeneration(ConfigurationSection c) {
    return c.getBoolean("natural-regeneration", true);
  }

  public Material icon() {
    return items.values().stream()
        .filter(i -> melee(i.getType()))
        .findFirst()
        .map(ItemStack::getType)
        .orElseGet(
            () ->
                previewItems().values().stream()
                    .map(ItemStack::getType)
                    .filter(type -> !type.isAir())
                    .findFirst()
                    .orElse(Material.CHEST));
  }

  public int count(Material material) {
    return items.values().stream()
        .filter(i -> i.getType() == material)
        .mapToInt(ItemStack::getAmount)
        .sum();
  }

  public String summary() {
    return health
        + " HP | "
        + count(Material.GOLDEN_APPLE)
        + " apples | "
        + count(Material.SPLASH_POTION)
        + " splash pots | "
        + count(Material.COBWEB)
        + " webs | "
        + count(Material.EXPERIENCE_BOTTLE)
        + " XP | "
        + description;
  }

  public static Kit read(String name, ConfigurationSection c) {
    List<?> rawArmor = c.getList("armor", List.of());
    List<ItemStack> armor = rawArmor.stream().map(Kit::parseItem).toList();
    if (armor.size() != 4)
      throw new IllegalArgumentException("armor requires boots, leggings, chestplate, helmet");
    for (int i = 0; i < 4; i++) {
      enchant(armor.get(i), c.getConfigurationSection("armor-enchantments"));
    }
    Map<Integer, ItemStack> items = new TreeMap<>();
    ConfigurationSection slots =
        Objects.requireNonNull(c.getConfigurationSection("items"), "items missing");
    for (String key : slots.getKeys(false)) {
      int slot = Integer.parseInt(key);
      if (slot < 0 || slot > 35)
        throw new IllegalArgumentException("Inventory slots must be 0..35");
      ItemStack item = parseItem(slots.get(key));
      Material type = item.getType();
      if (melee(type)) enchant(item, c.getConfigurationSection("weapon-enchantments"));
      if (type == Material.BOW || type == Material.CROSSBOW)
        enchant(item, c.getConfigurationSection("bow-enchantments"));
      items.put(slot, item);
    }
    ItemStack offhand = parseItem(c.get("offhand", "AIR"));
    armor.forEach(Kit::allowed);
    items.values().forEach(Kit::allowed);
    allowed(offhand);
    return new Kit(
        name,
        c.getString("description", "Custom PvP kit"),
        armor,
        items,
        offhand,
        c.getDouble("health", 20),
        foodRegeneration(c));
  }

  public static boolean melee(Material type) {
    return type.name().endsWith("_SWORD") || type.name().endsWith("_AXE");
  }

  public static boolean supported(Material type) {
    return !type.isLegacy() && !type.isAir() && type.isItem();
  }

  public static boolean tool(Material type) {
    return type == Material.SHEARS
        || type.name().endsWith("_PICKAXE")
        || type.name().endsWith("_SHOVEL")
        || type.name().endsWith("_HOE");
  }

  public static boolean armorItem(Material type) {
    String n = type.name();
    return n.endsWith("_BOOTS")
        || n.endsWith("_LEGGINGS")
        || n.endsWith("_CHESTPLATE")
        || n.endsWith("_HELMET");
  }

  private static void allowed(ItemStack item) {
    String n = item.getType().name();
    if (n.endsWith("_SPAWN_EGG")
        || n.contains("COMMAND_BLOCK")
        || n.startsWith("STRUCTURE_")
        || Set.of("BEDROCK", "BARRIER", "DEBUG_STICK", "JIGSAW", "LIGHT", "SPAWNER", "KNOWLEDGE_BOOK")
            .contains(n))
      throw new IllegalArgumentException("Item not allowed in kits: " + n);
  }

  public static boolean ammunition(Material type) {
    return type == Material.ARROW
        || type == Material.SPECTRAL_ARROW
        || type == Material.TIPPED_ARROW;
  }

  public static boolean supportedPotion(PotionType type) {
    return type != null;
  }

  private static ItemStack parseItem(Object raw) {
    if (raw instanceof ConfigurationSection section) raw = section.getValues(false);
    if (raw instanceof Map<?, ?> map) {
      Object material = map.get("type");
      Object amount = map.containsKey("amount") ? map.get("amount") : 1;
      ItemStack item = parseItem(String.valueOf(material) + ":" + amount);
      if (map.containsKey("potion")) {
        if (!(item.getItemMeta() instanceof PotionMeta meta))
          throw new IllegalArgumentException("Only potions may specify potion type.");
        meta.setBasePotionType(
            PotionType.valueOf(String.valueOf(map.get("potion")).toUpperCase(Locale.ROOT)));
        item.setItemMeta(meta);
      }
      Object rawEnchants = map.get("enchants");
      if (rawEnchants instanceof ConfigurationSection cs) rawEnchants = cs.getValues(false);
      if (rawEnchants instanceof Map<?, ?> enchants) {
        for (Map.Entry<?, ?> e : enchants.entrySet()) {
          String key = String.valueOf(e.getKey()).toLowerCase(Locale.ROOT);
          NamespacedKey id = NamespacedKey.fromString(key.contains(":") ? key : "minecraft:" + key);
          var enchantment = id == null ? null : org.bukkit.Registry.ENCHANTMENT.get(id);
          int level = Integer.parseInt(String.valueOf(e.getValue()));
          if (enchantment != null && level >= 1 && enchantment.canEnchantItem(item)) {
            item.addUnsafeEnchantment(enchantment, Math.min(level, enchantment.getMaxLevel()));
          }
        }
      }
      return item;
    }
    if (raw instanceof ItemStack item) {
      if (item.getType().isAir()) return new ItemStack(Material.AIR);
      if (item.getAmount() < 1) throw new IllegalArgumentException("Invalid item count");
      return item.clone();
    }
    String[] parts = String.valueOf(raw).split(":", -1);
    if (parts.length > 2) throw new IllegalArgumentException("Expected MATERIAL:amount");
    Material type = Material.matchMaterial(parts[0]);
    if (type == null || !type.isItem() && type != Material.AIR)
      throw new IllegalArgumentException("Unknown item: " + raw);
    int count = parts.length == 2 ? Integer.parseInt(parts[1]) : 1;
    if (count < 1 || type != Material.AIR && count > type.getMaxStackSize())
      throw new IllegalArgumentException("Invalid amount for " + type);
    return new ItemStack(type, count);
  }

  private static void enchant(ItemStack item, ConfigurationSection c) {
    if (c == null || item.getType().isAir()) return;
    for (String key : c.getKeys(false)) {
      String cleanKey = key.toLowerCase(Locale.ROOT);
      NamespacedKey id = NamespacedKey.fromString(cleanKey.contains(":") ? cleanKey : "minecraft:" + cleanKey);
      var enchantment =
          id == null
              ? null
              : org.bukkit.Registry.ENCHANTMENT.get(id);
      int level = c.getInt(key);
      if (enchantment == null || level < 1 || level > enchantment.getMaxLevel())
        throw new IllegalArgumentException("Invalid enchantment " + key + " for " + item.getType());
      if (!enchantment.canEnchantItem(item)) continue;
      item.addUnsafeEnchantment(enchantment, level);
    }
  }

  public static void capture(Player player, ConfigurationSection c) {
    c.createSection("items");
    c.set("description", "Saved by " + player.getName());
    c.set("health", Attributes.getValue(player, Attributes.MAX_HEALTH, 20.0));
    c.set("natural-regeneration", true);
    c.set(
        "armor",
        Arrays.stream(player.getInventory().getArmorContents())
            .map(i -> i == null ? new ItemStack(Material.AIR) : i.clone())
            .toList());
    for (int slot = 0; slot < 36; slot++) {
      ItemStack item = player.getInventory().getItem(slot);
      if (item != null && !item.getType().isAir()) c.set("items." + slot, item.clone());
    }
    c.set("offhand", player.getInventory().getItemInOffHand().clone());
  }

  public void equip(Player player, NamespacedKey key) {
    equip(player, key, null, null);
  }

  public void equip(Player player, NamespacedKey gearKey, NamespacedKey slotKey, KitLayout layout) {
    player.getInventory().clear();
    player
        .getInventory()
        .setArmorContents(
            armor.stream().map(i -> fresh(i, gearKey, slotKey, -1)).toArray(ItemStack[]::new));

    Map<Integer, ItemStack> freshItems = new LinkedHashMap<>();
    items.forEach((slot, item) -> freshItems.put(slot, fresh(item, gearKey, slotKey, slot)));
    if (offhand != null && !offhand.getType().isAir()) {
      freshItems.put(40, fresh(offhand, gearKey, slotKey, 40));
    }

    if (layout == null || layout.slots().isEmpty()) {
      items.forEach((slot, item) -> player.getInventory().setItem(slot, freshItems.get(slot)));
      if (freshItems.containsKey(40)) player.getInventory().setItemInOffHand(freshItems.get(40));
      player
          .getInventory()
          .setHeldItemSlot(
              items.entrySet().stream()
                  .filter(e -> e.getKey() < 9 && melee(e.getValue().getType()))
                  .mapToInt(Map.Entry::getKey)
                  .min()
                  .orElse(0));
      return;
    }

    Set<Integer> occupied = new HashSet<>();
    for (Map.Entry<Integer, Integer> entry : layout.slots().entrySet()) {
      int origSlot = entry.getKey();
      int targetSlot = entry.getValue();
      ItemStack item = freshItems.get(origSlot);
      if (item != null && !occupied.contains(targetSlot)) {
        if (targetSlot == 40) {
          player.getInventory().setItemInOffHand(item);
          occupied.add(40);
          freshItems.remove(origSlot);
        } else if (targetSlot >= 0 && targetSlot < 36) {
          player.getInventory().setItem(targetSlot, item);
          occupied.add(targetSlot);
          freshItems.remove(origSlot);
        }
      }
    }

    for (Map.Entry<Integer, ItemStack> entry : freshItems.entrySet()) {
      int defSlot = entry.getKey();
      ItemStack item = entry.getValue();
      if (defSlot == 40 && !occupied.contains(40)) {
        player.getInventory().setItemInOffHand(item);
        occupied.add(40);
      } else if (defSlot >= 0 && defSlot < 36 && !occupied.contains(defSlot)) {
        player.getInventory().setItem(defSlot, item);
        occupied.add(defSlot);
      } else {
        for (int s = 0; s < 36; s++) {
          if (!occupied.contains(s)) {
            player.getInventory().setItem(s, item);
            occupied.add(s);
            break;
          }
        }
      }
    }

    if (layout.heldSlot() >= 0
        && layout.heldSlot() < 9
        && player.getInventory().getItem(layout.heldSlot()) != null
        && !player.getInventory().getItem(layout.heldSlot()).getType().isAir()) {
      player.getInventory().setHeldItemSlot(layout.heldSlot());
    } else {
      int defaultMelee = -1;
      for (int s = 0; s < 9; s++) {
        ItemStack item = player.getInventory().getItem(s);
        if (item != null && melee(item.getType())) {
          defaultMelee = s;
          break;
        }
      }
      player.getInventory().setHeldItemSlot(defaultMelee >= 0 ? defaultMelee : 0);
    }
  }

  private static ItemStack fresh(ItemStack template, NamespacedKey key) {
    return fresh(template, key, null, -1);
  }

  private static ItemStack fresh(
      ItemStack template, NamespacedKey gearKey, NamespacedKey slotKey, int slot) {
    ItemStack item = template.clone();
    if (!item.getType().isAir())
      dev.insanmiy.practiceplugin.ItemMetadata.edit(
          item,
          m -> {
            m.getPersistentDataContainer().set(gearKey, PersistentDataType.BYTE, (byte) 1);
            if (slotKey != null && slot >= 0) {
              m.getPersistentDataContainer().set(slotKey, PersistentDataType.INTEGER, slot);
            }
            if (m instanceof Damageable damageable) damageable.setDamage(0);
          });
    return item;
  }
}
