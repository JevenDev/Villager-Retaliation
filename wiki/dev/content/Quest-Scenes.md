# Persistent Quest Scenes

Persistent quest scenes coordinate long quest sequences that must resume after a save, reload, disconnect, or unloaded chunk. A scene can move named actors, wait, show dialogue, start a controlled fight, branch, update a quest, and clean up what it created.

Use an inline quest scene for a short conversation or a few immediate actions. Use a persistent scene when the sequence waits on time, movement, players, actors, or combat.

Scene files use:

```text
data/<namespace>/quests/<questline>/<quest-slug>/scenes/<scene>.json
data/<namespace>/quests/_shared/scenes/<scene>.json
```

Encounter files use:

```text
data/<namespace>/quests/<questline>/<quest-slug>/encounters/<encounter>.json
data/<namespace>/quests/_shared/encounters/<encounter>.json
```


Use the private path when only one quest references a definition. Private companions may not be referenced across quest bundles. Put genuinely reusable definitions in `_shared`; every scene and encounter keeps its explicit stable `id` regardless of its file location.
## Terms Used On This Page

| Term | Plain meaning |
| --- | --- |
| Scene | A saved sequence of named steps. |
| Step | One unit of work, such as waiting, moving an actor, or starting a fight. |
| Actor | A named player, villager, entity, or position used by scene steps. |
| Binding | The saved link between an actor name and its actual player, entity, or position. |
| Encounter | A controlled group of enemies or allies created and tracked by a scene. |
| Operation ID | An author-chosen name that prevents the same quest action from starting the same scene twice. |
| Receipt | A saved record that an action was prepared or completed. It helps prevent duplicate rewards and messages after reload. |
| Cleanup | Removing or restoring entities, encounters, and temporary blocks owned by the scene. |
| Reconcile | Compare saved scene state with the loaded world and safely continue or report a problem. |

## Smallest Complete Scene

This scene waits one second, then completes:

```json
{
  "schema": "villagerretaliation:scene/v1",
  "id": "my_pack:short_pause",
  "definition_version": 1,
  "ownership": "quest_instance",
  "entry_step": "pause",
  "actors": [],
  "steps": [
    {
      "id": "pause",
      "type": "villagerretaliation:wait_ticks",
      "data": {
        "ticks": 20
      },
      "next": "done"
    },
    {
      "id": "done",
      "type": "villagerretaliation:scene_complete"
    }
  ]
}
```

Start it from a quest action:

```json
{
  "type": "start_scene",
  "scene": "my_pack:short_pause",
  "operation_id": "short_pause_v1",
  "wait_for_result": true
}
```

Keep `operation_id` stable after release. Set `wait_for_result` to `true` when the quest must pause until the scene succeeds. Use `false` when the quest can continue immediately.

## Starting a scene

Use the registered safe quest action `start_scene`:

```json
{
  "type": "start_scene",
  "scene": "example:gate_ambush",
  "operation_id": "gate_ambush_v1",
  "wait_for_result": true
}
```

The runtime combines the operation ID with the owning player, party, quest run, or world. Repeating that combination returns the existing scene instead of creating another one. Scene work continues over server ticks, even when the calling quest waits for its result.

For solo quests, the run identity includes the player UUID, quest ID, and persisted start count. Shared party quests use one durable shared instance ID before stage actions or `STARTED` triggers run. `world` ownership is an explicit global singleton for the dimension/scene/quest/operation combination.

When `wait_for_result` is true, the enclosing dialogue, stage, or lifecycle sequence is persisted and resumes only after scene success. Failure and cancellation remain distinct and do not run success actions. Scene `action_batch` cannot suspend and rejects a waiting scene launch during compilation.

## Scene format

Every step ID is authored and persistence-critical. Keep IDs stable when editing a live pack.

```json
{
  "schema": "villagerretaliation:scene/v1",
  "id": "example:gate_ambush",
  "definition_version": 1,
  "metadata": { "title": "Ambush at the Gate" },
  "ownership": "party",
  "entry_step": "move_captain",
  "timeout_ticks": 2400,
  "failure_policy": "block_for_repair",
  "cancellation_policy": "cancel_scene",
  "cleanup_policy": "all_owned",
  "actors": [],
  "steps": []
}
```

Ownership is `player`, `party`, `quest_instance`, or `world`. Failure/cancellation policies are `fail_scene`, `cancel_scene`, `block_for_repair`, and `run_failure_step`. Cleanup is `none`, `owned_entities`, `encounters`, `all_owned`, or `preserve_world`.

`quest_transition` uses one typed target: `target_stage` (or `target: "stage"` plus `target_stage`), `target: "complete"`, `target: "fail"`, or `target: "abandon"`. Mixed targets are compile errors.

Overall timeouts wake at the earlier of the current step wake and the absolute scene deadline, including while blocked. Terminal state remains visible until cleanup reaches `COMPLETE`. Missing definitions show a durable cleanup diagnostic and bounded retry time.

