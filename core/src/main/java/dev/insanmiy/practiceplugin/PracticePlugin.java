package dev.insanmiy.practiceplugin;

import dev.insanmiy.practiceplugin.bot.BotAdapters;
import dev.insanmiy.practiceplugin.config.*;
import dev.insanmiy.practiceplugin.config.Difficulty;
import dev.insanmiy.practiceplugin.model.PracticeMode;
import java.io.File;
import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;

public final class PracticePlugin extends JavaPlugin {
  private final Map<String, Kit> kits = new LinkedHashMap<>();
  private final Map<String, Difficulty> difficulties = new LinkedHashMap<>();
  private NamespacedKey gearKey, slotKey;
  private KitLayoutStore layouts;
  private RecoveryStore recovery;
  private final SessionRegistry<PracticeSession> sessions = new SessionRegistry<>();
  private SettingsMenu settingsMenu;
  private PlayerSettings settings;
  private ProgressStore progress;
  private PracticeMenu menu;
  private KitEditor kitEditor;
  private TemporaryBlocks blocks;
  private PracticeClouds clouds;
  private final Set<Projectile> projectiles = new HashSet<>();
  private Messages messages;
  private SnapshotWriter saves;

  public Messages messages() {
    return messages;
  }

  void clearUtilities(PracticeSession session) {
    CleanupSteps cleanup = cleanup();
    UUID owner = session.owner.getUniqueId();
    for (Projectile projectile : List.copyOf(session.projectiles)) {
      cleanup.run("Could Not Remove A Session Projectile", projectile::remove);
      projectiles.remove(projectile);
    }
    session.projectiles.clear();
    cleanup.run("Could Not Remove Session Clouds", () -> clouds.clear(owner));
    cleanup.run("Could Not Restore Session Blocks", () -> blocks.restoreOwner(owner));
  }

  void clearUtilities() {
    CleanupSteps cleanup = cleanup();
    for (PracticeSession session : sessions.values()) session.projectiles.clear();
    for (Projectile projectile : List.copyOf(projectiles))
      cleanup.run("Could Not Remove A Practice Projectile", projectile::remove);
    projectiles.clear();
    if (clouds != null) cleanup.run("Could Not Remove Practice Clouds", clouds::clearAll);
    if (blocks != null) cleanup.run("Could Not Restore Practice Blocks", () -> {
      if (!blocks.restoreAll()) getLogger().warning("Block Recovery Retained For An Unloaded World.");
    });
  }

  private final Map<UUID, Selection> lastSelections = new HashMap<>();

  public PracticeSession sessionFor(Entity entity) {
    if (entity instanceof Projectile p)
      return p.getShooter() instanceof Entity shooter ? sessionFor(shooter) : null;
    if (entity instanceof AreaEffectCloud cloud) {
      UUID owner = clouds == null ? null : clouds.owner(cloud);
      return owner == null ? null : sessions.get(owner);
    }
    if (!(entity instanceof Player p)) return null;
    return sessions.participant(p.getUniqueId());
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
    throw new IllegalArgumentException(messages != null ? messages.get("session.no-free-bot-names") : "No Free Bot Names Available.");
  }

  String validArenaName(String name) {
    String normalized = name.toLowerCase(Locale.ROOT);
    if (!normalized.matches("[a-z0-9_-]{1,32}"))
      throw new IllegalArgumentException(messages != null ? messages.get("arena.name-invalid") : "Arena Names: 1 To 32 Letters, Digits, Hyphen Or Underscore.");
    return normalized;
  }

  String arenaPath(String name) {
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
    ArenaPool.Selection selection = ArenaPool.select(arenaNames(), this::arena, occupiedArenas());
    if (selection.arena() != null) return selection.arena();
    throw new IllegalArgumentException(
        messages.get("arena.no-free-arena", "%errors%", String.join("; ", selection.errors())));
  }

  Map<String, Arena> occupiedArenas() {
    Map<String, Arena> occupied = new LinkedHashMap<>();
    for (PracticeSession session : sessions.values()) occupied.put(session.arenaName, session.arena);
    return occupied;
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
    clouds = new PracticeClouds(gearKey);
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
    saves = new SnapshotWriter((file, failure) -> getLogger().log(
        java.util.logging.Level.SEVERE, "Could Not Save " + file.getFileName() + "; Will Retry", failure));
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
        if ((entity instanceof Projectile || entity instanceof AreaEffectCloud)
            && entity.getPersistentDataContainer().has(gearKey))
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
    getServer().getPluginManager().registerEvents(new PracticeProtection(this), this);
    getServer().getScheduler().runTaskTimer(this, new MenuAnimation(this), 6, 6);
    PluginCommand practiceCmd = Objects.requireNonNull(getCommand("practice"));
    PracticeCommands commands = new PracticeCommands(this);
    practiceCmd.setExecutor(commands);
    practiceCmd.setTabCompleter(commands);
    Bukkit.getOnlinePlayers().forEach(recovery::restore);
    getServer()
        .getScheduler()
        .runTaskTimer(
            this,
            () -> {
              projectiles.removeIf(p -> !p.isValid());
              clouds.prune();
              for (PracticeSession session : List.copyOf(sessions.values())) {
                session.projectiles.removeIf(p -> !p.isValid());
                try {
                  session.tick();
                } catch (Exception | LinkageError e) {
                  getLogger().log(java.util.logging.Level.SEVERE, "Practice Session Failed", e);
                  stop(session, messages != null ? messages.get("session.session-stopped-error") : "Session Stopped After An Internal Error.");
                }
              }
            },
            1,
            1);
    if (Boolean.getBoolean("practice.compatibility-test"))
      getServer().getScheduler().runTask(this, () -> new CompatibilityChecks(this).start());
  }

