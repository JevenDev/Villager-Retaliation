# Dialogue Trees

Dialogue trees are for authored scenes with branching responses. In current quest module v2 content, trees are optional extracted scenes. A simple playable quest can keep offer, reminder, turn-in, responses, transitions, and actions inside `data/<namespace>/quests/...json`.

Use dialogue trees when a conversation should stay inside its own mini-flow, when it is too large for the quest module, when translators need a separate scene file, or when a datapack should replace only the scene without replacing quest objectives.

## Paths

```text
data/<namespace>/dialogue_trees/<locale>/<tree>.json
data/<namespace>/dialogue_trees/<locale>/quests/<module>/<quest>.json
```

Use the `quests/` path for quest-owned extracted scenes. The quest JSON decides whether the quest has a `questline`, `metadata.parent`, or only `group.*` tags.

## Minimal Tree

```json
{
  "id": "my_pack:road_ledger",
  "display": {
    "title": "Road Ledger",
    "description": "A small branching request scene."
  },
  "entries": [
    {
      "id": "offer",
      "label": "Road Ledger",
      "request": "question",
      "conditions": [
        { "type": "quest", "state": "available" }
      ],
      "start": "offer"
    }
  ],
  "nodes": {
    "offer": {
      "lines": [
        "I lost a ledger on the old road. If you find it, bring it back."
      ],
      "responses": [
        { "id": "accept", "label": "I can look for it.", "next": "start_quest" },
        { "id": "decline", "label": "Another time.", "next": "decline" }
      ]
    },
    "start_quest": {
      "actions": [
        {
          "type": "quest",
          "action": "start",
          "lines": {
            "started": [
              "Good. Search the road and return the ledger if you find it."
            ]
          }
        }
      ],
      "end": true
    },
    "decline": {
      "text": "Then the road keeps its paper a little longer.",
      "end": true
    }
  }
}
```

## Referencing A Tree From Quest Module V2

In the quest module:

```json
{
  "external_scenes": ["my_pack:quests/old_roads/road_ledger"],
  "stages": [
    {
      "id": "start",
      "objectives": [],
      "dialogue": {
        "offer": {
          "label": "Road Ledger",
          "request": "question",
          "external_scene": {
            "tree": "my_pack:quests/old_roads/road_ledger",
            "entry": "offer"
          }
        }
      }
    }
  ]
}
```

The external tree still owns its `entries`, `nodes`, and branch actions. The quest module still owns provider filters, objectives, rewards, stage transitions, and save compatibility.

## What Goes Where

| Part | Purpose |
| --- | --- |
| `entries` | Talk menu buttons that open the tree |
| `nodes` | Villager lines, player responses, and actions |
| `responses` | Buttons shown inside the scene |
| `actions` | State changes such as starting a quest, giving XP, writing facts, or forcing another scene |

## Conditions, Metadata, And Rich Text

Trees, entries, nodes, and responses accept the shared `conditions` array. Tree metadata is inherited by its menu entries, while response metadata is carried into the selected option:

```json
{
  "metadata": {
    "topic": "old_roads",
    "routing_tags": ["route.story"],
    "anti_repeat_groups": ["rotation.old_roads"],
    "quest": "my_pack:road_ledger"
  },
  "conditions": [
    { "type": "dimension", "dimension": "minecraft:overworld" }
  ]
}
```

Node `lines` and response `lines` can use the rich variant objects documented in [Dialogue](Dialogue.md#rich-text-variants). A parent node or response can provide `usage` defaults, and each variant can override its stable `id`, `text` or `text_key`, `priority`, `chance`, `weight`, `conditions`, metadata, or usage policy:

```json
{
  "lines": [
    {
      "id": "first_visit",
      "text": "The old road still remembers its first travelers.",
      "conditions": [
        { "type": "quest", "quest": "my_pack:road_ledger", "state": "available" }
      ],
      "usage": {
        "once": true,
        "scope": "player"
      }
    },
    {
      "id": "return_visit",
      "text": "Back to the road again?",
      "weight": 3
    }
  ]
}
```

Tree entry and response buttons sort by `priority` first, then `order`, then stable ID. Rich text variants use the shared priority, chance, and weight arbitration rules.


## Replacing Or Removing Built-Ins

At the top of a dialogue-tree file:


```json
{ "replace": true }
```

puts the dialogue-tree loader in replacement mode. VR skips built-in tree resources, then loads add-on tree files normally. In non-default locales, replacement mode also clears inherited fallback trees before applying that locale's add-on trees. Use this for total conversation overhauls.

```json
{
  "id": "villagerretaliation:bread_delivery",
  "remove": true
}
```

removes one tree by `id`. If `id` is omitted, the tree id is inferred from the file path. This remains useful for legacy v1 quest scenes and for v2 modules that deliberately reference extracted trees.

## Example: Non-Quest Branch

Trees are not just for quests. This is a simple lore branch:

```json
{
  "id": "my_pack:village_history",
  "entries": [
    {
      "id": "history",
      "label": "Ask About The Village",
      "request": "story",
      "start": "history"
    }
  ],
  "nodes": {
    "history": {
      "lines": [
        "This place was smaller once. Safer too, depending on who you ask."
      ],
      "responses": [
        { "id": "leave", "label": "Thanks.", "end": true }
      ]
    }
  }
}
```

## Use Trees When

- the player needs several responses in a row
- a quest scene is too large or too shared to keep inline
- you want scene-only overrides without replacing quest objective logic
- you want actions attached directly to branches
- the conversation should not fall back to the normal Talk menu until it ends

Use [Quests](Quests.md) for one-file quest modules. Use normal [Dialogue](Dialogue.md) when one option and one reply are enough.
