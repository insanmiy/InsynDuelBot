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
        Component.text(
            "Damage Readout "
                + (enabled ? "On" : "Off")
                + ". Raw = Hit Before Defenses; Final = HP Damage After Armor/Shield/Absorption. 2"
                + " HP = 1 Heart.")));
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
                "%s | Attribute %.2f | Charge %.0f%%",
                swing.weapon(),
                swing.base(),
                swing.charge() * 100)
            : "Secondary Hit";

    String result;
    if (event == session.finishingBlow) {
      result = "Round Ending Hit";
    } else if (event.isCancelled()) {
      result = "Cancelled: No HP Damage";
    } else {
      result = "Applied";
    }

    String attackerName = attacker.equals(session.owner) ? "You" : "Bot";
    String targetName = target.equals(session.owner) ? "You" : "Bot";
    double armor = Attributes.getValue(target, Attributes.ARMOR, 0.0);
    double toughness = Attributes.getValue(target, Attributes.ARMOR_TOUGHNESS, 0.0);
    String isCritical = ServerFeatures.critical(event);

    String message =
        String.format(
            Locale.ROOT,
            "%s -> %s | %s | Raw %.2f -> Final %.2f HP | Armor %.1f / Toughness %.1f | Critical %s | %s",
            attackerName,
            targetName,
            input,
            event.getDamage(),
            event.getFinalDamage(),
            armor,
            toughness,
            isCritical,
            result);

    session.owner.sendMessage(TextUI.legacy(Component.text(message)));
  }
}
