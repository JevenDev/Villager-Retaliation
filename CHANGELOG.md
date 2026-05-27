# Changelog

## 1.0.0-beta.12 - Unreleased

### Added

- Added persistent villager social profiles with generated Knowledge, Guts, Proficiency, Kindness, and Charm attributes.
- Added profession-biased profile generation, stable per-villager profile seeds, saved profile data, and parent-profile blending hooks for inherited villager traits.
- Added a Profile page to the villager interaction screen with a social-attribute chart, rank labels, localized attribute descriptions, and hover tooltips that show exact scores.
- Added profile request/sync networking and a client-side profile cache so interaction screens can display server-generated villager profiles.
- Added debug commands for villager and wandering trader profiles: get, reroll, set attribute, and export.
- Added villager profile data to dialogue context helpers so dialogue logic can react to high social attributes.
- Added temporary villager mood states for Neutral, Content, Grateful, Afraid, Angry, Suspicious, Grieving, Protective, Hopeful, Stressed, Proud, and Lonely.
- Added beta.12 dialogue filters for temporary moods and social attributes: `mood`, `moods`, `min_mood_intensity`, `requires_high_*`, and exact `min_*` / `max_*` attribute ranges.
- Added beta.12 normal-dialogue `conditions` blocks for compound line logic, including memory, family, relationship, and recruitment-memory checks.
- Added `conditions` support to dialogue options, so option visibility can use the same compound family and relationship checks as normal dialogue lines.
- Added normal dialogue line `priority`, `category`, and `text_key` fields for explicit selection tiers, debug grouping, and localized text indirection.
- Added `/villagerretaliation dialogue explain` and `/villagerretaliation datapack diagnostics` to make dialogue selection and datapack reload warnings, including invalid condition schemas, easier to inspect in-game.
- Added built-in dialogue data validation and datapack-builder wiki snapshot checks to catch schema/docs drift during development, including deep validation for beta.12 `conditions` blocks.
- Added beta.12 datapack builder and website wiki support while keeping the beta.11 wiki snapshot separate for beta.11 pack authors.
- Added trade-refresh buttons to villager trade slots so players can ask a villager to replace a specific trade on the next Minecraft day when an eligible skill-trade replacement exists.
- Added data-driven forced dialogue for trade-refresh results, including accepted, already-pending, unavailable, and not-ready responses with reputation-specific option replies.
- Added trade-refresh ready follow-up dialogue with `trade_refresh.ready` message lines and `trade_refresh.ready_options` forced-dialogue options, including placeholders for restocked trade summaries.
- Ready trade-refresh requests now trigger the ready follow-up when the player gets close to the villager, applying the refreshed trades before the player opens the trade menu so the order-status dialogue option does not linger after completion.
- Added recruitment left-behind follow-up dialogue with a dedicated talk option and biome-aware memory filtering through `recruitment_memory_biome` / `recruitment_memory_biomes`.
- Added selectable Skills-page detail cards with expanded localized skill descriptions and an in-tooltip click hint for deeper skill info.
- Added persistent per-villager last-seen day memory for each player, plus absence-aware opening dialogue that can reference day gaps with `{days_since_seen}`, `{day_or_days}`, and `{days_since_seen_phrase}` placeholders.

### Changed

