package dev.insanmiy.practiceplugin.nms.v1_20_R4;

import dev.insanmiy.practiceplugin.bot.BotAdapter;
import dev.insanmiy.practiceplugin.bot.BotPlatform;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.entity.Player;

// Adapter for Paper 1.20.6
public final class Adapter_1_20_R4 implements BotAdapter {

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
    try {
      return player.launchProjectile(org.bukkit.entity.WindCharge.class);
    } catch (Throwable ignored) {
      var sp = ((org.bukkit.craftbukkit.entity.CraftPlayer) player).getHandle();
      var level = (net.minecraft.server.level.ServerLevel) sp.level();
      var wc = new net.minecraft.world.entity.projectile.windcharge.WindCharge(
          sp, level, sp.getX(), sp.getEyeY() - 0.1, sp.getZ());
      wc.shootFromRotation(sp, sp.getXRot(), sp.getYRot(), 0.0F, 1.5F, 1.0F);
      level.addFreshEntity(wc);
      return (org.bukkit.entity.WindCharge) wc.getBukkitEntity();
    }
  }
}
