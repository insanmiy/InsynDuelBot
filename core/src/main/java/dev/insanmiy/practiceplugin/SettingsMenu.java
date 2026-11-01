package dev.insanmiy.practiceplugin;

import dev.insanmiy.practiceplugin.config.Difficulty;
import java.io.*;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.jetbrains.annotations.NotNull;

final class SettingsMenu implements Listener {
  private final PracticePlugin plugin;

  private enum Page {
    MATCH("Match Settings"),
    BOT("Bot Abilities"),
    ADVANCED("Advanced Bot Tuning");

    final String title;

    Page(String title) {
      this.title = title;
    }
  }

  private record BotSettingDef(
      String key,
      Material icon,
      String displayName,
      String description,
      String[] levelNames) {}

  private static final Map<Integer, BotSettingDef> BOT_SETTINGS =
      Map.of(
          10,
          new BotSettingDef(
              "bot-attack-charge",
              Material.DIAMOND_SWORD,
              "Attack Timing",
              "How Long The Bot Waits Before Swinging",
              new String[] {
                "Full Power (100% / Vanilla)",
                "Fast (95% Charge)",
                "Rapid (90% Charge)",
                "Very Rapid (85% Charge)"
              }),
          11,
          new BotSettingDef(
              "bot-aim",
              Material.BOW,
              "Aim Accuracy",
              "How Precisely The Bot Aims At You",
              new String[] {
                "Godlike (0.0° / 100% Accurate)",
                "Master (0.2° Error)",
                "High (1.0° Error)",
                "Medium (2.5° Error)",
                "Low (5.0° Error)",
                "Shaky (10.0° Error)"
              }),
          12,
          new BotSettingDef(
              "bot-reaction",
              Material.FEATHER,
              "Reaction Speed",
              "How Often The Bot Chooses Its Next Action",
              new String[] {
                "Instant (1t / 50ms)",
                "Very Fast (2t / 100ms)",
                "Fast (3t / 150ms)",
                "Normal (4t / 200ms)",
                "Slow (6t / 300ms)",
                "Delayed (10t / 500ms)"
              }),
          13,
          new BotSettingDef(
              "bot-sprint-reset",
              Material.LEATHER_BOOTS,
              "Sprint Reset",
              "How Often The Bot Restarts Its Sprint After Hits",
              new String[] {
                "Always (100%)",
                "High (80%)",
                "Medium (50%)",
                "Low (20%)",
                "Disabled (0%)"
              }),
          14,
          new BotSettingDef(
              "bot-shield-chance",
              Material.SHIELD,
              "Shield Defense",
              "How Consistently The Bot Blocks Incoming Attacks",
              new String[] {
                "Always (100%)",
                "High (85%)",
                "Medium (60%)",
                "Low (30%)",
                "Disabled (0%)"
              }),
          15,
          new BotSettingDef(
              "bot-strafe-speed",
              Material.SUGAR,
              "Strafe Movement",
              "Dodging And Circle-Strafing Movement Speed",
              new String[] {
                "Max Speed (100%)",
                "Fast (80%)",
                "Normal (60%)",
                "Light (35%)",
                "Disabled (0%)"
              }),
          16,
          new BotSettingDef(
              "bot-heal-threshold",
              Material.SPLASH_POTION,
              "Healing Threshold",
              "Health Level Where The Bot Starts Healing",
              new String[] {
                "Aggressive (75% HP)",
                "High (65% HP)",
                "Normal (50% HP)",
                "Clutch (35% HP)"
              }));

  private static final Map<Integer, String> FLAGS =
      Map.of(
          19, "healing",
          20, "utilities",
          21, "criticals",
          22, "shields",
          23, "strafing",
          24, "counters",
          31, "hud",
          32, "animations");

  private static final Map<Integer, String> NUMBERS =
      Map.of(28, "countdown", 29, "between-rounds", 30, "saturation");

