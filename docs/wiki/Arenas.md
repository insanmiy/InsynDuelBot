# Arenas

Water placed during practice can wash away blocks and waterlog supported blocks. The block journal records original block data for cleanup. Ownership checks, flow distance limits, and the per-player block limit still apply. Lava placement keeps its existing restrictions.

Arenas define where players and bots spawn. Arena commands require `practice.arena`, which operators receive through `practice.admin`.

## Create an arena

Stand at each spawn before running its command:

```text
/practice arena setplayer courtyard
/practice arena setbot courtyard
/practice arena setradius 32 courtyard
/practice arena list
```

Leaving out the name uses the `default` arena.

Choose solid ground with two clear blocks of headroom. Spawns must be in the same world, at least four blocks apart, and close enough for the arena radius.

## Manage arenas

| Command | Action |
| --- | --- |
| `/practice arena list` | Show readiness and availability |
| `/practice arena info [name]` | Show spawns and radius |
| `/practice arena tp [name]` | Teleport to the player spawn |
| `/practice arena setradius <radius> [name]` | Change the arena radius |
| `/practice arena delete <name>` | Remove an arena's setup |

Matches are assigned an available arena automatically. Occupied arenas and arenas that overlap an active match cannot be assigned to another match. Leave space between arenas.

Stop matches before changing their arena setup. Occupied arenas cannot be deleted.

If no arena is available, run `/practice arena list` and follow the reported setup error. See [troubleshooting](Troubleshooting).
