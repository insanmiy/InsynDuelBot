# Updating the wiki

The [live GitHub wiki](https://github.com/insanmiy/InsynDuelBot/wiki) and this project are separate Git repositories.

* Browser edits update the live wiki immediately.
* Files in `docs/wiki` are the project's local documentation copy.
* Changes do not sync automatically in either direction.

The PowerShell commands below assume you are in the plugin project's root folder.

## Edit through GitHub

1. Open the [wiki](https://github.com/insanmiy/InsynDuelBot/wiki).
2. Open a page and click edit.
3. Make your changes and click save page.
4. Follow the pull instructions below to update your local copy.

## Clone the wiki once

Install Git if needed. Clone the wiki into a folder beside the plugin project:

```powershell
git clone https://github.com/insanmiy/InsynDuelBot.wiki.git ..\InsynDuelBot-wiki
```

Skip this step if that folder already contains your wiki clone. Git may ask you to sign in when you push changes.

## Pull browser changes

Check the wiki clone for uncommitted work before pulling:

```powershell
git -C ..\InsynDuelBot-wiki status --short
git -C ..\InsynDuelBot-wiki pull --ff-only
```

If the status shows changes, save or commit that work before pulling. If the pull fails, resolve the reported problem before continuing.

Next, bring the updated pages into `docs/wiki`. Before copying, check whether your local pages have unpublished changes. Compare and merge those changes first so they are preserved.

For example, compare the getting started page:

```powershell
git diff --no-index -- .\docs\wiki\Getting-Started.md ..\InsynDuelBot-wiki\Getting-Started.md
```

This comparison returns exit code 1 when the files differ. That is expected.

Once you have checked the differences, copy that page from the wiki clone:

```powershell
Copy-Item -LiteralPath ..\InsynDuelBot-wiki\Getting-Started.md -Destination .\docs\wiki\Getting-Started.md
```

Repeat for other changed pages. Add new pages to the local folder too. If a page was deleted or renamed in the wiki, review and apply that change locally; copying files does not remove old pages.

## Publish a local edit

Start by pulling the latest wiki changes and updating your local copy as described above. Then edit a page in `docs/wiki`.

For example, after editing `Getting-Started.md`:

```powershell
Copy-Item -LiteralPath .\docs\wiki\Getting-Started.md -Destination ..\InsynDuelBot-wiki\Getting-Started.md
git -C ..\InsynDuelBot-wiki diff -- Getting-Started.md
git -C ..\InsynDuelBot-wiki add -- Getting-Started.md
git -C ..\InsynDuelBot-wiki commit -m "Update Getting Started"
git -C ..\InsynDuelBot-wiki push
```

Review the diff before committing. Open the live page after pushing to check its formatting and links.

If the push is rejected because someone edited the wiki meanwhile, keep your commit and merge their changes:

```powershell
git -C ..\InsynDuelBot-wiki pull --no-rebase
```

Resolve any conflicts in the wiki clone, commit the resolution if required, and push again. Copy the final merged pages back into `docs/wiki`. Do not force push to replace someone else's changes.

## Add a new page

1. Create a markdown file in `docs/wiki`, such as `New-Guide.md`.
2. Add a link to it in `_Sidebar.md` and any relevant pages.
3. Copy the new page and updated navigation files into the wiki clone.
4. Review, add, commit, and push those files as above.
5. Add the page to the local index in `docs/README.md`.

Inside wiki pages, link to `[New Guide](New-Guide)` without the `.md` extension. In the local index, use `[New Guide](wiki/New-Guide.md)`.

`_Sidebar.md` controls the wiki sidebar. `_Footer.md` controls its footer.

## Save the project copy

Publishing the wiki does not commit the plugin project's documentation files. Review and commit the relevant files in this repository separately:

```powershell
git status --short
git add -- docs/wiki/Getting-Started.md
git commit -m "Sync Getting Started From Wiki"
```

Use the paths you actually changed. New pages also need to be added to Git. The repository's `.gitignore` allows markdown files under `docs/`.

Documentation edits do not require rebuilding the plugin.
