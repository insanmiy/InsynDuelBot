package dev.insanmiy.practiceplugin;

import dev.insanmiy.practiceplugin.config.Difficulty;
import dev.insanmiy.practiceplugin.config.Kit;
import dev.insanmiy.practiceplugin.model.*;
import java.io.*;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;

final class PracticeMenu implements Listener {
  private enum Screen {
    KITS,
    PREVIEW,
    DIFFICULTIES,
    MODES,
    STATS
  }

  private static final List<String> CATEGORIES =
      List.of("all", "melee", "potion", "web", "ranged", "smp", "favorites");
  private static final Material[] CATEGORY_ICONS = {
    Material.COMPASS,
    Material.DIAMOND_SWORD,
    Material.SPLASH_POTION,
    Material.COBWEB,
    Material.BOW,
    Material.TOTEM_OF_UNDYING,
    Material.NETHER_STAR
  };
  private final PracticePlugin plugin;
  private final File favoritesFile;
  private final YamlConfiguration favorites;
  private final Map<UUID, Selection> drafts = new HashMap<>();

  PracticeMenu(PracticePlugin plugin) {
    this.plugin = plugin;
    favoritesFile = new File(plugin.getDataFolder(), "menu-favorites.yml");
    favorites = YamlConfiguration.loadConfiguration(favoritesFile);
  }

  private static final class View implements AnimatedMenu {
    final UUID owner;
    final Selection choice;
    final Screen screen;
    final String category;
    final int page;
    final List<String> entries;
    final Inventory inventory;

    View(
        Player player,
        Selection choice,
        Screen screen,
        String category,
        int page,
        List<String> entries) {
      owner = player.getUniqueId();
      this.choice = choice;
      this.screen = screen;
      this.category = category;
      this.page = page;
      this.entries = entries;
      String title =
          switch (screen) {
            case KITS -> "Practice / Select Kit";
            case PREVIEW -> "Preview / " + pretty(choice.kit());
            case DIFFICULTIES -> "Practice / Select Difficulty";
            case MODES -> "Practice / Select Mode";
            case STATS -> "Practice / Your Statistics";
          };
      inventory = TextUI.inventory(this, 54, text(title, NamedTextColor.DARK_AQUA));
    }

    @Override
    public Inventory getInventory() {
      return inventory;
    }

    public int[] decorativeSlots() {
      return new int[0];
    }

    public int pulseSlot() {
      return screen == Screen.KITS || screen == Screen.PREVIEW ? 49 : -1;
    }
  }

  void open(Player player, Selection choice, int page) {
    show(player, drafts.getOrDefault(player.getUniqueId(), choice), Screen.KITS, "all", page);
  }

  private Set<String> favorites(Player player) {
    return new LinkedHashSet<>(favorites.getStringList(player.getUniqueId().toString()));
  }

