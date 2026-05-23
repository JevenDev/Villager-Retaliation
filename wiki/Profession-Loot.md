# Profession Loot JSON

Profession loot rules connect villager professions to Minecraft loot tables. Use this when a datapack should add, remove, or replace profession-themed drops without changing Java.

## Paths

Rule files must be in the `villagerretaliation` namespace:

```text
data/villagerretaliation/profession_loot/default.json
data/villagerretaliation/profession_loot/my_pack_loot.json
```

Referenced loot tables can live in any namespace:

```text
data/villagerretaliation/loot_table/villager/profession/farmer/common.json
data/my_pack/loot_table/villager/profession/alchemist/common.json
```

## Top-Level Sections

| Key | Purpose |
| --- | --- |
| `tables` | Profession loot rules. |
| `loot_tables` | Alias for `tables`. |
| `replace` | If `true`, clears previously loaded profession loot rules before this file is read. |

## Rule Example

```json
{
  "tables": [
    {
      "id": "my_pack.alchemist.common",
      "professions": ["examplemod:alchemist"],
      "loot_table": "my_pack:villager/profession/alchemist/common",
      "chance": "always"
    },
    {
      "id": "my_pack.farmer.extra_rare",
      "professions": ["farmer"],
      "loot_table": "my_pack:villager/profession/farmer/extra_rare",
      "chance": "rare"
    }
  ]
}
```

## Rule Fields

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `id` | string | generated | Stable id used for replacement and removal. |
| `remove` | boolean | `false` | If `true`, removes the earlier rule with the same `id`. |
| `professions` | string or array | any | Profession filter. Vanilla ids may omit `minecraft:`; custom professions should use full ids. |
| `loot_table` | string | required | Loot table id to roll when the rule matches. |
| `chance` | string or number | `always` | `always`, `rare`, `very_rare`, or a fixed number from `0.0` to `1.0`. |

`rare` and `very_rare` use the matching Villager Retaliation config values, so packs can respect server balance settings without hardcoding percentages.

## Removing Built-In Rules

The built-in rules use ids like:

```text
villagerretaliation.profession_loot.farmer.common
villagerretaliation.profession_loot.farmer.rare
villagerretaliation.profession_loot.librarian.very_rare
```

Remove one rule without copying the whole default file:

```json
{
  "tables": [
    {
      "id": "villagerretaliation.profession_loot.farmer.rare",
      "remove": true
    }
  ]
}
```

To replace the full built-in table, use `replace: true` in a later-sorting file or override `data/villagerretaliation/profession_loot/default.json`.

## Loot Table Example

```json
{
  "type": "minecraft:entity",
  "pools": [
    {
      "rolls": { "type": "minecraft:uniform", "min": 1.0, "max": 2.0 },
      "bonus_rolls": 0.0,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "minecraft:amethyst_shard",
          "functions": [
            {
              "function": "minecraft:set_count",
              "count": { "type": "minecraft:uniform", "min": 1.0, "max": 3.0 }
            }
          ]
        }
      ]
    }
  ]
}
```

Profession loot still obeys the main villager loot config gates, including the global profession-drop chance and the optional player-kill requirement.