The compiler rejects missing references, duplicate actor or step IDs, missing capabilities, unknown types/templates, unreachable paths, invalid failure paths, and immediate unbounded cycles. Datapack reload compares the canonical definition hash and stable step IDs. Compatible edits continue. Incompatible edits leave the readable instance blocked for repair.

## Actors and replacement

An actor declaration has an `alias`, registered `type`, required `capabilities`, `required`, `binding_source`, optional `binding`, replacement/missing/death policies, optional string `filters`, and optional `timeout_ticks`.

Built-in actor types are:

| Type | Typical capabilities |
| --- | --- |
| `villagerretaliation:player` | live entity, living, dialogue target |
| `villagerretaliation:villager` | live entity, living, navigation, dialogue |
| `villagerretaliation:living_entity` | live entity and living |
| `villagerretaliation:hostile_encounter_group` | persistent encounter membership |
| `villagerretaliation:position` | stable dimension and block position |

Binding sources are `owner_player`, `party_member`, `quest_provider`, `uuid`, `marker`, `encounter`, `owned_spawn`, and `unbound`. Replacement policies are `fixed`, `operator_rebindable`, `compatible_replacement`, `respawn_if_owned`, and `optional`. Missing policies are `block`, `fail`, `skip`, and `wait_until_timeout`. Death policies are `fail`, `block`, `apply_missing_policy`, `respawn_if_owned`, and `continue_with_snapshot`.

Bindings persist UUID/target identity, source, last dimension and position, display snapshot, generation, live/snapshot state, and full replacement history. A fixed narrative actor is never proximity-replaced. Provider actors reuse quest-provider identity. A quest-provider rebind updates scene actors only when they explicitly use `compatible_replacement`, and appends both binding history and an audit entry. Use `/vr admin scene rebind <scene-uuid> <alias> <entity>` for an `operator_rebindable` repair.

## Built-in steps

Each step has `id`, `type`, optional `actors`, a `data` object, `next`, named `transitions`, and optional `failure_step`.

| Step | Important data |
| --- | --- |
| `wait_ticks` | `ticks`. Persists an absolute wake time |
| `wait_condition` | registered `conditions`, `timeout_ticks`, `poll_ticks` |
| `move_actor` | `actor`, `target_actor` or `dimension`/`x`/`y`/`z`, `speed`, `arrival_distance`, `timeout_ticks`, `path_failure_policy`, explicit `allow_teleport` |
| `face_actor` / `face_position` | source `actor` and target actor or position |
| `dialogue` | `text`, speaker aliases in `actors`, `offline_policy` (`wait`, `fail`, or `skip`), `offline_poll_ticks`. One delivery receipt per participant |
| `action_batch` | allowlisted `actions`, each with a stable `id`. Arbitrary commands are rejected |
| `quest_transition` | safe quest action fields such as `target_stage`, completion, or failure |
| `scene_branch` | ordered `branches` containing registered `conditions` and a transition name, plus `default_transition`. The chosen name is persisted |
| `scene_complete` / `scene_fail` | durable terminal result |
| `start_encounter` | `template`, anchor actor or coordinates, optional `offset_x`/`offset_y`/`offset_z`, optional `surface_anchor`, and persisted difficulty inputs |
| `wait_encounter` / `cancel_encounter` / `cleanup_encounter` | `encounter_step` naming the start step. Omitted only when the scene owns exactly one encounter |

Movement never force-loads a chunk. It waits for the actor/destination chunk, resumes navigation when available, and only teleports when both `path_failure_policy: "teleport"` and `allow_teleport: true` are authored.

Encounter offsets are applied to an actor or coordinate anchor before that anchor is persisted. Set `surface_anchor: true` to replace the resulting Y coordinate with the motion-blocking surface height. This is useful for portable quests that need a fixed destination some distance from a dynamically located villager without hard-coding world coordinates.

## Encounters

```json
{
  "schema": "villagerretaliation:encounter/v1",
  "id": "example:gate_ambush",
  "version": 1,
  "controller": "villagerretaliation:controlled",
  "members": [
    { "entity": "minecraft:zombie", "count": 3 },
    {
      "entity": "minecraft:pillager",
      "count": 1,
      "custom_name": {"key": "example.encounter.gate_ambush.member.gate_captain.custom_name"},
      "name_visible": true,
      "glowing": true,
      "persistent": true,
      "health": 40,
      "movement_speed": 0.35,
      "attack_damage": 8,
      "armor": 10,
      "knockback_resistance": 0.3,
      "boss": true,
      "boss_bar_color": "purple",
      "boss_bar_overlay": "notched_10",
      "equipment": {
        "mainhand": {
          "item": "minecraft:crossbow",
          "enchantments": { "minecraft:quick_charge": 2 },
          "drop_chance": 0.05
        },
        "head": { "item": "minecraft:iron_helmet" }
      }
    }
  ],
  "spawn_mode": "group",
  "spawn_points": [
    { "id": "west_gate", "marker": "gate", "offset_x": -8, "weight": 2 },
    { "id": "east_gate", "x": 120, "y": 64, "z": -32, "dimension": "minecraft:overworld" }
  ],
  "spawn_selection": "weighted",
  "extra_per_player": 1,
  "max_party_size": 4,
  "placement_attempts": 16,
  "spawn_radius": 8,
  "area": {
    "radius": 32,
    "vertical_radius": 16,
    "leave_behavior": "warn",
    "leave_timeout_ticks": 200,
    "mob_behavior": "return"
  },
  "respawn_policy": "missing_if_loaded",
  "cleanup_policy": "remove_survivors",
  "completion_condition": "all_defeated"
}
```

