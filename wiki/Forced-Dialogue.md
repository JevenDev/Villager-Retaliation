# Forced Dialogue

Forced dialogue is for event-driven villager reactions that should interrupt the normal flow. Use it for crimes, confrontations, authored quest interruptions, and chat barks.

## Path

```text
data/<namespace>/forced_dialogue/<file>.json
```

For larger packs, split forced dialogue by event so authors can find the rule they are changing:

```text
data/my_pack/forced_dialogue/events/container_theft.json
data/my_pack/forced_dialogue/events/container_opened.json
data/my_pack/forced_dialogue/events/retaliation_started.json
data/my_pack/forced_dialogue/events/retaliation_disengaged.json
data/my_pack/forced_dialogue/quests/lost_civilization.json
```

The built-in files follow this pattern under `forced_dialogue/events/`.

## Output Modes

| Mode | Use it for |
| --- | --- |
| `forced_dialogue` | Locked scenes with player response buttons |
| `chat` | One-shot nearby villager speech without opening a conversation |

## Example: Locked Theft Scene

```json
{
  "entries": [
    {
      "id": "my_pack.container_theft.warning",
      "trigger": "container_theft",
      "output": {
        "mode": "forced_dialogue"
      },
      "line": "Hands off that {container}. I saw what you took.",
      "witness_radius": 10,
      "requires_line_of_sight": true,
      "initiate_dialogue": true,
      "options": [
        {
          "id": "apologize",
          "label": "Apologize",
          "response": "Then prove it next time before the village has to ask.",
          "reputation": 2,
          "end_conversation": true
        },
        {
          "id": "talk_back",
          "label": "Talk back",
          "response": "Wrong answer.",
          "reputation": -6,
          "aggro": true,
          "end_conversation": true
        }
      ]
    }
  ]
}
```

## Example: Payment Option

Forced-dialogue options can take items directly from the player.

```json
{
  "id": "offer_payment",
  "label": "Offer payment",
  "response": "Payment does not make it yours, but it can make things right.",
  "take_items": {
    "items": ["minecraft:emerald"],
    "count": 8,
    "destination": "villager_inventory",
    "failure_response": "Do not offer emeralds you do not have."
  },
  "end_conversation": true
}
```

`take_items` removes matching items from the player's inventory. Its default `destination` is `discard`, which is best for fees, bribes, and abstract payments. `take_stolen_items` or `return_stolen_items` returns the exact stolen stacks. Its default `destination` is `villager_inventory_then_source_container`.

Destination values:

| Value | Result |
| --- | --- |
| `discard` | Remove the items without placing them anywhere. |
| `villager_inventory` | Put the items in the speaking villager's inventory. |
| `villager_inventory_then_source_container` | Try villager inventory first, then the source container. |
| `source_container` | Put the items back into the watched container. |
| `drop_at_villager` | Drop leftovers at the villager. |
| `drop_at_container` | Drop leftovers at the watched container position. |

Use `overflow_destination` when the main destination might not fit. With `require_space: true`, the option fails if neither destination can accept the full stack. With `require_space: false`, the option can still succeed after partial placement.

## Locale-Friendly Text

Forced dialogue can keep inline English as fallback text while using datapack message keys as the translation surface. The easiest form is `message_prefix`, which generates keys from the entry and option structure:

```json
{
  "id": "my_pack.container_theft.warning",
  "message_prefix": "forced.my_pack.container_theft.warning",
  "trigger": "container_theft",
  "line": "Hands off that {container}. I saw what you took.",
  "options": [
    {
      "id": "apologize",
      "label": "Apologize",
      "response": "Then prove it next time before the village has to ask."
    }
  ]
}
```

That example looks up these message keys first, then falls back to the inline text if a key is missing:

