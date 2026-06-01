# Profession Loot

Profession loot ties villager professions to Minecraft loot tables.

## Paths

Rules:

```text
data/villagerretaliation/profession_loot/<file>.json
```

Referenced loot tables:

```text
data/<namespace>/loot_table/villager/profession/<profession>/<table>.json
```

## Rule Example

```json
{
  "tables": [
    {
      "id": "my_pack.alchemist.common",
      "professions": ["examplemod:alchemist"],
      "loot_table": "my_pack:villager/profession/alchemist/common",
      "chance": "always"
    }
  ]
}
```

## Loot Table Example

```json
{
  "type": "minecraft:entity",
  "pools": [
    {
      "rolls": 1,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "minecraft:amethyst_shard"
        }
      ]
    }
  ]
}
```

## Common Uses

- give a modded profession its own drops
- add a rare drop to an existing vanilla profession
- remove one built-in profession rule by `id`

## Chance Values

Use either:

- `"always"`
- `"rare"`
- `"very_rare"`
- a fixed numeric chance such as `0.25`

`rare` and `very_rare` defer to the mod's config values.