Templates are allowlists, not command containers. Party-size and difficulty inputs are captured when the encounter starts. Owned entities carry durable encounter identity. Reload reconciles UUIDs and tags before bounded safe-placement attempts. Unrelated nearby mobs never count. Cleanup removes, retains, or releases surviving owned mobs according to the template and scene policy.

The optional `spawn_points` array supplies 1-64 named positions. Each point has a stable `id` and exactly one source: `actor`, `marker`, or complete `x`/`y`/`z` coordinates. `actor` and `marker` both name an actor alias declared by the scene. The two spellings let a template communicate whether it expects a live/snapshotted actor or a position actor bound from a marker. Actor and marker sources may add bounded `offset_x`, `offset_y`, and `offset_z` values. Explicit coordinates may set `dimension`. Otherwise they use the encounter anchor dimension. Every point must resolve into that same dimension. Missing actors, unknown or incompatible dimensions, incomplete coordinates, duplicate IDs, empty lists, and weights outside 1-10000 reject the start with a focused diagnostic.

### Localized encounter presentation

In quest-owned bundle source, use localized references such as `{"key":"#encounter.captain_name"}` or absolute message IDs. The bundle materializer preserves those references as runtime key fields and the encounter resolves them using each participant's locale.

Localized encounter fields include member and summoned-entity `custom_name`, wave `boss_bar_title`, phase/hook/action `text`, reward and trophy `trophy_name`, and fixed-mode `location_message`. The corresponding runtime fields are `custom_name_key`, `boss_bar_title_key`, `text_key`, `trophy_name_key`, and `location_message_key`.

Quest dialogue scenes likewise preserve response and node `label_key` values. For new bundle content, author localized reference objects in `quest.json` and keep the exhaustive English values in the owner's `locales/en_us.json`; the `*_key` forms are the compiled runtime representation, not a second source to maintain by hand.

`guidance.coordinate_message`, `guidance.arrival_message`, and `guidance.discovery_message` are also localized references. Placeholders such as `{location}`, `{coordinates}`, `{distance}`, and `{direction}` are substituted after locale resolution.

`spawn_selection` defaults to `random` and may be `random`, `sequential`, `weighted`, `nearest_player`, `farthest_player`, or `one_group_per_point`. Weighted selection uses each point's optional `weight` (default 1). Distance modes compare the points with online captured participants and wait when no suitable participant is online. Group selection assigns each member definition to a point in authored order. Party-scaling extras stay with the first group. Resolved absolute points, every member's selected point ID, and the sequential cursor are saved before placement, so reloads and unloaded chunks wait without rerolling. Recovery checks only the bounded anchor and authored-point neighborhoods and never force-loads chunks. Authored points cannot be combined with `spawn_mode: "near_player"`.

### Mid-fight phases

`phases` is an ordered array of up to 64 durable phase definitions. Every phase has a stable `id`, one `trigger`, and 1-32 allowlisted actions. Trigger shapes are:

| Trigger type | Required field | Fires when |
|---|---|---|
| `wave_started` | `wave` | The named authored or shorthand wave has durably started. |
| `wave_completed` | `wave` | Every enemy through the named wave has been defeated. |
| `remaining_percentage` | `percentage` (0-100) | Remaining enemies are at or below the threshold. |
| `elapsed_time` | `ticks` (1-1,728,000) | The durable time since first encounter reconciliation reaches the threshold. |
| `elite_defeated` | `member` | The referenced stable member ID has been defeated. The member must have count 1, must not receive party-scaling copies, and must be named, enhanced, or designated as a boss. |

Members only need an `id` when another encounter feature references them. IDs are unique across the encounter's waves. A phase action is `notification` or `dialogue` with a localized `text` reference, `fact` with either a namespaced `tag` or `key`/`value`, or `transition` with a target scene step. Fact scope is `player`, `quest`, or `world`. Player and quest facts apply to each captured participant, and quest scope requires a linked quest scene. Transitions are checked against the scene when `start_encounter` prepares. At most one transition may appear in a non-repeatable phase.

Phases fire once by default. Setting `repeatable: true` also requires `repeat_interval_ticks` from 1-12,000 and `max_fires` from 2-64. Repeatable phases cannot transition the scene. The encounter saves its start time, defeated member IDs, fire counts, and absolute repeat deadlines. Each phase run and action also receives a stable scene operation receipt. Idempotent facts and transitions resume safely, while participant messages reserve their receipt before delivery so a reload can never send them twice.

