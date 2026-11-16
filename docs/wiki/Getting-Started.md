# Getting started

## Install

1. Build the plugin using the instructions at the bottom.
2. Copy the plugin JAR from `build/libs/` into your Paper server's `plugins/` folder.
3. Start the server and check its log for plugin startup errors.

Use Java 21. The plugin targets Paper 1.20.5 through 1.21.11, with compatibility still requiring gameplay testing across the full range. PlaceholderAPI is optional.

## Set up your first arena

As an operator, stand at the player spawn and run:

```text
/practice arena setplayer
```

Move at least four blocks away to the bot spawn and run:

```text
/practice arena setbot
/practice arena list
```

Both spawns must be in the same world, on solid ground, with two clear blocks of headroom. See [arenas](Arenas) for additional arenas and radius settings.

## Start practicing

Open `/practice`, choose a kit, difficulty, and mode, then start the match. Use `/practice stop` when you want to leave.

You can also start directly:

```text
/practice start diamond-smp lt4 match 3
```

This starts a best of three match against one bot. [Bot settings](Bot-Settings) can override parts of your selected difficulty.

## Build from source

Install JDK 21 and set `JAVA_HOME` to its installation folder. Run the included Gradle wrapper from the repository root. A separate Gradle installation is not required.

Windows PowerShell:

```powershell
.\gradlew.bat build
```

Linux or macOS:

```sh
./gradlew build
```

The build runs the tests and writes the plugin JAR to `build/libs/`.
