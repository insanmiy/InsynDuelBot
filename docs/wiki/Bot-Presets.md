# Bot presets

Presets let you save a bot setup and restore it later. Each player can keep up to 21 personal presets, saved across server restarts.

## Save and load

1. Stop your current session.
2. Configure the bot in `/practice settings`.
3. Save your setup, then load it whenever you want to use it again.

```text
/practice preset save sword_practice
/practice preset load sword_practice
/practice preset list
/practice preset delete sword_practice
```

Names use 1 to 32 letters, numbers, underscores, or hyphens. Names are stored in lowercase. Saving an existing name replaces its previous settings.

## What gets saved

Presets include bot ability toggles and the seven advanced tuning settings. A setting left at its default continues to follow the difficulty selected for the match.

Presets do not choose a kit, difficulty, mode, or arena. Match timing, starting saturation, HUD, and menu animations stay separate.

Loading a preset changes your settings for your next session. You cannot manage presets during an active session.

## Presets menu

Open `/practice preset`, or select bot presets from the settings menu.

| Click | Action |
| --- | --- |
| Left click | Load the preset |
| Shift left click | Replace it with your current bot settings |
| Shift right click | Delete it |

Use `/practice preset save <name>` to create a new named preset. Preset commands and the menu require `practice.settings`.
