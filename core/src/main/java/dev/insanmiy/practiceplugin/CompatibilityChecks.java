package dev.insanmiy.practiceplugin;

import dev.insanmiy.practiceplugin.bot.BotAdapters;
import dev.insanmiy.practiceplugin.bot.BotPlatform;
import dev.insanmiy.practiceplugin.model.BotOptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

// Runs Only In The Disposable Server Started With -PcompatibilityTest=true.
final class CompatibilityChecks extends BukkitRunnable implements Listener {
  private enum Phase { CHASE, COMBAT, PAUSE, HEAL, DISCONNECT, SHUTDOWN }

  private final PracticePlugin plugin;
  private final Path result = Path.of("compatibility-result.txt");
  private final List<UUID> spawned = new ArrayList<>();
  private BotPlatform ownerBot;
  private PracticeSession session;
  private Location ownerSpawn;
  private Location botSpawn;
  private Location initialBotLocation;
  private Phase phase = Phase.CHASE;
  private int chaseSpeed = -2;
  private boolean chaseJumped;
  private int ticks;
  private int phaseTicks;
  private int hits;
  private int pausedHits;
  private boolean moved;
  private boolean consumed;
  private ItemStack[] originalInventory;
  private UUID firstOwner;
  private boolean finished;

  CompatibilityChecks(PracticePlugin plugin) {
    this.plugin = plugin;
  }

  void start() {
    try {
      Files.deleteIfExists(result);
      prepareArena();
      ownerBot = BotAdapters.getAdapter().createBot(null, ownerSpawn, "CheckOwner", id -> {
        spawned.add(id);
        plugin.registerBot(id, null);
      });
      Player owner = ownerBot.player();
      firstOwner = owner.getUniqueId();
      plugin.unregisterBot(owner.getUniqueId());
      owner.setOp(true);
      owner.setGameMode(GameMode.ADVENTURE);
      owner.setHealth(17);
      owner.getInventory().clear();
      owner.getInventory().setItem(5, new ItemStack(Material.DIAMOND, 3));
      originalInventory = owner.getInventory().getContents();
      plugin.settings().set(owner.getUniqueId(), "countdown", 1);
      Bukkit.getPluginManager().registerEvents(this, plugin);
      verifyMessages(owner);
      startSession();
      runTaskTimer(plugin, 1, 1);
    } catch (Exception | LinkageError failure) {
      finish(failure);
    }
  }

  private void prepareArena() {
    World world = Bukkit.getWorlds().getFirst();
    world.setTime(6000);
    for (int x = -24; x <= 24; x++) {
      for (int z = -24; z <= 24; z++) {
        world.getBlockAt(x, 80, z).setType(Material.STONE, false);
        for (int y = 81; y <= 85; y++) world.getBlockAt(x, y, z).setType(Material.AIR, false);
      }
    }
    ownerSpawn = new Location(world, 0.5, 81, 0.5);
    botSpawn = new Location(world, 6.5, 81, 0.5);
    plugin.getConfig().set("arena.player", ownerSpawn);
    plugin.getConfig().set("arena.bot", botSpawn);
    plugin.getConfig().set("arena.radius", 20);
    plugin.getConfig().set("hud", false);
    plugin.getConfig().set("session.round-timeout-seconds", 0);
    var difficulty = plugin.getConfig().createSection("difficulties.compatibility");
    difficulty.set("decision-ticks", 5);
    difficulty.set("perception-ticks", 0);
    difficulty.set("aim-error", 0);
    difficulty.set("attack-charge", 0.9);
    difficulty.set("shield-chance", 0);
    difficulty.set("strafe-strength", 0);
    difficulty.set("sprint-reset-chance", 0);
    difficulty.set("crit-chance", 0);
    difficulty.set("pearl-chance", 0);
    difficulty.set("heal-threshold", 0.75);
    difficulty.set("attacks-enabled", true);
    plugin.refreshKits();
  }

  private void verifyMessages(Player owner) throws IOException {
    Path file = plugin.getDataFolder().toPath().resolve("messages.yml");
    byte[] original = Files.readAllBytes(file);
    String arena = plugin.arenaPath("messagecheck");
    try {
      var configured = YamlFiles.read(file);
      configured.set("text.settings-menu.match-settings", "Custom match settings");
      configured.set("text.practice-commands.player", "Champion");
      configured.set("text.practice-commands.bot", "Opponent");
      YamlFiles.write(file, configured);
      plugin.messages().reload();
      Bukkit.dispatchCommand(owner, "practice settings");
      require(owner.getOpenInventory().getTitle().contains("Custom match settings"), "Configured Menu Title Was Not Applied");
      require(owner.getOpenInventory().getTopInventory().getItem(2).getItemMeta().displayName()
          .toString().contains("Custom match settings"), "Configured Menu Label Was Not Applied");
      owner.closeInventory();
      Bukkit.dispatchCommand(owner, "practice arena setplayer messagecheck");
      Bukkit.dispatchCommand(owner, "practice arena setbot messagecheck");
      require(plugin.getConfig().getLocation(arena + ".player") != null
          && plugin.getConfig().getLocation(arena + ".bot") != null,
          "Editable Spawn Labels Changed Arena Storage Keys");
    } finally {
      owner.closeInventory();
      plugin.getConfig().set(arena, null);
      plugin.saveConfig();
      Files.write(file, original);
      plugin.messages().reload();
    }
  }

