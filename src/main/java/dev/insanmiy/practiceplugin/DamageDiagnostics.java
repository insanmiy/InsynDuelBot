package dev.insanmiy.practiceplugin;

import dev.insanmiy.practiceplugin.bot.NmsBot;
import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

final class DamageDiagnostics {
  private record Swing(int tick, UUID target, String weapon, double base, double charge) {}

  private final Map<UUID, Swing> swings = new HashMap<>();
  private boolean enabled;

  void toggle(Player owner) {
    enabled = !enabled;
    swings.clear();
    owner.sendMessage(TextUI.legacy(
        Component.text(
            "Damage readout "
                + (enabled ? "ON" : "OFF")
                + ". Raw = hit before defenses; final = HP damage after armor/shield/absorption. 2"
                + " HP = 1 heart.")));
  }

  void capture(Player attacker, Player target) {
    if (!enabled) return;
    swings.put(
        attacker.getUniqueId(),
        new Swing(
            ServerFeatures.tick(),
            target.getUniqueId(),
            attacker.getInventory().getItemInMainHand().getType().name(),
            attacker.getAttribute(Attribute.ATTACK_DAMAGE).getValue(),
            NmsBot.attackCharge(attacker)));
  }

  void report(EntityDamageByEntityEvent event, PracticeSession session) {
    if (!enabled
        || !(event.getDamager() instanceof Player attacker)
        || !(event.getEntity() instanceof Player target)) return;
    Swing swing = swings.get(attacker.getUniqueId());
    boolean known =
        swing != null
            && swing.tick() == ServerFeatures.tick()
            && swing.target().equals(target.getUniqueId());
    String input =
        known
            ? String.format(
                Locale.ROOT,
                "%s | attribute %.2f | charge %.0f%%",
                swing.weapon(),
                swing.base(),
                swing.charge() * 100)
            : "secondary hit";
    String result =
        event == session.finishingBlow
            ? "round-ending hit"
            : event.isCancelled() ? "cancelled: no HP damage" : "applied";
    session.owner.sendMessage(TextUI.legacy(
        Component.text(
            String.format(
                Locale.ROOT,
                "%s -> %s | %s | raw %.2f -> final %.2f HP | armor %.1f / toughness %.1f | critical"
                    + " %s | %s",
                attacker.equals(session.owner) ? "You" : "Bot",
                target.equals(session.owner) ? "You" : "Bot",
                input,
                event.getDamage(),
                event.getFinalDamage(),
                target.getAttribute(Attribute.ARMOR).getValue(),
                target.getAttribute(Attribute.ARMOR_TOUGHNESS).getValue(),
                ServerFeatures.critical(event),
                result))));
  }
}
