package dev.insanmiy.practiceplugin;

import dev.insanmiy.practiceplugin.config.*;
import dev.insanmiy.practiceplugin.config.Difficulty;
import dev.insanmiy.practiceplugin.model.PracticeMode;
import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.jetbrains.annotations.NotNull;

final class PracticeCommands implements TabExecutor {
  private final PracticePlugin plugin;

  PracticeCommands(PracticePlugin plugin) {
    this.plugin = Objects.requireNonNull(plugin);
  }

  @Override
  public boolean onCommand(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {
    try {
      PracticeSession session = sender instanceof Player p ? plugin.sessions().get(p.getUniqueId()) : null;
      String sub =
          args.length == 0
              ? (sender instanceof Player ? "menu" : "help")
              : args[0].toLowerCase(Locale.ROOT);
      switch (sub) {
        case "settings" -> {
          checkPerm(sender, "practice.settings");
          plugin.settingsMenu().open(plugin.player(sender));
        }
        case "preset" -> {
          checkPerm(sender, "practice.settings");
          Player p = plugin.player(sender);
          if (session != null)
            throw new IllegalArgumentException(plugin.messages().get("session.stop-before-menu"));
          if (args.length == 1) {
            plugin.settingsMenu().openPresets(p);
            break;
          }
          String action = args[1].toLowerCase(Locale.ROOT);
          if (action.equals("list") && args.length == 2) {
            List<String> names = plugin.settings().presets(p.getUniqueId());
            plugin.messages().send(p, names.isEmpty() ? "settings.preset-empty" : "settings.preset-list",
                "%names%", String.join(", ", names));
            break;
          }
          if (args.length != 3 || !Set.of("save", "load", "delete").contains(action)) {
            plugin.messages().send(p, "settings.preset-usage");
            break;
          }
          String name = PlayerSettings.presetName(args[2]);
          try {
            switch (action) {
              case "save" -> plugin.settings().savePreset(p.getUniqueId(), name);
              case "load" -> plugin.settings().loadPreset(p.getUniqueId(), name);
              case "delete" -> plugin.settings().deletePreset(p.getUniqueId(), name);
            }
            String message = switch (action) {
              case "save" -> "settings.preset-saved";
              case "load" -> "settings.preset-loaded";
              default -> "settings.preset-deleted";
            };
            plugin.messages().send(p, message, "%name%", name);
          } catch (java.io.IOException failure) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Could Not Save Bot Preset Settings", failure);
            plugin.messages().send(p, "settings.save-error");
          }
        }
        case "menu" -> {
          checkPerm(sender, "practice.menu");
          Player p = plugin.player(sender);
          if (session != null)
            throw new IllegalArgumentException(plugin.messages().get("session.stop-before-menu"));
          plugin.menu().open(p, plugin.defaults(p));
        }
        case "start" -> {
          checkPerm(sender, "practice.start");
          Player p = plugin.player(sender);
          Selection d = plugin.defaults(p);
          plugin.start(
              p,
              new Selection(
                  args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : d.kit(),
                  args.length > 2 ? args[2] : d.difficulty(),
                  args.length > 3 ? PracticeMode.parse(args[3]) : d.mode(),
                  args.length > 4 ? Integer.parseInt(args[4]) : d.bestOf()));
        }
        case "rematch" -> {
          checkPerm(sender, "practice.rematch");
          Player p = plugin.player(sender);
          Selection previous = plugin.lastSelections().get(p.getUniqueId());
          if (previous == null)
            throw new IllegalArgumentException(plugin.messages().get("combat.rematch-none"));
          plugin.start(p, previous);
        }
        case "pause" -> {
          checkPerm(sender, "practice.pause");
          if (plugin.ownSession(sender).endingRound)
            throw new IllegalArgumentException(plugin.messages().get("combat.wait-for-round-result"));
          plugin.ownSession(sender).setPaused(true);
        }
        case "resume" -> {
          checkPerm(sender, "practice.pause");
          plugin.ownSession(sender).setPaused(false);
        }
        case "hud" -> {
          checkPerm(sender, "practice.hud");
          plugin.ownSession(sender).toggleHud();
        }
        case "damage" -> {
          checkPerm(sender, "practice.damage");
          PracticeSession active = plugin.ownSession(sender);
          active.damageDiagnostics.toggle(active.owner);
        }
        case "stats" -> {
          checkPerm(sender, "practice.stats");
          Player p = plugin.player(sender);
          for (PracticeMode mode : PracticeMode.values())
            sender.sendMessage(Messages.color(plugin.progress().summary(p.getUniqueId(), mode)));
          if (session != null && session.owner.equals(p))
            plugin.messages().send(
                sender,
                "commands.stats-current",
                "%summary%",
                session.total.summary(session.totalFightTicks));
        }
        case "kits" -> {
          checkPerm(sender, "practice.kits.list");
          plugin.kits().values()
              .forEach(
                  k ->
                      plugin.messages().send(
                          sender, "commands.kit-entry", "%kit%", k.name(), "%summary%", k.summary()));
        }
        case "difficulties" -> {
          checkPerm(sender, "practice.difficulties.list");
          plugin.difficulties()
              .values()
              .forEach(
                  d ->
                      plugin.messages().send(
                          sender,
                          "commands.difficulty-entry",
                          "%difficulty%",
                          d.prettyDisplayName(),
                          "%decision%",
                          String.valueOf(d.decisionTicks()),
                          "%perception%",
                          String.valueOf(d.perceptionTicks()),
                          "%aim%",
                          String.valueOf(d.aimError())));
        }
        case "kit" -> {
          if (args.length > 1 && args[1].equalsIgnoreCase("info")) {
            checkPerm(sender, "practice.kits.list");
            if (args.length < 3) throw new IllegalArgumentException(plugin.messages().get("commands.kit-info-usage"));
            Kit kit = plugin.kits().get(args[2].toLowerCase(Locale.ROOT));
            if (kit == null) throw new IllegalArgumentException(plugin.messages().get("kit.unknown-kit"));
            plugin.messages().send(sender, "commands.kit-entry", "%kit%", kit.name(), "%summary%", kit.summary());
          } else plugin.kitEditor().command(plugin.player(sender), args);
        }
        case "stop" -> {
          PracticeSession target = session;
          if (args.length > 1) {
            checkPerm(sender, "practice.stop.other");
            Player other = Bukkit.getPlayerExact(args[1]);
            target = other == null ? null : plugin.sessions().get(other.getUniqueId());
          } else {
            checkPerm(sender, "practice.stop");
          }
          if (target == null)
            throw new IllegalArgumentException(plugin.messages().get(args.length > 1 ? "session.no-active-match-other" : "session.no-active-match"));
          plugin.stop(target, plugin.messages().get("session.match-stopped"));
          if (args.length > 1 && sender != target.owner) {
            plugin.messages().send(sender, "session.match-stopped-player", "%player%", target.owner.getName());
          }
        }
        case "status" -> {
          checkPerm(sender, "practice.status");
          plugin.messages().send(
              sender,
              "commands.status-sessions",
              "%sessions%",
              String.valueOf(plugin.sessions().size()),
              "%arenas%",
              String.valueOf(plugin.arenaNames()));
          plugin.messages().send(
              sender,
              "commands.status-kits",
              "%kits%",
              String.valueOf(plugin.kits().keySet()),
              "%default_kit%",
              String.valueOf(plugin.getConfig().getString("session.default-kit")),
              "%default_difficulty%",
              String.valueOf(plugin.getConfig().getString("session.default-difficulty")));
          if (session == null) {
            plugin.messages().send(sender, "commands.status-no-match");
          } else {
            plugin.messages().send(
                sender,
                "commands.status-match",
                "%player%",
                session.owner.getName(),
                "%round%",
                String.valueOf(session.match.round()),
                "%wins%",
                String.valueOf(session.match.playerWins()),
                "%losses%",
                String.valueOf(session.match.botWins()));
          }
          try {
            Arena a = plugin.arena();
            plugin.messages().send(sender, "arena.ready", "%radius%", String.valueOf((int) a.radius()));
          } catch (IllegalArgumentException e) {
            sender.sendMessage(Messages.color(e.getMessage()));
          }
        }
        case "reload" -> {
          checkPerm(sender, "practice.reload");
          if (!plugin.sessions().isEmpty())
            throw new IllegalArgumentException(plugin.messages().get("general.reload-busy-warning"));
          plugin.reloadConfig();
          plugin.loadSettings();
          plugin.messages().reload();
          dev.insanmiy.practiceplugin.papi.PapiHook.shutdown();
          dev.insanmiy.practiceplugin.papi.PapiHook.init(plugin);
          plugin.messages().send(sender, "general.plugin-reloaded");
        }
        case "arena" -> {
          checkPerm(sender, "practice.arena");
          if (args.length < 2)
            throw new IllegalArgumentException(plugin.messages().get("commands.arena-usage"));
          String arenaSub = args[1].toLowerCase(Locale.ROOT);
          switch (arenaSub) {
            case "list" -> {
              plugin.messages().send(sender, "arena.header");
              Map<String, Arena> occupied = plugin.occupiedArenas();
              for (String name : plugin.arenaNames()) {
                String status;
                try {
                  Arena a = plugin.arena(name);
                  status = switch (ArenaPool.status(name, a, occupied)) {
                    case OCCUPIED -> plugin.messages().get("arena.status-in-use");
                    case OVERLAPPING -> plugin.messages().get("arena.status-overlapping");
                    case AVAILABLE -> plugin.messages().get("arena.status-ready", "%radius%", String.valueOf((int) a.radius()));
                  };
                } catch (IllegalArgumentException e) {
                  status = plugin.messages().get("arena.status-incomplete", "%error%", e.getMessage());
                }
                plugin.messages().send(sender, "arena.entry", "%arena%", name, "%status%", status);
              }
            }
            case "delete" -> {
              if (args.length < 3) throw new IllegalArgumentException(plugin.messages().get("commands.arena-delete-usage"));
              String toDelete = plugin.validArenaName(args[2]);
              if (plugin.sessions().values().stream().anyMatch(s -> s.arenaName.equals(toDelete)))
                throw new IllegalArgumentException(plugin.messages().get("arena.cannot-delete-occupied"));
              if (toDelete.equals("default")) {
                plugin.getConfig().set("arena.player", null);
                plugin.getConfig().set("arena.bot", null);
              } else {
                plugin.getConfig().set("arenas." + toDelete, null);
              }
              plugin.saveConfig();
              plugin.messages().send(sender, "arena.deleted", "%arena%", toDelete);
            }
            case "tp" -> {
              if (!(sender instanceof Player p)) throw new IllegalArgumentException(plugin.messages().get("general.players-only"));
              String target = args.length > 2 ? plugin.validArenaName(args[2]) : "default";
              Arena a = plugin.arena(target);
              p.teleport(a.playerSpawn());
              plugin.messages().send(sender, "arena.teleported", "%arena%", target);
            }
            case "setradius" -> {
              if (args.length < 3)
                throw new IllegalArgumentException(plugin.messages().get("commands.arena-setradius-usage"));
              double r;
              try {
                r = Double.parseDouble(args[2]);
              } catch (NumberFormatException e) {
                throw new IllegalArgumentException(plugin.messages().get("general.number-format-error", "%error%", e.getMessage()));
              }
              if (!Double.isFinite(r) || r < 1)
                throw new IllegalArgumentException(plugin.messages().get("arena.radius-invalid"));
              double maxR = plugin.getConfig().getDouble("arena.max-radius", 0.0);
              if (maxR <= 0.0) {
                maxR = 500.0;
              }
              if (r > maxR) {
                throw new IllegalArgumentException(
                    plugin.messages().get("arena.radius-exceeds-max", "%radius%", String.valueOf(r), "%max%", String.valueOf(maxR)));
              }
              String targetArena = args.length > 3 ? plugin.validArenaName(args[3]) : "default";
              plugin.getConfig().set(plugin.arenaPath(targetArena) + ".radius", r);
              plugin.saveConfig();
              plugin.messages().send(sender, "arena.saved-radius", "%radius%", String.valueOf(r), "%arena%", targetArena);
            }
            case "info" -> {
              String arenaName = args.length > 2 ? plugin.validArenaName(args[2]) : "default";
              Arena a = plugin.arena(arenaName);
              Location pSp = a.playerSpawn();
              Location bSp = a.botSpawn();
              String pStr = String.format("%.1f, %.1f, %.1f", pSp.getX(), pSp.getY(), pSp.getZ());
              String bStr = String.format("%.1f, %.1f, %.1f", bSp.getX(), bSp.getY(), bSp.getZ());
              plugin.messages().send(sender, "arena.info", "%player%", pStr, "%bot%", bStr, "%radius%", String.valueOf(a.radius()));
            }
            case "setplayer", "setbot" -> {
              String arenaName = args.length > 2 ? plugin.validArenaName(args[2]) : "default";
              if (plugin.sessions().values().stream().anyMatch(active -> active.arenaName.equals(arenaName)))
                throw new IllegalArgumentException(plugin.messages().get("arena.occupied"));
              if (!(sender instanceof Player p)) throw new IllegalArgumentException(plugin.messages().get("general.players-only"));
              String point = arenaSub.equals("setplayer") ? "Player" : "Bot";
              if (!Arena.safe(p.getLocation()))
                throw new IllegalArgumentException(plugin.messages().get("arena.unsafe-spawn"));
              plugin.getConfig().set(plugin.arenaPath(arenaName) + "." + (point.toLowerCase(Locale.ROOT)), p.getLocation());
              plugin.saveConfig();
              plugin.messages().send(sender, "arena.saved-spawn", "%point%", point, "%arena%", arenaName);
            }
            default -> throw new IllegalArgumentException(plugin.messages().get("commands.arena-usage"));
          }
        }
        default -> plugin.messages().send(sender, "commands.usage");
      }
    } catch (NumberFormatException e) {
      plugin.messages().send(sender, "general.number-format-error", "%error%", e.getMessage());
    } catch (IllegalArgumentException e) {
      sender.sendMessage(Messages.color(e.getMessage().contains("\u00A7") || e.getMessage().contains("&") ? e.getMessage() : "&c" + e.getMessage()));
    } catch (Exception | LinkageError e) {
      plugin.getLogger().log(java.util.logging.Level.SEVERE, "Practice Command Failed", e);
      plugin.messages().send(sender, "general.internal-error");
    }
    return true;
  }

