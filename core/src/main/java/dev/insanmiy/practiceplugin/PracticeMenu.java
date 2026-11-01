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
import org.jetbrains.annotations.NotNull;

final class PracticeMenu implements Listener {
  private enum Screen {
    HOME,
    KITS,
    PREVIEW,
    DIFFICULTIES,
    MODES,
    STATS
  }

  private final PracticePlugin plugin;
  private final File favoritesFile;
  private final YamlConfiguration favorites;
  private final Map<UUID, Selection> drafts = new HashMap<>();

  PracticeMenu(PracticePlugin plugin) {
    this.plugin = plugin;
    favoritesFile = new File(plugin.getDataFolder(), "menu-favorites.yml");
    favorites = YamlFiles.load(favoritesFile);
  }

  private static final class View implements AnimatedMenu {
    final UUID owner;
    Selection choice;
    Screen screen;
    int page;
    List<String> entries;
    final Inventory inventory;

    View(
        Player player,
        Selection choice,
        Screen screen,
        int page,
        List<String> entries) {
      this.owner = player.getUniqueId();
      this.choice = choice;
      this.screen = screen;
      this.page = page;
      this.entries = entries;
      String title = titleFor(screen, choice);
      this.inventory = TextUI.inventory(this, 54, text(title, NamedTextColor.DARK_AQUA));
    }

    static String titleFor(Screen screen, Selection choice) {
      return switch (screen) {
        case HOME -> "Practice / Setup";
        case KITS -> "Practice / Choose Your Kit";
        case PREVIEW -> "Preview / " + pretty(choice.kit());
        case DIFFICULTIES -> "Practice / Select Difficulty";
        case MODES -> "Practice / Select Mode";
        case STATS -> "Practice / Your Statistics";
      };
    }

    @Override
    public @NotNull Inventory getInventory() {
      return inventory;
    }

    public int[] decorativeSlots() {
      if (screen == Screen.PREVIEW) {
        return new int[] {46, 47, 48, 50, 51, 52};
      }
      return new int[] {
        0, 1, 2, 3, 5, 6, 7, 8,
        9, 17, 18, 26, 27, 35,
        46, 47, 48, 50, 51, 52
      };
    }

    public int pulseSlot() {
      return -1;
    }
  }

  void open(Player player, Selection choice) {
    show(player, drafts.getOrDefault(player.getUniqueId(), choice), Screen.HOME, 0);
  }

  private Set<String> favorites(Player player) {
    return new LinkedHashSet<>(favorites.getStringList(player.getUniqueId().toString()));
  }

