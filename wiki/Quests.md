# Quest JSON

Quests live under:

```text
data/<namespace>/quests/<quest_id>.json
```

Each quest owns its display text, offer gates, target rules, explicit objectives, lifecycle limits, rewards, and optional tracker text. Branching offer, reminder, turn-in, and abandon conversations should live in matching [Dialogue Tree JSON](Dialogue-Trees.md) files.

Quest JSON is canonical in beta.12. Older advancement-style `criteria` / `requirements` blocks and alias fields are not loaded by the quest system.

## Canonical Shape

```json
{
  "id": "example:tales_of_a_lost_civilization",
  "display": {
    "title": "Tales of a Lost Civilization",
    "description": "Reach an Ancient City center and return with an Echo Shard."
  },
  "questline": "lost_civilization",
  "offer": {
    "professions": ["minecraft:cartographer"],
    "min_villager_level": "journeyman",
    "skills": {
      "cartography": { "min": 50 }
    }
  },
  "target": {
    "structure": "minecraft:ancient_city",
    "pieces": ["ancient_city/city_center/city_center_1"],
    "search_radius": 256,
    "discovery_radius": 128,
    "proof_item": "minecraft:echo_shard"
  },
  "objectives": [
    {
      "id": "recover_echo_shard",
      "type": "item_check",
      "item": "minecraft:echo_shard",
      "count": 1
    }
  ]
}
```

`offer.professions` is always an array, and `offer.skills` is an object keyed by villager skill id. Skill requirements use `{ "min": number }` so future skill gates can grow without changing shape.

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
| `active` | object | none | Optional active-state gate with `conditions`, `hide_when_unmet`, and `pause_progress_when_unmet`. |
| `expiration` | object | none | Optional expiry rule with `after_ticks` / `_seconds` / `_days`, `conditions`, `consume`, and notification fields. |

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

## Objectives

Use `objectives` for extra completion rules beyond the top-level `target` visit/proof pair. Objective ids are stable and can drive tracker text and placeholders.

| Field | Type | Purpose |
| --- | --- | --- |
| `id` | string | Stable objective id. |
| `type` | enum | `structure_visit`, `item_check`, or `condition`. |
| `optional` | boolean | Optional objectives can complete and show progress without blocking turn-in. |
| `structure`, `pieces`, `search_radius`, `discovery_radius` | target fields | Used by `structure_visit`. |
| `item`, `count` | item fields | Used by `item_check`. |
| `conditions` | array | Used by `condition`; all conditions must match. |
| `tracker` | object | Optional objective-specific tracker text. |

Objective tracker fields are `text`, `complete_text`, `show_progress`, `progress`, and `metadata`.

### Conditional Active State

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

### Expiration

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

Tracker text supports `{quest}`, `{quest_id}`, `{target}`, `{target_x}`, `{target_z}`, `{direction}`, `{distance}`, `{proof_item}`, `{visited_target}`, `{has_proof}`, `{active_conditions}`, `{objective}`, `{objective_id}`, `{objective_type}`, `{objective_item}`, `{objective_item_id}`, `{objective_count}`, `{objective_complete}`, `{objective_progress}`, `{objective_target_x}`, `{objective_target_y}`, and `{objective_target_z}`.

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
      "repeatable": false,
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
| `player_tick` | Periodically while the quest is active and the starting villager is loaded. |
| `proximity` | Periodically while the quest is active and the player is within `radius` blocks of the starting villager. |
| `started` | Immediately after the quest is accepted. |
| `progress` | Immediately after objective progress changes, such as proof collected or target visited. |
| `completed` | Immediately after turn-in succeeds. |
| `abandoned` | Immediately after the quest is dropped. |
| `expired` | Immediately after the quest expires. |

Actions:

Quest triggers and dialogue trees use the same shared action parser. Prefer explicit `type` values for readability, or omit `type` when a unique action field identifies the kind.

| Type | Fields | Behavior |
| --- | --- | --- |
| `notification` / `notify` | `notification` or `trigger`, optional `text` | Sends a quest-styled HUD notification through the normal notification system. |
| `tracker` / `flash_tracker` | optional `flash_tracker` | Syncs the quest tracker and optionally flashes it. |
| `forced_dialogue` / `dialogue` | `forced_dialogue` | Runs a matching forced-dialogue entry with `trigger: "quest"`. This supports proximity dialogue, reminder scenes, and future event-driven quest scenes. |
| `quest` / `quest_action` | `quest`, `quest_id`, or `id`; `action` | Starts, reminds, turns in, or abandons a quest by using the same outcomes as dialogue tree quest actions. |
| `experience` / `xp` | `amount` or `experience` | Gives player experience. |
| `reputation` / `rep` | `amount` or `reputation` | Changes direct reputation with the acting villager. |
| `gossip` / `gossip_reputation` | `amount`, `gossip`, or `gossip_reputation` | Spreads village gossip reputation. |
| `memory` / `memory_event` | `memory_event` | Records a village event memory. |
| `loot` / `loot_table` | `loot_table` | Gives loot from the referenced loot table. |

Continuous triggers default to a 30-second cooldown if no `cooldown_ticks`, `cooldown_seconds`, or `cooldown_days` value is set. Lifecycle triggers default to no cooldown.

Forced-dialogue triggers default to one-shot behavior so a villager does not keep re-opening the same authored scene after a cooldown. Set `repeatable: true` to allow repeats, or `repeatable: false` when any trigger should run only once for an active quest.
