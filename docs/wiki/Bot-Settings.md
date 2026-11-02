# Bot Settings

Run `/practice settings` Outside An Active Session. Settings Are Saved For Your Future Sessions.

## Match Settings

Adjust The Starting Countdown, Break Between Rounds, Starting Saturation, Health Display, And Menu Animations. Choose Your Match Mode And Round Count In The Practice Setup Menu.

## Bot Abilities

Toggle Healing, Utilities, Critical Hits, Shields, Strafing, And Shield Counters.

Utilities Include Actions Such As Using Webs, Pearls, And Bows. The Selected Kit Must Contain The Required Items.

## Advanced Tuning

| Setting | What It Changes |
| --- | --- |
| Attack Timing | How Much Attack Charge The Bot Waits For |
| Aim Accuracy | How Precisely The Bot Aims |
| Reaction Speed | Decision Frequency And Perception Delay |
| Sprint Reset | How Often The Bot Restarts Its Sprint After Hits |
| Shield Defense | How Often The Bot Tries To Block |
| Strafe Movement | Sideways Movement Strength |
| Healing Threshold | When The Bot Starts Seeking Healing |

Left Click To Advance A Setting, Right Click To Go Back, Or Shift Click To Use The Selected Difficulty's Default.

Reset Advanced Tuning Restores These Seven Settings To Difficulty Defaults. Ability Toggles Stay As You Set Them. Custom Overrides Can Make A Difficulty Behave Differently From Its Shipped Values.

## Difficulties

The Default Configuration Includes `dummy` And Tiers From `lt5` Through `ht1`. Use `/practice difficulties` To List The Actual Choices On Your Server.

The `dummy` Difficulty Does Not Attack. Administrators Can Edit Difficulty Definitions In `config.yml`.

## Recovery

When Navigation Stalls, The Bot Can Try A Safe Sideways Or Backward Detour Before Calculating A Fresh Route. It Also Rechecks Blocked Walking Routes. Recovery Does Not Guarantee That Every Terrain Layout Is Reachable.

Save Useful Setups As [Bot Presets](Bot-Presets).
