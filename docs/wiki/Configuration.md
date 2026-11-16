# Configuration

Configuration files are stored in `plugins/InsynDuelBot/`.

| File | Purpose |
| --- | --- |
| `config.yml` | Arena settings, session defaults, and difficulties |
| `kits.yml` | Built in kit definitions |
| `customkits.yml` | Saved custom kits |
| `messages.yml` | Messages, formatting, and custom placeholders |
| `player-settings.yml` | Player preferences and personal bot presets |

Stop all matches before running `/practice reload` to reload configuration and messages. Existing player preferences can override defaults for their sessions.

## Session defaults

The shipped configuration uses `diamond-smp`, difficulty `lt4`, and a best of three `match`. Other settings include countdown length, break between rounds, starting saturation, and round timeout.

`session.round-timeout-seconds: 0` disables the round timeout. Leave `config-version` unchanged; the plugin uses it for configuration upgrades.

See [arenas](Arenas) for spawn commands and [bot settings](Bot-Settings) for player overrides.

## Difficulty definitions

Difficulty entries can set aim error, decision and perception ticks, attack charge, shield behavior, sprint reset chance, strafe strength, healing threshold, critical chance, and pearl chance.

The comments in the shipped [config.yml](https://github.com/insanmiy/InsynDuelBot/blob/main/core/src/main/resources/config.yml) explain the available fields and ranges.

## Messages

Default chat messages, menu labels, item descriptions, and log messages use sentence case. Edit `messages.yml` to change the wording. Kit descriptions and difficulty display names are configured in `kits.yml`, `customkits.yml`, and `config.yml`.

The `text` section groups labels, tooltips, errors, and console messages by their source screen or system. Some entries are fragments joined to runtime values, so preserve any leading or trailing spaces. `hud` contains full health bar and action bar templates, and `titles` contains victory and defeat text. Named placeholders such as `%health%`, `%round%`, and `%difficulty%` can be moved within their templates. Keep Java format placeholders such as `%s`, `%d`, and `%.1f` intact where they appear.

Generated material, enchantment, kit, and setting labels can be overridden under `names`. For example, `names.diamond_sword: "Training sword"` changes that generated label. Use lowercase identifiers and replace hyphens with underscores. Player names, arena names, saved preset names, and item names supplied by Minecraft or other plugins remain their own data.

Use an empty string to hide an editable label. Stop matches and run `/practice reload` after editing; reopen any menu that was already open. New menus, mode descriptions, HUD updates, errors, and console messages use the reloaded text. There is no migration of old message files. To use the shipped sentence case defaults on an existing installation, save a copy of your `messages.yml`, remove it, restart the plugin to generate the current file, and reapply the custom text you want to keep.

`messages.yml` supports legacy color codes such as `&a`, hex colors such as `&#55FF55`, and MiniMessage tags such as `<green>Ready</green>`.

Set a message to `""` or `"none"` to hide it. Message lists allow multiple lines. The `placeholders` section lets you define reusable text replacements.

## PlaceholderAPI

PlaceholderAPI is optional. When installed and enabled, the plugin registers its own `practice` expansion and can parse placeholders in messages sent to players.

Examples:

| Placeholder | Value |
| --- | --- |
| `%practice_in_match%` | Whether the player has a session |
| `%practice_fighting%` | Whether combat is active |
| `%practice_paused%` | Whether the session is paused |
| `%practice_current_kit%` | Current kit name |
| `%practice_current_difficulty%` | Current difficulty display name |
| `%practice_current_mode%` | Current mode |
| `%practice_current_arena%` | Current arena |
| `%practice_round%` | Current round |
| `%practice_player_score%` | Player's score in the current session |
| `%practice_bot_score%` | Bot's score in the current session |
| `%practice_wins%` | Saved total rounds won |
| `%practice_losses%` | Saved total rounds lost |

Mode totals include `%practice_duel_wins%`, `%practice_match_wins%`, `%practice_endless_wins%`, `%practice_2v1_wins%`, and `%practice_3v1_wins%`. Replace `wins` with `losses` for rounds lost. Despite their names, these count rounds, not completed series.
