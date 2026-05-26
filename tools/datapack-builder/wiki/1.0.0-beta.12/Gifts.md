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
| `replace` | If `true`, clears previously loaded preferences and rewards before this file is read. |

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

Players can only give a gift when the receiving villager has room to store the full gifted stack. If the villager inventory is full, the gift is refused and the item stays with the player.

## Preference Example

```json
{
  "preferences": [
    {
      "id": "my_pack.farmer.favorite_crop",
      "professions": ["farmer"],
      "reaction": "loved",
      "items": ["minecraft:wheat", "minecraft:golden_carrot"],
      "tags": ["minecraft:villager_plantable_seeds"],
      "reputation_per_item": 6,
      "response_key": "my_pack.gift.farmer.favorite_crop",
      "priority": 10
    }
  ]
}
```

## Preference Fields

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `id` | string | generated | Stable id used for replacement and removal. |
| `remove` | boolean | `false` | If `true`, removes the earlier preference with the same `id`. |
| `reaction` | enum | required | `loved`, `liked`, `neutral`, `disliked`, or `hated`. |
| `item` | string or array | none | One or more item ids. |
| `items` | string or array | none | One or more item ids. |
| `tag` | string or array | none | One or more item tag ids. |
| `tags` | string or array | none | One or more item tag ids. |
| `professions` | string or array | any | If present, rule applies only to those professions. |
| `requires_villager_unarmed` | boolean | `false` | Requires the receiving villager to have no usable weapon in either hand. `villager_unarmed` is also accepted as an alias. |
| `requires_villager_armed` | boolean | `false` | Requires the receiving villager to have a usable weapon in either hand. `villager_armed` is also accepted as an alias. |
| `reputation_per_item` | integer | reaction default | Overrides the per-item reputation value. |
| `response_key` | string | reaction default | Dialogue message key to use when this rule matches. |
| `priority` | integer | `0` | Higher priority wins among matching rules. |

At least one item or tag selector is required.

`remove` entries only need an `id`:

```json
{
  "preferences": [
    {
      "id": "builtin.farmer.hated",
      "remove": true
    }
  ]
}
```

`response_key` keeps gift files language-neutral. Define the actual text in localized dialogue JSON with a matching message `key`:

```json
{
  "messages": [
    {
      "key": "my_pack.gift.farmer.favorite_crop",
      "text": "{gift_item}? Fresh enough for the whole village."
    }
  ]
}
```

Custom gift responses can use `{gift_item}`, `{item}`, `{gift_item_id}`, and `{item_id}`. If the key is missing for a player's locale, the villager falls back to the normal `gift_response.global.*` or `gift_response.profession.*` reaction line.

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
      "id": "my_pack.reward.farmer.gold_carrots",
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
| `id` | string | generated | Stable id used for replacement and removal. |
| `remove` | boolean | `false` | If `true`, removes the earlier reward with the same `id`. |
| `item` | string | required | Reward item id. |
| `professions` | string or array | any | Profession filter. |
| `requires_villager_unarmed` | boolean | `false` | Requires the rewarding villager to have no usable weapon in either hand. |
| `requires_villager_armed` | boolean | `false` | Requires the rewarding villager to have a usable weapon in either hand. |
| `reputation_levels` | string or array | any | Reputation tier filter. |
| `min_count` | integer | `1` | Minimum stack count, clamped to at least 1. |
| `max_count` | integer | `min_count` | Maximum stack count, clamped to at least `min_count`. |
| `weight` | integer | `10` | Weighted selection. |

If any profession-specific reward matches, generic rewards are ignored for that reward roll. Equipment-specific gift preference and reward rules only match when the gift is evaluated for a concrete villager, such as the villager receiving the gift or giving a high-reputation reward.

## Add-On Pack Strategy

To add extra gifts while keeping the built-in table, create a new file:

```text
data/villagerretaliation/gifts/my_pack_extra_gifts.json
```

To replace one built-in entry, add a rule with the same `id`. The built-in defaults use ids such as `builtin.global.liked_food_and_emeralds`, `builtin.farmer.loved`, and `builtin.reward.farmer.royalty`.

To remove one built-in entry, add a `remove` entry with the built-in id. To replace all earlier gift rules from that point in file order, set:

```json
{
  "replace": true,
  "preferences": [],
  "rewards": []
}
```

You can still replace all built-in gifts by overriding:

```text
data/villagerretaliation/gifts/default.json
```

with your own `preferences` and `rewards` arrays.
