package dev.insanmiy.practiceplugin;

import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

final class SpigotHooks implements Listener {
  private final PracticePlugin plugin;
  SpigotHooks(PracticePlugin plugin) { this.plugin = plugin; }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void capture(EntityDamageByEntityEvent event) {
    if (!(event.getDamager() instanceof Player attacker)
        || !(event.getEntity() instanceof Player target)) return;
    var session = plugin.sessionFor(attacker);
    if (session != null && session.fighting() && session.participant(target))
      session.damageDiagnostics.capture(attacker, target);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void contact(EntityDamageByEntityEvent event) {
    if (!(event.getDamager() instanceof Player attacker)) return;
    var session = plugin.sessionFor(attacker);
    if (session != null && session.fighting() && attacker.equals(session.owner)
        && session.bot != null && event.getEntity().equals(session.bot.player())
        && session.lastContactTick != session.tick) {
      session.lastContactTick = session.tick;
      session.round.contact();
      session.total.contact();
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void experience(PlayerExpChangeEvent event) {
    if (plugin.participant(event.getPlayer())) event.setAmount(0);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void respawn(PlayerRespawnEvent event) {
    var session = plugin.sessionFor(event.getPlayer());
    if (session != null) event.setRespawnLocation(session.arena.playerSpawn());
  }
}
