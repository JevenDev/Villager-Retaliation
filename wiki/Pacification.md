# Pacification

Pacification files choose which held items can calm a villager or wandering trader that is hostile toward the player. A successful payment consumes the required count and clears that hostility.

These files choose the payment. The spoken success, failure, and refusal lines belong in normal dialogue under a `pacify/` folder.

## Path

Pacification data is fixed to the `villagerretaliation` namespace:

```text
data/villagerretaliation/pacification/<file>.json
```

## Simple Example

```json
{
  "payments": [
    {
      "items": ["minecraft:emerald"],
      "count": 8,
      "priority": 10
    }
  ]
}
```

A hostile villager can accept eight emeralds. The player right-clicks while holding the payment in the used hand or off hand.

## Modded Currency Example

```json
{
  "payments": [
    {
      "item": "numismatic-overhaul:gold_coin",
      "count": 12,
      "name": "gold coin",
      "plural_name": "gold coins",
      "priority": 20
    }
  ]
}
```

`name` and `plural_name` control the item wording used by pacification dialogue. They do not rename the item itself.

## Item Tag Example

```json
{
  "payments": [
    {
      "tags": ["#c:ingots/iron"],
      "count": 4
    }
  ]
}
```

A leading `#` is optional in `tag` and `tags`.

## Profession-Specific Example

```json
{
  "payments": [
    {
      "professions": ["minecraft:toolsmith"],
      "items": ["minecraft:iron_ingot"],
      "min_count": 2,
      "max_count": 4,
      "priority": 30
    }
  ]
}
```

The required count is chosen inclusively from 2 through 4 for each offer.

## How A Rule Is Chosen

1. The held item, profession, and optional armed state must match.
2. If any matching rule names a profession, general rules are ignored.
3. The highest `priority` wins.
4. If priorities tie, the rule loaded first wins.

This means a profession-specific payment can safely override a general payment without giving it a higher priority.

## Main Fields

| Field | Default | Meaning |
| --- | --- | --- |
| `item` or `items` | None | One or more exact item IDs. |
| `tag` or `tags` | None | One or more item tags. |
| `count` | None | Exact payment count. |
| `min_count` | `1` | Lowest randomized payment count when `count` is absent. |
| `max_count` | `min_count` | Highest randomized payment count. |
| `professions` | Any | Restrict the rule to one or more villager professions. |
| `priority` | `0` | Higher values win between otherwise eligible rules. |
| `name` | Item display name | Singular wording for dialogue. |
| `plural_name` | Singular wording | Plural wording for dialogue. |
| `requires_villager_armed` | `false` | Match only villagers with a usable weapon. |
| `requires_villager_unarmed` | `false` | Match only villagers without a usable weapon. |

Payment counts are clamped from 1 through 64.

Pacification entries do not use explicit IDs, `replace`, or `remove`. All files are combined. To replace a lower-priority file, override the same namespace and file path.

## Reputation Can Still Refuse Payment

A valid payment does not guarantee success. The server can block pacification when the player's reputation is too low. In that case, the item is not consumed and the matching pacify refusal line is shown.

## Dialogue Example

Place a line under:

```text
data/my_pack/dialogue/en_us/my_pack/pacify/00_toolsmith.json
```

```json
{
  "id": "my_pack.pacify.toolsmith",
  "professions": ["minecraft:toolsmith"],
  "text": "Fine. Leave the {payment_cost} {payment_items} and walk away."
}
```
