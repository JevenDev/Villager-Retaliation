# Example Packs

These examples are intentionally small. Use them as starter files, then add filters and entries as needed.

## Minimal Datapack Layout

```text
VillagerRetaliationExample/
  pack.mcmeta
  data/
    villagerretaliation/
      dialogue/
        en_us/
          examplepack_dialogue.json
      notifications/
        en_us/
          examplepack_notifications.json
      gifts/
        example_gifts.json
    examplepack/
      story_structures/
        haunted_places.json
      story_biomes/
        crystal_biomes.json
```

`pack.mcmeta`:

```json
{
  "pack": {
    "pack_format": 34,
    "description": "Villager Retaliation example datapack"
  }
}
```

## Dialogue Example

```text
data/villagerretaliation/dialogue/en_us/examplepack_dialogue.json
```

```json
{
  "options": [
    {
      "id": "examplepack.ask_local_rumors",
      "label": "Ask Local Rumors",
      "type": "story",
      "order": 30,
      "show_for_babies": false
    }
  ],
  "lines": [
    {
      "id": "examplepack.rumor.generic",
      "option": "examplepack.ask_local_rumors",
      "type": "story",
      "text": "Roads keep secrets. Villages keep better ones.",
      "weight": 10
    },
    {
      "id": "examplepack.share_story.haunted_keep",
      "type": "share_story",
      "option": "adult_share_story",
      "story_structure": "examplemod:haunted_keep",
      "text": "{target_article}. If you found it, walk home before dark.",
      "weight": 30
    }
  ],
  "messages": [
    {
      "id": "examplepack.sleep.broken_bed",
      "key": "sleep.broken_bed",
      "text": "That bed had a name in this house.",
      "weight": 15
    }
  ]
}
```

## Notifications Example

```text
data/villagerretaliation/notifications/en_us/examplepack_notifications.json
```

```json
{
  "notifications": [
    {
      "id": "examplepack.ambient.trusted_farmer",
      "trigger": "ambient.murmur",
      "text": "Good harvest follows good neighbors",
      "world_text_kind": "murmur",
      "professions": ["farmer"],
      "reputation_levels": ["trusted", "respected", "revered", "royalty"],
      "color": "#DCEBA6",
      "weight": 20
    },
    {
      "id": "examplepack.trade.refused.hostile",
      "trigger": "trade.refused",
      "text": "Not today",
      "world_text_kind": "negative",
      "reputation_levels": ["hostile", "despised", "feared"],
      "color": "red"
    }
  ]
}
```

## Gifts Example

```text
data/villagerretaliation/gifts/example_gifts.json
```

```json
{
  "preferences": [
    {
      "professions": ["librarian"],
      "reaction": "loved",
      "items": ["minecraft:enchanted_book", "minecraft:name_tag"],
      "priority": 20
    },
    {
      "reaction": "disliked",
      "items": ["minecraft:cobweb"],
      "reputation_per_item": -1
    }
  ],
  "rewards": [
    {
      "professions": ["librarian"],
      "reputation_levels": ["revered", "royalty"],
      "item": "minecraft:book",
      "min_count": 2,
      "max_count": 5,
      "weight": 10
    }
  ]
}
```

## Story Structure Example

```text
data/examplepack/story_structures/haunted_places.json
```

```json
{
  "radius": 128,
  "entries": [
    {
      "structure": "examplemod:haunted_keep",
      "name": "Haunted Keep"
    }
  ]
}
```

## Story Biome Example

```text
data/examplepack/story_biomes/crystal_biomes.json
```

```json
{
  "entries": [
    {
      "biome": "examplemod:crystal_marsh",
      "name": "Crystal Marsh"
    }
  ]
}
```

## Minimal Resource Pack Layout

```text
VillagerRetaliationResourceExample/
  pack.mcmeta
  assets/
    minecraft/
      textures/entity/villager/villager.png
      textures/entity/wandering_trader.png
    villagerretaliation/
      textures/entity/villager/villager.png
      textures/entity/wandering_trader/wandering_trader.png
      models/entity/villager/combat_villager.json
```

Use the built-in `combat_villager.json` as the safest starting point, then change part dimensions or decorative children gradually.
