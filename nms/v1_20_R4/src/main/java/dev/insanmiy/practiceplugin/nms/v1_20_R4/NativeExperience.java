package dev.insanmiy.practiceplugin.nms.v1_20_R4;

import dev.insanmiy.practiceplugin.bot.*;

import java.lang.reflect.Method;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.EntityType;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

public final class NativeExperience {
  private static final Method MEND = findMend();
  private NativeExperience() {}

  private static Method findMend() {
    Method match = null;
    for (Method method : ExperienceOrb.class.getDeclaredMethods()) {
      Class<?>[] params = method.getParameterTypes();
      if (method.getReturnType() == int.class
          && params.length == 2
          && (params[0] == ServerPlayer.class || params[0] == net.minecraft.world.entity.player.Player.class)
          && params[1] == int.class) {
        if (match != null) throw new IllegalStateException("Ambiguous native Mending method");
        match = method;
      }
    }
    if (match == null) throw new IllegalStateException("Native Mending method unavailable");
    match.setAccessible(true);
    return match;
  }

  public static int mend(Player player, int amount) {
    var handle = ((CraftPlayer) player).getHandle();
    var orb = new ExperienceOrb(EntityType.EXPERIENCE_ORB, handle.level());
    try { return (int) MEND.invoke(orb, handle, amount); }
    catch (ReflectiveOperationException e) { throw new IllegalStateException("Native Mending failed", e); }
  }
}
