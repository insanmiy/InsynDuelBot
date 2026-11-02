# Kits

Kits Define The Equipment And Items Used In A Match. Run `/practice kits` To See What Is Available On Your Server.

The Shipped Catalog Includes `diamond-smp`, `netherite-smp`, `sword`, `axe`, `pot`, And `uhc`.

## Use The Editor

Stop Your Session And Open `/practice kit editor`. Create Or Edit A Custom Kit, Then Click Save To Keep Your Changes. Closing A Draft Discards Changes That Have Not Been Saved.

## Kit Commands

| Command | Action |
| --- | --- |
| `/practice kit info <name>` | Show A Kit Summary |
| `/practice kit create <name> [template]` | Open A New Draft, Optionally Based On Another Kit |
| `/practice kit edit <name>` | Edit A Saved Custom Kit |
| `/practice kit import <name>` | Save Your Inventory As A Custom Kit And Open Its Editor |
| `/practice kit save <name>` | Save Your Current Inventory Directly As A Custom Kit |
| `/practice kit copy <source> <new-name>` | Open A New Draft Based On Another Kit |
| `/practice kit rename <old-name> <new-name>` | Rename A Custom Kit |
| `/practice kit delete <name>` | Open The Deletion Confirmation |

Names Use 1 To 32 Letters, Numbers, Underscores, Or Hyphens. Use A New Name When Copying A Built In Kit.

```text
/practice kit create my_sword sword
```

Edit The Draft And Click Save. It Then Becomes Available In Practice Setup.

## Ownership And Storage

Players Normally Edit Their Own Custom Kits. Administrators With `practice.kits.bypass` Can Modify Kits Owned By Other Players.

Built In Kit Definitions Are In `kits.yml`. Custom Kits Are Stored In `customkits.yml`. Items Must Be Allowed By The Plugin And Supported By Your Server Version.

See [Permissions](Permissions) For Individual Kit Permissions.
