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
  private final Map<UUID, Draft> pendingNames = new java.util.concurrent.ConcurrentHashMap<>();

  KitEditor(PracticePlugin plugin) {
    this.plugin = plugin;
    store = new CustomKitStore(plugin.getDataFolder().toPath().resolve("customkits.yml"));
  }

  private static final class Draft {
    String name, revision, description;
    final ItemStack[] items = new ItemStack[41];
    double health = 20;
    boolean regeneration = true;
    int selected;
    int paletteCategory = 2;
    Enchantment enchantment;
    String nameInput;

    Draft(String name) {
      this.name = name;
      this.description = "Custom Kit: " + name;
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
                      ? (screen == Screen.LIBRARY ? "Custom Kits / " : "Copy A Template / ")
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
        case LIBRARY -> new int[] {48, 51, 53};
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
      throw new IllegalArgumentException("Missing Permission: practice.kits");
    if (plugin.sessionFor(player) != null)
      throw new IllegalArgumentException("Stop Practice Before Editing Kits.");
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
          "/practice Kit <Create|Import|Edit|Save|Delete|Copy|Rename> <Name>");
    String name = CustomKitStore.name(args[2]);
    switch (action) {
      case "create" -> create(player, name, args.length > 3 ? args[3] : null);
      case "import" -> importKit(player, name);
      case "edit" -> edit(player, name);
      case "save" -> {
        var saved = store.read();
        var existingSection = saved.getConfigurationSection(name);
        if (existingSection != null && !CustomKitStore.editable(existingSection, player.getUniqueId(), player.hasPermission("practice.admin"))) {
          throw new IllegalArgumentException("You Can Only Overwrite Your Own Custom Kits.");
        }
        if (existingSection == null && (plugin.kits().containsKey(name) || saved.contains(name))) {
          throw new IllegalArgumentException("Name Already Used By A Built-In Kit.");
        }
        String revision = existingSection != null ? CustomKitStore.revision(existingSection) : null;
        var candidate = new YamlConfiguration();
        Kit.capture(player, candidate);
        save(player, name, candidate, revision);
        player.sendMessage(TextUI.legacy(Component.text("Saved " + name + ". Your Inventory Was Not Changed.")));
      }
      case "copy" -> {
        if (args.length != 4)
          throw new IllegalArgumentException("/practice Kit Copy <Source> <New Name>");
        create(player, CustomKitStore.name(args[3]), name);
      }
      case "rename" -> {
        if (args.length != 4)
          throw new IllegalArgumentException("/practice Kit Rename <Old Name> <New Name>");
        store.rename(
            name,
            args[3],
            player.getUniqueId(),
            player.hasPermission("practice.admin"),
            plugin.kits().keySet());
        plugin.refreshKits();
        player.sendMessage(TextUI.legacy(Component.text("Renamed Kit To " + CustomKitStore.name(args[3]))));
        library(player, 0);
      }
      case "delete" -> {
        if (args.length >= 4 && args[3].equalsIgnoreCase("confirm")) {
          store.delete(
              name,
              null,
              player.getUniqueId(),
              player.hasPermission("practice.admin"));
          plugin.refreshKits();
          player.sendMessage(TextUI.legacy(
              Component.text("Deleted " + name + ". Previous File: customkits.yml.bak")));
          return;
        }
        Draft draft = existing(player, name);
        show(player, Screen.DELETE, draft, 0);
      }
      default ->
          throw new IllegalArgumentException(
              "Use Editor, Create, Import, Edit, Save, Copy, Rename Or Delete.");
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
      throw new IllegalArgumentException("Name Already Used. Choose A New Name.");
    Draft draft = new Draft(name);
    if (template != null) {
      Kit kit = plugin.kits().get(template.toLowerCase(Locale.ROOT));
      if (kit == null) throw new IllegalArgumentException("Unknown Template Kit.");
      kit.previewItems().forEach((slot, item) -> draft.items[slot] = item);
      draft.health = kit.health();
      draft.regeneration = kit.regeneration();
      draft.description = kit.description();
    } else draft.items[0] = new ItemStack(Material.DIAMOND_SWORD);
    show(player, Screen.EDIT, draft, 0);
    player.sendMessage(TextUI.legacy(
        Component.text(
            "Editing " + name + ". Click Save To Keep It. Closing Discards Unsaved Changes.")));
  }

  private Draft existing(Player player, String name) throws IOException {
    name = CustomKitStore.name(name);
    var section = store.read().getConfigurationSection(name);
    if (section == null)
      throw new IllegalArgumentException("Custom Kit '" + name + "' Does Not Exist.");
    if (!CustomKitStore.editable(
        section, player.getUniqueId(), player.hasPermission("practice.admin")))
      throw new IllegalArgumentException(
          "You Can Only Edit Your Own Custom Kits. Copy A Preset To Customize It.");
    Draft draft = new Draft(name);
    draft.revision = CustomKitStore.revision(section);
    try {
      Kit kit = Kit.read(name, section);
      draft.description = kit.description();
      kit.previewItems().forEach((slot, item) -> draft.items[slot] = item);
      draft.health = kit.health();
      draft.regeneration = kit.regeneration();
    } catch (Exception e) {
      plugin.getLogger().warning("Could not fully parse kit " + name + ": " + e.getMessage());
      draft.description = section.getString("description", "Custom Kit: " + name);
      draft.health = section.getDouble("health", 20.0);
      draft.regeneration = section.getBoolean("natural-regeneration", true);
    }
    return draft;
  }

  private static void importInventory(Player player, Draft draft) {
    for (int i = 0; i < 36; i++) {
      ItemStack item = player.getInventory().getItem(i);
      if (!empty(item) && fits(i, item.getType())) {
        draft.items[i] = item.clone();
      } else {
        draft.items[i] = null;
      }
    }
    ItemStack[] armor = player.getInventory().getArmorContents();
    for (int i = 0; i < 4; i++) {
      ItemStack item = (armor != null && i < armor.length) ? armor[i] : null;
      int slot = 36 + i;
      if (!empty(item) && fits(slot, item.getType())) {
        draft.items[slot] = item.clone();
      } else {
        draft.items[slot] = null;
      }
    }
    ItemStack offhand = player.getInventory().getItemInOffHand();
    if (!empty(offhand) && fits(40, offhand.getType())) {
      draft.items[40] = offhand.clone();
    } else {
      draft.items[40] = null;
    }
  }

  private void importKit(Player player, String name) throws IOException {
    allowed(player);
    name = CustomKitStore.name(name);
    var saved = store.read();
    var existingSection = saved.getConfigurationSection(name);
    if (existingSection != null && !CustomKitStore.editable(existingSection, player.getUniqueId(), player.hasPermission("practice.admin"))) {
      throw new IllegalArgumentException("You Can Only Overwrite Your Own Custom Kits.");
    }
    if (existingSection == null && (plugin.kits().containsKey(name) || saved.contains(name))) {
      throw new IllegalArgumentException("Name Already Used By A Built-In Kit.");
    }
    Draft draft = new Draft(name);
    if (existingSection != null) {
      draft.revision = CustomKitStore.revision(existingSection);
    }
    draft.description = "Saved By " + player.getName();
    importInventory(player, draft);
    save(player, draft.name, serialize(draft), draft.revision);
    draft.revision = CustomKitStore.revision(store.read().getConfigurationSection(draft.name));
    show(player, Screen.EDIT, draft, 0);
    player.sendMessage(TextUI.legacy(
        Component.text("Imported Your Inventory Into " + name + " And Saved Successfully!")));
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
              "Left: Edit | Right: Duplicate | Shift-Right: Delete",
              "/practice kit rename " + name + " <new-name>"));
    }
    view.inventory.setItem(
        45,
        page == 0
            ? button(Material.ARROW, "Back To Main Menu", "Return To Kit Selection")
            : button(Material.ARROW, "Previous Page"));
    view.inventory.setItem(
        46, button(Material.EMERALD, "Create Blank Kit", "No Fixed Kit-Count Limit"));
    view.inventory.setItem(
        47,
        button(
            Material.CHEST, "Browse Kit Templates", "Choose Any Kit To Copy Into Your Own Draft"));
    view.inventory.setItem(
        49,
        button(
            Material.BOOK,
            names.size() + " Custom Kits",
            "/practice kit create <name> [template]"));
    view.inventory.setItem(52, button(Material.ARROW, "Next Page"));
    view.inventory.setItem(
        50,
        button(
            Material.HOPPER,
            "Create From Your Inventory",
            "Copies Hotbar, Inventory, Armor And Offhand Into A New Draft",
            "Your Real Items Stay Untouched; Click Save When Ready",
            "/practice kit import <name>"));
    player.openInventory(view.inventory);
  }

  private static boolean canEnchant(ItemStack item) {
    if (empty(item)) return false;
    for (Enchantment e : Registry.ENCHANTMENT) if (e.canEnchantItem(item)) return true;
    return false;
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
              kit.health() + " HP | Click To Create An Independent Copy"));
    }
    view.inventory.setItem(45, button(Material.ARROW, "Previous Page"));
    view.inventory.setItem(
        49, button(Material.BOOK, "Choose A Starting Loadout", "Original Kits Are Never Modified"));
    view.inventory.setItem(52, button(Material.ARROW, "Next Page"));
    view.inventory.setItem(53, button(Material.BARRIER, "Back To Your Kits"));
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
    if (screen == Screen.ITEM && empty(draft.items[draft.selected])) {
      palette(player, draft);
      return;
    }
    if (screen == Screen.ENCHANT && empty(draft.items[draft.selected])) {
      palette(player, draft);
      return;
    }
    if (screen == Screen.ENCHANT && !canEnchant(draft.items[draft.selected]))
      throw new IllegalArgumentException("This Item Cannot Be Enchanted.");
    pendingNames.remove(player.getUniqueId());

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
                    slotName(i) + " / Empty",
                    "Click To Choose An Item From The GUI",
                    "Shift-Left: Paste Selected Item | Right: Clear")
                : displayItem(
                    draft.items[i],
                    slotName(i),
                    "Click: Item Settings And Enchantments",
                    "Shift-Left: Paste Selected Item | Right: Clear"));
      inv.setItem(
          41,
          button(
              Material.COMPASS,
              "Selected: " + slotName(draft.selected),
              "Slots 0-8 (Top Row) Are The Hotbar",
              "Click An Item: Settings / Enchants",
              "Click Empty Slot: Item Picker",
              "Shift-Left Another Slot: Copy Selected Into It",
              "Click Your Inventory Below To Copy An Item Here"));
      inv.setItem(42, button(Material.RED_DYE, "Amount -1", "Shift: -16"));
      inv.setItem(43, button(Material.LIME_DYE, "Amount +1", "Shift: +16 (Native Stack Limits)"));
      inv.setItem(
          44,
          button(
              Material.CHEST, "Choose / Replace Selected Item", "Opens Categorized Item Picker"));
      inv.setItem(45, button(Material.ARROW, "Discard Changes / Back"));
      inv.setItem(46, button(Material.REDSTONE, "Health -2 / " + (int) draft.health + " HP"));
      inv.setItem(47, button(Material.GLOWSTONE_DUST, "Health +2 / " + (int) draft.health + " HP"));
      inv.setItem(48, button(Material.COOKED_BEEF, "Natural Regeneration: " + draft.regeneration));
      inv.setItem(
          49,
          button(
              Material.EMERALD_BLOCK,
              "Save " + draft.name,
              "Validates And Saves To Disk",
              "Both Fighters Get Independent Copies"));
      inv.setItem(
          50,
          button(
              Material.HOPPER,
              "Import Your Inventory",
              "Replaces Draft Gear; Real Items Untouched"));
      inv.setItem(51, button(Material.ENCHANTING_TABLE, "Enchant Selected Item"));
      inv.setItem(
          52,
          button(Material.WRITABLE_BOOK, "Name This Kit", "Click, Then Type A Name In Chat"));
      inv.setItem(53, button(Material.BARRIER, "Delete Saved Kit", "Confirmation Required"));
    } else if (screen == Screen.ITEM) {
      ItemStack item = draft.items[draft.selected];
      inv.setItem(
          4,
          button(
              Material.COMPASS,
              slotName(draft.selected),
              "Editing This Slot Only",
              "All Changes Stay In The Draft Until Save"));
      inv.setItem(13, displayItem(item, "Current Item / " + slotName(draft.selected)));
      inv.setItem(
          19,
          button(
              Material.CHEST,
              "Choose A Different Item",
              "Browse All Items, Equipment, Food, Potions And Blocks"));
      inv.setItem(
          21,
          button(
              Material.ENCHANTING_TABLE,
              "Add / Edit Enchantments",
              "Pick An Enchantment, Then Choose Its Level"));
      inv.setItem(
          23, button(Material.GRINDSTONE, "Remove All Enchantments", "Only Changes This Item"));
      inv.setItem(
          22,
          button(
              Material.NETHER_STAR,
              "Fast Enchant: Combat / Tool Essentials",
              "Max Compatible Damage, Protection, Efficiency And Durability",
              "Skips Conflicting Enchantments"));
      inv.setItem(
          24,
          button(
              Material.EXPERIENCE_BOTTLE,
              "Fast Enchant: Mending + Unbreaking",
              "One Click; Respects Conflicts Such As Infinity"));
      inv.setItem(25, button(Material.BARRIER, "Clear This Slot"));
      int[] amounts = {1, 8, 16, 32, 64};
      for (int i = 0; i < amounts.length; i++) {
        int count = EditorRules.amount(amounts[i], item.getMaxStackSize());
        inv.setItem(
            28 + i,
            button(
                Material.PAPER,
                "Set Amount: " + count,
                "Native Maximum: " + item.getMaxStackSize()));
      }
      inv.setItem(
          34,
          button(
              Material.CHEST_MINECART,
              "Fill Empty Inventory Slots With This Item",
              "Slots 9-35 Only; Existing Items Are Preserved"));
      inv.setItem(
          40,
          button(
              Material.ARMOR_STAND,
              "Copy Enchantments To Matching Armor",
              "Only Compatible, Non-Conflicting Enchants Are Copied",
              "Existing Armor Enchantments Are Preserved"));
      inv.setItem(45, button(Material.ARROW, "Back To Loadout"));
      inv.setItem(48, button(Material.RED_DYE, "Amount -1", "Shift: -16"));
      inv.setItem(
          49,
          button(Material.BOOK, "Amount: " + item.getAmount() + " / " + item.getMaxStackSize()));
      inv.setItem(50, button(Material.LIME_DYE, "Amount +1", "Shift: +16"));
      inv.setItem(53, button(Material.ARROW, "Back To Loadout"));
    } else if (screen == Screen.PALETTE) {
      if (draft.paletteCategory == 3 && fits(draft.selected, Material.SPLASH_POTION))
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
                "Click To Use In " + slotName(draft.selected),
                "Then Edit Amount And Enchantments"));
      if (view.palette.isEmpty())
        inv.setItem(
            22,
            button(
                Material.BARRIER,
                "No Items For This Slot / Category",
                "Try The All Items Category"));
      inv.setItem(45, button(Material.ARROW, "Previous Page"));
      Material[] icons = {
        Material.DIAMOND_SWORD,
        Material.COOKED_BEEF,
        Material.EXPERIENCE_BOTTLE,
        Material.SPLASH_POTION,
        Material.COBBLESTONE,
        Material.DIAMOND_CHESTPLATE
      };
      String[] categories = {
        "Weapons", "Food", "All Items", "Potions / Tipped Arrows", "Blocks", "Equipment"
      };
      for (int i = 0; i < categories.length; i++)
        inv.setItem(
            46 + i,
            button(
                icons[i],
                (i == draft.paletteCategory ? "Selected: " : "") + categories[i],
                "Page " + (page + 1) + " / " + (maxPage + 1),
                "Copies To " + slotName(draft.selected)));
      inv.setItem(52, button(Material.ARROW, "Next Page"));
      inv.setItem(53, button(Material.BARRIER, "Back To Editor"));
    } else if (screen == Screen.ENCHANT) {
      ItemStack item = draft.items[draft.selected];
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
                "Click: Exact Level | Shift-Left: Max | Right: Remove",
                conflicts(item, e)
                    ? "Conflict: Remove The Incompatible Enchantment First"
                    : "Compatible With Selected Item",
                "Maximum Vanilla Level: " + e.getMaxLevel()));
      }
      if (view.enchants.isEmpty())
        inv.setItem(22, button(Material.BARRIER, "This Item Has No Compatible Enchantments"));
      inv.setItem(48, button(Material.GRINDSTONE, "Remove All Enchantments"));
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
              "Current Level: " + item.getEnchantmentLevel(enchant),
              "Choose A Level Below"));
      inv.setItem(10, button(Material.GRINDSTONE, "Remove This Enchantment"));
      for (int level = 1; level <= Math.min(30, enchant.getMaxLevel()); level++)
        inv.setItem(
            10 + level,
            button(
                Material.ENCHANTED_BOOK,
                "Level " + level,
                conflicts(item, enchant)
                    ? "Conflicts With An Existing Enchantment"
                    : "Click To Apply"));
      inv.setItem(49, displayItem(item, "Your Item"));
      inv.setItem(53, button(Material.ARROW, "Back To Enchantments"));
    } else if (screen == Screen.NAME) {
      if (draft.nameInput == null) draft.nameInput = draft.name;
      String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
      for (int i = 0; i < alphabet.length(); i++)
        inv.setItem(i, button(Material.PAPER, "" + alphabet.charAt(i)));
      inv.setItem(36, button(Material.PAPER, "_"));
      inv.setItem(37, button(Material.PAPER, "-"));
      inv.setItem(38, button(Material.ARROW, "Backspace"));
      inv.setItem(39, button(Material.BARRIER, "Clear Name"));
      inv.setItem(
          40,
          button(
              Material.NAME_TAG,
              "Name: " + draft.nameInput,
              draft.nameInput.length() + " / 32 Characters"));
      inv.setItem(
          49,
          button(
              Material.EMERALD_BLOCK,
              "Apply Name",
              "Existing Saved Kits Are Renamed Immediately",
              "Other Draft Changes Still Require Save"));
      inv.setItem(53, button(Material.ARROW, "Cancel Naming"));
    } else if (screen == Screen.DELETE) {
      inv.setItem(
          22,
          button(
              Material.TNT,
              "Confirm Deletion: " + draft.name,
              "Removes The Saved Custom Kit",
              "Previous File Retained As customkits.yml.bak"));
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
      throw new IllegalArgumentException("That Item Does Not Fit " + slotName(slot) + ".");
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
      throw new IllegalArgumentException("Remove The Conflicting Enchantment First.");
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
    yaml.set("description", draft.description != null ? draft.description : "Custom Kit: " + draft.name);
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
    if (!Set.of(ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT, ClickType.CREATIVE)
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
                        "Could Not Complete The Kit Action. Check The Server Log Before Retrying.",
                        NamedTextColor.RED)));
                plugin.getLogger().warning(e.toString());
              } catch (Throwable t) {
                player.sendMessage(TextUI.legacy(
                    Component.text(
                        "An unexpected error occurred while processing this kit action.",
                        NamedTextColor.RED)));
                plugin.getLogger().severe("Unexpected error in KitEditor: " + t.getMessage());
                t.printStackTrace();
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
        String kitName = view.names.get(index);
        if (shift && right) {
          show(player, Screen.DELETE, existing(player, kitName), 0);
        } else if (right) {
          create(player, unusedName(), kitName);
        } else {
          edit(player, kitName);
        }
        return;
      } else if (slot == 45 && view.page == 0) {
        player.closeInventory();
        Bukkit.dispatchCommand(player, "practice menu");
      } else if (slot == 45 || slot == 52) library(player, view.page + (slot == 45 ? -1 : 1));
      else if (slot == 46) create(player, unusedName(), null);
      else if (slot == 47) templates(player, 0);
      else if (slot == 50) importKit(player, unusedName());
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
            Component.text("Deleted " + draft.name + ". Previous File: customkits.yml.bak")));
        library(player, 0);
      } else if (slot == 53) {
        if (draft.revision == null) library(player, 0);
        else show(player, Screen.EDIT, draft, 0);
      }
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
            throw new IllegalArgumentException("Name Already Used.");
          if (draft.revision != null) {
            if (!Objects.equals(
                draft.revision, CustomKitStore.revision(saved.getConfigurationSection(draft.name))))
              throw new IllegalArgumentException(
                  "This Kit Changed. Reopen The Editor Before Renaming It.");
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
        if (!canEnchant(item)) throw new IllegalArgumentException("This Item Cannot Be Enchanted.");
        show(player, Screen.ENCHANT, draft, 0);
        return;
      }
      if (slot == 23) removeEnchants(item);
      else if (slot == 22 || slot == 24) {
        if (!canEnchant(item)) throw new IllegalArgumentException("This Item Cannot Be Enchanted.");
        int applied = QuickEnchant.apply(item, slot == 24);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, .45f, 1.2f);
        player.sendMessage(TextUI.legacy(
            Component.text(
                "Applied "
                    + applied
                    + " Enchantment Upgrades; Incompatible/Conflicting Choices Skipped.")));
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
            Component.text("Filled " + filled + " Empty Reserve Slots. Existing Items Kept.")));
      } else if (slot == 40) {
        if (!armor(item.getType()))
          throw new IllegalArgumentException("Select An Armor Piece First.");
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
        player.sendMessage(TextUI.legacy(Component.text("Copied Compatible Enchantments To Equipped Armor.")));
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
      int maxEnchants = Math.min(45, view.enchants.size());
      if (slot < maxEnchants) {
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
              Component.text("Saved " + draft.name + ". Available Immediately In /practice.")));
        }
        case 50 -> {
          importInventory(player, draft);
          int count = 0;
          for (ItemStack it : draft.items) {
            if (!empty(it)) count++;
          }
          player.sendMessage(TextUI.legacy(Component.text(
              "Imported " + count + " items from your inventory. Click Save to keep these changes.")));
        }
        case 51 -> {
          if (!canEnchant(draft.items[draft.selected]))
            throw new IllegalArgumentException("This Item Cannot Be Enchanted.");
          show(player, Screen.ENCHANT, draft, 0);
          return;
        }
        case 52 -> {
          pendingNames.put(player.getUniqueId(), draft);
          player.closeInventory();
          player.sendMessage(TextUI.legacy(
              Component.text(
                  "Type The New Kit Name In Chat (Or Type 'cancel' To Cancel).",
                  NamedTextColor.YELLOW)));
          return;
        }
        case 53 -> {
          if (draft.revision == null) {
            player.sendMessage(TextUI.legacy(Component.text("Draft Discarded.", NamedTextColor.GRAY)));
            library(player, 0);
            return;
          }
          show(player, Screen.DELETE, draft, 0);
          return;
        }
        default -> {}
      }
    show(player, Screen.EDIT, draft, 0);
  }

  @EventHandler
  public void quit(org.bukkit.event.player.PlayerQuitEvent event) {
    pendingNames.remove(event.getPlayer().getUniqueId());
  }

  @SuppressWarnings("deprecation")
  @EventHandler(priority = EventPriority.LOWEST)
  public void chat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
    Player player = event.getPlayer();
    Draft draft = pendingNames.remove(player.getUniqueId());
    if (draft == null) return;
    event.setCancelled(true);
    String input = event.getMessage().trim();
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (!player.isOnline()) return;
              if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage(
                    TextUI.legacy(Component.text("Kit Naming Cancelled.", NamedTextColor.GRAY)));
                show(player, Screen.EDIT, draft, 0);
                return;
              }
              try {
                allowed(player);
                String name = CustomKitStore.name(input);
                if (!name.equals(draft.name)) {
                  var saved = store.read();
                  if (saved.contains(name) || plugin.kits().containsKey(name))
                    throw new IllegalArgumentException("Name Already Used.");
                  if (draft.revision != null) {
                    if (!Objects.equals(
                        draft.revision,
                        CustomKitStore.revision(saved.getConfigurationSection(draft.name))))
                      throw new IllegalArgumentException(
                          "This Kit Changed. Reopen The Editor Before Renaming It.");
                    store.rename(
                        draft.name,
                        name,
                        player.getUniqueId(),
                        player.hasPermission("practice.admin"),
                        plugin.kits().keySet());
                    plugin.refreshKits();
                    draft.revision =
                        CustomKitStore.revision(store.read().getConfigurationSection(name));
                  }
                  draft.name = name;
                }
                player.sendMessage(
                    TextUI.legacy(
                        Component.text("Kit Named " + draft.name + ".", NamedTextColor.GREEN)));
              } catch (IllegalArgumentException e) {
                player.sendMessage(TextUI.legacy(Component.text(e.getMessage(), NamedTextColor.RED)));
              } catch (IOException e) {
                player.sendMessage(
                    TextUI.legacy(
                        Component.text(
                            "Could Not Complete The Kit Action. Check The Server Log Before Retrying.",
                            NamedTextColor.RED)));
                plugin.getLogger().warning(e.toString());
              } catch (Throwable t) {
                player.sendMessage(TextUI.legacy(
                    Component.text(
                        "An unexpected error occurred while naming kit.",
                        NamedTextColor.RED)));
                plugin.getLogger().severe("Unexpected error naming kit in KitEditor: " + t.getMessage());
                t.printStackTrace();
              }
              show(player, Screen.EDIT, draft, 0);
            });
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
