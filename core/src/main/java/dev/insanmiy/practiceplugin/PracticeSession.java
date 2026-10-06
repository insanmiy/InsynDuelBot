package dev.insanmiy.practiceplugin;

import dev.insanmiy.practiceplugin.bot.*;
import dev.insanmiy.practiceplugin.config.*;
import dev.insanmiy.practiceplugin.config.Difficulty;
import dev.insanmiy.practiceplugin.model.*;
import java.util.*;
import org.bukkit.boss.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

public final class PracticeSession {
  final PracticePlugin plugin;
  final Player owner;
  final Arena arena;
  final Kit kit;
  final Difficulty difficulty;
  final PracticeMode mode;
  final Match match;
  String arenaName = "default";
  boolean endingRound;
  final BotOptions options;
  final int startingSaturation;
  BotPlatform bot;
  UUID botId;
  CombatStats round = new CombatStats(), total = new CombatStats();
  long tick, fightStart, totalFightTicks;
  long lastContactTick = -1;
  int roundsWon, roundsLost, seriesWon, seriesLost;
  boolean internalTeleport, closing;
  final Set<org.bukkit.entity.Projectile> projectiles = new HashSet<>();
  final DamageDiagnostics damageDiagnostics = new DamageDiagnostics();
  org.bukkit.event.entity.EntityDamageEvent finishingBlow;
  private boolean hud;
  private final List<PausedState> pausedStates = new ArrayList<>();
  private final BossBar bar =
      Bukkit.createBossBar("DuelBot", org.bukkit.boss.BarColor.RED, org.bukkit.boss.BarStyle.SOLID);


  PracticeSession(
      PracticePlugin plugin,
      Player owner,
      Arena arena,
      Kit kit,
      Difficulty difficulty,
      PracticeMode mode,
      int bestOf) {
    this.plugin = plugin;
    this.owner = owner;
    this.arena = arena;
    this.kit = kit;
    this.options = plugin.settings().options(owner.getUniqueId());
    Difficulty configured = difficulty.withOptions(this.options);
    this.difficulty = mode == PracticeMode.DUMMY ? configured.passive() : configured;
    startingSaturation = plugin.settings().number(owner.getUniqueId(), "saturation");
    this.mode = mode;
    hud = plugin.settings().flag(owner.getUniqueId(), "hud");
    match =
        new Match(
            plugin.settings().number(owner.getUniqueId(), "countdown") * 20,
            plugin.settings().number(owner.getUniqueId(), "between-rounds") * 20,
            mode == PracticeMode.MATCH ? Math.max(3, bestOf % 2 == 0 ? bestOf + 1 : bestOf) : mode == PracticeMode.DUEL ? 1 : 0);
  }

  void begin() {
    internalTeleport = true;
    try {
      bot =
          BotAdapters.getAdapter().createBot(
              owner,
              arena.botSpawn(),
              plugin.botName(),
              id -> {
                botId = id;
                plugin.registerBot(id);
              });
      bot.configure(options, kit.regeneration());
      resetRound();
    } finally {
      internalTeleport = false;
    }
    if (hud) bar.addPlayer(owner);
  }

  void replaceDeadBot() {
    if (bot != null) bot.close();
    plugin.unregisterBot(botId);
    bot = BotAdapters.getAdapter().createBot(owner, arena.botSpawn(), plugin.botName(), id -> {
      botId = id;
      plugin.registerBot(id);
    });
    bot.configure(options, kit.regeneration());
  }

  public boolean participant(Player p) {
    return p.getUniqueId().equals(owner.getUniqueId())
        || bot != null && p.getUniqueId().equals(bot.player().getUniqueId());
  }

  public boolean fighting() {
    return !closing && !match.paused() && match.phase() == Match.Phase.FIGHT;
  }

  void captureLayout() {
    if (plugin == null || plugin.layouts() == null || !owner.isOnline()) return;
    KitLayout current =
        plugin.layouts().getLayout(owner.getUniqueId(), difficulty.name(), kit.name());
    KitLayout captured = plugin.layouts().capture(owner, kit, plugin.slotKey(), current);
    if (captured != null && !captured.slots().isEmpty()) {
      plugin.layouts().saveLayout(owner.getUniqueId(), difficulty.name(), kit.name(), captured);
    }
  }

