package dev.insanmiy.practiceplugin;

import org.bukkit.Bukkit;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

final class ServerFeatures {
  private ServerFeatures() {}

  static int tick() { return Bukkit.getCurrentTick(); }

  static void register(PracticePlugin plugin) {

    Listener listener;
    try {
      Class.forName("io.papermc.paper.event.player.PrePlayerAttackEntityEvent");
      var type = Class.forName("dev.insanmiy.practiceplugin.PaperHooks");
      listener = (Listener) type.getDeclaredConstructor(PracticePlugin.class).newInstance(plugin);
    } catch (ClassNotFoundException e) {
      listener = new SpigotHooks(plugin);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Could not initialize Paper hooks", e);
    }
    Bukkit.getPluginManager().registerEvents(listener, plugin);
  }

  static String critical(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
    return event.isCritical() ? "YES" : "no";
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
