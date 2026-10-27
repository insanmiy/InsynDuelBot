package dev.insanmiy.practiceplugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

public final class Messages {
  private static final Pattern HEX_PATTERN =
      Pattern.compile("(&#([A-Fa-f0-9]{6})|<#([A-Fa-f0-9]{6})>)");
  private static final LegacyComponentSerializer SERIALIZER =
      LegacyComponentSerializer.legacySection();

  private final PracticePlugin plugin;
  private YamlConfiguration config = new YamlConfiguration();

  public Messages(PracticePlugin plugin) {
    this.plugin = plugin;
    reload();
  }

  public void reload() {
    File file = new File(plugin.getDataFolder(), "messages.yml");
    if (!file.exists()) {
      try {
        plugin.saveResource("messages.yml", false);
      } catch (Exception e) {
        plugin.getLogger().warning("Could Not Save Default messages.yml: " + e.getMessage());
      }
    }

    YamlConfiguration loaded = new YamlConfiguration();
    if (file.exists()) {
      try (InputStreamReader reader =
          new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
        loaded.load(reader);
      } catch (Exception e) {
        plugin.getLogger().severe("Could Not Load messages.yml: " + e.getMessage());
      }
    }

    try (InputStream defStream = plugin.getResource("messages.yml")) {
      if (defStream != null) {
        try (InputStreamReader defReader =
            new InputStreamReader(defStream, StandardCharsets.UTF_8)) {
          YamlConfiguration defaults = YamlConfiguration.loadConfiguration(defReader);
          loaded.setDefaults(defaults);
        }
      }
    } catch (Exception e) {
      plugin.getLogger().warning("Could Not Load Default messages.yml Fallback: " + e.getMessage());
    }

    this.config = loaded;
  }

  public String prefix() {
    return color(config.getString("prefix", "&8[&bInsynPracticeBot&8] &r"));
  }

  public String get(String key, String... placeholders) {
    return get((CommandSender) null, key, placeholders);
  }

  public String get(CommandSender sender, String key, String... placeholders) {
    if (config.isList(key)) {
      List<String> list = getList(sender, key, placeholders);
      return String.join("\n", list);
    }
    String raw = config.getString(key);
    if (raw == null) {
      return key;
    }
    return formatLine(sender, raw, placeholders);
  }

  public List<String> getList(String key, String... placeholders) {
    return getList((CommandSender) null, key, placeholders);
  }

  public List<String> getList(CommandSender sender, String key, String... placeholders) {
    if (!config.isList(key)) {
      String single = get(sender, key, placeholders);
      return single.isEmpty() ? List.of() : List.of(single);
    }
    List<String> rawList = config.getStringList(key);
    List<String> formatted = new ArrayList<>(rawList.size());
    for (String line : rawList) {
      String lineFormatted = formatLine(sender, line, placeholders);
      if (!lineFormatted.isEmpty()) {
        formatted.add(lineFormatted);
      }
    }
    return formatted;
  }

  public Component component(String key, String... placeholders) {
    return component((CommandSender) null, key, placeholders);
  }

  public Component component(CommandSender sender, String key, String... placeholders) {
    return SERIALIZER.deserialize(get(sender, key, placeholders));
  }

  public void send(CommandSender sender, String key, String... placeholders) {
    if (sender == null) return;
    if (config.isList(key)) {
      for (String line : getList(sender, key, placeholders)) {
        if (!line.isEmpty()) {
          sender.sendMessage(line);
        }
      }
      return;
    }
    String formatted = get(sender, key, placeholders);
    if (!formatted.isEmpty()) {
      sender.sendMessage(formatted);
    }
  }

  public String formatLine(String raw, String... placeholders) {
    return formatLine((CommandSender) null, raw, placeholders);
  }

  public String formatLine(CommandSender sender, String raw, String... placeholders) {
    if (raw == null) return "";
    if (raw.isEmpty() || raw.equalsIgnoreCase("none")) {
      return "";
    }
    String formatted = raw.replace("%prefix%", prefix());

    // Custom placeholders defined in messages.yml
    org.bukkit.configuration.ConfigurationSection customSection = config.getConfigurationSection("placeholders");
    if (customSection != null) {
      for (String customKey : customSection.getKeys(false)) {
        String customVal = customSection.getString(customKey, "");
        formatted = formatted.replace(customKey, customVal);
      }
    }

    // Contextual placeholders
    for (int i = 0; i + 1 < placeholders.length; i += 2) {
      String target = placeholders[i];
      String replacement = placeholders[i + 1];
      if (target != null && replacement != null) {
        formatted = formatted.replace(target, replacement);
      }
    }

    // PlaceholderAPI support for player recipients
    if (sender instanceof org.bukkit.entity.Player player) {
      formatted = dev.insanmiy.practiceplugin.papi.PapiHook.parse(player, formatted);
    }

    return color(formatted);
  }

  public static String color(String message) {
    if (message == null || message.isEmpty()) return "";
    Matcher matcher = HEX_PATTERN.matcher(message);
    StringBuilder buffer = new StringBuilder(message.length() + 32);
    while (matcher.find()) {
      String hex = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
      StringBuilder replacement = new StringBuilder("\u00A7x");
      for (char c : hex.toCharArray()) {
        replacement.append('\u00A7').append(c);
      }
      matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement.toString()));
    }
    matcher.appendTail(buffer);
    return ChatColor.translateAlternateColorCodes('&', buffer.toString());
  }
}
