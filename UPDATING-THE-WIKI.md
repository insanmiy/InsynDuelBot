# Updating The Wiki

The [Live GitHub Wiki](https://github.com/insanmiy/InsynDuelBot/wiki) And This Project Are Separate Git Repositories.

* Browser Edits Update The Live Wiki Immediately.
* Files In `docs/wiki` Are The Project's Local Documentation Copy.
* Changes Do Not Sync Automatically In Either Direction.

The PowerShell Commands Below Assume You Are In The Plugin Project's Root Folder.

## Edit Through GitHub

1. Open The [Wiki](https://github.com/insanmiy/InsynDuelBot/wiki).
2. Open A Page And Click Edit.
3. Make Your Changes And Click Save Page.
4. Follow The Pull Instructions Below To Update Your Local Copy.

## Clone The Wiki Once

Install Git If Needed. Clone The Wiki Into A Folder Beside The Plugin Project:

```powershell
git clone https://github.com/insanmiy/InsynDuelBot.wiki.git ..\InsynDuelBot-wiki
```

Skip This Step If That Folder Already Contains Your Wiki Clone. Git May Ask You To Sign In When You Push Changes.

## Pull Browser Changes

Check The Wiki Clone For Uncommitted Work Before Pulling:

```powershell
git -C ..\InsynDuelBot-wiki status --short
git -C ..\InsynDuelBot-wiki pull --ff-only
```

If The Status Shows Changes, Save Or Commit That Work Before Pulling. If The Pull Fails, Resolve The Reported Problem Before Continuing.

Next, Bring The Updated Pages Into `docs/wiki`. Before Copying, Check Whether Your Local Pages Have Unpublished Changes. Compare And Merge Those Changes First So They Are Preserved.

For Example, Compare The Getting Started Page:

```powershell
git diff --no-index -- .\docs\wiki\Getting-Started.md ..\InsynDuelBot-wiki\Getting-Started.md
```

This Comparison Returns Exit Code 1 When The Files Differ. That Is Expected.

Once You Have Checked The Differences, Copy That Page From The Wiki Clone:

```powershell
Copy-Item -LiteralPath ..\InsynDuelBot-wiki\Getting-Started.md -Destination .\docs\wiki\Getting-Started.md
```

Repeat For Other Changed Pages. Add New Pages To The Local Folder Too. If A Page Was Deleted Or Renamed In The Wiki, Review And Apply That Change Locally; Copying Files Does Not Remove Old Pages.

## Publish A Local Edit

Start By Pulling The Latest Wiki Changes And Updating Your Local Copy As Described Above. Then Edit A Page In `docs/wiki`.

For Example, After Editing `Getting-Started.md`:

```powershell
Copy-Item -LiteralPath .\docs\wiki\Getting-Started.md -Destination ..\InsynDuelBot-wiki\Getting-Started.md
git -C ..\InsynDuelBot-wiki diff -- Getting-Started.md
git -C ..\InsynDuelBot-wiki add -- Getting-Started.md
git -C ..\InsynDuelBot-wiki commit -m "Update Getting Started"
git -C ..\InsynDuelBot-wiki push
```

Review The Diff Before Committing. Open The Live Page After Pushing To Check Its Formatting And Links.

If The Push Is Rejected Because Someone Edited The Wiki Meanwhile, Keep Your Commit And Merge Their Changes:

```powershell
git -C ..\InsynDuelBot-wiki pull --no-rebase
```

Resolve Any Conflicts In The Wiki Clone, Commit The Resolution If Required, And Push Again. Copy The Final Merged Pages Back Into `docs/wiki`. Do Not Force Push To Replace Someone Else's Changes.

## Add A New Page

1. Create A Markdown File In `docs/wiki`, Such As `New-Guide.md`.
2. Add A Link To It In `_Sidebar.md` And Any Relevant Pages.
3. Copy The New Page And Updated Navigation Files Into The Wiki Clone.
4. Review, Add, Commit, And Push Those Files As Above.
5. Add The Page To The Local Index In `docs/README.md`.

Inside Wiki Pages, Link To `[New Guide](New-Guide)` Without The `.md` Extension. In The Local Index, Use `[New Guide](wiki/New-Guide.md)`.

`_Sidebar.md` Controls The Wiki Sidebar. `_Footer.md` Controls Its Footer.

## Save The Project Copy

Publishing The Wiki Does Not Commit The Plugin Project's Documentation Files. Review And Commit The Relevant Files In This Repository Separately:

```powershell
git status --short
git add -- docs/wiki/Getting-Started.md
git commit -m "Sync Getting Started From Wiki"
```

Use The Paths You Actually Changed. New Pages Also Need To Be Added To Git. The Repository's `.gitignore` Allows Markdown Files Under `docs/`.

Documentation Edits Do Not Require Rebuilding The Plugin.
