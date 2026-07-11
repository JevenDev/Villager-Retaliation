# Cinematic Gate Ambush

This beta.13 example starts a persistent party-owned scene from a quest response. Captain Mara is the fixed quest provider. Scout Tovin is an optional, operator-rebindable second villager: bind that alias through the public actor-binding API or `/villagerretaliation scene rebind <scene-id> scout <entity>` after the instance starts. His movement/dialogue safely skips while unbound, which makes the same pack useful for testing repair.

Choose the bold or cautious response, then unload the provider's chunk during the movement or wait. The scheduler retains the absolute wait deadline and destination and resumes after the actor chunk returns. The ambush has a stable encounter operation, reconciles owned zombie UUIDs before spawning, and removes surviving owned mobs on cancellation. Completing it applies the quest transition through a receipt.

The authored gate coordinates are intentionally obvious placeholders (`0 64 12`). Change them for the test world. Stable actor aliases, step IDs, and `operation_id` values should not be renamed once a world has active instances.
