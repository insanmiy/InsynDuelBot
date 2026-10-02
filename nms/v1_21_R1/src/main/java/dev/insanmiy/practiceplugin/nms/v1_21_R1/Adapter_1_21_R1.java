package dev.insanmiy.practiceplugin.nms.v1_21_R1;

import dev.insanmiy.practiceplugin.bot.BotAdapter;
import dev.insanmiy.practiceplugin.bot.BotPlatform;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.entity.Player;

// Adapter for Paper 1.21 and 1.21.1
public final class Adapter_1_21_R1 implements BotAdapter {

  @Override
  public BotPlatform createBot(Player owner, Location spawn, String name, Consumer<UUID> register) {
    return new NmsBot(owner, spawn, name, register);
  }

  @Override
  public void prepareRound(Player player) {
    NmsBot.prepareRound(player);
  }

  @Override
  public void synchronizeEquipment(Player player) {
    NmsBot.synchronizeEquipment(player);
  }

  @Override
  public boolean criticalsEnabled(Player player) {
    return NmsBot.criticalsEnabled(player);
  }

  @Override
  public void stopUsing(Player player) {
    NmsBot.stopUsing(player);
  }

  @Override
  public int mend(Player player, int amount) {
    return NativeExperience.mend(player, amount);
  }

  @Override
  public org.bukkit.entity.WindCharge launchWindCharge(Player player) {
    return player.launchProjectile(org.bukkit.entity.WindCharge.class);
  }
}
