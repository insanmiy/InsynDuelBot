# Troubleshooting

## The Plugin Does Not Start

Check The Server Log For The First InsynDuelBot Startup Error. Confirm That The Server Uses Java 21 And A Targeted Paper Version. The Plugin Targets 1.20.5 Through 1.21.11, But Every Version Has Not Been Fully Verified Through Gameplay.

An NMS Adapter Error Usually Needs The Exact Paper Version And Full Error Trace To Diagnose.

## No Free Arena

Run `/practice arena list`. Check That Both Spawns Are Set, In The Same Loaded World, On Solid Ground, And At Least Four Blocks Apart.

An Arena May Also Be Occupied Or Too Close To An Active Arena. See [Arenas](Arenas).

## The Bot Does Not Attack

Check Your Selected Difficulty. `dummy` Intentionally Disables Attacks. Confirm The Match Has Finished Its Countdown And Is Not Paused.

## The Bot Ignores My Difficulty Settings

Your Personal Advanced Tuning Can Override The Selected Difficulty. Open `/practice settings` And Reset Advanced Tuning, Or Shift Click An Individual Setting To Restore Its Difficulty Default. Ability Toggles Are Kept When Advanced Tuning Is Reset.

Also Check Whether You Loaded A [Bot Preset](Bot-Presets) With Custom Overrides.

## The Bot Gets Stuck

Recovery Can Try A Detour, But Some Locations Are Unreachable. Test On Open Ground, Check Spawn Headroom, And Record The Terrain Or Blocks Involved If The Problem Repeats.

## Presets Or Kits Cannot Be Changed

Stop Your Active Session First. Check The Reported Permission, Kit Ownership, And Name Format. Preset Names And Kit Names Accept Letters, Numbers, Underscores, And Hyphens, Up To 32 Characters.

## Reload Is Refused

All Matches Must End Before `/practice reload` Can Run. Administrators Can Use `/practice stop <player>` To End Another Player's Session.

## The Build Reports An Invalid JAVA_HOME

Set `JAVA_HOME` To Your JDK 21 Installation Folder, Then Run The Gradle Wrapper Again. See [Getting Started](Getting-Started).

## Report A Bug

[Open An Issue](https://github.com/insanmiy/InsynDuelBot/issues) With:

* Your Plugin Version And Exact Paper Version.
* The Kit, Difficulty, Mode, And Any Custom Bot Settings.
* The Steps Needed To Reproduce The Problem.
* Relevant Server Log Output And Screenshots Or A Short Video When Useful.
