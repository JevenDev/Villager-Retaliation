# Quest JSON

For the shared metadata, tag, and dialogue-linking rules that now apply across quests, dialogue trees, forced dialogue, and ambient dialogue, read [Dialogue And Quests](Dialogue-And-Quests.md) first.

Quests live under:

```text
data/<namespace>/quests/<quest_id>.json
data/<namespace>/quests/<questline>/<quest_id>.json
```

Each quest owns its display text, offer gates, target rules, explicit objectives, lifecycle limits, rewards, and optional tracker text. Branching offer, reminder, turn-in, and abandon conversations should live in matching [Dialogue Tree JSON](Dialogue-Trees.md) files.

For new quest/dialogue content, prefer the module layout from [Dialogue And Quests](Dialogue-And-Quests.md): put the quest under `quests/<questline>/<quest>.json` and put its branching scene under `dialogue_trees/<locale>/quests/<questline>/<quest>.json`.

Quest `links` are optional pack-author-facing metadata and validation hooks. They are parsed by the loader and checked by the validator when present, but runtime uses dialogue-tree quest actions and quest trigger actions to actually start, remind, turn in, abandon, or force quest dialogue.

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
    "dimension": "minecraft:overworld",
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

Optionally add a `metadata` block and `links` block when the quest owns dialogue surfaces and you want the quest file to document them explicitly:

```json
{
  "metadata": {
    "topic": "quests.lost_civilization",
    "questline": "lost_civilization",
    "quest": "example:tales_of_a_lost_civilization",
    "tags": [
      "content.quest",
      "dialogue.linked",
      "questline.lost_civilization"
    ]
  },
  "links": {
    "dialogue_tree": "example:tales_of_a_lost_civilization",
    "offer": "offer",
    "reminder": "reminder",
    "turn_in": "turn_in",
    "forced_dialogue": [
      "quest.lost_civilization.storm_reminder"
    ]
  }
}
```

When present, `links.offer`, `links.reminder`, and `links.turn_in` must match real `entries[].id` values in the linked dialogue tree. `links.forced_dialogue` must list real forced-dialogue entry ids, not file names.

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
| `structure`, `dimension`, `pieces`, `search_radius`, `discovery_radius` | target fields | Used by `structure_visit`. `dimension` is optional; when omitted, the quest searches the villager's current dimension first and then other server dimensions. |
| `item`, `count`, `consume` | item fields | Used by `item_check`. `consume` defaults to `true`; consumed quest items are moved into the quest villager's inventory and turn-in fails if the villager has no room. Set it to `false` when the player only needs to possess the item at turn-in. |
| `enchantment`, `enchantments` | string, object, or array | Optional `item_check` filters. Every listed enchantment must be present on the item or enchanted book. Object entries use `id`, `min_level`, and `max_level`. |
| `min_durability`, `max_durability`, `min_durability_percent`, `max_durability_percent` | integer | Optional `item_check` durability filters for damageable items. Percent values use remaining durability from `0` to `100`. |
| `custom_data` / `nbt` | object | Optional `item_check` custom data subset. The stack must contain these custom data keys and values; extra stack data is allowed. |
| `conditions` | array | Used by `condition`; all conditions must match. |
| `tracker` | object | Optional objective-specific tracker text. |

Objective tracker fields are `text`, `complete_text`, `show_progress`, `progress`, and `metadata`.

Example item check that only accepts a named custom token with Sharpness III or higher:

```json
{
  "type": "item_check",
  "item": "minecraft:diamond_sword",
  "count": 1,
  "consume": true,
  "enchantments": [
    { "id": "minecraft:sharpness", "min_level": 3 }
  ],
  "custom_data": {
    "villagerretaliation": {
      "quest_token": "larder"
    }
  }
}
```

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

## Quest Journal And Tracker

Accepted quests sync to the client quest journal and tracker UI. The default keybinds are:

```text
J - Open Quest Journal
L - Toggle Quest Tracker
```

The journal lists active quest state and detail text. The tracker uses the quest `tracker.title`, current tracker step, objective tracker text, and quest placeholders documented above. Active quest proof items can also receive quest-item highlighting in inventories, item tooltips, and dropped item labels when the server syncs them as quest-relevant items.

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

Inside a quest file, quest trigger conditions and quest actions inherit the current quest id. Add `quest` or `quest_id` only when the trigger intentionally checks or mutates a different quest.

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
| `notification` / `notify` | `notification` or `trigger`, optional `text` | Sends a quest-kind HUD notification through the normal notification system. |
| `tracker` / `flash_tracker` | optional `flash_tracker` | Syncs the quest tracker and optionally flashes it. |
| `forced_dialogue` / `dialogue` | `forced_dialogue` | Runs a matching forced-dialogue entry with `trigger: "quest"`. This supports proximity dialogue, reminder scenes, and other authored quest scenes. |
| `quest` / `quest_action` | Optional `quest`, `quest_id`, or `id` for another quest; `action` | Starts, reminds, turns in, or abandons a quest by using the same outcomes as dialogue tree quest actions. |
| `experience` / `xp` | `amount` or `experience` | Gives player experience. |
| `reputation` / `rep` | `amount` or `reputation` | Changes direct reputation with the acting villager. |
| `gossip` / `gossip_reputation` | `amount`, `gossip`, or `gossip_reputation` | Spreads village gossip reputation. |
| `memory` / `memory_event` | `memory_event` | Records a village event memory. |
| `loot` / `loot_table` | `loot_table` | Gives loot from the referenced loot table. Use a vanilla loot table/item modifier when a reward needs specific components, enchantments, or custom data. |

Continuous triggers default to a 30-second cooldown if no `cooldown_ticks`, `cooldown_seconds`, or `cooldown_days` value is set. Lifecycle triggers default to no cooldown.

Forced-dialogue triggers default to one-shot behavior so a villager does not keep re-opening the same authored scene after a cooldown. Set `repeatable: true` to allow repeats, or `repeatable: false` when any trigger should run only once for an active quest.
