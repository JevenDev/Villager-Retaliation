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
  "display": {
    "title": "Lost Civilization Intro",
    "description": "Cartographer quest offer and follow-up scene."
  },
  "metadata": {
    "topic": "ancient_city",
    "tags": ["quest", "exploration"],
    "questline": "lost_civilization",
    "quest": "tales_of_a_lost_civilization"
  },
  "entries": [
    {
      "id": "offer",
      "label": "Lost Civilization",
      "professions": ["minecraft:cartographer"],
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
| `start` | Node id to enter when selected. Defaults to `start`. |
| `request` | Dialogue request type used for mood/reputation behavior. Defaults to `story`. |
| `professions` | Optional villager profession filter. |
| `conditions` | Standard dialogue conditions. |
| `show_for_adults`, `show_for_babies` | Age gates. |
| `order` | Sort order in the Talk menu. |

## Nodes And Responses

Nodes contain villager `text` or `lines`, optional `actions`, and optional player `responses`.

Responses contain a player-facing `label` and either `next` or `end: true`. Responses can also have their own `conditions`, `metadata`, and `actions`.

If a node has responses, the conversation stays inside the tree and the response labels replace the normal Talk options. If a node has `end: true` or no available responses, the tree session ends and the normal Talk options return.

For active quest menu nodes, keep destructive choices near the bottom: use an `Abandon quest` response just above the final `Never mind` response so datapack authors can insert extra quest-specific choices between the ordinary quest details and the exit.

## Actions

Actions let dialogue mutate game state without adding new Java handlers for every feature.

Dialogue trees and quest triggers share the same action parser. Use explicit `type` values for clarity, or omit `type` when a unique action field identifies the kind. For example, `{ "experience": 25 }`, `{ "forced_dialogue": "quest.scene" }`, `{ "quest_id": "example:quest", "action": "accept" }`, and `{ "trigger": "quest.updated", "text": "Quest updated: {quest}" }` are all valid shorthand forms.

Supported action types:

| Type | Fields |
| --- | --- |
| `quest` / `quest_action` | `quest`, `quest_id`, or `id`; `action`: `start`/`accept`/`begin`, `remind`/`details`, `turn_in`/`complete`/`claim`, or `abandon`/`drop`/`cancel`; optional status-keyed `lines`. |
| `experience` / `xp` | `amount` or `experience` |
| `reputation` / `rep` | `amount` or `reputation` |
| `gossip` / `gossip_reputation` | `amount`, `gossip`, or `gossip_reputation` |
| `memory` / `memory_event` | `memory_event` |
| `loot` / `loot_table` | `loot_table` |
| `notification` / `notify` | `notification` or `trigger`, optional `text` |
| `tracker` / `flash_tracker` | `flash_tracker` |
| `forced_dialogue` / `dialogue` | `forced_dialogue` |

Quest actions return a status such as `started`, `reminder`, `inactive`, `completed`, `missing_target`, `missing_proof`, `abandoned`, `abandoned_cooldown`, `abandoned_forever`, `unavailable`, `already_completed`, or `locate_failed`. When an action defines `lines`, the matching status selects the spoken line.

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
{active_conditions}
{objective}
{objective_id}
{objective_type}
{objective_item}
{objective_item_id}
{objective_count}
{objective_complete}
{objective_progress}
{objective_target_x}
{objective_target_y}
{objective_target_z}
```