- Promoted the development version from `1.0.0-beta.11` to `1.0.0-beta.12`.
- Social Attributes now lightly affect mood transitions, reputation recovery, retaliation decisions, and gossip spread when the matching config toggles are enabled.
- The datapack builder Convert flow can retarget beta.11 packs to beta.12 without renaming or removing existing beta.11 JSON fields.
- Trade refresh replacement selection compares full result stacks instead of only item types, so variants such as different enchanted books can appear while exact duplicate refreshed trades are still avoided when possible.
- Special Order selection rows now show concise trade names while wait and cooldown details are delivered through the data-driven confirmation and queued dialogue lines.
- Special Order selection rows now include result counts when greater than one, distinguishing entries such as Empty Map from 2x Empty Map.
- Special Order selection rows now collapse requestable definitions with the same result item by keeping the higher-count result, such as preferring 2x Empty Map over Empty Map.
- Special Order confirmation, queued, cooldown, and limit dialogue now receives singular/plural placeholders so built-in lines say `day`/`days` and `request`/`requests` naturally.
- Clicking `Place Special Order` now plays a randomized data-driven follow-up response before the queued-order dialogue opens.
- Special Order cooldown now starts when the request is accepted, preventing the same player from immediately placing another Special Order with that villager.
- Special Order status choices now remain in the forced-dialogue flow when selected from the `Ask about orders` menu.
- `Ask about orders` now opens its dynamic status choices by updating the existing dialogue screen instead of replacing the client screen.
- Trade refresh `What do you need?` replies now explain the specific blocker, such as missing replacement stock, full Special Order slots, cooldown, or missing payment.
- Random trade-refresh requests now share the same three-active-request cap as Special Orders and use matching request-limit dialogue.
- Trade refresh blocker responses reached from an existing forced-dialogue menu now update that menu in place, or fall back to villager chat if no option set is available.
- Regular completed trades now add `0.5` primary profession skill progress by default instead of `0.1`, giving one visible skill point every two trades.
- Special Orders now treat `min_rank` as the skill unlock and ignore `max_rank`, letting high-skill villagers fulfill earlier catalog requests and duplicate items they already stock.
- Forced conversation request validation now keeps active forced-dialogue sessions attached to their villager target while forced-session distance and availability rules are still met.
- Built-in container-theft leave outcomes now default to response arrays (`responses`, `success_responses`, `failure_responses`) for more varied short reactions.
- Built-in normal dialogue lines now use beta.12 `conditions` for migrated memory, family, relationship, and recruitment filters.
- Built-in family and relationship dialogue options now use beta.12 `conditions`.
- Normal dialogue line selection now applies explicit `priority` tiers before weighted random selection. Existing packs keep the default `priority: 0`.
- The schema docs and Datapack Generator now distinguish canonical field names from compatibility aliases across player-item filters, item hand-ins, equipment filters, notification world text style, and forced-dialogue triggers.
- First-conversation opening lines now avoid replaying for villagers that already have persisted last-seen memory of the player, even after world leave/join cycles.

### Planned Beta.13 Deprecations

- Flat normal dialogue line memory, family, relationship, recruitment, container-theft, gear-report, and retaliation helper fields still load in beta.12, but are planned for beta.13 deprecation in favor of `conditions`: `requires_known_family`, `requires_known_parent`, `requires_known_sibling`, `requires_known_spouse`, `requires_known_child`, `requires_known_grandparent`, `requires_known_grandchild`, `requires_known_descendant`, `requires_known_aunt_uncle`, `requires_known_cousin`, `requires_known_niece_nephew`, `requires_known_extended_family`, `requires_known_deceased_family`, `requires_known_relationship`, `requires_known_current_relationship`, `requires_known_past_relationship`, `requires_known_crush`, `requires_known_dating_partner`, `requires_known_fiance`, `requires_known_romantic_spouse`, `requires_known_separated_partner`, `requires_known_widowed_partner`, `requires_recent_broken_bed_memory`, `requires_recent_direct_hit_memory`, `requires_gear_report_used_in_combat`, `requires_gear_report_unused_in_combat`, `requires_recruitment_memory`, `requires_recruitment_boat_trip`, `requires_recruitment_ocean_crossing`, `requires_recruitment_swim_trip`, `excludes_recruitment_ocean_crossing`, `requires_container_theft_to_self`, `requires_container_theft_from_other`, `requires_retaliation_to_self`, and `requires_retaliation_from_other`.
- Flat dialogue option family and relationship helper fields still load in beta.12, but are planned for beta.13 deprecation in favor of `conditions`.

### Fixed

- Special Order selection now shows the active-order limit dialogue as soon as a player tries to place a fourth active order with the same villager, and the cap is hard-clamped to three even if config data is stale.
- Multiple ready random refreshes now all fulfill in one pass when they were accepted earlier; they still prefer replacement results not already present in the offer list, but fall back to duplicates as long as the slot is not recycling the same exact result.
- Disabled vanilla villager trade-preview hand behavior so nearby players holding emeralds or other trade costs no longer cause displayed trade results to replace, duplicate, or drop held villager items.

## 1.0.0-beta.11-hotfix.1 - 2026-05-26

### Changed

- Villagers no longer generate arbitrary profession weapons when attacked. They now fight with weapons they already hold, weapons stored in their inventory, or eligible weapons they can pick up from the ground.
- Villagers can now swap a held non-weapon item with an inventory weapon for combat, then restore the held item after the borrowed weapon is returned.
- Villagers with a held non-weapon can now pick up eligible ground weapons; the held item is stored first and only drops if storage is truly full.
- Villager gifts are now refused before item removal when the receiving villager has no room to store the gift.
- Villager inventory screens now allow the player's normal drop key to drop hovered villager-slot items.

### Fixed

- Fixed a beta.11 item duplication issue where temporary combat/profession weapons could become real inventory overflow drops when a villager restored a managed held item.
- Fixed toolsmiths, farmers, and other combat-capable villagers dropping generated weapons or bread while simply walking around or exiting combat.
- Fixed full-inventory villagers dropping held items instead of swapping them with borrowed inventory weapons.
- Fixed vanilla trade-preview hand items, such as bells shown when a player holds emeralds near a toolsmith, being treated as real displaced inventory items and dropped.
- Fixed ground-weapon pickup being blocked when the villager already had a player-managed non-weapon in its main hand.

