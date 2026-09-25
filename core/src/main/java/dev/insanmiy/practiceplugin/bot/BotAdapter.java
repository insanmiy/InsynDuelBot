package dev.insanmiy.practiceplugin.bot;

import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.entity.Player;

// Version adapter for bot and player operations
public interface BotAdapter {

  // Spawn and register a new bot
  BotPlatform createBot(
      Player owner,
      Location spawn,
      String name,
      Consumer<UUID> register);

  // Prepare player before a round
  void prepareRound(Player player);

  // Synchronize equipment to nearby players
  void synchronizeEquipment(Player player);

  // Check if critical hits are enabled
  boolean criticalsEnabled(Player player);

  // Stop current item use
  void stopUsing(Player player);

  // Repair gear with mending experience
  int mend(Player player, int amount);
}
