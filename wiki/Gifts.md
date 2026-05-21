# Gift JSON

Gift JSON controls which items villagers like or dislike and which rewards trusted villagers can give back.

## Paths

Gift files must be in the `villagerretaliation` namespace:

```text
data/villagerretaliation/gifts/default.json
data/villagerretaliation/gifts/my_pack_extra_gifts.json
```

Gift files are not locale-specific. Dialogue and notification text handles localization.

## Top-Level Sections

| Key | Purpose |
| --- | --- |
| `preferences` | Item or tag rules that choose a gift reaction. |
| `rewards` | Items villagers can return at high reputation. |

## Gift Reactions

Use these values in `reaction`:

| Reaction | Default reputation per item |
| --- | ---: |
| `loved` | 6 |
| `liked` | 3 |
| `neutral` | 0 |
| `disliked` | -2 |
| `hated` | -5 |

Total reputation from one gifted stack is clamped between `-100` and `120`.

## Preference Example

```json
{
  "preferences": [
    {
      "professions": ["farmer"],
      "reaction": "loved",
      "items": ["minecraft:wheat", "minecraft:golden_carrot"],
      "tags": ["minecraft:villager_plantable_seeds"],
      "reputation_per_item": 6,
      "priority": 10
    }
  ]
}
```

## Preference Fields

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `reaction` | enum | required | `loved`, `liked`, `neutral`, `disliked`, or `hated`. |
| `item` | string or array | none | One or more item ids. |
| `items` | string or array | none | One or more item ids. |
| `tag` | string or array | none | One or more item tag ids. |
| `tags` | string or array | none | One or more item tag ids. |
| `professions` | string or array | any | If present, rule applies only to those professions. |
| `reputation_per_item` | integer | reaction default | Overrides the per-item reputation value. |
| `priority` | integer | `0` | Higher priority wins among matching rules. |

At least one item or tag selector is required.

## Item And Tag Selectors

These forms all work:

```json
{
  "items": ["bread", "minecraft:apple"],
  "tags": ["minecraft:villager_plantable_seeds"]
}
```

Inside `items`, a value beginning with `#` is treated as a tag:

```json
{
  "items": ["#minecraft:villager_plantable_seeds"]
}
```

## Matching And Overrides

When multiple preference rules match:

1. Higher `priority` wins.
2. Earlier rule order wins when priority ties.
3. If any matching rule is profession-specific, generic matches are ignored.

That means a farmer-specific rule beats a global rule for the same item, even if the global rule also matches.

## Reward Example

```json
{
  "rewards": [
    {
      "professions": ["farmer"],
      "reputation_levels": ["revered", "royalty"],
      "item": "minecraft:golden_carrot",
      "min_count": 2,
      "max_count": 5,
      "weight": 10
    }
  ]
}
```

## Reward Fields

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `item` | string | required | Reward item id. |
| `professions` | string or array | any | Profession filter. |
| `reputation_levels` | string or array | any | Reputation tier filter. |
| `min_count` | integer | `1` | Minimum stack count, clamped to at least 1. |
| `max_count` | integer | `min_count` | Maximum stack count, clamped to at least `min_count`. |
| `weight` | integer | `10` | Weighted selection. |

If any profession-specific reward matches, generic rewards are ignored for that reward roll.

## Add-On Pack Strategy

To add extra gifts while keeping the built-in table, create a new file:

```text
data/villagerretaliation/gifts/my_pack_extra_gifts.json
```

To replace all built-in gifts, override:

```text
data/villagerretaliation/gifts/default.json
```

with your own `preferences` and `rewards` arrays.

