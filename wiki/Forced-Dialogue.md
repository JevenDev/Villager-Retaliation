# Forced Dialogue JSON

Forced dialogue JSON controls event-driven conversation moments that can interrupt the player with a locked list of choices. Use it for scenes like a villager catching the player stealing from a village chest, warning them, and deciding whether the encounter ends peacefully or turns into aggro.

## Paths

Forced dialogue files must be in the `villagerretaliation` namespace:

```text
data/villagerretaliation/forced_dialogue/default.json
data/villagerretaliation/forced_dialogue/my_pack_events.json
```

Use a unique file name for addon entries. A datapack file at `data/villagerretaliation/forced_dialogue/default.json` replaces the mod's built-in default file at the Minecraft resource layer, so only use that exact path when you intentionally want a full-file override.

Forced dialogue text is server-side datapack text. Button labels and villager responses in forced dialogue entries are not resource-pack language keys.

## Top-Level Shape

A forced dialogue file can be a single entry:

```json
{
  "id": "my_pack.theft_warning",
  "trigger": "container_theft",
  "line": "Stop right there."
}
```

or an `entries` array:

```json
{
  "entries": [
    {
      "id": "my_pack.theft_warning",
      "trigger": "container_theft",
      "line": "Stop right there."
    }
  ]
}
```

## Entry Fields

| Field | Type | Default | Purpose |
| --- | --- | --- | --- |
| `id` | string | generated from file path | Stable id for replacement by later-loading files. |
| `trigger` | enum | required | Event that can start this forced dialogue. |
| `event` | enum | none | Alias for `trigger`. |
| `line` | string | required | Villager line shown when the event fires. |
| `priority` | integer | `0` | Lower values win when multiple entries match the same trigger. |
| `witness_radius` | number | `12.0` | Search radius for a witnessing villager. |
| `requires_line_of_sight` | boolean | `true` | Requires the witness to see the player and event block. |
| `initiate_dialogue` | boolean | `true` | Opens the locked interaction screen when true; otherwise only says `line`. |
| `aggro_immediately` | boolean | `false` | Makes the witness attack immediately after the event line. |
| `reputation` | integer | `0` | Reputation change applied to the witnessing villager when the event is caught. |
| `loot_table` | string | none | Optional single loot table id this entry can match. |
| `loot_tables` | array | none | Optional loot table ids this entry can match. If omitted, the entry can match any watched container. |
| `options` | array | generated Leave option | Choices shown in the forced dialogue screen. |

When multiple entries match, lower `priority` wins. If priority is tied, an entry with matching `loot_table` or `loot_tables` wins over a generic entry.

## Option Fields

| Field | Type | Default | Purpose |
| --- | --- | --- | --- |
| `id` | string | required | Choice id. Must be unique within the entry. |
| `label` | string | required | Button text shown to the player. |
| `response` | string | none | Villager response after the player chooses this option. |
| `reputation` | integer | `0` | Reputation change applied after this option. |
| `aggro` | boolean | `false` | Makes the villager attack after this option. |
| `end_conversation` | boolean | `true` | Closes the forced dialogue after this option. |
| `order` | integer | option index | Sort order in the locked option list. |
| `reputation_level` | string or array | any | Alias for `reputation_levels`. |
| `reputation_levels` | string or array | any | Shows this option only for the player's current reputation tier with the witnessing villager. |
| `min_reputation` | integer | none | Minimum exact reputation value with the witnessing villager. |
| `max_reputation` | integer | none | Maximum exact reputation value with the witnessing villager. |
| `take_items` | object | none | Removes a configured payment from the player's inventory before the option succeeds. |

Use reputation filters to change the choices available for the same event. For example, a trusted player can receive an `accept_warning` option while a hostile player only sees a higher-cost `take_items` payment or an aggro response.

### `take_items`

Forced dialogue options can take items from the player before applying the option's normal response, reputation, aggro, and end-conversation behavior.

```json
{
  "id": "offer_payment",
  "label": "Offer payment",
  "response": "That will help replace what you disturbed.",
  "take_items": {
    "items": ["minecraft:emerald"],
    "count": 8,
    "destination": "villager_inventory",
    "overflow_destination": "drop_at_villager",
    "success_response": "Eight emeralds is enough for me to believe you mean it.",
    "failure_response": "You do not have eight emeralds to offer.",
    "failure_reputation": -2,
    "failure_end_conversation": false
  },
  "reputation": 2,
  "end_conversation": true
}
```

