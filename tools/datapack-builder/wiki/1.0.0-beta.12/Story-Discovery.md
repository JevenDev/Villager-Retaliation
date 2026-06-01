# Story Discovery

Story discovery files define the structures and biomes villagers can talk about through `share_story`.

## Paths

```text
data/<namespace>/story_structures/<file>.json
data/<namespace>/story_biomes/<file>.json
```

## Structure Example

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

## Biome Example

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

## Dialogue Example

```json
{
  "id": "my_pack.story.haunted_keep",
  "request": "share_story",
  "option": "adult_share_story",
  "story_structure": "examplemod:haunted_keep",
  "text": "{target_article}. We do not say its name after sundown."
}
```

## Main Fields

| Field | Use |
| --- | --- |
| `structure` / `structures` | One or more structure ids |
| `biome` / `biomes` | One or more biome ids |
| `name` | Human-readable display name |
| `radius` | Discovery radius for structure checks |

If `name` is omitted, the mod derives a readable name from the id path.
