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

## Availability And Story Locks

Use `parent` for a linear quest chain. A quest with `parent` can only be offered after the current player has completed that parent quest.

```json
{
  "id": "my_pack:chapter_two",
  "parent": "my_pack:chapter_one"
}
```

Use `offer.conditions` for branch-specific availability, world-state locks, or optional story consequences. These conditions are checked before the quest can be started:

```json
{
  "offer": {
    "professions": ["minecraft:cartographer"],
    "conditions": [
      {
        "type": "quest_stage",
        "quest": "my_pack:old_road",
        "stage": "warned_guard"
      },
      {
        "type": "quest_fact",
        "scope": "world",
        "tag": "my_pack:bridge_repaired"
      }
    ]
  }
}
```

Use `rules.active.conditions` when an already accepted quest should pause or hide until the world is right again.

Use branch locks when one path should close another path. Quests in the same `exclusive_group` lock their siblings when the configured `exclusive_on` event fires:

```json
{
  "id": "my_pack:join_the_wardens",
  "rules": {
    "exclusive_group": "my_pack:faction_choice",
    "exclusive_on": "started"
  }
}
```

Use explicit lock lists for named consequences outside the group:

```json
{
  "rules": {
    "blocks_on_completion": [
      "my_pack:warn_the_raiders",
      "my_pack:smuggle_the_relic"
    ]
  }
}
```

Locked quests are consumed with state `branch_locked` and receive the quest-scoped tag `villagerretaliation:quest_branch_locked`. Their quest fact variables include `state: "branch_locked"`, `blocked_by`, `blocked_on`, and `exclusive_group` when a group caused the lock.

Dialogue and trigger actions can also close a path directly:

```json
{
  "actions": [
    {
      "type": "quest",
      "quest": "my_pack:smuggle_the_relic",
      "action": "block"
    }
  ]
}
```

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

## Example: Coordinate Quest

Use `location_visit` when the quest should complete after the player reaches a specific coordinate or region. This check is cheap and runs with the normal quest progress tick.

```json
{
  "id": "my_pack:old_milestone",
  "display": {
    "title": "Old Milestone",
    "description": "Find the road marker beyond the river."
  },
  "objectives": [
    {
      "id": "reach_marker",
      "type": "location_visit",
      "dimension": "minecraft:overworld",
      "x": 1840,
      "y": 72,
      "z": -420,
      "radius": 12,
      "tracker": {
        "text": "Find the old milestone near {objective_target_x}, {objective_target_z}.",
        "complete_text": "You found the old milestone."
      }
    }
  ]
}
```

`pos: [x, y, z]` is also accepted instead of separate `x`, `y`, and `z` fields.

## Example: Mob Kill Quest

Use `mob_kill` for event-driven kill counters. The counter updates from the living-death event, so it does not scan nearby mobs every tick.

```json
{
  "id": "my_pack:clear_raiders",
  "display": {
    "title": "Clear The Road",
    "description": "Defeat the raiders harassing the old road."
  },
  "objectives": [
    {
      "id": "clear_raiders",
      "type": "mob_kill",
      "entities": ["#minecraft:raiders"],
      "count": 5,
      "tracker": {
        "text": "Defeat raiders: {objective_progress_count}/{objective_count}",
        "complete_text": "The road is clear."
      }
    }
  ]
}
```

Mob selectors:

| Field | Meaning |
| --- | --- |
| `entity` | One entity type id, such as `minecraft:zombie` |
| `entities` | One or more entity type ids or `#tag` selectors |
| `entity_tag` | One entity type tag id |
| `entity_tags` | One or more entity type tag ids |

Add `dimension`, `x`/`y`/`z`, and `radius` to restrict kills to a region.

## Example: Block Event Quest

Use `block_break` and `block_place` for event-driven block counters. These update from block break/place events and do not scan the world.

```json
{
  "id": "my_pack:repair_the_bridge",
  "objectives": [
    {
      "id": "place_planks",
      "type": "block_place",
      "blocks": ["#minecraft:planks"],
      "count": 8,
      "x": 120,
      "y": 64,
      "z": -30,
      "radius": 10,
      "tracker": {
        "text": "Place planks at the bridge: {objective_progress_count}/{objective_count}",
        "complete_text": "The bridge has fresh planks."
      }
    }
  ]
}
```