  private static final class View implements AnimatedMenu {
    final UUID owner;
    final Page page;
    final Inventory inventory;

    View(Player p, Page page) {
      owner = p.getUniqueId();
      this.page = page;
      inventory = TextUI.inventory(this, 54, Component.text("Settings / " + page.title));
    }

    @Override
    public @NotNull Inventory getInventory() {
      return inventory;
    }

    public int[] decorativeSlots() {
      return new int[] {
        0, 1, 3, 5, 7, 8,
        9, 17, 18, 26, 27, 35, 36, 44,
        46, 47, 48, 50, 51, 52
      };
    }

    public int pulseSlot() {
      return -1;
    }
  }

  SettingsMenu(PracticePlugin plugin) {
    this.plugin = plugin;
  }

  void open(Player p) {
    open(p, Page.MATCH);
  }

  private void open(Player p, Page page) {
    if (!p.hasPermission("practice.settings") && !p.hasPermission("practice.admin") && !p.hasPermission("practice.*"))
      throw new IllegalArgumentException(plugin.messages().get("general.no-permission", "%permission%", "practice.settings"));
    if (plugin.sessionFor(p) != null)
      throw new IllegalArgumentException(plugin.messages().get("session.stop-before-menu"));

    boolean canReuse = false;
    View view;
    if (p.getOpenInventory().getTopInventory().getHolder() instanceof View currentView
        && currentView.owner.equals(p.getUniqueId())
        && currentView.page == page) {
      canReuse = true;
      view = currentView;
    } else {
      view = new View(p, page);
    }

    for (int slot = 0; slot < view.inventory.getSize(); slot++) {
      boolean border = slot < 9 || slot >= 36 || slot % 9 == 0 || slot % 9 == 8;
      view.inventory.setItem(slot, item(border ? Material.GRAY_STAINED_GLASS_PANE
          : Material.BLACK_STAINED_GLASS_PANE, " "));
    }
    view.inventory.setItem(0, item(Material.CYAN_STAINED_GLASS_PANE, " "));
    view.inventory.setItem(8, item(Material.CYAN_STAINED_GLASS_PANE, " "));
    PlayerSettings prefs = plugin.settings();
    UUID id = p.getUniqueId();

    view.inventory.setItem(2, item(Material.CLOCK,
        "Match Settings" + (page == Page.MATCH ? " (Selected)" : ""),
        "Countdown, Food, And Display Options", "Click To Open"));
    view.inventory.setItem(4, item(Material.IRON_SWORD,
        "Bot Abilities" + (page == Page.BOT ? " (Selected)" : ""),
        "Choose Which Actions The Bot Can Use", "Click To Open"));
    view.inventory.setItem(6, item(Material.COMPARATOR,
        "Advanced Tuning" + (page == Page.ADVANCED ? " (Selected)" : ""),
        "Override Individual Difficulty Settings", "Click To Open"));

    View targetView = view;
    BOT_SETTINGS.forEach(
        (slot, def) -> {
          if (page != Page.ADVANCED) {
            return;
          }
          int currentLevel = prefs.botSetting(id, def.key());
          String currentDisplay =
              currentLevel < 0 || currentLevel >= def.levelNames().length
                  ? "Default (From Difficulty)"
                  : def.levelNames()[currentLevel];
          boolean isCustom = currentLevel >= 0;
          targetView.inventory.setItem(
              slot,
              item(
                  def.icon(),
                  def.displayName(),
                  "Current: " + currentDisplay,
                  def.description(),
                  "",
                  isCustom ? "[Custom Override Active]" : "[Using Difficulty Default]",
                  "",
                  "Left Click: Next | Right Click: Previous",
                  "Shift Click: Use Difficulty Default"));
        });

    FLAGS.forEach(
        (slot, key) -> {
          if ((page == Page.MATCH && slot < 31)
              || (page == Page.BOT && slot >= 31)
              || page == Page.ADVANCED) {
            return;
          }
          boolean on = prefs.flag(id, key);
          targetView.inventory.setItem(
              slot,
              item(
                  on ? Material.LIME_DYE : Material.GRAY_DYE,
                  title(key) + ": " + (on ? "On" : "Off"),
                  flagDescription(key),
                  "Click To Toggle",
                  "Saved For Your Sessions"));
        });

    if (page == Page.MATCH) {
      NUMBERS.forEach(
          (slot, key) ->
              targetView.inventory.setItem(
                  slot,
                  item(
                      slot == 28 ? Material.CLOCK : slot == 29 ? Material.COMPASS : Material.COOKED_BEEF,
                      title(key) + ": " + prefs.number(id, key) + (key.equals("saturation") ? "" : "s"),
                      "Left Click: +1 | Right Click: Reduce By 1",
                      "Shift Click: Change By 5",
                      key.equals("saturation")
                          ? "Stored Food Energy For Both Fighters"
                          : "Seconds; Applies Next Session")));
    }

    if (page == Page.ADVANCED) {
      view.inventory.setItem(
          33,
          item(
              Material.REDSTONE,
              "Damage Diagnostics",
              "Advanced Debug Readout For All Active Matches",
              "Click To Toggle (/practice damage)"));
    }

    if (page == Page.MATCH) {
      view.inventory.setItem(
          22,
          item(
              Material.BOOK,
              "Match Rules",
              "Choose Your Mode In Practice Setup",
              "Win A Round By Defeating The Bot",
              "Use /practice Stop To Leave A Match"));
    }

    if (page == Page.BOT) {
      view.inventory.setItem(31, item(Material.COMPARATOR, "Advanced Bot Tuning",
          "Want More Control Over Aim Or Reactions?", "Click To Open Advanced Tuning"));
    }

    view.inventory.setItem(45, item(Material.ARROW, "Back To Practice Setup", "Your Match Selection Is Kept"));
    view.inventory.setItem(53, item(Material.BARRIER, "Close", "Changes Are Already Saved"));
    if (page == Page.ADVANCED) {
      view.inventory.setItem(
          49,
          item(
              Material.BLAZE_POWDER,
              "Reset Advanced Tuning",
              "Restores All 7 Combat Settings Above To",
              "Their Default Values From Difficulty Tier.",
              "Ability Toggles Stay As You Set Them",
              "Click To Reset"));
    }

    if (!canReuse) {
      p.openInventory(view.inventory);
    }
  }