  private void startSession() {
    Player owner = ownerBot.player();
    Bukkit.dispatchCommand(owner, "practice start diamond-smp compatibility endless");
    session = plugin.sessionFor(owner);
    require(session != null && session.bot != null, "Practice command did not start a session");
    Player bot = session.bot.player();
    spawned.add(bot.getUniqueId());
    require(plugin.sessionFor(bot) == session, "Bot session lookup failed");
    require(bot.isOnline() && bot.isValid(), "Bot was not registered in the world");
    initialBotLocation = bot.getLocation();
    session.bot.configure(new BotOptions(true, false, false, false, false), false);
    bot.getInventory().clear();
    bot.getInventory().setItem(0, new ItemStack(Material.WOODEN_SWORD));
    bot.getInventory().setHeldItemSlot(0);
    owner.getInventory().clear();
    var health = Attributes.get(owner, Attributes.MAX_HEALTH);
    require(health != null, "Maximum health attribute is missing");
    health.setBaseValue(100);
    owner.setHealth(100);
  }

  @Override
  public void run() {
    try {
      ticks++;
      phaseTicks++;
      require(ticks < 1200, "Timed out during " + phase);
      require(plugin.sessionFor(ownerBot.player()) == session, "Session ended unexpectedly during " + phase);
      Player bot = session.bot.player();
      switch (phase) {
        case CHASE -> {
          if (!session.fighting()) return;
          if (chaseSpeed == -2) {
            prepareChase(-1);
            return;
          }
          chaseJumped |= bot.getLocation().getY() > botSpawn.getY() + .2;
          require(phaseTicks < 70, "Bot did not reach the player during speed " + (chaseSpeed + 1) + " chase");
          if (bot.getLocation().distanceSquared(ownerBot.player().getLocation()) > 10.24) return;
          require(chaseJumped == (chaseSpeed <= 0), "Unexpected sprint jump behavior at speed " + (chaseSpeed + 1));
          plugin.getLogger().info("Chase check passed at speed " + (chaseSpeed + 1) + " in " + phaseTicks + " ticks");
          if (chaseSpeed < 1) prepareChase(chaseSpeed + 1);
          else next(Phase.COMBAT);
        }
        case COMBAT -> {
          moved |= bot.getLocation().distanceSquared(initialBotLocation) > 0.25;
          if (!moved || hits < 2) return;
          require(session.total.taken() > 0, "Bot attacks did not record damage");
          Bukkit.dispatchCommand(ownerBot.player(), "practice pause");
          require(!session.fighting(), "Pause command did not pause combat");
          pausedHits = hits;
          next(Phase.PAUSE);
        }
        case PAUSE -> {
          require(hits == pausedHits, "Bot dealt damage while paused");
          if (phaseTicks < 25) return;
          Bukkit.dispatchCommand(ownerBot.player(), "practice resume");
          require(session.fighting(), "Resume command did not resume combat");
          bot.getInventory().clear();
          bot.getInventory().setItem(0, new ItemStack(Material.GOLDEN_APPLE));
          bot.getInventory().setHeldItemSlot(0);
          bot.setHealth(5);
          session.internalTeleport = true;
          try {
            require(ownerBot.player().teleport(ownerSpawn.clone().add(-12, 0, 0)), "Healing target teleport failed");
          } finally {
            session.internalTeleport = false;
          }
          next(Phase.HEAL);
        }
        case HEAL -> {
          if (!consumed || bot.getAbsorptionAmount() <= 0) return;
          require(!bot.getInventory().contains(Material.GOLDEN_APPLE), "Healing item was not consumed");
          session.bot.reset(botSpawn);
          require(bot.getLocation().distanceSquared(botSpawn) < 0.1, "Bot reset failed");
          Bukkit.dispatchCommand(ownerBot.player(), "practice stop");
          require(plugin.sessionFor(ownerBot.player()) == null, "Stop command kept the session");
          verifyRecovery();
          verifyRemovedBots();
          startSession();
          next(Phase.DISCONNECT);
        }
        case DISCONNECT -> {
          ownerBot.player().setOp(false);
          ownerBot.close();
          require(plugin.sessions().isEmpty(), "Disconnect did not stop the session");
          verifyRemovedBots();
          require(!plugin.recovery().pending(ownerBot.player()), "Disconnect left player recovery pending");
          verifyRecovery();
          ownerBot = BotAdapters.getAdapter().createBot(null, ownerSpawn, "ShutdownOwner", id -> {
            spawned.add(id);
            plugin.registerBot(id, null);
          });
          plugin.unregisterBot(ownerBot.player().getUniqueId());
          ownerBot.player().setOp(true);
          ownerBot.player().setGameMode(GameMode.ADVENTURE);
          ownerBot.player().setHealth(17);
          ownerBot.player().getInventory().clear();
          ownerBot.player().getInventory().setItem(5, new ItemStack(Material.DIAMOND, 3));
          startSession();
          next(Phase.SHUTDOWN);
        }
        case SHUTDOWN -> {
          Bukkit.getPluginManager().disablePlugin(plugin);
          require(plugin.sessions().isEmpty(), "Shutdown kept active sessions");
          verifyRemovedBots();
          verifyRecovery();
          var dataFolder = plugin.getDataFolder().toPath();
          require(YamlFiles.read(dataFolder.resolve("stats.yml")).getLong(firstOwner + ".endless.sessions") > 0,
              "Shutdown did not persist queued statistics");
          require(YamlFiles.read(dataFolder.resolve("player-settings.yml")).getInt(firstOwner + ".countdown") == 1,
              "Shutdown did not persist queued settings");
          finish(null);
        }
      }
    } catch (Exception | LinkageError failure) {
      finish(failure);
    }
  }

