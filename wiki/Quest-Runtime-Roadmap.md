# Quest Runtime Roadmap

The Quest Runtime Foundation makes lifecycle changes explicit, versioned, and recoverable. The next milestone should add narrative scene capabilities on top of that boundary without moving Minecraft side effects into the state-machine core.

## Typed actors and bindings

- Add typed actor aliases for villagers, players, hostile entities, and extension-defined actors.
- Persist actor bindings by alias, provider type, stable identity, and last known snapshot.
- Give each alias an authored replacement policy: fixed, operator-rebindable, compatible replacement, or optional.
- Keep replacement history so recovery never silently rewrites narrative identity.

## Resumable scenes

- Treat the foundation's persisted, one-shot lifecycle-event replay as the minimum recovery layer; scene resumption must build on it without broadening an event into an untracked multi-step workflow.
- Represent scene work as persisted, idempotent steps with stable step IDs.
- Resume safely after save/reload, chunk unload, disconnect, or actor replacement.
- Separate dialogue, movement, waits, encounter gates, and completion markers so a repeated tick cannot replay a completed side effect.

## Safe actions and encounters

- Add safe namespaced function actions with an allowlisted registration contract; do not expose arbitrary command strings.
- Add encounter control for registered spawn groups, objectives, cleanup policies, and recovery after partial execution.
- Require server-authoritative validation and explicit capability declarations for every action.

## Extension APIs

Provide registration APIs and stable descriptors for providers, objectives, actions, conditions, and triggers. Extensions should declare required live/snapshot capabilities, serialization, diagnostics, and idempotency behavior so tooling can export the same contract used by the runtime.

## Recovery contract

Every persistent scene step should record its input binding version, started/completed markers, and follow-up side effects. On reload or chunk return, the runtime should reconcile those markers before doing work, preserve readable data from newer versions, and stop with a precise diagnostic when recovery cannot be proven safe.
