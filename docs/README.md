# Wiki Documentation

The Pages In `wiki/` Are The Source For The Published [InsynDuelBot GitHub Wiki](https://github.com/insanmiy/InsynDuelBot/wiki). GitHub Stores Wiki Pages In A Separate Git Repository.


## Read Locally

For Build, Test, And Live Server Verification Instructions, See [Development Checks](Development-Checks.md).

* [Home](wiki/Home.md)
* [Getting Started](wiki/Getting-Started.md)
* [Arenas](wiki/Arenas.md)
* [Practice Modes](wiki/Practice-Modes.md)
* [Commands](wiki/Commands.md)
* [Bot Settings](wiki/Bot-Settings.md)
* [Bot Presets](wiki/Bot-Presets.md)
* [Kits](wiki/Kits.md)
* [Configuration](wiki/Configuration.md)
* [Permissions](wiki/Permissions.md)
* [Troubleshooting](wiki/Troubleshooting.md)

Links Between Wiki Pages Use GitHub Wiki Page Names, Without `.md`. Use This Index When Browsing The Source Files Locally.

## Publish

For Browser Edits, Pulling Updates, And Publishing Individual Pages, Follow [Updating The Wiki](../UPDATING-THE-WIKI.md).

First, Open The Repository's Wiki On GitHub And Save An Initial Home Page. The Wiki Repository Cannot Be Cloned Until That First Page Exists.

From The Project Root, Clone The Wiki Into A Separate Folder:

```powershell
git clone https://github.com/insanmiy/InsynDuelBot.wiki.git ..\InsynDuelBot-wiki
```

Copy These Prepared Pages Into The Clone, Commit Them, And Push:

```powershell
Copy-Item -Path .\docs\wiki\*.md -Destination ..\InsynDuelBot-wiki\
git -C ..\InsynDuelBot-wiki add -- '*.md'
git -C ..\InsynDuelBot-wiki commit -m "Add Plugin Documentation"
git -C ..\InsynDuelBot-wiki push
```

For Later Updates, Pull The Wiki Repository Before Copying Changes. Review Existing Wiki Edits Before Replacing Pages. Push To Its Default Branch To Publish.

`_Sidebar.md` And `_Footer.md` Provide The Wiki Navigation. Keep Their Links Updated When Adding Or Renaming Pages.