  private void checkPerm(CommandSender sender, String perm) {
    if (!sender.hasPermission(perm)
        && !sender.hasPermission("practice.admin")
        && !sender.hasPermission("practice.*")) {
      throw new IllegalArgumentException(plugin.messages().get("general.no-permission", "%permission%", perm));
    }
  }

  static boolean hasPerm(CommandSender sender, String perm) {
    return sender.hasPermission(perm)
        || sender.hasPermission("practice.admin")
        || sender.hasPermission("practice.*");
  }

  private void admin(CommandSender sender) {
    if (!sender.hasPermission("practice.admin") && !sender.hasPermission("practice.*"))
      throw new IllegalArgumentException(plugin.messages().get("general.no-permission", "%permission%", "practice.admin"));
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
            if (hasPerm(s, "practice.settings")) subs.add("preset");
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
                case "preset" -> hasPerm(s, "practice.settings")
                    ? List.of("save", "load", "delete", "list") : List.of();
                case "start" -> plugin.kits().keySet();
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
              a[0].equals("preset") && s instanceof Player player && hasPerm(s, "practice.settings")
                  && Set.of("save", "load", "delete").contains(a[1].toLowerCase(Locale.ROOT))
                  ? plugin.settings().presets(player.getUniqueId())
                  : a[0].equals("arena")
                  ? (a[1].equalsIgnoreCase("setradius")
                      ? List.of("16", "24", "32", "64", "128", "256")
                      : a[1].equalsIgnoreCase("list") ? List.of() : plugin.arenaNames())
                  : a[0].equals("start")
                      ? plugin.difficulties().keySet()
                      : a[0].equals("kit")
                              && Set.of("info", "edit", "copy", "rename", "delete").contains(a[1])
                          ? plugin.kits().keySet()
                          : List.of();
          case 4 ->
              a[0].equals("arena") && a[1].equalsIgnoreCase("setradius")
                  ? plugin.arenaNames()
                  : a[0].equals("start")
                      ? Arrays.stream(PracticeMode.values()).map(PracticeMode::id).toList()
                      : a[0].equals("kit") && a[1].equals("create") ? plugin.kits().keySet() : List.of();
          case 5 -> a[0].equals("start") ? List.of("1", "3", "5", "7", "9", "11") : List.of();
          default -> List.of();
        };
    return choices.stream()
        .filter(v -> v.startsWith(a[a.length - 1].toLowerCase(Locale.ROOT)))
        .toList();
  }

}
