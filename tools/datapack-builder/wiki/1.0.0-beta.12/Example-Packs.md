# Example Packs

The repo already includes a full starter datapack you can copy from:

```text
example-packs/dialogue-folder-template/
```

This is the best source of real, current beta.12 examples.

## What Is In The Template

| Area | Example path |
| --- | --- |
| Dialogue option | `example-packs/dialogue-folder-template/data/villagerretaliation/dialogue/en_us/example_template/options/00_greeting.json` |
| Dialogue line | `example-packs/dialogue-folder-template/data/villagerretaliation/dialogue/en_us/example_template/lines/00_greeting.json` |
| Keyed message | `example-packs/dialogue-folder-template/data/villagerretaliation/dialogue/en_us/example_template/messages/00_example.json` |
| Forced dialogue | `example-packs/dialogue-folder-template/data/villagerretaliation/forced_dialogue/example_template/00_container_theft.json` |
| Notification | `example-packs/dialogue-folder-template/data/villagerretaliation/notifications/en_us/example_template/00_ambient.json` |
| Gifts | `example-packs/dialogue-folder-template/data/villagerretaliation/gifts/example_template/00_gifts.json` |
| Pacification | `example-packs/dialogue-folder-template/data/villagerretaliation/pacification/example_template/00_payments.json` |
| Profession loot | `example-packs/dialogue-folder-template/data/villagerretaliation/profession_loot/example_template/00_loot.json` |
| Villager names | `example-packs/dialogue-folder-template/data/villagerretaliation/villager_names/example_template_names.json` |

## Smallest Copyable Pack

If you want the lightest possible starting point, copy only:

```text
pack.mcmeta
data/
  my_pack/
    dialogue/en_us/my_pack/options/00_rumor.json
    dialogue/en_us/my_pack/lines/00_rumor.json
```

Example option:

```json
{
  "id": "my_pack.option.ask_rumor",
  "label": "Ask For A Rumor",
  "request": "story"
}
```

Example line:

```json
{
  "id": "my_pack.line.rumor",
  "request": "story",
  "option": "my_pack.option.ask_rumor",
  "text": "Roads carry stories faster than traders do."
}
```

## When To Copy The Full Template

Copy the whole `dialogue-folder-template` when you want:

- one file per dialogue request
- a translator-friendly folder layout
- examples for beta.12 conditions and filters
- a reference pack that covers almost every authoring surface

## Minimal `pack.mcmeta`

```json
{
  "pack": {
    "pack_format": 48,
    "description": "Villager Retaliation example pack"
  }
}
```

Add your own `villagerretaliation.pack_version` marker only if your workflow already expects it. The builder will add it automatically on export for supported versions.
