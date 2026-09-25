package dev.insanmiy.practiceplugin;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

final class ServerFeatures {
  private static int ticks;
  private static Method unlist;
  private ServerFeatures() {}

  static int tick() { return ticks; }

  static void register(PracticePlugin plugin) {
    Bukkit.getScheduler().runTaskTimer(plugin, () -> ticks++, 1, 1);
    Listener listener;
    try {
      Class.forName("io.papermc.paper.event.player.PrePlayerAttackEntityEvent");
      var type = Class.forName("dev.insanmiy.practiceplugin.PaperHooks");
      listener = (Listener) type.getDeclaredConstructor(PracticePlugin.class).newInstance(plugin);
      unlist = Player.class.getMethod("unlistPlayer", Player.class);
    } catch (ClassNotFoundException e) {
      listener = new SpigotHooks(plugin);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Could not initialize Paper hooks", e);
    }
    Bukkit.getPluginManager().registerEvents(listener, plugin);
  }

  static void unlist(Player viewer, Player bot) {
    if (unlist == null) return;
    try { unlist.invoke(viewer, bot); }
    catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
  }

  static String critical(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
    try {
      return Boolean.TRUE.equals(event.getClass().getMethod("isCritical").invoke(event)) ? "YES" : "no";
    } catch (NoSuchMethodException e) { return "unavailable"; }
    catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
  }

  static boolean revive(PlayerDeathEvent event) {
    if (!((Object) event instanceof Cancellable cancellable)) return false;
    try {
      event.getClass().getMethod("setReviveHealth", double.class).invoke(event, .5);
      cancellable.setCancelled(true);
      return true;
    } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
  }
}
