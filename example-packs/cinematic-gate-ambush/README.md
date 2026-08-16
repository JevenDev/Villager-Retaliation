# Cinematic Gate Ambush

This beta.13 example starts a persistent party-owned scene from a quest response. Captain Mara is the fixed quest provider. Scout Tovin is an optional, operator-rebindable second villager: bind that alias through the public actor-binding API or `/villagerretaliation scene rebind <scene-id> scout <entity>` after the instance starts. His movement/dialogue safely skips while unbound, which makes the same pack useful for testing repair.

The quest, English catalog, scene, and encounter are private companions under `data/gate_story/quests/gate_watch/gate_ambush/`. Their explicit IDs remain `gate_story:gate_ambush`; the bundle path owns them but does not derive their persistent identity.

Choose the bold or cautious response, then unload the provider's chunk during the movement or wait. The scheduler retains the absolute wait deadline and destination and resumes after the actor chunk returns. The ambush resolves two weighted spawn points from the gate marker, saves every selected point before placement, and uses receipt-backed phases for Gatebreaker's arrival line and defeat fact. It reconciles owned zombie UUIDs before spawning and removes surviving owned mobs on cancellation. Completing it applies the quest transition through a receipt.

The authored gate coordinates are intentionally obvious placeholders (`0 64 12`) because dynamic marker discovery remains a future authoring primitive. Change them for the test world. Stable actor aliases, step IDs, and `operation_id` values should not be renamed once a world has active instances.

`wait_for_result` now suspends the response sequence without blocking the server thread. The continuation survives reload, records success/failure/cancellation separately, and resumes once. The final `target: "complete"` transition is compiled as a typed terminal quest transition. Its quest callback excludes this originating scene while applying cancellation policy to siblings.
