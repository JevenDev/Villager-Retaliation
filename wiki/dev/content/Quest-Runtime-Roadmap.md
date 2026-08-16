# Quest Runtime Roadmap

The persistent quest scene runtime is already implemented in the beta.13 pre-release. Typed actors, resumable steps, controlled encounters, recovery, and extension descriptors are part of the current development surface. The remaining roadmap is focused on release hardening and long-term compatibility. It does not describe a replacement runtime.

## Current development baseline

The following work is complete in the beta.13 development branch:

- Typed player, villager, living-entity, encounter-group, and position actors
- Persistent actor bindings, replacement policies, snapshots, and replacement history
- Resumable scene steps with stable IDs and durable operation receipts
- Safe movement, dialogue, waits, branches, quest transitions, and allowlisted action batches
- Controlled encounters with waves, objectives, allies, rewards, cleanup, and recovery
- Save migrations, blocked-repair states, operator commands, and audit history
- Registered actor, step, encounter, provider, objective, action, condition, and trigger descriptors
- Exported schema metadata used by the runtime and browser authoring tools

## Beta.13 release work

Before beta.13 leaves pre-release, development will concentrate on these outcomes:

- Keep `villagerretaliation:scene/v1` and `villagerretaliation:encounter/v1` stable for datapack authors
- Continue regression coverage for reloads, unloaded chunks, offline participants, provider replacement, operator repair, cleanup, and reward delivery
- Use the built-in quest scenes and encounters as compatibility cases for parser, compiler, persistence, and runtime changes
- Gate SavedData changes with migration tests so existing readable scene state is preserved
- Keep diagnostics, exported descriptors, and browser authoring tools aligned with the runtime
- Fix save-safety and duplicate-side-effect defects before adding another step family

## Compatibility after beta.13

The first stable release will preserve authored actor aliases, step IDs, encounter member IDs, and operation IDs as persistence-critical data. Compatible additions will remain additive. A change that cannot safely load existing definitions or scene state will use a new schema version instead of silently changing version 1 behavior.

Existing scene instances will continue to fail closed when recovery cannot be proven safe. Operator repair, cancellation, and cleanup will remain explicit actions with audit records.

## Not currently scheduled

There is no scheduled `scene/v2`, no arbitrary command step, and no replacement for the current state-machine boundary. Unbounded entity searches and forced chunk loading are also outside the plan. New actor types, step types, or encounter systems will be added to this roadmap only after they have an implementation target and a release target.

## Roadmap updates

This page will change when work is assigned to a release, when compatibility commitments change, or when a planned item ships. Ideas without an implementation target will not be listed as future features.
