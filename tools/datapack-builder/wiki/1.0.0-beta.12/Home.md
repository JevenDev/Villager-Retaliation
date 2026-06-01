# Villager Retaliation Wiki

This wiki documents the current `1.0.0-beta.12` pack surface for Villager Retaliation. It is written for datapack authors, resource-pack authors, and modpack maintainers who want concrete file paths, working JSON, and clear override rules.

Every page includes at least one copyable example. Start small, test one system at a time, and only add filters after the base hook works.

## Quick Start

1. Read [Pack Development](Pack-Development.md) for folder layout, namespaces, overrides, and testing.
2. Read [JSON Reference](JSON-Reference.md) for shared rules like `id`, `replace`, `text` vs `lines`, and conditions.
3. Pick the system you want to customize from the table below.
4. Copy a working example from [Example Packs](Example-Packs.md) or the full template pack.

## Systems

| Area | What it changes | Root path | Page |
| --- | --- | --- | --- |
| Dialogue | Talk menu options, replies, keyed text, openings, closings, pacify lines | `data/<namespace>/dialogue/<locale>/` | [Dialogue](Dialogue.md) |
| Dialogue trees | Branching scenes and authored conversations | `data/<namespace>/dialogue_trees/<locale>/` | [Dialogue Trees](Dialogue-Trees.md) |
| Quests | Offers, objectives, rewards, tracker text, quest triggers | `data/<namespace>/quests/` | [Quests](Quests.md) |
| Forced dialogue | Event-driven locked scenes and chat barks | `data/<namespace>/forced_dialogue/` | [Forced Dialogue](Forced-Dialogue.md) |
| Notifications | HUD quest notices and ambient world text | `data/villagerretaliation/notifications/<locale>/` | [Notifications](Notifications.md) |
| Gifts | Gift preferences and high-trust rewards | `data/villagerretaliation/gifts/` | [Gifts](Gifts.md) |
| Pacification | Items that calm hostile villagers | `data/villagerretaliation/pacification/` | [Pacification](Pacification.md) |
| Profession loot | Villager drop rules backed by loot tables | `data/villagerretaliation/profession_loot/` | [Profession Loot](Profession-Loot.md) |
| Skill trades | Skill-based extra trade offers and Special Orders | `data/<namespace>/skill_trades/` | [Skill Trades](Skill-Trades.md) |
| Story discovery | Structures and biomes used by `share_story` dialogue | `data/<namespace>/story_structures/` and `story_biomes/` | [Story Discovery](Story-Discovery.md) |
| Villager names | Add to or replace the preset name pool | `data/villagerretaliation/villager_names/` | [Villager Names](Villager-Names.md) |
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

Run `/reload`, talk to a villager, and confirm the option appears.

## Recommended Reading Order

- [Pack Development](Pack-Development.md)
- [JSON Reference](JSON-Reference.md)
- One of: [Dialogue](Dialogue.md), [Forced Dialogue](Forced-Dialogue.md), [Quests](Quests.md), or [Notifications](Notifications.md)
- [Example Packs](Example-Packs.md)
- [Pack Format Changes](Pack-Format-Changes.md) if you are updating an older pack

## Version Note

The builder also ships versioned wiki snapshots under `tools/datapack-builder/wiki/`. This repo wiki and the `1.0.0-beta.12` builder wiki should match. Older snapshots exist so beta.11 packs can keep their historical reference surface.
