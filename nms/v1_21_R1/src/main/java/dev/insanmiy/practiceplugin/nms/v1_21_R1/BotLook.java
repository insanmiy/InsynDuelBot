package dev.insanmiy.practiceplugin.nms.v1_21_R1;

import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.server.level.ServerPlayer;

final class BotLook {
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
    var viewers = ((net.minecraft.server.level.ServerLevel) bot.level()).getChunkSource();
    viewers.broadcastAndSend(
        bot,
        new ClientboundMoveEntityPacket.Rot(
            bot.getId(),
            (byte) (bot.getYRot() * 256 / 360),
            (byte) (bot.getXRot() * 256 / 360),
            bot.onGround()));
    viewers.broadcastAndSend(
        bot, new ClientboundRotateHeadPacket(bot, (byte) (bot.getYRot() * 256 / 360)));
  }
}
