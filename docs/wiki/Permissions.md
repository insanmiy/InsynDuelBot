# Permissions

The Main Command Requires `practice.use`. Individual Actions Also Check Their Own Permissions. The Shipped Defaults Give Players Normal Practice And Kit Access; Administrative Permissions Default To Operators.

## Player Permissions

| Permission | Access |
| --- | --- |
| `practice.use` | Base Access And Normal Player Permission Children |
| `practice.menu` | Practice Setup Menu |
| `practice.start` | Start A Match |
| `practice.stop` | Stop Your Own Match |
| `practice.pause` | Pause And Resume |
| `practice.rematch` | Rematch |
| `practice.settings` | Settings And Personal Bot Presets |
| `practice.hud` | Health Display Toggle |
| `practice.damage` | Damage Diagnostics Toggle |
| `practice.stats` | Personal Statistics |
| `practice.status` | Session And Arena Status |
| `practice.kits.list` | Kit Listing And Kit Information |
| `practice.difficulties.list` | Difficulty Listing |

## Kit Permissions

| Permission | Access |
| --- | --- |
| `practice.kits` | Kit Management Access And Its Shipped Child Permissions |
| `practice.kits.*` | Editor, Creation, Import, And Deletion Permissions |
| `practice.kits.editor` | Kit Editor And Editing Custom Kits |
| `practice.kits.create` | Creating And Copying Kits |
| `practice.kits.import` | Importing Player Inventory |
| `practice.kits.delete` | Deleting Custom Kits |
| `practice.kits.bypass` | Modifying Kits Owned By Other Players |

## Administrative Permissions

| Permission | Access |
| --- | --- |
| `practice.admin` | Administrative Commands, Kit Ownership Bypass, And Normal Practice Access |
| `practice.arena` | Arena Management |
| `practice.reload` | Configuration Reload |
| `practice.stop.other` | Stopping Another Player's Session |
| `practice.*` | Full Plugin Access |

Permission Groups Include Child Permissions. When Restricting A Feature, Check Its Effective Permission In Your Permission Manager, Including Inherited Grants And Operator Status.

See [plugin.yml](https://github.com/insanmiy/InsynDuelBot/blob/main/core/src/main/resources/plugin.yml) For The Exact Hierarchy And Defaults.