| Text | Generated key |
| --- | --- |
| entry line | `forced.my_pack.container_theft.warning.line` |
| option label | `forced.my_pack.container_theft.warning.option.apologize.label` |
| option response | `forced.my_pack.container_theft.warning.option.apologize.response` |
| leave label | `forced.my_pack.container_theft.warning.leave.label` |
| payment success | `forced.my_pack.container_theft.warning.option.apologize.take_items.success` |
| stolen-item return failure | `forced.my_pack.container_theft.warning.option.apologize.take_stolen_items.failure` |

Supported key fields:

| Place | Key fields |
| --- | --- |
| entry line | `line_key`, `line_keys`, `text_key`, `text_keys` |
| option label | `label_key` |
| option response | `response_key`, `response_keys` |
| payment or stolen-item success | `success_response_key`, `success_response_keys` |
| payment or stolen-item failure | `failure_response_key`, `failure_response_keys` |

When a key is present, the keyed message is used first and the inline text is only a fallback. Explicit key fields win over `message_prefix`. Use explicit keys when several filtered options should share one label but use separate response prefixes.

## Example: Chat Bark

```json
{
  "entries": [
    {
      "id": "my_pack.retaliation.chat",
      "trigger": "retaliation_started",
      "output": {
        "mode": "chat",
        "radius": 18
      },
      "lines": [
        "You picked the wrong village.",
        "Run while you still remember how."
      ],
      "chance": 0.5
    }
  ]
}
```

## When To Use Forced Dialogue Instead Of Normal Dialogue

Use forced dialogue when:

- the villager should react immediately to an event
- the player must answer before returning to normal interaction
- you need event-specific buttons such as apology, payment, or escalation
- you want a reactive bark tied to a trigger instead of a Talk menu request

Retaliation chat barks can also use `low_guts_pursuit_abandoned`, `low_guts_counter_completed`, `retaliation_target_escaped`, and `retaliation_search_expired`. These triggers communicate why a villager deliberately stopped fighting without opening a locked conversation.

Use normal [Dialogue](Dialogue.md) when the player chooses to ask something on purpose.

## Held Item Proximity

Use `trigger: "player_item_proximity"` for lines that fire when a nearby player is holding, wearing, or carrying a matching item. Add `output.mode: "chat"` for a bark instead of a locked conversation.

```json
{
  "id": "my_pack.trade_cost_pitch",
  "trigger": "player_item_proximity",
  "output": {
    "mode": "chat"
  },
  "line": "I could use {trade_cost}. I have {trade_result_stack} ready if you are interested.",
  "witness_radius": 4,
  "chance": 0.35,
  "requires_held_trade_item": true,
  "min_trade_level": 2,
  "max_trade_level": 4
}
```

`requires_held_trade_item: true` makes the entry match only adult, non-nitwit villagers the player can currently trade with, and only when the player's main hand or off hand matches one of that villager's active trade cost items. It uses vanilla's `ShowTradesToPlayer` item check, extended to also consider the off hand, so counts and components do not need to match. Out-of-stock offers are ignored. You can also use `requires_trade_item` or `requires_matching_trade_item` as aliases.

Trade-cost entries can use `{held_item}`, `{trade_cost}`, `{trade_cost_count}`, `{trade_result}`, `{trade_result_stack}`, and `{trade_offer_index}`. Trade-level filters use villager levels 1 through 5. `min_villager_trade_level` and `max_villager_trade_level` are accepted aliases for `min_trade_level` and `max_trade_level`.

## Replacing Or Removing Built-Ins

Use top-level `replace: true` when a pack wants to replace the built-in forced-dialogue set instead of adding to it:

```json
{ "replace": true }
```

When any forced-dialogue resource in the reload uses `replace: true`, VR skips its built-in forced-dialogue resources before add-on content is applied. This makes total conversion packs predictable even when the add-on namespace sorts before `villagerretaliation`.

Use `remove: true` with an `id` to remove one definition:

```json
{
  "id": "player_item_proximity_diamond_sword_warning",
  "remove": true
}
```

Inside an `entries` array, `remove: true` removes that entry. Without an explicit `id`, the fallback id is inferred from the file path and entry index.
