# InsynDuelBot

Practice Minecraft PvP against a custom bot on your Paper server. Choose a kit, adjust the bot's difficulty, and fight a single duel, a series, or an outnumbered match.

![License](https://img.shields.io/github/license/insanmiy/InsynDuelBot)
![Java](https://img.shields.io/badge/Java-21-orange)
![Paper](https://img.shields.io/badge/Paper-1.20.5--1.21.11-blue)

## Documentation

See the [GitHub wiki](https://github.com/insanmiy/InsynDuelBot/wiki) for setup, commands, bot settings, presets, and troubleshooting. Documentation sources are in [docs](docs/README.md).

Quick links: [getting started](docs/wiki/Getting-Started.md), [arenas](docs/wiki/Arenas.md), [bot settings](docs/wiki/Bot-Settings.md), [kits](docs/wiki/Kits.md), and [troubleshooting](docs/wiki/Troubleshooting.md).

## Features

* Combat movement with strafing, sprint resetting, shield use, and critical hits.
* Healing and utility item use, controlled through bot settings.
* Navigation with safe recovery detours when the bot gets stuck.
* Direct sprint jumping on clear routes with no Speed effect or Speed I; Speed II and higher use sprinting without chase jumps.
* Water can wash away blocks and waterlog supported blocks, with original block data kept for cleanup.
* Built in kits and a custom kit editor.
* Adjustable aim, reaction speed, attack timing, and other combat settings.
* Personal bot presets saved across server restarts.
* Match health display, win and loss statistics, and combat damage diagnostics.
* Editable chat, menus, HUD, titles, tooltips, errors, and plugin logs.
* Legacy colors, hex colors, and MiniMessage support for chat, menus, and HUD text.
* Optional PlaceholderAPI integration.

## Requirements

| Requirement | Details |
| --- | --- |
| Server | Paper 1.20.5 through 1.21.11 |
| Java | Java 21 |
| Optional plugin | PlaceholderAPI |

The plugin includes multiple NMS adapters for this Minecraft version range. CI runs live server checks for bot combat, healing, and session recovery across the listed versions. See [development checks](docs/Development-Checks.md) for the test command and coverage. Client rendering and every utility item still require manual gameplay testing.

## Installation and first match

1. Build the plugin using the instructions below.
2. Copy the JAR from `build/libs/` into your Paper server's `plugins/` folder.
3. Start the server to generate the configuration files.
4. As an operator or a player with `practice.arena`, stand at your desired player spawn and run `/practice arena setplayer`.
5. Move to the bot spawn and run `/practice arena setbot`. Both spawns must be in the same world, at least four blocks apart, with solid floors and two clear blocks of headroom.
6. Run `/practice arena list` to check that the arena is ready.
7. Open `/practice`, choose your kit, difficulty, and mode, then start practicing.

Use `/practice stop` to end your session.

For additional arenas, add a name to both spawn commands:

```text
/practice arena setplayer courtyard
/practice arena setbot courtyard
/practice arena setradius 32 courtyard
```

## Practice modes

| Mode | Description |
| --- | --- |
| `duel` | One round against one bot |
| `match` | A best of series against one bot |
| `endless` | Continuous sparring with repeated rounds |
| `2v1` | A best of series against two bots |
| `3v1` | A best of series against three bots |

## Commands

Aliases: `/p` and `/prac`.

| Command | Action |
| --- | --- |
| `/practice` | Open practice setup |
| `/practice start [kit] [difficulty] [mode] [bestOf]` | Start with your selected or supplied settings |
| `/practice stop` | End your current session |
| `/practice pause` | Pause the current session |
| `/practice resume` | Resume a paused session |
| `/practice rematch` | Start again with your previous match selection |
| `/practice settings` | Configure match settings, bot abilities, and advanced tuning |
| `/practice preset` | Open your saved bot presets |
| `/practice hud` | Toggle the health display during a session |
| `/practice stats` | View your practice results |
| `/practice damage` | Toggle damage diagnostics during a session |
| `/practice kits` | List available kits |
| `/practice difficulties` | List available difficulties |
| `/practice kit editor` | Open the custom kit editor |
| `/practice arena list` | Check arena setup and availability |
| `/practice status` | View sessions and configuration status |
| `/practice reload` | Reload configuration and messages after stopping all sessions |

## Bot presets

Configure the bot through `/practice settings`, then save a named preset:

```text
/practice preset save sword_practice
/practice preset load sword_practice
/practice preset list
/practice preset delete sword_practice
```

Each player can save up to 21 personal presets. Presets include bot abilities and advanced combat tuning. Match timing, starting saturation, HUD, and menu animations stay separate.

Saving an existing name replaces that preset. Loading a preset applies its settings to your next session. Presets can be managed when you are outside an active match.

In the presets menu, left click to load, shift left click to replace a preset with your current bot settings, or shift right click to delete it.

## Configuration and permissions

| File | Purpose |
| --- | --- |
| `config.yml` | Arena settings, session defaults, and bot difficulties |
| `kits.yml` | Built in kit definitions |
| `customkits.yml` | Saved custom kits |
| `messages.yml` | Messages, formatting, and custom placeholders |
| `player-settings.yml` | Individual player settings and bot presets |
| `stats.yml` | Player results and combat totals by practice mode |

The base permission is `practice.use`. Bot settings and presets use `practice.settings`. Administrative permissions include `practice.arena`, `practice.reload`, and `practice.stop.other`.

Operators receive `practice.admin` by default. See [plugin.yml](core/src/main/resources/plugin.yml) for the full permission list and defaults.

## Building from source

Install JDK 21 and set `JAVA_HOME` to its installation folder. Run the included Gradle wrapper from the repository root. A separate Gradle installation is not required.

Windows PowerShell:

```powershell
.\gradlew.bat build
```

Linux or macOS:

```sh
./gradlew build
```

The plugin JAR is written to `build/libs/`. The build also runs the automated tests.

## Support and contributing

Found a bug or have an idea? [Open an issue](../../issues). For bug reports, include your Paper version, plugin version, relevant server log output, and the steps needed to reproduce the problem.

Pull requests are welcome.

## License

Licensed under the [MIT license](LICENSE).
