package dev.insanmiy.practiceplugin;

import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

final class DamageDiagnostics {
  private record Swing(int tick, UUID target, String weapon, double base, double charge) {}

  private final Map<UUID, Swing> swings = new HashMap<>();
  private boolean enabled;

  void cleanup(UUID id) {
    swings.remove(id);
  }

  void toggle(Player owner) {
    enabled = !enabled;
    swings.clear();
    owner.sendMessage(TextUI.legacy(
        Messages.component(
            Messages.text("text.damage-diagnostics.damage-readout")
                + (enabled ? Messages.text("text.damage-diagnostics.on") : Messages.text("text.damage-diagnostics.off"))
                + Messages.text("text.damage-diagnostics.raw-hit-before-defenses-final-hp")
                + Messages.text("text.damage-diagnostics.hp-1-heart"))));
  }

  void capture(Player attacker, Player target) {
    if (!enabled) return;
    double attackDamage = Attributes.getValue(attacker, Attributes.ATTACK_DAMAGE, 1.0);
    swings.put(
        attacker.getUniqueId(),
        new Swing(
            ServerFeatures.tick(),
            target.getUniqueId(),
            attacker.getInventory().getItemInMainHand().getType().name(),
            attackDamage,
            attacker.getAttackCooldown()));
  }

  void report(EntityDamageByEntityEvent event, PracticeSession session) {
    if (!enabled) {
      return;
    }
    if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player target)) {
      return;
    }

    Swing swing = swings.remove(attacker.getUniqueId());
    boolean known =
        swing != null
            && swing.tick() == ServerFeatures.tick()
            && swing.target().equals(target.getUniqueId());

    String input =
        known
            ? String.format(
                Locale.ROOT,
                Messages.text("text.damage-diagnostics.s-attribute-2f-charge-0f"),
                swing.weapon(),
                swing.base(),
                swing.charge() * 100)
            : Messages.text("text.damage-diagnostics.secondary-hit");

    String result;
    if (event == session.finishingBlow) {
      result = Messages.text("text.damage-diagnostics.round-ending-hit");
    } else if (event.isCancelled()) {
      result = Messages.text("text.damage-diagnostics.cancelled-no-hp-damage");
    } else {
      result = Messages.text("text.damage-diagnostics.applied");
    }

    String attackerName = attacker.equals(session.owner) ? Messages.text("text.damage-diagnostics.you") : Messages.text("text.damage-diagnostics.bot");
    String targetName = target.equals(session.owner) ? Messages.text("text.damage-diagnostics.you") : Messages.text("text.damage-diagnostics.bot");
    double armor = Attributes.getValue(target, Attributes.ARMOR, 0.0);
    double toughness = Attributes.getValue(target, Attributes.ARMOR_TOUGHNESS, 0.0);
    String isCritical = ServerFeatures.critical(event);

    String message =
        String.format(
            Locale.ROOT,
            Messages.text("text.damage-diagnostics.s-s-s-raw-2f-final"),
            attackerName,
            targetName,
            input,
            event.getDamage(),
            event.getFinalDamage(),
            armor,
            toughness,
            isCritical,
            result);

    session.owner.sendMessage(TextUI.legacy(Messages.component(message)));
  }
}
