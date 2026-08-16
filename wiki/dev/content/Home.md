# Villager Retaliation Wiki

This wiki documents the current `1.0.0-beta.13` development surface for Villager Retaliation. The datapack builder still exposes beta.11 and beta.12 authoring targets where those schemas remain current. It is written for datapack authors, resource-pack authors, and modpack maintainers who want concrete file paths, working JSON, and clear override rules.

System pages include copyable examples wherever JSON shape or file placement matters.

## Quick Start

1. Read [Pack Development](Pack-Development.md) for folder layout, namespaces, overrides, and testing.
2. Read [JSON Reference](JSON-Reference.md) for shared rules like `id`, `replace`, `text` vs `lines`, and conditions.
3. If you want to make a quest, start with [First Quest Guide](First-Quest.md).
4. Pick the system you want to customize from the table below.
5. Copy a working example from [Example Packs](Example-Packs.md) or the full template pack.

## Terms Used Here

| Term | Plain meaning |
| --- | --- |
| Datapack | A folder or zip that adds server-side data such as quests, dialogue rules, loot tables, or structures. |
| Resource pack | A folder or zip that adds client assets such as textures, models, and GUI translations. |
| Namespace | The part before the colon in an ID. Use your pack name, such as `my_pack`, unless a page requires `villagerretaliation`. |
| Resource ID | A namespaced identifier such as `my_pack:bread_delivery`. The first part is the namespace and the second part is the path. |
| Loader | The part of the mod that reads one data folder. Each loader has its own merge and override rules. |
| Provider | The villager or other actor that offers or owns a quest. |
| Live context | The player and relevant villager are currently loaded, so an action can interact with them. |
| Persistent | Saved in the world and restored after a reload or server restart. |
| Scope | The player, villager, village, quest, party, or world that owns saved state. |

Terms that matter only to advanced scene or Java extension work are explained on the page that uses them.

## Systems

| Area | What it changes | Root path | Page |
| --- | --- | --- | --- |
| Dialogue | Talk menu options, replies, keyed text, openings, closings, pacify lines | `data/<namespace>/dialogue/<locale>/` | [Dialogue](Dialogue.md) |
| Dialogue tuning | Built-in conversation frequency and memory-response chances | `data/<namespace>/dialogue_tuning/` | [Dialogue](Dialogue.md#dialogue-frequency-tuning) |
| Dialogue trees | Branching scenes and authored conversations | `data/<namespace>/dialogue_trees/<locale>/` | [Dialogue Trees](Dialogue-Trees.md) |
| Quests | Offers, objectives, rewards, tracker text, quest triggers, and inline quest scenes | `data/<namespace>/quests/` | [Quests](Quests.md) |
| Quest pools | Rotating, scoped sets of quest offers | `data/<namespace>/quests/_shared/pools/` | [Quests](Quests.md#quest-pools) |
| First quest | A complete beginner quest in one quest module v2 file | `data/<namespace>/quests/` | [First Quest Guide](First-Quest.md) |
| Persistent quest scenes | Multi-actor cinematic graphs, recovery, and controlled encounters | Quest-private or `data/<namespace>/quests/_shared/scenes/` | [Persistent Quest Scenes](Quest-Scenes.md) |
| Forced dialogue | Event-driven locked scenes and chat barks | `data/<namespace>/forced_dialogue/` | [Forced Dialogue](Forced-Dialogue.md) |
| Villager event triggers | Actions started by recorded village memories | `data/<namespace>/villager_events/` | [Villager Event Triggers](Villager-Event-Triggers.md) |
| Notifications | HUD quest notices and ambient world text | `data/villagerretaliation/notifications/<locale>/` | [Notifications](Notifications.md) |
| Currency and item text | Payment items, wallet presentation, and counted item wording | `data/villagerretaliation/currency/` and `item_text/<locale>/` | [Currency And Item Text](Currency-And-Item-Text.md) |
| Gifts | Gift preferences and high-trust rewards | `data/villagerretaliation/gifts/` | [Gifts](Gifts.md) |
| Pacification | Items that calm hostile villagers | `data/villagerretaliation/pacification/` | [Pacification](Pacification.md) |
| Profession loot | Villager drop rules backed by loot tables | `data/villagerretaliation/profession_loot/` | [Profession Loot](Profession-Loot.md) |
| Natural job armor | Armor fresh villagers can spawn with by profession | `data/villagerretaliation/natural_job_armor/` | [Natural Job Armor](Natural-Job-Armor.md) |
| Player raids | Player-led village sieges and militia loadouts | `data/villagerretaliation/player_raid_loadouts/` | [Player Raids](Player-Raids.md) |
| Duel kits | Temporary player and villager equipment for duels | `data/<namespace>/duel_kits/` | [Duel Kits](Duel-Kits.md) |
| Skill trades | Skill-based extra trade offers and Special Orders | `data/<namespace>/skill_trades/` | [Skill Trades](Skill-Trades.md) |
| Builder structures | Structure templates hired builders can offer and build costs | `data/<namespace>/builder_structures/` | [Builder Structures](Builder-Structures.md) |
| Story discovery | Structures and biomes used by `share_story` dialogue | `data/<namespace>/story_structures/` and `story_biomes/` | [Story Discovery](Story-Discovery.md) |
| Generated containers | Loot tables that count as village property | `data/<namespace>/generated_containers/` | [Generated Containers](Generated-Containers.md) |
| Villager names | Add to or replace the preset name pool | `data/villagerretaliation/villager_names/` | [Villager Names](Villager-Names.md) |
| Village names | Add to or replace the generated village prefix and suffix pools | `data/villagerretaliation/village_names/` | [Village Names](Village-Names.md) |
| GUI localization | Buttons, profile text, relationship rows, profession labels | `assets/villagerretaliation/lang/<locale>.json` | [Localization](Localization.md) |
| Combat textures and models | Villager and trader combat visuals | `assets/...` | [Resource Pack Models](Resource-Pack-Models.md) |

## Smallest Working Example

This is the smallest useful dialogue addon: one option and one reply.

```text
data/my_pack/dialogue/en_us/my_pack/options/00_rumor.json
data/my_pack/dialogue/en_us/my_pack/lines/00_rumor.json
```

```json
{
  "id": "my_pack.option.ask_rumor",
  "label": "Ask For A Rumor",
  "request": "story"
}
```

```json
{
  "id": "my_pack.line.rumor",
  "request": "story",
  "option": "my_pack.option.ask_rumor",
  "text": "Roads carry stories faster than traders do."
}
```

Datapack changes take effect after `/reload`.

## Recommended Reading Order

- [Pack Development](Pack-Development.md)
- [JSON Reference](JSON-Reference.md)
- [Sell Box and Daily Market](Sell-Box-And-Daily-Market.md)
- [First Quest Guide](First-Quest.md) if you are making quests
- One of: [Dialogue](Dialogue.md), [Forced Dialogue](Forced-Dialogue.md), [Quests](Quests.md), [Notifications](Notifications.md), or [Builder Structures](Builder-Structures.md)
- [Example Packs](Example-Packs.md)
- [Pack Format Changes](Pack-Format-Changes.md) if you are updating an older pack

## Version Note

The builder keeps frozen beta.11 and beta.12 source snapshots under `tools/datapack-builder/wiki/`; those targets are intentionally not mirrors of this live wiki. Its generated `wiki-snapshot.js` also embeds the current beta.13 developer pages for offline use. Update a frozen source snapshot only when correcting that specific historical target.
