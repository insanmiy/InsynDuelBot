# Wiki documentation

The pages in `wiki/` are the source for the published [InsynDuelBot GitHub wiki](https://github.com/insanmiy/InsynDuelBot/wiki). GitHub stores wiki pages in a separate Git repository.


## Read locally

For build, test, and live server verification instructions, see [development checks](Development-Checks.md).

* [Home](wiki/Home.md)
* [Getting started](wiki/Getting-Started.md)
* [Arenas](wiki/Arenas.md)
* [Practice modes](wiki/Practice-Modes.md)
* [Commands](wiki/Commands.md)
* [Bot settings](wiki/Bot-Settings.md)
* [Bot presets](wiki/Bot-Presets.md)
* [Kits](wiki/Kits.md)
* [Configuration](wiki/Configuration.md)
* [Permissions](wiki/Permissions.md)
* [Troubleshooting](wiki/Troubleshooting.md)

Links between wiki pages use GitHub wiki page names, without `.md`. Use this index when browsing the source files locally.

## Publish

For browser edits, pulling updates, and publishing individual pages, follow [updating the wiki](../UPDATING-THE-WIKI.md).

First, open the repository's wiki on GitHub and save an initial home page. The wiki repository cannot be cloned until that first page exists.

From the project root, clone the wiki into a separate folder:

```powershell
git clone https://github.com/insanmiy/InsynDuelBot.wiki.git ..\InsynDuelBot-wiki
```

Copy these prepared pages into the clone, commit them, and push:

```powershell
Copy-Item -Path .\docs\wiki\*.md -Destination ..\InsynDuelBot-wiki\
git -C ..\InsynDuelBot-wiki add -- '*.md'
git -C ..\InsynDuelBot-wiki commit -m "Add Plugin Documentation"
git -C ..\InsynDuelBot-wiki push
```

For later updates, pull the wiki repository before copying changes. Review existing wiki edits before replacing pages. Push to its default branch to publish.

`_Sidebar.md` and `_Footer.md` provide the wiki navigation. Keep their links updated when adding or renaming pages.