## 1.0.0-beta.11 - 2026-05-26

### Added

- Added data-driven villager dialogue, notifications, gifts, pacification payments, profession loot, story discovery, and villager-name resources.
- Added a richer villager interaction flow with localized UI text, chat-position controls, conversation state syncing, and updated interaction-screen assets.
- Added villager inventory access and inventory UI support, including gift inventory visuals and inventory request networking.
- Added villager recruitment support, follow-up dialogue, and recruitment-related reputation/event tracking.
- Added social graph data for villager gender, family, relationships, relationship stages, family-tree snapshots, and relationship dialogue filters/placeholders.
- Added story discovery systems for structure and biome rumors, story-hint reports, cartographer map reports, discovered-story dialogue, and story-related reputation advancements.
- Added gift advice, gift result follow-ups, high-reputation gifts, gift keepsakes, gift-return tracking, and data-driven gift preferences.
- Added data-driven notification triggers and notification text resources, including richer village-event, combat, reputation, and interaction notices.
- Added data-driven pacification payment offers with configurable payment items and updated pacification result handling.
- Added resource-pack hooks and documentation for combat/non-combat villager model overrides, combat textures, and EMF-compatible model loading.
- Added debug commands/items for villager testing, including breeding and maturity debug items.
- Added new reputation/story advancements such as trusted directions, changing course after betrayal, legendary trading, story keeping, and village chronicling.
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
- Added player item durability and enchantment filters and placeholders for item-filtered dialogue, forced dialogue, and notifications.
- Added normal dialogue option item hand-ins through `give_items`, with optional villager-inventory storage, drop/discard destinations, failure responses, and `{given_item}` / `{payment_item}` placeholders.
- Added armed and unarmed villager filters across villager-backed pack rules, including dialogue options, lines, messages, openings, closings, pacify lines, notifications, gifts, pacification payments, and profession loot.
- Added config support for choosing theft-only container confrontations or opening-based confrontations, and for limiting watched containers to world-generated loot-table containers or allowing all watched containers.
- Added loot-table filters for forced dialogue entries through `loot_table` and `loot_tables`, so packs can target vanilla or modded generated containers.
- Added forced dialogue options with responses, reputation changes, aggro behavior, ordering, and conversation-ending behavior.
- Added forced-dialogue response variations for option responses, payment outcomes, and stolen-item return outcomes.
- Added `take_items` forced-dialogue payments that can remove a total item count from the player, including counts larger than one stack.
- Added payment destinations for removed items: discard, witness villager inventory, source container, drop at villager, and drop at container.
- Added shared reputation condition fields for dialogue options, dialogue lines, forced-dialogue entries, and forced-dialogue options: `reputation_level`, `reputation_levels`, `min_reputation`, and `max_reputation`.
- Added top-level `replace` support for dialogue and notification datapack files.
- Added forced-dialogue `leave_options`, max-distance handling, smooth camera turning, and distance-aware dialogue camera zoom.
- Added id-based replacement/removal for gift rules and additive villager-name files, so small packs no longer need to copy full built-in files for small changes.
- Added village container theft memories and gossip through `player_container_theft`, `requires_container_theft_to_self`, and `requires_container_theft_from_other`.
- Added theft-memory placeholders for dialogue, including `{stolen_item}`, `{stolen_count}`, `{stolen_stack}`, `{stolen_container}`, `{stolen_loot_table}`, `{theft_witness}`, and `{theft_witness_possessive}`.
- Added data-driven profession loot resources, profession-specific villager loot tables, and resource-id-aware pack parsing.
- Added generated-container item tooltips, generated-item safeguards, and villager trade-payment tracking with reputation penalties when tracked payment items are taken.
- Added a browser-based Villager Retaliation datapack builder/generator with import/export, file-tree browsing, validation panels, JSON preview editing, panel toggles, resizable persistent panels, and GitHub Pages deployment.
- Added VR pack-version support to the datapack builder; beta.11+ exports write `villagerretaliation.pack_version` in `pack.mcmeta`, and imports use it to restore the target generator version.
- Added datapack-builder support for forced dialogue, item payments, profession loot, generated-container loot table filters, `player_item_proximity` item callouts, item durability filters, reputation-gated dialogue, armed/unarmed villager filters, and theft-memory event tags.
- Added datapack-builder support for `dialogue_option` entries with separate `request` fields and forced-dialogue chat output.
- Added datapack-builder quality-of-life tools: built-in versioned wiki tabs, multi-tab wiki navigation, wiki search/highlights, preview line numbers, drag-and-drop import and entry reordering, inline save, undo/redo, configurable keybinds, settings, migration UI, and suggestions for the `baby_villager_attacked` event tag.
- Added datapack-builder warnings for duplicate dialogue text variants.
- Added a larger built-in dialogue/event library covering reputation tiers, retaliation aftermath, apologies, village defense, raids, golem loss, fire, gifts, gear reports, recruitment memories, and container-theft gossip.
- Added more profession-specific and profession-group dialogue for everyday conversation, event follow-ups, gear reports, recruitment follow-ups, combat survival, jokes, apologies, gifts, and reputation changes.
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
- Built-in dialogue, notification, gift, pacification, story, and villager-name data moved out of hardcoded paths and into datapack-loadable JSON resources.
- Villager names are now loaded additively from datapacks instead of requiring one monolithic preset-name file.
- Profession-filtered dialogue, keyed messages, openings, and closings now default more carefully around baby villagers, with explicit `show_for_babies` support where needed.
- Dialogue options now use `type: "dialogue_option"` with a separate `request` value, and dialogue lines now use `request`.
- General player-selected conversation now uses `question`; `small_talk` is no longer a separate request.
- Built-in dialogue variants now use `lines` arrays instead of repeated near-identical entries, and recent-dialogue tracking now remembers individual line-array variants.
- Built-in dialogue entries now provide at least three text variants where they output villager speech, reducing repeated one-line responses in common conversations.
- Built-in profession dialogue is now split into profession files, with shared multi-profession reactions kept in grouped dialogue files.
- Mason dialogue had repeated line variants replaced with distinct profession-flavored variants.
- Forced-dialogue chat now uses normal triggers with `output.mode: "chat"` instead of separate `_chat` trigger names.
- Forced-dialogue reputation changes now spread through the gossip hook when a villager witnesses the event.
- Forced dialogue speaker labels now preserve custom villager names in villager chat instead of falling back to profession-only labels.
- Reputation change notifications can now be collapsed to reduce repeated HUD spam.
- The interaction GUI and reputation text now use localization keys.
- Client/server interaction networking was refactored around dedicated payloads for dialogue, gifts, inventory, recruitment, reputation notices, name sync, world text, and conversation endings.
- Villager model, renderer, reputation overlay, world-text, and interaction HUD behavior were adjusted for the expanded interaction systems.
- Villager combat, follow, pathing, hostile-target lookup, cleric support, ground-weapon pursuit, bell search, weapon usability, gossip processing, story lookup, and reputation scans were refactored and optimized to reduce redundant work and stale data.
- Slimes are no longer treated as villager retaliation targets.
- Datapack diagnostics now report dialogue, forced-dialogue, and notification parsing problems more consistently, including stricter builder/import folder validation and clearer validation results.
- Datapack-builder rendering, validation, and imports were tuned with paginated entry lists, render caching, flexible checks/file-tree panels, optimized line-number rendering, notification routing, duplicate detection fixes, invalid validation fixtures, safer unsaved-draft/export handling, strict known-root imports, normalized backslash paths, and more reliable zip entry handling.
- Wiki pages and the built-in builder wiki snapshot now document forced dialogue, profession loot, profession-group dialogue files, player item proximity callouts, theft memories, reputation-gated dialogue, line variation fields, response variation fields, localization, event tags, notification triggers, pack format changes, resource-pack model overrides, and the updated datapack builder workflow.
- Updated README/docs links for the Villager Retaliation Generator and added the GitHub Pages deploy workflow for publishing the builder.
- Promoted the release version from `1.0.0-beta.11-dev4` to `1.0.0-beta.11`.

### Deprecated

- Full-file gift and villager-name overrides still work, but small packs should prefer id-based gift changes and additive name files.
- Legacy pacification placeholders `{emerald_cost}` and `{emeralds}` still work, but new packs should prefer `{payment_cost}`, `{payment_item}`, and `{payment_items}`.

### Removed

- Removed `small_talk` as a separate dialogue request. Use `question` for general player-selected conversation.
- Removed request values from dialogue option `type`; dialogue options now use `type: "dialogue_option"` plus `request`, and dialogue lines use `request`.
- Removed `_chat` forced-dialogue triggers. Use the normal trigger with `output.mode: "chat"`.

### Notes For Pack Authors

- See [Pack Format Changes](wiki/Pack-Format-Changes.md) for the pack-facing migration log.
- See [Forced Dialogue JSON](wiki/Forced-Dialogue.md) for the full forced-dialogue schema.
- See [Dialogue JSON](wiki/Dialogue.md) for reputation-gated dialogue options and lines.
