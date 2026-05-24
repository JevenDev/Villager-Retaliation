# Changelog

## 1.0.0-beta.11 - Unreleased

### Added

- Added forced dialogue datapacks under `data/villagerretaliation/forced_dialogue/`.
- Added witnessed container forced-dialogue triggers: `container_theft` and `container_opened`.
- Added config support for choosing theft-only container confrontations or opening-based confrontations, and for limiting watched containers to world-generated loot-table containers or allowing all watched containers.
- Added loot-table filters for forced dialogue entries through `loot_table` and `loot_tables`, so packs can target vanilla or modded generated containers.
- Added forced dialogue options with responses, reputation changes, aggro behavior, ordering, and conversation-ending behavior.
- Added `take_items` forced-dialogue payments that can remove a total item count from the player, including counts larger than one stack.
- Added payment destinations for removed items: discard, witness villager inventory, source container, drop at villager, and drop at container.
- Added shared reputation condition fields for dialogue options, dialogue lines, and forced-dialogue options: `reputation_level`, `reputation_levels`, `min_reputation`, and `max_reputation`.
- Added village container theft memories and gossip through `player_container_theft`, `requires_container_theft_to_self`, and `requires_container_theft_from_other`.
- Added theft-memory placeholders for dialogue, including `{stolen_item}`, `{stolen_count}`, `{stolen_stack}`, `{stolen_container}`, `{stolen_loot_table}`, `{theft_witness}`, and `{theft_witness_possessive}`.
- Added datapack-builder support for forced dialogue, item payments, generated-container loot table filters, reputation-gated dialogue, and theft-memory event tags.
- Added a larger built-in dialogue/event library covering reputation tiers, retaliation aftermath, apologies, village defense, raids, golem loss, fire, gifts, gear reports, recruitment memories, and container-theft gossip.
- Added loot-table-specific forced dialogue scenes for weaponsmith, temple, cartographer, and armorer village chests.

### Changed

- Village generated chest confrontations now use forced dialogue by default when container opening is watched.
- Built-in village chest forced dialogue now varies by reputation: trusted players can be warned, neutral or suspicious players can offer normal payment, and low-reputation players can face more expensive or harsher options.
- Built-in dialogue now leans harder into the mod's core identity: villagers remember personal harm, share gossip, reward defense cautiously, and respond differently to the same player based on current reputation.
- Forced-dialogue reputation changes now spread through the gossip hook when a villager witnesses the event.
- Forced dialogue speaker labels now preserve custom villager names in villager chat instead of falling back to profession-only labels.
- Wiki pages now document forced dialogue, theft memories, reputation-gated dialogue, and the updated datapack builder workflow.

### Notes For Pack Authors

- See [Pack Format Changes](wiki/Pack-Format-Changes.md) for the pack-facing migration log.
- See [Forced Dialogue JSON](wiki/Forced-Dialogue.md) for the full forced-dialogue schema.
- See [Dialogue JSON](wiki/Dialogue.md) for reputation-gated dialogue options and lines.
