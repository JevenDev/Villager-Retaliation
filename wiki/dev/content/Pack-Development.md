# Pack Development

This page is the setup guide for writing Villager Retaliation addons. Use it before touching any system-specific JSON.

## Pack Types

Use a datapack for behavior and authored text:

```text
<datapack root>/
  pack.mcmeta
  data/
    villagerretaliation/
      gifts/
      currency/
      item_text/en_us/
      notifications/
      natural_job_armor/
      pacification/
      profession_loot/
      villager_names/
      village_names/
    my_pack/
      builder_structures/
      dialogue/en_us/
      dialogue_tuning/
      generated_containers/
      duel_kits/
      dialogue_trees/en_us/
      forced_dialogue/
      quests/
        _shared/
          locales/
          pools/
          scenes/
          encounters/
          rewards/
        <questline>/<quest-slug>/
          quest.json
          locales/
      sell_prices/
      skill_trades/
      story_structures/
      story_biomes/
      villager_events/
      loot_table/
```

The global `dialogue_trees/` and `forced_dialogue/` systems are separate from quest bundles. Beta.13 structural quest dialogue stays in each bundle's `quest.json`; persistent scene companions belong under that bundle's `scenes/` directory.

Every datapack also needs a `pack.mcmeta` at its root. For Minecraft 1.21.1 datapacks:

```json
{
  "pack": {
    "pack_format": 48,
    "description": "My Villager Retaliation addon"
  }
}
```

Use a resource pack for GUI text, textures, and model JSON:

```text
<resource-pack root>/
  pack.mcmeta
  assets/
    villagerretaliation/
      lang/en_us.json
      models/entity/villager/combat_villager.json
      textures/entity/villager/villager.png
```

## Namespace Rules

These systems are intentionally fixed to the mod namespace:

| System | Namespace |
| --- | --- |
| Notifications | `villagerretaliation` |
| Gifts | `villagerretaliation` |
| Currency | `villagerretaliation` |
| Natural job armor | `villagerretaliation` |
| Pacification | `villagerretaliation` |
| Profession loot rules | `villagerretaliation` |
| Villager names | `villagerretaliation` |
| Village names | `villagerretaliation` |

These systems can live in your own namespace:

- Dialogue
- Dialogue tuning
- Dialogue trees
- Quests
- Quest pools
- Forced dialogue
- Skill trades
- Builder structures
- Story structures
- Story biomes
- Villager event triggers
- Duel kits
- Sell prices
- Persistent quest scenes and encounter templates
- Generated-container lists
- Referenced loot tables

Example:

```text
data/my_pack/dialogue/en_us/global/lines/rumors.json
data/my_pack/dialogue_tuning/conversation_chances.json
data/my_pack/quests/lost_civilization/echo_shard/quest.json
data/my_pack/quests/_shared/pools/daily_commissions.json
data/my_pack/skill_trades/cartographer.json
data/my_pack/builder_structures/custom_houses.json
data/my_pack/loot_table/villager/profession/alchemist/common.json
```

## Override Rules

Minecraft resolves exact resource paths first. Villager Retaliation then merges the files it finds for that loader.

- A file at the same resource path as a built-in file replaces that built-in file before VR reads it.
- Inside many systems, a later entry with the same `id` replaces an earlier entry without replacing the whole file.
- Quest bundle structural files are whole-definition replacements by stable ID. Companions, rewards, and English apply transactionally with their canonical owner.
- For normal dialogue, top-level `replace: true` clears the current dialogue pool, and `replace_sections` can clear only selected sections.
- Top-level `remove: true` removes one quest, dialogue tree, or forced-dialogue definition by `id`.

| System | Additive by default | Clear everything | Remove one entry |
| --- | --- | --- | --- |
| Dialogue | Yes | `replace: true` or `replace_sections` | Replace by same entry `id` |
| Dialogue trees | Yes | `replace: true` | `remove: true` with `id` |
| Quest bundles | Yes | Replace the same stable ID in a higher layer | Remove the owning bundle from that layer |
| Quest pools | Yes | No global clear flag | `remove: true` with `id` |
| Dialogue tuning | Yes; later values replace the same key | No global clear flag | Redefine the numeric key |
| Forced dialogue | Yes | `replace: true` | `remove: true` with `id` |
| Notifications and gifts | Yes | `replace: true` | Replace or remove by entry `id` |
| Pacification | Yes | Same-path file replacement only | No entry removal |
| Profession loot | Yes | `replace: true` | `remove: true` with rule `id` |
| Villager and village names | Yes | `replace: true` | No entry removal |
| Duel kits and sell prices | One definition per resource path | Replace the same resource path | Disable sell prices with `enabled: false` |
| Story discovery and generated containers | Yes | Same-path file replacement only | Redefine a story target ID |
| Villager event triggers | Yes | Replace by trigger `id` | No removal flag |

Use your own file names when you want additive content:

```text
data/my_pack/dialogue/en_us/my_pack/lines/rumors.json
data/villagerretaliation/notifications/en_us/my_pack/world_text.json
data/villagerretaliation/gifts/my_pack_preferences.json
data/villagerretaliation/currency/default.json
data/villagerretaliation/village_names/my_village_names.json
```

Use a small control file when you want a complete overhaul:

```json
{ "replace": true }
```

For dialogue trees and forced dialogue, a control-only `replace` file disables the built-ins without registering a dummy entry. Quest bundles do not use loader-wide control files.

## Suggested Workflow

1. Make one file.
2. Put one obvious line or rule in it.
3. Run `/reload`.
4. Trigger that feature in game.
5. Only then add more filters or more entries.

Example first test:

```text
data/my_pack/dialogue/en_us/my_pack/messages/00_test.json
```

```json
{
  "id": "my_pack.message.test",
  "key": "my_pack.message.test",
  "text": "Testing."
}
```

If the file loads, you know the path and JSON shape are valid before you build something more complex around it.

## Reload And Diagnostic Commands

```mcfunction
/reload
/vr admin datapack diagnostics
/vr admin villager reputation set <targets> <player> <value>
/vr admin dialogue explain <villager> <request> [option_id]
```

`datapack diagnostics` reports loading and validation problems. `dialogue explain` reports why a line matched or was rejected.

## Common Mistakes

- Putting `notifications` data inside a dialogue file.
- Using the wrong namespace for gifts, pacification, or notifications.
- Forgetting to add stable `id` values to content you want to translate or override later.
- Copying a built-in file path when you only meant to add one extra line.
- Adding heavy filters before verifying the unfiltered version works.
- Using resource-pack format `34` for a Minecraft 1.21.1 datapack. Datapacks use format `48`.

## Example Layout

This is a clean small addon that touches several systems:

```text
pack.mcmeta
data/
  villagerretaliation/
    gifts/my_pack_gifts.json
    notifications/en_us/my_pack_notifications.json
  my_pack/
    dialogue/en_us/my_pack/options/00_rumor.json
    dialogue/en_us/my_pack/lines/00_rumor.json
    dialogue_tuning/conversation_chances.json
    forced_dialogue/my_pack_events.json
    quests/_shared/pools/daily_commissions.json
    quests/old_roads/road_ledger/quest.json
    quests/old_roads/road_ledger/locales/en_us.json
```

That is usually easier to maintain than one giant file per system.
