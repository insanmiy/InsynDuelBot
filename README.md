# InsynDuelBot

A Paper Minecraft plugin for 1v1 PvP practice against a custom bot.

## Features

* Custom PvP bot with combat movement, strafing, and sprint resetting
* Built in kits, custom kit editor, and customizable difficulties
* Multi-version support for Paper 1.20.6 through 1.21.11 (Probably broken)

## Commands

* `/practice menu` — Open the practice menu
* `/practice start` — Start a match against the bot
* `/practice stop` — End the current match
* `/practice settings` — Open bot and match settings

Aliases: `/p`, `/prac`

## Building

Requires Java 21.

```bash
./gradlew build
```

The compiled JAR will be in `build/libs/InsynDuelBot.jar`.

## License

Licensed under the [MIT License](LICENSE).
