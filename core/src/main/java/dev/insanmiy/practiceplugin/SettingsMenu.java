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
    MATCH("text.settings-menu.match-settings"),
    BOT("text.settings-menu.bot-abilities"),
    ADVANCED("text.settings-menu.advanced-bot-tuning"),
    PRESETS("text.settings-menu.bot-presets");

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
      String[] levelNames) {
    public String displayName() { return Messages.text(displayName); }
    public String description() { return Messages.text(description); }
    public String[] levelNames() {
      return Arrays.stream(levelNames).map(Messages::text).toArray(String[]::new);
    }
  }

  private static final Map<Integer, BotSettingDef> BOT_SETTINGS =
      Map.of(
          10,
          new BotSettingDef(
              "bot-attack-charge",
              Material.DIAMOND_SWORD,
              "text.settings-menu.attack-timing",
              "text.settings-menu.how-long-the-bot-waits-before",
              new String[] {
                "text.settings-menu.full-power-100-vanilla",
                "text.settings-menu.fast-95-charge",
                "text.settings-menu.rapid-90-charge",
                "text.settings-menu.very-rapid-85-charge"
              }),
          11,
          new BotSettingDef(
              "bot-aim",
              Material.BOW,
              "text.settings-menu.aim-accuracy",
              "text.settings-menu.how-precisely-the-bot-aims-at",
              new String[] {
                "text.settings-menu.godlike-0-0-100-accurate",
                "text.settings-menu.master-0-2-error",
                "text.settings-menu.high-1-0-error",
                "text.settings-menu.medium-2-5-error",
                "text.settings-menu.low-5-0-error",
                "text.settings-menu.shaky-10-0-error"
              }),
          12,
          new BotSettingDef(
              "bot-reaction",
              Material.FEATHER,
              "text.settings-menu.reaction-speed",
              "text.settings-menu.how-often-the-bot-chooses-its",
              new String[] {
                "text.settings-menu.instant-1t-50ms",
                "text.settings-menu.very-fast-2t-100ms",
                "text.settings-menu.fast-3t-150ms",
                "text.settings-menu.normal-4t-200ms",
                "text.settings-menu.slow-6t-300ms",
                "text.settings-menu.delayed-10t-500ms"
              }),
          13,
          new BotSettingDef(
              "bot-sprint-reset",
              Material.LEATHER_BOOTS,
              "text.settings-menu.sprint-reset",
              "text.settings-menu.how-often-the-bot-restarts-its",
              new String[] {
                "text.settings-menu.always-100",
                "text.settings-menu.high-80",
                "text.settings-menu.medium-50",
                "text.settings-menu.low-20",
                "text.settings-menu.disabled-0"
              }),
          14,
          new BotSettingDef(
              "bot-shield-chance",
              Material.SHIELD,
              "text.settings-menu.shield-defense",
              "text.settings-menu.how-consistently-the-bot-blocks-incoming",
              new String[] {
                "text.settings-menu.always-100",
                "text.settings-menu.high-85",
                "text.settings-menu.medium-60",
                "text.settings-menu.low-30",
                "text.settings-menu.disabled-0"
              }),
          15,
          new BotSettingDef(
              "bot-strafe-speed",
              Material.SUGAR,
              "text.settings-menu.strafe-movement",
              "text.settings-menu.dodging-and-circle-strafing-movement-speed",
              new String[] {
                "text.settings-menu.max-speed-100",
                "text.settings-menu.fast-80",
                "text.settings-menu.normal-60",
                "text.settings-menu.light-35",
                "text.settings-menu.disabled-0"
              }),
          16,
          new BotSettingDef(
              "bot-heal-threshold",
              Material.SPLASH_POTION,
              "text.settings-menu.healing-threshold",
              "text.settings-menu.health-level-where-the-bot-starts",
              new String[] {
                "text.settings-menu.aggressive-75-hp",
                "text.settings-menu.high-65-hp",
                "text.settings-menu.normal-50-hp",
                "text.settings-menu.clutch-35-hp"
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
    final Map<Integer, String> presetNames = new HashMap<>();
    boolean clickPending;

    View(Player p, Page page) {
      owner = p.getUniqueId();
      this.page = page;
      inventory = TextUI.inventory(this, 54, Messages.component(Messages.text("text.settings-menu.settings") + Messages.text(page.title)));
    }

    @Override
    public @NotNull Inventory getInventory() {
      return inventory;
    }

    public int[] decorativeSlots() {
      return new int[] {
        0, 1, 3, 5, 8,
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

  void openPresets(Player p) {
    open(p, Page.PRESETS);
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
    view.presetNames.clear();

    view.inventory.setItem(2, item(Material.CLOCK,
        Messages.text("text.settings-menu.match-settings") + (page == Page.MATCH ? Messages.text("text.settings-menu.selected-2") : ""),
        Messages.text("text.settings-menu.countdown-food-and-display-options"), Messages.text("text.settings-menu.click-to-open")));
    view.inventory.setItem(4, item(Material.IRON_SWORD,
        Messages.text("text.settings-menu.bot-abilities") + (page == Page.BOT ? Messages.text("text.settings-menu.selected-2") : ""),
        Messages.text("text.settings-menu.choose-which-actions-the-bot-can"), Messages.text("text.settings-menu.click-to-open")));
    view.inventory.setItem(6, item(Material.COMPARATOR,
        Messages.text("text.settings-menu.advanced-tuning") + (page == Page.ADVANCED ? Messages.text("text.settings-menu.selected-2") : ""),
        Messages.text("text.settings-menu.override-individual-difficulty-settings"), Messages.text("text.settings-menu.click-to-open")));
    view.inventory.setItem(7, item(Material.KNOWLEDGE_BOOK,
        plugin.messages().get("settings.preset-tab") + (page == Page.PRESETS ? Messages.text("text.settings-menu.selected-2") : ""),
        plugin.messages().get("settings.preset-tab-description")));

    View targetView = view;
    BOT_SETTINGS.forEach(
        (slot, def) -> {
          if (page != Page.ADVANCED) {
            return;
          }
          int currentLevel = prefs.botSetting(id, def.key());
          String currentDisplay =
              currentLevel < 0 || currentLevel >= def.levelNames().length
                  ? Messages.text("text.settings-menu.default-from-difficulty")
                  : def.levelNames()[currentLevel];
          boolean isCustom = currentLevel >= 0;
          targetView.inventory.setItem(
              slot,
              item(
                  def.icon(),
                  def.displayName(),
                  Messages.text("text.settings-menu.current") + currentDisplay,
                  def.description(),
                  "",
                  isCustom ? Messages.text("text.settings-menu.custom-override-active") : Messages.text("text.settings-menu.using-difficulty-default"),
                  "",
                  Messages.text("text.settings-menu.left-click-next-right-click-previous"),
                  Messages.text("text.settings-menu.shift-click-use-difficulty-default")));
        });

    FLAGS.forEach(
        (slot, key) -> {
          if ((page == Page.MATCH && slot < 31)
              || (page == Page.BOT && slot >= 31)
              || page == Page.ADVANCED || page == Page.PRESETS) {
            return;
          }
          boolean on = prefs.flag(id, key);
          targetView.inventory.setItem(
              slot,
              item(
                  on ? Material.LIME_DYE : Material.GRAY_DYE,
                  title(key) + ": " + (on ? Messages.text("text.settings-menu.on") : Messages.text("text.settings-menu.off")),
                  flagDescription(key),
                  Messages.text("text.settings-menu.click-to-toggle"),
                  Messages.text("text.settings-menu.saved-for-your-sessions")));
        });

    if (page == Page.MATCH) {
      NUMBERS.forEach(
          (slot, key) ->
              targetView.inventory.setItem(
                  slot,
                  item(
                      slot == 28 ? Material.CLOCK : slot == 29 ? Material.COMPASS : Material.COOKED_BEEF,
                      title(key) + ": " + prefs.number(id, key) + (key.equals("saturation") ? "" : Messages.text("text.settings-menu.seconds-suffix")),
                      Messages.text("text.settings-menu.left-click-1-right-click-reduce"),
                      Messages.text("text.settings-menu.shift-click-change-by-5"),
                      key.equals("saturation")
                          ? Messages.text("text.settings-menu.stored-food-energy-for-both-fighters")
                          : Messages.text("text.settings-menu.seconds-applies-next-session"))));
    }

    if (page == Page.ADVANCED) {
      view.inventory.setItem(
          33,
          item(
              Material.REDSTONE,
              Messages.text("text.settings-menu.damage-diagnostics"),
              Messages.text("text.settings-menu.advanced-debug-readout-for-all-active"),
              Messages.text("text.settings-menu.click-to-toggle-practice-damage")));
    }

    if (page == Page.MATCH) {
      view.inventory.setItem(
          22,
          item(
              Material.BOOK,
              Messages.text("text.settings-menu.match-rules"),
              Messages.text("text.settings-menu.choose-your-mode-in-practice-setup"),
              Messages.text("text.settings-menu.win-a-round-by-defeating-the"),
              Messages.text("text.settings-menu.use-practice-stop-to-leave-a")));
    }

    if (page == Page.BOT) {
      view.inventory.setItem(31, item(Material.COMPARATOR, Messages.text("text.settings-menu.advanced-bot-tuning"),
          Messages.text("text.settings-menu.want-more-control-over-aim-or"), Messages.text("text.settings-menu.click-to-open-advanced-tuning")));
    }

    if (page == Page.PRESETS) {
      List<String> names = prefs.presets(id);
      for (int index = 0; index < Math.min(names.size(), PlayerSettings.MAX_PRESETS); index++) {
        int slot = 10 + (index / 7) * 9 + index % 7;
        String name = names.get(index);
        view.presetNames.put(slot, name);
        view.inventory.setItem(slot, item(Material.BOOK, Difficulty.formatName(name),
            plugin.messages().get("settings.preset-load-help"),
            plugin.messages().get("settings.preset-update-help"),
            plugin.messages().get("settings.preset-delete-help")));
      }
      view.inventory.setItem(49, item(Material.WRITABLE_BOOK,
          plugin.messages().get("settings.preset-save-title"),
          plugin.messages().get("settings.preset-save-help"),
          plugin.messages().get("settings.preset-scope")));
    }

    view.inventory.setItem(45, item(Material.ARROW, Messages.text("text.settings-menu.back-to-practice-setup"), Messages.text("text.settings-menu.your-match-selection-is-kept")));
    view.inventory.setItem(53, item(Material.BARRIER, Messages.text("text.settings-menu.close"), Messages.text("text.settings-menu.changes-are-already-saved")));
    if (page == Page.ADVANCED) {
      view.inventory.setItem(
          49,
          item(
              Material.BLAZE_POWDER,
              Messages.text("text.settings-menu.reset-advanced-tuning"),
              Messages.text("text.settings-menu.restores-all-7-combat-settings-above"),
              Messages.text("text.settings-menu.their-default-values-from-difficulty-tier"),
              Messages.text("text.settings-menu.ability-toggles-stay-as-you-set"),
              Messages.text("text.settings-menu.click-to-reset")));
    }

    if (!canReuse) {
      p.openInventory(view.inventory);
    }
  }

  private static String flagDescription(String key) {
    return switch (key) {
      case "healing" -> Messages.text("text.settings-menu.allow-the-bot-to-use-healing");
      case "utilities" -> Messages.text("text.settings-menu.allow-the-bot-to-use-webs");
      case "criticals" -> Messages.text("text.settings-menu.allow-the-bot-to-jump-for");
      case "shields" -> Messages.text("text.settings-menu.allow-the-bot-to-block-with");
      case "strafing" -> Messages.text("text.settings-menu.allow-the-bot-to-move-sideways");
      case "counters" -> Messages.text("text.settings-menu.bot-switches-to-axe-when-you");
      case "hud" -> Messages.text("text.settings-menu.real-time-health-and-round-bossbar");
      case "animations" -> Messages.text("text.settings-menu.pulsing-borders-and-ui-effects");
      default -> Messages.text("text.settings-menu.click-to-toggle");
    };
  }

  private static String title(String text) {
    return switch (text) {
      case "countdown" -> Messages.text("text.settings-menu.starting-countdown");
      case "between-rounds" -> Messages.text("text.settings-menu.break-between-rounds");
      case "saturation" -> Messages.text("text.settings-menu.starting-food-saturation");
      case "hud" -> Messages.text("text.settings-menu.match-health-bar");
      case "animations" -> Messages.text("text.settings-menu.menu-animations");
      case "counters" -> Messages.text("text.settings-menu.shield-counters");
      default -> Messages.displayName(text);
    };
  }

  private static ItemStack item(Material type, String name, String... lore) {
    ItemStack item = new ItemStack(type);
    dev.insanmiy.practiceplugin.ItemMetadata.edit(item,
        meta -> {
          TextUI.name(meta, Messages.component(name, NamedTextColor.AQUA)
              .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
          TextUI.lore(meta,
              Arrays.stream(lore).map(line -> Messages.component(line, NamedTextColor.GRAY)
                  .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)).toList());
          meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
          String selectedSuffix = Messages.text("text.settings-menu.selected-2");
          meta.setEnchantmentGlintOverride(!selectedSuffix.isEmpty() && name.endsWith(selectedSuffix));
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
    if (view.clickPending) return;
    view.clickPending = true;
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              try {
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
              } finally {
                view.clickPending = false;
              }
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
      if (slot == 7) {
        openPresets(p);
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
      if (view.page == Page.PRESETS) {
        if (slot == 49) {
          p.closeInventory();
          plugin.messages().send(p, "settings.preset-usage");
          return;
        }
        String name = view.presetNames.get(slot);
        if (name == null || !prefs.presets(id).contains(name)) return;
        if (right && shift) {
          prefs.deletePreset(id, name);
          plugin.messages().send(p, "settings.preset-deleted", "%name%", name);
        }
        else if (shift) {
          prefs.savePreset(id, name);
          plugin.messages().send(p, "settings.preset-saved", "%name%", name);
        }
        else if (!right) {
          prefs.loadPreset(id, name);
          plugin.messages().send(p, "settings.preset-loaded", "%name%", name);
        }
        else return;
        openPresets(p);
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
    } catch (IllegalArgumentException failure) {
      plugin.messages().send(p, "settings.preset-error", "%error%", failure.getMessage());
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
