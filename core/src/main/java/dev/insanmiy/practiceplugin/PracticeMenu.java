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
            case KITS -> "INSYN / PRACTICE KITS";
            case PREVIEW -> "LOADOUT / " + pretty(choice.kit());
            case DIFFICULTIES -> "PRACTICE / DIFFICULTY";
            case MODES -> "PRACTICE / MODES";
            case STATS -> "PRACTICE / YOUR PROGRESS";
          };
      inventory = TextUI.inventory(this, 54, text(title, NamedTextColor.DARK_AQUA));
    }

    @Override
    public Inventory getInventory() {
      return inventory;
    }

    public int[] decorativeSlots() {
      return screen == Screen.PREVIEW ? new int[0] : new int[] {9, 17, 18, 26, 27, 35};
    }

    public int pulseSlot() {
      return screen == Screen.KITS ? 49 : -1;
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
    if (plugin.kits().isEmpty() || plugin.difficulties().isEmpty())
      throw new IllegalArgumentException("No valid kits or difficulties. Check configuration.");
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
    for (int i = 0; i < 54; i++)
      inventory.setItem(
          i, icon(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, false));
    for (int i = 0; i < 9; i++)
      inventory.setItem(i, icon(Material.CYAN_STAINED_GLASS_PANE, " ", NamedTextColor.AQUA, false));
    Kit selected = plugin.kits().get(choice.kit());

    if (screen == Screen.KITS) {
      for (int i = 0; i < CATEGORIES.size(); i++) {
        String cat = CATEGORIES.get(i);
        inventory.setItem(
            i,
            icon(
                CATEGORY_ICONS[i],
                pretty(cat),
                NamedTextColor.AQUA,
                category.equals(cat),
                category.equals(cat) ? "Currently browsing" : "Click to browse this category"));
      }
      inventory.setItem(
          7,
          icon(
              Material.KNOWLEDGE_BOOK,
              "Custom kit editor",
              NamedTextColor.GOLD,
              false,
              "Click to create and manage custom kits",
              "No fixed limit; edit your own kits",
              "Left click a kit: select",
              "Right click: inspect full loadout",
              "Shift click: toggle favorite",
              "Select difficulty and mode below",
              "Green center button starts practice"));
      if (entries.isEmpty())
        inventory.setItem(
            22,
            icon(
                Material.BARRIER,
                "No kits here",
                NamedTextColor.YELLOW,
                false,
                "Shift-click a kit to favorite it.",
                "Choose All to browse every kit."));
    } else {
      inventory.setItem(
          4,
          icon(
              Material.NETHER_STAR,
              pretty(screen.name()),
              NamedTextColor.GOLD,
              true,
              "Your selection is kept while navigating."));
    }

    if (screen == Screen.PREVIEW) {
      for (int slot = 0; slot < 41; slot++) inventory.setItem(slot, null);
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
                                "Preview only - identical for both fighters",
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
              "Exact inventory layout",
              NamedTextColor.AQUA,
              false,
              "Slots 0-35: kit inventory",
              "Bottom: boots / legs / chest / helmet / offhand",
              "Stacks, potion types and enchants shown"));
      inventory.setItem(
          42,
          icon(
              Material.COOKED_BEEF,
              "Food regeneration",
              NamedTextColor.GOLD,
              false,
              selected.regeneration()
                  ? "ON - vanilla saturation healing"
                  : "OFF - explicitly disabled by this kit"));
      inventory.setItem(
          43, icon(Material.RED_DYE, selected.health() + " HP each", NamedTextColor.RED, false));
    } else {
      int from = page * MenuPaging.PAGE_SIZE;
      for (int index = 0; index < MenuPaging.PAGE_SIZE && from + index < entries.size(); index++) {
        String name = entries.get(from + index);
        ItemStack card;
        if (screen == Screen.KITS) {
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
                  kit.health() + " HP | Regen " + (kit.regeneration() ? "ON" : "OFF"),
                  kit.count(Material.GOLDEN_APPLE)
                      + " apples | "
                      + kit.count(Material.SPLASH_POTION)
                      + " splash pots",
                  kit.count(Material.COBWEB)
                      + " webs | "
                      + kit.count(Material.EXPERIENCE_BOTTLE)
                      + " XP bottles",
                  "",
                  choice.kit().equals(name) ? "SELECTED" : "Left click to select",
                  "Right click: preview | Shift: favorite");
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
            lore.add("Decision interval: " + d.decisionTicks() + " ticks");
            lore.add("Perception delay: " + d.perceptionTicks() + " ticks");
            lore.add("Aim error: " + String.format(Locale.ROOT, "%.1f", d.aimError()) + " degrees");
            lore.add("Shield defense: " + Math.round(d.shieldChance() * 100) + "%");
            lore.add("Sprint reset (W-tap): " + Math.round(d.sprintResetChance() * 100) + "%");
            lore.add("Strafe dodge: " + Math.round(d.strafeStrength() * 100) + "%");
            lore.add("Heal urgency: " + Math.round(d.healThreshold() * 100) + "% HP");
          }
          lore.add("");
          lore.add(choice.difficulty().equals(name) ? "SELECTED" : "Click to select");
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
                      ? "Best of " + choice.bestOf() + "; first to " + (choice.bestOf() / 2 + 1) + " wins"
                      : mode == PracticeMode.DUEL
                          ? "Single 1v1 duel to the knockout"
                          : mode == PracticeMode.DUMMY
                              ? "Passive target for aim and combos"
                              : "Continuous rounds with score tracking",
                  mode == PracticeMode.ENDLESS || mode == PracticeMode.DUMMY
                      ? "/practice stop to finish"
                      : "Concludes automatically when finished",
                  "Click to select");
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

    if (screen == Screen.KITS) {
      inventory.setItem(
          37,
          icon(
              selected.icon(),
              "Selected: " + pretty(choice.kit()),
              NamedTextColor.GREEN,
              true,
              "Right-click any kit for a full preview"));
      inventory.setItem(
          39,
          icon(
              Material.COOKED_BEEF,
              "Saturation & healing",
              NamedTextColor.GOLD,
              false,
              selected.regeneration() ? "Food regeneration: ON" : "Food regeneration: OFF",
              "Saturation fuels vanilla healing.",
              "World naturalRegeneration must be ON."));
      inventory.setItem(
          41,
          icon(
              Material.PAPER,
              "Page " + (page + 1) + " / " + MenuPaging.pages(entries.size()),
              NamedTextColor.AQUA,
              false,
              entries.size() + " kits in this category"));
      List<String> problems = plugin.startProblems(player);
      inventory.setItem(
          43,
          icon(
              problems.isEmpty() ? Material.LIME_DYE : Material.ORANGE_DYE,
              problems.isEmpty() ? "Ready to practice" : "Before starting",
              NamedTextColor.YELLOW,
              false,
              problems.isEmpty()
                  ? new String[] {"Arena and player checks passed"}
                  : problems.toArray(String[]::new)));
    }

    if (screen != Screen.PREVIEW) {
      inventory.setItem(
          40,
          icon(
              Material.REPEATER,
              "Bot & match settings",
              NamedTextColor.AQUA,
              false,
              "Bot attack skill, reaction speed, aim accuracy,",
              "tactics, animations and match rules",
              "Click to customize without config.yml"));
      if (page > 0)
        inventory.setItem(36, icon(Material.ARROW, "Previous page", NamedTextColor.AQUA, false));
      if (page + 1 < MenuPaging.pages(entries.size()))
        inventory.setItem(44, icon(Material.ARROW, "Next page", NamedTextColor.AQUA, false));
    }
    inventory.setItem(
        45,
        icon(
            Material.COMPASS,
            screen == Screen.KITS ? "All kits" : "Back to kits",
            NamedTextColor.AQUA,
            false));
    Difficulty selectedDiff = plugin.difficulties().get(choice.difficulty());
    String diffDisplayName = selectedDiff != null ? selectedDiff.prettyDisplayName() : pretty(choice.difficulty());
    Material diffIcon = selectedDiff != null ? selectedDiff.effectiveIcon() : Material.COMPARATOR;
    inventory.setItem(
        46,
        icon(
            diffIcon,
            "Difficulty: " + diffDisplayName,
            NamedTextColor.GOLD,
            false,
            "Click to choose a difficulty"));
    inventory.setItem(
        47,
        icon(
            Material.TARGET,
            "Mode: " + pretty(choice.mode().id()),
            NamedTextColor.AQUA,
            false,
            "Click to choose a mode"));
    inventory.setItem(
        48,
        icon(
            Material.PAPER,
            "Series: best of " + choice.bestOf(),
            NamedTextColor.YELLOW,
            false,
            "For match mode only",
            "Left: increase | Right: decrease"));
    inventory.setItem(
        49,
        icon(
            Material.LIME_CONCRETE,
            "START PRACTICE",
            NamedTextColor.GREEN,
            true,
            pretty(choice.kit()) + " / " + diffDisplayName,
            choice.mode().description(),
            "Equal gear and health for both fighters",
            "Click to start | /practice stop to finish"));
    inventory.setItem(
        50,
        icon(
            Material.ENDER_CHEST,
            "Inspect loadout",
            NamedTextColor.AQUA,
            false,
            "View every item, potion and enchantment"));
    inventory.setItem(
        51,
        icon(
            Material.BOOK,
            "Your progress",
            NamedTextColor.GOLD,
            false,
            "Saved statistics by practice mode"));
    inventory.setItem(
        52,
        icon(
            Material.CLOCK,
            "Rematch",
            NamedTextColor.YELLOW,
            false,
            "Start with your previous session's settings"));
    inventory.setItem(53, icon(Material.BARRIER, "Close", NamedTextColor.RED, false));
    if (screen != Screen.PREVIEW)
      inventory.setItem(8, icon(Material.BARRIER, "Close", NamedTextColor.RED, false));
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
            StringBuilder line = new StringBuilder();
            for (String word : raw.split(" ")) {
              if (line.length() > 0 && line.length() + word.length() + 1 > 38) {
                lines.add(text(line.toString(), NamedTextColor.GRAY));
                line.setLength(0);
              }
              if (line.length() > 0) line.append(' ');
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
    if (!(event.getView().getTopInventory().getHolder() instanceof View view)) return;
    event.setCancelled(true);
    if (!(event.getWhoClicked() instanceof Player player)
        || !view.owner.equals(player.getUniqueId())) return;
    int slot = event.getRawSlot();
    if (slot < 0 || slot >= 54) return;
    boolean right = event.isRightClick(), shift = event.isShiftClick();
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (!player.isOnline()
                  || player.getOpenInventory().getTopInventory().getHolder() != view) return;
              if (!player.hasPermission("practice.use")) {
                player.closeInventory();
                return;
              }
              Selection choice = view.choice;
              try {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, .35f, 1.1f);
                if (view.screen != Screen.PREVIEW && slot == 40) {
                  plugin.openSettings(player);
                  return;
                }
                if (view.screen == Screen.KITS && slot == 7) {
                  Bukkit.dispatchCommand(player, "practice kit editor");
                  return;
                }
                if (view.screen == Screen.KITS && slot <= 6) {
                  show(player, choice, Screen.KITS, CATEGORIES.get(slot), 0);
                  return;
                }
                int index = MenuPaging.index(slot);
                if (view.screen != Screen.PREVIEW
                    && index >= 0
                    && view.page * MenuPaging.PAGE_SIZE + index < view.entries.size()) {
                  String name = view.entries.get(view.page * MenuPaging.PAGE_SIZE + index);
                  if (view.screen == Screen.KITS) {
                    if (shift) {
                      Set<String> starred = favorites(player);
                      if (!starred.remove(name)) starred.add(name);
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
                    } else
                      show(
                          player,
                          new Selection(name, choice.difficulty(), choice.mode(), choice.bestOf()),
                          right ? Screen.PREVIEW : Screen.KITS,
                          view.category,
                          view.page);
                  } else if (view.screen == Screen.DIFFICULTIES)
                    show(
                        player,
                        new Selection(choice.kit(), name, choice.mode(), choice.bestOf()),
                        Screen.KITS,
                        view.category,
                        0);
                  else if (view.screen == Screen.MODES) {
                    PracticeMode chosenMode = PracticeMode.parse(name);
                    int bestOf = chosenMode == PracticeMode.MATCH ? Math.max(3, choice.bestOf() % 2 == 0 ? choice.bestOf() + 1 : choice.bestOf()) : choice.bestOf();
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
                switch (slot) {
                  case 8 -> {
                    if (view.screen != Screen.PREVIEW) player.closeInventory();
                  }
                  case 36 -> {
                    if (view.screen != Screen.PREVIEW)
                      show(player, choice, view.screen, view.category, view.page - 1);
                  }
                  case 44 -> {
                    if (view.screen != Screen.PREVIEW)
                      show(player, choice, view.screen, view.category, view.page + 1);
                  }
                  case 45 -> show(player, choice, Screen.KITS, "all", 0);
                  case 46 -> show(player, choice, Screen.DIFFICULTIES, view.category, 0);
                  case 47 -> show(player, choice, Screen.MODES, view.category, 0);
                  case 48 ->
                      show(
                          player,
                          new Selection(
                              choice.kit(),
                              choice.difficulty(),
                              choice.mode(),
                              right
                                  ? (choice.bestOf() <= 3 ? 11 : choice.bestOf() - 2)
                                  : (choice.bestOf() >= 11 ? 3 : choice.bestOf() + 2)),
                          view.screen,
                          view.category,
                          view.page);
                  case 49 -> {
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
                  case 50 -> show(player, choice, Screen.PREVIEW, view.category, view.page);
                  case 51 -> show(player, choice, Screen.STATS, view.category, 0);
                  case 52 -> {
                    player.closeInventory();
                    Bukkit.dispatchCommand(player, "practice rematch");
                  }
                  case 53 -> player.closeInventory();
                  default -> {}
                }
              } catch (IllegalArgumentException e) {
                player.sendMessage(TextUI.legacy(text(e.getMessage(), NamedTextColor.RED)));
              } catch (IOException e) {
                player.sendMessage(TextUI.legacy(
                    text("Could not save favorites. Check the server log.", NamedTextColor.RED)));
                plugin.getLogger().warning(e.toString());
              }
            });
  }

  @EventHandler
  public void drag(InventoryDragEvent event) {
    if (event.getView().getTopInventory().getHolder() instanceof View) event.setCancelled(true);
  }

  Selection draft(UUID id) {
    return drafts.get(id);
  }

  void closeAll() {
    for (Player player : Bukkit.getOnlinePlayers())
      if (player.getOpenInventory().getTopInventory().getHolder() instanceof View)
        player.closeInventory();
    drafts.clear();
  }
}