Block selectors:

| Field | Meaning |
| --- | --- |
| `block` | One block id, such as `minecraft:oak_planks` |
| `blocks` | One or more block ids or `#tag` selectors |
| `block_tag` | One block tag id |
| `block_tags` | One or more block tag ids |

Add `dimension`, `x`/`y`/`z`, and `radius` to restrict break/place credit to a region. Tracker placeholders include `{objective_block}` and `{objective_block_id}`.

## Example: Village Memory Event Quest

Use `memory_event` when a quest should advance from a story event already recorded by VR, such as defending a village, curing a villager, giving a loved gift, stealing from a village container, or completing another quest. These update when the memory is written; they do not poll old memories every tick.

```json
{
  "id": "my_pack:prove_your_intent",
  "display": {
    "title": "Prove Your Intent",
    "description": "Help the village before asking for guarded knowledge."
  },
  "objectives": [
    {
      "id": "defend_village",
      "type": "memory_event",
      "memory": "player_defended_village",
      "count": 1,
      "tracker": {
        "text": "Defend the village from danger.",
        "complete_text": "The village saw what you did."
      }
    }
  ],
  "triggers": [
    {
      "id": "defense_witnessed",
      "event": "progress",
      "actions": [
        { "type": "set_tag", "tag": "my_pack:village_defender" },
        { "type": "set_stage", "value": "trusted_by_watch" }
      ]
    }
  ]
}
```

Memory selectors:

| Field | Meaning |
| --- | --- |
| `memory` | One memory tag, such as `player_defended_village` or `villagerretaliation:player_defended_village` |
| `memory_event` | Alias for `memory` |
| `memory_tag` | Alias for `memory` |
| `memories`, `memory_events`, `memory_tags` | One or more memory tags |
| `event`, `events` | Short aliases for memory event objectives |

Add `dimension`, `x`/`y`/`z`, and `radius` to restrict credit to memories written in a region. Tracker placeholders include `{objective_memory}` and `{objective_memory_id}`.

Useful built-in memory tags include `baby_born`, `iron_golem_defeated_mob`, `thunderstorm`, `sandstorm`, `snowstorm`, `village_fire`, `night_attack`, `raid`, `villager_death`, `player_killed_villager`, `villager_attacked`, `baby_villager_attacked`, `player_attacked_villager`, `player_defended_village`, `player_defended_raid`, `player_cured_villager`, `golem_created`, `golem_killed`, `nearby_hostile_mob`, `reputation_changed`, `player_gave_loved_gift`, `player_gave_liked_gift`, `player_gave_neutral_gift`, `player_gave_disliked_gift`, `player_gave_hated_gift`, `player_container_theft`, `player_completed_quest`, and `villager_retaliation_started`.

Memory objectives only count new player-associated memory writes while the quest is active. Use quest fact tags or variables for permanent history gates, such as unlocking a branch after an event that may have happened earlier.

## Example: Fact Objective

Use `fact` when a quest should wait for durable story state written by a dialogue choice, quest trigger, forced dialogue, or another quest. Fact objectives use the same vocabulary as `quest_fact` conditions: `scope`, `tag`, `key`, `variable`, `counter`, `stage`, `value`, `min`, and `max`.

```json
{
  "id": "my_pack:choose_a_route",
  "display": {
    "title": "Choose A Route",
    "description": "Choose how the village will move supplies."
  },
  "objectives": [
    {
      "id": "choose_route",
      "type": "fact",
      "scope": "quest",
      "stage": ["river_route", "ridge_route"],
      "tracker": {
        "text": "Choose a supply route.",
        "complete_text": "The route is chosen: {objective_fact_value}."
      }
    }
  ]
}
```

Common fact objective shapes:

```json
{ "type": "fact", "scope": "player", "tag": "my_pack:village_defender" }
```

```json
{ "type": "fact", "scope": "world", "counter": "old_roads.relics_found", "min": 3 }
```

```json
{ "type": "quest_stage", "quest": "my_pack:trial_path", "stage": "accused_the_guard" }
```

