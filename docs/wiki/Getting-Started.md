# Getting Started

## Install

1. Build The Plugin Using The Instructions At The Bottom.
2. Copy The Plugin JAR From `build/libs/` Into Your Paper Server's `plugins/` Folder.
3. Start The Server And Check Its Log For Plugin Startup Errors.

Use Java 21. The Plugin Targets Paper 1.20.5 Through 1.21.11, With Compatibility Still Requiring Gameplay Testing Across The Full Range. PlaceholderAPI Is Optional.

## Set Up Your First Arena

As An Operator, Stand At The Player Spawn And Run:

```text
/practice arena setplayer
```

Move At Least Four Blocks Away To The Bot Spawn And Run:

```text
/practice arena setbot
/practice arena list
```

Both Spawns Must Be In The Same World, On Solid Ground, With Two Clear Blocks Of Headroom. See [Arenas](Arenas) For Additional Arenas And Radius Settings.

## Start Practicing

Open `/practice`, Choose A Kit, Difficulty, And Mode, Then Start The Match. Use `/practice stop` When You Want To Leave.

You Can Also Start Directly:

```text
/practice start diamond-smp lt4 match 3
```

This Starts A Best Of Three Match Against One Bot. [Bot Settings](Bot-Settings) Can Override Parts Of Your Selected Difficulty.

## Build From Source

Install JDK 21 And Set `JAVA_HOME` To Its Installation Folder. Run The Included Gradle Wrapper From The Repository Root. A Separate Gradle Installation Is Not Required.

Windows PowerShell:

```powershell
.\gradlew.bat build
```

Linux Or macOS:

```sh
./gradlew build
```

The Build Runs The Tests And Writes The Plugin JAR To `build/libs/`.
