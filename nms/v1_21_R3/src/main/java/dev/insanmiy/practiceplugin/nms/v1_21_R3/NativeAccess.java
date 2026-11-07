package dev.insanmiy.practiceplugin.nms.v1_21_R3;

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
import java.lang.reflect.Method;
import net.minecraft.world.entity.Entity;

final class NativeAccess {
  private NativeAccess() {}

  private static Method broadcastMethod;
  private static volatile boolean methodResolved;

  static void moveToLocation(ServerPlayer player, Location loc) {
    double x = loc.getX(), y = loc.getY(), z = loc.getZ();
    float yaw = loc.getYaw(), pitch = loc.getPitch();
    // 1. Try absSnapTo (MC 1.21.4+ / Paper 1.21.11)
    try {
      java.lang.reflect.Method m =
          player.getClass().getMethod("absSnapTo", double.class, double.class, double.class, float.class, float.class);
      m.invoke(player, x, y, z, yaw, pitch);
      return;
    } catch (Throwable ignored) {
    }
    // 2. Try absMoveTo (MC 1.21.2 - 1.21.3)
    try {
      java.lang.reflect.Method m =
          player.getClass().getMethod("absMoveTo", double.class, double.class, double.class, float.class, float.class);
      m.invoke(player, x, y, z, yaw, pitch);
      return;
    } catch (Throwable ignored) {
    }
    // 3. Fallback: snapTo
    try {
      java.lang.reflect.Method m =
          player.getClass().getMethod("snapTo", double.class, double.class, double.class, float.class, float.class);
      m.invoke(player, x, y, z, yaw, pitch);
      return;
    } catch (Throwable ignored) {
    }
    // 4. Safe fallback: setPos + rotations
    player.setPos(x, y, z);
    player.setYRot(yaw);
    player.setXRot(pitch);
    player.yRotO = yaw;
    player.xRotO = pitch;
  }

  static void setClientLoadedSafe(ServerPlayer player) {
    try {
      java.lang.reflect.Method m = player.getClass().getMethod("setClientLoaded", boolean.class);
      m.invoke(player, true);
      return;
    } catch (NoSuchMethodException ignored) {
      // Newer Versions Acknowledge Loading Through The Packet Listener.
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Could Not Mark Bot Client Loaded", failure);
    }
    try {
      Class<?> packet = Class.forName("net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket");
      player.connection.getClass().getMethod("handleAcceptPlayerLoad", packet)
          .invoke(player.connection, packet.getConstructor().newInstance());
    } catch (ClassNotFoundException ignored) {
      // Older Versions Do Not Have A Client Loaded Packet.
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Could Not Acknowledge Bot Client Loading", failure);
    }
  }

  static Vec3 extractMotion(ClientboundSetEntityMotionPacket motion) {
    try {
      java.lang.reflect.Method m = motion.getClass().getMethod("getMovement");
      return (Vec3) m.invoke(motion);
    } catch (Throwable ignored) {
    }
    try {
      java.lang.reflect.Method x = motion.getClass().getMethod("getXa");
      java.lang.reflect.Method y = motion.getClass().getMethod("getYa");
      java.lang.reflect.Method z = motion.getClass().getMethod("getZa");
      double dx = ((Number) x.invoke(motion)).doubleValue();
      double dy = ((Number) y.invoke(motion)).doubleValue();
      double dz = ((Number) z.invoke(motion)).doubleValue();
      return new Vec3(dx, dy, dz);
    } catch (Throwable ignored) {
    }
    return Vec3.ZERO;
  }

  static GameProfile createBotProfile(UUID botId, String name, GameProfile ownerProfile) {
    Object ownerProps = getProfileProperties(ownerProfile);
    if (ownerProps != null) {
      // 1. Try 3-arg constructor for modern AuthLib 7+ (Record GameProfile(UUID, String, PropertyMap))
      for (java.lang.reflect.Constructor<?> ctor : GameProfile.class.getConstructors()) {
        Class<?>[] paramTypes = ctor.getParameterTypes();
        if (paramTypes.length == 3
            && paramTypes[0] == UUID.class
            && paramTypes[1] == String.class
            && paramTypes[2].isAssignableFrom(ownerProps.getClass())) {
          try {
            return (GameProfile) ctor.newInstance(botId, name, ownerProps);
          } catch (Throwable ignored) {
          }
        }
      }
    }
    // 2. Fallback to 2-arg constructor for legacy AuthLib 6 (GameProfile(UUID, String))
    GameProfile profile = new GameProfile(botId, name);
    if (ownerProps != null) {
      try {
        Object botProps = getProfileProperties(profile);
        if (botProps != null) {
          for (java.lang.reflect.Method m : botProps.getClass().getMethods()) {
            if ("putAll".equals(m.getName()) && m.getParameterCount() == 1) {
              if (m.getParameterTypes()[0].isAssignableFrom(ownerProps.getClass())) {
                m.invoke(botProps, ownerProps);
                break;
              }
            }
          }
        }
      } catch (Throwable ignored) {
      }
    }
    return profile;
  }

  private static Object getProfileProperties(GameProfile profile) {
    if (profile == null) return null;
    try {
      java.lang.reflect.Method m;
      try {
        m = profile.getClass().getMethod("properties");
      } catch (NoSuchMethodException e) {
        m = profile.getClass().getMethod("getProperties");
      }
      return m.invoke(profile);
    } catch (Throwable t) {
      return null;
    }
  }

  static void synchronizeEquipment(Player player) {
    ServerPlayer handle = ((CraftPlayer) player).getHandle();
    try {
      java.lang.reflect.Method m = handle.getClass().getMethod("detectEquipmentUpdates");
      m.invoke(handle);
      return;
    } catch (Throwable ignored) {
    }
    try {
      java.lang.reflect.Method m = handle.getClass().getMethod("detectEquipmentUpdatesPublic");
      m.invoke(handle);
    } catch (Throwable ignored) {
    }
  }

  static void broadcast(ServerPlayer bot, Packet<?> packet) {
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

  static int teleportId(ClientboundPlayerPositionPacket packet) {
    return packet.id();
  }

  static double fallDistance(ServerPlayer player) {
    return player.getBukkitEntity().getFallDistance();
  }

  static void setFallDistance(ServerPlayer player, float distance) {
    player.getBukkitEntity().setFallDistance(distance);
  }

  static int maxBuildHeight(ServerPlayer player) {
    return player.level().getMaxY();
  }

  static boolean supportsWindCharge() {
    return true;
  }
}
