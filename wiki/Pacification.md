# Pacification JSON

Pacification JSON controls which held items can calm a hostile villager or wandering trader, and how many items are consumed.

## Paths

Pacification files must be in the `villagerretaliation` namespace:

```text
data/villagerretaliation/pacification/default.json
data/villagerretaliation/pacification/my_pack_currency.json
```

Pacification files are not locale-specific. Dialogue text handles localization.

## Example

```json
{
  "payments": [
    {
      "item": "numismatic-overhaul:gold_coin",
      "count": 12,
      "name": "gold coin",
      "plural_name": "gold coins",
      "priority": 10
    },
    {
      "items": ["minecraft:emerald", "minecraft:diamond"],
      "min_count": 3,
      "max_count": 32
    }
  ]
}
```

## Payment Fields

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `item` | string or array | none | One or more item ids. |
| `items` | string or array | none | One or more item ids. |
| `tag` | string or array | none | One or more item tag ids. |
| `tags` | string or array | none | One or more item tag ids. |
| `professions` | string or array | any | If present, rule applies only to those villager professions. Wandering traders match `none`. |
| `count` | integer | none | Exact number of items to consume. Clamped from `1` to `64`. |
| `min_count` | integer | `1` | Minimum random cost when `count` is omitted. Clamped from `1` to `64`. |
| `max_count` | integer | `min_count` | Maximum random cost when `count` is omitted. Clamped from `1` to `64`. |
| `name` | string | held item name | Singular name used by dialogue placeholders. |
| `plural_name` | string | `name` | Plural name used by dialogue placeholders when count is not `1`. |
| `priority` | integer | `0` | Higher priority wins among matching rules. |

At least one item or tag selector is required.

## Item And Tag Selectors

Unnamespaced item ids are treated as Minecraft ids:

```json
{
  "items": ["emerald", "minecraft:diamond"],
  "tags": ["c:coins"]
}
```

Inside `items`, a value beginning with `#` is treated as a tag:

```json
{
  "items": ["#c:coins"]
}
```

## Matching And Overrides

When multiple payment rules match:

1. Higher `priority` wins.
2. Earlier rule order wins when priority ties.
3. If any matching rule is profession-specific, generic matches are ignored.

To keep vanilla emerald pacification and add modded currency, add a new file under `data/villagerretaliation/pacification/`. To replace the built-in emerald rule, override `data/villagerretaliation/pacification/default.json`.
