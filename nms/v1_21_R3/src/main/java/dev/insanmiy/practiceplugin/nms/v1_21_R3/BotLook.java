package dev.insanmiy.practiceplugin.nms.v1_21_R3;

import dev.insanmiy.practiceplugin.bot.*;

import java.lang.reflect.Method;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

final class BotLook {
  private static Method broadcastMethod;
  private static volatile boolean methodResolved;

  private BotLook() {}

  static void towards(ServerPlayer bot, float yaw, float pitch, float speed) {
    bot.setYRot(dev.insanmiy.practiceplugin.model.LookMotion.turn(bot.getYRot(), yaw, speed));
    float targetPitch = Math.max(-90.0f, Math.min(90.0f, pitch));
    float deltaPitch = Math.max(-speed * .6f, Math.min(speed * .6f, targetPitch - bot.getXRot()));
    bot.setXRot(Math.max(-90.0f, Math.min(90.0f, bot.getXRot() + deltaPitch)));
    bot.setYHeadRot(bot.getYRot());
  }

  static void publish(ServerPlayer bot) {
    bot.setYHeadRot(bot.getYRot());
    bot.setYBodyRot(
        dev.insanmiy.practiceplugin.model.LookMotion.turn(bot.yBodyRot, bot.getYRot(), 18));
    broadcast(
        bot,
        new ClientboundMoveEntityPacket.Rot(
            bot.getId(),
            (byte) (bot.getYRot() * 256 / 360),
            (byte) (bot.getXRot() * 256 / 360),
            bot.onGround()));
    broadcast(
        bot, new ClientboundRotateHeadPacket(bot, (byte) (bot.getYRot() * 256 / 360)));
  }

  private static void broadcast(ServerPlayer bot, Packet<?> packet) {
    var viewers = ((net.minecraft.server.level.ServerLevel) bot.level()).getChunkSource();
    if (!methodResolved) {
      resolveBroadcastMethod(viewers.getClass());
    }
    if (broadcastMethod != null) {
      try {
        broadcastMethod.invoke(viewers, bot, packet);
      } catch (Throwable ignored) {
      }
    }
  }

  private static synchronized void resolveBroadcastMethod(Class<?> cacheClass) {
    if (methodResolved) return;
    methodResolved = true;
    String[] candidateNames = {
      "sendToTrackingPlayersAndSelf",
      "sendToTrackingPlayers",
      "broadcastAndSend",
      "broadcast"
    };
    for (String name : candidateNames) {
      try {
        Method m = cacheClass.getMethod(name, Entity.class, Packet.class);
        m.setAccessible(true);
        broadcastMethod = m;
        return;
      } catch (NoSuchMethodException ignored) {
      }
    }
    for (Method m : cacheClass.getMethods()) {
      Class<?>[] params = m.getParameterTypes();
      if (params.length == 2
          && Entity.class.isAssignableFrom(params[0])
          && Packet.class.isAssignableFrom(params[1])) {
        m.setAccessible(true);
        broadcastMethod = m;
        return;
      }
    }
  }
}
