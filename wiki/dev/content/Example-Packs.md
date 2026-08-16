# Example Packs

These folders are working test packs, not just JSON fragments. Copy the smallest example that covers your system, change its namespace and IDs, then run `/reload`.

## Sell Prices

`example-packs/sell-prices-example/` keeps a legacy beta.13 price beside explicit rates, tag pricing, discrete prices, component matching, durability, a built-in override, and a disabled definition. Its README calls out the fictitious mod and tag IDs used for illustration.

## Custom Duel Kits

`example-packs/custom-duel-kits/` adds an enchanted `duel_examples:champion` kit with temporary equipment for the player and villager. See [Duel Kits](Duel-Kits.md) for every field.

## Skill Trades And Special Orders

`example-packs/skill-trades-special-orders/` adds profession-specialty and farming-order pools that demonstrate skill gates, reputation, trade-level ranges, persistent trade stock, and targetable Special Orders. See [Skill Trades](Skill-Trades.md) for the schema and refresh behavior.

## Persistent Cinematic Gate Ambush

`example-packs/cinematic-gate-ambush/` is the complete beta.13 scene-orchestration example: two named villagers, player/party ownership, a recorded choice branch, movement, dialogue, a persisted wait, controlled encounter scaling and cleanup, quest completion/failure, and provider-unload recovery.

## Repeatable scene run identity

`example-packs/repeatable-scene-run-id/` is a deliberately small repeatable quest. It launches the same scene operation twice in one run (one instance), then demonstrates that a later legitimate run and an unrelated player's run receive different `QUEST_INSTANCE` owners.

The repo already includes a full starter datapack you can copy from:

```text
example-packs/dialogue-folder-template/
```

This is the best source of the current folderized dialogue format introduced in beta.12. Its quest is a beta.13 owner bundle, and the scene examples above cover the persistent quest runtime surface.

## What Is In The Template

| Area | Example path |
| --- | --- |
| Dialogue option | `example-packs/dialogue-folder-template/data/example_template/dialogue/en_us/example_template/options/00_greeting.json` |
| Dialogue line | `example-packs/dialogue-folder-template/data/example_template/dialogue/en_us/example_template/lines/00_greeting.json` |
| Keyed message | `example-packs/dialogue-folder-template/data/example_template/dialogue/en_us/example_template/messages/00_example.json` |
| Forced dialogue | `example-packs/dialogue-folder-template/data/example_template/forced_dialogue/example_template/00_container_theft.json` |
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
- current examples for conditions, filters, message keys, and a beta.13 quest bundle
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
