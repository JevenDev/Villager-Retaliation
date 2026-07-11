# Persistent Quest Scenes

Quest scenes are server-owned, datapack-authored execution graphs for cinematic quest work that must survive saves, reloads, disconnects, and chunk unloads. Scene resources use `villagerretaliation:scene/v1` under `data/<namespace>/quest_scenes/`; controlled encounter templates use `villagerretaliation:encounter/v1` under `data/<namespace>/quest_encounters/`.

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

The operation ID is part of the owning player, party, quest instance, or world key. Repeating the same operation returns the existing instance instead of creating another. `wait_for_result` records that the caller consumes the durable terminal result; scene execution itself is always asynchronous.

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

The compiler rejects missing references, duplicate actor or step IDs, missing capabilities, unknown types/templates, unreachable paths, invalid failure paths, and immediate unbounded cycles. Datapack reload compares the canonical definition hash and stable step IDs. Compatible edits continue; incompatible edits leave the readable instance blocked for repair.

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

Binding sources are `owner_player`, `party_member`, `quest_provider`, `uuid`, `marker`, `encounter`, `owned_spawn`, and `unbound`. Replacement policies are `fixed`, `operator_rebindable`, `compatible_replacement`, `respawn_if_owned`, and `optional`. Missing policies are `block`, `fail`, `skip`, and `wait_until_timeout`; death policies are `fail`, `block`, `apply_missing_policy`, `respawn_if_owned`, and `continue_with_snapshot`.

Bindings persist UUID/target identity, source, last dimension and position, display snapshot, generation, live/snapshot state, and full replacement history. A fixed narrative actor is never proximity-replaced. Provider actors reuse quest-provider identity and compatible rebind history. Use `/villagerretaliation scene rebind <scene-uuid> <alias> <entity>` for an `operator_rebindable` repair.

## Built-in steps

Each step has `id`, `type`, optional `actors`, a `data` object, `next`, named `transitions`, and optional `failure_step`.

| Step | Important data |
| --- | --- |
| `wait_ticks` | `ticks`; persists an absolute wake time |
| `wait_condition` | registered `conditions`, `timeout_ticks`, `poll_ticks` |
| `move_actor` | `actor`, `target_actor` or `dimension`/`x`/`y`/`z`, `speed`, `arrival_distance`, `timeout_ticks`, `path_failure_policy`, explicit `allow_teleport` |
| `face_actor` / `face_position` | source `actor` and target actor or position |
| `dialogue` | `text`, speaker aliases in `actors`; one delivery receipt per participant |
| `action_batch` | allowlisted `actions`, each with a stable `id`; arbitrary commands are rejected |
| `quest_transition` | safe quest action fields such as `target_stage`, completion, or failure |
| `scene_branch` | ordered `branches` containing registered `conditions` and a transition name, plus `default_transition`; the chosen name is persisted |
| `scene_complete` / `scene_fail` | durable terminal result |
| `start_encounter` | `template`, anchor actor or coordinates, and persisted difficulty inputs |
| `wait_encounter` / `cancel_encounter` / `cleanup_encounter` | `encounter_step` naming the start step; omitted only when the scene owns exactly one encounter |

Movement never force-loads a chunk. It waits for the actor/destination chunk, resumes navigation when available, and only teleports when both `path_failure_policy: "teleport"` and `allow_teleport: true` are authored.

## Encounters

```json
{
  "schema": "villagerretaliation:encounter/v1",
  "id": "example:gate_ambush",
  "version": 1,
  "controller": "villagerretaliation:controlled",
  "members": [{ "entity": "minecraft:zombie", "count": 3 }],
  "extra_per_player": 1,
  "max_party_size": 4,
  "placement_attempts": 16,
  "spawn_radius": 8,
  "respawn_policy": "missing_if_loaded",
  "cleanup_policy": "remove_survivors",
  "completion_condition": "all_defeated"
}
```

Templates are allowlists, not command containers. Party-size and difficulty inputs are captured when the encounter starts. Owned entities carry durable encounter identity; reload reconciles UUIDs and tags before bounded safe-placement attempts. Unrelated nearby mobs never count. Cleanup removes, retains, or releases surviving owned mobs according to the template and scene policy.

## Persistence and recovery

Scene SavedData is version 2. Version 0 gains the explicit version/instance/encounter/audit containers; version 1 gains durable receipts, pending operations, cleanup, and result fields. Quest and fact SavedData keep their independent migrations.

Instances persist identity, definition version/hash, quest/player/party ownership, participants, lifecycle state, current step, casts, step records, retry/failure data, cleanup, pending operations, receipts, and results. Step states are `pending`, `prepared`, `running`, `applied`, `completed`, `failed`, and `skipped`; scene states are `pending`, `running`, `waiting`, `blocked`, `completed`, `failed`, `cancelled`, and `cleaning_up`.

Minecraft and separate SavedData writes are not a transaction, so the runtime does not promise universal exactly-once effects. It provides stable operation IDs, durable intent, prepared/applied/completed receipts, idempotent state-setting, and world reconciliation. Item/loot, XP, reputation/gossip, counter, quest-transition, dialogue, and encounter operations use receipts. A reload that finds a prepared operation without enough evidence blocks with a precise diagnostic instead of guessing and risking duplication or loss.

## Scheduling and ownership

The server scheduler has a fixed work budget per tick, a wake-time queue, and fair owner buckets. It does not scan all entities, force-load chunks, or repeatedly synchronize an unchanged journal. Player reconnect and provider return wake blocked work. Quest completion, failure, abandonment, and expiration apply the scene's cancellation/cleanup policy. Party membership changes preserve the scene's captured identity and encounter scaling, preventing duplicate scenes or rewards.

Journal status exposes actor waits, party waits, active encounters, failures, blocks, and operator-repair requirements. Operator commands include `inspect`, `trace`, `list`, `rebind`, `retry`, `cancel`, `cleanup_encounter`, and `resume` under `/villagerretaliation scene`. Every mutation appends an audit record with scene/actor identity, before/after state, reason, game time, and operator identity; bindings and receipts remain historical.

## Extensions

Register actor types, scene steps, encounter controllers/templates, providers, objectives, actions, conditions, and trigger events through `VillagerRetaliationRegistries` during mod registration. Descriptors own aliases, live/snapshot capabilities, codec/parser ownership, validation, execution/query hooks, debug formatting, recovery/idempotency mode, tooling/schema metadata, and client-sync policy. Built-ins use the same registration path.

Registries reject malformed namespaced IDs, duplicates, and alias collisions and expose ordered immutable descriptor views. They freeze before datapack compilation; late registration throws a diagnostic instead of changing the contract. Scene executors are installed through `SceneStepExecutors` against a registered descriptor. Browser tools show exported descriptors marked browser-available and label runtime-only extension types.

## Performance and migration guidance

- Keep waits event-driven or use sensible `poll_ticks`; do not build one-tick polling loops.
- Prefer marker/position targets over broad entity searches.
- Keep encounters small and placement attempts bounded.
- Never renumber or derive step IDs from array positions. Raise `definition_version` for intentional semantic changes.
- Preserve the old resource while active instances drain if a step graph cannot remain compatible.
- Back up world data before removing an extension that owns active actor or step types.

The complete two-villager, player/party, branch, movement, dialogue, wait, controlled ambush, cleanup, provider-unload, and quest-result example is in `example-packs/cinematic-gate-ambush/`.
