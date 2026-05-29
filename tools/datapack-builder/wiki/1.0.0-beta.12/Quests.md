# Quest JSON

Quests live under:

```text
data/<namespace>/quests/<quest_id>.json
```

Each quest is one JSON file with a stable `id`, an advancement-like `criteria` block for author clarity, and explicit runtime sections for offer rules, target tracking, lifecycle rules, rewards, tracker text, and event triggers. Put authored quest conversations in [Dialogue Tree JSON](Dialogue-Trees.md), where entries can start, remind, turn in, and abandon the quest.

## Minimal Shape

```json
{
  "id": "example:tales_of_a_lost_civilization",
  "display": {
    "title": "Tales of a Lost Civilization",
    "description": "Find an Ancient City center and bring back proof."
  },
  "questline": "lost_civilization",
  "offer": {
    "profession": "minecraft:cartographer",
    "min_villager_level": "journeyman",
    "skills": [{ "skill": "cartography", "min": 50 }]
  },
  "target": {
    "structure": "minecraft:ancient_city",
    "pieces": [
      "ancient_city/city_center/city_center_1",
      "ancient_city/city_center/city_center_2",
      "ancient_city/city_center/city_center_3"
    ],
    "search_radius": 256,
    "discovery_radius": 128,
    "proof_item": "minecraft:echo_shard"
  },
  "rewards": {
    "experience": 350,
    "reputation": 18,
    "gossip_reputation": 8,
    "memory": "player_completed_quest",
    "loot": "example:quest/lost_civilization"
  }
}
```

## Dialogue Trees

Quest conversations should be authored as dialogue trees under `data/<namespace>/dialogue_trees/<locale>/`. A tree can expose several entries for different quest states:

```json
{
  "entries": [
    {
      "id": "offer",
      "label": "Lost Civilization",
      "profession": "minecraft:cartographer",
      "conditions": [
        { "type": "quest", "quest": "example:tales_of_a_lost_civilization", "state": "available" }
      ],
      "start": "offer"
    },
    {
      "id": "turn_in",
      "label": "Lost Civilization",
      "profession": "minecraft:cartographer",
      "conditions": [
        { "type": "quest", "quest": "example:tales_of_a_lost_civilization", "state": "ready" }
      ],
      "start": "turn_in"
    }
  ]
}
```

Inside tree nodes, run the quest with an action:

```json
{
  "type": "quest",
  "quest": "example:tales_of_a_lost_civilization",
  "action": "start",
  "lines": {
    "started": ["Travel {direction} toward {target_x}, {target_z}. Bring back {proof_item}."],
    "locate_failed": ["The map table is quiet today."]
  }
}
```

Supported quest actions are `start`, `remind`, `turn_in`, and `abandon`. Pair entries with quest states:

```text
available
not_started
in_progress
inactive
paused
ready
completed
expired
not_completed
```

## Conditional Active State

Use `rules.active` when an accepted quest should only behave as active while conditions match. Conditions use the same `conditions` array format as dialogue.

```json
{
  "rules": {
    "active": {
      "hide_when_unmet": false,
      "pause_progress_when_unmet": true,
      "conditions": [
        { "type": "weather", "state": "thunder" }
      ]
    }
  }
}
```

When `hide_when_unmet` is `false`, the quest can still appear in dialogue and tracker UI, but quest state conditions can target `inactive` or `paused` for "come back when it thunders again" dialogue. When it is `true`, normal `active`, `in_progress`, and `ready` quest conditions stop matching until the active conditions return.

## Expiration

Use `rules.expiration` to expire an active quest after a duration or when conditions match. Expired quests match the `expired` quest condition state.

```json
{
  "rules": {
    "expiration": {
      "after_days": 3,
      "conditions": [
        {
          "type": "not",
          "condition": { "type": "weather", "state": "thunder" }
        }
      ],
      "consume": false,
      "allow_repickup": true,
      "notification": "quest.expired",
      "text": "Quest expired: {quest}"
    }
  }
}
```

`consume: true` moves the quest to the permanent `consumed` state with an expiration reason. `allow_repickup` lets expired quests become available again if the normal start limits also allow another start.

## Quest Triggers

Use `triggers` when a quest should react to world state or quest lifecycle events without hardcoding a new Java hook each time. A trigger has an `event`, optional `conditions`, a cooldown, and one or more `actions`.

The event says when the trigger is checked. The conditions reuse the same condition objects as dialogue and dialogue trees, so authors can combine gates such as night, thunder, reputation, skill, memories, and quest state.

```json
{
  "triggers": [
    {
      "id": "storm_reminder",
      "event": "proximity",
      "radius": 10,
      "once": true,
      "cooldown_seconds": 120,
      "conditions": [
        {
          "type": "quest",
          "quest": "villagerretaliation:tales_of_a_lost_civilization",
          "state": "active"
        },
        {
          "type": "time",
          "value": "night"
        },
        {
          "type": "weather",
          "state": "thunder"
        }
      ],
      "actions": [
        {
          "type": "forced_dialogue",
          "forced_dialogue": "quest.lost_civilization.storm_reminder"
        }
      ]
    }
  ]
}
```

Events:

| Event | When it is checked |
| --- | --- |
| `player_tick` | Periodically while the quest is active and the starting villager is loaded. Aliases: `tick`, `while_active`. |
| `proximity` | Periodically while the quest is active and the player is within `radius` blocks of the starting villager. Aliases: `villager_proximity`, `near_villager`. |
| `started` | Immediately after the quest is accepted. |
| `progress` | Immediately after objective progress changes, such as proof collected or target visited. |
| `completed` | Immediately after turn-in succeeds. |
| `abandoned` | Immediately after the quest is dropped. |
| `expired` | Immediately after the quest expires. |

Actions:

| Type | Fields | Behavior |
| --- | --- | --- |
| `notification` | `notification` or `trigger`, optional `text` | Sends a quest-styled HUD notification through the normal notification system. |
| `tracker` | optional `flash_tracker` | Syncs the quest tracker and optionally flashes it. |
| `forced_dialogue` | `forced_dialogue` | Runs a matching forced-dialogue entry with `trigger: "quest"`. This supports proximity dialogue, reminder scenes, and future event-driven quest scenes. |

Continuous triggers default to a 30-second cooldown if no `cooldown_ticks`, `cooldown_seconds`, or `cooldown_days` value is set. Lifecycle triggers default to no cooldown.

Forced-dialogue triggers default to one-shot behavior so a villager does not keep re-opening the same authored scene after a cooldown. Set `repeatable: true` to allow repeats, or use `once: true` / `run_once: true` explicitly for other trigger types.

## Runtime Notes

- `target.structure` is located through Minecraft's structure search, then stored in player quest progress.
- `target.pieces` checks the actual structure piece the player stands in. Omit it when any piece of the structure should count.
- `proof_item` must be in the player's inventory at turn-in time.
- `reputation` changes the returning cartographer's relationship with the player.
- `gossip_reputation` spreads a smaller reputation change through villager gossip.
- `memory` creates a village event memory, so later dialogue can reference the completed quest with `player_completed_quest`.
- `loot` points at a normal loot table under `data/<namespace>/loot_table/`.

## Quest Dialogue Placeholders

Quest dialogue supports:

```text
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
```
