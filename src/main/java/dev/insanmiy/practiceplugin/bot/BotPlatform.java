package dev.insanmiy.practiceplugin.bot;

import dev.insanmiy.practiceplugin.config.*;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface BotPlatform extends AutoCloseable {
  Player player();

  void tick(Player opponent, Difficulty difficulty, Arena arena, boolean fighting);

  void reset(Location location);

  default void relocate(Location location) {
    reset(location);
  }

  boolean isTeleporting();

  default void onOpponentHit() {}

  default void onIncomingDamage(double amount) {}

  default void onRepairImpact(boolean repaired) {}

  default void onOpponentPearled(Player opponent) {}

  default void configure(
      dev.insanmiy.practiceplugin.model.BotOptions options, boolean naturalRegeneration) {}

  @Override
  void close();
}