  @Override
  public void onDisable() {
    CleanupSteps cleanup = cleanup();
    cleanup.run("Could Not Cancel Practice Tasks", () -> getServer().getScheduler().cancelTasks(this));
    cleanup.run("Could Not Unregister Practice Listeners", () -> org.bukkit.event.HandlerList.unregisterAll(this));
    cleanup.run("Could Not Stop Placeholders", dev.insanmiy.practiceplugin.papi.PapiHook::shutdown);
    for (PracticeSession session : List.copyOf(sessions.values()))
      cleanup.run("Could Not Stop A Practice Session", () -> stop(session, null));
    cleanup.run("Could Not Clear Practice Utilities", this::clearUtilities);
    if (settingsMenu != null) cleanup.run("Could Not Close Settings Menus", settingsMenu::closeAll);
    if (menu != null) cleanup.run("Could Not Close Practice Menus", menu::closeAll);
    if (kitEditor != null) cleanup.run("Could Not Close Kit Editors", kitEditor::closeAll);
    if (progress != null) cleanup.run("Could Not Queue Practice Stats", progress::save);
    if (blocks != null) cleanup.run("Could Not Save Block Journal", blocks::flush);
    if (saves != null) cleanup.run("Could Not Flush Practice Saves", saves::close);
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

  void unregisterBot(UUID id) { sessions.unregisterBot(id); }

  void registerBot(UUID id, PracticeSession session) {
    sessions.registerBot(id, session);
  }

  boolean isBot(Player p) {
    return sessions.isBot(p.getUniqueId());
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
      sessions.remove(old.owner.getUniqueId(), old);
      if (message != null && !message.isEmpty())
        cleanup().run("Could Not Send Session Result", () -> old.owner.sendMessage(Messages.color(message)));
    }
  }

  void loadSettings() {
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

  void start(Player p, Selection choice) throws java.io.IOException {
    if (sessions.containsKey(p.getUniqueId()))
      throw new IllegalArgumentException(messages.get("session.already-in-match"));
    if (recovery.pending(p)) {
      recovery.restore(p);
      if (recovery.pending(p))
        throw new IllegalArgumentException(messages.get("session.recovery-pending"));
    }
    if (p.isDead())
      throw new IllegalArgumentException(messages.get("session.respawn-before-practice"));
    Kit kit = kits.get(choice.kit());
    Difficulty difficulty = difficulties.get(choice.difficulty());
    if (kit == null || difficulty == null)
      throw new IllegalArgumentException(messages.get("session.unknown-selection"));
    if (!PracticeCommands.hasPerm(p, "practice.start"))
      throw new IllegalArgumentException(messages.get("general.no-permission", "%permission%", "practice.start"));
    Map.Entry<String, Arena> assigned = availableArena();
    PracticeSession session =
        new PracticeSession(
            this, p, assigned.getValue(), kit, difficulty, choice.mode(), choice.bestOf());
    session.arenaName = assigned.getKey();
    recovery.capture(p);
    sessions.put(p.getUniqueId(), session);
    try {
      p.closeInventory();
      if (p.isInsideVehicle() && !p.leaveVehicle())
        throw new IllegalStateException("Could Not Leave Vehicle For Practice");
      p.setInvulnerable(false);
      p.getInventory().clear();
      p.setItemOnCursor(null);
      session.internalGameMode = true;
      try {
        p.setGameMode(GameMode.SURVIVAL);
      } finally {
        session.internalGameMode = false;
      }
      p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
      p.setAbsorptionAmount(0);
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

  PracticeSession ownSession(CommandSender sender) {
    if (!(sender instanceof Player p)) throw new IllegalArgumentException(messages.get("general.players-only"));
    PracticeSession session = sessions.get(p.getUniqueId());
    if (session == null) throw new IllegalArgumentException(messages.get("session.no-active-match"));
    return session;
  }

  Player player(CommandSender sender) {
    if (!(sender instanceof Player p)) throw new IllegalArgumentException(messages.get("general.players-only"));
    return p;
  }

  Arena arena() {
    return arena("default");
  }

  Arena arena(String name) {
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


  SessionRegistry<PracticeSession> sessions() { return sessions; }
  SettingsMenu settingsMenu() { return settingsMenu; }
  PracticeMenu menu() { return menu; }
  KitEditor kitEditor() { return kitEditor; }
  TemporaryBlocks blocks() { return blocks; }
  PracticeClouds clouds() { return clouds; }
  Set<Projectile> projectiles() { return projectiles; }
  Map<UUID, Selection> lastSelections() { return lastSelections; }
  SnapshotWriter saves() { return saves; }

  CleanupSteps cleanup() {
    return new CleanupSteps((description, failure) -> getLogger().log(
        java.util.logging.Level.SEVERE, description + "; Recovery Records Retained Where Needed", failure));
  }
}
