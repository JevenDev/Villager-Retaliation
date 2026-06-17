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
  "wallet_label": "Emeralds",
  "text_color": "#55ff55"
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
| `text_color` / `wallet_text_color` / `wallet_color` / `color` | Hex or named color for the villager interaction wallet number. Defaults to `#55ff55`. |

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

### Quest Facts

Use `quest_fact` conditions for durable story flags, branch choices, and counters written by quest or dialogue actions.

```json
{
  "conditions": [
    {
      "type": "quest_fact",
      "scope": "quest",
      "quest": "my_pack:old_road",
      "tag": "my_pack:warned_the_guard"
    }
  ]
}
```

Scopes:

| Scope | Meaning |
| --- | --- |
| `player` | Stored for the current player across the world |
| `world` | Stored once for the whole save |
| `quest` | Stored for the current player and a quest id |
| `villager` | Stored on the current villager id |
| `village` | Stored on the resolved village area, or the current villager position fallback |

Variables and counters use `key` plus `value`, `min`, or `max`:

```json
{
  "conditions": [
    {
      "type": "quest_fact",
      "scope": "quest",
      "quest": "my_pack:old_road",
      "key": "route",
      "value": "river"
    },
    {
      "type": "quest_fact",
      "scope": "player",
      "counter": "raiders_defeated",
      "min": 5
    }
  ]
}
```

Quest stages are shorthand for `scope: "quest"`, `key: "stage"`, and a stage value:

```json
{
  "conditions": [
    {
      "type": "quest_stage",
      "quest": "my_pack:old_road",
      "stage": "warned_guard"
    }
  ]
}
```

Use `all_of`, `any_of`, and `not` around `quest_fact` conditions for larger branch logic.

Quest offers can use the same condition shape:

```json
{
  "parent": "my_pack:first_chapter",
  "offer": {
    "conditions": [
      {
        "type": "quest_fact",
        "scope": "world",
        "tag": "my_pack:bridge_repaired"
      }
    ]
  }
}
```

`parent` gates a quest behind a completed parent quest for the current player. `offer.conditions` gates whether the quest can be offered at all. `rules.active.conditions` controls whether an already active quest can currently progress.

Branch locks close unchosen paths:

```json
{
  "rules": {
    "exclusive_group": "my_pack:faction_choice",
    "exclusive_on": "started",
    "blocks_on_completion": ["my_pack:other_outcome"]
  }
}
```

`exclusive_group` makes sibling quests in the same group mutually exclusive. `exclusive_on` accepts `started` or `completed`. `blocks_on_start`, `blocks_on_completion`, and `blocks` explicitly consume named quests. A locked quest matches quest state `branch_locked` and gets the quest-scoped tag `villagerretaliation:quest_branch_locked` plus variables `blocked_by`, `blocked_on`, and `exclusive_group`.

## Shared Actions

Dialogue trees, quest triggers, and villager event triggers use the same `actions` shape for most state changes.

```json
{
  "actions": [
    { "type": "quest", "quest": "my_pack:old_road", "action": "start" },
    { "type": "notification", "trigger": "quest.updated", "text": "Quest updated: {quest}" }
  ]
}
```

Common action types:

| Type | Important fields |
| --- | --- |
| `quest` | `quest` or `quest_id`, `action`: `start`, `remind`, `turn_in`, or `abandon` |
| `notification` | `trigger`, `text` |
| `forced_dialogue` | `forced_dialogue` |
| `experience` | `amount` or `experience` |
| `reputation` | `amount` or `reputation` |
| `gossip` | `amount`, `gossip`, or `gossip_reputation` |
| `memory` | `memory_event` |
| `loot` | `loot_table` |
| `tracker` | `flash_tracker` |
| `set_tag` | `tag` or `set_tag`, optional `scope`, optional `quest` |
| `clear_tag` | `tag` or `clear_tag`, optional `scope`, optional `quest` |
| `set_variable` | `key` or `variable`, `value`, optional `scope`, optional `quest` |
| `set_stage` | `stage`, optional `quest`; stores quest-scoped branch state |
| `counter` | `key` or `counter`, optional `amount`, `by`, or `delta`, optional `scope`, optional `quest` |

Quest facts default to `quest` scope when the action has a quest id or is inside a quest-owned trigger. Otherwise they default to `player` scope.

```json
{
  "actions": [
    {
      "type": "set_tag",
      "scope": "quest",
      "quest": "my_pack:old_road",
      "tag": "my_pack:warned_the_guard"
    },
    {
      "type": "set_variable",
      "scope": "quest",
      "quest": "my_pack:old_road",
      "key": "route",
      "value": "river"
    },
    {
      "type": "set_stage",
      "quest": "my_pack:old_road",
      "stage": "warned_guard"
    },
    {
      "type": "counter",
      "scope": "player",
      "counter": "raiders_defeated",
      "amount": 1
    }
  ]
}
```

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
