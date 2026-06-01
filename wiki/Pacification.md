# Pacification

Pacification files decide which items can calm a hostile villager or wandering trader.

## Path

```text
data/villagerretaliation/pacification/<file>.json
```

## Simple Example

```json
{
  "payments": [
    {
      "id": "my_pack.pacification.emerald",
      "items": ["minecraft:emerald"],
      "count": 8,
      "priority": 10
    }
  ]
}
```

## Example: Modded Currency

```json
{
  "payments": [
    {
      "id": "my_pack.pacification.coins",
      "item": "numismatic-overhaul:gold_coin",
      "count": 12,
      "name": "gold coin",
      "plural_name": "gold coins",
      "priority": 20
    }
  ]
}
```

## Example: Profession-Specific Cost

```json
{
  "payments": [
    {
      "id": "my_pack.pacification.toolsmith",
      "professions": ["minecraft:toolsmith"],
      "items": ["minecraft:iron_ingot"],
      "min_count": 2,
      "max_count": 4
    }
  ]
}
```

## Main Fields

| Field | Use |
| --- | --- |
| `items` or `tags` | Which item ids or tags qualify |
| `count` | Exact payment |
| `min_count` / `max_count` | Randomized payment range |
| `professions` | Restrict the rule to specific villager professions |
| `priority` | Break ties between several matches |
| `name` / `plural_name` | Better wording for spoken text and placeholders |

The spoken pacify line itself belongs in normal dialogue under a `pacify/` folder.
