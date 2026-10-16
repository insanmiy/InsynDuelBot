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
              "Attack Speed (CPS)",
              "Attack Cooldown Charge Threshold Before Striking",
              new String[] {
                "Full Power (100% / Vanilla)",
                "Fast (95% Charge)",
                "Rapid (90% Charge / High CPS)",
                "Furious (85% Charge / Max CPS)"
              }),
          11,
          new BotSettingDef(
              "bot-aim",
              Material.BOW,
              "Aim Accuracy",
              "Crosshair Tracking Precision And Aim Spread",
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
              "Decision Tick Interval And Perception Reaction Time",
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
              "Sprint-Reset (W-Tap)",
              "Frequency Of Sprint-Resetting After Landing Hits",
              new String[] {
                "Always (100% Combos)",
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
                "Always (100% Perfect Blocks)",
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
              "Heal Urgency",
              "Health Threshold When The Bot Uses Potions/Food",
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
    final Inventory inventory;

    View(Player p) {
      owner = p.getUniqueId();
      inventory = TextUI.inventory(this, 54, Component.text("INSYN / BOT & MATCH SETTINGS"));
    }

    @Override
    public @NotNull Inventory getInventory() {
      return inventory;
    }

    public int[] decorativeSlots() {
      return new int[] {
        0, 1, 2, 3, 5, 6, 7, 8,
        9, 17,
        18, 25, 26,
        27, 35,
        36, 37, 38, 39, 40, 41, 42, 43, 44,
        46, 47, 48, 50, 51, 52
      };
    }

    public int pulseSlot() {
      return 4;
    }
  }

  SettingsMenu(PracticePlugin plugin) {
    this.plugin = plugin;
  }

  void open(Player p) {
    if (plugin.sessionFor(p) != null)
      throw new IllegalArgumentException("Use /practice Stop Before Changing Match Settings.");

    boolean canReuse = false;
    View view;
    if (p.getOpenInventory().getTopInventory().getHolder() instanceof View currentView
        && currentView.owner.equals(p.getUniqueId())) {
      canReuse = true;
      view = currentView;
    } else {
      view = new View(p);
    }

    for (int i = 0; i < 54; i++)
      view.inventory.setItem(i, item(Material.GRAY_STAINED_GLASS_PANE, " "));
    PlayerSettings prefs = plugin.settings();
    UUID id = p.getUniqueId();

    view.inventory.setItem(
        4,
        item(
            Material.NETHER_STAR,
            "INSYN PRACTICE SETTINGS",
            "Configure Bot Combat Behavior, Reaction Times,",
            "Movement, Tactics, And Match Rules In-Game.",
            "No Config.yml Editing Required!"));

    View targetView = view;
    BOT_SETTINGS.forEach(
        (slot, def) -> {
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
                  def.displayName() + ": " + currentDisplay,
                  def.description(),
                  "",
                  isCustom ? "[Custom Override Active]" : "[Using Difficulty Default]",
                  "",
                  "Left-Click: Next | Right-Click: Previous"));
        });

    FLAGS.forEach(
        (slot, key) -> {
          boolean on = prefs.flag(id, key);
          targetView.inventory.setItem(
              slot,
              item(
                  on ? Material.LIME_DYE : Material.GRAY_DYE,
                  title(key) + ": " + (on ? "ON" : "OFF"),
                  flagDescription(key),
                  "Click To Toggle",
                  "Saved For Your Sessions"));
        });

    NUMBERS.forEach(
        (slot, key) ->
            targetView.inventory.setItem(
                slot,
                item(
                    slot == 28 ? Material.CLOCK : slot == 29 ? Material.COMPASS : Material.COOKED_BEEF,
                    title(key) + ": " + prefs.number(id, key) + (key.equals("saturation") ? "" : "s"),
                    "Left: Increase | Right: Decrease (Shift: ±5)",
                    key.equals("saturation")
                        ? "Starting Saturation For Both Fighters"
                        : "Seconds; Applies Next Session")));

    view.inventory.setItem(
        33,
        item(
            Material.REDSTONE,
            "Damage Diagnostics",
            "Detailed Hit And Knockback Diagnostics In Chat",
            "Click To Toggle (/practice damage)"));

    view.inventory.setItem(
        34,
        item(
            Material.BOOK,
            "Match Rules",
            "Mode And Best-Of: Choose In Practice Menu",
            "Native Damage And Cooldowns Stay 1:1",
            "Movement Never Ends A Round",
            "Only Lethal Hits Score; /practice Stop Exits"));

    view.inventory.setItem(45, item(Material.ARROW, "Back To Practice Menu", "Click To Return To Kit Selection"));
    view.inventory.setItem(
        49,
        item(
            Material.BLAZE_POWDER,
            "Reset Bot Tuning to Defaults",
            "Restores All 7 Combat Settings Above To",
            "Their Default Values From Difficulty Tier.",
            "Click To Reset"));
    view.inventory.setItem(53, item(Material.BARRIER, "Close"));

    if (!canReuse) {
      p.openInventory(view.inventory);
    }
  }

  private static String flagDescription(String key) {
    return switch (key) {
      case "healing" -> "Bot Potion And Food Consumption";
      case "utilities" -> "Offensive Cobwebs, Ender Pearls And Bows";
      case "criticals" -> "Bot Jump-Resets To Land Critical Hits";
      case "shields" -> "Bot Off-Hand Shield Usage";
      case "strafing" -> "Circling And Dodging Movement";
      case "counters" -> "Bot Switches To Axe When You Block With Shield";
      case "hud" -> "Real-Time Health And Round BossBar";
      case "animations" -> "Pulsing Borders And UI Effects";
      default -> "Click To Toggle";
    };
  }

  private static String title(String text) {
    return Difficulty.formatName(text);
  }

  private static ItemStack item(Material type, String name, String... lore) {
    ItemStack item = new ItemStack(type);
    dev.insanmiy.practiceplugin.ItemMetadata.edit(item,
        meta -> {
          TextUI.name(meta, Component.text(name, NamedTextColor.AQUA));
          TextUI.lore(meta,
              Arrays.stream(lore).map(line -> Component.text(line, NamedTextColor.GRAY)).toList());
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
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (!p.isOnline() || p.getOpenInventory().getTopInventory().getHolder() != view)
                return;
              if (plugin.sessionFor(p) != null || !p.hasPermission("practice.use")) {
                p.closeInventory();
                return;
              }
              apply(p, slot, right, shift);
            });
  }

  private void apply(Player p, int slot, boolean right, boolean shift) {
    PlayerSettings prefs = plugin.settings();
    UUID id = p.getUniqueId();
    try {
      if (slot == 53) {
        p.closeInventory();
        return;
      }
      if (slot == 45) {
        p.closeInventory();
        p.performCommand("practice menu");
        return;
      }
      if (slot == 49) {
        prefs.resetBotSettings(id);
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.4f, 1.2f);
        p.sendMessage(TextUI.legacy(Component.text("Reset Bot Combat Settings To Difficulty Defaults.")));
        open(p);
        return;
      }
      if (slot == 33) {
        p.performCommand("practice damage");
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);
        return;
      }
      if (BOT_SETTINGS.containsKey(slot)) {
        BotSettingDef def = BOT_SETTINGS.get(slot);
        int total = def.levelNames().length;
        int current = prefs.botSetting(id, def.key());
        int next;
        if (right) {
          if (current <= -1) next = total - 1;
          else next = current - 1;
        } else {
          if (current >= total - 1) next = -1;
          else next = current + 1;
        }
        prefs.set(id, def.key(), next);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
      } else if (FLAGS.containsKey(slot)) {
        String key = FLAGS.get(slot);
        boolean next = !prefs.flag(id, key);
        prefs.set(id, key, next);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.4f, next ? 1.5f : 0.8f);
      } else if (NUMBERS.containsKey(slot)) {
        String key = NUMBERS.get(slot);
        int max = key.equals("saturation") ? 20 : 60, min = key.equals("saturation") ? 0 : 1;
        int step = shift ? 5 : 1;
        int delta = right ? -step : step;
        prefs.set(id, key, Math.clamp(prefs.number(id, key) + delta, min, max));
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);
      } else return;
      open(p);
    } catch (IOException failure) {
      p.sendMessage(TextUI.legacy(Component.text("Settings Could Not Be Saved; Previous Value Retained.")));
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
