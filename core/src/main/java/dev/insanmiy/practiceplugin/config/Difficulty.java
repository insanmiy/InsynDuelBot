package dev.insanmiy.practiceplugin.config;

import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

public record Difficulty(
    String name,
    int decisionTicks,
    int perceptionTicks,
    double aimError,
    double attackCharge,
    double shieldChance,
    boolean counters,
    double sprintResetChance,
    double strafeStrength,
    double healThreshold,
    int shieldTicks,
    boolean attacksEnabled,
    String displayName,
    String description,
    Material icon) {

  public Difficulty(
      String name,
      int decisionTicks,
      int perceptionTicks,
      double aimError,
      double attackCharge,
      double shieldChance,
      boolean counters,
      double sprintResetChance,
      double strafeStrength,
      double healThreshold,
      int shieldTicks,
      boolean attacksEnabled) {
    this(
        name,
        decisionTicks,
        perceptionTicks,
        aimError,
        attackCharge,
        shieldChance,
        counters,
        sprintResetChance,
        strafeStrength,
        healThreshold,
        shieldTicks,
        attacksEnabled,
        null,
        null,
        null);
  }

  public Difficulty(
      String name,
      int decisions,
      int perception,
      double aim,
      double charge,
      double shield,
      boolean counters,
      double sprint) {
    this(
        name,
        decisions,
        perception,
        aim,
        charge,
        shield,
        counters,
        sprint,
        .65,
        .5,
        6,
        true,
        null,
        null,
        null);
  }

  public Difficulty {
    if (decisionTicks < 1
        || decisionTicks > 40
        || perceptionTicks < 0
        || perceptionTicks > 40
        || !Double.isFinite(aimError)
        || aimError < 0
        || aimError > 45
        || !unit(attackCharge)
        || attackCharge < .2
        || !unit(shieldChance)
        || !unit(sprintResetChance)
        || !unit(strafeStrength)
        || !unit(healThreshold)
        || shieldTicks < 1
        || shieldTicks > 40)
      throw new IllegalArgumentException("Invalid difficulty values for " + name);
  }

  private static boolean unit(double d) {
    return Double.isFinite(d) && d >= 0 && d <= 1;
  }

  public Material effectiveIcon() {
    if (icon != null) return icon;
    if (decisionTicks <= 2) return Material.NETHERITE_SWORD;
    if (decisionTicks <= 4) return Material.DIAMOND_SWORD;
    if (decisionTicks <= 6) return Material.IRON_SWORD;
    if (decisionTicks <= 8) return Material.STONE_SWORD;
    return Material.WOODEN_SWORD;
  }

  public String prettyDisplayName() {
    if (displayName != null && !displayName.isBlank()) {
      return displayName;
    }
    return formatName(name);
  }

  public static String formatName(String value) {
    if (value.toLowerCase(Locale.ROOT).matches("[lh]t[1-5]")) {
      return value.toUpperCase(Locale.ROOT);
    }
    String replaced = value.replace('_', ' ').replace('-', ' ');
    String[] words = replaced.split("\\s+");
    StringBuilder sb = new StringBuilder();
    for (String w : words) {
      if (w.isEmpty()) continue;
      if (!sb.isEmpty()) sb.append(' ');
      sb.append(Character.toUpperCase(w.charAt(0)));
      if (w.length() > 1) {
        sb.append(w.substring(1).toLowerCase(Locale.ROOT));
      }
    }
    return sb.isEmpty() ? value : sb.toString();
  }

  public static Difficulty read(String name, ConfigurationSection c) {
    String dispName = c.getString("display-name", c.getString("display_name", null));
    String desc = c.getString("description", null);
    String iconName = c.getString("icon", null);
    Material parsedIcon = null;
    if (iconName != null) {
      try {
        parsedIcon = Material.matchMaterial(iconName);
      } catch (Exception ignored) {
      }
    }
    return new Difficulty(
        name,
        c.getInt("decision-ticks", 4),
        c.getInt("perception-ticks", 2),
        c.getDouble("aim-error", 4.0),
        1.0,
        c.getDouble("shield-chance", 0.5),
        c.getBoolean("counters", false),
        c.getDouble("sprint-reset-chance", 0.5),
        c.getDouble("strafe-strength", .65),
        c.getDouble("heal-threshold", .5),
        c.getInt("shield-ticks", 6),
        true,
        dispName,
        desc,
        parsedIcon);
  }

  public Difficulty passive() {
    return new Difficulty(
        name,
        decisionTicks,
        perceptionTicks,
        aimError,
        attackCharge,
        shieldChance,
        counters,
        sprintResetChance,
        strafeStrength,
        healThreshold,
        shieldTicks,
        false,
        displayName,
        description,
        icon);
  }

  public Difficulty withOptions(dev.insanmiy.practiceplugin.model.BotOptions options) {
    int dec = options.decisionTicks() > 0 ? options.decisionTicks() : decisionTicks;
    int per = options.perceptionTicks() >= 0 ? options.perceptionTicks() : perceptionTicks;
    double aim = options.aimError() >= 0 ? options.aimError() : aimError;
    double charge = options.attackCharge() >= 0.2 ? options.attackCharge() : attackCharge;
    double shield =
        !options.shields()
            ? 0
            : (options.shieldChance() >= 0 ? options.shieldChance() : shieldChance);
    boolean axeCounter = options.counters();
    double sprint =
        options.sprintResetChance() >= 0 ? options.sprintResetChance() : sprintResetChance;
    double strafe =
        !options.strafing()
            ? 0
            : (options.strafeStrength() >= 0 ? options.strafeStrength() : strafeStrength);
    double heal = options.healThreshold() >= 0 ? options.healThreshold() : healThreshold;
    return new Difficulty(
        name,
        dec,
        per,
        aim,
        charge,
        shield,
        axeCounter,
        sprint,
        strafe,
        heal,
        shieldTicks,
        attacksEnabled,
        displayName,
        description,
        icon);
  }
}
