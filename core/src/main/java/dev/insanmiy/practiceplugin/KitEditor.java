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
      this.description = Messages.text("text.kit-editor.custom-kit") + name;
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
              Messages.component(
                  screen == Screen.LIBRARY || screen == Screen.TEMPLATES
                      ? (screen == Screen.LIBRARY ? Messages.text("text.kit-editor.custom-kits") : Messages.text("text.kit-editor.copy-a-template"))
                          + (page + 1)
                      : Messages.text("text.kit-editor.screen-" + screen.name().toLowerCase(Locale.ROOT))
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
    if (!canEditor(player)
        && !canCreate(player)
        && !canDelete(player)
        && !canImport(player))
      throw new IllegalArgumentException(Messages.text("text.kit-editor.missing-permission-practice-kits"));
    if (plugin.sessionFor(player) != null)
      throw new IllegalArgumentException(Messages.text("text.kit-editor.stop-practice-before-editing-kits"));
  }

  boolean hasBypass(Player player) {
    return player.hasPermission("practice.admin")
        || player.hasPermission("practice.*")
        || player.hasPermission("practice.kits.bypass");
  }

  boolean canCreate(Player player) {
    return player.hasPermission("practice.admin")
        || player.hasPermission("practice.*")
        || player.hasPermission("practice.kits.create");
  }

  boolean canDelete(Player player) {
    return player.hasPermission("practice.admin")
        || player.hasPermission("practice.*")
        || player.hasPermission("practice.kits.delete");
  }

  boolean canImport(Player player) {
    return player.hasPermission("practice.admin")
        || player.hasPermission("practice.*")
        || player.hasPermission("practice.kits.import");
  }

  boolean canEditor(Player player) {
    return player.hasPermission("practice.admin")
        || player.hasPermission("practice.*")
        || player.hasPermission("practice.kits.editor");
  }

  void command(Player player, String[] args) throws IOException {
    allowed(player);
    String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "editor";
    if (action.equals("editor")) {
      if (!canEditor(player)) throw new IllegalArgumentException(Messages.text("text.kit-editor.missing-permission-practice-kits-editor"));
      library(player, 0);
      return;
    }
    if (args.length < 3)
      throw new IllegalArgumentException(
          plugin.messages().get("commands.kit-command-usage"));
    String name = CustomKitStore.name(args[2]);
    switch (action) {
      case "create" -> {
        if (!canCreate(player)) throw new IllegalArgumentException(Messages.text("text.kit-editor.missing-permission-practice-kits-create"));
        create(player, name, args.length > 3 ? args[3] : null);
      }
      case "import" -> {
        if (!canImport(player)) throw new IllegalArgumentException(Messages.text("text.kit-editor.missing-permission-practice-kits-import"));
        importKit(player, name);
      }
      case "edit" -> {
        if (!canEditor(player)) throw new IllegalArgumentException(Messages.text("text.kit-editor.missing-permission-practice-kits-editor"));
        edit(player, name);
      }
      case "save" -> {
        var saved = store.read();
        var existingSection = saved.getConfigurationSection(name);
        if (existingSection == null && !canCreate(player))
          throw new IllegalArgumentException(Messages.text("text.kit-editor.missing-permission-practice-kits-create"));
        if (existingSection != null && !canEditor(player))
          throw new IllegalArgumentException(Messages.text("text.kit-editor.missing-permission-practice-kits-editor"));
        if (existingSection != null && !CustomKitStore.editable(existingSection, player.getUniqueId(), hasBypass(player))) {
          throw new IllegalArgumentException(Messages.text("text.kit-editor.you-can-only-overwrite-your-own"));
        }
        if (existingSection == null && (plugin.kits().containsKey(name) || saved.contains(name))) {
          throw new IllegalArgumentException(Messages.text("text.kit-editor.name-already-used-by-a-built"));
        }
        String revision = existingSection != null ? CustomKitStore.revision(existingSection) : null;
        var candidate = new YamlConfiguration();
        Kit.capture(player, candidate);
        save(player, name, candidate, revision);
        plugin.messages().send(player, "kit.saved", "%name%", name);
      }
      case "copy" -> {
        if (!canCreate(player)) throw new IllegalArgumentException(Messages.text("text.kit-editor.missing-permission-practice-kits-create"));
        if (args.length != 4)
          throw new IllegalArgumentException(Messages.text("text.kit-editor.practice-kit-copy-source-new-name"));
        create(player, CustomKitStore.name(args[3]), name);
      }
      case "rename" -> {
        if (!canCreate(player) && !canEditor(player)) throw new IllegalArgumentException(Messages.text("text.kit-editor.missing-permission-practice-kits"));
        if (args.length != 4)
          throw new IllegalArgumentException(Messages.text("text.kit-editor.practice-kit-rename-old-name-new"));
        store.rename(
            name,
            args[3],
            player.getUniqueId(),
            hasBypass(player),
            plugin.kits().keySet());
        plugin.refreshKits();
        plugin.messages().send(player, "kit.named", "%name%", CustomKitStore.name(args[3]));
        library(player, 0);
      }
      case "delete" -> {
        if (!canDelete(player)) throw new IllegalArgumentException(Messages.text("text.kit-editor.missing-permission-practice-kits-delete"));
        if (args.length >= 4 && args[3].equalsIgnoreCase("confirm")) {
          store.delete(
              name,
              null,
              player.getUniqueId(),
              hasBypass(player));
          plugin.refreshKits();
          plugin.messages().send(player, "kit.deleted", "%name%", name);
          return;
        }
        Draft draft = existing(player, name);
        show(player, Screen.DELETE, draft, 0);
      }
      default ->
          throw new IllegalArgumentException(
              Messages.text("text.kit-editor.use-editor-create-import-edit-save"));
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
        hasBypass(player),
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
    if (!canCreate(player)) throw new IllegalArgumentException(Messages.text("text.kit-editor.missing-permission-practice-kits-create"));
    name = CustomKitStore.name(name);
    if (plugin.kits().containsKey(name) || store.read().contains(name))
      throw new IllegalArgumentException(Messages.text("text.kit-editor.name-already-used-choose-a-new"));
    Draft draft = new Draft(name);
    if (template != null) {
      Kit kit = plugin.kits().get(template.toLowerCase(Locale.ROOT));
      if (kit == null) throw new IllegalArgumentException(Messages.text("text.kit-editor.unknown-template-kit"));
      kit.previewItems().forEach((slot, item) -> draft.items[slot] = item);
      draft.health = kit.health();
      draft.regeneration = kit.regeneration();
      draft.description = kit.description();
    } else draft.items[0] = new ItemStack(Material.DIAMOND_SWORD);
    show(player, Screen.EDIT, draft, 0);
    plugin.messages().send(player, "kit.editing", "%name%", name);
  }

  private Draft existing(Player player, String name) throws IOException {
    name = CustomKitStore.name(name);
    var section = store.read().getConfigurationSection(name);
    if (section == null)
      throw new IllegalArgumentException(Messages.text("text.kit-editor.custom-kit-2") + name + Messages.text("text.kit-editor.does-not-exist"));
    if (!CustomKitStore.editable(
        section, player.getUniqueId(), hasBypass(player)))
      throw new IllegalArgumentException(
          Messages.text("text.kit-editor.you-can-only-edit-your-own"));
    Draft draft = new Draft(name);
    draft.revision = CustomKitStore.revision(section);
    try {
      Kit kit = Kit.read(name, section);
      draft.description = kit.description();
      kit.previewItems().forEach((slot, item) -> draft.items[slot] = item);
      draft.health = kit.health();
      draft.regeneration = kit.regeneration();
    } catch (Exception e) {
      plugin.getLogger().warning(Messages.text("text.kit-editor.could-not-fully-parse-kit") + name + ": " + e.getMessage());
      draft.description = section.getString("description", Messages.text("text.kit-editor.custom-kit") + name);
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
    if (!canImport(player)) throw new IllegalArgumentException(Messages.text("text.kit-editor.missing-permission-practice-kits-import"));
    name = CustomKitStore.name(name);
    var saved = store.read();
    var existingSection = saved.getConfigurationSection(name);
    if (existingSection != null && !CustomKitStore.editable(existingSection, player.getUniqueId(), hasBypass(player))) {
      throw new IllegalArgumentException(Messages.text("text.kit-editor.you-can-only-overwrite-your-own"));
    }
    if (existingSection == null && (plugin.kits().containsKey(name) || saved.contains(name))) {
      throw new IllegalArgumentException(Messages.text("text.kit-editor.name-already-used-by-a-built"));
    }
    Draft draft = new Draft(name);
    if (existingSection != null) {
      draft.revision = CustomKitStore.revision(existingSection);
    }
    draft.description = Messages.text("text.kit-editor.saved-by") + player.getName();
    importInventory(player, draft);
    save(player, draft.name, serialize(draft), draft.revision);
    draft.revision = CustomKitStore.revision(store.read().getConfigurationSection(draft.name));
    show(player, Screen.EDIT, draft, 0);
    plugin.messages().send(player, "kit.inventory-saved", "%name%", name);
  }

  private void edit(Player player, String name) throws IOException {
    show(player, Screen.EDIT, existing(player, name), 0);
  }

  void library(Player player, int requested) throws IOException {
    allowed(player);
    if (!canEditor(player)) throw new IllegalArgumentException(Messages.text("text.kit-editor.missing-permission-practice-kits-editor"));
    var saved = store.read();
    List<String> names =
        saved.getKeys(false).stream()
            .filter(
                n ->
                    CustomKitStore.editable(
                        saved.getConfigurationSection(n),
                        player.getUniqueId(),
                        hasBypass(player)))
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
              Messages.text("text.kit-editor.left-edit-right-duplicate-shift-right"),
              Messages.text("text.kit-editor.practice-kit-rename") + name + Messages.text("text.kit-editor.new-name")));
    }
    view.inventory.setItem(
        45,
        page == 0
            ? button(Material.ARROW, Messages.text("text.kit-editor.back-to-main-menu"), Messages.text("text.kit-editor.return-to-kit-selection"))
            : button(Material.ARROW, Messages.text("text.kit-editor.previous-page")));
    view.inventory.setItem(
        46, button(Material.EMERALD, Messages.text("text.kit-editor.create-blank-kit"), Messages.text("text.kit-editor.no-fixed-kit-count-limit")));
    view.inventory.setItem(
        47,
        button(
            Material.CHEST, Messages.text("text.kit-editor.browse-kit-templates"), Messages.text("text.kit-editor.choose-any-kit-to-copy-into")));
    view.inventory.setItem(
        49,
        button(
            Material.BOOK,
            names.size() + Messages.text("text.kit-editor.custom-kits-2"),
            Messages.text("text.kit-editor.practice-kit-create-name-template")));
    view.inventory.setItem(52, button(Material.ARROW, Messages.text("text.kit-editor.next-page")));
    view.inventory.setItem(
        50,
        button(
            Material.HOPPER,
            Messages.text("text.kit-editor.create-from-your-inventory"),
            Messages.text("text.kit-editor.copies-hotbar-inventory-armor-and-offhand"),
            Messages.text("text.kit-editor.your-real-items-stay-untouched-click"),
            Messages.text("text.kit-editor.practice-kit-import-name")));
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
              kit.health() + Messages.text("text.kit-editor.hp-click-to-create-an-independent")));
    }
    view.inventory.setItem(45, button(Material.ARROW, Messages.text("text.kit-editor.previous-page")));
    view.inventory.setItem(
        49, button(Material.BOOK, Messages.text("text.kit-editor.choose-a-starting-loadout"), Messages.text("text.kit-editor.original-kits-are-never-modified")));
    view.inventory.setItem(52, button(Material.ARROW, Messages.text("text.kit-editor.next-page")));
    view.inventory.setItem(53, button(Material.BARRIER, Messages.text("text.kit-editor.back-to-your-kits")));
    player.openInventory(view.inventory);
  }

  private void palette(Player player, Draft draft) {
    show(player, Screen.PALETTE, draft, 0);
  }

  private static String pretty(String value) {
    return Messages.displayName(value);
  }

  private static ItemStack displayItem(ItemStack item, String... hints) {
    ItemStack display = item.clone();
    dev.insanmiy.practiceplugin.ItemMetadata.edit(display,
        meta -> {
          List<Component> lore =
              TextUI.lore(meta) == null ? new ArrayList<>() : new ArrayList<>(TextUI.lore(meta));
          lore.add(Component.empty());
          for (String hint : hints) lore.add(Messages.component(hint, NamedTextColor.AQUA));
          TextUI.lore(meta, lore);
        });
    return display;
  }

  private static String slotName(int slot) {
    return slot < 9
        ? Messages.text("text.kit-editor.hotbar") + (slot + 1)
        : slot < 36
            ? Messages.text("text.kit-editor.inventory") + slot
            : new String[] {Messages.text("text.kit-editor.boots"), Messages.text("text.kit-editor.leggings"), Messages.text("text.kit-editor.chestplate"), Messages.text("text.kit-editor.helmet"), Messages.text("text.kit-editor.offhand")}[slot - 36];
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
      throw new IllegalArgumentException(Messages.text("text.kit-editor.this-item-cannot-be-enchanted"));
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
                    slotName(i) + Messages.text("text.kit-editor.empty"),
                    Messages.text("text.kit-editor.click-to-choose-an-item-from"),
                    Messages.text("text.kit-editor.shift-left-paste-selected-item-right"))
                : displayItem(
                    draft.items[i],
                    slotName(i),
                    Messages.text("text.kit-editor.click-item-settings-and-enchantments"),
                    Messages.text("text.kit-editor.shift-left-paste-selected-item-right")));
      inv.setItem(
          41,
          button(
              Material.COMPASS,
              Messages.text("text.kit-editor.selected") + slotName(draft.selected),
              Messages.text("text.kit-editor.slots-0-8-top-row-are"),
              Messages.text("text.kit-editor.click-an-item-settings-enchants"),
              Messages.text("text.kit-editor.click-empty-slot-item-picker"),
              Messages.text("text.kit-editor.shift-left-another-slot-copy-selected"),
              Messages.text("text.kit-editor.click-your-inventory-below-to-copy")));
      inv.setItem(42, button(Material.RED_DYE, Messages.text("text.kit-editor.amount-1"), Messages.text("text.kit-editor.shift-16")));
      inv.setItem(43, button(Material.LIME_DYE, Messages.text("text.kit-editor.amount-1-2"), Messages.text("text.kit-editor.shift-16-native-stack-limits")));
      inv.setItem(
          44,
          button(
              Material.CHEST, Messages.text("text.kit-editor.choose-replace-selected-item"), Messages.text("text.kit-editor.opens-categorized-item-picker")));
      inv.setItem(45, button(Material.ARROW, Messages.text("text.kit-editor.discard-changes-back")));
      inv.setItem(46, button(Material.REDSTONE, Messages.text("text.kit-editor.health-2") + (int) draft.health + Messages.text("text.kit-editor.hp")));
      inv.setItem(47, button(Material.GLOWSTONE_DUST, Messages.text("text.kit-editor.health-2-2") + (int) draft.health + Messages.text("text.kit-editor.hp")));
      inv.setItem(48, button(Material.COOKED_BEEF, Messages.text("text.kit-editor.natural-regeneration") + draft.regeneration));
      inv.setItem(
          49,
          button(
              Material.EMERALD_BLOCK,
              Messages.text("text.kit-editor.save") + draft.name,
              Messages.text("text.kit-editor.validates-and-saves-to-disk"),
              Messages.text("text.kit-editor.both-fighters-get-independent-copies")));
      inv.setItem(
          50,
          button(
              Material.HOPPER,
              Messages.text("text.kit-editor.import-your-inventory"),
              Messages.text("text.kit-editor.replaces-draft-gear-real-items-untouched")));
      inv.setItem(51, button(Material.ENCHANTING_TABLE, Messages.text("text.kit-editor.enchant-selected-item")));
      inv.setItem(
          52,
          button(Material.WRITABLE_BOOK, Messages.text("text.kit-editor.name-this-kit"), Messages.text("text.kit-editor.click-then-type-a-name-in")));
      inv.setItem(53, button(Material.BARRIER, Messages.text("text.kit-editor.delete-saved-kit"), Messages.text("text.kit-editor.confirmation-required")));
    } else if (screen == Screen.ITEM) {
      ItemStack item = draft.items[draft.selected];
      inv.setItem(
          4,
          button(
              Material.COMPASS,
              slotName(draft.selected),
              Messages.text("text.kit-editor.editing-this-slot-only"),
              Messages.text("text.kit-editor.all-changes-stay-in-the-draft")));
      inv.setItem(13, displayItem(item, Messages.text("text.kit-editor.current-item") + slotName(draft.selected)));
      inv.setItem(
          19,
          button(
              Material.CHEST,
              Messages.text("text.kit-editor.choose-a-different-item"),
              Messages.text("text.kit-editor.browse-all-items-equipment-food-potions")));
      inv.setItem(
          21,
          button(
              Material.ENCHANTING_TABLE,
              Messages.text("text.kit-editor.add-edit-enchantments"),
              Messages.text("text.kit-editor.pick-an-enchantment-then-choose-its")));
      inv.setItem(
          23, button(Material.GRINDSTONE, Messages.text("text.kit-editor.remove-all-enchantments"), Messages.text("text.kit-editor.only-changes-this-item")));
      inv.setItem(
          22,
          button(
              Material.NETHER_STAR,
              Messages.text("text.kit-editor.fast-enchant-combat-tool-essentials"),
              Messages.text("text.kit-editor.max-compatible-damage-protection-efficiency-and"),
              Messages.text("text.kit-editor.skips-conflicting-enchantments")));
      inv.setItem(
          24,
          button(
              Material.EXPERIENCE_BOTTLE,
              Messages.text("text.kit-editor.fast-enchant-mending-unbreaking"),
              Messages.text("text.kit-editor.one-click-respects-conflicts-such-as")));
      inv.setItem(25, button(Material.BARRIER, Messages.text("text.kit-editor.clear-this-slot")));
      int[] amounts = {1, 8, 16, 32, 64};
      for (int i = 0; i < amounts.length; i++) {
        int count = EditorRules.amount(amounts[i], item.getMaxStackSize());
        inv.setItem(
            28 + i,
            button(
                Material.PAPER,
                Messages.text("text.kit-editor.set-amount") + count,
                Messages.text("text.kit-editor.native-maximum") + item.getMaxStackSize()));
      }
      inv.setItem(
          34,
          button(
              Material.CHEST_MINECART,
              Messages.text("text.kit-editor.fill-empty-inventory-slots-with-this"),
              Messages.text("text.kit-editor.slots-9-35-only-existing-items")));
      inv.setItem(
          40,
          button(
              Material.ARMOR_STAND,
              Messages.text("text.kit-editor.copy-enchantments-to-matching-armor"),
              Messages.text("text.kit-editor.only-compatible-non-conflicting-enchants-are"),
              Messages.text("text.kit-editor.existing-armor-enchantments-are-preserved")));
      inv.setItem(45, button(Material.ARROW, Messages.text("text.kit-editor.back-to-loadout")));
      inv.setItem(48, button(Material.RED_DYE, Messages.text("text.kit-editor.amount-1"), Messages.text("text.kit-editor.shift-16")));
      inv.setItem(
          49,
          button(Material.BOOK, Messages.text("text.kit-editor.amount") + item.getAmount() + " / " + item.getMaxStackSize()));
      inv.setItem(50, button(Material.LIME_DYE, Messages.text("text.kit-editor.amount-1-2"), Messages.text("text.kit-editor.shift-16-2")));
      inv.setItem(53, button(Material.ARROW, Messages.text("text.kit-editor.back-to-loadout")));
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
                Messages.text("text.kit-editor.click-to-use-in") + slotName(draft.selected),
                Messages.text("text.kit-editor.then-edit-amount-and-enchantments")));
      if (view.palette.isEmpty())
        inv.setItem(
            22,
            button(
                Material.BARRIER,
                Messages.text("text.kit-editor.no-items-for-this-slot-category"),
                Messages.text("text.kit-editor.try-the-all-items-category")));
      inv.setItem(45, button(Material.ARROW, Messages.text("text.kit-editor.previous-page")));
      Material[] icons = {
        Material.DIAMOND_SWORD,
        Material.COOKED_BEEF,
        Material.EXPERIENCE_BOTTLE,
        Material.SPLASH_POTION,
        Material.COBBLESTONE,
        Material.DIAMOND_CHESTPLATE
      };
      String[] categories = {
        Messages.text("text.kit-editor.weapons"), Messages.text("text.kit-editor.food"), Messages.text("text.kit-editor.all-items"), Messages.text("text.kit-editor.potions-tipped-arrows"), Messages.text("text.kit-editor.blocks"), Messages.text("text.kit-editor.equipment")
      };
      for (int i = 0; i < categories.length; i++)
        inv.setItem(
            46 + i,
            button(
                icons[i],
                (i == draft.paletteCategory ? Messages.text("text.kit-editor.selected") : "") + categories[i],
                Messages.text("text.kit-editor.page") + (page + 1) + " / " + (maxPage + 1),
                Messages.text("text.kit-editor.copies-to") + slotName(draft.selected)));
      inv.setItem(52, button(Material.ARROW, Messages.text("text.kit-editor.next-page")));
      inv.setItem(53, button(Material.BARRIER, Messages.text("text.kit-editor.back-to-editor")));
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
                pretty(e.getKey().getKey()) + Messages.text("text.kit-editor.level-2") + item.getEnchantmentLevel(e),
                Messages.text("text.kit-editor.click-exact-level-shift-left-max"),
                conflicts(item, e)
                    ? Messages.text("text.kit-editor.conflict-remove-the-incompatible-enchantment-first")
                    : Messages.text("text.kit-editor.compatible-with-selected-item"),
                Messages.text("text.kit-editor.maximum-vanilla-level") + e.getMaxLevel()));
      }
      if (view.enchants.isEmpty())
        inv.setItem(22, button(Material.BARRIER, Messages.text("text.kit-editor.this-item-has-no-compatible-enchantments")));
      inv.setItem(48, button(Material.GRINDSTONE, Messages.text("text.kit-editor.remove-all-enchantments")));
      inv.setItem(49, displayItem(item, Messages.text("text.kit-editor.enchanting") + slotName(draft.selected)));
      inv.setItem(53, button(Material.ARROW, Messages.text("text.kit-editor.back")));
    } else if (screen == Screen.LEVEL) {
      Enchantment enchant = draft.enchantment;
      ItemStack item = draft.items[draft.selected];
      inv.setItem(
          4,
          button(
              Material.ENCHANTED_BOOK,
              pretty(enchant.getKey().getKey()),
              Messages.text("text.kit-editor.current-level") + item.getEnchantmentLevel(enchant),
              Messages.text("text.kit-editor.choose-a-level-below")));
      inv.setItem(10, button(Material.GRINDSTONE, Messages.text("text.kit-editor.remove-this-enchantment")));
      for (int level = 1; level <= Math.min(30, enchant.getMaxLevel()); level++)
        inv.setItem(
            10 + level,
            button(
                Material.ENCHANTED_BOOK,
                Messages.text("text.kit-editor.level") + level,
                conflicts(item, enchant)
                    ? Messages.text("text.kit-editor.conflicts-with-an-existing-enchantment")
                    : Messages.text("text.kit-editor.click-to-apply")));
      inv.setItem(49, displayItem(item, Messages.text("text.kit-editor.your-item")));
      inv.setItem(53, button(Material.ARROW, Messages.text("text.kit-editor.back-to-enchantments")));
    } else if (screen == Screen.NAME) {
      if (draft.nameInput == null) draft.nameInput = draft.name;
      String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
      for (int i = 0; i < alphabet.length(); i++)
        inv.setItem(i, button(Material.PAPER, "" + alphabet.charAt(i)));
      inv.setItem(36, button(Material.PAPER, "_"));
      inv.setItem(37, button(Material.PAPER, "-"));
      inv.setItem(38, button(Material.ARROW, Messages.text("text.kit-editor.backspace")));
      inv.setItem(39, button(Material.BARRIER, Messages.text("text.kit-editor.clear-name")));
      inv.setItem(
          40,
          button(
              Material.NAME_TAG,
              Messages.text("text.kit-editor.name") + draft.nameInput,
              draft.nameInput.length() + Messages.text("text.kit-editor.32-characters")));
      inv.setItem(
          49,
          button(
              Material.EMERALD_BLOCK,
              Messages.text("text.kit-editor.apply-name"),
              Messages.text("text.kit-editor.existing-saved-kits-are-renamed-immediately"),
              Messages.text("text.kit-editor.other-draft-changes-still-require-save")));
      inv.setItem(53, button(Material.ARROW, Messages.text("text.kit-editor.cancel-naming")));
    } else if (screen == Screen.DELETE) {
      inv.setItem(
          22,
          button(
              Material.TNT,
              Messages.text("text.kit-editor.confirm-deletion") + draft.name,
              Messages.text("text.kit-editor.removes-the-saved-custom-kit"),
              Messages.text("text.kit-editor.previous-file-retained-as-customkits-yml")));
      inv.setItem(53, button(Material.ARROW, Messages.text("text.kit-editor.cancel")));
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
      throw new IllegalArgumentException(Messages.text("text.kit-editor.that-item-does-not-fit") + slotName(slot) + ".");
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
      throw new IllegalArgumentException(Messages.text("text.kit-editor.remove-the-conflicting-enchantment-first"));
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
          TextUI.name(meta, Messages.component(title, NamedTextColor.AQUA));
          TextUI.lore(meta, Arrays.stream(lore).map(s -> Messages.component(s, NamedTextColor.GRAY)).toList());
        });
    return item;
  }

  private static YamlConfiguration serialize(Draft draft) {
    var yaml = new YamlConfiguration();
    yaml.set("description", draft.description != null ? draft.description : Messages.text("text.kit-editor.custom-kit") + draft.name);
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
                player.sendMessage(Messages.color(e.getMessage().contains("\u00A7") || e.getMessage().contains("&") ? e.getMessage() : "&c" + e.getMessage()));
              } catch (IOException e) {
                plugin.messages().send(player, "kit.action-error");
                plugin.getLogger().warning(e.toString());
              } catch (Throwable t) {
                plugin.messages().send(player, "general.internal-error");
                plugin.getLogger().severe(Messages.text("text.kit-editor.unexpected-error-in-kiteditor") + t.getMessage());
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
      if (slot < 45 && index < view.names.size()) {
        if (!canCreate(player)) throw new IllegalArgumentException(Messages.text("text.kit-editor.missing-permission-practice-kits-create"));
        create(player, unusedName(), view.names.get(index));
      }
      else if (slot == 45 || slot == 52) templates(player, view.page + (slot == 45 ? -1 : 1));
      else if (slot == 53) library(player, 0);
      return;
    }
    if (view.screen == Screen.LIBRARY) {
      int index = view.page * 45 + slot;
      if (slot < 45 && index < view.names.size()) {
        String kitName = view.names.get(index);
        if (shift && right) {
          if (!canDelete(player)) throw new IllegalArgumentException(Messages.text("text.kit-editor.missing-permission-practice-kits-delete"));
          show(player, Screen.DELETE, existing(player, kitName), 0);
        } else if (right) {
          if (!canCreate(player)) throw new IllegalArgumentException(Messages.text("text.kit-editor.missing-permission-practice-kits-create"));
          create(player, unusedName(), kitName);
        } else {
          if (!canEditor(player)) throw new IllegalArgumentException(Messages.text("text.kit-editor.missing-permission-practice-kits-editor"));
          edit(player, kitName);
        }
        return;
      } else if (slot == 45 && view.page == 0) {
        player.closeInventory();
        Bukkit.dispatchCommand(player, "practice menu");
      } else if (slot == 45 || slot == 52) library(player, view.page + (slot == 45 ? -1 : 1));
      else if (slot == 46) {
        if (!canCreate(player)) throw new IllegalArgumentException(Messages.text("text.kit-editor.missing-permission-practice-kits-create"));
        create(player, unusedName(), null);
      } else if (slot == 47) {
        if (!canCreate(player)) throw new IllegalArgumentException(Messages.text("text.kit-editor.missing-permission-practice-kits-create"));
        templates(player, 0);
      } else if (slot == 50) {
        if (!canImport(player)) throw new IllegalArgumentException(Messages.text("text.kit-editor.missing-permission-practice-kits-import"));
        importKit(player, unusedName());
      }
      return;
    }
    if (view.screen == Screen.DELETE) {
      if (slot == 22) {
        if (!canDelete(player)) throw new IllegalArgumentException(Messages.text("text.kit-editor.missing-permission-practice-kits-delete"));
        store.delete(
            draft.name,
            draft.revision,
            player.getUniqueId(),
            hasBypass(player));
        plugin.refreshKits();
        plugin.messages().send(player, "kit.deleted", "%name%", draft.name);
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
            throw new IllegalArgumentException(Messages.text("text.kit-editor.name-already-used"));
          if (draft.revision != null) {
            if (!Objects.equals(
                draft.revision, CustomKitStore.revision(saved.getConfigurationSection(draft.name))))
              throw new IllegalArgumentException(
                  Messages.text("text.kit-editor.this-kit-changed-reopen-the-editor"));
            store.rename(
                draft.name,
                name,
                player.getUniqueId(),
                hasBypass(player),
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
        if (!canEnchant(item)) throw new IllegalArgumentException(Messages.text("text.kit-editor.this-item-cannot-be-enchanted"));
        show(player, Screen.ENCHANT, draft, 0);
        return;
      }
      if (slot == 23) removeEnchants(item);
      else if (slot == 22 || slot == 24) {
        if (!canEnchant(item)) throw new IllegalArgumentException(Messages.text("text.kit-editor.this-item-cannot-be-enchanted"));
        int applied = QuickEnchant.apply(item, slot == 24);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, .45f, 1.2f);
        plugin.messages().send(player, "kit.quick-enchant-applied", "%count%", String.valueOf(applied));
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
        plugin.messages().send(player, "kit.reserve-filled", "%count%", String.valueOf(filled));
      } else if (slot == 40) {
        if (!armor(item.getType()))
          throw new IllegalArgumentException(Messages.text("text.kit-editor.select-an-armor-piece-first"));
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
        plugin.messages().send(player, "kit.armor-enchants-copied");
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
          if (draft.revision == null && !canCreate(player))
            throw new IllegalArgumentException(Messages.text("text.kit-editor.missing-permission-practice-kits-create"));
          if (draft.revision != null && !canEditor(player))
            throw new IllegalArgumentException(Messages.text("text.kit-editor.missing-permission-practice-kits-editor"));
          save(player, draft.name, serialize(draft), draft.revision);
          draft.revision =
              CustomKitStore.revision(store.read().getConfigurationSection(draft.name));
          plugin.messages().send(player, "kit.saved", "%name%", draft.name);
        }
        case 50 -> {
          if (!canImport(player)) throw new IllegalArgumentException(Messages.text("text.kit-editor.missing-permission-practice-kits-import"));
          importInventory(player, draft);
          int count = 0;
          for (ItemStack it : draft.items) {
            if (!empty(it)) count++;
          }
          plugin.messages().send(player, "kit.imported-inventory", "%count%", String.valueOf(count));
        }
        case 51 -> {
          if (!canEnchant(draft.items[draft.selected]))
            throw new IllegalArgumentException(Messages.text("text.kit-editor.this-item-cannot-be-enchanted"));
          show(player, Screen.ENCHANT, draft, 0);
          return;
        }
        case 52 -> {
          pendingNames.put(player.getUniqueId(), draft);
          player.closeInventory();
          plugin.messages().send(player, "kit.naming-prompt");
          return;
        }
        case 53 -> {
          if (draft.revision == null) {
            plugin.messages().send(player, "kit.discarded");
            library(player, 0);
            return;
          }
          if (!canDelete(player)) throw new IllegalArgumentException(Messages.text("text.kit-editor.missing-permission-practice-kits-delete"));
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
                plugin.messages().send(player, "kit.naming-cancelled");
                show(player, Screen.EDIT, draft, 0);
                return;
              }
              try {
                allowed(player);
                String name = CustomKitStore.name(input);
                if (!name.equals(draft.name)) {
                  var saved = store.read();
                  if (saved.contains(name) || plugin.kits().containsKey(name))
                    throw new IllegalArgumentException(Messages.text("text.kit-editor.name-already-used"));
                  if (draft.revision != null) {
                    if (!Objects.equals(
                        draft.revision,
                        CustomKitStore.revision(saved.getConfigurationSection(draft.name))))
                      throw new IllegalArgumentException(
                          Messages.text("text.kit-editor.this-kit-changed-reopen-the-editor"));
                    store.rename(
                        draft.name,
                        name,
                        player.getUniqueId(),
                        hasBypass(player),
                        plugin.kits().keySet());
                    plugin.refreshKits();
                    draft.revision =
                        CustomKitStore.revision(store.read().getConfigurationSection(name));
                  }
                  draft.name = name;
                }
                plugin.messages().send(player, "kit.named", "%name%", draft.name);
              } catch (IllegalArgumentException e) {
                player.sendMessage(Messages.color(e.getMessage().contains("\u00A7") || e.getMessage().contains("&") ? e.getMessage() : "&c" + e.getMessage()));
              } catch (IOException e) {
                plugin.messages().send(player, "kit.action-error");
                plugin.getLogger().warning(e.toString());
              } catch (Throwable t) {
                plugin.messages().send(player, "kit.naming-error");
                plugin.getLogger().severe(Messages.text("text.kit-editor.unexpected-error-naming-kit-in-kiteditor") + t.getMessage());
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
