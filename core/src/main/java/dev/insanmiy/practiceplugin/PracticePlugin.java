package dev.insanmiy.practiceplugin;

import dev.insanmiy.practiceplugin.bot.BotAdapters;
import dev.insanmiy.practiceplugin.config.*;
import dev.insanmiy.practiceplugin.config.Difficulty;
import dev.insanmiy.practiceplugin.model.PracticeMode;
import java.io.File;
import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class PracticePlugin extends JavaPlugin implements Listener {
  private final Set<UUID> bots = new HashSet<>();
  private final Map<String, Kit> kits = new LinkedHashMap<>();
  private final Map<String, Difficulty> difficulties = new LinkedHashMap<>();
  private NamespacedKey gearKey, slotKey;
  private KitLayoutStore layouts;
  private RecoveryStore recovery;
  private final Map<UUID, PracticeSession> sessions = new LinkedHashMap<>();
  private SettingsMenu settingsMenu;
  private PlayerSettings settings;
  private ProgressStore progress;
  private PracticeMenu menu;
  private KitEditor kitEditor;
  private TemporaryBlocks blocks;
  private final Set<Projectile> projectiles = new HashSet<>();
  private Messages messages;

  public Messages messages() {
    return messages;
  }

  void clearUtilities(PracticeSession session) {
    UUID owner = session.owner.getUniqueId();
    session.projectiles.forEach(p -> {
      projectiles.remove(p);
      p.remove();
    });
    session.projectiles.clear();
    try {
      blocks.restoreOwner(owner);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Could Not Save Session Cleanup", e);
    }
    World world = session.arena.playerSpawn().getWorld();
    if (world != null) {
      Location center = session.arena.playerSpawn();
      double r = Math.min(128.0, Math.max(16.0, session.arena.radius()));
      for (Entity entity : world.getNearbyEntities(center, r, r, r)) {
        if ((entity instanceof Item
                || entity instanceof Projectile
                || entity instanceof ExperienceOrb
                || entity instanceof AreaEffectCloud)
            && session.arena.contains(entity.getLocation())) {
          entity.remove();
        }
      }
    }
  }

  void clearUtilities() {
    for (PracticeSession s : sessions.values()) {
      s.projectiles.clear();
    }
    projectiles.forEach(Entity::remove);
    projectiles.clear();
    if (blocks != null){
      try {
        if (!blocks.restoreAll()){
          getLogger().warning("Block Recovery Retained For An Unloaded World.");
        }
      } catch (java.io.IOException e) {
        throw new IllegalStateException("Could Not Save Block Cleanup Journal", e);
      }
    }
  }

  private final Map<UUID, Selection> lastSelections = new HashMap<>();

  public PracticeSession sessionFor(Entity entity) {
    if (entity instanceof Projectile p)
      return p.getShooter() instanceof Entity shooter ? sessionFor(shooter) : null;
    if (!(entity instanceof Player p)) return null;
    PracticeSession own = sessions.get(p.getUniqueId());
    if (own != null) return own;
    if (!bots.contains(p.getUniqueId())) return null;
    return sessions.values().stream().filter(s -> s.participant(p)).findFirst().orElse(null);
  }

  PlayerSettings settings() {
    return settings;
  }

  void openSettings(Player p) {
    settingsMenu.open(p);
  }

  String botName() {
    String base = getConfig().getString("bot-name", "PracticeBot");
    for (int i = 0; i < 10000; i++) {
      String suffix = i == 0 ? "" : "_" + i;
      String name = base.substring(0, Math.min(base.length(), 16 - suffix.length())) + suffix;
      if (Bukkit.getPlayerExact(name) == null) return name;
    }
    throw new IllegalArgumentException("No Free Bot Names.");
  }

  static String validArenaName(String name) {
    String normalized = name.toLowerCase(Locale.ROOT);
    if (!normalized.matches("[a-z0-9_-]{1,32}"))
      throw new IllegalArgumentException("Arena Names: 1 To 32 Letters, Digits, Hyphen Or Underscore.");
    return normalized;
  }

  private static String arenaPath(String name) {
    return name.equals("default") ? "arena" : "arenas." + validArenaName(name);
  }

  List<String> arenaNames() {
    List<String> names = new ArrayList<>();
    names.add("default");
    var root = getConfig().getConfigurationSection("arenas");
    if (root != null)
      names.addAll(root.getKeys(false).stream().filter(n -> !n.equals("default")).toList());
    return names;
  }

  boolean hasConfiguredArena() {
    for (String name : arenaNames()) {
      try {
        arena(name);
        return true;
      } catch (IllegalArgumentException ignored) {}
    }
    return false;
  }

  private Map.Entry<String, Arena> availableArena() {
    List<String> errors = new ArrayList<>();
    for (String name : arenaNames()) {
      if (sessions.values().stream().anyMatch(s -> s.arenaName.equals(name))) continue;
      try {
        Arena candidate = arena(name);
        if (sessions.values().stream().anyMatch(s -> s.arena.overlaps(candidate))) continue;
        return Map.entry(name, candidate);
      } catch (IllegalArgumentException e) {
        errors.add(name + ": " + e.getMessage());
      }
    }
    throw new IllegalArgumentException(
        messages.get("arena.no-free-arena", "%errors%", String.join("; ", errors)));
  }

  public ProgressStore progress() {
    return progress;
  }

  Map<String, Kit> kits() {
    return Collections.unmodifiableMap(kits);
  }

  void refreshKits() {
    loadSettings();
  }

  Map<String, Difficulty> difficulties() {
    return Collections.unmodifiableMap(difficulties);
  }

  Selection defaults(Player p) {
    Selection draft = menu != null ? menu.draft(p.getUniqueId()) : null;
    if (draft != null && kits.containsKey(draft.kit()) && difficulties.containsKey(draft.difficulty())) {
      return draft;
    }
    Selection last = lastSelections.get(p.getUniqueId());
    if (last != null && kits.containsKey(last.kit()) && difficulties.containsKey(last.difficulty())) {
      return last;
    }
    String defaultKit = getConfig().getString("session.default-kit", "diamond-smp");
    if (!kits.containsKey(defaultKit) && !kits.isEmpty()) {
      defaultKit = kits.keySet().iterator().next();
    }
    String defaultDiff = getConfig().getString("session.default-difficulty", "lt4");
    if (!difficulties.containsKey(defaultDiff) && !difficulties.isEmpty()) {
      defaultDiff = difficulties.keySet().iterator().next();
    }
    return new Selection(
        defaultKit,
        defaultDiff,
        PracticeMode.parse(getConfig().getString("session.default-mode", "match")),
        getConfig().getInt("session.best-of", 5));
  }

  @Override
  public void onEnable() {
    String serverVersion = Bukkit.getBukkitVersion().split("-")[0];
    try {
      BotAdapters.getAdapter();
      getLogger().info("Successfully Loaded NMS Adapter For Minecraft " + serverVersion);
    } catch (UnsupportedOperationException | IllegalStateException e) {
      getLogger().severe("Failed To Initialize Version Adapter: " + e.getMessage());
      getServer().getPluginManager().disablePlugin(this);
      return;
    }

    gearKey = Objects.requireNonNull(NamespacedKey.fromString("practiceplugin:practice_gear"));
    slotKey = Objects.requireNonNull(NamespacedKey.fromString("practiceplugin:kit_slot"));
    layouts = new KitLayoutStore(this);
    recovery = new RecoveryStore(this);
    saveDefaultConfig();
    try {
      ConfigUpgrade.apply(this);
      KitFiles.initialize(this);
      reloadConfig();
    } catch (java.io.IOException e) {
      getLogger().log(java.util.logging.Level.SEVERE, "Could Not Upgrade Configuration Safely", e);
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    messages = new Messages(this);
    dev.insanmiy.practiceplugin.papi.PapiHook.init(this);
    progress = new ProgressStore(this);
    blocks = new TemporaryBlocks(this);
    getServer()
        .getScheduler()
        .runTaskTimer(
            this,
            () -> {
              try {
                blocks.flush();
              } catch (java.io.IOException e) {
                getLogger().log(java.util.logging.Level.SEVERE, "Could Not Save Block Journal", e);
              }
            },
            20,
            20);
    clearUtilities();
    for (World world : Bukkit.getWorlds())
      for (Entity entity : world.getEntities())
        if (entity instanceof Projectile && entity.getPersistentDataContainer().has(gearKey))
          entity.remove();
    settings = new PlayerSettings(this);
    settingsMenu = new SettingsMenu(this);
    menu = new PracticeMenu(this);
    kitEditor = new KitEditor(this);
    loadSettings();
    ServerFeatures.register(this);
    getServer().getPluginManager().registerEvents(kitEditor, this);
    getServer().getPluginManager().registerEvents(menu, this);
    getServer().getPluginManager().registerEvents(settingsMenu, this);
    getServer().getPluginManager().registerEvents(this, this);
    getServer().getScheduler().runTaskTimer(this, new MenuAnimation(this), 6, 6);
    PluginCommand practiceCmd = Objects.requireNonNull(getCommand("practice"));
    practiceCmd.setExecutor(this);
    practiceCmd.setTabCompleter(this);
    Bukkit.getOnlinePlayers().forEach(recovery::restore);
    getServer()
        .getScheduler()
        .runTaskTimer(
            this,
            () -> {
              projectiles.removeIf(p -> !p.isValid());
              for (PracticeSession session : List.copyOf(sessions.values())) {
                session.projectiles.removeIf(p -> !p.isValid());
                try {
                  session.tick();
                } catch (Exception | LinkageError e) {
                  getLogger().log(java.util.logging.Level.SEVERE, "Practice Session Failed", e);
                  stop(session, "Session Stopped After An Internal Error.");
                }
              }
            },
            1,
            1);
  }

  @Override
  public void onDisable() {
    dev.insanmiy.practiceplugin.papi.PapiHook.shutdown();
    for (PracticeSession session : List.copyOf(sessions.values())) stop(session, messages != null ? messages.get("general.internal-error") : "Plugin Stopped.");
    clearUtilities();
    if (settingsMenu != null) settingsMenu.closeAll();
    if (menu != null) menu.closeAll();
    if (kitEditor != null) kitEditor.closeAll();
    if (progress != null) progress.save();
    if (blocks != null) {
      try {
        blocks.flush();
      } catch (java.io.IOException e) {
        getLogger().log(java.util.logging.Level.SEVERE, "Could Not Save Block Journal", e);
      }
    }
  }

  NamespacedKey gearKey() {
    return gearKey;
  }

  NamespacedKey slotKey() {
    return slotKey;
  }

  KitLayoutStore layouts() {
    return layouts;
  }

  RecoveryStore recovery() {
    return recovery;
  }

  void unregisterBot(UUID id) { bots.remove(id); }

  void registerBot(UUID id) {
    bots.add(id);
  }

  boolean isBot(Player p) {
    return bots.contains(p.getUniqueId());
  }

  boolean participant(Player p) {
    return sessionFor(p) != null;
  }

  void stop(PracticeSession old, String message) {
    if (old == null || sessions.get(old.owner.getUniqueId()) != old) return;
    try {
      old.close();
    } catch (Exception e) {
      getLogger()
          .log(java.util.logging.Level.SEVERE, "Cleanup Failed; Recovery Record Retained", e);
    } finally {
      if (message != null && !message.isEmpty()) {
        old.owner.sendMessage(Messages.color(message));
      }
      sessions.remove(old.owner.getUniqueId(), old);
      if (old.botId != null) bots.remove(old.botId);
    }
  }

  private void loadSettings() {
    getConfig()
        .setDefaults(new YamlConfiguration());
    kits.clear();
    difficulties.clear();
    ConfigurationSection root =
        YamlConfiguration.loadConfiguration(new File(getDataFolder(), "kits.yml"))
            .getConfigurationSection("kits");
    if (root != null)
      for (String key : root.getKeys(false))
        try {
          kits.put(key, Kit.read(key, Objects.requireNonNull(root.getConfigurationSection(key))));
        } catch (Exception e) {
          getLogger().warning("Kit '" + key + "' Disabled: " + e.getMessage());
        }
    ConfigurationSection custom =
        YamlConfiguration.loadConfiguration(new File(getDataFolder(), "customkits.yml"));
    for (String key : custom.getKeys(false)) {
      try {
        kits.put(key, Kit.read(key, Objects.requireNonNull(custom.getConfigurationSection(key))));
      } catch (Exception e) {
        getLogger().warning("Custom Kit '" + key + "' Disabled: " + e.getMessage());
      }
    }
    root = getConfig().getConfigurationSection("difficulties");
    if (root != null)
      for (String key : root.getKeys(false))
        try {
          difficulties.put(
              key, Difficulty.read(key, Objects.requireNonNull(root.getConfigurationSection(key))));
        } catch (Exception e) {
          getLogger().warning("Difficulty '" + key + "' Disabled: " + e.getMessage());
        }
    String name = getConfig().getString("bot-name", "PracticeBot");
    if (!name.matches("[A-Za-z0-9_]{1,16}")) {
      getConfig().set("bot-name", "PracticeBot");
      getLogger().warning("Invalid Bot-Name. Using PracticeBot");
    }
    for (String key :
        List.of("countdown-seconds", "between-round-seconds", "round-timeout-seconds")) {
      int value = getConfig().getInt("session." + key, 3);
      if (value < (key.equals("round-timeout-seconds") ? 0 : 1) || value > 600)
        getConfig().set("session." + key, key.equals("round-timeout-seconds") ? 0 : 3);
    }
    int bestOf = getConfig().getInt("session.best-of", 5);
    double startingSaturation = getConfig().getDouble("session.starting-saturation", 20);
    if (!Double.isFinite(startingSaturation) || startingSaturation < 0 || startingSaturation > 20)
      getConfig().set("session.starting-saturation", 20);
    if (bestOf < 1 || bestOf > 99 || bestOf % 2 == 0) getConfig().set("session.best-of", 5);
    try {
      PracticeMode.parse(getConfig().getString("session.default-mode", "match"));
    } catch (IllegalArgumentException e) {
      getConfig().set("session.default-mode", "match");
    }
    String configuredDiff = getConfig().getString("session.default-difficulty");
    if (configuredDiff != null && !difficulties.containsKey(configuredDiff) && !difficulties.isEmpty()) {
      getConfig().set("session.default-difficulty", difficulties.keySet().iterator().next());
    }
    String configuredKit = getConfig().getString("session.default-kit");
    if (configuredKit != null && !kits.containsKey(configuredKit) && !kits.isEmpty()) {
      getConfig().set("session.default-kit", kits.keySet().iterator().next());
    }
  }

  void startFromMenu(Player p, Selection choice) {
    try {
      start(p, choice);
    } catch (IllegalArgumentException e) {
      p.sendMessage(Messages.color(e.getMessage().contains("\u00A7") || e.getMessage().contains("&") ? e.getMessage() : "&c" + e.getMessage()));
    } catch (Exception | LinkageError e) {
      getLogger().log(java.util.logging.Level.SEVERE, "Practice Start Failed", e);
      messages.send(p, "session.session-internal-error");
    }
  }

  private void start(Player p, Selection choice) throws java.io.IOException {
    if (sessions.containsKey(p.getUniqueId()))
      throw new IllegalArgumentException(messages.get("session.already-in-match"));
    p.closeInventory();
    if (recovery.pending(p)) {
      recovery.restore(p);
      if (recovery.pending(p))
        throw new IllegalArgumentException(messages.get("session.recovery-pending"));
    }
    if (p.isDead())
      throw new IllegalArgumentException(messages.get("session.respawn-before-practice"));
    if (p.isInsideVehicle()) {
      p.leaveVehicle();
    }
    if (p.isInvulnerable()) {
      p.setInvulnerable(false);
    }
    Kit kit = kits.get(choice.kit());
    Difficulty difficulty = difficulties.get(choice.difficulty());
    if (kit == null || difficulty == null)
      throw new IllegalArgumentException(messages.get("session.unknown-selection"));
    if (!hasPerm(p, "practice.start"))
      throw new IllegalArgumentException(messages.get("general.no-permission", "%permission%", "practice.start"));
    Map.Entry<String, Arena> assigned = availableArena();
    PracticeSession session =
        new PracticeSession(
            this, p, assigned.getValue(), kit, difficulty, choice.mode(), choice.bestOf());
    session.arenaName = assigned.getKey();
    recovery.capture(p);
    p.getInventory().clear();
    p.setItemOnCursor(null);
    p.setGameMode(GameMode.SURVIVAL);
    p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
    p.setAbsorptionAmount(0);

    sessions.put(p.getUniqueId(), session);
    try {
      session.begin();
      if (!dev.insanmiy.practiceplugin.bot.BotAdapters.getAdapter().criticalsEnabled(p))
        messages.send(p, "session.paper-criticals-warning");
    } catch (Exception | LinkageError e) {
      stop(session, messages.get("session.bot-spawn-failed"));
      throw e;
    }
    lastSelections.put(p.getUniqueId(), choice);
    if (settings.hasBotOverrides(p.getUniqueId())) {
      messages.send(p, "session.custom-tuning-active");
    }
    messages.send(p, "session.starting-arena", "%arena%", session.arenaName);
    messages.send(
        p,
        "session.starting",
        "%kit%", kit.name(),
        "%difficulty%", difficulty.prettyDisplayName(),
        "%mode%", choice.mode().id(),
        "%summary%", kit.summary());
  }

  private PracticeSession ownSession(CommandSender sender) {
    if (!(sender instanceof Player p)) throw new IllegalArgumentException(messages.get("general.players-only"));
    PracticeSession session = sessions.get(p.getUniqueId());
    if (session == null) throw new IllegalArgumentException(messages.get("session.no-active-match"));
    return session;
  }

  private Player player(CommandSender sender) {
    if (!(sender instanceof Player p)) throw new IllegalArgumentException(messages.get("general.players-only"));
    return p;
  }

  private Arena arena() {
    return arena("default");
  }

  private Arena arena(String name) {
    String path = arenaPath(name);
    Location player = getConfig().getLocation(path + ".player"),
        bot = getConfig().getLocation(path + ".bot");
    if (player == null || bot == null)
      throw new IllegalArgumentException(messages.get("arena.missing-spawns"));
    double radius = getConfig().getDouble(path + ".radius", getConfig().getDouble("arena.radius", 32));
    double maxRadius = getConfig().getDouble("arena.max-radius", 0.0);
    if (maxRadius > 0 && radius > maxRadius)
      throw new IllegalArgumentException(
          messages.get("arena.radius-exceeds-max", "%radius%", String.valueOf(radius), "%max%", String.valueOf(maxRadius)));
    Arena arena = new Arena(player, bot, radius);
    if (arena.error() != null) throw new IllegalArgumentException(arena.error());
    return arena;
  }

  @Override
  public boolean onCommand(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {
    try {
      PracticeSession session = sender instanceof Player p ? sessions.get(p.getUniqueId()) : null;
      String sub =
          args.length == 0
              ? (sender instanceof Player ? "menu" : "help")
              : args[0].toLowerCase(Locale.ROOT);
      switch (sub) {
        case "settings" -> {
          checkPerm(sender, "practice.settings");
          settingsMenu.open(player(sender));
        }
        case "menu" -> {
          checkPerm(sender, "practice.menu");
          Player p = player(sender);
          if (session != null)
            throw new IllegalArgumentException(messages.get("session.stop-before-menu"));
          menu.open(p, defaults(p));
        }
        case "start" -> {
          checkPerm(sender, "practice.start");
          Player p = player(sender);
          Selection d = defaults(p);
          start(
              p,
              new Selection(
                  args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : d.kit(),
                  args.length > 2 ? args[2] : d.difficulty(),
                  args.length > 3 ? PracticeMode.parse(args[3]) : d.mode(),
                  args.length > 4 ? Integer.parseInt(args[4]) : d.bestOf()));
        }
        case "rematch" -> {
          checkPerm(sender, "practice.rematch");
          Player p = player(sender);
          Selection previous = lastSelections.get(p.getUniqueId());
          if (previous == null)
            throw new IllegalArgumentException(messages.get("combat.rematch-none"));
          start(p, previous);
        }
        case "pause" -> {
          checkPerm(sender, "practice.pause");
          if (ownSession(sender).endingRound)
            throw new IllegalArgumentException(messages.get("combat.wait-for-round-result"));
          ownSession(sender).setPaused(true);
        }
        case "resume" -> {
          checkPerm(sender, "practice.pause");
          ownSession(sender).setPaused(false);
        }
        case "hud" -> {
          checkPerm(sender, "practice.hud");
          ownSession(sender).toggleHud();
        }
        case "damage" -> {
          checkPerm(sender, "practice.damage");
          PracticeSession active = ownSession(sender);
          active.damageDiagnostics.toggle(active.owner);
        }
        case "stats" -> {
          checkPerm(sender, "practice.stats");
          Player p = player(sender);
          for (PracticeMode mode : PracticeMode.values())
            sender.sendMessage(Messages.color(progress.summary(p.getUniqueId(), mode)));
          if (session != null && session.owner.equals(p))
            sender.sendMessage(Messages.color(
                "&7Current (Not Saved Yet): &f" + session.total.summary(session.totalFightTicks)));
        }
        case "kits" -> {
          checkPerm(sender, "practice.kits.list");
          kits.values()
              .forEach(k -> sender.sendMessage(Messages.color("&b" + k.name() + "&7: &f" + k.summary())));
        }
        case "difficulties" -> {
          checkPerm(sender, "practice.difficulties.list");
          difficulties
              .values()
              .forEach(
                  d ->
                      sender.sendMessage(Messages.color(
                          "&b" + d.prettyDisplayName()
                              + "&7: Reaction &f"
                              + d.decisionTicks()
                              + "t&7, Perception &f"
                              + d.perceptionTicks()
                              + "t&7, Aim Error &f"
                              + d.aimError()
                              + " Degrees&7; Vanilla Damage")));
        }
        case "kit" -> {
          if (args.length > 1 && args[1].equalsIgnoreCase("info")) {
            checkPerm(sender, "practice.kits.list");
            if (args.length < 3) throw new IllegalArgumentException(messages.get("commands.kit-info-usage"));
            Kit kit = kits.get(args[2].toLowerCase(Locale.ROOT));
            if (kit == null) throw new IllegalArgumentException(messages.get("kit.unknown-kit"));
            sender.sendMessage(Messages.color("&b" + kit.name() + "&7: &f" + kit.summary()));
          } else kitEditor.command(player(sender), args);
        }
        case "stop" -> {
          PracticeSession target = session;
          if (args.length > 1) {
            checkPerm(sender, "practice.stop.other");
            Player other = Bukkit.getPlayerExact(args[1]);
            target = other == null ? null : sessions.get(other.getUniqueId());
          } else {
            checkPerm(sender, "practice.stop");
          }
          if (target == null)
            throw new IllegalArgumentException(messages.get(args.length > 1 ? "session.no-active-match-other" : "session.no-active-match"));
          stop(target, messages.get("session.match-stopped"));
          if (args.length > 1 && sender != target.owner) {
            messages.send(sender, "session.match-stopped-player", "%player%", target.owner.getName());
          }
        }
        case "status" -> {
          checkPerm(sender, "practice.status");
          sender.sendMessage(Messages.color("&7Active Sessions: &f" + sessions.size() + " &7| Arenas: &f" + arenaNames()));
          sender.sendMessage(Messages.color(
              "&7Kits: &f"
                  + kits.keySet()
                  + " &7| Default: &f"
                  + getConfig().getString("session.default-kit")
                  + " &7/ &f"
                  + getConfig().getString("session.default-difficulty")));
          sender.sendMessage(Messages.color(
              session == null
                  ? "&7No Active Match."
                  : "&f" + session.owner.getName()
                      + " &7• Round &f"
                      + session.match.round()
                      + " &7• &a"
                      + session.match.playerWins()
                      + "&7:&c"
                      + session.match.botWins()));
          try {
            Arena a = arena();
            messages.send(sender, "arena.ready", "%radius%", String.valueOf((int) a.radius()));
          } catch (IllegalArgumentException e) {
            sender.sendMessage(Messages.color(e.getMessage()));
          }
        }
        case "reload" -> {
          checkPerm(sender, "practice.reload");
          if (!sessions.isEmpty())
            throw new IllegalArgumentException(messages.get("general.reload-busy-warning"));
          reloadConfig();
          loadSettings();
          messages.reload();
          dev.insanmiy.practiceplugin.papi.PapiHook.shutdown();
          dev.insanmiy.practiceplugin.papi.PapiHook.init(this);
          messages.send(sender, "general.plugin-reloaded");
        }
        case "arena" -> {
          checkPerm(sender, "practice.arena");
          if (args.length < 2)
            throw new IllegalArgumentException(messages.get("commands.arena-usage"));
          String arenaSub = args[1].toLowerCase(Locale.ROOT);
          switch (arenaSub) {
            case "list" -> {
              messages.send(sender, "arena.header");
              for (String name : arenaNames()) {
                String status;
                try {
                  Arena a = arena(name);
                  boolean busy = sessions.values().stream().anyMatch(s -> s.arenaName.equals(name));
                  status = busy ? messages.get("arena.status-in-use") : messages.get("arena.status-ready", "%radius%", String.valueOf((int) a.radius()));
                } catch (IllegalArgumentException e) {
                  status = messages.get("arena.status-incomplete", "%error%", e.getMessage());
                }
                messages.send(sender, "arena.entry", "%arena%", name, "%status%", status);
              }
            }
            case "delete" -> {
              if (args.length < 3) throw new IllegalArgumentException(messages.get("commands.arena-delete-usage"));
              String toDelete = validArenaName(args[2]);
              if (sessions.values().stream().anyMatch(s -> s.arenaName.equals(toDelete)))
                throw new IllegalArgumentException(messages.get("arena.cannot-delete-occupied"));
              if (toDelete.equals("default")) {
                getConfig().set("arena.player", null);
                getConfig().set("arena.bot", null);
              } else {
                getConfig().set("arenas." + toDelete, null);
              }
              saveConfig();
              messages.send(sender, "arena.deleted", "%arena%", toDelete);
            }
            case "tp" -> {
              if (!(sender instanceof Player p)) throw new IllegalArgumentException(messages.get("general.players-only"));
              String target = args.length > 2 ? validArenaName(args[2]) : "default";
              Arena a = arena(target);
              p.teleport(a.playerSpawn());
              messages.send(sender, "arena.teleported", "%arena%", target);
            }
            case "setradius" -> {
              if (args.length < 3)
                throw new IllegalArgumentException(messages.get("commands.arena-setradius-usage"));
              double r;
              try {
                r = Double.parseDouble(args[2]);
              } catch (NumberFormatException e) {
                throw new IllegalArgumentException(messages.get("general.number-format-error", "%error%", e.getMessage()));
              }
              if (!Double.isFinite(r) || r < 1)
                throw new IllegalArgumentException(messages.get("arena.radius-invalid"));
              double maxR = getConfig().getDouble("arena.max-radius", 0.0);
              if (maxR <= 0.0) {
                maxR = 500.0;
              }
              if (r > maxR) {
                throw new IllegalArgumentException(
                    messages.get("arena.radius-exceeds-max", "%radius%", String.valueOf(r), "%max%", String.valueOf(maxR)));
              }
              String targetArena = args.length > 3 ? validArenaName(args[3]) : "default";
              getConfig().set(arenaPath(targetArena) + ".radius", r);
              saveConfig();
              messages.send(sender, "arena.saved-radius", "%radius%", String.valueOf(r), "%arena%", targetArena);
            }
            case "info" -> {
              String arenaName = args.length > 2 ? validArenaName(args[2]) : "default";
              Arena a = arena(arenaName);
              Location pSp = a.playerSpawn();
              Location bSp = a.botSpawn();
              String pStr = String.format("%.1f, %.1f, %.1f", pSp.getX(), pSp.getY(), pSp.getZ());
              String bStr = String.format("%.1f, %.1f, %.1f", bSp.getX(), bSp.getY(), bSp.getZ());
              messages.send(sender, "arena.info", "%player%", pStr, "%bot%", bStr, "%radius%", String.valueOf(a.radius()));
            }
            case "setplayer", "setbot" -> {
              String arenaName = args.length > 2 ? validArenaName(args[2]) : "default";
              if (sessions.values().stream().anyMatch(active -> active.arenaName.equals(arenaName)))
                throw new IllegalArgumentException(messages.get("arena.occupied"));
              if (!(sender instanceof Player p)) throw new IllegalArgumentException(messages.get("general.players-only"));
              String point = arenaSub.equals("setplayer") ? "Player" : "Bot";
              if (!Arena.safe(p.getLocation()))
                throw new IllegalArgumentException(messages.get("arena.unsafe-spawn"));
              getConfig().set(arenaPath(arenaName) + "." + (point.toLowerCase(Locale.ROOT)), p.getLocation());
              saveConfig();
              messages.send(sender, "arena.saved-spawn", "%point%", point, "%arena%", arenaName);
            }
            default -> throw new IllegalArgumentException(messages.get("commands.arena-usage"));
          }
        }
        default -> messages.send(sender, "commands.usage");
      }
    } catch (NumberFormatException e) {
      messages.send(sender, "general.number-format-error", "%error%", e.getMessage());
    } catch (IllegalArgumentException e) {
      sender.sendMessage(Messages.color(e.getMessage().contains("\u00A7") || e.getMessage().contains("&") ? e.getMessage() : "&c" + e.getMessage()));
    } catch (Exception | LinkageError e) {
      getLogger().log(java.util.logging.Level.SEVERE, "Practice Command Failed", e);
      messages.send(sender, "general.internal-error");
    }
    return true;
  }

  private void checkPerm(CommandSender sender, String perm) {
    if (!sender.hasPermission(perm)
        && !sender.hasPermission("practice.admin")
        && !sender.hasPermission("practice.*")) {
      throw new IllegalArgumentException(messages.get("general.no-permission", "%permission%", perm));
    }
  }

  private static boolean hasPerm(CommandSender sender, String perm) {
    return sender.hasPermission(perm)
        || sender.hasPermission("practice.admin")
        || sender.hasPermission("practice.*");
  }

  private void admin(CommandSender sender) {
    if (!sender.hasPermission("practice.admin") && !sender.hasPermission("practice.*"))
      throw new IllegalArgumentException(messages.get("general.no-permission", "%permission%", "practice.admin"));
  }

  @Override
  public List<String> onTabComplete(
      @NotNull CommandSender s,
      @NotNull Command c,
      @NotNull String label,
      @NotNull String[] a) {
    a[0] = a[0].toLowerCase(Locale.ROOT);
    Collection<String> choices =
        switch (a.length) {
          case 1 -> {
            List<String> subs = new ArrayList<>();
            if (hasPerm(s, "practice.menu")) subs.add("menu");
            if (hasPerm(s, "practice.settings")) subs.add("settings");
            if (hasPerm(s, "practice.start")) subs.add("start");
            if (hasPerm(s, "practice.stop")) subs.add("stop");
            if (hasPerm(s, "practice.pause")) {
              subs.add("pause");
              subs.add("resume");
            }
            if (hasPerm(s, "practice.rematch")) subs.add("rematch");
            if (hasPerm(s, "practice.hud")) subs.add("hud");
            if (hasPerm(s, "practice.damage")) subs.add("damage");
            if (hasPerm(s, "practice.stats")) subs.add("stats");
            if (hasPerm(s, "practice.kits.list")) subs.add("kits");
            if (hasPerm(s, "practice.difficulties.list")) subs.add("difficulties");
            if (hasPerm(s, "practice.kits")
                || hasPerm(s, "practice.kits.*")
                || hasPerm(s, "practice.kits.editor")
                || hasPerm(s, "practice.kits.create")
                || hasPerm(s, "practice.kits.delete")
                || hasPerm(s, "practice.kits.import")
                || hasPerm(s, "practice.kits.list")) subs.add("kit");
            if (hasPerm(s, "practice.status")) subs.add("status");
            if (hasPerm(s, "practice.arena")) subs.add("arena");
            if (hasPerm(s, "practice.reload")) subs.add("reload");
            yield subs;
          }
          case 2 ->
              switch (a[0]) {
                case "start" -> kits.keySet();
                case "kit" -> {
                  List<String> kitSubs = new ArrayList<>();
                  if (hasPerm(s, "practice.kits.list")) kitSubs.add("info");
                  if (hasPerm(s, "practice.kits.editor")) kitSubs.addAll(List.of("editor", "edit"));
                  if (hasPerm(s, "practice.kits.create")) kitSubs.addAll(List.of("create", "copy", "save", "rename"));
                  if (hasPerm(s, "practice.kits.import")) kitSubs.add("import");
                  if (hasPerm(s, "practice.kits.delete")) kitSubs.add("delete");
                  yield kitSubs;
                }
                case "arena" ->
                    hasPerm(s, "practice.arena")
                        ? List.of("list", "setplayer", "setbot", "setradius", "info", "delete", "tp")
                        : List.of();
                case "stop" ->
                    hasPerm(s, "practice.stop.other")
                        ? Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()
                        : List.of();
                default -> List.of();
              };
          case 3 ->
              a[0].equals("arena")
                  ? (a[1].equalsIgnoreCase("setradius")
                      ? List.of("16", "24", "32", "64", "128", "256")
                      : a[1].equalsIgnoreCase("list") ? List.of() : arenaNames())
                  : a[0].equals("start")
                      ? difficulties.keySet()
                      : a[0].equals("kit")
                              && Set.of("info", "edit", "copy", "rename", "delete").contains(a[1])
                          ? kits.keySet()
                          : List.of();
          case 4 ->
              a[0].equals("arena") && a[1].equalsIgnoreCase("setradius")
                  ? arenaNames()
                  : a[0].equals("start")
                      ? Arrays.stream(PracticeMode.values()).map(PracticeMode::id).toList()
                      : a[0].equals("kit") && a[1].equals("create") ? kits.keySet() : List.of();
          case 5 -> a[0].equals("start") ? List.of("1", "3", "5", "7", "9", "11") : List.of();
          default -> List.of();
        };
    return choices.stream()
        .filter(v -> v.startsWith(a[a.length - 1].toLowerCase(Locale.ROOT)))
        .toList();
  }

  @EventHandler
  public void join(PlayerJoinEvent e) {
    if (isBot(e.getPlayer())) {
      e.joinMessage(null);
      return;
    }
    if (recovery != null && sessionFor(e.getPlayer()) == null)
      getServer()
          .getScheduler()
          .runTask(
              this,
              () -> {
                if (sessionFor(e.getPlayer()) == null) recovery.restore(e.getPlayer());
              });
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    PracticeSession session = sessionFor(e.getPlayer());
    if (isBot(e.getPlayer())) {
      e.quitMessage(null);
      return;
    }
    UUID uid = e.getPlayer().getUniqueId();
    lastSelections.remove(uid);
    if (menu != null) menu.cleanup(uid);
    if (participant(e.getPlayer())) stop(session, messages != null ? messages.get("session.player-disconnected") : "Player Disconnected.");
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void healingPressure(EntityDamageByEntityEvent event) {
    PracticeSession session = sessionFor(event.getEntity());
    if (session == null
        || !session.fighting()
        || session.bot == null
        || !event.getEntity().equals(session.bot.player())) return;

    session.bot.onIncomingDamage(event.getFinalDamage());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void resurrect(EntityResurrectEvent e) {
    PracticeSession session = sessionFor(e.getEntity());
    if (session == null
        || !session.fighting()
        || session.bot == null
        || !e.getEntity().equals(session.bot.player())) return;

    session.bot.onTotemPopped();
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void damage(EntityDamageEvent e) {
    Entity source = e instanceof EntityDamageByEntityEvent by ? by.getDamager() : null;
    if (source instanceof Projectile projectile
        && projectile.getShooter() instanceof Entity shooter) source = shooter;
    PracticeSession victimSession = sessionFor(e.getEntity());
    PracticeSession attackSession = sessionFor(source);
    if (victimSession == null) return;
    if (attackSession != null && attackSession != victimSession) {
      e.setCancelled(true);
      return;
    }
    if (!victimSession.fighting() || victimSession.endingRound) {
      e.setCancelled(true);
      return;
    }
    if (!(e.getEntity() instanceof Player damaged)) return;
    double amount = Math.clamp(e.getFinalDamage(), 0.0, damaged.getHealth());
    if (e.getFinalDamage() >= damaged.getHealth()) victimSession.finishingBlow = e;
    if (damaged.equals(victimSession.owner)) {
      victimSession.round.hurt(amount);
      victimSession.total.hurt(amount);
    } else {
      victimSession.round.hit(amount, victimSession.tick);
      victimSession.total.hit(amount, victimSession.tick);
      if (amount > 0 && source != null && source.equals(victimSession.owner)) {
        victimSession.bot.onOpponentHit();
      }
    }

  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void reportDamage(EntityDamageByEntityEvent e) {
    PracticeSession session = sessionFor(e.getEntity());
    if (session != null
        && e.getDamager() instanceof Player attacker
        && session.participant(attacker)
        && e.getEntity() instanceof Player target
        && participant(target)) session.damageDiagnostics.report(e, session);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void regain(EntityRegainHealthEvent e) {
    PracticeSession session = sessionFor(e.getEntity());
    if (e.getEntity() instanceof Player p
        && participant(p)
        && (!session.fighting()
            || !session.kit.regeneration()
                && e.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED))
      e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void swing(PlayerAnimationEvent e) {
    PracticeSession session = sessionFor(e.getPlayer());
    if (session != null
        && session.fighting()
        && e.getPlayer().equals(session.owner)
        && e.getAnimationType() == PlayerAnimationType.ARM_SWING) {
      session.round.swing();
      session.total.swing();
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void consume(PlayerItemConsumeEvent e) {
    PracticeSession session = sessionFor(e.getPlayer());
    if (session != null
        && session.fighting()
        && e.getPlayer().equals(session.owner)
        && e.getItem().getType() == Material.GOLDEN_APPLE) {
      session.round.apple();
      session.total.apple();
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void move(PlayerMoveEvent e) {
    PracticeSession session = sessionFor(e.getPlayer());
    if (session != null
        && !session.fighting()
        && !session.internalTeleport
        && !(e instanceof PlayerTeleportEvent)) {
      if (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getY() != e.getTo().getY() || e.getFrom().getZ() != e.getTo().getZ()) {
        Location to = e.getFrom().clone();
        to.setYaw(e.getTo().getYaw());
        to.setPitch(e.getTo().getPitch());
        e.setTo(to);
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void teleport(PlayerTeleportEvent e) {
    PracticeSession session = sessionFor(e.getPlayer());
    if (participant(e.getPlayer()) && !session.internalTeleport && !session.closing) {
      if (isBot(e.getPlayer()) && session.bot != null && session.bot.isTeleporting()) return;
      if (e.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
          || e.getCause() == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
        if (session.fighting() && session.bot != null && e.getPlayer().equals(session.owner)) {
          session.bot.onOpponentPearled(session.owner);
        }
        return;
      }
      getServer()
          .getScheduler()
          .runTask(
              this,
              () -> {
                if (sessions.get(session.owner.getUniqueId()) == session
                    && e.getPlayer().equals(session.owner))
                  session.followTeleport(session.owner.getLocation());
              });
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void respawn(PlayerRespawnEvent e) {
    PracticeSession session = sessionFor(e.getPlayer());
    if (session != null) {
      e.setRespawnLocation(session.arena.playerSpawn());
    } else {
      getServer()
          .getScheduler()
          .runTask(
              this,
              () -> {
                if (sessionFor(e.getPlayer()) == null && recovery.pending(e.getPlayer()))
                  recovery.restore(e.getPlayer());
              });
    }
  }

  @EventHandler
  public void gameMode(PlayerGameModeChangeEvent e) {
    if (participant(e.getPlayer()) && !isBot(e.getPlayer())) e.setCancelled(true);
  }

  @EventHandler
  public void drop(PlayerDropItemEvent e) {
    if (participant(e.getPlayer())) e.setCancelled(true);
  }

  @EventHandler
  public void pickup(EntityPickupItemEvent e) {
    if (e.getEntity() instanceof Player p && participant(p)) e.setCancelled(true);
  }

  private boolean practiceProjectile(Projectile p) {
    return p.getPersistentDataContainer().has(gearKey);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void launch(ProjectileLaunchEvent e) {
    if (!(e.getEntity().getShooter() instanceof Player shooter) || !participant(shooter)) return;
    PracticeSession session = sessionFor(shooter);
    session.projectiles.removeIf(p -> !p.isValid());
    if (!session.fighting() || session.projectiles.size() >= 128) {
      e.setCancelled(true);
      return;
    }
    e.getEntity()
        .getPersistentDataContainer()
        .set(gearKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
    e.getEntity().setPersistent(false);
    if (e.getEntity() instanceof AbstractArrow arrow)
      arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
    projectiles.add(e.getEntity());
    session.projectiles.add(e.getEntity());
    session.round.utility();
    session.total.utility();
  }

  @SuppressWarnings("UnstableApiUsage")
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void projectileHit(ProjectileHitEvent e) {
    if (!practiceProjectile(e.getEntity())) return;
    PracticeSession session = sessionFor(e.getEntity());

    if (session != null
        && session.fighting()
        && sessionFor(e.getHitEntity()) == session
        && (e.getEntity() instanceof EnderPearl && e.getHitEntity() instanceof WindCharge
            || e.getEntity() instanceof WindCharge && e.getHitEntity() instanceof EnderPearl)) {
      e.setCancelled(true);
      return;
    }
    if (session == null
        || !session.fighting()
        || e.getEntity() instanceof AbstractArrow && e.getHitBlock() != null
        || e.getHitEntity() instanceof Player p && !session.participant(p)) {
      e.setCancelled(true);
      e.getEntity().remove();
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void combustion(EntityCombustByEntityEvent e) {
    Entity source = e.getCombuster();
    if (source instanceof Projectile projectile
        && projectile.getShooter() instanceof Entity shooter) source = shooter;
    PracticeSession attacker = sessionFor(source), victim = sessionFor(e.getEntity());
    if ((attacker != null || victim != null) && (attacker != victim || !attacker.fighting()))
      e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void splash(PotionSplashEvent e) {
    PracticeSession session = sessionFor(e.getEntity());
    boolean ours = practiceProjectile(e.getEntity());
    if (ours && (session == null || !session.fighting())) {
      e.setCancelled(true);
      return;
    }
    for (LivingEntity entity : e.getAffectedEntities()) {
      boolean fighter = entity instanceof Player p && participant(p);
      if (ours ? sessionFor(entity) != session : fighter) e.setIntensity(entity, 0);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void experienceBottle(ExpBottleEvent e) {
    PracticeSession session = sessionFor(e.getEntity());
    if (!practiceProjectile(e.getEntity())) return;
    int xp = e.getExperience();
    e.setExperience(0);
    if (session != null
        && session.fighting()
        && e.getEntity().getShooter() instanceof Player p
        && participant(p)) {
      long before = MendingExperience.equippedDamage(p);
      MendingExperience.award(p, xp);
      if (session.bot != null && session.bot.player().equals(p))
        session.bot.onRepairImpact(MendingExperience.equippedDamage(p) < before);
    }
  }

  private static final Set<Material> DRINKABLES =
      Set.of(
          Material.POTION,
          Material.HONEY_BOTTLE,
          Material.MUSHROOM_STEW,
          Material.RABBIT_STEW,
          Material.BEETROOT_SOUP,
          Material.SUSPICIOUS_STEW);

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void potionBottle(PlayerItemConsumeEvent e) {
    PracticeSession session = sessionFor(e.getPlayer());
    if (session != null && DRINKABLES.contains(e.getItem().getType())) {
      getServer()
          .getScheduler()
          .runTask(
              this,
              () -> {
                if (sessions.get(session.owner.getUniqueId()) != session
                    || !participant(e.getPlayer())) return;
                var inventory = e.getPlayer().getInventory();
                for (int slot = 0; slot < 36; slot++) {
                  var remainder = inventory.getItem(slot);
                  if (remainder != null
                      && !remainder.hasItemMeta()
                      && (remainder.getType() == Material.BOWL
                          || remainder.getType() == Material.GLASS_BOTTLE))
                    inventory.setItem(slot, null);
                }
              });
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void pourWater(PlayerBucketEmptyEvent e) {
    PracticeSession session = sessionFor(e.getPlayer());
    if (protectedFrom(session, e.getBlock().getLocation())) {
      e.setCancelled(true);
      return;
    }
    if (!participant(e.getPlayer())) return;
    if (!session.fighting()
        || (e.getBucket() != Material.WATER_BUCKET && e.getBucket() != Material.LAVA_BUCKET)) {
      e.setCancelled(true);
      return;
    }
    tagBucketResult(e);
    try {
      Material fluid = e.getBucket() == Material.LAVA_BUCKET ? Material.LAVA : Material.WATER;
      if (!blocks.recordFluid(e.getBlock(), null, session.owner.getUniqueId(), fluid)){
        e.setCancelled(true);
        messages.send(e.getPlayer(), "session.water-journal-failed");
      }
    } catch (java.io.IOException failure) {
      e.setCancelled(true);
      getLogger().severe("Water Journal Failed: " + failure.getMessage());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void fillWater(PlayerBucketFillEvent e) {
    PracticeSession session = sessionFor(e.getPlayer());
    if (protectedFrom(session, e.getBlock().getLocation())
        || blocks.owns(e.getBlock())
            && (session == null
                || !session.owner.getUniqueId().equals(blocks.owner(e.getBlock())))){
      e.setCancelled(true);
      return;
    }
    if (!participant(e.getPlayer())){
      return;
    }
    if (!session.fighting()
        || e.getItemStack() == null
        || (e.getItemStack().getType() != Material.WATER_BUCKET
            && e.getItemStack().getType() != Material.LAVA_BUCKET)){
      e.setCancelled(true);
    }
    else {
      tagBucketResult(e);
    }
  }

  private void tagBucketResult(PlayerBucketEvent e) {
    if (e.getItemStack() == null) return;
    var item = e.getItemStack().clone();
    dev.insanmiy.practiceplugin.ItemMetadata.edit(item,
        meta ->
            meta.getPersistentDataContainer()
                .set(gearKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1));
    e.setItemStack(item);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void waterFlow(BlockFromToEvent e) {
    if (!blocks.owns(e.getBlock())){
      return;
    }
    PracticeSession source = sessions.get(blocks.owner(e.getBlock()));
    if (protectedFrom(source, e.getToBlock().getLocation())){
      e.setCancelled(true);
      return;
    }
    try {
      if (!blocks.recordFluid(e.getToBlock(), e.getBlock(), blocks.owner(e.getBlock()))){
        e.setCancelled(true);
      }
    } catch (java.io.IOException failure) {
      e.setCancelled(true);
      getLogger().severe("Water Flow Journal Failed: " + failure.getMessage());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void waterFreeze(BlockFormEvent e) {
    if (blocks.owns(e.getBlock())){
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void practiceIgnition(BlockIgniteEvent e) {
    var source = e.getIgnitingBlock();

    if (source != null && (blocks.owns(source) || blocks.fire(source))){
      e.setCancelled(true);
      return;
    }
    PracticeSession session = e.getPlayer() == null ? null : sessionFor(e.getPlayer());
    if (protectedFrom(session, e.getBlock().getLocation())){
      e.setCancelled(true);
      return;
    }
    if (session == null){
      return;
    }
    if (!session.fighting()){
      e.setCancelled(true);
      return;
    }
    try {
      if (!blocks.recordFire(e.getBlock(), session.owner.getUniqueId())){
        e.setCancelled(true);
      }
    } catch (java.io.IOException failure) {
      e.setCancelled(true);
      getLogger().severe("Fire Journal Failed: " + failure.getMessage());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void practiceFireSpread(BlockSpreadEvent e) {
    if (blocks.fire(e.getSource())){
      e.setCancelled(true);
    }
  }

  @EventHandler
  public void loadedEntities(org.bukkit.event.world.EntitiesLoadEvent e) {
    for (Entity entity : e.getEntities())
      if (entity instanceof Projectile p && practiceProjectile(p) && !projectiles.contains(p))
        p.remove();
  }

  @EventHandler
  public void extendPiston(BlockPistonExtendEvent e) {
    if (e.getBlocks().stream().anyMatch(blocks::owns)){
      e.setCancelled(true);
    }
  }

  @EventHandler
  public void retractPiston(BlockPistonRetractEvent e) {
    if (e.getBlocks().stream().anyMatch(blocks::owns)){
      e.setCancelled(true);
    }
  }

  @EventHandler
  public void burnWeb(BlockBurnEvent e) {
    if (blocks.owns(e.getBlock())
        || (e.getIgnitingBlock() != null && blocks.fire(e.getIgnitingBlock()))
        || protectedFrom(null, e.getBlock().getLocation())){
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void breakBlock(BlockBreakEvent e) {
    PracticeSession session = sessionFor(e.getPlayer());
    if (protectedFrom(session, e.getBlock().getLocation())
        || blocks.owns(e.getBlock())
            && (session == null || !session.owner.getUniqueId().equals(blocks.owner(e.getBlock())))){
      e.setCancelled(true);
      return;
    }
    if (dev.insanmiy.practiceplugin.model.BlockAccess.shouldCancel(
        participant(e.getPlayer()), session != null && session.fighting())){
      e.setCancelled(true);
      return;
    }
    if (participant(e.getPlayer())) {

      e.setDropItems(false);
      e.setExpToDrop(0);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void placeBlock(BlockPlaceEvent e) {
    PracticeSession session = sessionFor(e.getPlayer());
    if (protectedFrom(session, e.getBlock().getLocation())){
      e.setCancelled(true);
      return;
    }
    if (dev.insanmiy.practiceplugin.model.BlockAccess.shouldCancel(
        participant(e.getPlayer()), session != null && session.fighting())){
      e.setCancelled(true);
      return;
    }
    if (participant(e.getPlayer()) && session.fighting()){
      try {
        if (blocks.record(e.getBlock(), e.getBlockReplacedState(), session.owner.getUniqueId())){
          return;
        }
      } catch (java.io.IOException failure) {
        getLogger().severe("Block Placement Cancelled: Journal Could Not Be Saved.");
      }
      e.setCancelled(true);
      messages.send(e.getPlayer(), "session.block-journal-failed");
    }
  }

  private boolean inArena(Location l) {
    return sessions.values().stream().anyMatch(session -> session.arena.contains(l));
  }

  private boolean protectedFrom(PracticeSession actor, Location location) {
    return sessions.values().stream().anyMatch(s -> s != actor && s.arena.contains(location));
  }

  @EventHandler
  public void explode(EntityExplodeEvent e) {
    e.blockList().removeIf(b -> inArena(b.getLocation()) || blocks.owns(b));
  }

  @EventHandler
  public void explodeBlock(BlockExplodeEvent e) {
    e.blockList().removeIf(b -> inArena(b.getLocation()) || blocks.owns(b));
  }

  @SuppressWarnings("UnstableApiUsage")
  @EventHandler(priority = EventPriority.HIGH)
  public void interact(PlayerInteractEvent e) {
    Player player = e.getPlayer();
    PracticeSession session = sessionFor(player);
    if (session == null || !participant(player)) return;

    if (Kit.isVersionAtLeast1_21()
        && (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)
        && e.getItem() != null
        && e.getItem().getType() == Material.WIND_CHARGE) {
      if (session.fighting()) {
        if (!player.hasCooldown(Material.WIND_CHARGE)) {
          try {
            WindCharge charge = BotAdapters.getAdapter().launchWindCharge(player);
            if (charge != null) {
              player.setCooldown(Material.WIND_CHARGE, 10);
              try {
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WIND_CHARGE_THROW, 0.5f, 1.0f);
              } catch (Exception | LinkageError ignored) {}
              if (e.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND) player.swingOffHand(); else player.swingMainHand();
              charge
                  .getPersistentDataContainer()
                  .set(gearKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
              charge.setPersistent(false);
              projectiles.add(charge);
              session.projectiles.add(charge);
              session.round.utility();
              session.total.utility();
              if (player.getGameMode() != GameMode.CREATIVE) { e.getItem().subtract(1); player.updateInventory(); }
            }
          } catch (Exception ex) {
            getLogger().fine("Wind charge launch error: " + ex.getMessage());
          }
        }
      }
      e.setUseItemInHand(Event.Result.DENY);
      e.setUseInteractedBlock(Event.Result.DENY);
      e.setCancelled(true);
      return;
    }

    if (e.getClickedBlock() != null && protectedFrom(session, e.getClickedBlock().getLocation())) {
      e.setUseInteractedBlock(Event.Result.DENY);
      e.setUseItemInHand(Event.Result.DENY);
      return;
    }
    if (!session.fighting()) {
      e.setUseInteractedBlock(Event.Result.DENY);
      e.setUseItemInHand(Event.Result.DENY);
    }
  }

  @EventHandler
  public void interactEntity(PlayerInteractEntityEvent e) {
    if (participant(e.getPlayer())) e.setCancelled(true);
  }

  @EventHandler
  public void inventory(InventoryClickEvent e) {
    PracticeSession session = sessionFor(e.getWhoClicked());
    if (e.getWhoClicked() instanceof Player p
        && participant(p)
        && (session != null && session.closing
            || e.getView().getTopInventory().getType() != InventoryType.CRAFTING
            || e.getRawSlot() < 5
            || e.getAction() == InventoryAction.DROP_ALL_SLOT
            || e.getAction() == InventoryAction.DROP_ONE_SLOT
            || e.getAction() == InventoryAction.DROP_ALL_CURSOR
            || e.getAction() == InventoryAction.DROP_ONE_CURSOR)) e.setCancelled(true);
  }

  @EventHandler
  public void drag(InventoryDragEvent e) {
    if (e.getWhoClicked() instanceof Player p && participant(p)) {
      PracticeSession session = sessionFor(p);
      if (session != null
          && (session.closing
              || e.getView().getTopInventory().getType() != InventoryType.CRAFTING
              || e.getRawSlots().stream().anyMatch(slot -> slot < 5))) {
        e.setCancelled(true);
      }
    }
  }

  @EventHandler
  public void open(InventoryOpenEvent e) {
    if (e.getPlayer() instanceof Player p && participant(p)) e.setCancelled(true);
  }

  @EventHandler
  public void target(EntityTargetLivingEntityEvent e) {
    if (e.getTarget() instanceof Player p && participant(p)) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void death(PlayerDeathEvent e) {
    PracticeSession session = sessionFor(e.getEntity());
    if (participant(e.getEntity())) {
      e.getDrops().clear();
      e.setDroppedExp(0);
      e.deathMessage(null);
      if (session != null && session.fighting() && !session.endingRound) {
        session.captureLayout();
        boolean playerWon = !e.getEntity().equals(session.owner);

        boolean revived = ServerFeatures.revive(e);
        e.setKeepInventory(true);
        e.setKeepLevel(true);
        session.endingRound = true;
        messages.send(session.owner, "combat.lethal-hit", "%target%", playerWon ? "Bot" : "You");
        getServer()
            .getScheduler()
            .runTask(
                this,
                () -> {
                  if (sessions.get(session.owner.getUniqueId()) != session) return;
                  if (!revived) {
                    if (playerWon) session.replaceDeadBot();
                    else session.owner.spigot().respawn();
                  }
                  session.endingRound = false;
                  session.win(playerWon);
                });
        return;
      }
      getServer()
          .getScheduler()
          .runTask(this, () -> stop(session, "Unexpected Player Death; Practice Aborted."));
    }
  }
}
