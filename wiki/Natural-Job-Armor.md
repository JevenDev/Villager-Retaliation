# Natural Job Armor

Natural job armor controls the armor fresh villagers can receive when they naturally enter the world and later resolve to a configured profession.

## Paths

```text
data/villagerretaliation/natural_job_armor/<file>.json
```

## Example

```json
{
  "profiles": [
    {
      "id": "my_pack.guard_smiths",
      "professions": ["armorer", "toolsmith", "weaponsmith"],
      "chance": {
        "peaceful": 0.25,
        "easy": 0.40,
        "normal": 0.60,
        "hard": 0.80
      },
      "next_piece_chance": {
        "peaceful": 0.50,
        "easy": 0.60,
        "normal": 0.75,
        "hard": 0.90
      },
      "mixed_gear_chance": {
        "peaceful": 0.05,
        "easy": 0.10,
        "normal": 0.20,
        "hard": 0.30
      },
      "enchant_chance": {
        "peaceful": 0.01,
        "easy": 0.03,
        "normal": 0.08,
        "hard": 0.16
      },
      "armor_sets": [
        {
          "id": "iron",
          "material": "iron",
          "weight": 95
        },
        {
          "id": "diamond",
          "material": "diamond",
          "weight_by_difficulty": {
            "peaceful": 0,
            "easy": 1,
            "normal": 2,
            "hard": 5
          }
        }
      ]
    }
  ]
}
```

## Fields

| Field | Meaning |
| --- | --- |
| `replace` | Clears previously loaded natural job armor profiles before this file applies. |
| `profiles` / `armor_profiles` | Array of profile entries. |
| `id` | Stable profile id. Later entries with the same id replace earlier ones. |
| `remove` | Removes an earlier profile with the same `id`. |
| `profession` / `professions` | One or more villager professions. Vanilla professions can omit `minecraft:`. |
| `chance` / `armor_chance` | Chance that a matching fresh villager receives any armor. Number or per-difficulty object. |
| `next_piece_chance` | Chance to continue adding another armor piece after each piece except the helmet. |
| `mixed_gear_chance` | Per-piece chance to choose a different eligible armor set instead of the profile's base armor set. |
| `enchant_chance` | Per-piece chance to apply vanilla mob-spawn equipment enchantments. |
| `armor_sets` / `materials` | Weighted armor set entries. |

Chance objects support `peaceful`, `easy`, `normal`, and `hard`, with values from `0.0` to `1.0`.

Modded professions are supported by using their full registry id:

```json
"professions": ["examplemod:guard", "examplemod:archer"]
```

Modded villager entities can use these rules when they are villager-like entities that expose normal villager data through Minecraft's `VillagerDataHolder` contract.

## Armor Sets

Use a vanilla material shorthand:

```json
{
  "material": "chainmail",
  "weight": 30
}
```

Supported material shorthands are `leather`, `chainmail`, `iron`, and `diamond`.

Or provide explicit item ids:

```json
{
  "id": "modded_guard_set",
  "weight": 10,
  "items": {
    "feet": "examplemod:guard_boots",
    "legs": "examplemod:guard_leggings",
    "chest": "examplemod:guard_chestplate",
    "head": "examplemod:guard_helmet"
  }
}
```

Armor set weights can be a single `weight` or a per-difficulty `weight_by_difficulty` object.

When `mixed_gear_chance` passes, the piece rerolls from the same `armor_sets` list, excluding the base set when another weighted set is available.