`take_items` accepts `item` or `items`, plus `tag` or `tags`. Tags can also be written with a `#` prefix inside `items`. `count` is the total number removed across matching stacks, so `128` removes two full stacks when enough items exist. The removal checks the player's inventory and offhand, and transferred stacks keep their item components.

`destination` controls where removed items go:

| Value | Behavior |
| --- | --- |
| `discard` | Removes the items from the player. This is the default. |
| `villager_inventory` | Moves the items into the witnessing villager's inventory. |
| `source_container` | Moves the items into the container that started the forced dialogue. |
| `drop_at_villager` | Drops the items at the witnessing villager. |
| `drop_at_container` | Drops the items at the source container. |

When the destination is an inventory or container, `require_space` defaults to `true`, so the option fails unless the full payment can fit. Set `overflow_destination` to a drop or discard destination to allow overflow while keeping the payment successful.

If the player does not have enough matching items, the normal option response and reputation do not apply. Instead, `failure_response`, `failure_reputation`, `failure_end_conversation`, and `failure_aggro` control what happens. Leaving `failure_end_conversation` false keeps the forced dialogue open so the player can choose another response.

## Triggers

### `container_theft`

Fires when a player opens a watched container and closes it with fewer items than it had when opened.

### `container_opened`

Fires when a player opens a watched container. This trigger is used when the server config's container forced-dialogue trigger is set to `OPENING`.

Watched containers:

```text
chests
barrels
shulker boxes
```

The built-in events require a villager witness with line of sight to the player and the container block. If no adult villager can witness the event, no forced dialogue starts.

Server config controls whether watched containers trigger on actual theft or on opening. By default, Villager Retaliation watches `OPENING` and `WORLD_GENERATED_ONLY`, so the built-in village chest confrontation fires when a player opens a generated village chest before its loot table unpacks. Servers can switch back to theft-only behavior or broaden the scope to all watched containers.

Generated-container detection checks for an unresolved loot table through Minecraft's `RandomizableContainer` interface, so modded generated containers can participate when they expose loot tables the same way vanilla generated containers do.

The built-in `default.json` includes village-specific entries for vanilla village chest loot tables, plus a lower-priority generic theft fallback for packs or configs that still want broad theft detection.

Forced dialogue entries can optionally filter by generated container loot table:

```json
{
  "id": "examplepack.armorer_chest_opened",
  "trigger": "container_opened",
  "loot_tables": ["minecraft:chests/village/village_armorer"],
  "line": "That chest belongs to the armorer."
}
```

## Placeholders

Forced dialogue `line` and option `response` text can use:

```text
{villager}
{player}
{container}
{count}
{item}
{loot_table}
{payment_count}
{payment_items}
{x}
{y}
{z}
```

`{container}` is the block display name, `{count}` is the number of removed items for `container_theft`, `{loot_table}` is the matched generated loot table id when one exists, `{payment_count}` and `{payment_items}` describe a `take_items` option, and `{x}`, `{y}`, `{z}` are the container position.

## Example

```json
{
  "entries": [
    {
      "id": "examplepack.witnessed_chest_theft",
      "trigger": "container_theft",
      "priority": 0,
      "witness_radius": 12.0,
      "requires_line_of_sight": true,
      "initiate_dialogue": true,
      "aggro_immediately": false,
      "reputation": -8,
      "line": "Stop right there. That {container} is not yours to empty.",
      "options": [
        {
          "id": "apologize",
          "label": "Apologize",
          "response": "Words are easy after the lid closes. Put your hands to better use.",
          "reputation": 2,
          "aggro": false,
          "end_conversation": true,
          "order": 0
        },
        {
          "id": "deny",
          "label": "Deny it",
          "response": "I watched you take from it. Do not make me repeat myself.",
          "reputation": -4,
          "aggro": true,
          "end_conversation": true,
          "order": 1
        },
        {
          "id": "threaten",
          "label": "Threaten them",
          "response": "Then we are past talking.",
          "reputation": -8,
          "aggro": true,
          "end_conversation": true,
          "order": 2
        }
      ]
    }
  ]
}
```

## Behavior Notes

Forced dialogue opens the normal Villager Retaliation interaction screen in a locked mode. The player cannot navigate to Talk, Trade, Gift, Inventory, Recruit, Family, or Relationships from that moment; only the forced event options are available.

If `aggro_immediately` is true, the villager says the event line and attacks without opening the dialogue screen. If `initiate_dialogue` is false and `aggro_immediately` is false, the villager only says the event line.
