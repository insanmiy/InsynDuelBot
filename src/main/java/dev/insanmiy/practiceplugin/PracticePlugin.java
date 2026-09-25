package dev.insanmiy.practiceplugin;

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
  private TemporaryWebs webs;
  private TemporaryWater water;
  private final Set<Projectile> projectiles = new HashSet<>();

  void clearUtilities(PracticeSession session) {
    UUID owner = session.owner.getUniqueId();
    projectiles.removeIf(
        p -> {
          if (sessionFor(p) != session) return false;
          p.remove();
          return true;
        });
    try {
      water.restoreOwner(owner);
      webs.restoreOwner(owner);
      water.restoreOwner(owner);
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Could not save session cleanup", e);
    }
  }

  void clearUtilities() {
    projectiles.forEach(Entity::remove);
    projectiles.clear();
    if (water != null) {
      try {
        water.restoreAll();
      } catch (java.io.IOException e) {
        throw new IllegalStateException("Could not save water cleanup journal", e);
      }
    }
    if (webs != null) {
      try {
        if (!webs.restoreAll()) getLogger().warning("Web recovery retained for an unloaded world.");
      } catch (java.io.IOException e) {
        throw new IllegalStateException("Could not save web cleanup journal", e);
      }
    }
    if (water != null) {
      try {
        if (!water.restoreAll())
          getLogger().warning("Water recovery retained for an unloaded world.");
      } catch (java.io.IOException e) {
        throw new IllegalStateException("Could not finish water cleanup", e);
      }
    }
  }

  private final Map<UUID, Selection> lastSelections = new HashMap<>();

  PracticeSession sessionFor(Entity entity) {
    if (entity instanceof Projectile p)
      return p.getShooter() instanceof Entity shooter ? sessionFor(shooter) : null;
    if (!(entity instanceof Player p)) return null;
    PracticeSession own = sessions.get(p.getUniqueId());
    if (own != null) return own;
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
    throw new IllegalArgumentException("No free bot names.");
  }

  static String validArenaName(String name) {
    String normalized = name.toLowerCase(Locale.ROOT);
    if (!normalized.matches("[a-z0-9_-]{1,32}"))
      throw new IllegalArgumentException("Arena names: 1-32 letters, digits, - or _.");
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
        "No free, non-overlapping arena. "
            + String.join("; ", errors)
            + " Admins: /practice arena setplayer <name> and setbot <name>.");
  }

  ProgressStore progress() {
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

  List<String> startProblems(Player p) {
    List<String> problems = new ArrayList<>();
    if (sessions.containsKey(p.getUniqueId())) problems.add("Your practice is already active");
    try {
      availableArena();
    } catch (IllegalArgumentException e) {
      problems.add(e.getMessage());
    }
    if (p.getGameMode() != GameMode.SURVIVAL) problems.add("Switch to Survival");
    if (p.isInsideVehicle()) problems.add("Dismount first");
    if (p.isDead()) problems.add("Respawn first");
    if (p.isInvulnerable()) problems.add("Disable invulnerability");
    if (!p.getActivePotionEffects().isEmpty() || p.getAbsorptionAmount() > 0)
      problems.add("Clear potion effects / absorption");
    if (Arrays.stream(p.getInventory().getContents())
            .anyMatch(i -> i != null && !i.getType().isAir())
        || !p.getItemOnCursor().getType().isAir())
      problems.add("Empty inventory, armor and offhand");
    if (!p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getModifiers().isEmpty())
      problems.add("Remove external max-health modifiers");

    if (recovery.pending(p)) problems.add("Recovery pending; start will retry it");
    return problems;
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
    String defaultKit = getConfig().getString("session.default-kit", "modern-diamond-sword");
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
        PracticeMode.parse(getConfig().getString("session.default-mode", "endless")),
        getConfig().getInt("session.best-of", 5));
  }

  @Override
  public void onEnable() {
    String serverVersion = Bukkit.getBukkitVersion().split("-")[0];
    if (!serverVersion.equals(getDescription().getAPIVersion())) {
      getLogger()
          .severe(
              "This plugin build targets Minecraft " + getDescription().getAPIVersion()
                  + "; this server is " + serverVersion);
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
      getLogger().log(java.util.logging.Level.SEVERE, "Could not upgrade configuration safely", e);
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    progress = new ProgressStore(this);
    webs = new TemporaryWebs(this);
    water = new TemporaryWater(this, webs);
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
    Objects.requireNonNull(getCommand("practice")).setExecutor(this);
    getCommand("practice").setTabCompleter(this);
    Bukkit.getOnlinePlayers().forEach(recovery::restore);
    getServer()
        .getScheduler()
        .runTaskTimer(
            this,
            () -> {
              try {
                webs.tick();
                water.tick();
              } catch (java.io.IOException e) {
                getLogger().severe("Web cleanup journal failed: " + e.getMessage());
              }
              projectiles.removeIf(p -> !p.isValid());
              for (PracticeSession session : List.copyOf(sessions.values())) {
                try {
                  session.tick();
                } catch (Exception | LinkageError e) {
                  getLogger().log(java.util.logging.Level.SEVERE, "Practice session failed", e);
                  stop(session, "Session stopped after an internal error.");
                }
              }
            },
            1,
            1);
  }

  @Override
  public void onDisable() {
    for (PracticeSession session : List.copyOf(sessions.values())) stop(session, "Plugin stopped.");
    clearUtilities();
    if (settingsMenu != null) settingsMenu.closeAll();
    if (menu != null) menu.closeAll();
    if (kitEditor != null) kitEditor.closeAll();
    if (progress != null) progress.save();
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
      old.owner.sendMessage(TextUI.legacy(Component.text(message)));
    } catch (Exception e) {
      getLogger()
          .log(java.util.logging.Level.SEVERE, "Cleanup failed; recovery record retained", e);
    } finally {
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
          getLogger().warning("Kit '" + key + "' disabled: " + e.getMessage());
        }
    ConfigurationSection custom =
        YamlConfiguration.loadConfiguration(new File(getDataFolder(), "customkits.yml"));
    for (String key : custom.getKeys(false)) {
      try {
        kits.put(key, Kit.read(key, Objects.requireNonNull(custom.getConfigurationSection(key))));
      } catch (Exception e) {
        getLogger().warning("Custom kit '" + key + "' disabled: " + e.getMessage());
      }
    }
    root = getConfig().getConfigurationSection("difficulties");
    if (root != null)
      for (String key : root.getKeys(false))
        try {
          difficulties.put(
              key, Difficulty.read(key, Objects.requireNonNull(root.getConfigurationSection(key))));
        } catch (Exception e) {
          getLogger().warning("Difficulty '" + key + "' disabled: " + e.getMessage());
        }
    String name = getConfig().getString("bot-name", "PracticeBot");
    if (!name.matches("[A-Za-z0-9_]{1,16}")) {
      getConfig().set("bot-name", "PracticeBot");
      getLogger().warning("Invalid bot-name; using PracticeBot");
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
      PracticeMode.parse(getConfig().getString("session.default-mode", "endless"));
    } catch (IllegalArgumentException e) {
      getConfig().set("session.default-mode", "endless");
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

  private void start(Player p, Selection choice) throws java.io.IOException {
    if (sessions.containsKey(p.getUniqueId()))
      throw new IllegalArgumentException(
          "Your practice is already active. Use /practice stop first.");
    p.closeInventory();
    if (recovery.pending(p)) {
      recovery.restore(p);
      if (recovery.pending(p))
        throw new IllegalArgumentException("An interrupted session must finish recovery first.");
    }
    if (p.getGameMode() != GameMode.SURVIVAL
        || !p.getActivePotionEffects().isEmpty()
        || p.getAbsorptionAmount() > 0
        || p.isInsideVehicle()
        || p.isDead())
      throw new IllegalArgumentException(
          "Enter Survival, dismount, and clear active effects/absorption first.");
    if (Arrays.stream(p.getInventory().getContents())
            .anyMatch(i -> i != null && !i.getType().isAir())
        || !p.getItemOnCursor().getType().isAir())
      throw new IllegalArgumentException("Empty inventory, armor, off-hand, and cursor first.");
    if (!p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getModifiers().isEmpty())
      throw new IllegalArgumentException("Remove external maximum-health modifiers first.");
    if (p.isInvulnerable())
      throw new IllegalArgumentException("Disable invulnerability before practicing.");
    Kit kit = kits.get(choice.kit());
    Difficulty difficulty = difficulties.get(choice.difficulty());
    if (kit == null || difficulty == null)
      throw new IllegalArgumentException(
          "Unknown kit/difficulty. Use /practice kits or /practice difficulties.");
    Map.Entry<String, Arena> assigned = availableArena();
    PracticeSession session =
        new PracticeSession(
            this, p, assigned.getValue(), kit, difficulty, choice.mode(), choice.bestOf());
    session.arenaName = assigned.getKey();
    recovery.capture(p);
    sessions.put(p.getUniqueId(), session);
    try {
      session.begin();
      if (!dev.insanmiy.practiceplugin.bot.NmsBot.criticalsEnabled(p))
        p.sendMessage(TextUI.legacy(
            Component.text(
                "Warning: this world's Paper configuration disables player critical hits. The bot"
                    + " respects that setting.")));
    } catch (Exception | LinkageError e) {
      stop(session, "Could not start the bot.");
      throw e;
    }
    lastSelections.put(p.getUniqueId(), choice);
    p.sendMessage(TextUI.legacy(
        Component.text("Arena: " + session.arenaName + " | Drill: " + session.options.drill())));
    p.sendMessage(TextUI.legacy(
        Component.text(
            "Starting "
                + kit.name()
                + " / "
                + difficulty.name()
                + " / "
                + choice.mode().id()
                + ". Both receive "
                + kit.summary()
                + ". Use /practice stop to finish.")));
  }

  private PracticeSession ownSession(CommandSender sender) {
    PracticeSession session = sender instanceof Player p ? sessions.get(p.getUniqueId()) : null;
    if (session == null) throw new IllegalArgumentException("No active practice.");
    if (!(sender instanceof Player p) || !p.equals(session.owner))
      throw new IllegalArgumentException("This is not your practice session.");
    return session;
  }

  private static Player player(CommandSender sender) {
    if (!(sender instanceof Player p)) throw new IllegalArgumentException("Players only.");
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
      throw new IllegalArgumentException("Set both spawns: /practice arena setplayer and setbot.");
    double radius = getConfig().getDouble(path + ".radius", 16);
    double maxRadius = getConfig().getDouble("arena.max-radius", 0.0);
    if (maxRadius > 0 && radius > maxRadius)
      throw new IllegalArgumentException(
          "Arena radius (" + radius + ") exceeds configured max-radius (" + maxRadius + ").");
    Arena arena = new Arena(player, bot, radius);
    if (arena.error() != null) throw new IllegalArgumentException(arena.error());
    return arena;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    try {
      PracticeSession session = sender instanceof Player p ? sessions.get(p.getUniqueId()) : null;
      String sub =
          args.length == 0
              ? (sender instanceof Player ? "menu" : "help")
              : args[0].toLowerCase(Locale.ROOT);
      switch (sub) {
        case "settings" -> settingsMenu.open(player(sender));
        case "menu" -> {
          Player p = player(sender);
          if (session != null)
            throw new IllegalArgumentException("Use /practice stop before selecting another kit.");
          menu.open(p, defaults(p), 0);
        }
        case "start" -> {
          Player p = player(sender);
          Selection d = defaults(p);
          start(
              p,
              new Selection(
                  args.length > 1 ? args[1] : d.kit(),
                  args.length > 2 ? args[2] : d.difficulty(),
                  args.length > 3 ? PracticeMode.parse(args[3]) : d.mode(),
                  args.length > 4 ? Integer.parseInt(args[4]) : d.bestOf()));
        }
        case "rematch" -> {
          Player p = player(sender);
          Selection previous = lastSelections.get(p.getUniqueId());
          if (previous == null)
            throw new IllegalArgumentException("Start a practice session first.");
          start(p, previous);
        }
        case "pause" -> {
          if (ownSession(sender).endingRound)
            throw new IllegalArgumentException("Wait for the round result.");
          ownSession(sender).setPaused(true);
        }
        case "resume" -> ownSession(sender).setPaused(false);
        case "hud" -> ownSession(sender).toggleHud();
        case "damage" -> {
          PracticeSession active = ownSession(sender);
          active.damageDiagnostics.toggle(active.owner);
        }
        case "stats" -> {
          Player p = player(sender);
          for (PracticeMode mode : PracticeMode.values())
            sender.sendMessage(TextUI.legacy(Component.text(progress.summary(p.getUniqueId(), mode))));
          if (session != null && session.owner.equals(p))
            sender.sendMessage(TextUI.legacy(
                Component.text(
                    "Current (not saved yet): " + session.total.summary(session.totalFightTicks))));
        }
        case "kits" ->
            kits.values()
                .forEach(k -> sender.sendMessage(TextUI.legacy(Component.text(k.name() + ": " + k.summary()))));
        case "difficulties" ->
            difficulties
                .values()
                .forEach(
                    d ->
                        sender.sendMessage(TextUI.legacy(
                            Component.text(
                                d.name()
                                    + ": reaction "
                                    + d.decisionTicks()
                                    + "t, perception "
                                    + d.perceptionTicks()
                                    + "t, aim error "
                                    + d.aimError()
                                    + " degrees; vanilla damage"))));
        case "kit" -> {
          if (args.length > 1 && args[1].equalsIgnoreCase("info")) {
            if (args.length < 3) throw new IllegalArgumentException("/practice kit info <name>");
            Kit kit = kits.get(args[2].toLowerCase(Locale.ROOT));
            if (kit == null) throw new IllegalArgumentException("Unknown kit.");
            sender.sendMessage(TextUI.legacy(Component.text(kit.name() + ": " + kit.summary())));
          } else kitEditor.command(player(sender), args);
        }
        case "stop" -> {
          PracticeSession target = session;
          if (args.length > 1) {
            admin(sender);
            Player other = Bukkit.getPlayerExact(args[1]);
            target = other == null ? null : sessions.get(other.getUniqueId());
          }
          if (target == null)
            throw new IllegalArgumentException("No active practice for that player.");
          stop(target, "Practice stopped.");
        }
        case "status" -> {
          sender.sendMessage(TextUI.legacy(
              Component.text("Active sessions: " + sessions.size() + " | Arenas: " + arenaNames())));
          sender.sendMessage(TextUI.legacy(
              Component.text(
                  "Kits: "
                      + kits.keySet()
                      + " | default: "
                      + getConfig().getString("session.default-kit")
                      + " / "
                      + getConfig().getString("session.default-difficulty"))));
          sender.sendMessage(TextUI.legacy(
              Component.text(
                  session == null
                      ? "No active match."
                      : session.owner.getName()
                          + " • round "
                          + session.match.round()
                          + " • "
                          + session.match.playerWins()
                          + ":"
                          + session.match.botWins())));
          try {
            arena();
            sender.sendMessage(TextUI.legacy(Component.text("Arena ready.")));
          } catch (IllegalArgumentException e) {
            sender.sendMessage(TextUI.legacy(Component.text(e.getMessage())));
          }
        }
        case "reload" -> {
          admin(sender);
          if (!sessions.isEmpty())
            throw new IllegalArgumentException("Stop all matches before reloading settings.");
          reloadConfig();
          loadSettings();
          sender.sendMessage(TextUI.legacy(Component.text("Practice settings reloaded.")));
        }
        case "arena" -> {
          admin(sender);
          if (args.length < 2)
            throw new IllegalArgumentException("/practice arena <setplayer|setbot|setradius|info> [name]");
          if (args[1].equalsIgnoreCase("setradius")) {
            if (args.length < 3)
              throw new IllegalArgumentException("/practice arena setradius <radius> [name]");
            double r;
            try {
              r = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
              throw new IllegalArgumentException("Radius must be a number.");
            }
            if (!Double.isFinite(r) || r < 1)
              throw new IllegalArgumentException("Radius must be at least 1 block.");
            double maxR = getConfig().getDouble("arena.max-radius", 0.0);
            if (maxR > 0 && r > maxR)
              throw new IllegalArgumentException(
                  "Radius (" + r + ") exceeds configured max-radius (" + maxR + ").");
            String targetArena = args.length > 3 ? validArenaName(args[3]) : "default";
            getConfig().set(arenaPath(targetArena) + ".radius", r);
            saveConfig();
            sender.sendMessage(
                TextUI.legacy(Component.text("Saved radius " + r + " for arena '" + targetArena + "'.")));
            break;
          }
          String arenaName = args.length > 2 ? validArenaName(args[2]) : "default";
          if (args[1].equalsIgnoreCase("info")) {
            Arena a = arena(arenaName);
            sender.sendMessage(TextUI.legacy(
                Component.text(
                    "Player: "
                        + a.playerSpawn()
                        + " | Bot: "
                        + a.botSpawn()
                        + " | Radius: "
                        + a.radius())));
          } else {
            if (sessions.values().stream().anyMatch(active -> active.arenaName.equals(arenaName)))
              throw new IllegalArgumentException("That arena is occupied.");
            if (!(sender instanceof Player p)) throw new IllegalArgumentException("Players only.");
            String point =
                switch (args[1].toLowerCase(Locale.ROOT)) {
                  case "setplayer" -> "player";
                  case "setbot" -> "bot";
                  default -> throw new IllegalArgumentException("Unknown arena command.");
                };
            if (!Arena.safe(p.getLocation()))
              throw new IllegalArgumentException("Stand on solid ground with clear headroom.");
            getConfig().set(arenaPath(arenaName) + "." + point, p.getLocation());
            saveConfig();
            sender.sendMessage(TextUI.legacy(Component.text("Saved " + point + " spawn.")));
          }
        }
        default ->
            sender.sendMessage(TextUI.legacy(
                Component.text(
                    "/practice menu | settings | start [kit] [difficulty]"
                        + " [endless|match|duel|dummy] [best-of] | stop | pause | resume | rematch"
                        + " | hud | damage | stats | kits | difficulties | kit editor | kit"
                        + " <info|create|import|edit|save|copy|rename|delete> <name> | status | arena"
                        + " <setplayer|setbot|setradius|info> [name] | reload")));
      }
    } catch (IllegalArgumentException e) {
      sender.sendMessage(TextUI.legacy(Component.text(e.getMessage())));
    } catch (Exception | LinkageError e) {
      getLogger().log(java.util.logging.Level.SEVERE, "Practice command failed", e);
      sender.sendMessage(TextUI.legacy(
          Component.text("Practice could not complete that action. Check the server log.")));
    }
    return true;
  }

  private static void admin(CommandSender sender) {
    if (!sender.hasPermission("practice.admin"))
      throw new IllegalArgumentException("Requires practice.admin.");
  }

  @Override
  public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
    Collection<String> choices =
        switch (a.length) {
          case 1 ->
              s.hasPermission("practice.admin")
                  ? List.of(
                      "menu",
                      "settings",
                      "start",
                      "stop",
                      "pause",
                      "resume",
                      "rematch",
                      "hud",
                      "damage",
                      "stats",
                      "kits",
                      "difficulties",
                      "kit",
                      "status",
                      "arena",
                      "reload")
                  : List.of(
                      "menu",
                      "settings",
                      "start",
                      "stop",
                      "pause",
                      "resume",
                      "rematch",
                      "hud",
                      "damage",
                      "stats",
                      "kits",
                      "difficulties",
                      "kit",
                      "status");
          case 2 ->
              switch (a[0]) {
                case "start" -> kits.keySet();
                case "kit" ->
                    s.hasPermission("practice.admin") || s.hasPermission("practice.kits")
                        ? List.of(
                            "info", "editor", "create", "import", "edit", "save", "copy", "rename", "delete")
                        : List.of("info");
                case "arena" ->
                    s.hasPermission("practice.admin")
                        ? List.of("setplayer", "setbot", "setradius", "info")
                        : List.of();
                default -> List.of();
              };
          case 3 ->
              a[0].equals("arena")
                  ? (a[1].equalsIgnoreCase("setradius")
                      ? List.of("16", "24", "32", "64", "128", "256")
                      : arenaNames())
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
                      ? List.of("endless", "match", "duel", "dummy")
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
      e.setJoinMessage(null);
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
    for (PracticeSession session : sessions.values())
      if (session.bot != null) ServerFeatures.unlist(e.getPlayer(), session.bot.player());
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    PracticeSession session = sessionFor(e.getPlayer());
    if (isBot(e.getPlayer())) {
      e.setQuitMessage(null);
      return;
    }
    if (participant(e.getPlayer())) stop(session, "Player disconnected.");
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void healingPressure(EntityDamageByEntityEvent event) {
    PracticeSession session = sessionFor(event.getEntity());
    if (session == null
        || !session.fighting()
        || session.bot == null
        || !event.getEntity().equals(session.bot.player())) return;
    Object source =
        event.getDamager() instanceof Projectile p ? p.getShooter() : event.getDamager();
    if (session.owner.equals(source)) session.bot.onIncomingDamage(event.getFinalDamage());
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void damage(EntityDamageEvent e) {
    Entity source = e instanceof EntityDamageByEntityEvent by ? by.getDamager() : null;
    if (source instanceof Projectile projectile
        && projectile.getShooter() instanceof Entity shooter) source = shooter;
    PracticeSession victimSession = sessionFor(e.getEntity());
    PracticeSession attackSession = sessionFor(source);
    PracticeSession session = victimSession != null ? victimSession : attackSession;
    if (session == null) return;
    if (attackSession != null && victimSession != attackSession) {
      e.setCancelled(true);
      return;
    }
    boolean victim = victimSession != null;
    boolean attacker = attackSession == session;
    if (!victim && !attacker) return;
    boolean utilitySelfDamage =
        victim
            && (source != null
                    && source.equals(e.getEntity())
                    && e instanceof EntityDamageByEntityEvent by
                    && by.getDamager() instanceof Projectile
                || e.getCause() == EntityDamageEvent.DamageCause.POISON
                || e.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK
                || e.getCause() == EntityDamageEvent.DamageCause.FIRE
                || e.getCause() == EntityDamageEvent.DamageCause.LAVA
                || e.getCause() == EntityDamageEvent.DamageCause.FALL);
    if (!victim
        || !attacker && !utilitySelfDamage
        || !session.fighting()
        || session.endingRound
        || source != null && source.equals(e.getEntity()) && !utilitySelfDamage) {
      e.setCancelled(true);
      return;
    }
    Player damaged = (Player) e.getEntity();
    double amount = Math.max(0, Math.min(e.getFinalDamage(), damaged.getHealth()));
    if (damaged.equals(session.owner)) {
      session.round.hurt(amount);
      session.total.hurt(amount);
    } else {
      session.round.hit(amount, session.tick);
      session.total.hit(amount, session.tick);
      if (amount > 0 && source != null && source.equals(session.owner)) session.bot.onOpponentHit();
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
    if (participant(e.getPlayer())
        && !session.fighting()
        && !session.internalTeleport
        && !(e instanceof PlayerTeleportEvent)) {
      if ((e.getTo() != null && (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getY() != e.getTo().getY() || e.getFrom().getZ() != e.getTo().getZ()))) {
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
      PracticeSession active = session;
      getServer()
          .getScheduler()
          .runTask(
              this,
              () -> {
                if (sessions.get(active.owner.getUniqueId()) == active
                    && e.getPlayer().equals(active.owner))
                  active.followTeleport(active.owner.getLocation());
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
    if (!session.fighting()
        || projectiles.stream().filter(p -> sessionFor(p) == session).count() >= 128) {
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
    session.round.utility();
    session.total.utility();
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void projectileHit(ProjectileHitEvent e) {
    PracticeSession session = sessionFor(e.getEntity());
    if (!practiceProjectile(e.getEntity())) return;

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
        || e.getHitEntity() != null
            && (!(e.getHitEntity() instanceof Player p) || !session.participant(p))) {
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
      long before = MendingExperience.armorDamage(p);
      MendingExperience.award(p, xp);
      if (session.bot != null && session.bot.player().equals(p))
        session.bot.onRepairImpact(MendingExperience.armorDamage(p) < before);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void potionBottle(PlayerItemConsumeEvent e) {
    PracticeSession session = sessionFor(e.getPlayer());
    if (participant(e.getPlayer())
        && Set.of(
                Material.POTION,
                Material.HONEY_BOTTLE,
                Material.MUSHROOM_STEW,
                Material.RABBIT_STEW,
                Material.BEETROOT_SOUP,
                Material.SUSPICIOUS_STEW)
            .contains(e.getItem().getType())) {
      PracticeSession active = session;
      getServer()
          .getScheduler()
          .runTask(
              this,
              () -> {
                if (sessions.get(active.owner.getUniqueId()) != active
                    || !participant(e.getPlayer())) return;
                var inventory = e.getPlayer().getInventory();
                for (int slot = 0; slot < 36; slot++) {
                  var remainder = inventory.getItem(slot);
                  if (remainder != null
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
      if (!water.record(e.getBlock(), null, session.owner.getUniqueId(), fluid)) {
        e.setCancelled(true);
        e.getPlayer()
            .sendMessage(TextUI.legacy(
                Component.text(
                    "Place water/lava into air or a web; waterlogging and oversized flows are not"
                        + " supported in practice.")));
      }
    } catch (java.io.IOException failure) {
      e.setCancelled(true);
      getLogger().severe("Water journal failed: " + failure.getMessage());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void fillWater(PlayerBucketFillEvent e) {
    PracticeSession session = sessionFor(e.getPlayer());
    if (protectedFrom(session, e.getBlock().getLocation())
        || water.owns(e.getBlock())
            && (session == null
                || !session.owner.getUniqueId().equals(water.owner(e.getBlock())))) {
      e.setCancelled(true);
      return;
    }
    if (!participant(e.getPlayer())) return;
    if (!session.fighting()
        || e.getItemStack() == null
        || (e.getItemStack().getType() != Material.WATER_BUCKET
            && e.getItemStack().getType() != Material.LAVA_BUCKET)) e.setCancelled(true);
    else tagBucketResult(e);
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
    if (!water.owns(e.getBlock())) return;
    PracticeSession source = sessions.get(water.owner(e.getBlock()));
    if (protectedFrom(source, e.getToBlock().getLocation())) {
      e.setCancelled(true);
      return;
    }
    try {
      if (!water.record(e.getToBlock(), e.getBlock(), water.owner(e.getBlock())))
        e.setCancelled(true);
    } catch (java.io.IOException failure) {
      e.setCancelled(true);
      getLogger().severe("Water flow journal failed: " + failure.getMessage());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void waterFreeze(BlockFormEvent e) {
    if (water.owns(e.getBlock())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void practiceIgnition(BlockIgniteEvent e) {
    var source = e.getIgnitingBlock();

    if (source != null && (water.owns(source) || webs.fire(source))) {
      e.setCancelled(true);
      return;
    }
    PracticeSession session = e.getPlayer() == null ? null : sessionFor(e.getPlayer());
    if (protectedFrom(session, e.getBlock().getLocation())) {
      e.setCancelled(true);
      return;
    }
    if (session == null) return;
    if (!session.fighting()) {
      e.setCancelled(true);
      return;
    }
    try {
      if (!webs.recordFire(e.getBlock(), session.owner.getUniqueId())) e.setCancelled(true);
    } catch (java.io.IOException failure) {
      e.setCancelled(true);
      getLogger().severe("Fire journal failed: " + failure.getMessage());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void practiceFireSpread(BlockSpreadEvent e) {
    if (webs.fire(e.getSource())) e.setCancelled(true);
  }

  @EventHandler
  public void loadedEntities(org.bukkit.event.world.EntitiesLoadEvent e) {
    for (Entity entity : e.getEntities())
      if (entity instanceof Projectile p && practiceProjectile(p) && !projectiles.contains(p))
        p.remove();
  }

  @EventHandler
  public void extendPiston(BlockPistonExtendEvent e) {
    if (e.getBlocks().stream().anyMatch(webs::owns)) e.setCancelled(true);
  }

  @EventHandler
  public void retractPiston(BlockPistonRetractEvent e) {
    if (e.getBlocks().stream().anyMatch(webs::owns)) e.setCancelled(true);
  }

  @EventHandler
  public void burnWeb(BlockBurnEvent e) {
    if (webs.owns(e.getBlock())
        || (e.getIgnitingBlock() != null && webs.fire(e.getIgnitingBlock()))
        || protectedFrom(null, e.getBlock().getLocation())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void breakBlock(BlockBreakEvent e) {
    PracticeSession session = sessionFor(e.getPlayer());
    if (protectedFrom(session, e.getBlock().getLocation())
        || webs.owns(e.getBlock())
            && (session == null || !session.owner.getUniqueId().equals(webs.owner(e.getBlock())))) {
      e.setCancelled(true);
      return;
    }
    if (dev.insanmiy.practiceplugin.model.BlockAccess.shouldCancel(
        participant(e.getPlayer()), session != null && session.fighting())) {
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
    if (protectedFrom(session, e.getBlock().getLocation())) {
      e.setCancelled(true);
      return;
    }
    if (dev.insanmiy.practiceplugin.model.BlockAccess.shouldCancel(
        participant(e.getPlayer()), session != null && session.fighting())) {
      e.setCancelled(true);
      return;
    }
    if (participant(e.getPlayer())
        && session.fighting()
        && e.getBlock().getType() == Material.COBWEB) {
      try {
        if (webs.record(e.getBlock(), e.getBlockReplacedState(), session.owner.getUniqueId()))
          return;
      } catch (java.io.IOException failure) {
        getLogger().severe("Web placement cancelled: journal could not be saved.");
      }
      e.setCancelled(true);
      e.getPlayer()
          .sendMessage(TextUI.legacy(
              Component.text(
                  "Web placement cancelled because safe cleanup could not be recorded.")));
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
    e.blockList().removeIf(b -> inArena(b.getLocation()) || webs.owns(b));
  }

  @EventHandler
  public void explodeBlock(BlockExplodeEvent e) {
    e.blockList().removeIf(b -> inArena(b.getLocation()) || webs.owns(b));
  }

  @EventHandler
  public void interact(PlayerInteractEvent e) {
    PracticeSession session = sessionFor(e.getPlayer());
    if (e.getClickedBlock() != null && protectedFrom(session, e.getClickedBlock().getLocation())) {
      e.setUseInteractedBlock(Event.Result.DENY);
      e.setUseItemInHand(Event.Result.DENY);
      return;
    }
    if (!participant(e.getPlayer())) return;
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
        && (session.closing
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
      e.setDeathMessage(null);
      if (session.fighting() && !session.endingRound) {
        PracticeSession active = session;
        active.captureLayout();
        boolean playerWon = !e.getEntity().equals(active.owner);

        boolean revived = ServerFeatures.revive(e);
        e.setKeepInventory(true);
        e.setKeepLevel(true);
        session.endingRound = true;
        active.owner.sendMessage(TextUI.legacy(
            Component.text((playerWon ? "Bot" : "You") + " took a lethal hit.")));
        getServer()
            .getScheduler()
            .runTask(
                this,
                () -> {
                  if (sessions.get(active.owner.getUniqueId()) != active) return;
                  if (!revived) {
                    if (playerWon) active.replaceDeadBot();
                    else active.owner.spigot().respawn();
                  }
                  active.endingRound = false;
                  active.win(playerWon);
                });
        return;
      }
      getServer()
          .getScheduler()
          .runTask(this, () -> stop(session, "Unexpected player death; practice aborted."));
    }
  }
}
