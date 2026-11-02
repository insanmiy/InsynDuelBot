# Configuration

Configuration Files Are Stored In `plugins/InsynDuelBot/`.

| File | Purpose |
| --- | --- |
| `config.yml` | Arena Settings, Session Defaults, And Difficulties |
| `kits.yml` | Built In Kit Definitions |
| `customkits.yml` | Saved Custom Kits |
| `messages.yml` | Messages, Formatting, And Custom Placeholders |
| `player-settings.yml` | Player Preferences And Personal Bot Presets |

Stop All Matches Before Running `/practice reload` To Reload Configuration And Messages. Existing Player Preferences Can Override Defaults For Their Sessions.

## Session Defaults

The Shipped Configuration Uses `diamond-smp`, Difficulty `lt4`, And A Best Of Three `match`. Other Settings Include Countdown Length, Break Between Rounds, Starting Saturation, And Round Timeout.

`session.round-timeout-seconds: 0` Disables The Round Timeout. Leave `config-version` Unchanged; The Plugin Uses It For Configuration Upgrades.

See [Arenas](Arenas) For Spawn Commands And [Bot Settings](Bot-Settings) For Player Overrides.

## Difficulty Definitions

Difficulty Entries Can Set Aim Error, Decision And Perception Ticks, Attack Charge, Shield Behavior, Sprint Reset Chance, Strafe Strength, Healing Threshold, Critical Chance, And Pearl Chance.

The Comments In The Shipped [config.yml](https://github.com/insanmiy/InsynDuelBot/blob/main/core/src/main/resources/config.yml) Explain The Available Fields And Ranges.

## Messages

`messages.yml` Supports Legacy Color Codes Such As `&a`, Hex Colors Such As `&#55FF55`, And MiniMessage Tags Such As `<green>Ready</green>`.

Set A Message To `""` Or `"none"` To Hide It. Message Lists Allow Multiple Lines. The `placeholders` Section Lets You Define Reusable Text Replacements.

## PlaceholderAPI

PlaceholderAPI Is Optional. When Installed And Enabled, The Plugin Registers Its Own `practice` Expansion And Can Parse Placeholders In Messages Sent To Players.

Examples:

| Placeholder | Value |
| --- | --- |
| `%practice_in_match%` | Whether The Player Has A Session |
| `%practice_fighting%` | Whether Combat Is Active |
| `%practice_paused%` | Whether The Session Is Paused |
| `%practice_current_kit%` | Current Kit Name |
| `%practice_current_difficulty%` | Current Difficulty Display Name |
| `%practice_current_mode%` | Current Mode |
| `%practice_current_arena%` | Current Arena |
| `%practice_round%` | Current Round |
| `%practice_player_score%` | Player's Score In The Current Session |
| `%practice_bot_score%` | Bot's Score In The Current Session |
| `%practice_wins%` | Saved Total Rounds Won |
| `%practice_losses%` | Saved Total Rounds Lost |

Mode Totals Include `%practice_duel_wins%`, `%practice_match_wins%`, `%practice_endless_wins%`, `%practice_2v1_wins%`, And `%practice_3v1_wins%`. Replace `wins` With `losses` For Rounds Lost. Despite Their Names, These Count Rounds, Not Completed Series.
