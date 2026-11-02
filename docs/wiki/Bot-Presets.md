# Bot Presets

Presets Let You Save A Bot Setup And Restore It Later. Each Player Can Keep Up To 21 Personal Presets, Saved Across Server Restarts.

## Save And Load

1. Stop Your Current Session.
2. Configure The Bot In `/practice settings`.
3. Save Your Setup, Then Load It Whenever You Want To Use It Again.

```text
/practice preset save sword_practice
/practice preset load sword_practice
/practice preset list
/practice preset delete sword_practice
```

Names Use 1 To 32 Letters, Numbers, Underscores, Or Hyphens. Names Are Stored In Lowercase. Saving An Existing Name Replaces Its Previous Settings.

## What Gets Saved

Presets Include Bot Ability Toggles And The Seven Advanced Tuning Settings. A Setting Left At Its Default Continues To Follow The Difficulty Selected For The Match.

Presets Do Not Choose A Kit, Difficulty, Mode, Or Arena. Match Timing, Starting Saturation, HUD, And Menu Animations Stay Separate.

Loading A Preset Changes Your Settings For Your Next Session. You Cannot Manage Presets During An Active Session.

## Presets Menu

Open `/practice preset`, Or Select Bot Presets From The Settings Menu.

| Click | Action |
| --- | --- |
| Left Click | Load The Preset |
| Shift Left Click | Replace It With Your Current Bot Settings |
| Shift Right Click | Delete It |

Use `/practice preset save <name>` To Create A New Named Preset. Preset Commands And The Menu Require `practice.settings`.