### Completion objectives

`completion_objectives` replaces the legacy `completion_condition` when an encounter needs more than a simple enemy clear. It contains a `mode` of `all` (the default) or `any` and 1-32 objectives with unique stable IDs. `all` completes after every objective completes and fails as soon as one objective fails. `any` completes after the first success and fails only when every objective has failed. The two completion fields are mutually exclusive.

| Objective type | Fields | Meaning |
| --- | --- | --- |
| `all_defeated` | none | Every encounter-owned enemy has been defeated. |
| `all_gone` | none | Every owned enemy is defeated or durably missing. |
| `survive_duration` | `duration_ticks` | The encounter remains active for the requested duration. |
| `protect_actor` | `actor`, `duration_ticks` | The bound scene actor survives for the duration. Its death fails the objective. |
| `prevent_entry` | `point`, `duration_ticks`, optional radii | No living encounter-owned enemy enters the named point's area for the duration. A breach fails the objective. |
| `escort_actor` | `actor`, `point`, optional radii | The live bound actor reaches the named point. The actor's death fails the objective. |
| `destroy_targets` | `actors` | Every listed bound scene actor dies. |
| `defeat_leader` | `member` | The encounter member with that stable ID is defeated. |
| `retrieve_item` | `item`, optional `count`, stack predicate | Captured participants collectively carry the matching item count. Items are inspected, not consumed. |
| `hold_areas` | `points`, `duration_ticks`, optional radii | Every named point is continuously occupied by at least one captured participant for the duration. Leaving any area resets the timer. |

Durations are 1-1,728,000 ticks. Horizontal `radius` and `vertical_radius` default to 4 and are bounded to 1-64. Point references use resolved `spawn_points`. Actor references are checked against the owning scene at encounter preparation, item IDs are checked against the item registry, and leader IDs must name an authored member. Runtime evaluation uses only captured participants, bound actor UUIDs, resolved points, and encounter-owned entity UUIDs. It never performs an unbounded world scan.

