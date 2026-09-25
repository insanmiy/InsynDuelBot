package dev.insanmiy.practiceplugin;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.*;

final class PaperHooks implements Listener {
  private final PracticePlugin plugin;
  PaperHooks(PracticePlugin plugin) { this.plugin = plugin; }
  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void synchronizeMelee(PrePlayerAttackEntityEvent e) {
    PracticeSession session = plugin.sessionFor(e.getPlayer());
    if (session == null
        || !session.fighting()
        || !plugin.participant(e.getPlayer())
        || !(e.getAttacked() instanceof Player target)
        || !session.participant(target)) return;

    dev.insanmiy.practiceplugin.bot.NmsBot.synchronizeEquipment(e.getPlayer());
    dev.insanmiy.practiceplugin.bot.NmsBot.synchronizeEquipment(target);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void isolateMelee(PrePlayerAttackEntityEvent e) {
    PracticeSession attacker = plugin.sessionFor(e.getPlayer()), victim = plugin.sessionFor(e.getAttacked());
    if ((attacker != null || victim != null)
        && (attacker != victim || !attacker.fighting() || attacker.endingRound))
      e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void captureMelee(PrePlayerAttackEntityEvent e) {
    PracticeSession session = plugin.sessionFor(e.getPlayer());
    if (session != null
        && session.fighting()
        && e.willAttack()
        && plugin.participant(e.getPlayer())
        && e.getAttacked() instanceof Player target
        && session.participant(target)) session.damageDiagnostics.capture(e.getPlayer(), target);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void contact(PrePlayerAttackEntityEvent e) {
    PracticeSession session = plugin.sessionFor(e.getPlayer());
    if (session != null
        && session.fighting()
        && e.getPlayer().equals(session.owner)
        && session.bot != null
        && e.getAttacked().equals(session.bot.player())
        && e.willAttack()
        && session.lastContactTick != session.tick) {
      session.lastContactTick = session.tick;
      session.round.contact();
      session.total.contact();
    }
  }

  @EventHandler
  public void pickupXp(com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent e) {
    if (plugin.participant(e.getPlayer())) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void respawn(org.bukkit.event.player.PlayerRespawnEvent event) {
    PracticeSession session = plugin.sessionFor(event.getPlayer());
    if (session != null) event.setRespawnLocation(session.arena.playerSpawn());
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void experience(org.bukkit.event.player.PlayerExpChangeEvent event) {
    if (plugin.participant(event.getPlayer())) event.setAmount(0);
  }
}