  private void show(
      Player player, Selection selection, Screen screen, int requestedPage) {
    if (plugin.kits().isEmpty() || plugin.difficulties().isEmpty()) {
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
                    : List.copyOf(plugin.kits().keySet());
    int page = MenuPaging.clamp(requestedPage, entries.size());

    boolean canReuse = false;
    View view = null;
    if (player.getOpenInventory().getTopInventory().getHolder() instanceof View currentView) {
      if (currentView.owner.equals(player.getUniqueId())
          && currentView.screen == screen
          && (screen != Screen.PREVIEW || currentView.choice.kit().equals(choice.kit()))) {
        canReuse = true;
        currentView.choice = choice;
        currentView.page = page;
        currentView.entries = entries;
        view = currentView;
      }
    }
    if (!canReuse) {
      view = new View(player, choice, screen, page, entries);
    }
    Inventory inventory = view.inventory;
    inventory.clear();

    ItemStack divider = icon(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, false);
    ItemStack background = icon(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, false);
    for (int slot = 0; slot < inventory.getSize(); slot++) {
      boolean border = slot < 9 || slot >= 45 || slot % 9 == 0 || slot % 9 == 8;
      inventory.setItem(slot, border || slot >= 36 ? divider : background);
    }
    ItemStack accent = icon(Material.CYAN_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_AQUA, false);
    inventory.setItem(0, accent);
    inventory.setItem(8, accent);

    Kit selected = plugin.kits().get(choice.kit());

    boolean canKitEditor = player.hasPermission("practice.kits.editor")
        || player.hasPermission("practice.admin")
        || player.hasPermission("practice.*");
    if (screen == Screen.HOME) {
      inventory.setItem(
          29,
          icon(
              canKitEditor ? Material.KNOWLEDGE_BOOK : Material.BARRIER,
              "Custom Kit Editor",
              canKitEditor ? NamedTextColor.GOLD : NamedTextColor.RED,
              false,
              canKitEditor ? "Click To Create And Edit Custom Kits" : "Locked: Missing practice.kits.editor"));
    }
    inventory.setItem(
        4,
        icon(
            Material.NETHER_STAR,
            screen == Screen.HOME ? "Ready To Practice?" : pretty(screen.name()),
            NamedTextColor.GOLD,
            false,
            screen == Screen.HOME ? "Use The Current Setup Or Change A Choice Below"
                : screen == Screen.STATS ? "Your Results For Each Practice Mode"
                : "Click A Choice To Return To Practice Setup",
            "Your Selection Is Kept While Navigating"));

    if (screen == Screen.HOME) {
      Difficulty difficulty = plugin.difficulties().get(choice.difficulty());
      inventory.setItem(11, icon(selected.icon() == null || selected.icon().isAir()
              ? Material.IRON_SWORD : selected.icon(),
          "Kit: " + pretty(choice.kit()), NamedTextColor.AQUA, false,
          "The Equipment You And The Bot Will Use", "Click To Choose A Kit"));
      inventory.setItem(13, icon(difficulty.effectiveIcon(),
          "Difficulty: " + difficulty.prettyDisplayName(), NamedTextColor.GOLD, false,
          "Choose How Challenging The Bot Is", "Click To Choose A Difficulty"));
      inventory.setItem(15, icon(Material.TARGET,
          "Mode: " + pretty(choice.mode().id()), NamedTextColor.AQUA, false,
          choice.mode().description(), "Click To Choose A Mode"));
      inventory.setItem(22, icon(Material.CHEST, "Preview Your Kit", NamedTextColor.AQUA,
          false, "See Equipment Before Starting", "Click To Preview"));
      boolean canSettings = player.hasPermission("practice.settings")
          || player.hasPermission("practice.admin") || player.hasPermission("practice.*");
      inventory.setItem(31, icon(canSettings ? Material.REPEATER : Material.BARRIER,
          "Optional Settings", canSettings ? NamedTextColor.AQUA : NamedTextColor.RED, false,
          "Match Options And Bot Abilities",
          canSettings ? "Click To Open" : "Locked: Missing practice.settings"));
      boolean canStats = player.hasPermission("practice.stats")
          || player.hasPermission("practice.admin") || player.hasPermission("practice.*");
      inventory.setItem(33, icon(canStats ? Material.BOOK : Material.BARRIER,
          "Your Progress", canStats ? NamedTextColor.GOLD : NamedTextColor.RED, false,
          canStats ? "Click To View Your Results" : "Locked: Missing practice.stats"));
    }
    else if (screen == Screen.PREVIEW) {
      for (int slot = 0; slot < 41; slot++) {
        inventory.setItem(slot, null);
      }
      selected
          .previewItems()
          .forEach(
              (slot, item) -> {
                if (!item.getType().isAir()) {
                  dev.insanmiy.practiceplugin.ItemMetadata.edit(item,
                      meta -> {
                        List<Component> lore =
                            TextUI.lore(meta) == null ? new ArrayList<>() : new ArrayList<>(TextUI.lore(meta));
                        lore.add(
                            text(
                                "Preview Only: Identical For Both Fighters",
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
              "Slots 0 To 35: Kit Inventory",
              "Bottom: Armor And Offhand"));
      inventory.setItem(
          42,
          icon(
              Material.COOKED_BEEF,
              "Food Regeneration",
              NamedTextColor.GOLD,
              false,
              selected.regeneration() ? "Enabled: Vanilla Saturation Healing" : "Disabled For This Kit"));
      inventory.setItem(
          43, icon(Material.RED_DYE, selected.health() + " HP Each", NamedTextColor.RED, false));
    } else {
      int from = page * MenuPaging.PAGE_SIZE;
      for (int index = 0; index < MenuPaging.PAGE_SIZE && from + index < entries.size(); index++) {
        String name = entries.get(from + index);
        ItemStack card;
        if (screen == Screen.KITS) {
          Kit kit = plugin.kits().get(name);
          Material mat = kit != null && kit.icon() != null && kit.icon() != Material.AIR ? kit.icon() : Material.IRON_SWORD;
          card =
              icon(
                  mat,
                  (starred.contains(name) ? "* " : "") + pretty(name),
                  choice.kit().equals(name) ? NamedTextColor.GREEN : NamedTextColor.AQUA,
                  choice.kit().equals(name),
                  kit != null ? kit.description() : "",
                  "",
                  kit != null ? (kit.health() + " HP | Regen: " + (kit.regeneration() ? "Enabled" : "Disabled")) : "",
                  choice.kit().equals(name) ? "Selected | Click To Keep This Kit" : "Click To Use This Kit",
                  "Right Click: Preview | Shift Click: Favorite");
        } else if (screen == Screen.DIFFICULTIES) {
          var d = plugin.difficulties().get(name);
          Material mat = d != null ? d.effectiveIcon() : Material.IRON_SWORD;
          String dispName = d != null ? d.prettyDisplayName() : pretty(name);
          List<String> lore = new ArrayList<>();
          if (d != null && d.description() != null && !d.description().isBlank()) {
            lore.add(d.description());
            lore.add("");
          }
          if (d != null) {
            lore.add(!d.attacksEnabled() ? "Target Practice: This Bot Does Not Attack"
                : d.decisionTicks() >= 6 ? "Slower Reactions For Learning The Basics"
                : d.decisionTicks() >= 3 ? "Steady Reactions For Regular Practice"
                : "Fast Reactions For A Tougher Challenge");
          }
          if (plugin.settings().hasBotOverrides(player.getUniqueId())) {
            lore.add("Your Custom Bot Settings Also Apply");
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
        } else if (screen == Screen.MODES) {
          PracticeMode mode = PracticeMode.parse(name);
          Material modeIcon =
              switch (mode) {
                case MATCH -> Material.GOLDEN_SWORD;
                case DUEL -> Material.IRON_SWORD;
                case ENDLESS -> Material.TARGET;
                case V2_1 -> Material.DIAMOND_SWORD;
                case V3_1 -> Material.NETHERITE_SWORD;
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
                          : mode == PracticeMode.V2_1
                              ? "Best Of " + choice.bestOf() + "; 2 Bots vs 1 Player"
                              : mode == PracticeMode.V3_1
                                  ? "Best Of " + choice.bestOf() + "; 3 Bots vs 1 Player"
                                  : "Continuous Rounds With Score Tracking",
                  "Click To Select");
        } else {
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

    if (screen != Screen.PREVIEW && screen != Screen.HOME) {
      if (page > 0) {
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
      if (page + 1 < totalPages) {
        inventory.setItem(44, icon(Material.ARROW, "Next Page", NamedTextColor.AQUA, false));
      }
    }

    Difficulty selectedDiff = plugin.difficulties().get(choice.difficulty());
    String diffDisplayName = selectedDiff != null ? selectedDiff.prettyDisplayName() : pretty(choice.difficulty());

    PracticeMode currentMode = choice.mode();

    if (screen != Screen.HOME) {
      inventory.setItem(
        45,
        icon(
            Material.ARROW,
            "Back To Practice Setup",
            NamedTextColor.AQUA,
            false,
            "Keep Your Choices And Return"));
    }

    if ((screen == Screen.HOME || screen == Screen.MODES)
        && (currentMode == PracticeMode.MATCH || currentMode.isOutnumbered())) {
      inventory.setItem(
          40,
          icon(
              Material.COMPARATOR,
              "Match Length: Best Of " + choice.bestOf(),
              NamedTextColor.YELLOW,
              false,
              "First To " + (choice.bestOf() / 2 + 1) + " Round Wins",
              "",
              "Left Click: +2 Rounds",
              "Right Click: Reduce By 2 Rounds"));
    }

    boolean canStart = player.hasPermission("practice.start")
        || player.hasPermission("practice.admin")
        || player.hasPermission("practice.*");
    boolean arenaAvailable = plugin.hasConfiguredArena();
    List<String> startLore = new ArrayList<>();
    if (!canStart) {
      startLore.add("Locked: Missing practice.start");
    } else if (!arenaAvailable) {
      startLore.add("No Arena Configured On This Server.");
      startLore.add("Ask An Admin To Configure Spawns:");
      startLore.add("/practice arena setplayer & setbot");
    } else {
      startLore.add("Kit: " + pretty(choice.kit()));
      startLore.add("Difficulty: " + diffDisplayName);
      startLore.add("Mode: " + pretty(currentMode.id()));
      if (currentMode == PracticeMode.MATCH || currentMode.isOutnumbered()) {
        startLore.add("First To " + (choice.bestOf() / 2 + 1) + " Round Wins");
      }
      if (plugin.settings().hasBotOverrides(player.getUniqueId())) {
        startLore.add("");
        startLore.add("! Custom Bot Tuning Active");
      }
      startLore.add("");
      startLore.add("Click To Launch Practice");
    }

    inventory.setItem(
        49,
        icon(
            (!canStart || !arenaAvailable) ? Material.RED_CONCRETE : Material.LIME_CONCRETE,
            !canStart ? "Start Practice Locked" : (arenaAvailable ? "Start Practice" : "No Arena Configured"),
            (!canStart || !arenaAvailable) ? NamedTextColor.RED : NamedTextColor.GREEN,
            canStart && arenaAvailable,
            startLore.toArray(String[]::new)));

    inventory.setItem(53, icon(Material.BARRIER, "Close", NamedTextColor.RED, false,
        "You Can Return With /practice"));


    if (!canReuse) {
      player.openInventory(inventory);
    }
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
            if (raw == null || raw.isBlank()) {
              lines.add(Component.empty());
              continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : raw.split(" ")) {
              if (!line.isEmpty() && line.length() + word.length() + 1 > 38) {
                lines.add(text(line.toString(), NamedTextColor.GRAY));
                line.setLength(0);
              }
              if (!line.isEmpty()) {
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
    if (!(event.getView().getTopInventory().getHolder() instanceof View view)) {
      return;
    }
    event.setCancelled(true);
    if (!(event.getWhoClicked() instanceof Player player)
        || !view.owner.equals(player.getUniqueId())) {
      return;
    }
    int slot = event.getRawSlot();
    if (slot < 0 || slot >= 54) {
      return;
    }
    boolean right = event.isRightClick(), shift = event.isShiftClick();
    if (!event.isLeftClick() && !right) {
      return;
    }
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (!player.isOnline()
                  || player.getOpenInventory().getTopInventory().getHolder() != view) {
                return;
              }
              if (!player.hasPermission("practice.use")
                  && !player.hasPermission("practice.admin")
                  && !player.hasPermission("practice.*")) {
                player.closeInventory();
                return;
              }
              if (!player.hasPermission("practice.menu")
                  && !player.hasPermission("practice.admin")
                  && !player.hasPermission("practice.*")) {
                player.closeInventory();
                return;
              }
              Selection choice = view.choice;
              try {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, .35f, 1.1f);

                // Setup Actions
                if (view.screen == Screen.HOME && slot == 29) {
                  if (!player.hasPermission("practice.kits.editor")
                      && !player.hasPermission("practice.admin")
                      && !player.hasPermission("practice.*")) {
                    throw new IllegalArgumentException("Missing Permission: practice.kits.editor");
                  }
                  Bukkit.dispatchCommand(player, "practice kit editor");
                  return;
                }
                if (view.screen == Screen.HOME) {
                  switch (slot) {
                    case 11 -> show(player, choice, Screen.KITS, 0);
                    case 13 -> show(player, choice, Screen.DIFFICULTIES, 0);
                    case 15 -> show(player, choice, Screen.MODES, 0);
                    case 22 -> show(player, choice, Screen.PREVIEW, 0);
                    case 31 -> plugin.openSettings(player);
                    case 33 -> {
                      if (!player.hasPermission("practice.stats")
                          && !player.hasPermission("practice.admin")
                          && !player.hasPermission("practice.*")) {
                        throw new IllegalArgumentException("Missing Permission: practice.stats");
                      }
                      show(player, choice, Screen.STATS, 0);
                    }
                    default -> {}
                  }
                  if (slot != 40 && slot != 49 && slot != 53) {
                    return;
                  }
                }

                // Middle area selections (kits, difficulties, modes)
                int index = MenuPaging.index(slot);
                if (view.screen != Screen.PREVIEW && view.screen != Screen.HOME
                    && index >= 0
                    && view.page * MenuPaging.PAGE_SIZE + index < view.entries.size()) {
                  String name = view.entries.get(view.page * MenuPaging.PAGE_SIZE + index);
                  if (view.screen == Screen.KITS) {
                    if (shift) {
                      Set<String> starred = favorites(player);
                      if (!starred.remove(name)) {
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
                      show(player, choice, Screen.KITS, view.page);
                    } else if (right) {
                      show(
                          player,
                          new Selection(name, choice.difficulty(), choice.mode(), choice.bestOf()),
                          Screen.PREVIEW,
                          view.page);
                    } else {
                      show(
                          player,
                          new Selection(name, choice.difficulty(), choice.mode(), choice.bestOf()),
                          Screen.HOME,
                          0);
                    }
                  } else if (view.screen == Screen.DIFFICULTIES) {
                    show(
                        player,
                        new Selection(choice.kit(), name, choice.mode(), choice.bestOf()),
                        Screen.HOME,
                        0);
                  } else if (view.screen == Screen.MODES) {
                    PracticeMode chosenMode = PracticeMode.parse(name);
                    int bestOf = (chosenMode == PracticeMode.MATCH || chosenMode.isOutnumbered())
                        ? Math.max(3, choice.bestOf() % 2 == 0 ? choice.bestOf() + 1 : choice.bestOf())
                        : choice.bestOf();
                    show(
                        player,
                        new Selection(
                            choice.kit(),
                            choice.difficulty(),
                            chosenMode,
                            bestOf),
                        Screen.HOME,
                        0);
                  }
                  return;
                }

                // navigation and action bar
                switch (slot) {
                  case 36 -> {
                    if (view.screen != Screen.PREVIEW && view.screen != Screen.HOME && view.page > 0) {
                      show(player, choice, view.screen, view.page - 1);
                    }
                  }
                  case 44 -> {
                    if (view.screen != Screen.PREVIEW && view.screen != Screen.HOME && view.page + 1 < MenuPaging.pages(view.entries.size())) {
                      show(player, choice, view.screen, view.page + 1);
                    }
                  }
                  case 45 -> show(player, choice, Screen.HOME, 0);
                  case 40 -> {
                    if ((view.screen != Screen.HOME && view.screen != Screen.MODES)
                        || (choice.mode() != PracticeMode.MATCH && !choice.mode().isOutnumbered())) {
                      return;
                    }
                    int nextBestOf = Math.clamp(choice.bestOf() + (right ? -2 : 2), 3, 99);
                    show(
                        player,
                        new Selection(choice.kit(), choice.difficulty(), choice.mode(), nextBestOf),
                        view.screen,
                        view.page);
                  }
                  case 49 -> {
                    if (!player.hasPermission("practice.start")
                        && !player.hasPermission("practice.admin")
                        && !player.hasPermission("practice.*")) {
                      throw new IllegalArgumentException("Missing Permission: practice.start");
                    }
                    if (!plugin.hasConfiguredArena()) {
                      return;
                    }
                    player.closeInventory();
                    plugin.startFromMenu(player, choice);
                  }
                  case 53 -> player.closeInventory();
                  default -> {}
                }
              } catch (IllegalArgumentException e) {
                player.sendMessage(Messages.color(e.getMessage().contains("\u00A7") || e.getMessage().contains("&") ? e.getMessage() : "&c" + e.getMessage()));
              } catch (IOException e) {
                plugin.messages().send(player, "general.menu-favorites-save-error");
                plugin.getLogger().warning(e.toString());
              }
            });
  }

  @EventHandler
  public void drag(InventoryDragEvent event) {
    if (event.getView().getTopInventory().getHolder() instanceof View) {
      event.setCancelled(true);
    }
  }

  Selection draft(UUID id) {
    return drafts.get(id);
  }

  void cleanup(UUID id) {
    drafts.remove(id);
  }

  void closeAll() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (player.getOpenInventory().getTopInventory().getHolder() instanceof View) {
        player.closeInventory();
      }
    }
    drafts.clear();
  }
}
