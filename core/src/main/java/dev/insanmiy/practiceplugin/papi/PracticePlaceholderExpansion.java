package dev.insanmiy.practiceplugin.papi;

import dev.insanmiy.practiceplugin.PracticePlugin;
import dev.insanmiy.practiceplugin.PracticeSession;
import dev.insanmiy.practiceplugin.model.PracticeMode;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PracticePlaceholderExpansion extends PlaceholderExpansion {
  private final PracticePlugin plugin;

  public PracticePlaceholderExpansion(PracticePlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public @NotNull String getIdentifier() {
    return "practice";
  }

  @Override
  public @NotNull String getAuthor() {
    return "insanmiy";
  }

  @Override
  public @NotNull String getVersion() {
    return plugin.getDescription().getVersion();
  }

  @Override
  public boolean persist() {
    return true;
  }

  @Override
  public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
    if (offlinePlayer == null) return "";
    String lower = params.toLowerCase(java.util.Locale.ROOT);
    PracticeSession session = offlinePlayer instanceof Player p ? plugin.sessionFor(p) : null;

    return switch (lower) {
      case "in_match" -> String.valueOf(session != null);
      case "fighting" -> String.valueOf(session != null && session.isFighting());
      case "paused" -> String.valueOf(session != null && session.isPaused());
      case "current_kit" -> session != null ? session.kit().name() : "None";
      case "current_difficulty" -> session != null ? session.difficulty().prettyDisplayName() : "None";
      case "current_mode" -> session != null ? session.mode().id() : "None";
      case "current_arena" -> session != null ? session.arenaName() : "None";
      case "round" -> session != null ? String.valueOf(session.match().round()) : "0";
      case "player_score", "score_player" -> session != null ? String.valueOf(session.match().playerWins()) : "0";
      case "bot_score", "score_bot" -> session != null ? String.valueOf(session.match().botWins()) : "0";
      case "bot_name" -> session != null && session.bot() != null && session.bot().player() != null
          ? session.bot().player().getName()
          : "None";
      case "wins", "total_wins" -> String.valueOf(plugin.progress().totalRoundsWon(offlinePlayer.getUniqueId()));
      case "losses", "total_losses" -> String.valueOf(plugin.progress().totalRoundsLost(offlinePlayer.getUniqueId()));
      case "match_wins" -> String.valueOf(plugin.progress().roundsWon(offlinePlayer.getUniqueId(), PracticeMode.MATCH));
      case "match_losses" -> String.valueOf(plugin.progress().roundsLost(offlinePlayer.getUniqueId(), PracticeMode.MATCH));
      case "duel_wins" -> String.valueOf(plugin.progress().roundsWon(offlinePlayer.getUniqueId(), PracticeMode.DUEL));
      case "duel_losses" -> String.valueOf(plugin.progress().roundsLost(offlinePlayer.getUniqueId(), PracticeMode.DUEL));
      case "endless_wins" -> String.valueOf(plugin.progress().roundsWon(offlinePlayer.getUniqueId(), PracticeMode.ENDLESS));
      case "endless_losses" -> String.valueOf(plugin.progress().roundsLost(offlinePlayer.getUniqueId(), PracticeMode.ENDLESS));
      default -> null;
    };
  }
}
