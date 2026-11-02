# Arenas

Arenas Define Where Players And Bots Spawn. Arena Commands Require `practice.arena`, Which Operators Receive Through `practice.admin`.

## Create An Arena

Stand At Each Spawn Before Running Its Command:

```text
/practice arena setplayer courtyard
/practice arena setbot courtyard
/practice arena setradius 32 courtyard
/practice arena list
```

Leaving Out The Name Uses The `default` Arena.

Choose Solid Ground With Two Clear Blocks Of Headroom. Spawns Must Be In The Same World, At Least Four Blocks Apart, And Close Enough For The Arena Radius.

## Manage Arenas

| Command | Action |
| --- | --- |
| `/practice arena list` | Show Readiness And Availability |
| `/practice arena info [name]` | Show Spawns And Radius |
| `/practice arena tp [name]` | Teleport To The Player Spawn |
| `/practice arena setradius <radius> [name]` | Change The Arena Radius |
| `/practice arena delete <name>` | Remove An Arena's Setup |

Matches Are Assigned An Available Arena Automatically. Occupied Arenas And Arenas That Overlap An Active Match Cannot Be Assigned To Another Match. Leave Space Between Arenas.

Stop Matches Before Changing Their Arena Setup. Occupied Arenas Cannot Be Deleted.

If No Arena Is Available, Run `/practice arena list` And Follow The Reported Setup Error. See [Troubleshooting](Troubleshooting).
