# Development checks

Run the full build with Java 21:

```powershell
.\gradlew.bat build
```

The build runs the core tests and checks that the release JAR does not contain Minecraft, CraftBukkit, or Mojang server classes. A source check keeps server imports out of the core module.

## Live compatibility checks

Run the same checks used by CI against one Minecraft version:

```powershell
.\gradlew.bat runServer '-PminecraftVersion=1.21.11' '-PcompatibilityTest=true'
```

On Linux or macOS, use `./gradlew` instead of `.\gradlew.bat`.

The task uses a separate server folder at `run/compatibility/<version>/`, binds to loopback, and creates a test arena. It spawns real NMS players and runs the practice commands without requiring a connected Minecraft client.

The checks assert:

* Bot registration and direct session lookup.
* Editable menu titles and labels after message reload, and arena storage keys remaining stable when spawn labels change.
* Bot movement and successful melee damage.
* Direct pursuit with no Speed effect, Speed I, and Speed II, including the matching sprint jump behavior.
* Pause and resume behavior.
* Golden apple consumption and absorption.
* Bot reset and removal.
* Inventory, health, and game mode restoration after stopping a match.
* Cleanup and recovery after disconnecting.
* Cleanup and recovery when disabling the plugin during a match.

The server stops after the checks finish. Gradle requires a `PASS` result and rejects plugin errors, packet encoding errors, and common binary compatibility errors in the server log. The task has a five minute timeout. Results are written to `compatibility-result.txt`; server logs are in `logs/latest.log` inside the test server folder.

CI runs these checks across all 13 published Paper releases from 1.20.5 through 1.21.11 and uploads the result and server log for each version. [Paper's release index](https://fill.papermc.io/v3/projects/paper) does not include a 1.21.2 build, so that version is excluded. These checks cover common gameplay and lifecycle paths. They do not replace client testing of rendering, skins, menus, or every utility item.

## Saves and recovery

Routine player settings, presets, and statistics are serialized on the main thread and queued as immutable strings. One background writer combines pending changes per file and writes them in order about once per second. Failed writes remain queued for retry, and shutdown flushes pending changes.

A sudden process crash can lose routine changes that have not yet been written. Recovery captures and block journals use their existing synchronous save paths and do not share the background queue.

Unreadable recovery journals stay in place. Recovery validates the destination and decodes inventory items before changing player state. Invalid entries, missing worlds, dead players, and failed restores leave recovery pending. Repair the reported journal or load the missing world before retrying. Keep a copy of the original journal when repairing it.

Core tests cover corrupt journals, unavailable recovery destinations, dead players, invalid inventory data, failed journal writes, unloaded block worlds, save retry ordering, and independent cleanup steps.
