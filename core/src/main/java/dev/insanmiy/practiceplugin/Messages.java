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
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class Messages {
  private static final Pattern HEX_PATTERN =
      Pattern.compile("(&#([A-Fa-f0-9]{6})|<#([A-Fa-f0-9]{6})>)");
  private static final Pattern LEGACY_CODE_PATTERN =
      Pattern.compile("(?i)[&§]([0-9a-fk-or])|&#([A-Fa-f0-9]{6})|§x(§[0-9a-fA-F]){6}");

  private static final LegacyComponentSerializer SERIALIZER =
      LegacyComponentSerializer.legacySection();
  private static final MiniMessage MINI_MESSAGE =
      MiniMessage.miniMessage();

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
    return color(config.getString("prefix", "&9InsynPracticeBot &r"));
  }

  public Component prefixComponent() {
    return toComponent(config.getString("prefix", "&9InsynPracticeBot &r"));
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

  public List<Component> getComponentList(String key, String... placeholders) {
    return getComponentList((CommandSender) null, key, placeholders);
  }

  public List<Component> getComponentList(CommandSender sender, String key, String... placeholders) {
    if (!config.isList(key)) {
      Component single = component(sender, key, placeholders);
      return single.equals(Component.empty()) ? List.of() : List.of(single);
    }
    List<String> rawList = config.getStringList(key);
    List<Component> formatted = new ArrayList<>(rawList.size());
    for (String line : rawList) {
      Component lineComp = formatComponent(sender, line, placeholders);
      if (!lineComp.equals(Component.empty())) {
        formatted.add(lineComp);
      }
    }
    return formatted;
  }

  public Component component(String key, String... placeholders) {
    return component((CommandSender) null, key, placeholders);
  }

  public Component component(CommandSender sender, String key, String... placeholders) {
    if (config.isList(key)) {
      List<Component> list = getComponentList(sender, key, placeholders);
      if (list.isEmpty()) return Component.empty();
      Component combined = list.get(0);
      for (int i = 1; i < list.size(); i++) {
        combined = combined.append(Component.newline()).append(list.get(i));
      }
      return combined;
    }
    String raw = config.getString(key);
    if (raw == null) {
      return toComponent(key);
    }
    return formatComponent(sender, raw, placeholders);
  }

  public void send(CommandSender sender, String key, String... placeholders) {
    if (sender == null) return;
    if (config.isList(key)) {
      for (Component line : getComponentList(sender, key, placeholders)) {
        sender.sendMessage(line);
      }
      return;
    }
    String raw = config.getString(key);
    if (raw == null) return;
    Component comp = formatComponent(sender, raw, placeholders);
    if (!comp.equals(Component.empty())) {
      sender.sendMessage(comp);
    }
  }

  private String formatRaw(CommandSender sender, String raw, String... placeholders) {
    if (raw == null || raw.isEmpty() || raw.equalsIgnoreCase("none")) {
      return "";
    }
    String prefixRaw = config.getString("prefix", "&9InsynPracticeBot &r");
    String formatted = raw.replace("%prefix%", prefixRaw);

    // Custom placeholders defined in messages.yml
    ConfigurationSection customSection = config.getConfigurationSection("placeholders");
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

    return formatted;
  }

  public String formatLine(String raw, String... placeholders) {
    return formatLine((CommandSender) null, raw, placeholders);
  }

  public String formatLine(CommandSender sender, String raw, String... placeholders) {
    return color(formatRaw(sender, raw, placeholders));
  }

  public Component formatComponent(String raw, String... placeholders) {
    return formatComponent((CommandSender) null, raw, placeholders);
  }

  public Component formatComponent(CommandSender sender, String raw, String... placeholders) {
    return toComponent(formatRaw(sender, raw, placeholders));
  }

  public static Component toComponent(String message) {
    if (message == null || message.isEmpty()) return Component.empty();
    if (message.indexOf('<') != -1 && message.indexOf('>') != -1) {
      try {
        String converted = legacyToMiniMessage(message);
        return MINI_MESSAGE.deserialize(converted);
      } catch (Exception ignored) {
        // Fallback to legacy deserializer if MiniMessage parsing encounters unknown or unclosed tags
      }
    }
    return SERIALIZER.deserialize(color(message));
  }

  static String legacyToMiniMessage(String text) {
    if (text == null || text.isEmpty()) return "";
    Matcher matcher = LEGACY_CODE_PATTERN.matcher(text);
    StringBuilder sb = new StringBuilder(text.length() + 32);
    while (matcher.find()) {
      if (matcher.group(1) != null) {
        char code = Character.toLowerCase(matcher.group(1).charAt(0));
        String tag = switch (code) {
          case '0' -> "<black>";
          case '1' -> "<dark_blue>";
          case '2' -> "<dark_green>";
          case '3' -> "<dark_aqua>";
          case '4' -> "<dark_red>";
          case '5' -> "<dark_purple>";
          case '6' -> "<gold>";
          case '7' -> "<gray>";
          case '8' -> "<dark_gray>";
          case '9' -> "<blue>";
          case 'a' -> "<green>";
          case 'b' -> "<aqua>";
          case 'c' -> "<red>";
          case 'd' -> "<light_purple>";
          case 'e' -> "<yellow>";
          case 'f' -> "<white>";
          case 'k' -> "<obfuscated>";
          case 'l' -> "<bold>";
          case 'm' -> "<strikethrough>";
          case 'n' -> "<underlined>";
          case 'o' -> "<italic>";
          case 'r' -> "<reset>";
          default -> "";
        };
        matcher.appendReplacement(sb, tag);
      } else if (matcher.group(2) != null) {
        matcher.appendReplacement(sb, "<#" + matcher.group(2) + ">");
      } else {
        String full = matcher.group(0);
        String hex = full.replace("§x", "").replace("§", "").replace("§X", "");
        matcher.appendReplacement(sb, "<#" + hex + ">");
      }
    }
    matcher.appendTail(sb);
    return sb.toString();
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
