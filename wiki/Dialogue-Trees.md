# Dialogue Tree JSON

Dialogue trees are for authored scenes: quest offers, story beats, branching conversations, and anything that should live in one readable file instead of separate option and line pools.

Ambient chatter still belongs in `dialogue/<locale>/`. Narrative scenes should use:

```text
data/<namespace>/dialogue_trees/<locale>/<tree_id>.json
```

## Minimal Shape

```json
{
  "id": "example:lost_civilization_intro",
  "title": "Lost Civilization Intro",
  "entries": [
    {
      "id": "offer",
      "label": "Lost Civilization",
      "profession": "minecraft:cartographer",
      "request": "story",
      "order": 18,
      "conditions": [
        { "type": "quest", "quest": "example:tales_of_a_lost_civilization", "state": "available" }
      ],
      "start": "offer"
    }
  ],
  "nodes": {
    "offer": {
      "lines": [
        "I found a mark that does not belong to any road I know."
      ],
      "responses": [
        { "id": "accept", "label": "Tell me where to go.", "next": "start_quest" },
        { "id": "decline", "label": "Another time.", "next": "decline" }
      ]
    },
    "start_quest": {
      "actions": [
        {
          "type": "quest",
          "quest": "example:tales_of_a_lost_civilization",
          "action": "start",
          "lines": {
            "started": ["Travel {direction} toward {target_x}, {target_z}. Bring back {proof_item}."],
            "locate_failed": ["The map table is quiet today."]
          }
        }
      ],
      "end": true
    },
    "decline": {
      "text": "Then leave the map folded.",
      "end": true
    }
  }
}
```

## Entries

`entries` generate Talk menu options. A tree can have several entries pointing at different starting nodes, which is useful for quest offer, reminder, and turn-in states.

Common fields:

| Field | Purpose |
| --- | --- |
| `id` | Stable entry id within this tree. |
| `label` | Player-facing Talk menu label. |
| `start` / `start_node` | Node id to enter when selected. Defaults to `start`. |
| `request` | Dialogue request type used for mood/reputation behavior. Defaults to `story`. |
| `profession` / `professions` | Optional villager profession filter. |
| `conditions` | Standard dialogue conditions. |
| `show_for_adults`, `show_for_babies` | Age gates. |
| `order` | Sort order in the Talk menu. |

## Nodes And Responses

Nodes contain villager `text` or `lines`, optional `actions`, and optional player `responses`.

Responses contain a player-facing `label` and either `next`/`next_node` or `end: true`. Responses can also have their own `conditions` and `actions`.

If a node has responses, the conversation stays inside the tree and the response labels replace the normal Talk options. If a node has `end: true` or no available responses, the tree session ends and the normal Talk options return.

## Actions

Actions let dialogue mutate game state without adding new Java handlers for every feature.

Supported action types:

| Type | Fields |
| --- | --- |
| `quest` | `quest`, `action`: `start`, `remind`, `turn_in`, or `abandon`; optional status-keyed `lines`. |
| `experience` / `xp` | `amount` |
| `reputation` | `amount` |
| `gossip` / `gossip_reputation` | `amount` |
| `memory` / `village_memory` | `memory` or `tag` |
| `loot` / `give_loot` | `loot` or `loot_table` |

Quest actions return a status such as `started`, `reminder`, `completed`, `missing_target`, `missing_proof`, `abandoned`, `abandoned_cooldown`, `abandoned_forever`, `unavailable`, `already_completed`, or `locate_failed`. When an action defines `lines`, the matching status selects the spoken line.

## Placeholders

Dialogue trees support the shared dialogue placeholders plus quest placeholders returned by quest actions:

```text
{player}
{villager}
{villager_name}
{villager_possessive}
{profession}
{reputation}
{reputation_level}
{quest}
{quest_id}
{target}
{target_x}
{target_z}
{direction}
{distance}
{proof_item}
{visited_target}
{has_proof}
```