  void resetRound() {
    captureLayout();
    plugin.clearUtilities(this);
    internalTeleport = true;
    try {
      if (arena.error() != null) throw new IllegalStateException(arena.error());
      if (!owner.teleport(arena.playerSpawn()))
        throw new IllegalStateException("Player teleport cancelled");
      bot.reset(arena.botSpawn());

      for (Attribute attribute :
          new Attribute[] {
            Attributes.ATTACK_DAMAGE,
            Attributes.ATTACK_SPEED,
            Attributes.ARMOR,
            Attributes.ARMOR_TOUGHNESS,
            Attributes.KNOCKBACK_RESISTANCE,
            Attributes.MOVEMENT_SPEED,
            Attributes.ATTACK_KNOCKBACK
          }) {
        if (attribute == null) continue;
        var source = owner.getAttribute(attribute);
        var target = bot.player().getAttribute(attribute);
        if (source != null && target != null) target.setBaseValue(source.getBaseValue());
      }
      KitLayout ownerLayout =
          plugin.layouts().getLayout(owner.getUniqueId(), difficulty.name(), kit.name());
      for (Player p : new Player[] {owner, bot.player()}) {
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        AttributeInstance maxHealthAttr = Attributes.get(p, Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
          maxHealthAttr.setBaseValue(kit.health());
        }
        kit.equip(
            p,
            plugin.gearKey(),
            plugin.slotKey(),
            p.equals(owner) ? ownerLayout : null);
        double currentMaxHealth = Attributes.getValue(p, Attributes.MAX_HEALTH, kit.health());
        if (Math.abs(currentMaxHealth - kit.health()) > .001)
          throw new IllegalStateException("An external health modifier prevents equal kit health");
        p.setHealth(kit.health());
        p.setAbsorptionAmount(0);
        p.setFoodLevel(20);
        p.setSaturation(startingSaturation);
        p.setExhaustion(0);
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.setVelocity(new Vector());
        p.setNoDamageTicks(0);
        p.setCooldown(Material.SHIELD, 0);
        p.setCooldown(Material.ENDER_PEARL, 0);
        p.setLevel(0);
        p.setExp(0);
        p.setTotalExperience(0);
        BotAdapters.getAdapter().prepareRound(p);
      }

      round = new CombatStats();
    } finally {
      internalTeleport = false;
    }
  }

  void tick() {
    if (closing) return;
    if (!owner.isOnline() || bot == null || !bot.player().isOnline()) {
      plugin.stop(this, "A fighter disconnected; your state has been saved for recovery.");
      return;
    }
    if (owner.isDead() || bot.player().isDead()) return;

    if (!owner.getWorld().equals(bot.player().getWorld())) {
      Location destination = nearbySafe(owner.getLocation());
      if (destination != null) relocateBot(destination);
      else return;
    }
    if (owner.getLocation().getY() < owner.getWorld().getMinHeight() + 2
        || bot.player().getLocation().getY() < bot.player().getWorld().getMinHeight() + 2)
      resetRound();
    if (match.paused()) {
      bot.tick(owner, difficulty, arena, false);
      return;
    }
    tick++;
    if (fighting()) totalFightTicks++;
    bot.tick(owner, difficulty, arena, fighting());
    if (closing) return;
    if (match.tick()) {
      if (match.phase() == Match.Phase.COUNTDOWN) resetRound();
      else if (match.phase() == Match.Phase.FIGHT) {
        fightStart = tick;
        owner.sendMessage(TextUI.legacy(Component.text("Fight! /practice stop to finish.")));
      }
    }

    if (tick % 5 == 0 && hud) {
      bar.setTitle(TextUI.legacy(
          Component.text(
              "Bot "
                  + Math.round(bot.player().getHealth())
                  + "/"
                  + kit.health()
                  + " HP | apples "
                  + countApples(bot.player()))));
      bar.setProgress((float) Math.max(0, Math.min(1, bot.player().getHealth() / kit.health())));
      TextUI.actionBar(owner,
          Component.text(
              mode.id()
                  + " | Round "
                  + match.round()
                  + " | You "
                  + match.playerWins()
                  + " : "
                  + match.botWins()
                  + " Bot | "
                  + difficulty.prettyDisplayName()
                  + " | "
                  + (match.phase() == Match.Phase.FINISHED
                      ? "Finished"
                      : fighting()
                          ? "Combo " + round.combo(tick)
                          : "Ready in " + ((match.remaining() + 19) / 20))
                  + " | Sat "
                  + String.format(java.util.Locale.ROOT, "%.1f", owner.getSaturation())));
    }
  }



  static int countApples(Player player) {
    return Arrays.stream(player.getInventory().getContents())
        .filter(i -> i != null && i.getType() == Material.GOLDEN_APPLE)
        .mapToInt(org.bukkit.inventory.ItemStack::getAmount)
        .sum();
  }

  private static Location nearbySafe(Location center) {
    for (int radius = 3; radius <= 6; radius++)
      for (int x = -radius; x <= radius; x++)
        for (int z = -radius; z <= radius; z++) {
          if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
          for (int y = 1; y >= -2; y--) {
            Location candidate = center.clone().add(x, y, z);
            if (Arena.safe(candidate)) return candidate;
          }
        }
    return null;
  }

  void followTeleport(Location destination) {
    if (bot == null || closing || internalTeleport) return;
    if (bot.player().getWorld().equals(destination.getWorld())
        && bot.player().getLocation().distanceSquared(destination) < 32 * 32) return;
    Location safe = nearbySafe(destination);
    if (safe != null) relocateBot(safe);
    else
      owner.sendMessage(TextUI.legacy(
          Component.text("Practice is still active. The bot is waiting for safe ground nearby.")));
  }

