# Story Discovery JSON

Story discovery JSON defines structures and biomes that villagers can turn into `share_story` dialogue.

## Paths

Unlike dialogue, notifications, and gifts, story discovery files can live in any namespace:

```text
data/villagerretaliation/story_structures/dangerous_places.json
data/my_pack/story_structures/haunted_places.json
data/villagerretaliation/story_biomes/vanilla.json
data/my_pack/story_biomes/crystal_biomes.json
```

## Structure Entries

Structure files live under:

```text
data/<namespace>/story_structures/
```

You can define one entry at the root:

```json
{
  "structure": "examplemod:haunted_keep",
  "name": "Haunted Keep",
  "radius": 128
}
```

Or multiple entries:

```json
{
  "radius": 96,
  "entries": [
    {
      "structure": "minecraft:ancient_city",
      "name": "Ancient City",
      "radius": 128
    },
    {
      "structures": ["examplemod:haunted_keep", "examplemod:ruined_watchtower"],
      "name": "Haunted Ruins"
    }
  ]
}
```

## Structure Fields

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `structure` | string or array | none | Structure id or ids. |
| `structures` | string or array | none | Additional structure id or ids. |
| `name` | string | generated from id path | Display name used by story placeholders. |
| `radius` | integer | root radius or `96` | Detection radius in blocks, clamped to at least 1. |
| `entries` | array | none | Multiple entry objects. |

When `entries` is present, root-level `radius` becomes the fallback radius for entries that omit their own.

## Biome Entries

Biome files live under:

```text
data/<namespace>/story_biomes/
```

Example:

```json
{
  "entries": [
    {
      "biome": "minecraft:deep_dark",
      "name": "Deep Dark"
    },
    {
      "biomes": ["examplemod:crystal_marsh", "examplemod:glimmering_fen"],
      "name": "Crystal Marsh"
    }
  ]
}
```

## Biome Fields

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `biome` | string or array | none | Biome id or ids. |
| `biomes` | string or array | none | Additional biome id or ids. |
| `name` | string | generated from id path | Display name used by story placeholders. |
| `entries` | array | none | Multiple entry objects. |

## Using Story Entries In Dialogue

Story entries unlock dialogue lines with `type: "share_story"`.

```json
{
  "lines": [
    {
      "id": "my_pack.share_story.haunted_keep",
      "type": "share_story",
      "option": "adult_share_story",
      "story_structure": "examplemod:haunted_keep",
      "text": "{target_article}. We do not say its name after sundown.",
      "weight": 24
    },
    {
      "id": "my_pack.share_story.crystal_marsh",
      "type": "share_story",
      "option": "adult_share_story",
      "story_biome": "examplemod:crystal_marsh",
      "text": "{target_article}? Pretty places still drown boots.",
      "weight": 24
    }
  ]
}
```

Use `story_structures` or `story_biomes` when one line should match several targets.

## Placeholders

`share_story` lines can use:

```text
{target}
{target_article}
```

The target name comes from the story entry. If `name` is omitted, the id path is turned into a readable name.

