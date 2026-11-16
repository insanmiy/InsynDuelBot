# Kits

Kits define the equipment and items used in a match. Run `/practice kits` to see what is available on your server.

The shipped catalog includes `diamond-smp`, `netherite-smp`, `sword`, `axe`, `pot`, and `uhc`.

## Use the editor

Stop your session and open `/practice kit editor`. Create or edit a custom kit, then click save to keep your changes. Closing a draft discards changes that have not been saved.

## Kit commands

| Command | Action |
| --- | --- |
| `/practice kit info <name>` | Show a kit summary |
| `/practice kit create <name> [template]` | Open a new draft, optionally based on another kit |
| `/practice kit edit <name>` | Edit a saved custom kit |
| `/practice kit import <name>` | Save your inventory as a custom kit and open its editor |
| `/practice kit save <name>` | Save your current inventory directly as a custom kit |
| `/practice kit copy <source> <new-name>` | Open a new draft based on another kit |
| `/practice kit rename <old-name> <new-name>` | Rename a custom kit |
| `/practice kit delete <name>` | Open the deletion confirmation |

Names use 1 to 32 letters, numbers, underscores, or hyphens. Use a new name when copying a built in kit.

```text
/practice kit create my_sword sword
```

Edit the draft and click save. It then becomes available in practice setup.

## Ownership and storage

Players normally edit their own custom kits. Administrators with `practice.kits.bypass` can modify kits owned by other players.

Built in kit definitions are in `kits.yml`. Custom kits are stored in `customkits.yml`. Items must be allowed by the plugin and supported by your server version.

See [permissions](Permissions) for individual kit permissions.
