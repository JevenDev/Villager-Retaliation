# Quests

Quest JSON defines who can offer a quest, what the player must do, how the quest repeats, and what happens on completion.

## Paths

```text
data/<namespace>/quests/<quest>.json
data/<namespace>/quests/<questline>/<quest>.json
```

For new packs, prefer the questline folder layout.

## Minimal Fetch Quest

```json
{
  "id": "my_pack:bread_delivery",
  "display": {
    "title": "Bread Delivery",
    "description": "Bring 16 bread to the village stores."
  },
  "questline": "village_supply",
  "offer": {
    "professions": ["minecraft:farmer"],
    "min_villager_level": "novice"
  },
  "objectives": [
    {
      "id": "bring_bread",
      "type": "item_check",
      "item": "minecraft:bread",
      "count": 16
    }
  ],
  "rewards": {
    "experience": 60,
    "reputation": 5,
    "gossip_reputation": 2
  },
  "rules": {
    "repeatable": true,
    "completion_cooldown_days": 1
  }
}
```

## Main Parts

| Section | Purpose |
| --- | --- |
| `display` | Title and description shown to players |
| `offer` | Which villagers can offer it |
| `target` | Optional world target such as a structure search |
| `objectives` | What the player must actually complete |
| `rewards` | XP, reputation, gossip, loot, memory events |
| `rules` | Repeat limits, abandonment, cooldowns, locking |
| `tracker` | Optional custom quest tracker text |
| `triggers` | Event-based reactions while the quest exists |

## Example: Structure Quest

```json
{
  "id": "my_pack:echo_shard_run",
  "display": {
    "title": "Echo Shard Run",
    "description": "Reach the Ancient City and return with an Echo Shard."
  },
  "target": {
    "structure": "minecraft:ancient_city",
    "dimension": "minecraft:overworld",
    "proof_item": "minecraft:echo_shard"
  },
  "objectives": [
    {
      "id": "recover_shard",
      "type": "item_check",
      "item": "minecraft:echo_shard",
      "count": 1
    }
  ]
}
```

## Example: Tracker Text

```json
{
  "tracker": {
    "title": "Bread Delivery",
    "steps": {
      "proof": {
        "text": "Bring 16 bread back to the quest giver.",
        "show_progress": true,
        "progress": 0.7
      }
    }
  }
}
```

## Example: Triggered Quest Follow-Up

```json
{
  "triggers": [
    {
      "id": "storm_warning",
      "event": "proximity",
      "radius": 10,
      "cooldown_seconds": 120,
      "conditions": [
        { "type": "quest", "state": "active" },
        { "type": "weather", "state": "thunder" }
      ],
      "actions": [
        {
          "type": "forced_dialogue",
          "forced_dialogue": "my_pack.quest.road_ledger.storm_warning"
        }
      ]
    }
  ]
}
```

## Best Practice

Pair every quest with a [Dialogue Tree](Dialogue-Trees.md) for the offer, reminder, and turn-in scene. Keep the quest file focused on state and the tree focused on the conversation.
