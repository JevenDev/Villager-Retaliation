# Quest Runtime Roadmap

The persistent quest scene runtime supports typed actors, resumable steps, controlled encounters, recovery and extension descriptors.

## Supported features

- Typed player, villager, living-entity, encounter-group and position actors
- Persistent actor bindings, replacement policies, snapshots and replacement history
- Resumable scene steps with stable IDs and durable operation receipts
- Safe movement, dialogue, waits, branches, quest transitions and allowlisted action batches
- Controlled encounters with waves, objectives, allies, rewards, cleanup and recovery
- Save migrations, blocked-repair states, operator commands and audit history
- Registered actor, step, encounter, provider, objective, action, condition and trigger descriptors
- Exported schema metadata used by the runtime and browser authoring tools

## Compatibility

`villagerretaliation:scene/v1` and `villagerretaliation:encounter/v1` define the authored scene and encounter formats.

Actor aliases, step IDs, encounter member IDs and operation IDs are persistence-critical identifiers. Preserve them when updating content used by existing saves. Compatible additions remain additive. Changes that cannot safely load existing definitions or scene state require a new schema version.

Scene instances fail closed when recovery cannot be proven safe. Operator repair, cancellation and cleanup are explicit actions with audit records.

## Runtime limits

The runtime does not provide arbitrary command steps, unbounded entity searches or forced chunk loading.
