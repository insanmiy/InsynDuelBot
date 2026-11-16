# Commands

The main command is `/practice`. Aliases are `/p` and `/prac`.

Arguments in `<angle brackets>` are required. Arguments in `[square brackets]` are optional. Type commands without the brackets. Tab completion helps find valid names.

## Player commands

| Command | Action |
| --- | --- |
| `/practice` or `/practice menu` | Open practice setup |
| `/practice start [kit] [difficulty] [mode] [bestOf]` | Start a match |
| `/practice stop` | End your session |
| `/practice pause` | Pause your session |
| `/practice resume` | Resume your session |
| `/practice rematch` | Reuse your previous match selection |
| `/practice settings` | Open match settings and bot tuning |
| `/practice preset` | Open your bot presets |
| `/practice preset save <name>` | Save or replace a bot preset |
| `/practice preset load <name>` | Load a bot preset |
| `/practice preset delete <name>` | Delete a bot preset |
| `/practice preset list` | List your bot presets |
| `/practice hud` | Toggle the health display during your session |
| `/practice damage` | Toggle damage diagnostics during your session |
| `/practice stats` | View your win and loss statistics |
| `/practice kits` | List available kits |
| `/practice difficulties` | List available difficulties |
| `/practice kit info <name>` | Show a kit summary |
| `/practice kit editor` | Open the kit editor |
| `/practice status` | Show sessions and arena status |

Omitted start arguments use your current selection or defaults. Stop your active session before opening settings, managing presets, or selecting another kit.

See [kits](Kits) for kit management commands and [bot presets](Bot-Presets) for preset behavior.

## Administrative commands

| Command | Action |
| --- | --- |
| `/practice stop <player>` | End another player's session |
| `/practice arena list` | Show arena readiness |
| `/practice arena setplayer [name]` | Set the player spawn |
| `/practice arena setbot [name]` | Set the bot spawn |
| `/practice arena setradius <radius> [name]` | Set the arena radius |
| `/practice arena info [name]` | Show arena details |
| `/practice arena tp [name]` | Teleport to an arena |
| `/practice arena delete <name>` | Remove arena setup |
| `/practice reload` | Reload configuration and messages |

Stop all sessions before reloading. See [permissions](Permissions) for access requirements.
