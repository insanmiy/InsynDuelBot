# Bot settings

When chasing on a clear route, the bot sprint jumps with no Speed effect or Speed I. With Speed II or higher, it sprints without chase jumps. Obstacle jumps and critical hit jumps keep their separate rules.

Run `/practice settings` outside an active session. Settings are saved for your future sessions.

## Match settings

Adjust the starting countdown, break between rounds, starting saturation, health display, and menu animations. Choose your match mode and round count in the practice setup menu.

## Bot abilities

Toggle healing, utilities, critical hits, shields, strafing, and shield counters.

Utilities include actions such as using webs, pearls, and bows. The selected kit must contain the required items.

## Advanced tuning

| Setting | What it changes |
| --- | --- |
| Attack timing | How much attack charge the bot waits for |
| Aim accuracy | How precisely the bot aims |
| Reaction speed | Decision frequency and perception delay |
| Sprint reset | How often the bot restarts its sprint after hits |
| Shield defense | How often the bot tries to block |
| Strafe movement | Sideways movement strength |
| Healing threshold | When the bot starts seeking healing |

Left click to advance a setting, right click to go back, or shift click to use the selected difficulty's default.

Reset advanced tuning restores these seven settings to difficulty defaults. Ability toggles stay as you set them. Custom overrides can make a difficulty behave differently from its shipped values.

## Difficulties

The default configuration includes `dummy` and tiers from `lt5` through `ht1`. Use `/practice difficulties` to list the actual choices on your server.

The `dummy` difficulty does not attack. Administrators can edit difficulty definitions in `config.yml`.

## Recovery

When navigation stalls, the bot can try a safe sideways or backward detour before calculating a fresh route. It also rechecks blocked walking routes. Recovery does not guarantee that every terrain layout is reachable.

Save useful setups as [bot presets](Bot-Presets).
