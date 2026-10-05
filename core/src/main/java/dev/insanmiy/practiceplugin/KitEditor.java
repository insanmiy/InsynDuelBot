package dev.insanmiy.practiceplugin;

import dev.insanmiy.practiceplugin.config.Kit;
import dev.insanmiy.practiceplugin.model.EditorRules;
import java.io.IOException;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

final class KitEditor implements Listener {
  private enum Screen {
    LIBRARY,
    TEMPLATES,
    EDIT,
    ITEM,
    PALETTE,
    ENCHANT,
    LEVEL,
    NAME,
    DELETE
  }

  private final PracticePlugin plugin;
  private final CustomKitStore store;

  KitEditor(PracticePlugin plugin) {
    this.plugin = plugin;
    store = new CustomKitStore(plugin.getDataFolder().toPath().resolve("customkits.yml"));
  }

  private static final class Draft {
    String name, revision;
    final ItemStack[] items = new ItemStack[41];
    double health = 20;
    boolean regeneration = true;
    int selected;
    int paletteCategory = 2;
    Enchantment enchantment;
    String nameInput;

    Draft(String name) {
      this.name = name;
    }
  }

  private static final class View implements AnimatedMenu {
    final UUID owner;
    final Screen screen;
    final Draft draft;
    final int page;
    final List<String> names = new ArrayList<>();
    final List<ItemStack> palette = new ArrayList<>();
    final List<Enchantment> enchants = new ArrayList<>();
    final Inventory inventory;

    View(Player player, Screen screen, Draft draft, int page) {
      owner = player.getUniqueId();
      this.screen = screen;
      this.draft = draft;
      this.page = page;
      inventory =
          TextUI.inventory(
              this,
              54,
              Component.text(
                  screen == Screen.LIBRARY || screen == Screen.TEMPLATES
                      ? (screen == Screen.LIBRARY ? "CUSTOM KITS / " : "COPY A TEMPLATE / ")
                          + (page + 1)
                      : screen
                          + " / "
                          + draft.name
                          + (screen == Screen.PALETTE ? " / " + (page + 1) : "")));
    }

    public Inventory getInventory() {
      return inventory;
    }

    public int pulseSlot() {
      return screen == Screen.EDIT || screen == Screen.NAME ? 49 : -1;
    }

    public int[] decorativeSlots() {
      return switch (screen) {
        case EDIT, PALETTE -> new int[0];
        case LIBRARY -> new int[] {48, 51};
        case TEMPLATES -> new int[] {48, 50, 51};
        case ITEM ->
            new int[] {
              0, 1, 2, 3, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 37, 38, 39, 41, 42, 43, 44, 46, 47,
              51, 52
            };
        case ENCHANT -> new int[] {45, 46, 47, 50, 51, 52};
        case LEVEL -> new int[] {0, 1, 2, 3, 5, 6, 7, 8, 45, 46, 47, 48, 50, 51, 52};
        case NAME -> new int[] {42, 43, 44, 45, 46, 47, 48, 50, 51, 52};
        case DELETE ->
            new int[] {
              0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 49, 50, 51,
              52
            };
      };
    }
  }

  void allowed(Player player) {
    if (!player.hasPermission("practice.kits") && !player.hasPermission("practice.admin"))
      throw new IllegalArgumentException("Missing permission: practice.kits");
    if (plugin.sessionFor(player) != null)
      throw new IllegalArgumentException("Stop practice before editing kits.");
  }

  void command(Player player, String[] args) throws IOException {
    allowed(player);
    String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "editor";
    if (action.equals("editor")) {
      library(player, 0);
      return;
    }
    if (args.length < 3)
      throw new IllegalArgumentException(
          "/practice kit <create|import|edit|save|delete|copy|rename> <name>");
    String name = CustomKitStore.name(args[2]);
    switch (action) {
      case "create" -> create(player, name, args.length > 3 ? args[3] : null);
      case "import" -> importKit(player, name);
      case "edit" -> edit(player, name);
      case "save" -> {
        var candidate = new YamlConfiguration();
        Kit.capture(player, candidate);
        save(player, name, candidate, null);
        player.sendMessage(TextUI.legacy(Component.text("Saved " + name + ". Your inventory was not changed.")));
      }
      case "copy" -> {
        if (args.length != 4)
          throw new IllegalArgumentException("/practice kit copy <source> <new-name>");
        create(player, CustomKitStore.name(args[3]), name);
      }
      case "rename" -> {
        if (args.length != 4)
          throw new IllegalArgumentException("/practice kit rename <old-name> <new-name>");
        store.rename(
            name,
            args[3],
            player.getUniqueId(),
            player.hasPermission("practice.admin"),
            plugin.kits().keySet());
        plugin.refreshKits();
        player.sendMessage(TextUI.legacy(Component.text("Renamed kit to " + CustomKitStore.name(args[3]))));
        library(player, 0);
      }
      case "delete" -> {
        Draft draft = existing(player, name);
        show(player, Screen.DELETE, draft, 0);
      }
      default ->
          throw new IllegalArgumentException(
              "Use editor, create, import, edit, save, copy, rename, delete or info.");
    }
  }