  private static String flagDescription(String key) {
    return switch (key) {
      case "healing" -> "Allow The Bot To Use Healing Items";
      case "utilities" -> "Allow The Bot To Use Webs, Pearls, And Bows";
      case "criticals" -> "Allow The Bot To Jump For Critical Hits";
      case "shields" -> "Allow The Bot To Block With A Shield";
      case "strafing" -> "Allow The Bot To Move Sideways And Dodge";
      case "counters" -> "Bot Switches To Axe When You Block With Shield";
      case "hud" -> "Real-Time Health And Round BossBar";
      case "animations" -> "Pulsing Borders And UI Effects";
      default -> "Click To Toggle";
    };
  }

  private static String title(String text) {
    return switch (text) {
      case "countdown" -> "Starting Countdown";
      case "between-rounds" -> "Break Between Rounds";
      case "saturation" -> "Starting Food Saturation";
      case "hud" -> "Match Health Bar";
      case "animations" -> "Menu Animations";
      case "counters" -> "Shield Counters";
      default -> Difficulty.formatName(text);
    };
  }

  private static ItemStack item(Material type, String name, String... lore) {
    ItemStack item = new ItemStack(type);
    dev.insanmiy.practiceplugin.ItemMetadata.edit(item,
        meta -> {
          TextUI.name(meta, Component.text(name, NamedTextColor.AQUA)
              .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
          TextUI.lore(meta,
              Arrays.stream(lore).map(line -> Component.text(line, NamedTextColor.GRAY)
                  .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)).toList());
          meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
          meta.setEnchantmentGlintOverride(name.endsWith("(Selected)"));
        });
    return item;
  }

