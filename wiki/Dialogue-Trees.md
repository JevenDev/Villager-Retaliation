# Dialogue Trees

Dialogue trees are for authored scenes with branching responses. Use them when a conversation should stay inside its own mini-flow instead of pulling one reply from a normal dialogue pool.

## Paths

```text
data/<namespace>/dialogue_trees/<locale>/<tree>.json
data/<namespace>/dialogue_trees/<locale>/quests/<module>/<quest>.json
```

Use the `quests/` path for quest-owned scenes. The module folder keeps related files together; the quest JSON decides whether the quest has a `questline` or only a `group.*` tag.

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

## What Goes Where

| Part | Purpose |
| --- | --- |
| `entries` | The Talk menu buttons that open the tree |
| `nodes` | Villager lines, player responses, and actions |
| `responses` | Buttons shown inside the scene |
| `actions` | State changes such as starting a quest, giving XP, or forcing another scene |

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

removes one tree by `id`. If `id` is omitted, the tree id is inferred from the file path. This is useful when you want to remove a built-in quest scene and provide your own normal dialogue or forced dialogue instead.

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
- a quest offer, reminder, or turn-in should feel authored
- you want actions attached directly to branches
- the conversation should not fall back to the normal Talk menu until it ends

Use normal [Dialogue](Dialogue.md) when one option and one reply are enough.
