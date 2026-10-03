package dev.insanmiy.practiceplugin;

import java.io.*;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;

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
              "Attack cooldown charge threshold before striking",
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
              "Crosshair tracking precision and aim spread",
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
              "Decision tick interval and perception reaction time",
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
              "Frequency of sprint-resetting after landing hits",
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
              "How consistently the bot blocks incoming attacks",
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
              "Dodging and circle-strafing movement speed",
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
              "Health threshold when the bot uses potions/food",
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

    public Inventory getInventory() {
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
      throw new IllegalArgumentException("Use /practice stop before changing match settings.");
    View view = new View(p);
    for (int i = 0; i < 54; i++)
      view.inventory.setItem(i, item(Material.GRAY_STAINED_GLASS_PANE, " "));
    PlayerSettings prefs = plugin.settings();
    UUID id = p.getUniqueId();

    view.inventory.setItem(
        4,
        item(
            Material.NETHER_STAR,
            "INSYN PRACTICE SETTINGS",
            "Configure bot combat behavior, reaction times,",
            "movement, tactics, and match rules in-game.",
            "No config.yml editing required!"));

    BOT_SETTINGS.forEach(
        (slot, def) -> {
          int currentLevel = prefs.botSetting(id, def.key());
          String currentDisplay =
              currentLevel < 0 || currentLevel >= def.levelNames().length
                  ? "Default (From Difficulty)"
                  : def.levelNames()[currentLevel];
          boolean isCustom = currentLevel >= 0;
          view.inventory.setItem(
              slot,
              item(
                  def.icon(),
                  def.displayName() + ": " + currentDisplay,
                  def.description(),
                  "",
                  isCustom ? "[Custom Override Active]" : "[Using Difficulty Default]",
                  "",
                  "Left-click: Next | Right-click: Previous"));
        });

    FLAGS.forEach(
        (slot, key) -> {
          boolean on = prefs.flag(id, key);
          view.inventory.setItem(
              slot,
              item(
                  on ? Material.LIME_DYE : Material.GRAY_DYE,
                  title(key) + ": " + (on ? "ON" : "OFF"),
                  flagDescription(key),
                  "Click to toggle",
                  "Saved for your sessions"));
        });

    NUMBERS.forEach(
        (slot, key) ->
            view.inventory.setItem(
                slot,
                item(
                    slot == 28 ? Material.CLOCK : slot == 29 ? Material.COMPASS : Material.COOKED_BEEF,
                    title(key) + ": " + prefs.number(id, key) + (key.equals("saturation") ? "" : "s"),
                    "Left: increase | Right: decrease",
                    key.equals("saturation")
                        ? "Starting saturation for BOTH fighters"
                        : "Seconds; applies next session")));

    view.inventory.setItem(
        33,
        item(
            Material.REDSTONE,
            "Damage Diagnostics",
            "Detailed hit and knockback diagnostics in chat",
            "Click to toggle (/practice damage)"));

    view.inventory.setItem(
        34,
        item(
            Material.BOOK,
            "Match rules",
            "Mode and best-of: choose in practice menu",
            "Native damage and cooldowns stay 1:1",
            "Movement never ends a round",
            "Only lethal hits score; /practice stop exits"));

    view.inventory.setItem(45, item(Material.ARROW, "Back to practice menu", "Click to return to kit selection"));
    view.inventory.setItem(
        49,
        item(
            Material.BLAZE_POWDER,
            "Reset Bot Tuning to Defaults",
            "Restores all 7 combat settings above to",
            "their default values from difficulty tier.",
            "Click to reset"));
    view.inventory.setItem(53, item(Material.BARRIER, "Close"));
    p.openInventory(view.inventory);
  }

  private static String flagDescription(String key) {
    return switch (key) {
      case "healing" -> "Bot potion and food consumption";
      case "utilities" -> "Offensive cobwebs, ender pearls and bows";
      case "criticals" -> "Bot jump-resets to land critical hits";
      case "shields" -> "Bot off-hand shield usage";
      case "strafing" -> "Circling and dodging movement";
      case "counters" -> "Bot switches to axe when you block with shield";
      case "hud" -> "Real-time health and round BossBar";
      case "animations" -> "Pulsing borders and UI effects";
      default -> "Click to toggle";
    };
  }

  private static String title(String text) {
    return text.replace('_', ' ').replace('-', ' ');
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
              apply(p, slot, right);
            });
  }

  private void apply(Player p, int slot, boolean right) {
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
        p.sendMessage(TextUI.legacy(Component.text("Reset bot combat settings to difficulty defaults.")));
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
        prefs.set(id, key, !prefs.flag(id, key));
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);
      } else if (NUMBERS.containsKey(slot)) {
        String key = NUMBERS.get(slot);
        int max = key.equals("saturation") ? 20 : 60, min = key.equals("saturation") ? 0 : 1;
        prefs.set(id, key, Math.max(min, Math.min(max, prefs.number(id, key) + (right ? -1 : 1))));
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);
      } else return;
      open(p);
    } catch (IOException failure) {
      p.sendMessage(TextUI.legacy(Component.text("Settings could not be saved; previous value retained.")));
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
