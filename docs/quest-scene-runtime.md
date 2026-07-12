# Quest Scene Runtime

This document defines the durable contract between quest runs, scene instances, continuations, encounters, and cleanup.

## Definitive quest-run identity

A solo run ID is derived from the player UUID, quest resource ID, and persisted start count. It is stable for one run, different for every legitimate repeat, and cannot be replaced after allocation. A shared party quest uses the durable `PartySharedQuestRecord.instanceId` for every member, regardless of personal start history.

Startup establishes identity before authored effects:

1. decide solo or shared-party ownership;
2. allocate or recover the definitive run ID;
3. persist quest, provider, and run state;
4. enroll/link party members using that ID;
5. run stage-entry actions;
6. dispatch `STARTED` triggers;
7. synchronize tracker/UI state.

Duplicate start requests observe the already-active progress and do not increment `startCount`.

## Owners and operation keys

- `PLAYER`: player identity plus the quest/run/operation key; repeats are separate.
- `PARTY`: one owner per party and shared quest run. Reuse merges enrolled participants and missing bindings but never replaces a fixed binding.
- `QUEST_INSTANCE`: the definitive quest-run UUID. Unrelated players cannot collide.
- `WORLD`: a global singleton for the dimension, scene, quest, and operation ID. It is intentionally not run-scoped; authors must choose globally stable operation IDs.

The operation index is rebuilt on load. Legacy owner aliases and compact terminal tombstones also participate in lookup.

## Wait-for-result continuations

`wait_for_result: false` launches/reuses a scene and continues immediately. `true` records a non-blocking continuation containing the scene ID, quest/run, player/provider, source pointer, compiled action snapshot, next action index, replacements, terminal result, and completion receipt.

Success resumes remaining actions once. Failure and cancellation are recorded distinctly and do not execute success actions. Duplicate packets reuse the pending continuation. Offline players or unloaded providers leave it pending. A stale optional scene definition skips the optional launch; a required launch stops the sequence. Scene `action_batch` cannot safely suspend and is rejected during compilation.

Maintenance processes at most 16 continuations per tick.

## Deadlines

Overall timeout zero is disabled for compatibility. Otherwise the absolute deadline is `startGameTime + timeoutTicks` with saturating overflow. Waiting scenes schedule the earlier of their step wake and overall deadline. Blocked scenes retain a deadline wake without polling. Reload reconstructs the same deadline; an overdue scene is processed once.

Definition reload uses the current compiled timeout with the persisted start time. Shortening an elapsed timeout fires immediately; setting it to zero disables the overall deadline deterministically.

## Failure and cancellation policies

All executor failures, encounter failures, actor-policy failures, overall timeouts, quest-terminal callbacks, and operator cancellations use one persisted transition service:

- `FAIL_SCENE`: terminal failure;
- `CANCEL_SCENE`: terminal cancellation;
- `BLOCK_FOR_REPAIR`: durable, explicitly repairable block;
- `RUN_FAILURE_STEP`: advance once to the current step's `failure_step`.

A missing failure step blocks with a focused diagnostic. Transition intent and the applied marker survive reload. Operator resume accepts only policy-created repairable blocks.

The typed `quest_transition` scene step accepts exactly one of `target_stage`/`target: stage`, `target: complete`, `target: fail`, or `target: abandon`. Mixed and unknown forms fail compilation. Quest terminal callbacks exclude the originating scene while still applying sibling cancellation policy.

## Cleanup lifecycle

Scene terminal result and cleanup are orthogonal. A terminal scene keeps `COMPLETED`, `FAILED`, or `CANCELLED` while `CleanupStatus` moves from `RUNNING` to `COMPLETE` (or `BLOCKED` with a diagnostic/backoff). `CLEANING_UP` is reserved for legacy state compatibility and is not the authoritative cleanup marker.

Every terminal path queues cleanup once. Encounter cleanup removes/releases only owned entities, restores only blocks still matching the owned replacement, and preserves later player edits. Missing definitions retry after 1,200 ticks instead of spinning. Operator inspection exposes cleanup status, diagnostic, and retry time.

## Saves, legacy aliases, and exactly-once records

Scene save version 3 introduced explicit `RunIdentityKind`: `QUEST_RUN` or `LEGACY_OWNER`. This marker survives every save and keeps pre-run-ID `PLAYER` and `QUEST_INSTANCE` operations reusable until terminal. Version 4 added terminal tombstones.

Non-idempotent effects use operation receipts with `PREPARED`, `APPLIED`, and `COMPLETED` states. Ambiguous prepared work blocks for repair. Continuations have their own completion receipt. Compaction never removes unresolved receipts, pending continuations/rewards, active encounters, blocked cleanup, or nonterminal scenes.

Fully settled terminal scenes remain detailed for seven in-game days, then compact incrementally into replay-blocking tombstones containing the operation identity, result, times, and completed receipt IDs. Maintenance inspects at most 16 candidates every 200 ticks and retains up to 4,096 tombstones.
