# Commands

The Main Command Is `/practice`. Aliases Are `/p` And `/prac`.

Arguments In `<angle brackets>` Are Required. Arguments In `[square brackets]` Are Optional. Type Commands Without The Brackets. Tab Completion Helps Find Valid Names.

## Player Commands

| Command | Action |
| --- | --- |
| `/practice` Or `/practice menu` | Open Practice Setup |
| `/practice start [kit] [difficulty] [mode] [bestOf]` | Start A Match |
| `/practice stop` | End Your Session |
| `/practice pause` | Pause Your Session |
| `/practice resume` | Resume Your Session |
| `/practice rematch` | Reuse Your Previous Match Selection |
| `/practice settings` | Open Match Settings And Bot Tuning |
| `/practice preset` | Open Your Bot Presets |
| `/practice preset save <name>` | Save Or Replace A Bot Preset |
| `/practice preset load <name>` | Load A Bot Preset |
| `/practice preset delete <name>` | Delete A Bot Preset |
| `/practice preset list` | List Your Bot Presets |
| `/practice hud` | Toggle The Health Display During Your Session |
| `/practice damage` | Toggle Damage Diagnostics During Your Session |
| `/practice stats` | View Your Win And Loss Statistics |
| `/practice kits` | List Available Kits |
| `/practice difficulties` | List Available Difficulties |
| `/practice kit info <name>` | Show A Kit Summary |
| `/practice kit editor` | Open The Kit Editor |
| `/practice status` | Show Sessions And Arena Status |

Omitted Start Arguments Use Your Current Selection Or Defaults. Stop Your Active Session Before Opening Settings, Managing Presets, Or Selecting Another Kit.

See [Kits](Kits) For Kit Management Commands And [Bot Presets](Bot-Presets) For Preset Behavior.

## Administrative Commands

| Command | Action |
| --- | --- |
| `/practice stop <player>` | End Another Player's Session |
| `/practice arena list` | Show Arena Readiness |
| `/practice arena setplayer [name]` | Set The Player Spawn |
| `/practice arena setbot [name]` | Set The Bot Spawn |
| `/practice arena setradius <radius> [name]` | Set The Arena Radius |
| `/practice arena info [name]` | Show Arena Details |
| `/practice arena tp [name]` | Teleport To An Arena |
| `/practice arena delete <name>` | Remove Arena Setup |
| `/practice reload` | Reload Configuration And Messages |

Stop All Sessions Before Reloading. See [Permissions](Permissions) For Access Requirements.
