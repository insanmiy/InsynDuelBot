# Troubleshooting

## The plugin does not start

Check the server log for the first InsynDuelBot startup error. Confirm that the server uses Java 21 and a targeted Paper version. The plugin targets 1.20.5 through 1.21.11, but every version has not been fully verified through gameplay.

An NMS adapter error usually needs the exact Paper version and full error trace to diagnose.

## No free arena

Run `/practice arena list`. Check that both spawns are set, in the same loaded world, on solid ground, and at least four blocks apart.

An arena may also be occupied or too close to an active arena. See [arenas](Arenas).

## The bot does not attack

Check your selected difficulty. `dummy` intentionally disables attacks. Confirm the match has finished its countdown and is not paused.

## The bot ignores my difficulty settings

Your personal advanced tuning can override the selected difficulty. Open `/practice settings` and reset advanced tuning, or shift click an individual setting to restore its difficulty default. Ability toggles are kept when advanced tuning is reset.

Also check whether you loaded a [bot preset](Bot-Presets) with custom overrides.

## The bot gets stuck

Recovery can try a detour, but some locations are unreachable. Test on open ground, check spawn headroom, and record the terrain or blocks involved if the problem repeats.

## Presets or kits cannot be changed

Stop your active session first. Check the reported permission, kit ownership, and name format. Preset names and kit names accept letters, numbers, underscores, and hyphens, up to 32 characters.

## Reload is refused

All matches must end before `/practice reload` can run. Administrators can use `/practice stop <player>` to end another player's session.

## The build reports an invalid JAVA_HOME

Set `JAVA_HOME` to your JDK 21 installation folder, then run the Gradle wrapper again. See [getting started](Getting-Started).

## Report a bug

[Open an issue](https://github.com/insanmiy/InsynDuelBot/issues) with:

* Your plugin version and exact Paper version.
* The kit, difficulty, mode, and any custom bot settings.
* The steps needed to reproduce the problem.
* Relevant server log output and screenshots or a short video when useful.
