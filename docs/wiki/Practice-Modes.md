# Practice modes

Choose your mode in `/practice`, or supply it to `/practice start`.

| Mode | How it works |
| --- | --- |
| `duel` | One round against one bot |
| `match` | A best of series against one bot |
| `endless` | Repeated rounds until you stop |
| `2v1` | A best of series against two bots |
| `3v1` | A best of series against three bots |

For a best of series, choose an odd round count such as 3, 5, or 7. A best of three ends when one side wins two rounds. In an outnumbered round, defeat all opposing bots to win.

```text
/practice start diamond-smp lt4 duel
/practice start diamond-smp lt4 match 5
/practice start diamond-smp lt4 endless
/practice start diamond-smp lt4 2v1 3
```

Use `/practice pause` and `/practice resume` to take a break. Use `/practice rematch` after a session to reuse your previous match selection, or `/practice stop` to leave.

To practice against a bot that does not attack, choose the `dummy` difficulty. It is a difficulty setting, not a separate mode.