  private void verifyRecovery() {
    Player owner = ownerBot.player();
    require(java.util.Arrays.equals(originalInventory, owner.getInventory().getContents()), "Original inventory was not restored");
    require(owner.getGameMode() == GameMode.ADVENTURE, "Original game mode was not restored");
    require(Math.abs(owner.getHealth() - 17) < 0.01, "Original health was not restored");
    require(!plugin.recovery().pending(owner), "Recovery journal was not cleared");
  }

  private void prepareChase(int speedAmplifier) {
    chaseSpeed = speedAmplifier;
    chaseJumped = false;
    phaseTicks = 0;
    session.internalTeleport = true;
    try {
      require(ownerBot.player().teleport(ownerSpawn), "Chase target teleport failed");
      session.bot.reset(botSpawn.clone().add(10, 0, 8));
    } finally {
      session.internalTeleport = false;
    }
    Player bot = session.bot.player();
    bot.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
    if (speedAmplifier >= 0) {
      bot.addPotionEffect(new org.bukkit.potion.PotionEffect(
          org.bukkit.potion.PotionEffectType.SPEED, 1200, speedAmplifier));
    }
  }

  private void verifyRemovedBots() {
    for (UUID id : spawned) {
      if (ownerBot != null && id.equals(ownerBot.player().getUniqueId())) continue;
      require(Bukkit.getPlayer(id) == null && Bukkit.getEntity(id) == null, "Bot was not removed: " + id);
      require(!plugin.sessions().isBot(id), "Bot lookup was not cleared: " + id);
    }
  }

  private void next(Phase phase) {
    plugin.getLogger().info("Compatibility check passed: " + this.phase);
    this.phase = phase;
    phaseTicks = 0;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void damage(EntityDamageByEntityEvent event) {
    if (session != null && event.getEntity().equals(session.owner)
        && event.getDamager().equals(session.bot.player()) && event.getFinalDamage() > 0) hits++;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void consume(PlayerItemConsumeEvent event) {
    if (session != null && event.getPlayer().equals(session.bot.player())
        && event.getItem().getType() == Material.GOLDEN_APPLE) consumed = true;
  }

  private void finish(Throwable failure) {
    if (finished) return;
    finished = true;
    HandlerList.unregisterAll(this);
    Throwable resultFailure = failure;
    try {
      if (session != null) plugin.stop(session, null);
      if (ownerBot != null) {
        ownerBot.player().setOp(false);
        ownerBot.close();
        require(Bukkit.getPlayer(ownerBot.player().getUniqueId()) == null, "Owner bot was not removed");
      }
      if (plugin.isEnabled()) plugin.saves().flush();
    } catch (Exception | LinkageError cleanupFailure) {
      if (resultFailure == null) resultFailure = cleanupFailure;
      else resultFailure.addSuppressed(cleanupFailure);
    }
    try {
      if (resultFailure == null) {
        Files.writeString(result, "PASS\n");
        plugin.getLogger().info("Compatibility checks passed");
      } else {
        Files.writeString(result, "FAIL: " + resultFailure + "\n");
        plugin.getLogger().log(java.util.logging.Level.SEVERE, "Compatibility checks failed", resultFailure);
      }
    } catch (IOException writeFailure) {
      plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not write compatibility result", writeFailure);
    } finally {
      Bukkit.shutdown();
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
