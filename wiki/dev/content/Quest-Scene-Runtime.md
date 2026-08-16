# Quest Scene Runtime

This page explains the saved runtime rules for contributors who change quest or scene code. Pack authors usually need [Quest Scenes](Quest-Scenes.md) instead.

Terms used on this page:

- **Run ID**: the UUID that identifies one attempt at a quest.
- **Owner**: the player, party, quest run, or world record responsible for a scene.
- **Operation ID**: an author-chosen name that prevents the same scene action from starting twice.
- **Continuation**: saved work that resumes after a scene finishes.
- **Receipt**: a saved record showing whether a one-time effect has run.
- **Tombstone**: a small record retained after old scene details are removed. It prevents a completed operation from running again.
- **Cleanup**: removal of scene-owned entities and restoration of scene-owned block changes.

## Definitive quest-run identity

A solo run ID comes from the player UUID, quest resource ID, and saved start count. It remains stable for one run and changes for every valid repeat. A shared party quest uses the saved `PartySharedQuestRecord.instanceId` for every member, regardless of personal start history.

Startup establishes identity before authored effects:

1. Decide solo or shared-party ownership.
2. Allocate or recover the definitive run ID.
3. Save quest, provider, and run state.
4. Enroll or link party members using that ID.
5. Run stage-entry actions.
6. Dispatch `STARTED` triggers.
7. Synchronize tracker and UI state.

Duplicate start requests use the already-active progress and do not increment `startCount`.

## Owners and operation keys

- `PLAYER`: player identity plus the quest, run, and operation key. Repeated quests stay separate.
- `PARTY`: one owner per party and shared quest run. Reuse adds missing participants and bindings but never replaces a fixed binding.
- `QUEST_INSTANCE`: the definitive quest-run UUID. Unrelated players cannot collide.
- `WORLD`: one shared owner for a dimension, scene, quest, and operation ID. It is not tied to one quest run. Authors must use an operation ID that remains unique in that dimension.

The runtime rebuilds its operation lookup table when a world loads. Older owner aliases and compact tombstones remain searchable, so an operation completed by an earlier version cannot start again.

## Wait-for-result continuations

`wait_for_result: false` starts or reuses a scene and continues immediately. `true` saves a continuation without freezing the server. It records enough information to resume at the next action, including the scene, quest run, player, provider, compiled actions, replacement values, result, and completion receipt.

Success resumes the remaining actions once. Failure and cancellation are recorded separately and do not run success actions. Duplicate packets reuse the pending continuation. Offline players or unloaded providers leave it pending. If an optional scene definition is missing after reload, the runtime skips it. A required missing scene stops the sequence. Scene `action_batch` cannot pause safely, so compilation rejects that combination.

Maintenance processes at most 16 continuations per tick.

## Deadlines

An overall timeout of zero disables the timeout for compatibility. Any other value creates a deadline from `startGameTime + timeoutTicks`. If that addition would exceed the largest supported number, the value is capped. Waiting scenes schedule whichever comes first, their next step or the overall deadline. Blocked scenes keep a deadline wake-up without checking every tick. Reload recreates the same deadline. An overdue scene is processed once.

Definition reload uses the newly compiled timeout with the saved start time. Shortening a timeout past the current time fires it immediately. Changing it to zero disables the overall deadline.

## Failure and cancellation policies

All executor failures, encounter failures, actor-policy failures, overall timeouts, quest-terminal callbacks, and operator cancellations use one saved transition service:

- `FAIL_SCENE`: end the scene as failed.
- `CANCEL_SCENE`: end the scene as cancelled.
- `BLOCK_FOR_REPAIR`: save a blocked state that an operator can repair.
- `RUN_FAILURE_STEP`: advance once to the current step's `failure_step`.

A missing failure step blocks with a focused error. Transition intent and the applied marker survive reload. Operator resume accepts only repairable blocks created by a policy.

The typed `quest_transition` scene step accepts exactly one destination. Use `target_stage` or `target: stage` for a stage, or use `target: complete`, `target: fail`, or `target: abandon`. Mixed and unknown forms fail compilation. When a quest ends, its callback excludes the scene that caused the transition but still applies the cancellation policy to sibling scenes.

## Cleanup lifecycle

Scene result and cleanup are saved separately. A terminal scene keeps `COMPLETED`, `FAILED`, or `CANCELLED` while `CleanupStatus` moves from `RUNNING` to `COMPLETE`. Cleanup can instead become `BLOCKED` with an explanation and a delayed retry. `CLEANING_UP` exists only for older saved data and is not the current cleanup marker.

Every terminal path queues cleanup once. Encounter cleanup removes or releases only entities owned by the scene. It restores a changed block only when the block still matches the scene's replacement, which preserves later player edits. Missing definitions retry after 1,200 ticks instead of checking continuously. Operator inspection shows the cleanup status, explanation, and retry time.