  private void relocateBot(Location destination) {
    internalTeleport = true;
    try {
      double health = bot.player().getHealth();
      bot.relocate(destination);
      double maxAllowedHealth = Attributes.getValue(bot.player(), Attributes.MAX_HEALTH, 20.0);
      bot.player().setHealth(Math.min(health, maxAllowedHealth));
    } finally {
      internalTeleport = false;
    }
  }

  void setPaused(boolean paused) {
    if (match.paused() == paused)
      throw new IllegalArgumentException(paused ? "Already paused." : "Not paused.");
    if (paused) {
      plugin.clearUtilities(this);
      for (Player p : new Player[] {owner, bot.player()}) {
        BotAdapters.getAdapter().stopUsing(p);
        p.setVelocity(new Vector());
        pausedStates.add(
            new PausedState(
                p,
                List.copyOf(p.getActivePotionEffects()),
                p.getAbsorptionAmount(),
                p.getFoodLevel(),
                p.getSaturation(),
                p.getExhaustion()));
      }
    } else {
      for (PausedState state : pausedStates) state.restore();
      pausedStates.clear();
    }
    match.setPaused(paused);
    TextUI.actionBar(owner,
        Component.text(paused ? "Practice paused. /practice resume" : "Practice resumed."));
  }

  private record PausedState(
      Player player,
      List<PotionEffect> effects,
      double absorption,
      int food,
      float saturation,
      float exhaustion) {
    void restore() {
      player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
      player.addPotionEffects(effects);
      player.setAbsorptionAmount(absorption);
      player.setFoodLevel(food);
      player.setSaturation(saturation);
      player.setExhaustion(exhaustion);
    }
  }

  void toggleHud() {
    hud = !hud;
    if (hud) bar.addPlayer(owner);
    else {
      bar.removePlayer(owner);
      TextUI.actionBar(owner, Component.empty());
    }
    owner.sendMessage(TextUI.legacy(Component.text("Practice HUD " + (hud ? "enabled." : "hidden."))));
  }

  void win(boolean player) {
    if (!match.win(player)) return;
    if (player) roundsWon++;
    else roundsLost++;
    owner.sendMessage(TextUI.legacy(
        Component.text(
            (player ? "You won" : "Bot won")
                + " round "
                + match.round()
                + ". "
                + round.summary(tick - fightStart))));
    if (match.phase() == Match.Phase.FINISHED) {
      boolean playerWonSeries = match.playerWins() > match.botWins();
      if (playerWonSeries) seriesWon++;
      else seriesLost++;

      if (mode == PracticeMode.DUEL) {
        String result = playerWonSeries ? "You won the duel!" : "Bot won the duel!";
        NamedTextColor titleColor = playerWonSeries ? NamedTextColor.GREEN : NamedTextColor.RED;
        String titleText = playerWonSeries ? "VICTORY!" : "DEFEAT!";

        owner.sendMessage(TextUI.legacy(Component.text("Duel finished! " + result)));
        owner.showTitle(Title.title(
            Component.text(titleText, titleColor),
            Component.text(result, NamedTextColor.GRAY)));

        Sound sound = playerWonSeries ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_WITHER_DEATH;
        owner.playSound(owner.getLocation(), sound, 0.8f, 1.0f);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
          plugin.stop(this, result);
        }, 40L);
        return;
      }
      if (mode == PracticeMode.MATCH) {
        String score = match.playerWins() + "-" + match.botWins();
        String result = (playerWonSeries ? "You won the match " : "Bot won the match ") + score + "!";
        NamedTextColor titleColor = playerWonSeries ? NamedTextColor.GREEN : NamedTextColor.RED;
        String titleText = playerWonSeries ? "MATCH VICTORY!" : "MATCH DEFEAT!";

        owner.sendMessage(TextUI.legacy(Component.text("Match finished! " + result)));
        owner.showTitle(Title.title(
            Component.text(titleText, titleColor),
            Component.text("Final score: " + score, NamedTextColor.GOLD)));

        Sound sound = playerWonSeries ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_WITHER_DEATH;
        owner.playSound(owner.getLocation(), sound, 0.8f, 1.0f);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
          plugin.stop(this, result);
        }, 40L);
        return;
      }

      String result = "Series finished: " + match.playerWins() + "-" + match.botWins() + ".";
      owner.sendMessage(TextUI.legacy(Component.text(result)));
      plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
        plugin.stop(this, result);
      }, 40L);
    }
  }

  void close() {
    if (closing) return;
    closing = true;
    captureLayout();
    bar.removePlayer(owner);
    TextUI.actionBar(owner, Component.empty());
    owner.sendMessage(TextUI.legacy(
        Component.text(
            "Session: " + roundsWon + "W/" + roundsLost + "L | " + total.summary(totalFightTicks))));
    try {
      try {
        plugin.clearUtilities(this);
      } finally {
        if (bot != null) bot.close();
      }
    } finally {
      internalTeleport = true;
      try {
        plugin.recovery().restore(owner);
      } finally {
        internalTeleport = false;
        plugin.progress().record(this);
      }
    }
  }
}