`retrieve_item` accepts `components`, `custom_data` (or `nbt`), and `durability` beside `item` and `count`. Only stacks satisfying the full predicate count. See [Item stack predicates](JSON-Reference.md#item-stack-predicates).

```json
{
"completion_objectives": {
  "mode": "all",
  "objectives": [
    { "id": "hold_gate", "type": "prevent_entry", "point": "west_gate", "duration_ticks": 600, "radius": 5 },
    { "id": "stop_captain", "type": "defeat_leader", "member": "raider_captain" }
  ]
}
}
```

Completed and failed objective IDs, continuous-hold timestamps, destroyed actor aliases, and the custom-completion flag are saved with the encounter. The quest tracker reports custom-objective progress, and scene inspection includes the completed, failed, and active-timer sets.

### Friendly participants

The optional `allies` array declares 1-32 controlled friendly definitions, capped at 64 resulting entities. Each ally has a stable `id` and exactly one source. `entity` creates 1-16 living entities using the same safe equipment, presentation, and combat-attribute allowlists as hostile members. `actor` captures one live scene actor by UUID and rejects entity-only fields such as count, equipment, or attributes.

```json
{
"allies": [
  {
    "id": "village_guard",
    "entity": "minecraft:iron_golem",
    "revivable": true,
    "revive_delay_ticks": 100,
    "replacement_policy": "missing_if_loaded",
    "cleanup_policy": "preserve",
    "affects_completion": true
  },
  {
    "id": "watch_captain",
    "actor": "watch_captain",
    "invulnerable": true,
    "cleanup_policy": "preserve"
  }
]
}
```

`required_survival` fails the encounter when the ally dies or is confirmed missing in a loaded chunk. It is mutually exclusive with `revivable`, which recreates the ally after `revive_delay_ticks` (default 100, maximum 12,000). `replacement_policy` is `never` by default or `missing_if_loaded`. Replacement never treats an unloaded chunk as proof of loss. Bound allies retain their captured entity type for revival or replacement without silently changing the owning scene's actor binding.

`invulnerable` is applied only while the encounter owns the ally. Preservation restores the entity's prior invulnerability value. `cleanup_policy` is `remove` or `preserve`, independent of hostile cleanup. Entity-defined allies default to removal, while bound scene actors safely default to preservation. `affects_completion` makes victory wait while that ally has a recoverable death or missing/replacement state and fails clearly when recovery is impossible. Allies are never added to hostile kill counts. Enemy and ally UUIDs are stored in separate ledgers.

Loaded ally mobs and encounter-owned hostile mobs receive direct, encounter-local targets. Same-side targets are cleared, but no scoreboard team, global targeting rule, nearby unrelated entity, or participant team membership is changed. Ally identities include definition/index keys, entity UUID and type, last loaded location, generation, recovery deadline, source kind, cleanup policy, and invulnerability restoration state.

### Failure and retry policies

The optional `failure` object controls participant and protected-actor death without embedding commands:

```json
{
"failure": {
  "on_player_death": "reset_wave",
  "on_protected_actor_death": "branch_scene",
  "branch_step": "failed",
  "retry_delay_ticks": 200,
  "max_attempts": 3,
  "retain_defeated": false
}
}
```

`on_player_death` applies to captured participants. `on_protected_actor_death` applies to actors referenced by `protect_actor` objectives and bound allies with `required_survival`. Each action is one of:

| Action | Behavior |
| --- | --- |
| `fail` | Fails immediately. This is the default for both triggers. |
| `reset_wave` | Waits for the retry deadline, retires the current wave's non-retained owned mobs, and respawns that wave. |
| `restart_encounter` | Waits, retires non-retained hostile progress from the whole encounter, and reconciles again from the first remaining wave. |
| `pause` | Pauses until the retry deadline, then resumes the same entities and progress. |
| `branch_scene` | Records a scene-transition receipt, chooses `branch_step`, and terminates the failed encounter. |

`retry_delay_ticks` defaults to 200 and is bounded to 0-12,000. `max_attempts` includes the initial attempt, defaults to 3, and is bounded to 1-16. Once exhausted, a retry action becomes a normal failure. `retain_defeated` keeps defeated UUID credits while retiring living owned mobs. Otherwise the affected scope's defeat progress is cleared. `branch_step` is required exactly when either trigger uses `branch_scene` and is validated against the owning scene before start.

The encounter saves its attempt count, absolute retry deadline, pending action, cause, and protected actor alias. Retry removal increments the durable spawn generation: an unloaded retired mob that later returns is discarded before it can rejoin the fight, while tracked hostiles from an earlier timer wave remain valid. Wave hook IDs, phase fire counts, and scene operation receipts are never cleared, so retries cannot replay dialogue, notifications, facts, or transitions. Objective state is reevaluated for the new attempt, and cleanup remains idempotent.

### Deterministic encounter variants

An encounter resource may be a bounded selector instead of defining `members` or `waves`:

```json
{
  "schema": "villagerretaliation:encounter/v1",
  "id": "my_pack:roadblock_variants",
  "variants": [
    { "id": "zombie_roadblock", "weight": 3, "template": "my_pack:zombie_roadblock" },
    { "id": "skeleton_ambush", "weight": 2, "template": "my_pack:skeleton_ambush" }
  ]
}
```

Selector resources may contain only `schema`, `id`, optional `version` and `controller`, and `variants`. A `start_encounter` step may author the same `variants` array directly instead of `template` or `encounter_template`. Arrays contain 1-32 entries. IDs are stable and unique, weights are integers from 1-10,000, and templates are namespaced encounter IDs. Every referenced template must exist. Selectors may reference other selectors, but reload validation rejects direct or indirect recursion and chains deeper than 32.

Selection uses a deterministic seed derived from the durable scene ID and encounter operation ID. The start step records the seed, selected variant ID, source template, and final concrete template before spawning. The encounter copies those values into its own save state. Reloads and retries therefore reuse the decision and cannot reroll enemies or duplicate creation receipts.

To branch after creation, give the `start_encounter` step a transition named for a variant ID:

```json
{
  "id": "start_roadblock",
  "type": "villagerretaliation:start_encounter",
  "data": { "template": "my_pack:roadblock_variants", "x": 120, "y": 64, "z": -40 },
  "transitions": {
    "zombie_roadblock": "warn_about_zombies",
    "skeleton_ambush": "raise_shields"
  }
}
```

If no matching transition is authored, normal `next`/success routing is unchanged. Quest tracker text can use `{encounter_variant}` and `{encounter_template}`. Both resolve to empty text before an encounter exists. Scene inspection reports the source template, selected variant, resolved template, and seed.

### Environmental setup and restoration

Concrete encounter templates may add bounded, command-free environmental presentation and temporary world setup:

```json
{
"environment": {
  "cues": [
    { "id": "alarm", "type": "sound", "sound": "minecraft:block.bell.use", "volume": 1.0, "pitch": 0.8 },
    { "id": "gate_column", "type": "glowing_column", "particle": "minecraft:end_rod", "offset_y": 1, "count": 32, "height": 8 }
  ],
  "temporary_blocks": [
    { "id": "gate_light", "block": "minecraft:light", "offset_y": 3 }
  ]
}
}
```

`cues` contains at most 32 stable IDs. `sound` and `music` use registered sound IDs and are sent only to online encounter participants in the encounter dimension. `particles` and `glowing_column` use registered simple particle types. Counts are 1-128 and columns are 1-64 blocks high. Offsets are relative to the durable encounter anchor and bounded to 64 blocks per axis. Cue IDs are persisted before delivery, so reloads never replay one-time presentation.

`temporary_blocks` contains at most 64 entries and initially allowlists `barrier`, `light`, `structure_void`, and `glass`. A block may replace only a replaceable state in an already loaded chunk. Before mutation, the encounter saves the exact original state, intended placed state, dimension, position, and ownership status. Setup never force-loads chunks.

Cleanup restores a block only while the world still contains the exact state placed by that encounter. If a player or another system changes it, cleanup records the block as preserved and never overwrites the edit. Prepared, applied, restored, and preserved decisions survive reloads. Cleanup remains pending while a required chunk is unloaded and the server maintenance pass resumes it after the chunk returns. Completion, failure, cancellation, explicit cleanup, and operator cleanup all converge on the same idempotent restoration path.

This first environmental pass intentionally does not mutate global weather or world time. Those presentation types require participant-scoped client state and conflict arbitration before they can be safe alongside overlapping encounters.

### Navigation guidance

Concrete templates can guide captured participants to the durable anchor for fixed-coordinate and authored-location encounters:

```json
{
"guidance": {
  "coordinate_message": "Find {location}. It is {distance}m {direction}.",
  "arrival_message": "You reached {coordinates}.",
  "discovery_radius": 64,
  "arrival_radius": 8,
  "distance_tracker": true,
  "compass_target": true,
  "directional_particles": true,
  "hud_marker": true,
  "exact_coordinates": "after_discovery",
  "update_interval_ticks": 20
}
}
```

Guidance is participant-only and dimension-aware. `discovery_radius` is 1-512 blocks, `arrival_radius` is 1-64 and cannot exceed discovery range, and live presentation updates every 10-200 ticks. `distance_tracker` exposes a rounded block distance, `compass_target` exposes an eight-way compass bearing, `hud_marker` renders the enabled distance/bearing through Minecraft's temporary action-bar HUD, and `directional_particles` sends a short end-rod trail only to that participant. Cross-dimension guidance identifies the target dimension without calculating a misleading distance or bearing.

`exact_coordinates` is `always`, `after_discovery` (default), or `never`. Messages support `{location}`, `{coordinates}`, `{x}`, `{y}`, `{z}`, `{dimension}`, `{distance}`, and `{direction}`. Hidden coordinates resolve to `undiscovered` or `,`. Each participant's initial message, discovery, arrival, next update deadline, and cleanup acknowledgement are persisted. A participant who was offline receives their own initial guidance after returning, while one-time discovery and arrival messages never replay after reload.

Quest tracker text can use `{encounter_distance}`, `{encounter_direction}`, `{encounter_coordinates}`, `{encounter_dimension}`, `{encounter_discovered}`, and `{encounter_arrived}`. Values respect the exact-coordinate policy and are empty when a feature is disabled or the target is in another dimension. Completion, failure, cancellation, and cleanup stop updates and remove the temporary HUD marker. The legacy fixed-mode `location_message` keeps its old one-time behavior when `guidance` is omitted. It cannot be combined with `guidance.coordinate_message`.

### Rewards and mob drops

Concrete encounter templates can grant bounded rewards and control drops without commands:

```json
{
"rewards": {
  "waves": [
    { "id": "scout_supplies", "wave": "scouts", "item": "minecraft:arrow", "count": 4 }
  ],
  "phases": [
    { "id": "captain_token", "phase": "captain_falls", "item": "minecraft:iron_nugget" }
  ],
  "completion": [
    { "id": "village_medal", "item": "minecraft:emerald", "trophy_name": {"key": "example.encounter.gate_defense.reward.village_medal.trophy_name"} },
    { "id": "bonus_cache", "loot_table": "example:encounters/gate_cache" }
  ],
  "trophies": [
    { "id": "captain_badge", "member": "gate_captain", "item": "minecraft:gold_nugget", "name": "Captain Badge" }
  ],
  "drop_policy": "trophy_only"
}
}
```

`waves`, `phases`, and `completion` each contain at most 32 rewards, with at most 64 triggered rewards total. IDs are unique across every reward and trophy. A reward has exactly one registered `item` (count 1-64) or registered `loot_table`. `trophy_name` is an optional localized reference for direct item rewards. Wave and phase targets must reference authored IDs. Repeatable phase rewards use the phase fire ordinal, so each bounded fire is independently receipt-guarded.

Every eligible reward reserves a durable scene operation receipt per captured participant before delivery. Item and loot grants use the existing item/loot receipt kinds. Loot rolls use a stable encounter/reward/player seed. Reconciliation, reload, retry, and maintenance reuse the receipt and never grant it twice. Offline participants remain pending, and successfully completed encounters retain completion eligibility through cleanup so their rewards can be delivered after they reconnect. Failed or cancelled encounters do not create new pending grants. A persisted ambiguous `prepared` receipt is treated as consumed rather than risking a duplicate.

`drop_policy` defaults to `normal` and preserves vanilla drops plus authored equipment `drop_chance`. `suppress` removes all item drops. `authored_only` removes vanilla loot and deterministically rolls only authored equipment with a positive `drop_chance`. It is rejected when no such equipment exists. `trophy_only` removes vanilla and equipment drops, requires `trophies`, and drops matching trophies once per durable hostile spawn index. Trophy claims persist separately from hostile death progress, so wave reset, encounter restart, reload, and repeated drop callbacks cannot farm them. Cleanup/discard operations do not produce encounter drops.

The optional `area` is a cylinder centered on the encounter's durable anchor. `radius` is required and limited to 256 blocks. `vertical_radius` defaults to the radius and is limited to 128. `leave_behavior` is `ignore` (the backward-compatible default), `warn`, `pause`, or `fail`. A failing participant has `leave_timeout_ticks` (default 200, maximum 12000) to return. Warnings and absolute deadlines are saved, messages go only to the affected participant, offline players do not start or advance a new leave decision, and returning clears that excursion's state.

`mob_behavior` is `ignore`, `return`, or `teleport`. `return` asks loaded owned mobs to navigate back without changing unrelated entities. `teleport` waits for the persisted `mob_timeout_ticks` deadline (default 200, maximum 12000) before returning a loaded mob to the anchor. Area checks never force-load the anchor, a participant, or an owned mob's chunk. Omitting `area` preserves encounter/v1 behavior exactly.

Every mob runs its normal vanilla spawn initialization first, so mobs such as pillagers receive their usual equipment. A member's optional `equipment` object then overrides individual `mainhand`, `offhand`, `head`, `chest`, `legs`, `feet`, or `body` slots. Each slot accepts `item`, optional `count`, an `enchantments` object mapping namespaced enchantment IDs to levels, and `drop_chance` from `0.0` to `1.0`.

### Elite and boss members

Member presentation is an allowlist: localized `custom_name`, `name_visible`, `glowing`, and `persistent`. The last option calls the mob's normal persistence mechanism. It does not inject NBT. `name_visible: true` requires a custom name. Omitting every field retains vanilla encounter/v1 presentation and despawn behavior.

Safe combat attributes can use the short fields below or their exact namespaced IDs inside `attributes`, but not both for the same attribute:

| Short field | Attribute ID | Bounds |
| --- | --- | --- |
| `health` | `minecraft:max_health` | 1-2048 |
| `movement_speed` | `minecraft:movement_speed` | 0-4 |
| `attack_damage` | `minecraft:attack_damage` | 0-2048 |
| `armor` | `minecraft:armor` | 0-30 |
| `knockback_resistance` | `minecraft:knockback_resistance` | 0-1 |

Attributes are applied after vanilla spawn initialization and before authored equipment. When maximum health is changed, current health is then set to the resulting maximum. If the selected entity is not living or does not own an authored attribute, spawning fails with a focused diagnostic instead of silently ignoring the field.

Set `boss: true` for a participant-only health bar owned by that spawned member. `boss_bar_color` is `pink`, `blue`, `red`, `green`, `yellow`, `purple`, or `white`. `boss_bar_overlay` is `progress`, `notched_6`, `notched_10`, `notched_12`, or `notched_20`. The designation is stored on the owned entity, so the bar reconstructs after reload or chunk return and disappears on death, failure, cancellation, release, or cleanup. Boss-bar presentation without `boss: true` is rejected.

### Spawn modes

| `spawn_mode` | Behavior |
| --- | --- |
| `group` | Spawns one raid-like group around the authored anchor. This is the backward-compatible default. |
| `near_player` | Captures an online participant's current position when `start_encounter` runs and spawns within three blocks. An explicit anchor is not required. |
| `fixed` | Spawns at the step's `dimension`, `x`, `y`, and `z` coordinates and tells participants where to go. |
| `raid_waves` | Spawns either `wave_count` copies of `members` or an explicit `waves` array, retaining authored identity and progress across saves. |

For `fixed`, customize the message with a localized `location_message` reference such as `{"key":"example.encounter.gate_defense.location_message"}`. `{x}`, `{y}`, `{z}`, and `{dimension}` are replaced after localization. If omitted, the player receives a default "Go to the encounter" coordinate message.

For `raid_waves`, `wave_interval_ticks` controls the delay between waves. `wave_trigger` is `all_defeated` (the default raid-style behavior) or `timer`. A timer-triggered wave waits only for its interval. An all-defeated wave starts its interval after every mob in the previous wave has been defeated. Raid waves show a participant-only boss bar by default. Set `"boss_bar": false` to disable it. The bar is restored after a reload and removed when the encounter ends or is cleaned up.

The legacy `members` plus `wave_count` shape remains shorthand for identical waves. For distinct waves, omit those shorthand fields and author `waves` with 1-32 entries. Every wave requires a stable lowercase `id` and its own `members`. It may set `delay_ticks` (0-12000), `trigger`, a localized `boss_bar_title` reference, and wave-level `equipment` defaults that individual members override. The current wave index and ID, its absolute delay deadline, started-wave IDs, and fired hook IDs are persisted. Changing or removing an active wave ID fails safely rather than silently substituting a different definition.

`extra_per_player` is deterministic for both forms: after party size is captured at encounter creation, that many copies of each wave's first member are added for every additional participant up to `max_party_size`. Explicit waves may also use bounded, participant-only `scene_actions` of type `notification` or `dialogue`, plus a single `dialogue_hook`. Every hook needs a stable ID and a localized `text` reference. Hook IDs are recorded before delivery and are never fired again after reload.

```json
{
  "schema": "villagerretaliation:encounter/v1",
  "id": "example:three_wave_raid",
  "members": [{ "entity": "minecraft:pillager", "count": 3 }],
  "spawn_mode": "raid_waves",
  "wave_count": 3,
  "wave_interval_ticks": 100,
  "wave_trigger": "all_defeated",
  "boss_bar": true,
  "spawn_radius": 12
}
```

Distinct composition example:

```json
{
  "schema": "villagerretaliation:encounter/v1",
  "id": "example:gate_defense",
  "spawn_mode": "raid_waves",
  "waves": [
    {
      "id": "scouts",
      "members": [{ "entity": "minecraft:zombie", "count": 3 }],
      "boss_bar_title": {"key": "example.encounter.gate_defense.wave.scouts.boss_bar_title"}
    },
    {
      "id": "captain",
      "members": [{ "entity": "minecraft:pillager" }],
      "delay_ticks": 100,
      "trigger": "all_defeated",
      "boss_bar_title": {"key": "example.encounter.gate_defense.wave.captain.boss_bar_title"},
      "equipment": { "mainhand": { "item": "minecraft:crossbow" } },
      "dialogue_hook": { "id": "captain_arrives", "text": {"key": "example.encounter.gate_defense.wave.captain.dialogue_hook.text"} }
    }
  ]
}
```

## Persistence and recovery

Scene SavedData is version 2. Version 0 gains the explicit version/instance/encounter/audit containers. Version 1 gains durable receipts, pending operations, cleanup, and result fields. Quest and fact SavedData keep their independent migrations.

Instances persist identity, definition version/hash, quest/player/party ownership, participants, lifecycle state, current step, casts, step records, retry/failure data, cleanup, pending operations, receipts, and results. Step states are `pending`, `prepared`, `running`, `applied`, `completed`, `failed`, and `skipped`. Scene states are `pending`, `running`, `waiting`, `blocked`, `completed`, `failed`, `cancelled`, and `cleaning_up`.

Minecraft and separate SavedData writes are not a transaction, so the runtime does not promise universal exactly-once effects. It provides stable operation IDs, durable intent, prepared/applied/completed receipts, idempotent state-setting, and world reconciliation. Item/loot, XP, reputation/gossip, counter, quest-transition, dialogue, and encounter operations use receipts. A reload that finds a prepared operation without enough evidence blocks with a precise diagnostic instead of guessing and risking duplication or loss.

## Scheduling and ownership

The server scheduler has a fixed work budget per tick, a wake-time queue, and fair owner buckets. It does not scan all entities, force-load chunks, or repeatedly synchronize an unchanged journal. Player reconnect and provider return wake blocked work. Quest completion, failure, abandonment, and expiration apply the scene's cancellation/cleanup policy. Party membership changes preserve the scene's captured identity and encounter scaling, preventing duplicate scenes or rewards.

Journal status exposes actor waits, party waits, active encounters, failures, blocks, and operator-repair requirements. Operator commands include `inspect`, `trace`, `list`, `rebind`, `retry`, `cancel`, `cleanupEncounter`, and `resume` under `/vr admin scene`. Every mutation appends an audit record with scene/actor identity, before/after state, reason, game time, and operator identity. Bindings and receipts remain historical.

## Extensions For Java Mod Authors

Other mods can register actor types, scene steps, encounter controllers, providers, objectives, actions, conditions, and trigger events through `VillagerRetaliationRegistries` during mod registration.

Each registered type supplies its ID, accepted JSON, validation, runtime behavior, debug text, recovery behavior, and any schema metadata needed by authoring tools. Built-in types use the same registration path.

Registration rejects invalid IDs, duplicate IDs, and conflicting aliases. Registration closes before datapack files are compiled, so late registration fails with a clear error. Install step executors through `SceneStepExecutors` using the matching registered step description.

## Performance and migration guidance

- Keep waits event-driven or use sensible `poll_ticks`. Do not build one-tick polling loops.
- Prefer marker/position targets over broad entity searches.
- Keep encounters small and placement attempts bounded.
- Never renumber or derive step IDs from array positions. Raise `definition_version` for intentional semantic changes.
- Preserve the old resource while active instances drain if a step graph cannot remain compatible.
- Back up world data before removing an extension that owns active actor or step types.

The complete two-villager, player/party, branch, movement, dialogue, wait, controlled ambush, cleanup, provider-unload, and quest-result example is in `example-packs/cinematic-gate-ambush/`.