  private void show(
      Player player, Selection selection, Screen screen, String category, int requestedPage) {
    if (plugin.kits().isEmpty() || plugin.difficulties().isEmpty()){
      throw new IllegalArgumentException("No Valid Kits Or Difficulties. Check Configuration.");
    }
    Selection choice =
        new Selection(
            plugin.kits().containsKey(selection.kit())
                ? selection.kit()
                : plugin.kits().keySet().iterator().next(),
            plugin.difficulties().containsKey(selection.difficulty())
                ? selection.difficulty()
                : plugin.difficulties().keySet().iterator().next(),
            selection.mode(),
            selection.bestOf());
    drafts.put(player.getUniqueId(), choice);
    Set<String> starred = favorites(player);
    List<String> entries =
        screen == Screen.DIFFICULTIES
            ? List.copyOf(plugin.difficulties().keySet())
            : screen == Screen.MODES
                ? Arrays.stream(PracticeMode.values()).map(PracticeMode::id).toList()
                : screen == Screen.STATS
                    ? Arrays.stream(PracticeMode.values()).map(PracticeMode::id).toList()
                    : plugin.kits().values().stream()
                        .filter(
                            k ->
                                category.equals("all")
                                    || category.equals("favorites") && starred.contains(k.name())
                                    || k.category().equals(category))
                        .map(Kit::name)
                        .toList();
    int page = MenuPaging.clamp(requestedPage, entries.size());
    View view = new View(player, choice, screen, category, page, entries);
    Inventory inventory = view.inventory;

    ItemStack divider = icon(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, false);
    for (int slot : new int[] {9, 17, 18, 26, 27, 35}) {
      inventory.setItem(slot, divider);
    }
    for (int slot = 36; slot <= 44; slot++) {
      inventory.setItem(slot, divider);
    }

    Kit selected = plugin.kits().get(choice.kit());

    if (screen == Screen.KITS){
      // Custom Kit Editor on the left. Categories in the middle. Close on the right
      inventory.setItem(
          0,
          icon(
              Material.KNOWLEDGE_BOOK,
              "Custom Kit Editor",
              NamedTextColor.GOLD,
              false,
              "Click To Create And Edit Custom Kits"));

      for (int i = 0; i < CATEGORIES.size(); i++) {
        String cat = CATEGORIES.get(i);
        boolean active = category.equals(cat);
        inventory.setItem(
            i + 1,
            icon(
                CATEGORY_ICONS[i],
                pretty(cat),
                active ? NamedTextColor.GREEN : NamedTextColor.AQUA,
                active,
                active ? "Currently Selected" : "Click To View Category"));
      }

      inventory.setItem(8, icon(Material.BARRIER, "Close Menu", NamedTextColor.RED, false));

      if (entries.isEmpty()){
        inventory.setItem(
            22,
            icon(
                Material.BARRIER,
                "No Kits In This Category",
                NamedTextColor.YELLOW,
                false,
                "Shift Click A Kit To Favorite It",
                "Click All To Browse Every Kit"));
      }
    }
    else {
      inventory.setItem(
          4,
          icon(
              Material.NETHER_STAR,
              pretty(screen.name()),
              NamedTextColor.GOLD,
              true,
              "Your Selection Is Kept While Navigating"));
      inventory.setItem(8, icon(Material.BARRIER, "Close Menu", NamedTextColor.RED, false));
    }

    if (screen == Screen.PREVIEW){
      for (int slot = 0; slot < 41; slot++) {
        inventory.setItem(slot, null);
      }
      selected
          .previewItems()
          .forEach(
              (slot, item) -> {
                if (!item.getType().isAir()){
                  dev.insanmiy.practiceplugin.ItemMetadata.edit(item,
                      meta -> {
                        List<Component> lore =
                            TextUI.lore(meta) == null ? new ArrayList<>() : new ArrayList<>(TextUI.lore(meta));
                        lore.add(
                            text(
                                "Preview Only - Identical For Both Fighters",
                                NamedTextColor.DARK_AQUA));
                        TextUI.lore(meta, lore);
                      });
                  inventory.setItem(slot, item);
                }
              });
      inventory.setItem(
          41,
          icon(
              Material.ARMOR_STAND,
              "Exact Inventory Layout",
              NamedTextColor.AQUA,
              false,
              "Slots 0-35: Kit Inventory",
              "Bottom: Armor And Offhand"));
      inventory.setItem(
          42,
          icon(
              Material.COOKED_BEEF,
              "Food Regeneration",
              NamedTextColor.GOLD,
              false,
              selected.regeneration() ? "Enabled - Vanilla Saturation Healing" : "Disabled For This Kit"));
      inventory.setItem(
          43, icon(Material.RED_DYE, selected.health() + " HP Each", NamedTextColor.RED, false));
    }
    else {
      int from = page * MenuPaging.PAGE_SIZE;
      for (int index = 0; index < MenuPaging.PAGE_SIZE && from + index < entries.size(); index++) {
        String name = entries.get(from + index);
        ItemStack card;
        if (screen == Screen.KITS){
          Kit kit = plugin.kits().get(name);
          card =
              icon(
                  kit.category().equals("potion")
                      ? Material.SPLASH_POTION
                      : kit.category().equals("web")
                          ? Material.COBWEB
                          : kit.category().equals("ranged") ? Material.BOW : kit.icon(),
                  (starred.contains(name) ? "* " : "") + pretty(name),
                  choice.kit().equals(name) ? NamedTextColor.GREEN : NamedTextColor.AQUA,
                  choice.kit().equals(name),
                  kit.description(),
                  "",
                  kit.health() + " HP | Regen: " + (kit.regeneration() ? "Enabled" : "Disabled"),
                  kit.count(Material.GOLDEN_APPLE) + " Apples | " + kit.count(Material.SPLASH_POTION) + " Potions",
                  kit.count(Material.COBWEB) + " Webs | " + kit.count(Material.EXPERIENCE_BOTTLE) + " XP Bottles",
                  "",
                  choice.kit().equals(name) ? "» Selected «" : "Left Click: Select",
                  "Right Click: Preview | Shift Click: Favorite");
        }
        else if (screen == Screen.DIFFICULTIES){
          var d = plugin.difficulties().get(name);
          Material mat = d != null ? d.effectiveIcon() : Material.IRON_SWORD;
          String dispName = d != null ? d.prettyDisplayName() : pretty(name);
          List<String> lore = new ArrayList<>();
          if (d != null && d.description() != null && !d.description().isBlank()){
            lore.add(d.description());
            lore.add("");
          }
          if (d != null){
            lore.add("Decision Interval: " + d.decisionTicks() + " Ticks");
            lore.add("Perception Delay: " + d.perceptionTicks() + " Ticks");
            lore.add("Aim Error: " + String.format(Locale.ROOT, "%.1f", d.aimError()) + " Degrees");
            lore.add("Shield Defense: " + Math.round(d.shieldChance() * 100) + "%");
            lore.add("Sprint Reset: " + Math.round(d.sprintResetChance() * 100) + "%");
            lore.add("Strafe Dodge: " + Math.round(d.strafeStrength() * 100) + "%");
            lore.add("Heal Urgency: " + Math.round(d.healThreshold() * 100) + "% HP");
          }
          lore.add("");
          lore.add(choice.difficulty().equals(name) ? "» Selected «" : "Click To Select");
          card =
              icon(
                  mat,
                  dispName,
                  choice.difficulty().equals(name) ? NamedTextColor.GREEN : NamedTextColor.GOLD,
                  choice.difficulty().equals(name),
                  lore.toArray(String[]::new));
        }
        else if (screen == Screen.MODES){
          PracticeMode mode = PracticeMode.parse(name);
          Material modeIcon =
              switch (mode) {
                case DUMMY -> Material.ARMOR_STAND;
                case MATCH -> Material.GOLDEN_SWORD;
                case DUEL -> Material.IRON_SWORD;
                case ENDLESS -> Material.TARGET;
              };
          card =
              icon(
                  modeIcon,
                  pretty(name),
                  NamedTextColor.AQUA,
                  choice.mode() == mode,
                  mode.description(),
                  mode == PracticeMode.MATCH
                      ? "Best Of " + choice.bestOf() + "; First To " + (choice.bestOf() / 2 + 1) + " Wins"
                      : mode == PracticeMode.DUEL
                          ? "Single 1v1 Duel To Knockout"
                          : mode == PracticeMode.DUMMY
                              ? "Passive Target For Combos"
                              : "Continuous Rounds With Score Tracking",
                  "Click To Select");
        }
        else {
          card =
              icon(
                  Material.BOOK,
                  pretty(name),
                  NamedTextColor.GOLD,
                  false,
                  plugin
                      .progress()
                      .summary(player.getUniqueId(), PracticeMode.parse(name))
                      .split(" \\| "));
        }
        inventory.setItem(MenuPaging.slot(index), card);
      }
    }

    if (screen != Screen.PREVIEW){
      if (page > 0){
        inventory.setItem(36, icon(Material.ARROW, "Previous Page", NamedTextColor.AQUA, false));
      }
      int totalPages = Math.max(1, MenuPaging.pages(entries.size()));
      inventory.setItem(
          40,
          icon(
              Material.PAPER,
              "Page " + (page + 1) + " Of " + totalPages,
              NamedTextColor.YELLOW,
              false,
              entries.size() + " Total Entries"));
      if (page + 1 < totalPages){
        inventory.setItem(44, icon(Material.ARROW, "Next Page", NamedTextColor.AQUA, false));
      }
    }

    Difficulty selectedDiff = plugin.difficulties().get(choice.difficulty());
    String diffDisplayName = selectedDiff != null ? selectedDiff.prettyDisplayName() : pretty(choice.difficulty());
    Material diffIcon = selectedDiff != null ? selectedDiff.effectiveIcon() : Material.COMPARATOR;

    PracticeMode currentMode = choice.mode();
    Material modeIcon =
        switch (currentMode) {
          case DUMMY -> Material.ARMOR_STAND;
          case MATCH -> Material.GOLDEN_SWORD;
          case DUEL -> Material.IRON_SWORD;
          case ENDLESS -> Material.TARGET;
        };

    inventory.setItem(
        45,
        icon(
            Material.COMPASS,
            screen == Screen.KITS ? "Browse All Kits" : "Back To Kits",
            NamedTextColor.AQUA,
            false,
            "Reset Filter To View All Kits"));

    inventory.setItem(
        46,
        icon(
            diffIcon,
            "Difficulty: " + diffDisplayName,
            NamedTextColor.GOLD,
            screen == Screen.DIFFICULTIES,
            "Current: " + diffDisplayName,
            "",
            "Click To Change Difficulty"));

    inventory.setItem(
        47,
        icon(
            modeIcon,
            "Mode: " + pretty(currentMode.id()),
            NamedTextColor.AQUA,
            screen == Screen.MODES,
            currentMode.description(),
            "",
            "Click To Change Mode"));

    inventory.setItem(
        48,
        icon(
            Material.COMPARATOR,
            "Series: Best Of " + choice.bestOf(),
            NamedTextColor.YELLOW,
            false,
            "For Match Mode Only",
            "",
            "Left Click: +2 Rounds",
            "Right Click: -2 Rounds"));

    inventory.setItem(
        49,
        icon(
            Material.LIME_CONCRETE,
            "Start Practice",
            NamedTextColor.GREEN,
            true,
            "Kit: " + pretty(choice.kit()),
            "Difficulty: " + diffDisplayName,
            "Mode: " + pretty(currentMode.id()),
            "",
            "Click To Launch Practice"));

    inventory.setItem(
        50,
        icon(
            Material.CHEST,
            "Inspect Loadout",
            NamedTextColor.AQUA,
            screen == Screen.PREVIEW,
            "View Full Inventory And Enchants",
            "",
            "Click To Inspect"));

    inventory.setItem(
        51,
        icon(
            Material.REPEATER,
            "Bot Settings",
            NamedTextColor.AQUA,
            false,
            "Customize Bot Behavior And Rules",
            "",
            "Click To Open Settings"));

    inventory.setItem(
        52,
        icon(
            Material.BOOK,
            "Your Progress",
            NamedTextColor.GOLD,
            screen == Screen.STATS,
            "View Match And Duel Record",
            "",
            "Click To View Stats"));

    inventory.setItem(53, icon(Material.BARRIER, "Close Menu", NamedTextColor.RED, false));

    player.openInventory(inventory);
  }

