# Dialogue Trees

Dialogue trees define standalone Talk-menu conversations with several authored responses. They remain a global dialogue system, but they are not companions for beta.13 quest bundles.

Quest offer, reminder, turn-in, response, and branch structure always stays in the owning bundle's `quest.json`. A dialogue tree may inspect quest state like any other global conversation, but it cannot own or override a quest's structural dialogue.

## Paths

```text
data/<namespace>/dialogue_trees/<locale>/<tree>.json
```

Do not place beta.13 quest-owned trees under a `quests/` convention or reference them with `external_scene`. Those old patterns are diagnosed as unsupported quest layout.

## Minimal Standalone Tree

```json
{
  "id": "my_pack:village_history",
  "display": {
    "title": "Village History",
    "description": "A short lore conversation."
  },
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
        {
          "id": "road",
          "label": "What about the old road?",
          "next": "road"
        },
        {
          "id": "leave",
          "label": "Thanks.",
          "end": true
        }
      ]
    },
    "road": {
      "lines": ["The old road remembers more travelers than we do."],
      "end": true
    }
  }
}
```

## What Goes Where

| Part | Purpose |
| --- | --- |
| `entries` | Talk-menu buttons that open the tree |
| `nodes` | Villager lines, player responses, and actions |
| `responses` | Buttons shown inside the standalone scene |
| `actions` | State changes such as giving XP, writing facts, or triggering another global system |

Trees, entries, nodes, and responses accept the shared `conditions` array. They may query a quest by stable ID without becoming part of that quest's owner transaction:

```json
{
  "conditions": [
    {
      "type": "quest",
      "quest": "my_pack:road_ledger",
      "state": "completed"
    }
  ]
}
```

Node `lines` and response text accept the rich variant objects documented in [Dialogue](Dialogue.md#rich-text-variants). Ordered variants retain priority, chance, weight, conditions, usage policy, and authored order.

## Replacing Or Removing Trees

At the top of a dialogue-tree file:

```json
{ "replace": true }
```

puts the standalone dialogue-tree loader in replacement mode. VR skips built-in tree resources, then loads add-on tree files normally. In non-default locales, replacement mode also clears inherited fallback trees before applying that locale's add-on trees.

```json
{
  "id": "villagerretaliation:village_history",
  "remove": true
}
```

removes one global tree by stable ID. If `id` is omitted, the legacy loader infers it from the file path; new content should declare the ID explicitly.

## Quest Boundary

Do not use `external_scenes`, `external`, `external_scene`, or `external_entry` in a beta.13 quest. Keep structural dialogue in `quest.json`, localized wording in the bundle's `locales/*.json`, and persistent runtime scenes in the bundle's `scenes/*.json`.

Forced dialogue is another separate global system for locked event-driven interruptions. Referencing a stable forced-dialogue ID from a quest action is legal, but it does not move quest dialogue ownership out of the bundle.

Use dialogue trees for standalone lore, services, or global conversations that need several responses. Use [Quests](Quests.md) for quest state and structural quest dialogue, and normal [Dialogue](Dialogue.md) when one option and one reply are enough.
