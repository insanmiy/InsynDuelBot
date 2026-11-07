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
  private enum Phase { COMBAT, PAUSE, HEAL, DISCONNECT, SHUTDOWN }

  private final PracticePlugin plugin;
  private final Path result = Path.of("compatibility-result.txt");
  private final List<UUID> spawned = new ArrayList<>();
  private BotPlatform ownerBot;
  private PracticeSession session;
  private Location ownerSpawn;
  private Location botSpawn;
  private Location initialBotLocation;
  private Phase phase = Phase.COMBAT;
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
    difficulty.set("decision-ticks", 1);
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

  private void startSession() {
    Player owner = ownerBot.player();
    Bukkit.dispatchCommand(owner, "practice start diamond-smp compatibility endless");
    session = plugin.sessionFor(owner);
    require(session != null && session.bot != null, "Practice Command Did Not Start A Session");
    Player bot = session.bot.player();
    spawned.add(bot.getUniqueId());
    require(plugin.sessionFor(bot) == session, "Bot Session Lookup Failed");
    require(bot.isOnline() && bot.isValid(), "Bot Was Not Registered In The World");
    initialBotLocation = bot.getLocation();
    session.bot.configure(new BotOptions(true, false, false, false, false), false);
    bot.getInventory().clear();
    bot.getInventory().setItem(0, new ItemStack(Material.WOODEN_SWORD));
    bot.getInventory().setHeldItemSlot(0);
    owner.getInventory().clear();
    var health = Attributes.get(owner, Attributes.MAX_HEALTH);
    require(health != null, "Maximum Health Attribute Is Missing");
    health.setBaseValue(100);
    owner.setHealth(100);
  }

  @Override
  public void run() {
    try {
      ticks++;
      phaseTicks++;
      require(ticks < 1200, "Timed Out During " + phase);
      require(plugin.sessionFor(ownerBot.player()) == session, "Session Ended Unexpectedly During " + phase);
      Player bot = session.bot.player();
      switch (phase) {
        case COMBAT -> {
          moved |= bot.getLocation().distanceSquared(initialBotLocation) > 0.25;
          if (!moved || hits < 2) return;
          require(session.total.taken() > 0, "Bot Attacks Did Not Record Damage");
          Bukkit.dispatchCommand(ownerBot.player(), "practice pause");
          require(!session.fighting(), "Pause Command Did Not Pause Combat");
          pausedHits = hits;
          next(Phase.PAUSE);
        }
        case PAUSE -> {
          require(hits == pausedHits, "Bot Dealt Damage While Paused");
          if (phaseTicks < 25) return;
          Bukkit.dispatchCommand(ownerBot.player(), "practice resume");
          require(session.fighting(), "Resume Command Did Not Resume Combat");
          bot.getInventory().clear();
          bot.getInventory().setItem(0, new ItemStack(Material.GOLDEN_APPLE));
          bot.getInventory().setHeldItemSlot(0);
          bot.setHealth(5);
          session.internalTeleport = true;
          try {
            require(ownerBot.player().teleport(ownerSpawn.clone().add(-12, 0, 0)), "Healing Target Teleport Failed");
          } finally {
            session.internalTeleport = false;
          }
          next(Phase.HEAL);
        }
        case HEAL -> {
          if (!consumed || bot.getAbsorptionAmount() <= 0) return;
          require(!bot.getInventory().contains(Material.GOLDEN_APPLE), "Healing Item Was Not Consumed");
          session.bot.reset(botSpawn);
          require(bot.getLocation().distanceSquared(botSpawn) < 0.1, "Bot Reset Failed");
          Bukkit.dispatchCommand(ownerBot.player(), "practice stop");
          require(plugin.sessionFor(ownerBot.player()) == null, "Stop Command Kept The Session");
          verifyRecovery();
          verifyRemovedBots();
          startSession();
          next(Phase.DISCONNECT);
        }
        case DISCONNECT -> {
          ownerBot.player().setOp(false);
          ownerBot.close();
          require(plugin.sessions().isEmpty(), "Disconnect Did Not Stop The Session");
          verifyRemovedBots();
          require(!plugin.recovery().pending(ownerBot.player()), "Disconnect Left Player Recovery Pending");
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
          require(plugin.sessions().isEmpty(), "Shutdown Kept Active Sessions");
          verifyRemovedBots();
          verifyRecovery();
          var dataFolder = plugin.getDataFolder().toPath();
          require(YamlFiles.read(dataFolder.resolve("stats.yml")).getLong(firstOwner + ".endless.sessions") > 0,
              "Shutdown Did Not Persist Queued Statistics");
          require(YamlFiles.read(dataFolder.resolve("player-settings.yml")).getInt(firstOwner + ".countdown") == 1,
              "Shutdown Did Not Persist Queued Settings");
          finish(null);
        }
      }
    } catch (Exception | LinkageError failure) {
      finish(failure);
    }
  }

  private void verifyRecovery() {
    Player owner = ownerBot.player();
    require(java.util.Arrays.equals(originalInventory, owner.getInventory().getContents()), "Original Inventory Was Not Restored");
    require(owner.getGameMode() == GameMode.ADVENTURE, "Original Game Mode Was Not Restored");
    require(Math.abs(owner.getHealth() - 17) < 0.01, "Original Health Was Not Restored");
    require(!plugin.recovery().pending(owner), "Recovery Journal Was Not Cleared");
  }

  private void verifyRemovedBots() {
    for (UUID id : spawned) {
      if (ownerBot != null && id.equals(ownerBot.player().getUniqueId())) continue;
      require(Bukkit.getPlayer(id) == null && Bukkit.getEntity(id) == null, "Bot Was Not Removed: " + id);
      require(!plugin.sessions().isBot(id), "Bot Lookup Was Not Cleared: " + id);
    }
  }

  private void next(Phase phase) {
    plugin.getLogger().info("Compatibility Check Passed: " + this.phase);
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
        require(Bukkit.getPlayer(ownerBot.player().getUniqueId()) == null, "Owner Bot Was Not Removed");
      }
      if (plugin.isEnabled()) plugin.saves().flush();
    } catch (Exception | LinkageError cleanupFailure) {
      if (resultFailure == null) resultFailure = cleanupFailure;
      else resultFailure.addSuppressed(cleanupFailure);
    }
    try {
      if (resultFailure == null) {
        Files.writeString(result, "PASS\n");
        plugin.getLogger().info("Compatibility Checks Passed");
      } else {
        Files.writeString(result, "FAIL: " + resultFailure + "\n");
        plugin.getLogger().log(java.util.logging.Level.SEVERE, "Compatibility Checks Failed", resultFailure);
      }
    } catch (IOException writeFailure) {
      plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could Not Write Compatibility Result", writeFailure);
    } finally {
      Bukkit.shutdown();
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
