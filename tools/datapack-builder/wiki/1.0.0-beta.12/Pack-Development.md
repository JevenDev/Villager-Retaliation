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
      notifications/
      pacification/
      profession_loot/
      villager_names/
    my_pack/
      builder_structures/
      dialogue/en_us/
      dialogue_trees/en_us/
      forced_dialogue/
      quests/
      skill_trades/
      story_structures/
      story_biomes/
      loot_table/
```

Use a resource pack for GUI text, textures, and model JSON:

```text
<resource-pack root>/
  pack.mcmeta
  assets/
    villagerretaliation/
      lang/en_us.json
      models/entity/villager/combat_villager.json
      models/entity/villager/render_options.json
      textures/entity/villager/villager.png
```

## Namespace Rules

These systems are intentionally fixed to the mod namespace:

| System | Namespace |
| --- | --- |
| Notifications | `villagerretaliation` |
| Gifts | `villagerretaliation` |
| Pacification | `villagerretaliation` |
| Profession loot rules | `villagerretaliation` |
| Villager names | `villagerretaliation` |

These systems can live in your own namespace:

- Dialogue
- Dialogue trees
- Quests
- Forced dialogue
- Skill trades
- Builder structures
- Story structures
- Story biomes
- Referenced loot tables

Example:

```text
data/my_pack/dialogue/en_us/global/lines/rumors.json
data/my_pack/quests/lost_civilization/echo_shard.json
data/my_pack/skill_trades/cartographer.json
data/my_pack/builder_structures/custom_houses.json
data/my_pack/loot_table/villager/profession/alchemist/common.json
```

## Override Rules

Minecraft resolves exact resource paths first. Villager Retaliation then merges the files it finds for that loader.

- A file at the same resource path as a built-in file replaces that built-in file before VR reads it.
- Inside many systems, a later entry with the same `id` replaces an earlier entry without replacing the whole file.
- Top-level `replace: true` clears the loaded pool for that file type before the current file is applied.

Use your own file names when you want additive content:

```text
data/my_pack/dialogue/en_us/my_pack/lines/rumors.json
data/villagerretaliation/notifications/en_us/my_pack/world_text.json
data/villagerretaliation/gifts/my_pack_preferences.json
```

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

## Testing Commands

```mcfunction
/reload
/villagerretaliation datapack diagnostics
/villagerretaliation setNearbyReputation <value>
/villagerretaliation dialogue explain <villager> <request> [option_id]
```

Use `datapack diagnostics` after a reload whenever a file appears to do nothing. Use `dialogue explain` when a line should match but does not.

## Common Mistakes

- Putting `notifications` data inside a dialogue file.
- Using the wrong namespace for gifts, pacification, or notifications.
- Forgetting to add stable `id` values to content you want to translate or override later.
- Copying a built-in file path when you only meant to add one extra line.
- Adding heavy filters before verifying the unfiltered version works.

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
    forced_dialogue/my_pack_events.json
    quests/old_roads/road_ledger.json
    dialogue_trees/en_us/quests/old_roads/road_ledger.json
```

That is usually easier to maintain than one giant file per system.
