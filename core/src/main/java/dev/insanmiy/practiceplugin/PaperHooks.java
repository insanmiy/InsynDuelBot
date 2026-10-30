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

    dev.insanmiy.practiceplugin.bot.BotAdapters.getAdapter().synchronizeEquipment(e.getPlayer());
    dev.insanmiy.practiceplugin.bot.BotAdapters.getAdapter().synchronizeEquipment(target);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void isolateMelee(PrePlayerAttackEntityEvent e) {
    if (!(e.getAttacked() instanceof Player target)) return;
    PracticeSession attacker = plugin.sessionFor(e.getPlayer()), victim = plugin.sessionFor(target);
    if ((attacker != null || victim != null)
        && (attacker != victim || !attacker.fighting() || attacker.endingRound))
      e.setCancelled(true);
    if (attacker != null && attacker == victim && attacker.isBotPlayer(e.getPlayer()) && attacker.isBotPlayer(target))
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
        && session.isBotPlayer(e.getAttacked())
        && e.willAttack()
        && session.lastContactTick != session.tick) {
      session.lastContactTick = session.tick;
      session.round.contact();
      session.total.contact();
    }
  }

  @EventHandler
  public void pickupXp(com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent e) {
    if (!plugin.participant(e.getPlayer())) return;
    PracticeSession s = plugin.sessionFor(e.getPlayer());
    if (s == null) return;
    int exp = e.getExperienceOrb().getExperience();
    int leftover = dev.insanmiy.practiceplugin.bot.BotAdapters.getAdapter().mend(e.getPlayer(), exp);
    if (leftover <= 0) {
      e.getExperienceOrb().remove();
      e.setCancelled(true);
    } else {
      e.getExperienceOrb().setExperience(leftover);
    }
  }
}