  private void save(Player player, String name, YamlConfiguration candidate, String revision)
      throws IOException {
    allowed(player);
    Kit.read(name, candidate);
    store.save(
        name,
        candidate,
        revision,
        player.getUniqueId(),
        player.hasPermission("practice.admin"),
        plugin.kits().keySet());
    plugin.refreshKits();
  }

  private String unusedName() throws IOException {
    var saved = store.read();
    int suffix = 1;
    while (saved.contains("custom-" + suffix) || plugin.kits().containsKey("custom-" + suffix))
      suffix++;
    return "custom-" + suffix;
  }

  void create(Player player, String name, String template) throws IOException {
    allowed(player);
    name = CustomKitStore.name(name);
    if (plugin.kits().containsKey(name) || store.read().contains(name))
      throw new IllegalArgumentException("Name already used. Choose a new name.");
    Draft draft = new Draft(name);
    if (template != null) {
      Kit kit = plugin.kits().get(template.toLowerCase(Locale.ROOT));
      if (kit == null) throw new IllegalArgumentException("Unknown template kit.");
      kit.previewItems().forEach((slot, item) -> draft.items[slot] = item);
      draft.health = kit.health();
      draft.regeneration = kit.regeneration();
    } else draft.items[0] = new ItemStack(Material.DIAMOND_SWORD);
    show(player, Screen.EDIT, draft, 0);
    player.sendMessage(TextUI.legacy(
        Component.text(
            "Editing " + name + ". Click Save to keep it. Closing discards unsaved changes.")));
  }

  private Draft existing(Player player, String name) throws IOException {
    var section = store.read().getConfigurationSection(name);
    if (!CustomKitStore.editable(
        section, player.getUniqueId(), player.hasPermission("practice.admin")))
      throw new IllegalArgumentException(
          "You can only edit your own custom kits. Copy a preset to customize it.");
    Kit kit = Kit.read(name, section);
    Draft draft = new Draft(name);
    draft.revision = CustomKitStore.revision(section);
    kit.previewItems().forEach((slot, item) -> draft.items[slot] = item);
    draft.health = kit.health();
    draft.regeneration = kit.regeneration();
    return draft;
  }

  private static void importInventory(Player player, Draft draft) {
    for (int i = 0; i < draft.items.length; i++)
      put(draft, i, player.getInventory().getItem(i));
  }

  private void importKit(Player player, String name) throws IOException {
    allowed(player);
    name = CustomKitStore.name(name);
    if (plugin.kits().containsKey(name) || store.read().contains(name))
      throw new IllegalArgumentException("Name already used. Choose a new name.");
    Draft draft = new Draft(name);
    importInventory(player, draft);
    show(player, Screen.EDIT, draft, 0);
    player.sendMessage(TextUI.legacy(
        Component.text("Imported your inventory into " + name + ". Click Save to keep it.")));
  }

  private void edit(Player player, String name) throws IOException {
    show(player, Screen.EDIT, existing(player, name), 0);
  }

  void library(Player player, int requested) throws IOException {
    allowed(player);
    var saved = store.read();
    List<String> names =
        saved.getKeys(false).stream()
            .filter(
                n ->
                    CustomKitStore.editable(
                        saved.getConfigurationSection(n),
                        player.getUniqueId(),
                        player.hasPermission("practice.admin")))
            .sorted()
            .toList();
    int page = EditorRules.page(requested, names.size());
    View view = new View(player, Screen.LIBRARY, null, page);
    view.names.addAll(names);
    for (int i = 0; i < 45 && page * 45 + i < names.size(); i++) {
      String name = names.get(page * 45 + i);
      view.inventory.setItem(
          i,
          button(
              Material.WRITABLE_BOOK,
              name,
              "Left: edit | Right: duplicate",
              "/practice kit rename " + name + " <new-name>"));
    }
    view.inventory.setItem(45, button(Material.ARROW, "Previous page"));
    view.inventory.setItem(
        46, button(Material.EMERALD, "Create blank kit", "No fixed kit-count limit"));
    view.inventory.setItem(
        47,
        button(
            Material.CHEST, "Browse kit templates", "Choose any kit to copy into your own draft"));
    view.inventory.setItem(
        49,
        button(
            Material.BOOK,
            names.size() + " custom kits",
            "/practice kit create <name> [template]"));
    view.inventory.setItem(52, button(Material.ARROW, "Next page"));
    view.inventory.setItem(
        50,
        button(
            Material.HOPPER,
            "Create from your inventory",
            "Copies hotbar, inventory, armor and offhand into a new draft",
            "Your real items stay untouched; click Save when ready",
            "/practice kit import <name>"));
    view.inventory.setItem(53, button(Material.BARRIER, "Close"));
    player.openInventory(view.inventory);
  }

