package dev.insanmiy.practiceplugin.nms.v1_21_R1;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

final class NativeAccess {
  private NativeAccess() {}

  static GameProfile createBotProfile(UUID botId, String name, GameProfile ownerProfile) {
    GameProfile profile = new GameProfile(botId, name);
    if (ownerProfile != null) profile.getProperties().putAll(ownerProfile.getProperties());
    return profile;
  }

  static void moveToLocation(ServerPlayer player, Location location) {
    player.absMoveTo(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
  }

  static void setClientLoadedSafe(ServerPlayer player) {
    // These Versions Do Not Require Client Load Acknowledgement
  }

  static void synchronizeEquipment(Player player) {
    ((CraftPlayer) player).getHandle().detectEquipmentUpdatesPublic();
  }

  static Vec3 extractMotion(ClientboundSetEntityMotionPacket motion) {
    return new Vec3(motion.getXa(), motion.getYa(), motion.getZa());
  }

  static void broadcast(ServerPlayer bot, Packet<?> packet) {
    ((net.minecraft.server.level.ServerLevel) bot.level()).getChunkSource().broadcastAndSend(bot, packet);
  }

  static int teleportId(ClientboundPlayerPositionPacket packet) {
    return packet.getId();
  }

  static double fallDistance(ServerPlayer player) {
    return player.fallDistance;
  }

  static void setFallDistance(ServerPlayer player, float distance) {
    player.fallDistance = distance;
  }

  static int maxBuildHeight(ServerPlayer player) {
    return player.level().getMaxBuildHeight();
  }

  static boolean supportsWindCharge() {
    return true;
  }
}