## Saves, legacy aliases, and exactly-once records

Scene save version 3 introduced explicit `RunIdentityKind`: `QUEST_RUN` or `LEGACY_OWNER`. This marker survives every save and keeps older `PLAYER` and `QUEST_INSTANCE` operations reusable until they finish. Version 4 added terminal tombstones.

Effects that must run only once use operation receipts with `PREPARED`, `APPLIED`, and `COMPLETED` states. Work left in an uncertain prepared state blocks for operator repair instead of risking a duplicate effect. Continuations have their own completion receipt. Data cleanup never removes unresolved receipts, pending continuations or rewards, active encounters, blocked cleanup, or unfinished scenes.

Finished scenes retain full details for seven in-game days. The runtime then reduces them gradually to tombstones that contain the operation identity, result, times, and completed receipt IDs. Maintenance inspects at most 16 candidates every 200 ticks and retains up to 4,096 tombstones.

## Protected villagers and the downed state

Protected villagers do not enter the normal death path after ordinary lethal damage. Final damage is capped in `LivingDamageEvent.Pre`, the villager remains at one health, and a saved downed record stores when the state began, when recovery can begin, which protection source applied, the data version, and the AI or pickup settings to restore. Party contracts, quest provider bindings, scene bindings, inventories, and hired work state remain intact.

Protection is active when any of these sources applies:

- `combat.allVillagersUseDownedState` is enabled.
- The villager is in a vanilla or player raid and `combat.raidVillagersUseDownedState` is enabled.
- The villager has an active hired contract and `combat.hiredVillagersUseDownedState` is enabled.
- The villager has an active party contract and `combat.partyVillagersUseDownedState` is enabled.
- An active quest run from that exact provider UUID uses `death_protection: "while_active"`.
- That provider successfully started a quest using `death_protection: "after_start"`.
- An active scene binds the exact villager to an actor with `lethal_damage_policy: "downed"`.
- The entity has the permanent scoreboard tag `villagerretaliation_essential`.

After a protection source qualifies the villager, separate player, mob, and environmental damage settings decide whether that lethal source may down them. Disabled source categories can finish an already-downed villager with a lethal hit. Operator kill, void, and invulnerability-bypassing sources always bypass protection.

While downed, AI, navigation, attacks, work, following, item pickup, trading, gifts, breeding, and dialogue are suspended. Repeated attacks still cause normal hit effects once but cannot reduce health. Nearby mobs targeting the villager are cleared on entry and once per second. The client receives state changes and renders one of three stable whole-body poses. Each pose also adjusts the hitbox and name-tag position.

Recovery requires the configured minimum duration, no nearby natural hostile or mob targeting the villager within `downedThreatRadius`, and `downedQuietTicks` of quiet. Health returns to `downedRecoveryHealthPercent` of maximum, with a minimum of one. Previous AI and pickup settings are restored. `DOWNED` scene bindings return to `LIVE` and wake their scenes. Recovery still finishes normally if the original protection expires while the villager is downed.

When Second Wind is installed, every villager protected by this resolver is also available through Second Wind's player revive interaction. Villager Retaliation still owns the state. There is no bleedout deadline, and a villager that is not manually revived continues through the normal quiet-period recovery. Compatible clients select a downed pose for each new record. Ordinary unprotected villagers are not included.

`/kill` with `minecraft:generic_kill`, out-of-world damage, damage tagged `minecraft:bypasses_invulnerability`, and direct entity removal bypass protection. Void damage is allowed to kill so a protected villager cannot fall forever.

### Quest provider example

```json
{
  "provider": {
    "type": "villagerretaliation:villager",
    "death_protection": "while_active",
    "filters": {
      "professions": ["minecraft:cartographer"]
    }
  }
}
```

`none` is the default. `while_active` applies only while at least one active run is bound to the exact provider UUID. `after_start` writes the originating quest ID to the villager only after startup is saved successfully. Offers and viewed dialogue do not protect the villager. The marker survives completed or failed quests and reloads. Multiple quest IDs can coexist without repeating start effects. Invalid values report a focused error and fall back to `none`.

### Scene actor example

```json
{
  "alias": "guide",
  "type": "villagerretaliation:villager",
  "binding_source": "quest_provider",
  "replacement_policy": "fixed",
  "missing_actor_policy": "block",
  "lethal_damage_policy": "downed",
  "death_policy": "apply_missing_policy"
}
```

`lethal_damage_policy` defaults to `normal`. It prevents death only while the owning scene is active and is separate from the post-death `death_policy`. A downed binding is saved as `DOWNED`. Steps that need that actor remain blocked without checking every tick. Recovery wakes and resumes the scene.