  private static String pretty(String value) {
    return Difficulty.formatName(value);
  }

  private static Component text(String value, NamedTextColor color) {
    return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
  }

  private static ItemStack icon(
      Material type, String name, NamedTextColor color, boolean selected, String... lore) {
    ItemStack item = new ItemStack(type);
    dev.insanmiy.practiceplugin.ItemMetadata.edit(item,
        meta -> {
          TextUI.name(meta, text(name, color));
          List<Component> lines = new ArrayList<>();
          for (String raw : lore) {
            if (raw == null || raw.isBlank()){
              lines.add(Component.empty());
              continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : raw.split(" ")) {
              if (line.length() > 0 && line.length() + word.length() + 1 > 38){
                lines.add(text(line.toString(), NamedTextColor.GRAY));
                line.setLength(0);
              }
              if (line.length() > 0){
                line.append(' ');
              }
              line.append(word);
            }
            lines.add(text(line.toString(), NamedTextColor.GRAY));
          }
          TextUI.lore(meta, lines);
          meta.setEnchantmentGlintOverride(selected);
          meta.addItemFlags(
              ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        });
    return item;
  }

  @EventHandler
  public void click(InventoryClickEvent event) {
    if (!(event.getView().getTopInventory().getHolder() instanceof View view)){
      return;
    }
    event.setCancelled(true);
    if (!(event.getWhoClicked() instanceof Player player)
        || !view.owner.equals(player.getUniqueId())){
      return;
    }
    int slot = event.getRawSlot();
    if (slot < 0 || slot >= 54){
      return;
    }
    boolean right = event.isRightClick(), shift = event.isShiftClick();
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (!player.isOnline()
                  || player.getOpenInventory().getTopInventory().getHolder() != view){
                return;
              }
              if (!player.hasPermission("practice.use")){
                player.closeInventory();
                return;
              }
              Selection choice = view.choice;
              try {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, .35f, 1.1f);

                // Actions on KITS screen
                if (view.screen == Screen.KITS && slot == 0){
                  Bukkit.dispatchCommand(player, "practice kit editor");
                  return;
                }
                if (view.screen == Screen.KITS && slot >= 1 && slot <= 7){
                  show(player, choice, Screen.KITS, CATEGORIES.get(slot - 1), 0);
                  return;
                }
                if (slot == 8){
                  player.closeInventory();
                  return;
                }

                // Middle area selections (kits, difficulties, modes)
                int index = MenuPaging.index(slot);
                if (view.screen != Screen.PREVIEW
                    && index >= 0
                    && view.page * MenuPaging.PAGE_SIZE + index < view.entries.size()){
                  String name = view.entries.get(view.page * MenuPaging.PAGE_SIZE + index);
                  if (view.screen == Screen.KITS){
                    if (shift){
                      Set<String> starred = favorites(player);
                      if (!starred.remove(name)){
                        starred.add(name);
                      }
                      String key = player.getUniqueId().toString();
                      Object previous = favorites.get(key);
                      favorites.set(key, new ArrayList<>(starred));
                      try {
                        YamlFiles.write(favoritesFile.toPath(), favorites);
                      } catch (IOException failure) {
                        favorites.set(key, previous);
                        throw failure;
                      }
                      show(player, choice, Screen.KITS, view.category, view.page);
                    }
                    else if (right){
                      show(
                          player,
                          new Selection(name, choice.difficulty(), choice.mode(), choice.bestOf()),
                          Screen.PREVIEW,
                          view.category,
                          view.page);
                    }
                    else {
                      show(
                          player,
                          new Selection(name, choice.difficulty(), choice.mode(), choice.bestOf()),
                          Screen.KITS,
                          view.category,
                          view.page);
                    }
                  }
                  else if (view.screen == Screen.DIFFICULTIES){
                    show(
                        player,
                        new Selection(choice.kit(), name, choice.mode(), choice.bestOf()),
                        Screen.KITS,
                        view.category,
                        0);
                  }
                  else if (view.screen == Screen.MODES){
                    PracticeMode chosenMode = PracticeMode.parse(name);
                    int bestOf = chosenMode == PracticeMode.MATCH
                        ? Math.max(3, choice.bestOf() % 2 == 0 ? choice.bestOf() + 1 : choice.bestOf())
                        : choice.bestOf();
                    show(
                        player,
                        new Selection(
                            choice.kit(),
                            choice.difficulty(),
                            chosenMode,
                            bestOf),
                        Screen.KITS,
                        view.category,
                        0);
                  }
                  return;
                }

                // navigation and action bar
                switch (slot) {
                  case 36 -> {
                    if (view.screen != Screen.PREVIEW && view.page > 0){
                      show(player, choice, view.screen, view.category, view.page - 1);
                    }
                  }
                  case 44 -> {
                    if (view.screen != Screen.PREVIEW && view.page + 1 < MenuPaging.pages(view.entries.size())){
                      show(player, choice, view.screen, view.category, view.page + 1);
                    }
                  }
                  case 45 -> {
                    show(player, choice, Screen.KITS, "all", 0);
                  }
                  case 46 -> {
                    if (view.screen == Screen.DIFFICULTIES){
                      show(player, choice, Screen.KITS, view.category, 0);
                    }
                    else {
                      show(player, choice, Screen.DIFFICULTIES, view.category, 0);
                    }
                  }
                  case 47 -> {
                    if (view.screen == Screen.MODES){
                      show(player, choice, Screen.KITS, view.category, 0);
                    }
                    else {
                      show(player, choice, Screen.MODES, view.category, 0);
                    }
                  }
                  case 48 -> {
                    int nextBestOf = right
                        ? (choice.bestOf() <= 3 ? 11 : choice.bestOf() - 2)
                        : (choice.bestOf() >= 11 ? 3 : choice.bestOf() + 2);
                    show(
                        player,
                        new Selection(choice.kit(), choice.difficulty(), choice.mode(), nextBestOf),
                        view.screen,
                        view.category,
                        view.page);
                  }
                  case 49 -> {
                    if (view.screen == Screen.DIFFICULTIES || view.screen == Screen.MODES || view.screen == Screen.STATS){
                      show(player, choice, Screen.KITS, view.category, 0);
                    }
                    else {
                      player.closeInventory();
                      Bukkit.dispatchCommand(
                          player,
                          "practice start "
                              + choice.kit()
                              + " "
                              + choice.difficulty()
                              + " "
                              + choice.mode().id()
                              + " "
                              + choice.bestOf());
                    }
                  }
                  case 50 -> {
                    if (view.screen == Screen.PREVIEW){
                      show(player, choice, Screen.KITS, view.category, view.page);
                    }
                    else {
                      show(player, choice, Screen.PREVIEW, view.category, view.page);
                    }
                  }
                  case 51 -> {
                    plugin.openSettings(player);
                  }
                  case 52 -> {
                    if (view.screen == Screen.STATS){
                      show(player, choice, Screen.KITS, view.category, 0);
                    }
                    else {
                      show(player, choice, Screen.STATS, view.category, 0);
                    }
                  }
                  case 53 -> {
                    player.closeInventory();
                  }
                  default -> {}
                }
              } catch (IllegalArgumentException e) {
                player.sendMessage(TextUI.legacy(text(e.getMessage(), NamedTextColor.RED)));
              } catch (IOException e) {
                player.sendMessage(TextUI.legacy(
                    text("Could Not Save Favorites. Check The Server Log.", NamedTextColor.RED)));
                plugin.getLogger().warning(e.toString());
              }
            });
  }

  @EventHandler
  public void drag(InventoryDragEvent event) {
    if (event.getView().getTopInventory().getHolder() instanceof View){
      event.setCancelled(true);
    }
  }

  Selection draft(UUID id) {
    return drafts.get(id);
  }

  void closeAll() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (player.getOpenInventory().getTopInventory().getHolder() instanceof View){
        player.closeInventory();
      }
    }
    drafts.clear();
  }
}
