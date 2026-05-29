# Quest JSON

Quests live under:

```text
data/<namespace>/quests/<quest_id>.json
```

Each quest owns its objective rules, lifecycle limits, rewards, and optional tracker text. Branching offer, reminder, turn-in, and abandon conversations should live in matching [Dialogue Tree JSON](Dialogue-Trees.md) files.

## Lifecycle Rules

Use `rules` to control whether a player can retake or farm a quest.

| Field | Type | Default | Purpose |
| --- | --- | --- | --- |
| `repeatable` | boolean | `false` | Allows a completed quest to be accepted again when limits and cooldowns allow it. |
| `locked_to_villager` | boolean | `true` | Active quest reminder, turn-in, and abandon actions must happen with the villager who started it. |
| `cross_villager_compatible` | boolean | `false` | If `false`, prior starts/completions/abandons are tied to the original villager and cannot be immediately bypassed with another villager. |
| `max_starts` | integer | `1`, or unlimited when repeatable | Maximum times this player may start the quest. Use `0` for unlimited starts. |
| `max_completions` | integer | `1`, or unlimited when repeatable | Maximum times this player may complete the quest. Use `0` for unlimited completions. |
| `completion_cooldown_ticks` / `_seconds` / `_days` | duration | `0` | Wait after completion before a repeatable quest can be accepted again. |
| `abandonment` | enum | `allow_repickup` | `remove_forever`, `allow_repickup`, or `cooldown`. |
| `abandonment_cooldown_ticks` / `_seconds` / `_days` | duration | `0` | Wait after abandoning before the quest can be accepted again. |
| `consume_on_completion` | boolean | `false` | Marks the quest permanently consumed after completion. |
| `consume_on_abandonment` | boolean | `false` | Marks the quest permanently consumed after abandonment. |

Example:

```json
{
  "rules": {
    "repeatable": false,
    "locked_to_villager": true,
    "cross_villager_compatible": false,
    "max_starts": 0,
    "max_completions": 1,
    "abandonment": "cooldown",
    "abandonment_cooldown_days": 1,
    "consume_on_completion": true
  }
}
```

## Tracker Text

Use `tracker` to define the middle-left quest tracker copy. The runtime chooses a current step key such as `travel`, `proof`, or `return`, then resolves placeholders in that step.

```json
{
  "tracker": {
    "title": "Tales of a Lost Civilization",
    "metadata": {
      "source": "Cartographer commission"
    },
    "steps": {
      "travel": {
        "text": "Reach the Ancient City center near {target_x}, {target_z}.",
        "show_progress": true,
        "progress": 0.25,
        "metadata": {
          "hint": "{distance} blocks {direction}"
        }
      },
      "proof": {
        "text": "Recover {proof_item} as proof of the journey.",
        "show_progress": true,
        "progress": 0.66
      },
      "return": {
        "text": "Return to the cartographer with {proof_item}.",
        "show_progress": true,
        "progress": 1.0
      }
    }
  }
}
```

Tracker text supports `{quest}`, `{quest_id}`, `{target}`, `{target_x}`, `{target_z}`, `{direction}`, `{distance}`, `{proof_item}`, `{visited_target}`, and `{has_proof}`.

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

Actions:

| Type | Fields | Behavior |
| --- | --- | --- |
| `notification` | `notification` or `trigger`, optional `text` | Sends a quest-styled HUD notification through the normal notification system. |
| `tracker` | optional `flash_tracker` | Syncs the quest tracker and optionally flashes it. |
| `forced_dialogue` | `forced_dialogue` | Runs a matching forced-dialogue entry with `trigger: "quest"`. This supports proximity dialogue, reminder scenes, and future event-driven quest scenes. |

Continuous triggers default to a 30-second cooldown if no `cooldown_ticks`, `cooldown_seconds`, or `cooldown_days` value is set. Lifecycle triggers default to no cooldown.
