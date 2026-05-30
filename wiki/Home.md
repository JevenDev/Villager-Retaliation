# Villager Retaliation Wiki

Villager Retaliation is data-driven in the places pack makers usually want to touch: dialogue, forced dialogue events, notifications, gifts, pacification payments, profession loot, skill trades, story discoveries, villager names, combat villager models, and beta.12 dialogue filters for temporary moods and villager Social Attributes. This wiki is written for JSON authors, datapack authors, resource-pack authors, and modpack developers who want predictable hooks without touching Java.

## Quick Links

- [Pack Development Guide](Pack-Development.md) - folder layout, namespaces, reload behavior, override strategy, and testing workflow.
- [Datapack Generator](Datapack-Generator.md) - browser-based builder for creating, importing, previewing, and exporting Villager Retaliation datapacks.
- [Pack Format Changes](Pack-Format-Changes.md) - pack-facing added, modified, deprecated, and removed fields, tags, triggers, paths, and manual retargeting notes.
- [JSON Reference](JSON-Reference.md) - shared conventions, text/line variation rules, enum values, filters, ids, weights, colors, and common pitfalls.
- [Dialogue JSON](Dialogue.md) - conversation options, dialogue lines, line variations, openings, closings, pacify lines, keyed messages, localization overlays, and story placeholders.
- [Dialogue And Quests](Dialogue-And-Quests.md) - the shared metadata, tag, and linking standard across ambient dialogue, dialogue trees, forced dialogue, and quests.
- [Quest JSON](Quests.md) - quest lifecycle rules, repeat limits, abandonment policies, rewards, tracker text, and event triggers.
- [Forced Dialogue JSON](Forced-Dialogue.md) - event-driven locked dialogue moments, including witnessed container theft outcomes and aggro choices.
- [Dialogue Requests](Dialogue-Requests.md) - every current dialogue `request` value, with simple and expanded dropdown examples.
- [Event Tags](Event-Tags.md) - every current `event_tags` / `player_event_tags` value, with simple and expanded dropdown examples.
- [Notifications JSON](Notifications.md) - HUD notifications, ambient world text, line variations, trigger ids, colors, notice kinds, filters, and translated replacements.
- [Notification Triggers](Notification-Triggers.md) - every built-in notification `trigger`, with simple and expanded dropdown examples.
- [Localization Guide](Localization.md) - how datapack locale folders and resource-pack language keys work together.
- [Gift JSON](Gifts.md) - gift preferences, reactions, item and tag selectors, profession overrides, priority, and high-reputation rewards.
- [Pacification JSON](Pacification.md) - item or tag payments, modded currencies, costs, and profession-specific pacify rules.
- [Profession Loot JSON](Profession-Loot.md) - datapack profession loot rules backed by Minecraft loot tables.
- [Skill Trades](Skill-Trades.md) - profession, skill, rank, chance, item, enchantment, and config-gated extra trade offers.
- [Story Discovery JSON](Story-Discovery.md) - structure and biome discovery lists used by `share_story` dialogue.
- [Villager Names](Villager-Names.md) - adding to or replacing the preset name pool.
- [Resource Pack Models And Textures](Resource-Pack-Models.md) - combat textures, combat model JSON, non-combat model opt-in, and EMF compatibility.
- [Example Packs](Example-Packs.md) - copyable datapack and resource-pack examples.

## What Can Be Customized

| Area | Pack type | Root path |
| --- | --- | --- |
| Dialogue | Datapack | `data/villagerretaliation/dialogue/<locale>/` |
| Quests | Datapack | `data/<namespace>/quests/` |
| Dialogue mood and Social Attribute filters | Datapack | `data/villagerretaliation/dialogue/<locale>/` |
| Forced dialogue and chat event lines | Datapack | `data/villagerretaliation/forced_dialogue/` |
| Notifications and world text | Datapack | `data/villagerretaliation/notifications/<locale>/` |
| Interaction GUI and reputation UI text | Resource pack | `assets/villagerretaliation/lang/<locale>.json` |
| Gifts and rewards | Datapack | `data/villagerretaliation/gifts/` |
| Pacification payments | Datapack | `data/villagerretaliation/pacification/` |
| Profession loot rules | Datapack | `data/villagerretaliation/profession_loot/` |
| Profession loot tables | Datapack | `data/<namespace>/loot_table/villager/profession/` |
| Skill trades | Datapack | `data/<namespace>/skill_trades/` |
| Story structures | Datapack | `data/<namespace>/story_structures/` |
| Story biomes | Datapack | `data/<namespace>/story_biomes/` |
| Preset villager names | Datapack | `data/villagerretaliation/villager_names/` |
| Combat textures | Resource pack | `assets/minecraft/textures/entity/...` and `assets/villagerretaliation/textures/entity/...` |
| Combat model | Resource pack | `assets/villagerretaliation/models/entity/villager/combat_villager.json` |
| Optional non-combat model | Resource pack | `assets/villagerretaliation/models/entity/villager/render_options.json` |

## Recommended Reading Order

1. Start with [Pack Development Guide](Pack-Development.md) for where files go and how packs layer.
2. Read [JSON Reference](JSON-Reference.md) for common field rules.
3. Jump to the page for the system you want to change.
4. Use [Example Packs](Example-Packs.md) as a starter and trim it down.

## Compatibility Notes

Villager Retaliation is built around vanilla villager systems. The JSON files in this wiki add, replace, or filter mod-provided data; they do not register new villager professions, items, entities, structures, or biomes by themselves. Use normal Minecraft datapack, resource-pack, or mod systems for that, then reference the resulting ids from Villager Retaliation JSON.
