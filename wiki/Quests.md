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

## Locale-Friendly Text

Inline English can stay in the quest as a fallback, but every player-facing quest string can point at a datapack message key:

```json
{
  "display": {
    "title": "Bread Delivery",
    "title_key": "quest.village_supply.bread_delivery.title",
    "description": "Bring 16 bread to the village stores.",
    "description_key": "quest.village_supply.bread_delivery.description"
  },
  "dialogue": {
    "start": ["Bring me 16 bread."],
    "start_key": "quest.village_supply.bread_delivery.dialogue.start"
  }
}
```

Put the keyed text in `data/<namespace>/dialogue/<locale>/.../messages/*.json`:

```json
{
  "messages": [
    {
      "id": "quest.village_supply.bread_delivery.title",
      "key": "quest.village_supply.bread_delivery.title",
      "lines": ["Bread Delivery"]
    }
  ]
}
```

Supported quest text keys:

| Place | Key fields |
| --- | --- |
| `display` | `title_key`, `description_key` |
| objective `tracker` | `text_key`, `complete_text_key` |
| top-level `tracker` | `title_key` |
| tracker `steps.*` | `text_key` |
| `dialogue` stages | `<stage>_key`, `<stage>_keys` |
| object-form dialogue stage | `text_key`, `text_keys` |
| `rules.expiration` | `text_key` or `notification_text_key` |

Quest dialogue stages are `start`, `reminder`, `turn_in`, `already_completed`, `unavailable`, `inactive`, `missing_target`, `missing_proof`, and `locate_failed`.

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
    "title_key": "quest.village_supply.bread_delivery.tracker.title",
    "steps": {
      "proof": {
        "text": "Bring 16 bread back to the quest giver.",
        "text_key": "quest.village_supply.bread_delivery.tracker.proof.text",
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

## Replacing Or Removing Built-Ins

At the top of any quest file:

```json
{ "replace": true }
```

puts the quest loader in replacement mode. VR skips built-in quest resources, then loads add-on quest files normally. A control-only replace file is valid; it disables the built-ins without registering a placeholder quest. Put your replacement quests in the same file or any other quest file in your datapack.

```json
{
  "id": "villagerretaliation:bread_delivery",
  "remove": true
}
```

removes one quest by `id`. If `id` is omitted, the id is inferred from the file path.

## Best Practice

Pair every quest with a [Dialogue Tree](Dialogue-Trees.md) for the offer, reminder, and turn-in scene. Keep the quest file focused on state, and put translatable wording behind message keys so translators do not have to edit objective logic.
