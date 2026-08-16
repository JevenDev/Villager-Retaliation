# Story Discovery

Story discovery files list structures and biomes that villagers can turn into discovery leads. The `share_story` request uses the player's recorded discovery context to choose matching dialogue.

Adding a structure or biome here does not add it to world generation. The target must already be registered by Minecraft or another mod.

## Paths

```text
data/<namespace>/story_structures/<file>.json
data/<namespace>/story_biomes/<file>.json
```

Both systems can use any namespace.

## Structure Example

```json
{
  "radius": 128,
  "entries": [
    {
      "structure": "examplemod:haunted_keep",
      "name": "Haunted Keep"
    },
    {
      "structures": [
        "examplemod:ruined_watchtower",
        "examplemod:ruined_gate"
      ],
      "radius": 96
    }
  ]
}
```

The root `radius` is the default for entries in that file. An entry can override it. If neither supplies a radius, the default is 96 blocks.

`structure` accepts one ID. `structures` accepts one or more IDs. When several IDs share one `name`, they also share that display name.

## Biome Example

```json
{
  "entries": [
    {
      "biome": "examplemod:crystal_marsh",
      "name": "Crystal Marsh"
    },
    {
      "biomes": [
        "examplemod:ashen_fen",
        "examplemod:smoke_bog"
      ]
    }
  ]
}
```

Biomes do not use a radius field. Discovery follows the player's current biome.

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

`story_structure` restricts the line to one structure. Use `story_structures` for several. Biome lines use `story_biome` or `story_biomes`.

Without a story target filter, a `share_story` line can match any current story target.

## Main Fields

| Field | Meaning |
| --- | --- |
| `structure` or `structures` | One or more registered structure IDs. |
| `biome` or `biomes` | One or more registered biome IDs. |
| `name` | Player-facing target name used by story text. |
| `radius` | Structure discovery radius in blocks. The minimum is 1. |

If `name` is omitted, the mod turns the resource path into readable text. For example, `examplemod:haunted_keep` becomes `Haunted Keep`. Supply `name` when capitalization, punctuation, or translation matters.

## Loading And Overrides

Every valid target ID is stored once. When later data defines the same target, the later definition replaces its name and structure radius.

There is no `replace` or `remove` field. Use the exact same namespace and file path to replace a lower-priority resource, or redefine the same target ID in a later-loading file.