  @EventHandler
  public void click(InventoryClickEvent e) {
    if (!(e.getView().getTopInventory().getHolder() instanceof View view)) return;
    e.setCancelled(true);
    if (!(e.getWhoClicked() instanceof Player p)
        || !view.owner.equals(p.getUniqueId())
        || e.getRawSlot() < 0
        || e.getRawSlot() >= 54) return;
    int slot = e.getRawSlot();
    boolean right = e.isRightClick();
    boolean shift = e.isShiftClick();
    if (!e.isLeftClick() && !right) {
      return;
    }
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (!p.isOnline() || p.getOpenInventory().getTopInventory().getHolder() != view)
                return;
              if (plugin.sessionFor(p) != null
                  || (!p.hasPermission("practice.settings")
                      && !p.hasPermission("practice.admin")
                      && !p.hasPermission("practice.*"))) {
                p.closeInventory();
                return;
              }
              apply(p, view, slot, right, shift);
            });
  }

  private void apply(Player p, View view, int slot, boolean right, boolean shift) {
    PlayerSettings prefs = plugin.settings();
    UUID id = p.getUniqueId();
    try {
      if (slot == 2 || slot == 4 || slot == 6) {
        open(p, Page.values()[slot / 2 - 1]);
        return;
      }
      if (slot == 53) {
        p.closeInventory();
        return;
      }
      if (view.page == Page.BOT && slot == 31) {
        open(p, Page.ADVANCED);
        return;
      }
      if (slot == 45) {
        p.closeInventory();
        p.performCommand("practice menu");
        return;
      }
      if (view.page == Page.ADVANCED && slot == 49) {
        prefs.resetBotSettings(id);
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.4f, 1.2f);
        plugin.messages().send(p, "settings.reset-defaults");
        open(p, view.page);
        return;
      }
      if (view.page == Page.ADVANCED && slot == 33) {
        p.performCommand("practice damage");
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);
        return;
      }
      if (view.page == Page.ADVANCED && BOT_SETTINGS.containsKey(slot)) {
        BotSettingDef def = BOT_SETTINGS.get(slot);
        int total = def.levelNames().length;
        int current = prefs.botSetting(id, def.key());
        int next;
        if (shift) {
          next = -1;
        }
        else if (right) {
          if (current <= -1) next = total - 1;
          else next = current - 1;
        } else {
          if (current >= total - 1) next = -1;
          else next = current + 1;
        }
        prefs.set(id, def.key(), next);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
      } else if (FLAGS.containsKey(slot)
          && ((view.page == Page.MATCH && slot >= 31)
              || (view.page == Page.BOT && slot < 31))) {
        String key = FLAGS.get(slot);
        boolean next = !prefs.flag(id, key);
        prefs.set(id, key, next);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.4f, next ? 1.5f : 0.8f);
      } else if (view.page == Page.MATCH && NUMBERS.containsKey(slot)) {
        String key = NUMBERS.get(slot);
        int max = key.equals("saturation") ? 20 : 600, min = key.equals("saturation") ? 0 : 1;
        int step = shift ? 5 : 1;
        int delta = right ? -step : step;
        prefs.set(id, key, Math.clamp(prefs.number(id, key) + delta, min, max));
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);
      } else return;
      open(p, view.page);
    } catch (IOException failure) {
      plugin.messages().send(p, "settings.save-error");
    }
  }

  @EventHandler
  public void drag(InventoryDragEvent e) {
    if (e.getView().getTopInventory().getHolder() instanceof View) e.setCancelled(true);
  }

  void closeAll() {
    for (Player p : Bukkit.getOnlinePlayers())
      if (p.getOpenInventory().getTopInventory().getHolder() instanceof View) p.closeInventory();
  }
}
