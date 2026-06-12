# JSON Reference

This page covers the shared authoring rules used across Villager Retaliation JSON.

## Stable Ids

Give entries a stable `id` whenever you may want to:

- override that entry later
- translate it in another locale
- remove it with a follow-up datapack
- read cleaner debug output

Example:

```json
{
  "id": "my_pack.greeting.rainy_day",
  "request": "greeting",
  "text": "Rain makes even short roads feel longer."
}
```

## `text` vs `lines`

Use `text` for one output. Use `lines` when the same rule should randomly say one of several variations.

```json
{
  "id": "my_pack.line.variants",
  "request": "question",
  "lines": [
    "Quiet roads are usually planning something.",
    "Roads are safer when someone else has already checked them."
  ],
  "weight": 10
}
```

## `replace` and `remove`

Top-level `replace: true` clears the previously loaded pool for that system before the file is applied.

```json
{
  "replace": true,
  "notifications": []
}
```

Some systems also support entry-level removal by `id`.

```json
{
  "preferences": [
    {
      "id": "villagerretaliation.default.bad_gift",
      "remove": true
    }
  ]
}
```

Builder structures also support removal by structure id:

```json
{
  "entries": [
    {
      "structure": "minecraft:village/plains/houses/plains_small_house_1",
      "remove": true
    }
  ]
}
```

## Arrays and Single Values

Many fields accept one value or several values.

```json
"professions": ["minecraft:farmer", "minecraft:fletcher"]
```

When in doubt, prefer arrays. They are clearer and easier to extend later.

## Reputation Filters

These fields show up in several systems:

| Field | Meaning |
| --- | --- |
| `reputation_levels` | One or more named tiers such as `trusted` or `hostile` |
| `min_reputation` | Lowest numeric or named reputation allowed |
| `max_reputation` | Highest numeric or named reputation allowed |

Named tiers commonly used in docs:

```text
royalty
revered
respected
trusted
neutral
suspicious
hostile
despised
feared
```

Example:

```json
{
  "id": "my_pack.notification.low_trust",
  "trigger": "trade.refused",
  "text": "Not today.",
  "reputation_levels": ["hostile", "despised", "feared"]
}
```

## Item and Tag Selectors

Use item ids for exact matches:

```json
"items": ["minecraft:emerald"]
```

Use tags with `#` when any item in the tag should count:

```json
"items": ["#minecraft:flowers"]
```

The same pattern is used in gifts, pacification, and some forced-dialogue payment selectors.

## Currency

Villager Retaliation's hire payments, payment boxes, wallet deposits, wallet UI, default currency drops, and emerald-default skill-trade costs use:

```text
data/villagerretaliation/currency/default.json
```

Built-in default:

```json
{
  "item": "minecraft:emerald",
  "name": "emerald",
  "plural_name": "emeralds",
  "wallet_label": "Emeralds"
}
```

Fields:

| Field | Meaning |
| --- | --- |
| `item` | Primary currency item. Refunds, wallet deposits, drops, and emerald-default skill trade costs use this item. |
| `accepted_items` / `items` | Extra item ids accepted as equivalent payment. |
| `accepted_tags` / `tags` | Item tags accepted as equivalent payment. Prefixing with `#` is optional here. |
| `name` | Singular display name used in notices. |
| `plural_name` | Plural display name used in notices. |
| `wallet_label` | Label shown in the villager interaction wallet line. |

Payment-box recipes and client-side "hold currency" checks also use the `villagerretaliation:currency` item tag:

```text
data/villagerretaliation/tags/item/currency.json
```

Keep that tag aligned with your currency item so crafting recipes, payment boxes, and client hints all agree.

## Conditions

`conditions` are the preferred way to express complex logic in newer beta.12 content. A condition array usually means all listed conditions must pass.

```json
{
  "id": "my_pack.line.night_storm",
  "request": "village_event_report",
  "conditions": [
    { "type": "time", "value": "night" },
    { "type": "weather", "state": "thunder" }
  ],
  "text": "Storm nights make bad fences and worse promises."
}
```

Use conditions when the older one-off helper flags start to pile up.

## Weights and Priority

- `weight` changes the random odds between otherwise equivalent matches.
- `priority` is a stronger sort step used on normal dialogue lines before weighted selection.

Example:

```json
{
  "id": "my_pack.line.high_priority_warning",
  "request": "question",
  "priority": 20,
  "weight": 1,
  "text": "You should deal with the raid first."
}
```

Use `priority` when one line should win reliably. Use `weight` when several matched lines should all stay in rotation.

## Message Keys

When several rules should share the same localized text, move the wording into a keyed message and reference it with `text_key`.

```json
{
  "id": "my_pack.line.shared_warning",
  "request": "question",
  "text_key": "my_pack.warning.road_closed"
}
```

```json
{
  "id": "my_pack.message.road_closed",
  "key": "my_pack.warning.road_closed",
  "text": "The road is closed until morning."
}
```

## Canonical Naming

Prefer the current documented field names even if compatibility aliases still work. For new content, that usually means:

- `trigger` instead of older event aliases
- `world_text_kind` for notifications
- `request` on dialogue options and lines
- `conditions` for complex logic

## Troubleshooting Example

If a file appears valid but nothing happens, strip it back to a bare minimum:

```json
{
  "id": "my_pack.debug",
  "request": "question",
  "text": "Debug line."
}
```

If that works, the problem is in the filters, not the path or loader.
