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
| `options` | array | generated Leave option | Choices shown in the forced dialogue screen. |

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

## Triggers

### `container_theft`

Fires when a player opens a watched container and closes it with fewer items than it had when opened.

Watched containers:

```text
chests
barrels
shulker boxes
```

The built-in event requires a villager witness with line of sight to the player and the container block. If no adult villager can witness the theft, no forced dialogue starts.

## Placeholders

Forced dialogue `line` and option `response` text can use:

```text
{villager}
{player}
{container}
{count}
{item}
{x}
{y}
{z}
```

`{container}` is the block display name, `{count}` is the number of removed items, and `{x}`, `{y}`, `{z}` are the container position.

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
