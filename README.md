# InsynDuelBot

Practice Minecraft PvP Against A Custom Bot On Your Paper Server. Choose A Kit, Adjust The Bot's Difficulty, And Fight A Single Duel, A Series, Or An Outnumbered Match.

![License](https://img.shields.io/github/license/insanmiy/InsynDuelBot)
![Java](https://img.shields.io/badge/Java-21-orange)
![Paper](https://img.shields.io/badge/Paper-1.20.5--1.21.11-blue)

## Features

* Combat Movement With Strafing, Sprint Resetting, Shield Use, And Critical Hits.
* Healing And Utility Item Use, Controlled Through Bot Settings.
* Navigation With Safe Recovery Detours When The Bot Gets Stuck.
* Built In Kits And A Custom Kit Editor.
* Adjustable Aim, Reaction Speed, Attack Timing, And Other Combat Settings.
* Personal Bot Presets Saved Across Server Restarts.
* Match Health Display, Win And Loss Statistics, And Combat Damage Diagnostics.
* Configurable Messages With Legacy Colors, Hex Colors, And MiniMessage Support.
* Optional PlaceholderAPI Integration.

## Requirements

| Requirement | Details |
| --- | --- |
| Server | Paper 1.20.5 Through 1.21.11 |
| Java | Java 21 |
| Optional Plugin | PlaceholderAPI |

The Plugin Includes Multiple NMS Adapters For This Minecraft Version Range. Compatibility Across Every Listed Version Has Not Been Fully Verified Through Live Gameplay.

## Installation And First Match

1. Build The Plugin Using The Instructions Below.
2. Copy The JAR From `build/libs/` Into Your Paper Server's `plugins/` Folder.
3. Start The Server To Generate The Configuration Files.
4. As An Operator Or A Player With `practice.arena`, Stand At Your Desired Player Spawn And Run `/practice arena setplayer`.
5. Move To The Bot Spawn And Run `/practice arena setbot`. Both Spawns Must Be In The Same World, At Least Four Blocks Apart, With Solid Floors And Two Clear Blocks Of Headroom.
6. Run `/practice arena list` To Check That The Arena Is Ready.
7. Open `/practice`, Choose Your Kit, Difficulty, And Mode, Then Start Practicing.

Use `/practice stop` To End Your Session.

For Additional Arenas, Add A Name To Both Spawn Commands:

```text
/practice arena setplayer courtyard
/practice arena setbot courtyard
/practice arena setradius 32 courtyard
```

## Practice Modes

| Mode | Description |
| --- | --- |
| `duel` | One Round Against One Bot |
| `match` | A Best Of Series Against One Bot |
| `endless` | Continuous Sparring With Repeated Rounds |
| `2v1` | A Best Of Series Against Two Bots |
| `3v1` | A Best Of Series Against Three Bots |

## Commands

Aliases: `/p` And `/prac`.

| Command | Action |
| --- | --- |
| `/practice` | Open Practice Setup |
| `/practice start [kit] [difficulty] [mode] [bestOf]` | Start With Your Selected Or Supplied Settings |
| `/practice stop` | End Your Current Session |
| `/practice pause` | Pause The Current Session |
| `/practice resume` | Resume A Paused Session |
| `/practice rematch` | Start Again With Your Previous Match Selection |
| `/practice settings` | Configure Match Settings, Bot Abilities, And Advanced Tuning |
| `/practice preset` | Open Your Saved Bot Presets |
| `/practice hud` | Toggle The Health Display During A Session |
| `/practice stats` | View Your Practice Results |
| `/practice damage` | Toggle Damage Diagnostics During A Session |
| `/practice kits` | List Available Kits |
| `/practice difficulties` | List Available Difficulties |
| `/practice kit editor` | Open The Custom Kit Editor |
| `/practice arena list` | Check Arena Setup And Availability |
| `/practice status` | View Sessions And Configuration Status |
| `/practice reload` | Reload Configuration And Messages After Stopping All Sessions |

## Bot Presets

Configure The Bot Through `/practice settings`, Then Save A Named Preset:

```text
/practice preset save sword_practice
/practice preset load sword_practice
/practice preset list
/practice preset delete sword_practice
```

Each Player Can Save Up To 21 Personal Presets. Presets Include Bot Abilities And Advanced Combat Tuning. Match Timing, Starting Saturation, HUD, And Menu Animations Stay Separate.

Saving An Existing Name Replaces That Preset. Loading A Preset Applies Its Settings To Your Next Session. Presets Can Be Managed When You Are Outside An Active Match.

In The Presets Menu, Left Click To Load, Shift Left Click To Replace A Preset With Your Current Bot Settings, Or Shift Right Click To Delete It.

## Configuration And Permissions

| File | Purpose |
| --- | --- |
| `config.yml` | Arena Settings, Session Defaults, And Bot Difficulties |
| `kits.yml` | Built In Kit Definitions |
| `customkits.yml` | Saved Custom Kits |
| `messages.yml` | Messages, Formatting, And Custom Placeholders |
| `player-settings.yml` | Individual Player Settings And Bot Presets |

The Base Permission Is `practice.use`. Bot Settings And Presets Use `practice.settings`. Administrative Permissions Include `practice.arena`, `practice.reload`, And `practice.stop.other`.

Operators Receive `practice.admin` By Default. See [plugin.yml](core/src/main/resources/plugin.yml) For The Full Permission List And Defaults.

## Building From Source

Install JDK 21 And Set `JAVA_HOME` To Its Installation Folder. Run The Included Gradle Wrapper From The Repository Root. A Separate Gradle Installation Is Not Required.

Windows PowerShell:

```powershell
.\gradlew.bat build
```

Linux Or macOS:

```sh
./gradlew build
```

The Plugin JAR Is Written To `build/libs/`. The Build Also Runs The Automated Tests.

## Support And Contributing

Found A Bug Or Have An Idea? [Open An Issue](../../issues). For Bug Reports, Include Your Paper Version, Plugin Version, Relevant Server Log Output, And The Steps Needed To Reproduce The Problem.

Pull Requests Are Welcome.

## License

Licensed Under The [MIT License](LICENSE).