Fact objective fields:

| Field | Meaning |
| --- | --- |
| `scope` | `player`, `world`, `quest`, `villager`, or `village`; defaults to `quest` inside quest files |
| `quest` / `quest_id` | Quest id to read when `scope` is `quest`; defaults to the current quest |
| `tag`, `tags`, `fact_tag`, `quest_tag` | Durable fact tags to require |
| `key`, `variable`, `fact` | Variable key to require |
| `counter` | Counter key to test with `min` / `max` |
| `stage`, `stages` | Shorthand for variable key `stage` with one or more accepted values |
| `value`, `values` | Accepted variable values |
| `min`, `max` | Counter bounds |

Tracker placeholders include `{objective_fact}`, `{objective_fact_id}`, `{objective_fact_key}`, `{objective_fact_value}`, and `{objective_fact_scope}`.

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

## Example: Quest Facts For Branches

Quest facts are durable tags, variables, and counters that dialogue, triggers, and later quests can read with `quest_fact` conditions. Use them for branch choices, story locks, and persistent consequences.

This trigger writes a quest-scoped tag when the quest starts:

```json
{
  "id": "my_pack:old_road",
  "triggers": [
    {
      "id": "remember_offer_seen",
      "event": "started",
      "actions": [
        {
          "type": "set_tag",
          "tag": "my_pack:old_road_started"
        }
      ]
    }
  ]
}
```

This dialogue-tree branch stores a chosen route:

```json
{
  "actions": [
    {
      "type": "set_variable",
      "scope": "quest",
      "quest": "my_pack:old_road",
      "key": "route",
      "value": "river"
    }
  ]
}
```

Quest stages are shorthand for the quest-scoped variable `stage`. Use them for Skyrim-style branch gates inside a questline:

```json
{
  "actions": [
    {
      "type": "set_stage",
      "quest": "my_pack:old_road",
      "stage": "warned_guard"
    }
  ]
}
```

Later dialogue or quest availability can require that branch:

```json
{
  "conditions": [
    {
      "type": "quest_fact",
      "scope": "quest",
      "quest": "my_pack:old_road",
      "key": "route",
      "value": "river"
    }
  ]
}
```

The same branch can be written with the stage alias:

```json
{
  "conditions": [
    {
      "type": "quest_stage",
      "quest": "my_pack:old_road",
      "stage": "warned_guard"
    }
  ]
}
```

Use `scope: "world"` for shared save-wide consequences, `scope: "player"` for player story flags, `scope: "village"` for local outcomes, and `scope: "villager"` for villager-specific secrets or promises.

### Automatic Quest Facts

Every quest also writes common quest-scoped facts for the current player:

| Fact | When it is written |
| --- | --- |
| `villagerretaliation:quest_started` | The quest starts |
| `villagerretaliation:quest_completed` | The quest is turned in |
| `villagerretaliation:quest_abandoned` | The quest is abandoned |
| `villagerretaliation:quest_expired` | The quest expires |
| `villagerretaliation:quest_objective_completed` | An objective completes |

The quest-scoped variable `state` is set to `started`, `completed`, `abandoned`, or `expired`. Objective completion also sets `last_objective` and increments `objective_completed:<objective_id>`.

```json
{
  "conditions": [
    {
      "type": "quest_fact",
      "scope": "quest",
      "quest": "my_pack:old_road",
      "tag": "villagerretaliation:quest_completed"
    }
  ]
}
```

## Example: Once Per World Or Village

By default, completion limits are per player. Add `completion_scope` when a quest should be globally settled after enough completions happen in a wider scope.

```json
{
  "rules": {
    "repeatable": false,
    "max_completions": 1,
    "completion_scope": "world"
  },
  "dialogue": {
    "already_completed": [
      "That matter has already been settled."
    ]
  }
}
```

Completion scopes:

| Scope | Meaning |
| --- | --- |
| `player` | Default. Completion count is stored on that player's quest progress. |
| `world` | One shared completion count for the whole save. |
| `village` | One shared completion count for the resolved village area. |
| `villager` | One shared completion count for the issuing villager. |

Use `world` for unique story chapters, `village` for local village crises, and `villager` for personal favor chains.

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
