# Permissions

The main command requires `practice.use`. Individual actions also check their own permissions. The shipped defaults give players normal practice and kit access; administrative permissions default to operators.

## Player permissions

| Permission | Access |
| --- | --- |
| `practice.use` | Base access and normal player permission children |
| `practice.menu` | Practice setup menu |
| `practice.start` | Start a match |
| `practice.stop` | Stop your own match |
| `practice.pause` | Pause and resume |
| `practice.rematch` | Rematch |
| `practice.settings` | Settings and personal bot presets |
| `practice.hud` | Health display toggle |
| `practice.damage` | Damage diagnostics toggle |
| `practice.stats` | Personal statistics |
| `practice.status` | Session and arena status |
| `practice.kits.list` | Kit listing and kit information |
| `practice.difficulties.list` | Difficulty listing |

## Kit permissions

| Permission | Access |
| --- | --- |
| `practice.kits` | Kit management access and its shipped child permissions |
| `practice.kits.*` | Editor, creation, import, and deletion permissions |
| `practice.kits.editor` | Kit editor and editing custom kits |
| `practice.kits.create` | Creating and copying kits |
| `practice.kits.import` | Importing player inventory |
| `practice.kits.delete` | Deleting custom kits |
| `practice.kits.bypass` | Modifying kits owned by other players |

## Administrative permissions

| Permission | Access |
| --- | --- |
| `practice.admin` | Administrative commands, kit ownership bypass, and normal practice access |
| `practice.arena` | Arena management |
| `practice.reload` | Configuration reload |
| `practice.stop.other` | Stopping another player's session |
| `practice.*` | Full plugin access |

Permission groups include child permissions. When restricting a feature, check its effective permission in your permission manager, including inherited grants and operator status.

See [plugin.yml](https://github.com/insanmiy/InsynDuelBot/blob/main/core/src/main/resources/plugin.yml) for the exact hierarchy and defaults.
