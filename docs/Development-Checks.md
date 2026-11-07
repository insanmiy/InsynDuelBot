# Development Checks

Run The Full Build With Java 21:

```powershell
.\gradlew.bat build
```

The Build Runs The Core Tests And Checks That The Release JAR Does Not Contain Minecraft, CraftBukkit, Or Mojang Server Classes. A Source Check Keeps Server Imports Out Of The Core Module.

## Live Compatibility Checks

Run The Same Checks Used By CI Against One Minecraft Version:

```powershell
.\gradlew.bat runServer '-PminecraftVersion=1.21.11' '-PcompatibilityTest=true'
```

On Linux Or macOS, Use `./gradlew` Instead Of `.\gradlew.bat`.

The Task Uses A Separate Server Folder At `run/compatibility/<version>/`, Binds To Loopback, And Creates A Test Arena. It Spawns Real NMS Players And Runs The Practice Commands Without Requiring A Connected Minecraft Client.

The Checks Assert:

* Bot Registration And Direct Session Lookup.
* Bot Movement And Successful Melee Damage.
* Pause And Resume Behavior.
* Golden Apple Consumption And Absorption.
* Bot Reset And Removal.
* Inventory, Health, And Game Mode Restoration After Stopping A Match.
* Cleanup And Recovery After Disconnecting.
* Cleanup And Recovery When Disabling The Plugin During A Match.

The Server Stops After The Checks Finish. Gradle Requires A `PASS` Result And Rejects Plugin Errors, Packet Encoding Errors, And Common Binary Compatibility Errors In The Server Log. The Task Has A Five Minute Timeout. Results Are Written To `compatibility-result.txt`; Server Logs Are In `logs/latest.log` Inside The Test Server Folder.

CI Runs These Checks Across All 13 Published Paper Releases From 1.20.5 Through 1.21.11 And Uploads The Result And Server Log For Each Version. [Paper?s Release Index](https://fill.papermc.io/v3/projects/paper) Does Not Include A 1.21.2 Build, So That Version Is Excluded. These Checks Cover Common Gameplay And Lifecycle Paths. They Do Not Replace Client Testing Of Rendering, Skins, Menus, Or Every Utility Item.

## Saves And Recovery

Routine Player Settings, Presets, And Statistics Are Serialized On The Main Thread And Queued As Immutable Strings. One Background Writer Combines Pending Changes Per File And Writes Them In Order About Once Per Second. Failed Writes Remain Queued For Retry, And Shutdown Flushes Pending Changes.

A Sudden Process Crash Can Lose Routine Changes That Have Not Yet Been Written. Recovery Captures And Block Journals Use Their Existing Synchronous Save Paths And Do Not Share The Background Queue.

Unreadable Recovery Journals Stay In Place. Recovery Validates The Destination And Decodes Inventory Items Before Changing Player State. Invalid Entries, Missing Worlds, Dead Players, And Failed Restores Leave Recovery Pending. Repair The Reported Journal Or Load The Missing World Before Retrying. Keep A Copy Of The Original Journal When Repairing It.

Core Tests Cover Corrupt Journals, Unavailable Recovery Destinations, Dead Players, Invalid Inventory Data, Failed Journal Writes, Unloaded Block Worlds, Save Retry Ordering, And Independent Cleanup Steps.
