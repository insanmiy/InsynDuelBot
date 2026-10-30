package dev.insanmiy.practiceplugin;

import dev.insanmiy.practiceplugin.bot.*;
import dev.insanmiy.practiceplugin.config.*;
import dev.insanmiy.practiceplugin.config.Difficulty;
import dev.insanmiy.practiceplugin.model.*;
import java.util.*;
import org.bukkit.boss.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
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
  final int roundTimeoutSeconds;
  String arenaName = "default";
  boolean endingRound;
  final BotOptions options;
  final int startingSaturation;
  BotPlatform bot;
  UUID botId;
  final List<BotPlatform> bots = new ArrayList<>();
  final Set<UUID> botIds = new HashSet<>();
  final List<BotPlatform> aliveBots = new ArrayList<>();
  CombatStats round = new CombatStats(), total = new CombatStats();
  long tick, fightStart, totalFightTicks;
  long lastContactTick = -1;
  int roundsWon, roundsLost, seriesWon, seriesLost;
  boolean internalTeleport, closing;
  final Set<org.bukkit.entity.Projectile> projectiles = new HashSet<>();
  final DamageDiagnostics damageDiagnostics = new DamageDiagnostics();
  org.bukkit.event.entity.EntityDamageEvent finishingBlow;
  private boolean hud;
  private final List<AttributeModifier> strippedModifiers = new ArrayList<>();
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
    this.difficulty = difficulty.withOptions(this.options);
    startingSaturation = plugin.settings().number(owner.getUniqueId(), "saturation");
    this.mode = mode;
    hud = plugin.settings().flag(owner.getUniqueId(), "hud");
    roundTimeoutSeconds = plugin.getConfig().getInt("session.round-timeout-seconds", 0);
    match =
        new Match(
            plugin.settings().number(owner.getUniqueId(), "countdown") * 20,
            plugin.settings().number(owner.getUniqueId(), "between-rounds") * 20,
            mode == PracticeMode.MATCH || mode.isOutnumbered()
                ? Math.max(3, bestOf % 2 == 0 ? bestOf + 1 : bestOf)
                : mode == PracticeMode.DUEL ? 1 : 0);
  }

  void begin() {
    internalTeleport = true;
    try {
      stripModifiers(owner, true);
      spawnAllBots();
      resetRound();
    } finally {
      internalTeleport = false;
    }
    if (hud) bar.addPlayer(owner);
  }

  Location spawnForBot(int index, int total) {
    Location base = arena.botSpawn().clone();
    if (total <= 1) return base;
    Location playerSp = arena.playerSpawn();
    double dx = playerSp.getX() - base.getX();
    double dz = playerSp.getZ() - base.getZ();
    Vector perp = new Vector(-dz, 0, dx);
    if (perp.lengthSquared() < 0.001) perp = new Vector(1, 0, 0);
    else perp.normalize();

    double spacing = 1.6;
    double multiplier = (index - (total - 1) / 2.0) * spacing;
    Location candidate = base.clone().add(perp.clone().multiply(multiplier));
    candidate.setYaw(base.getYaw());
    candidate.setPitch(base.getPitch());
    if (Arena.safe(candidate)) return candidate;
    return base;
  }

  private void updatePrimaryBot() {
    if (!aliveBots.isEmpty()) {
      bot = aliveBots.get(0);
      botId = bot.player() != null ? bot.player().getUniqueId() : null;
    } else if (!bots.isEmpty()) {
      bot = bots.get(0);
      botId = bot.player() != null ? bot.player().getUniqueId() : null;
    } else {
      bot = null;
      botId = null;
    }
  }

  void spawnAllBots() {
    clearBots();
    int count = mode.botCount();
    for (int i = 0; i < count; i++) {
      Location sp = spawnForBot(i, count);
      BotPlatform created =
          BotAdapters.getAdapter().createBot(
              owner,
              sp,
              plugin.botName(),
              id -> {
                botIds.add(id);
                plugin.registerBot(id);
              });
      created.configure(options, kit.regeneration());
      bots.add(created);
      aliveBots.add(created);
    }
    updatePrimaryBot();
  }

  void clearBots() {
    for (BotPlatform b : bots) {
      if (b != null) {
        try {
          b.close();
        } catch (Exception ignored) {}
      }
    }
    for (UUID id : botIds) {
      plugin.unregisterBot(id);
    }
    bots.clear();
    aliveBots.clear();
    botIds.clear();
    bot = null;
    botId = null;
  }

  void ensureBots(int targetCount) {
    Iterator<BotPlatform> it = bots.iterator();
    while (it.hasNext()) {
      BotPlatform b = it.next();
      if (b == null || b.player() == null || !b.player().isOnline()) {
        if (b != null) {
          try {
            b.close();
          } catch (Exception ignored) {}
          if (b.player() != null) {
            botIds.remove(b.player().getUniqueId());
            plugin.unregisterBot(b.player().getUniqueId());
          }
        }
        it.remove();
      }
    }

    while (bots.size() < targetCount) {
      int index = bots.size();
      Location sp = spawnForBot(index, targetCount);
      BotPlatform created =
          BotAdapters.getAdapter().createBot(
              owner,
              sp,
              plugin.botName(),
              id -> {
                botIds.add(id);
                plugin.registerBot(id);
              });
      created.configure(options, kit.regeneration());
      bots.add(created);
    }
    updatePrimaryBot();
  }

  void replaceDeadBot() {
    clearBots();
    spawnAllBots();
  }

  public int aliveBotCount() {
    return (int) aliveBots.stream()
        .filter(b -> b != null && b.player() != null && b.player().isOnline() && !b.player().isDead())
        .count();
  }

  public void eliminateBot(BotPlatform deadBot) {
    if (deadBot != null) {
      aliveBots.remove(deadBot);
      updatePrimaryBot();
    }
  }

  public void removeEliminatedBot(BotPlatform deadBot) {
    if (deadBot != null) {
      aliveBots.remove(deadBot);
      bots.remove(deadBot);
      if (deadBot.player() != null) {
        botIds.remove(deadBot.player().getUniqueId());
        plugin.unregisterBot(deadBot.player().getUniqueId());
      }
      try {
        deadBot.close();
      } catch (Exception ignored) {}
      updatePrimaryBot();
    }
  }

  public boolean isBotPlayer(Entity entity) {
    if (!(entity instanceof Player p)) return false;
    return botIds.contains(p.getUniqueId())
        || bots.stream().anyMatch(b -> b != null && b.player() != null && b.player().getUniqueId().equals(p.getUniqueId()));
  }

  public BotPlatform findBot(Player p) {
    if (p == null) return null;
    for (BotPlatform b : bots) {
      if (b != null && b.player() != null && b.player().getUniqueId().equals(p.getUniqueId())) {
        return b;
      }
    }
    return null;
  }

  public boolean participant(Player p) {
    return p != null && (p.getUniqueId().equals(owner.getUniqueId()) || isBotPlayer(p));
  }

  public boolean fighting() {
    return !closing && !match.paused() && match.phase() == Match.Phase.FIGHT;
  }

  void captureLayout() {
    if (plugin == null || plugin.layouts() == null || !owner.isOnline()) return;
    KitLayout current =
        plugin.layouts().getLayout(owner.getUniqueId(), difficulty.name(), kit.name());
    KitLayout captured = plugin.layouts().capture(owner, kit, plugin.slotKey(), current);
    if (!captured.slots().isEmpty()) {
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
        throw new IllegalStateException("Player Teleport Cancelled");

      int count = mode.botCount();
      ensureBots(count);
      aliveBots.clear();
      aliveBots.addAll(bots);

      for (int i = 0; i < bots.size(); i++) {
        BotPlatform b = bots.get(i);
        b.reset(spawnForBot(i, bots.size()));
      }

      List<Player> participants = new ArrayList<>();
      participants.add(owner);
      for (BotPlatform b : bots) {
        if (b != null && b.player() != null) {
          participants.add(b.player());
        }
      }

      KitLayout ownerLayout =
          plugin.layouts().getLayout(owner.getUniqueId(), difficulty.name(), kit.name());
      for (Player p : participants) {
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
          var target = p.getAttribute(attribute);
          if (source != null && target != null && !p.equals(owner)) target.setBaseValue(source.getBaseValue());
        }
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        stripModifiers(p, false);
        AttributeInstance maxHealthAttr = Attributes.get(p, Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
          maxHealthAttr.setBaseValue(kit.health());
        }
        kit.equip(
            p,
            plugin.gearKey(),
            plugin.slotKey(),
            p.equals(owner) ? ownerLayout : null);
        stripModifiers(p, false);
        double currentMaxHealth = Attributes.getValue(p, Attributes.MAX_HEALTH, kit.health());
        if (Math.abs(currentMaxHealth - kit.health()) > .001)
          throw new IllegalStateException("An External Health Modifier Prevents Equal Kit Health");
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
    if (!owner.isOnline() || bots.isEmpty() || bots.stream().noneMatch(b -> b.player() != null && b.player().isOnline())) {
      plugin.stop(this, plugin.messages().get("session.fighter-disconnected"));
      return;
    }
    if (owner.isDead()) return;

    boolean ownerFell = owner.getLocation().getY() < owner.getWorld().getMinHeight() + 2;
    if (ownerFell) {
      if (fighting() && !endingRound) win(false);
      resetRound();
      return;
    }

    for (BotPlatform b : new ArrayList<>(aliveBots)) {
      if (b == null || b.player() == null || !b.player().isOnline()) continue;
      if (b.player().isDead()) continue;

      if (!owner.getWorld().equals(b.player().getWorld())) {
        Location destination = nearbySafe(owner.getLocation());
        if (destination != null) relocateBot(b, destination);
        else continue;
      }

      boolean botFell = b.player().getLocation().getY() < b.player().getWorld().getMinHeight() + 2;
      if (botFell) {
        eliminateBot(b);
        if (aliveBotCount() <= 0) {
          if (fighting() && !endingRound) win(true);
          resetRound();
          return;
        } else {
          plugin.messages().send(owner, "combat.bot-eliminated", "%remaining%", String.valueOf(aliveBotCount()));
          removeEliminatedBot(b);
        }
        continue;
      }

      if (match.paused()) {
        b.tick(owner, difficulty, arena, false);
      } else {
        b.tick(owner, difficulty, arena, fighting());
      }
    }

    if (closing) return;
    if (roundTimeoutSeconds > 0 && fighting() && tick - fightStart >= roundTimeoutSeconds * 20L) {
      plugin.messages().send(owner, "combat.round-timed-out");
      fightStart = tick;
      resetRound();
      return;
    }

    tick++;
    if (fighting()) totalFightTicks++;

    if (match.phase() == Match.Phase.COUNTDOWN) {
      int remaining = match.remaining();
      if (remaining > 0 && remaining % 20 == 0) {
        int seconds = remaining / 20;
        if (seconds <= 5) {
          NamedTextColor color =
              seconds <= 1
                  ? NamedTextColor.RED
                  : (seconds == 2 ? NamedTextColor.GOLD : NamedTextColor.YELLOW);
          owner.showTitle(
              Title.title(
                  Component.text(seconds, color, TextDecoration.BOLD),
                  Component.text("Round " + match.round(), NamedTextColor.GRAY),
                  Title.Times.times(
                      java.time.Duration.ZERO,
                      java.time.Duration.ofMillis(750),
                      java.time.Duration.ofMillis(250))));
          owner.playSound(owner.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.0f);
        }
      }
    }

    if (match.tick()) {
      if (match.phase() == Match.Phase.COUNTDOWN) resetRound();
      else if (match.phase() == Match.Phase.FIGHT) {
        fightStart = tick;
        owner.showTitle(
            Title.title(
                Component.text("FIGHT!", NamedTextColor.RED, TextDecoration.BOLD),
                Component.empty(),
                Title.Times.times(
                    java.time.Duration.ZERO,
                    java.time.Duration.ofMillis(1000),
                    java.time.Duration.ofMillis(500))));
        owner.playSound(owner.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.5f);
        plugin.messages().send(owner, "combat.fight-start");
      }
    }

    if (tick % 5 == 0 && hud) {
      if (mode.isOutnumbered()) {
        int alive = aliveBotCount();
        int totalBots = mode.botCount();
        double totalHp = aliveBots.stream()
            .filter(b -> b != null && b.player() != null && !b.player().isDead())
            .mapToDouble(b -> b.player().getHealth())
            .sum();
        double maxHp = kit.health() * totalBots;
        bar.setTitle(TextUI.legacy(
            Component.text(
                mode.id().toUpperCase(java.util.Locale.ROOT)
                    + " | Bots Alive: "
                    + alive
                    + "/"
                    + totalBots
                    + " | HP: "
                    + Math.round(totalHp)
                    + "/"
                    + Math.round(maxHp))));
        bar.setProgress((float) Math.clamp(totalHp / Math.max(1.0, maxHp), 0.0, 1.0));
      } else if (bot != null && bot.player() != null) {
        bar.setTitle(TextUI.legacy(
            Component.text(
                "Bot "
                    + Math.round(bot.player().getHealth())
                    + "/"
                    + kit.health()
                    + " HP | Apples "
                    + countApples(bot.player()))));
        bar.setProgress((float) Math.clamp(bot.player().getHealth() / kit.health(), 0.0, 1.0));
      }
      TextUI.actionBar(owner,
          Component.text(
              mode.id()
                  + " | Round "
                  + match.round()
                  + " | You "
                  + match.playerWins()
                  + " : "
                  + match.botWins()
                  + (mode.isOutnumbered() ? " Bots | " : " Bot | ")
                  + difficulty.prettyDisplayName()
                  + " | "
                  + (match.phase() == Match.Phase.FINISHED
                      ? "Finished"
                      : fighting()
                          ? "Combo " + round.combo(tick)
                          : "Ready In " + ((match.remaining() + 19) / 20))
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
    if (closing || internalTeleport || !fighting()) return;
    if (arena.contains(destination)) return;
    for (BotPlatform b : aliveBots) {
      if (b == null || b.player() == null) continue;
      if (b.player().getWorld().equals(destination.getWorld())
          && b.player().getLocation().distanceSquared(destination) < 32 * 32) continue;
      Location safe = nearbySafe(destination);
      if (safe != null) relocateBot(b, safe);
      else
        plugin.messages().send(owner, "session.safe-ground-waiting");
    }
  }

  private void relocateBot(BotPlatform b, Location destination) {
    internalTeleport = true;
    try {
      double health = b.player().getHealth();
      b.relocate(destination);
      double maxAllowedHealth = Attributes.getValue(b.player(), Attributes.MAX_HEALTH, 20.0);
      b.player().setHealth(Math.min(health, maxAllowedHealth));
    } finally {
      internalTeleport = false;
    }
  }

  public Kit kit() { return kit; }
  public Difficulty difficulty() { return difficulty; }
  public PracticeMode mode() { return mode; }
  public Match match() { return match; }
  public String arenaName() { return arenaName; }
  public boolean isPaused() { return match.paused(); }
  public boolean isFighting() { return fighting(); }
  public BotPlatform bot() { return bot; }
  public List<BotPlatform> bots() { return Collections.unmodifiableList(bots); }
  public List<BotPlatform> aliveBots() { return Collections.unmodifiableList(aliveBots); }
  public Set<UUID> botIds() { return Collections.unmodifiableSet(botIds); }

  void setPaused(boolean paused) {
    if (match.paused() == paused)
      throw new IllegalArgumentException(plugin.messages().get(paused ? "combat.already-paused" : "combat.not-paused"));
    if (paused) {
      plugin.clearUtilities(this);
      List<Player> participants = new ArrayList<>();
      participants.add(owner);
      for (BotPlatform b : aliveBots) {
        if (b != null && b.player() != null) participants.add(b.player());
      }
      for (Player p : participants) {
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
    TextUI.actionBar(owner, plugin.messages().component(paused ? "combat.actionbar-paused" : "combat.actionbar-resumed"));
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
    plugin.messages().send(owner, hud ? "combat.hud-enabled" : "combat.hud-hidden");
  }

  void win(boolean player) {
    if (!match.win(player)) return;
    if (player) roundsWon++;
    else roundsLost++;
    owner.sendMessage(plugin.messages().get(
        player ? "combat.round-won" : "combat.round-lost",
        "%round%", String.valueOf(match.round()),
        "%summary%", round.summary(tick - fightStart)));
    if (match.phase() == Match.Phase.FINISHED) {
      boolean playerWonSeries = match.playerWins() > match.botWins();
      if (playerWonSeries) seriesWon++;
      else seriesLost++;

      boolean duel = mode == PracticeMode.DUEL;
      String score = match.playerWins() + "-" + match.botWins();
      String result;
      if (duel || mode == PracticeMode.MATCH || mode.isOutnumbered()) {
        if (duel) {
          result = plugin.messages().get(playerWonSeries ? "combat.player-won-duel" : "combat.bot-won-duel");
          plugin.messages().send(owner, "combat.duel-finished", "%result%", result);
        } else if (mode == PracticeMode.V2_1) {
          result = plugin.messages().get(playerWonSeries ? "combat.player-won-2v1" : "combat.bot-won-2v1", "%score%", score);
          plugin.messages().send(owner, "combat.v21-finished", "%result%", result);
        } else if (mode == PracticeMode.V3_1) {
          result = plugin.messages().get(playerWonSeries ? "combat.player-won-3v1" : "combat.bot-won-3v1", "%score%", score);
          plugin.messages().send(owner, "combat.v31-finished", "%result%", result);
        } else {
          result = plugin.messages().get(playerWonSeries ? "combat.player-won-match" : "combat.bot-won-match", "%score%", score);
          plugin.messages().send(owner, "combat.match-finished", "%result%", result);
        }
        String victoryTitle = (mode == PracticeMode.V2_1 ? "2v1 " : mode == PracticeMode.V3_1 ? "3v1 " : duel ? "" : "MATCH ")
            + (playerWonSeries ? "VICTORY!" : "DEFEAT!");
        owner.showTitle(Title.title(
            Component.text(victoryTitle, playerWonSeries ? NamedTextColor.GREEN : NamedTextColor.RED),
            duel
                ? Component.text(result, NamedTextColor.GRAY)
                : Component.text("Final Score: " + score, NamedTextColor.GOLD)));
        Sound sound = playerWonSeries ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_WITHER_DEATH;
        owner.playSound(owner.getLocation(), sound, 0.8f, 1.0f);
      } else {
        result = plugin.messages().get("combat.series-finished", "%score%", score);
        owner.sendMessage(result);
      }
      String finalResult = result;
      plugin.getServer().getScheduler().runTaskLater(plugin, () -> plugin.stop(this, finalResult), 40L);
    }
  }

  void close() {
    if (closing) return;
    closing = true;
    captureLayout();
    bar.removePlayer(owner);
    TextUI.actionBar(owner, Component.empty());
    if (totalFightTicks > 0 || roundsWon + roundsLost > 0) {
      plugin.messages().send(
          owner,
          "combat.session-summary",
          "%wins%", String.valueOf(roundsWon),
          "%losses%", String.valueOf(roundsLost),
          "%summary%", total.summary(totalFightTicks));
    }
    try {
      try {
        plugin.clearUtilities(this);
      } finally {
        clearBots();
      }
    } finally {
      restoreModifiers();
      internalTeleport = true;
      try {
        plugin.recovery().restore(owner);
      } finally {
        internalTeleport = false;
        plugin.progress().record(this);
      }
    }

    Component actions = Component.text("  ")
        .append(Component.text("[Rematch]", NamedTextColor.GREEN, TextDecoration.BOLD)
            .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/practice rematch"))
            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                Component.text("Click to rematch with the same kit & difficulty", NamedTextColor.GRAY))))
        .append(Component.text("   "))
        .append(Component.text("[Kit Menu]", NamedTextColor.AQUA, TextDecoration.BOLD)
            .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/practice menu"))
            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                Component.text("Click to open the practice kit selection menu", NamedTextColor.GRAY))))
        .append(Component.text("   "))
        .append(Component.text("[Settings]", NamedTextColor.GOLD, TextDecoration.BOLD)
            .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/practice settings"))
            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                Component.text("Click to customize bot settings", NamedTextColor.GRAY))));
    owner.sendMessage(actions);
  }

  private void stripModifiers(Player player, boolean save) {
    AttributeInstance maxHealthAttr = Attributes.get(player, Attributes.MAX_HEALTH);
    if (maxHealthAttr != null) {
      for (AttributeModifier mod : List.copyOf(maxHealthAttr.getModifiers())) {
        if (save) strippedModifiers.add(mod);
        maxHealthAttr.removeModifier(mod);
      }
    }
  }

  private void restoreModifiers() {
    AttributeInstance maxHealth = Attributes.get(owner, Attributes.MAX_HEALTH);
    if (maxHealth != null) {
      for (AttributeModifier mod : strippedModifiers) {
        try {
          maxHealth.addModifier(mod);
        } catch (IllegalArgumentException ignored) {}
      }
      strippedModifiers.clear();
    }
  }
}