  private static boolean empty(ItemStack item) {
    return item == null || item.getType().isAir();
  }

  private void templates(Player player, int requested) {
    List<String> names = plugin.kits().keySet().stream().sorted().toList();
    int page = EditorRules.page(requested, names.size());
    View view = new View(player, Screen.TEMPLATES, null, page);
    view.names.addAll(names);
    for (int i = 0; i < 45 && page * 45 + i < names.size(); i++) {
      Kit kit = plugin.kits().get(names.get(page * 45 + i));
      view.inventory.setItem(
          i,
          button(
              kit.icon(),
              kit.name(),
              kit.description(),
              kit.health() + " HP | Click to create an independent copy"));
    }
    view.inventory.setItem(45, button(Material.ARROW, "Previous page"));
    view.inventory.setItem(
        49, button(Material.BOOK, "Choose a starting loadout", "Original kits are never modified"));
    view.inventory.setItem(52, button(Material.ARROW, "Next page"));
    view.inventory.setItem(53, button(Material.BARRIER, "Back to your kits"));
    player.openInventory(view.inventory);
  }

  private void palette(Player player, Draft draft) {
    show(player, Screen.PALETTE, draft, 0);
  }

  private static String pretty(String value) {
    return Arrays.stream(value.toLowerCase(Locale.ROOT).split("_"))
        .map(s -> s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1))
        .collect(java.util.stream.Collectors.joining(" "));
  }

  private static ItemStack displayItem(ItemStack item, String... hints) {
    ItemStack display = item.clone();
    dev.insanmiy.practiceplugin.ItemMetadata.edit(display,
        meta -> {
          List<Component> lore =
              TextUI.lore(meta) == null ? new ArrayList<>() : new ArrayList<>(TextUI.lore(meta));
          lore.add(Component.empty());
          for (String hint : hints) lore.add(Component.text(hint, NamedTextColor.AQUA));
          TextUI.lore(meta, lore);
        });
    return display;
  }

  private static String slotName(int slot) {
    return slot < 9
        ? "Hotbar " + (slot + 1)
        : slot < 36
            ? "Inventory " + slot
            : new String[] {"Boots", "Leggings", "Chestplate", "Helmet", "Offhand"}[slot - 36];
  }

  private void show(Player player, Screen screen, Draft draft, int requested) {
    int page = Math.max(0, requested);
    View view = new View(player, screen, draft, page);
    Inventory inv = view.inventory;
    if (screen == Screen.EDIT) {
      for (int i = 0; i < 41; i++)
        inv.setItem(
            i,
            empty(draft.items[i])
                ? button(
                    Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                    slotName(i) + " / empty",
                    "Click to choose an item from the GUI",
                    "Shift-left: paste selected item | Right: clear")
                : displayItem(
                    draft.items[i],
                    slotName(i),
                    "Click: item settings and enchantments",
                    "Shift-left: paste selected item | Right: clear"));
      inv.setItem(
          41,
          button(
              Material.COMPASS,
              "Selected: " + slotName(draft.selected),
              "Slots 0-8 (top row) are the hotbar",
              "Click an item: settings / enchants",
              "Click empty slot: item picker",
              "Shift-left another slot: copy selected into it",
              "Click your inventory below to copy an item here"));
      inv.setItem(42, button(Material.RED_DYE, "Amount -1", "Shift: -16"));
      inv.setItem(43, button(Material.LIME_DYE, "Amount +1", "Shift: +16 (native stack limits)"));
      inv.setItem(
          44,
          button(
              Material.CHEST, "Choose / replace selected item", "Opens categorized item picker"));
      inv.setItem(45, button(Material.ARROW, "Discard changes / back"));
      inv.setItem(46, button(Material.REDSTONE, "Health -2 / " + draft.health + " HP"));
      inv.setItem(47, button(Material.GLOWSTONE_DUST, "Health +2 / " + draft.health + " HP"));
      inv.setItem(48, button(Material.COOKED_BEEF, "Natural regeneration: " + draft.regeneration));
      inv.setItem(
          49,
          button(
              Material.EMERALD_BLOCK,
              "SAVE " + draft.name,
              "Validates and saves to disk",
              "Both fighters get independent copies"));
      inv.setItem(
          50,
          button(
              Material.HOPPER,
              "Import your inventory",
              "Replaces draft gear; real items untouched"));
      inv.setItem(51, button(Material.ENCHANTING_TABLE, "Enchant selected item"));
      inv.setItem(
          52,
          button(Material.WRITABLE_BOOK, "Name this kit", "Type a name using the GUI keyboard"));
      inv.setItem(53, button(Material.BARRIER, "Delete saved kit", "Confirmation required"));
    } else if (screen == Screen.ITEM) {
      ItemStack item = draft.items[draft.selected];
      if (empty(item)) {
        palette(player, draft);
        return;
      }
      inv.setItem(
          4,
          button(
              Material.COMPASS,
              slotName(draft.selected),
              "Editing this slot only",
              "All changes stay in the draft until Save"));
      inv.setItem(13, displayItem(item, "Current item / " + slotName(draft.selected)));
      inv.setItem(
          19,
          button(
              Material.CHEST,
              "Choose a different item",
              "Browse all items, equipment, food, potions and blocks"));
      inv.setItem(
          21,
          button(
              Material.ENCHANTING_TABLE,
              "ADD / EDIT ENCHANTMENTS",
              "Pick an enchantment, then choose its level"));
      inv.setItem(
          23, button(Material.GRINDSTONE, "Remove all enchantments", "Only changes this item"));
      inv.setItem(
          22,
          button(
              Material.NETHER_STAR,
              "FAST ENCHANT: combat / tool essentials",
              "Max compatible damage, protection, efficiency and durability",
              "Skips conflicting enchantments"));
      inv.setItem(
          24,
          button(
              Material.EXPERIENCE_BOTTLE,
              "FAST ENCHANT: Mending + Unbreaking",
              "One click; respects conflicts such as Infinity"));
      inv.setItem(25, button(Material.BARRIER, "Clear this slot"));
      int[] amounts = {1, 8, 16, 32, 64};
      for (int i = 0; i < amounts.length; i++) {
        int count = EditorRules.amount(amounts[i], item.getMaxStackSize());
        inv.setItem(
            28 + i,
            button(
                Material.PAPER,
                "Set amount: " + count,
                "Native maximum: " + item.getMaxStackSize()));
      }
      inv.setItem(
          34,
          button(
              Material.CHEST_MINECART,
              "Fill empty inventory slots with this item",
              "Slots 9-35 only; existing items are preserved"));
      inv.setItem(
          40,
          button(
              Material.ARMOR_STAND,
              "Copy enchantments to matching armor",
              "Only compatible, non-conflicting enchants are copied",
              "Existing armor enchantments are preserved"));
      inv.setItem(45, button(Material.ARROW, "Back to loadout"));
      inv.setItem(48, button(Material.RED_DYE, "Amount -1", "Shift: -16"));
      inv.setItem(
          49,
          button(Material.BOOK, "Amount: " + item.getAmount() + " / " + item.getMaxStackSize()));
      inv.setItem(50, button(Material.LIME_DYE, "Amount +1", "Shift: +16"));
      inv.setItem(53, button(Material.ARROW, "Back to loadout"));
    } else if (screen == Screen.PALETTE) {
      if (draft.paletteCategory == 3)
        for (Material material :
            List.of(Material.SPLASH_POTION, Material.POTION, Material.LINGERING_POTION,
                Material.TIPPED_ARROW))
          for (PotionType type : PotionType.values()) {
            ItemStack potion = new ItemStack(material);
            PotionMeta meta = (PotionMeta) potion.getItemMeta();
            meta.setBasePotionType(type);
            potion.setItemMeta(meta);
            potion.setAmount(potion.getMaxStackSize());
            view.palette.add(potion);
          }
      for (Material type : Material.values())
        if (Kit.supported(type)
            && inCategory(type, draft.paletteCategory)
            && fits(draft.selected, type)) {
          ItemStack item = new ItemStack(type);
          item.setAmount(item.getMaxStackSize());
          view.palette.add(item);
        }
      int maxPage = Math.max(0, (view.palette.size() - 1) / 45);
      if (page > maxPage) {
        show(player, screen, draft, maxPage);
        return;
      }
      for (int i = 0; i < 45 && page * 45 + i < view.palette.size(); i++)
        inv.setItem(
            i,
            displayItem(
                view.palette.get(page * 45 + i),
                "Click to use in " + slotName(draft.selected),
                "Then edit amount and enchantments"));
      if (view.palette.isEmpty())
        inv.setItem(
            22,
            button(
                Material.BARRIER,
                "No items for this slot / category",
                "Try the All items category"));
      inv.setItem(45, button(Material.ARROW, "Previous page"));
      Material[] icons = {
        Material.DIAMOND_SWORD,
        Material.COOKED_BEEF,
        Material.EXPERIENCE_BOTTLE,
        Material.SPLASH_POTION,
        Material.COBBLESTONE,
        Material.DIAMOND_CHESTPLATE
      };
      String[] categories = {
        "Weapons", "Food", "All items", "Potions / tipped arrows", "Blocks", "Equipment"
      };
      for (int i = 0; i < categories.length; i++)
        inv.setItem(
            46 + i,
            button(
                icons[i],
                (i == draft.paletteCategory ? "Selected: " : "") + categories[i],
                "Page " + (page + 1) + " / " + (maxPage + 1),
                "Copies to " + slotName(draft.selected)));
      inv.setItem(52, button(Material.ARROW, "Next page"));
      inv.setItem(53, button(Material.BARRIER, "Back to editor"));
    } else if (screen == Screen.ENCHANT) {
      ItemStack item = draft.items[draft.selected];
      if (empty(item)) {
        palette(player, draft);
        return;
      }
      if (!empty(item))
        org.bukkit.Registry.ENCHANTMENT
            .forEach(
                e -> {
                  if (e.canEnchantItem(item)) view.enchants.add(e);
                });
      view.enchants.sort(Comparator.comparing(e -> e.getKey().getKey()));
      for (int i = 0; i < Math.min(45, view.enchants.size()); i++) {
        Enchantment e = view.enchants.get(i);
        inv.setItem(
            i,
            button(
                Material.ENCHANTED_BOOK,
                pretty(e.getKey().getKey()) + " / Level " + item.getEnchantmentLevel(e),
                "Click: exact level | Shift-left: MAX | Right: remove",
                conflicts(item, e)
                    ? "CONFLICT: remove the incompatible enchantment first"
                    : "Compatible with selected item",
                "Maximum vanilla level: " + e.getMaxLevel()));
      }
      if (view.enchants.isEmpty())
        inv.setItem(22, button(Material.BARRIER, "This item has no compatible enchantments"));
      inv.setItem(48, button(Material.GRINDSTONE, "Remove all enchantments"));
      inv.setItem(49, displayItem(item, "Enchanting " + slotName(draft.selected)));
      inv.setItem(53, button(Material.ARROW, "Back"));
    } else if (screen == Screen.LEVEL) {
      Enchantment enchant = draft.enchantment;
      ItemStack item = draft.items[draft.selected];
      inv.setItem(
          4,
          button(
              Material.ENCHANTED_BOOK,
              pretty(enchant.getKey().getKey()),
              "Current level: " + item.getEnchantmentLevel(enchant),
              "Choose a level below"));
      inv.setItem(10, button(Material.GRINDSTONE, "Remove this enchantment"));
      for (int level = 1; level <= Math.min(30, enchant.getMaxLevel()); level++)
        inv.setItem(
            10 + level,
            button(
                Material.ENCHANTED_BOOK,
                "Level " + level,
                conflicts(item, enchant)
                    ? "Conflicts with an existing enchantment"
                    : "Click to apply"));
      inv.setItem(49, displayItem(item, "Your item"));
      inv.setItem(53, button(Material.ARROW, "Back to enchantments"));
    } else if (screen == Screen.NAME) {
      if (draft.nameInput == null) draft.nameInput = draft.name;
      String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
      for (int i = 0; i < alphabet.length(); i++)
        inv.setItem(i, button(Material.PAPER, "" + alphabet.charAt(i)));
      inv.setItem(36, button(Material.PAPER, "_"));
      inv.setItem(37, button(Material.PAPER, "-"));
      inv.setItem(38, button(Material.ARROW, "Backspace"));
      inv.setItem(39, button(Material.BARRIER, "Clear name"));
      inv.setItem(
          40,
          button(
              Material.NAME_TAG,
              "Name: " + draft.nameInput,
              draft.nameInput.length() + " / 32 characters"));
      inv.setItem(
          49,
          button(
              Material.EMERALD_BLOCK,
              "Apply name",
              "Existing saved kits are renamed immediately",
              "Other draft changes still require Save"));
      inv.setItem(53, button(Material.ARROW, "Cancel naming"));
    } else if (screen == Screen.DELETE) {
      inv.setItem(
          22,
          button(
              Material.TNT,
              "Confirm deletion: " + draft.name,
              "Removes the saved custom kit",
              "Previous file retained as customkits.yml.bak"));
      inv.setItem(53, button(Material.ARROW, "Cancel"));
    }
    player.openInventory(inv);
  }

  private static boolean armor(Material type) {
    return List.of("_BOOTS", "_LEGGINGS", "_CHESTPLATE", "_HELMET").stream()
        .anyMatch(type.name()::endsWith);
  }

  private static boolean fits(int slot, Material type) {
    return EditorRules.equipmentFits(slot, type.name()) && Kit.supported(type);
  }

  private static void put(Draft draft, int slot, ItemStack item) {
    if (!empty(item) && !fits(slot, item.getType()))
      throw new IllegalArgumentException("That item does not fit " + slotName(slot) + ".");
    draft.items[slot] = empty(item) ? null : item.clone();
  }

  private static boolean conflicts(ItemStack item, Enchantment enchant) {
    return item.getEnchantments().keySet().stream()
        .anyMatch(
            e -> !e.equals(enchant) && (e.conflictsWith(enchant) || enchant.conflictsWith(e)));
  }

  private static void enchant(ItemStack item, Enchantment enchant, int level) {
    if (level == 0) {
      item.removeEnchantment(enchant);
      return;
    }
    if (conflicts(item, enchant))
      throw new IllegalArgumentException("Remove the conflicting enchantment first.");
    item.addUnsafeEnchantment(enchant, level);
  }

  private static void removeEnchants(ItemStack item) {
    for (Enchantment enchant : new ArrayList<>(item.getEnchantments().keySet()))
      item.removeEnchantment(enchant);
  }

  private static boolean inCategory(Material type, int category) {
    return switch (category) {
      case 0 -> Kit.melee(type) || Kit.tool(type) || type == Material.BOW
          || type == Material.CROSSBOW || type == Material.TRIDENT || type == Material.MACE
          || Kit.ammunition(type);
      case 1 -> type.isEdible();
      case 2 -> true;
      case 4 -> type.isBlock();
      case 5 -> armor(type) || type == Material.ELYTRA || type == Material.SHIELD
          || type == Material.TOTEM_OF_UNDYING || type == Material.CARVED_PUMPKIN
          || type.name().endsWith("_HEAD") || type.name().endsWith("_SKULL");
      default -> false;
    };
  }

  private static ItemStack button(Material type, String title, String... lore) {
    ItemStack item = new ItemStack(type);
    dev.insanmiy.practiceplugin.ItemMetadata.edit(item,
        meta -> {
          TextUI.name(meta, Component.text(title, NamedTextColor.AQUA));
          TextUI.lore(meta, Arrays.stream(lore).map(s -> Component.text(s, NamedTextColor.GRAY)).toList());
        });
    return item;
  }

  private static YamlConfiguration serialize(Draft draft) {
    var yaml = new YamlConfiguration();
    yaml.set("description", "Custom kit: " + draft.name);
    yaml.set("health", draft.health);
    yaml.set("natural-regeneration", draft.regeneration);
    yaml.createSection("items");
    for (int i = 0; i < 36; i++)
      if (!empty(draft.items[i])) yaml.set("items." + i, draft.items[i].clone());
    var armor = new ArrayList<ItemStack>();
    for (int i = 36; i < 40; i++)
      armor.add(empty(draft.items[i]) ? new ItemStack(Material.AIR) : draft.items[i].clone());
    yaml.set("armor", armor);
    yaml.set(
        "offhand", empty(draft.items[40]) ? new ItemStack(Material.AIR) : draft.items[40].clone());
    return yaml;
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void click(InventoryClickEvent event) {
    if (!(event.getView().getTopInventory().getHolder() instanceof View view)) return;
    event.setCancelled(true);
    if (!(event.getWhoClicked() instanceof Player player)
        || !view.owner.equals(player.getUniqueId())) return;
    if (!Set.of(ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT)
        .contains(event.getClick())) return;
    int slot = event.getRawSlot();
    boolean right = event.isRightClick(), shift = event.isShiftClick();
    ItemStack source =
        slot >= 54 && !empty(event.getCurrentItem()) ? event.getCurrentItem().clone() : null;
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (!player.isOnline()
                  || player.getOpenInventory().getTopInventory().getHolder() != view) return;
              try {
                allowed(player);
                player.playSound(
                    player.getLocation(), Sound.UI_BUTTON_CLICK, .25f, shift ? 1.4f : 1.1f);
                handle(player, view, slot, right, shift, source);
              } catch (IllegalArgumentException e) {
                player.sendMessage(TextUI.legacy(Component.text(e.getMessage(), NamedTextColor.RED)));
              } catch (IOException e) {
                player.sendMessage(TextUI.legacy(
                    Component.text(
                        "Could not complete the kit action. Check the server log before retrying.",
                        NamedTextColor.RED)));
                plugin.getLogger().warning(e.toString());
              }
            });
  }

  private void handle(
      Player player, View view, int slot, boolean right, boolean shift, ItemStack source)
      throws IOException {
    Draft draft = view.draft;
    if (slot < 0) return;
    if (view.screen == Screen.TEMPLATES) {
      int index = view.page * 45 + slot;
      if (slot < 45 && index < view.names.size())
        create(player, unusedName(), view.names.get(index));
      else if (slot == 45 || slot == 52) templates(player, view.page + (slot == 45 ? -1 : 1));
      else if (slot == 53) library(player, 0);
      return;
    }
    if (view.screen == Screen.LIBRARY) {
      int index = view.page * 45 + slot;
      if (slot < 45 && index < view.names.size()) {
        if (right) create(player, unusedName(), view.names.get(index));
        else edit(player, view.names.get(index));
      } else if (slot == 45 || slot == 52) library(player, view.page + (slot == 45 ? -1 : 1));
      else if (slot == 46) create(player, unusedName(), null);
      else if (slot == 47) templates(player, 0);
      else if (slot == 50) importKit(player, unusedName());
      else if (slot == 53) player.closeInventory();
      return;
    }
    if (view.screen == Screen.DELETE) {
      if (slot == 22) {
        store.delete(
            draft.name,
            draft.revision,
            player.getUniqueId(),
            player.hasPermission("practice.admin"));
        plugin.refreshKits();
        player.sendMessage(TextUI.legacy(
            Component.text("Deleted " + draft.name + ". Previous file: customkits.yml.bak")));
        library(player, 0);
      } else if (slot == 53) show(player, Screen.EDIT, draft, 0);
      return;
    }
    if (view.screen == Screen.PALETTE) {
      int index = view.page * 45 + slot;
      if (slot < 45 && index < view.palette.size()) {
        put(draft, draft.selected, view.palette.get(index));
        show(player, Screen.ITEM, draft, 0);
      } else if (slot == 45 || slot == 52)
        show(player, Screen.PALETTE, draft, view.page + (slot == 45 ? -1 : 1));
      else if (slot >= 46 && slot <= 51) {
        draft.paletteCategory = slot - 46;
        show(player, Screen.PALETTE, draft, 0);
      } else if (slot == 53) show(player, Screen.EDIT, draft, 0);
      return;
    }
    if (view.screen == Screen.NAME) {
      if (slot < 38)
        draft.nameInput =
            EditorRules.appendName(
                draft.nameInput, "abcdefghijklmnopqrstuvwxyz0123456789_-".charAt(slot));
      else if (slot == 38 && !draft.nameInput.isEmpty())
        draft.nameInput = draft.nameInput.substring(0, draft.nameInput.length() - 1);
      else if (slot == 39) draft.nameInput = "";
      else if (slot == 49) {
        String name = CustomKitStore.name(draft.nameInput);
        if (!name.equals(draft.name)) {
          var saved = store.read();
          if (saved.contains(name) || plugin.kits().containsKey(name))
            throw new IllegalArgumentException("Name already used.");
          if (draft.revision != null) {
            if (!Objects.equals(
                draft.revision, CustomKitStore.revision(saved.getConfigurationSection(draft.name))))
              throw new IllegalArgumentException(
                  "This kit changed. Reopen the editor before renaming it.");
            store.rename(
                draft.name,
                name,
                player.getUniqueId(),
                player.hasPermission("practice.admin"),
                plugin.kits().keySet());
            plugin.refreshKits();
            draft.revision = CustomKitStore.revision(store.read().getConfigurationSection(name));
          }
          draft.name = name;
        }
        draft.nameInput = null;
        show(player, Screen.EDIT, draft, 0);
        return;
      } else if (slot == 53) {
        draft.nameInput = null;
        show(player, Screen.EDIT, draft, 0);
        return;
      }
      show(player, Screen.NAME, draft, 0);
      return;
    }
    if (view.screen == Screen.ITEM) {
      ItemStack item = draft.items[draft.selected];
      if (slot >= 54 && source != null) {
        put(draft, draft.selected, source);
        show(player, Screen.ITEM, draft, 0);
        return;
      }
      if (slot == 19) {
        palette(player, draft);
        return;
      }
      if (slot == 21) {
        show(player, Screen.ENCHANT, draft, 0);
        return;
      }
      if (slot == 23) removeEnchants(item);
      else if (slot == 22 || slot == 24) {
        int applied = QuickEnchant.apply(item, slot == 24);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, .45f, 1.2f);
        player.sendMessage(TextUI.legacy(
            Component.text(
                "Applied "
                    + applied
                    + " enchantment upgrades; incompatible/conflicting choices skipped.")));
      } else if (slot == 25) {
        draft.items[draft.selected] = null;
        show(player, Screen.EDIT, draft, 0);
        return;
      } else if (slot >= 28 && slot <= 32)
        item.setAmount(
            EditorRules.amount(new int[] {1, 8, 16, 32, 64}[slot - 28], item.getMaxStackSize()));
      else if (slot == 48 || slot == 50)
        item.setAmount(
            EditorRules.amount(
                item.getAmount() + (slot == 48 ? -1 : 1) * (shift ? 16 : 1),
                item.getMaxStackSize()));
      else if (slot == 34) {
        int filled = 0;
        for (int i = 9; i < 36; i++)
          if (empty(draft.items[i])) {
            put(draft, i, item);
            filled++;
          }
        player.sendMessage(TextUI.legacy(
            Component.text("Filled " + filled + " empty reserve slots. Existing items kept.")));
      } else if (slot == 40) {
        if (!armor(item.getType()))
          throw new IllegalArgumentException("Select an armor piece first.");
        for (int i = 36; i < 40; i++) {
          ItemStack target = draft.items[i];
          if (empty(target)) continue;
          item.getEnchantments()
              .forEach(
                  (e, level) -> {
                    if (e.canEnchantItem(target) && !conflicts(target, e))
                      target.addUnsafeEnchantment(e, Math.max(target.getEnchantmentLevel(e), level));
                  });
        }
        player.sendMessage(TextUI.legacy(Component.text("Copied compatible enchantments to equipped armor.")));
      } else if (slot == 45 || slot == 53) {
        show(player, Screen.EDIT, draft, 0);
        return;
      }
      show(player, Screen.ITEM, draft, 0);
      return;
    }
    if (view.screen == Screen.LEVEL) {
      if (slot >= 10 && slot <= 10 + Math.min(30, draft.enchantment.getMaxLevel())) {
        enchant(draft.items[draft.selected], draft.enchantment, slot - 10);
        show(player, Screen.ENCHANT, draft, 0);
      } else if (slot == 53) show(player, Screen.ENCHANT, draft, 0);
      return;
    }
    if (view.screen == Screen.ENCHANT) {
      if (slot < view.enchants.size()) {
        Enchantment enchant = view.enchants.get(slot);
        ItemStack item = draft.items[draft.selected];
        if (right) {
          enchant(item, enchant, 0);
          show(player, Screen.ENCHANT, draft, 0);
        } else if (shift) {
          enchant(item, enchant, enchant.getMaxLevel());
          show(player, Screen.ENCHANT, draft, 0);
        } else {
          draft.enchantment = enchant;
          show(player, Screen.LEVEL, draft, 0);
        }
      } else if (slot == 48) {
        removeEnchants(draft.items[draft.selected]);
        show(player, Screen.ENCHANT, draft, 0);
      } else if (slot == 53) show(player, Screen.ITEM, draft, 0);
      return;
    }
    if (slot >= 54) {
      if (source != null) put(draft, draft.selected, source);
    } else if (slot < 41) {
      if (right) draft.items[slot] = null;
      else if (shift) put(draft, slot, draft.items[draft.selected]);
      else {
        draft.selected = slot;
        if (empty(draft.items[slot])) palette(player, draft);
        else show(player, Screen.ITEM, draft, 0);
        return;
      }
    } else
      switch (slot) {
        case 42, 43 -> {
          ItemStack item = draft.items[draft.selected];
          if (!empty(item))
            item.setAmount(
                Math.max(
                    1,
                    Math.min(
                        item.getMaxStackSize(),
                        item.getAmount() + (slot == 42 ? -1 : 1) * (shift ? 16 : 1))));
        }
        case 44 -> {
          palette(player, draft);
          return;
        }
        case 45 -> {
          library(player, 0);
          return;
        }
        case 46 -> draft.health = Math.max(2, draft.health - 2);
        case 47 -> draft.health = Math.min(100, draft.health + 2);
        case 48 -> draft.regeneration = !draft.regeneration;
        case 49 -> {
          save(player, draft.name, serialize(draft), draft.revision);
          draft.revision =
              CustomKitStore.revision(store.read().getConfigurationSection(draft.name));
          player.sendMessage(TextUI.legacy(
              Component.text("Saved " + draft.name + ". Available immediately in /practice.")));
        }
        case 50 -> {
          importInventory(player, draft);
          player.sendMessage(TextUI.legacy(Component.text("Inventory imported. Click Save to keep these changes.")));
        }
        case 51 -> {
          show(player, Screen.ENCHANT, draft, 0);
          return;
        }
        case 52 -> {
          draft.nameInput = draft.name;
          show(player, Screen.NAME, draft, 0);
          return;
        }
        case 53 -> {
          if (draft.revision == null)
            throw new IllegalArgumentException("This draft is not saved. Use Back to discard it.");
          show(player, Screen.DELETE, draft, 0);
          return;
        }
        default -> {}
      }
    show(player, Screen.EDIT, draft, 0);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void drag(InventoryDragEvent event) {
    if (event.getView().getTopInventory().getHolder() instanceof View) event.setCancelled(true);
  }

  void closeAll() {
    for (Player player : Bukkit.getOnlinePlayers())
      if (player.getOpenInventory().getTopInventory().getHolder() instanceof View)
        player.closeInventory();
  }
}
