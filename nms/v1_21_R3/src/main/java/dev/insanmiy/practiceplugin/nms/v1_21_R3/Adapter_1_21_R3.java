package dev.insanmiy.practiceplugin.nms.v1_21_R3;

import dev.insanmiy.practiceplugin.bot.BotAdapter;
import dev.insanmiy.practiceplugin.bot.BotPlatform;
import java.lang.reflect.Constructor;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.entity.Player;

// Adapter for Paper 1.21.2 to 1.21.11
public final class Adapter_1_21_R3 implements BotAdapter {

  private static final Constructor<?> WIND_CHARGE_CONSTRUCTOR;

  static {
    Constructor<?> ctor = null;
    String[] classNames = {
      "net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge",
      "net.minecraft.world.entity.projectile.windcharge.WindCharge"
    };
    for (String name : classNames) {
      try {
        Class<?> clazz = Class.forName(name);
        ctor = clazz.getConstructor(
            net.minecraft.world.entity.player.Player.class,
            net.minecraft.world.level.Level.class,
            double.class,
            double.class,
            double.class);
        break;
      } catch (Throwable ignored) {
      }
    }
    WIND_CHARGE_CONSTRUCTOR = ctor;
  }

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
      if (WIND_CHARGE_CONSTRUCTOR != null) {
        var sp = ((org.bukkit.craftbukkit.entity.CraftPlayer) player).getHandle();
        var level = (net.minecraft.server.level.ServerLevel) sp.level();
        var forward = sp.getForward();
        var eyePos = sp.getEyePosition().add(forward.scale(0.8));
        if (!level.getBlockState(net.minecraft.core.BlockPos.containing(eyePos)).canBeReplaced()) {
          eyePos = sp.getEyePosition().add(forward.scale(0.05));
        }
        var wc = (net.minecraft.world.entity.projectile.Projectile) WIND_CHARGE_CONSTRUCTOR.newInstance(
            sp, level, eyePos.x(), eyePos.y(), eyePos.z());
        wc.shootFromRotation(sp, sp.getXRot(), sp.getYRot(), 0.0F, 1.5F, 1.0F);
        level.addFreshEntity(wc);
        return (org.bukkit.entity.WindCharge) wc.getBukkitEntity();
      }
    } catch (Throwable ignored) {
    }
    return player.launchProjectile(org.bukkit.entity.WindCharge.class);
  }
}
