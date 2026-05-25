# Changelog

## 1.0.0-beta.11 - Unreleased

### Added

- Added forced dialogue datapacks under `data/villagerretaliation/forced_dialogue/`.
- Added witnessed container forced-dialogue triggers: `container_theft`, `container_opened`, and `container_broken`.
- Added forced-dialogue output modes through `output.mode`, including `forced_dialogue` and `chat`.
- Added per-entry chat broadcast radius through `output.radius`.
- Added configurable watched-container break reputation loss, including an additional penalty per generated item dropped.
- Added `retaliation_started` chat-output support for non-player retaliation targets, broadcasting the villager line to nearby players.
- Added `combat.flee_started` notification trigger for villagers that keep fleeing hostile mobs.
- Added forced-dialogue chat `chance` support for occasional event callouts.
- Added forced dialogue witness equipment filters for armed and unarmed villagers.
- Added `player_item_proximity` forced-dialogue triggers for held or worn player item reactions near villagers, including chat-output support.
- Added armed and unarmed villager filters across villager-backed pack rules, including dialogue options, lines, messages, openings, closings, pacify lines, notifications, gifts, pacification payments, and profession loot.
- Added config support for choosing theft-only container confrontations or opening-based confrontations, and for limiting watched containers to world-generated loot-table containers or allowing all watched containers.
- Added loot-table filters for forced dialogue entries through `loot_table` and `loot_tables`, so packs can target vanilla or modded generated containers.
- Added forced dialogue options with responses, reputation changes, aggro behavior, ordering, and conversation-ending behavior.
- Added forced-dialogue response variations for option responses, payment outcomes, and stolen-item return outcomes.
- Added `take_items` forced-dialogue payments that can remove a total item count from the player, including counts larger than one stack.
- Added payment destinations for removed items: discard, witness villager inventory, source container, drop at villager, and drop at container.
- Added shared reputation condition fields for dialogue options, dialogue lines, forced-dialogue entries, and forced-dialogue options: `reputation_level`, `reputation_levels`, `min_reputation`, and `max_reputation`.
- Added village container theft memories and gossip through `player_container_theft`, `requires_container_theft_to_self`, and `requires_container_theft_from_other`.
- Added theft-memory placeholders for dialogue, including `{stolen_item}`, `{stolen_count}`, `{stolen_stack}`, `{stolen_container}`, `{stolen_loot_table}`, `{theft_witness}`, and `{theft_witness_possessive}`.
- Added datapack-builder support for forced dialogue, item payments, generated-container loot table filters, reputation-gated dialogue, armed/unarmed villager filters, and theft-memory event tags.
- Added datapack-builder support for `dialogue_option` entries with separate `request` fields and forced-dialogue chat output.
- Added a larger built-in dialogue/event library covering reputation tiers, retaliation aftermath, apologies, village defense, raids, golem loss, fire, gifts, gear reports, recruitment memories, and container-theft gossip.
- Added built-in `retaliation_started` chat-output combat barks for player targets, raiders, undead, monsters, and generic retaliation targets.
- Added built-in unarmed-villager `retaliation_started` chat-output combat barks.
- Added loot-table-specific forced dialogue scenes for weaponsmith, temple, cartographer, and armorer village chests.
- Added default-on baby villager fleeing when they witness a villager death, configurable with `retaliation.babyVillagersFleeWitnessedDeaths`.
- Added baby-specific witnessed-death alert lines.
- Added baby-specific hit alert lines and village-event dialogue for baby villager attacks.

### Changed

- Village generated chest confrontations now use forced dialogue by default when container opening is watched.
- Built-in village chest opening forced dialogue now varies by reputation: neutral and suspicious players get the standard warning, hostile/despised/feared players get harsher warnings, and trusted or better players are only interrupted if they take items.
- Built-in dialogue now leans harder into the mod's core identity: villagers remember personal harm, share gossip, reward defense cautiously, and respond differently to the same player based on current reputation.
- Dialogue options now use `type: "dialogue_option"` with a separate `request` value, and dialogue lines now use `request`.
- General player-selected conversation now uses `question`; `small_talk` is no longer a separate request.
- Forced-dialogue chat now uses normal triggers with `output.mode: "chat"` instead of separate `_chat` trigger names.
- Forced-dialogue reputation changes now spread through the gossip hook when a villager witnesses the event.
- Forced dialogue speaker labels now preserve custom villager names in villager chat instead of falling back to profession-only labels.
- Wiki pages now document forced dialogue, theft memories, reputation-gated dialogue, and the updated datapack builder workflow.

### Notes For Pack Authors

- See [Pack Format Changes](wiki/Pack-Format-Changes.md) for the pack-facing migration log.
- See [Forced Dialogue JSON](wiki/Forced-Dialogue.md) for the full forced-dialogue schema.
- See [Dialogue JSON](wiki/Dialogue.md) for reputation-gated dialogue options and lines.
