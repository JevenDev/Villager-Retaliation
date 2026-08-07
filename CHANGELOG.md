# Changelog

## 1.0.0-beta.13 - Unreleased

Beta.13 is the Hired Help and Village Life update. It adds complete worker logistics, player parties, persistent village allegiance, Player Raids, protected villagers, duels, quest scenes and encounters, and a much larger quest catalog.


### Added

#### Hiring, Contracts, And Progression

- Added renewable hired-villager contracts with role and duration selection, reputation- and aptitude-aware prices, daily wages, extension limits, early-cancellation refunds, payment grace, automatic renewal, and live contract countdowns.
- Added thirteen contract roles: Combat, Hunting, Mining, Logging, Farming, Fishing, Brewing, Craftsman, Animal Handling, Nitwit, Cook, Smelter, and Courier. Builder is offered separately as a paid one-off construction service.
- Added role qualification through primary and support skills. Canonical professions qualify automatically, Courier is available to every adult, Nitwit remains profession-restricted, and Builder uses its own service eligibility.
- Added a Job Stats view with role readiness, profession overrides, the two contributing skills, weighted aptitude, work speed, transfer capacity, and role-specific job icons and explanations.
- Added skill-based hired-work practice and throughput. Completed work trains both role skills at a 70/30 split, the same weighted aptitude scales work cadence from 75% to 125% and transfer capacity from 50% to 150% without changing vanilla workstation timers.
- Added a persistent, server-authoritative worker runtime with bounded work areas, route focus, work plans, task states, status and final reports, path backoff, unreachable-target memory, stall recovery, storage navigation, and budgeted scans.

#### Worker Roles

- Added Combat workers that guard or roam assigned areas and routes, use configurable target policies, switch weapons for the threat, recover between fights, and keep job combat separate from ordinary retaliation.
- Added Hunting workers with selectable animal, hostile-mob, and player targets, patrol behavior, melee and ranged weapon support, ammunition recovery, and collection and deposit of hunting drops.
- Added Mining workers with exposed-ore, horizontal excavation, and vertical excavation modes, vein plans, stairs and ladders, torch and support placement, hazard checks, water navigation, tool and supply retrieval, and output delivery.
- Added Logging workers with selectable log families, natural-tree and Nether-fungus harvesting, optional stripping, leaf clearing and collection, decay-drop pickup, sapling replanting and bonemeal, and interrupted-tree recovery.
- Added Farming workers that directly harvest mature crops, replant from job supplies, fill empty farmland, optionally till suitable soil, use hoes correctly, and deposit crop output.
- Added Fishing workers with a real villager fishing hook, open-water spot selection, rod durability and enchantment handling, skill-scaled bite timing, catch XP handling, and immediate output deposits.
- Added Brewing workers with selectable potion orders and batch sizes, water-bottle filling, ingredient and blaze-powder supply handling, support for potion variants, multiple brewing stands, and finished-potion collection.
- Added Craftsman workers that use Recipe Filters to choose ordinary crafting recipes, pull exact ingredients from Supplies storage, work at crafting tables, and send crafted results to Output storage.
- Added Cook workers that use filters and vanilla cooking recipes across available cooking workstations, fetch ingredients and fuel, and collect and deposit finished food.
- Added Smelter workers that manage furnaces and blast furnaces, fetch raw materials and fuel, preserve fuel remainders, collect results, and spread work across multiple stations.
- Added Animal Handling workers with selectable breeding targets, breeding and culling limits, safe culling drops and XP, sheep shearing, cow milking, egg and other product collection, tool retrieval, and periodic output deposits.
- Added Courier workers that move filtered cargo between assigned containers, patrol empty inputs, honor per-container extraction allowances, collect Sell Box proceeds, and scale each transfer with villager aptitude.
- Added Builder services with construction blueprints, placement and material previews, work-site validation, paid escrow, cancellation refunds, safe block-entity handling, clearing rules, pause/cancel/finalize states, and a data-driven village-house catalog.
- Added a deliberately low-productivity Nitwit role with periodic supervision reports and its own configurable practice rate.

#### Storage, Logistics, And Economy

- Added persistent villager wallets with lifetime earnings, spending, and deposit totals. Trade income, wages, deposits, refunds, duel stakes, payment boxes, worker earnings, and Sell Box proceeds use the wallet economy.
- Added data-driven currency definitions and the `villagerretaliation:currency` item tag so wallets, contracts, deposits, refunds, payment boxes, duels, drops, worker earnings, and sales no longer assume emeralds.
- Added the Payment Box with a dedicated inventory and screen, recurring contract payment, shared-owner support, bounded chunk loading, automatic renewal, and configured-currency handling.
- Added the Sell Box and daily market with 293 built-in price definitions, animated block art and UI, server-authoritative daily sale values, configured-currency payouts, and courier collection.
- Added the Clipboard, Construction Blueprint, Villager Item Filter, Attribute Filter, and Recipe Filter items with crafting recipes, dedicated screens, previews, tooltips, and copy/reset utility recipes.
- Added Clipboard workforce management for live worker lists, multi-selection, assignment and removal, role changes, warnings, work-area drafting and resizing, route editing, storage assignment, reusable drafts, and world-space previews.
- Added per-purpose container assignments for Supplies, Output, General, and Payment storage. Shared and double containers retain every worker assignment and are resolved as one logical inventory where appropriate.
- Added separate Personal, Job, and Party inventory views on top of the beta.12 storage foundation, with nine-slot hotbars, job equipment and filter slots, protected gear, authorization rules, reclaim windows, overflow handling, and safe return of supplied equipment.
- Added role-aware tool selection, dynamic combat weapon switching, transient-gear tracking, and safeguards against duplicated or leaked equipment.
- Added item filters with item and tag entries, quantities, allow/deny modes, all/any combination rules, stock targets, and configurable transfer policies.
- Added Attribute Filters for component and item-property matching and Recipe Filters for selecting crafted, cooked, or processed outputs, EMI, JEI, and REI drag-and-drop can populate compatible ghost slots.
- Added composable container filters, including attached item-frame rules, persisted filter snapshots, per-container collection and destination policies, and cached matching for large logistics networks.
- Added output backpressure so workers pause when every valid destination is full or filter-capped, report the blocking reason, and resume when capacity returns.
- Added Courier routes with ordered nodes, out-and-back branches, branch previews, reach hints, node-tethered container stops, bounded chunk tickets, nearest-node recovery, batching, and return traversal.
- Added cooperative villager traffic handling for narrow passages and shared routes, with stable right-of-way, short queues, safe sidesteps, and Clipboard visualization controls.

#### Parties, Commands, And Mounts

- Added persistent player parties with paid villager recruitment, invitations, player and villager rosters, leader/admin controls, contract renewal and expiry, dismissal, disbanding, death cleanup, and saved party state.
- Added shared Party inventory and management tabs, authorization-aware container access, protected party gear, preferred inventory opening, and cleanup when membership or contracts end.
- Added party-wide quest credit, retaliation and witness context, raid memories, villager last-known positions, player-party membership commands, and mutual party alliances.
- Added a party quick-command wheel with Follow, Stay, Move To, Regroup, Stand Guard, attack targeting, heal/recover, Ride Mount, Dismount Mount, gather drops, loot containers, equipment, and policy controls.
- Added global and per-villager combat modes, target categories, weapon preferences, drop-collection rules, formation targets, friendly-fire protection, attack raycasting, and server-authoritative permission and target validation.
- Added Stay Here for Revered unhired villagers and authorized hirers, plus one command policy that arbitrates following, holding position, Move To, Regroup, work, party orders, combat, and mounted travel.
- Added persisted one-to-one mount assignments for party villagers and hired workers, supporting horses, donkeys, mules, llamas, and camels through native Minecraft mob-jockey control.
- Added mounted following and long-distance work travel, precise-work dismounts, idle parking, unload/reload recovery, remount and seat promotion, leashed-mount selection, player takeover, and quick mount commands.
- Added optional Ride On integration for two villager seats, front-rider control, rear-rider safety, player seat takeover, and automatic passenger reshuffling without competing navigation.

#### Villages, Allegiance, Raids, And Villager Life

- Added durable POI-backed village identities with canonical UUIDs and aliases, resident rosters, lifecycle state, merge history, retirement and archival, and persisted home allegiance.
- Added connected village footprints built from occupied POIs, complete village structure pieces, and tagged connected terrain such as paths, with datapack tags for modded village worldgen.
- Added data-driven generated village names, custom naming by using a banner on a village bell, synced Home dialogue, village-bound HUD labels, and operator inspect, rename, merge, prune, and reassignment tools.
- Added Wanderer identity for villagers created outside tracked villages, one-day settlement for stable Wanderers, newborn home inheritance, delayed natural-spawn assignment, trusted home reassignment, and party-member settlement protection.
- Added allegiance-aware behavior for villagers and natural iron golems. Locals, allied parties, Wanderers, and foreign communities are distinguished for reputation, gossip, retaliation, discipline, aid, and combat authorization.
- Added a bounded village-footprint debug overlay with gold section outlines, current-village naming, subscription cleanup, and configurable render limits.
- Added Player Raids started by displaying an ominous banner and using a goat horn, with an optional double-use confirmation, preparation and abandonment timers, boss bars, and persistent per-village cooldowns.
- Added snapshotted raid parties and defenders, data-driven militia loadouts, golem reinforcements, defender highlighting, siege targeting, pursuit rules, betrayal handling, and persistent raid memories.
- Added a raid mercy phase for babies and nitwits, village victory and defeat outcomes, fifteen outcome reactions, reputation consequences, dialogue follow-ups, and operator win/lose testing controls.
- Added configurable downed protection for party members, hired villagers, quest providers, scene actors, raid participants, and tagged essential villagers, with source filters, threat checks, synchronized poses and collision sizes, quiet-period recovery, and optional Second Wind revival.
- Added villager duels with server-authoritative offers and confirmation, configurable terms, optional stakes, isolated inventories and loadouts, arena boundaries, spectators, knockout recovery, cooldowns, sounds, post-duel dialogue, and witness stories.
- Added nearby player duels through `/duel <player> <kit> <wager>`, with clickable invitation acceptance, preset or Bring Your Own kits, wager escrow and payout, arena boundaries, combat isolation, and inventory recovery. The Bring Your Own server restriction remains specific to village duels.
- Added optional Curios and Accessories compatibility for assigned duel loadouts and recovery, accessory-aware dialogue item conditions, and ominous-banner recognition.
- Added villager hunger, visible hunger status, hunger-driven natural recovery, Hunger-effect drain, difficulty-scaled starvation damage, food recovery behavior, and healing after completed sleep, all with balance controls.
- Added natural data-driven profession armor, per-piece mixing, armor scavenging and upgrade ranking, shields and guard loadouts, charged-crossbow preservation, arrow recovery, automatic Totem of Undying use, and Mending for armor and held equipment.
- Added ominous banners as wearable player and villager head attachments and four advancements: Sound the Horn, Steady Gaze, The Mark You Chose, and The Village Falls.
- Added in-game MarkedDown guide content covering reputation, interaction, quests, hiring, jobs, Clipboard storage, Builder work, parties, mounts, villages, gifts, watched property, downed villagers, Player Raids, controls, and advancements.

#### Quests, Scenes, Dialogue, And Interfaces

- Added 64 built-in quests, expanding the exact beta.12 catalog from 21 to 85 quests, including 33 repeatable requests and Cartographer's Atlas, Green Thumb, Deep Delvers, Redstone Works, Nether Routes, End Survey, Hearthbound, Field Medicine, Workshop Oaths, Courier Roads, and Last Ember.
- Added five four-quest branching adventure lines with two choice-gated endings apiece, plus connected quest prerequisites, restart rules, provider policies, and end-game progression.
- Added quest module v2 with one-file modules, declarative stages and aliases, branches and choice history, facts and scopes, availability rules, completion limits, provider binding, lifecycle hooks, shared actions and conditions, and v1 compatibility.
- Added objective and trigger support for locations, structures, mob kills, block breaking, placing and interaction, trades, gifts, reputation, inventory, memory events, choices, facts, quest-state changes, and composite requirements.
- Added failed and recoverable quest states, missing-provider rebind rules, provider death protection, deferred lifecycle work, deterministic transition evaluation, and traceable blocker reasons.
- Added persistent quest scenes with typed durable actors, multi-tick steps, waits and continuations, deadlines, exactly-once operation receipts, saved run identities, disconnect and chunk-return recovery, cleanup, audits, and operator repair.
- Added encounter templates with anchored areas, leave policies, authored spawn points and offsets, explicit waves, equipment and enchantments, drop chances, elites, bosses, boss bars, and deterministic weighted variants.
- Added encounter phases, friendly participants, composite victory objectives, retries and failure policies, navigation markers and particles, temporary environmental blocks and restoration, rewards, trophy and mob-drop policies, and safe cleanup.
- Added 10 built-in persistent scenes and five encounter templates for Atlas choices and patrols, Standing Watch, Night Ward, Night Run, Trial Chamber Recall, Nether and End choices, Mansion Warning, and Last Ember.
- Added multi-quest tracking, completion history, selected-quest highlighting, bookmarks, count badges, reward and prerequisite previews, objective item counts, locked-adventure hints, saved journal position, and improved tracker navigation.
- Added a standalone Quest Builder and expanded Datapack Generator support for quest v2, scene v1, and encounter v1 projects, including import, preview, validation, migration guidance, and datapack ZIP export.
- Added dialogue and story coverage for hired work, parties, discipline, village allegiance, raids, downed recovery, duels, mount ownership, gifts, combat outcomes, return visits, and retaliation disengagement.
- Added data-driven non-hostile weapon drawing through shared actions and held, worn, or carried item proximity rules, with configurable duration and Datapack Generator controls.
- Added villager mouth movement while dialogue text appears, configurable text speed, cinematic transitions and bars, styled text effects, blip audio every few visible characters, and configurable routine chat broadcasting and muting.
- Added a non-destructive gift amount selector, scrollable item selection, reaction previews and tooltips, per-player/per-villager daily reputation caps, repeated-item diminishing returns, request throttling, and keepsake-aware validation.
- Added non-binary villager gender support across profiles, family data, breeding compatibility, preset names, UI labels, networking, persistence, and the operator `set_gender` command.
- Added a centralized breeding and birth policy for managed villagers so party, hired, downed, and otherwise protected villagers keep consistent eligibility and newborn initialization.
- Added configurable villager stat nameplates with health, armor, and hunger in Always, Hired Only, Party Only, or Never modes, plus a reputation debug overlay and an optional reputation icon in the vanilla trade screen.
- Added optional EMI, JEI, and REI exclusion zones and filter-slot drag-and-drop so recipe panels avoid villager, party, Payment Box, and filter interfaces.
- Added resource-pack support for dual-arm combat villagers, crossed-arm and side-arm layouts, armor and profession overlays, and OptiFine CEM/EMF-style villager models.

### Changed

- Trade-based skill growth now uses continuous practice XP, advances more slowly at high skill values, and applies per-day and repeated-offer diminishing returns. Existing skill values and saved fractional trade progress are preserved.
- Skill-based merchant leveling now uses linear rather than squared skill scaling, guarantees at least one visible XP for an XP-bearing trade, persists fractional carry, and shows the same adjusted award on the merchant screen that the server applies.
- Villager interaction screen was redesigned with animated page transitions, scrollable option stacks, profession-colored art, portraits and ornaments, pixel controls, scale-aware tooltips, dedicated work and party pages, and consistent layouts across GUI scales.
- Quest runtime and tracker now compile staged quest state, facts, scopes, triggers, transitions, provider bindings, failure states, and blocker reasons instead of assuming a single live-provider objective flow.
- The 21 built-in quests were migrated from the v1 resource shape to self-contained quest module v2 files while preserving stable quest IDs and legacy v1 pack loading, their existing objectives, dialogue, rewards, prerequisites, and presentation were reauthored for the staged runtime.
- Dialogue trees, forced dialogue, and quest dialogue now share localized text metadata, actions and conditions, payload codecs, dry-run diagnostics, and consistent reload, replacement, removal, and session-clearing behavior.
- The active-quest UI is now a tabbed active/completed/history interface journal with multi-tracking, persistent selection and scroll state, richer objective and reward details, and updated HUD highlighting.
- Skill Trade refreshes and Special Orders now use stricter server-authoritative request state, clearer readiness and refusal results, targetable schema validation, safer refresh replacement, and full Datapack Generator round-tripping.
- Trade option is now shown only for villagers with a trade-capable profession instead of presenting an unusable trade path for every adult villager.
- Villager AI suppression now uses a central priority policy so active retaliation, conversation, trading, following, hired work, party orders, combat, support, sleep, and vanilla schedules do not overwrite one another.
- Profession combat and support now use unified weapon and action state so compatible vanilla and modded villagers can switch melee, shield, bow, charged crossbow, trident, potion, support, and recovery behavior without stale goals or conflicting animations.
- Witness retaliation no longer treats every nearby villager as the same community: witnesses must share the harmed villager's village allegiance, and indirect damage resolves the actual attacker before the community response is chosen.
- "Follow Me" no longer uses a fixed `0.62` navigation speed: followers use vanilla villager walking speed (`0.5`) within eight blocks and Vindicator-equivalent running speed (`0.7`) beyond eight blocks.
- New-config defaults changed from Despised `-250` and Feared `-750` in beta.12 to Despised `-400` and Feared `-1000`. Existing Feared `-750` values migrate to `-1000`, every other stored value, including Despised `-250`, is left untouched.
- Normal villager conversation now accepts occupied hands when their items have no entity or air right-click behavior. Consumables, shields, equippable armor, projectiles, and modded use items keep their native action, while the beta.12 Clipboard plus beta.13 purpose-built items retain dedicated interaction handlers.
- Blanket rejection of baby-villager interaction was removed, babies can now use the conversation surfaces appropriate to them while adult-only trading, hiring, inventory, and combat actions remain gated.
- Profile and skill descriptions were rewritten to explain practical effects, practice gains, profession overrides, job aptitude, and progression more clearly.

### Fixed

- Fixed the Hearthbound feast request changing ingredient lists in quick succession; each quest now shows its full supply list up front, and follow-up quests can use a prerequisite cooldown before appearing.
- Fixed beta.12 skill-trade registration adding duplicate villager or wandering-trader offers when registration ran more than once.
- Fixed beta.12 skill-adjusted merchant XP losing or desynchronizing fractional carry after trades and offer refreshes, and fixed the merchant-screen preview drifting from the server award.
- Fixed the beta.12 interaction and gift screens double-firing into vanilla interaction or trading paths and sizing the gift inventory incorrectly at GUI scale 4.
- Fixed Chat Heads player icons disappearing after animated villager dialogue replaced the vanilla chat rendering path.
- Fixed beta.12 generic profession greetings and farewells leaking into reputation-specific dialogue pools.
- Fixed beta.12 disliked and hated gifts being accepted and removed from the player's inventory instead of being rejected.
- Fixed villagers interrupting beta.12 retaliation or fleeing to collect unrelated vanilla item drops.
- Fixed Bread Delivery, Village Lanterns, and Trial Chamber Recall allowing repeat turn-ins without consuming their required hand-in items.
- Fixed quest abandonment and expiration hooks being lost while their original provider was unavailable, deferred lifecycle work now survives saves and replays only after a valid provider return or explicit compatible rebind.
- Fixed beta.12 shared-story dialogue replacing the story's `{target}` structure placeholder with an unrelated remembered-retaliation target.

### Technical / Pack Development

#### Added For Pack Authors

- Added versioned JSON Schemas and registry metadata for `villagerretaliation:quest/v2`, `villagerretaliation:scene/v1`, encounter templates, skill trades, builder structures, and shared actor, provider, objective, trigger, condition, action, and scene-step registries.
- Added data-driven roots for currency, 293 sell prices, builder structures, village names, natural job armor, Player Raid loadouts, localized counted-item text, quest module v2 resources, quest scenes, and quest encounters.
- Added pack-extensible tags for village structures and terrain, allegiance holders, protected civilians, assignable mounts, currencies, ominous-banner equivalents, equipment, worker targets, and logistics matching.
- Added public scene and quest extension registries with explicit recovery and client-sync contracts, stable IDs, registry freezing, duplicate checks, and compiled source diagnostics.
- Added structured diagnostics, trace and explain commands, provider and lifecycle audits, objective and transition inspection, scene repair, village administration, hired-work previews, payload bounds, migration helpers, and example packs.
- Added save schemas and migrations for assignments, parties, villages, quests, scenes, encounters, raids, mounts, wallets, storage, filters, profiles, and completion history, plus bounded server-to-client payloads for their user interfaces.
- Added broad GameTest and lightweight regression coverage for worker roles, inventories, filters, transfers, parties, mounts, villages, allegiance, raids, duels, downed villagers, quest v1/v2 compatibility, scenes, encounters, dialogue, combat, rendering state, and save recovery.
- Added player-wiki, pack-wiki, README, JSON-reference, first-quest, scene-runtime, tracked-village, Builder, Sell Box, raid, model, localization, and example-pack documentation for the beta.13 surface.

#### Changed For Pack Authors

- Beta.12 v1 quest JSON remains supported through a compatibility adapter, while v2 is the maintained authoring target for new or intentionally migrated quests, stable quest IDs do not change.
- Shared conditions and actions now drive dialogue and quests. Older helper-heavy dialogue fields still load where documented, but conditions are the maintained replacement and authoring tools flag planned deprecations.
- The beta.12 Datapack Generator now preserves unknown and legacy pass-through content instead of silently rewriting it, and its beta.13 target can author quest, scene, encounter, dialogue, skill-trade, and other supported resources.

## 1.0.0-beta.12-hotfix.2 - 2026-06-06

Beta.12-hotfix.2 expands the beta.13 follow-up with broader gender support, new Village Supply quest content, operator gender correction tooling, and refined skill-based villager trade leveling with synced merchant-screen feedback.

### Added

- Added non-binary villager gender support across social data, breeding compatibility checks, profile/family UI labels, deterministic preset-name selection, and English localization.
- Added five new built-in Village Supply quests: `beetroot_bundle`, `bottle_stock`, `egg_baskets`, `feather_fletching`, and `torch_bundle`, each with authored quest definitions, dialogue trees, loot tables, and player-wiki coverage.
- Added `/villagerretaliation profile set_gender` so operators can set a villager's stored gender directly in-game.
- Added more preset villager names to the built-in male and female name pools.

### Changed

- Built-in quest totals documented in the README, CurseForge description, and generated player wiki now reflect the added Village Supply questline content.
- Villager gender chosen through the new command is now persisted on the villager so profile data and future loads keep the override.
- Added skill-based trade-level XP scaling driven by the villager's primary profession skill, including persisted fractional progress so slowed or boosted trade XP carries cleanly across trades.
- Merchant trade UI now displays the adjusted trade-level XP gain using synced villager profile data instead of showing stale vanilla-only values.
- Skill-based trade leveling now guarantees at least 1 visible trade-level XP from XP-bearing trades, matching the updated config tooltip and avoiding zero-looking progress on valid trades.

### Fixed

- Fixed skill-based trade-level XP progress desync between server-side villager leveling and the client merchant display.
- Fixed adjusted trade XP being lost between trades by syncing and caching the remaining fractional progress in villager profile payloads.
- Fixed merchant result and container flows bypassing adjusted trade-level XP presentation after a trade completed or when trade offers refreshed.

## 1.0.0-beta.12-hotfix.1 - 2026-06-02

Beta.12-hotfix.1 is a focused stability follow-up for beta.12. It fixes duplicate skill-trade registration and makes the villager interaction UI behave consistently across UI scale and text-scale settings.

### Changed

- Made villager skill-trade registration idempotent so repeated registration passes no longer append duplicate offers to villager or wandering trader trade pools.
- Reworked interaction-screen layout math to be scale-aware across profile, skills, gift, navigation, option-list, and quest-journal UI surfaces.
- Tooltip rendering now uses bounded, scale-aware positioning so item and component tooltips stay readable and remain on-screen at larger UI scales.

### Fixed

- Low-guts apology confrontations now interrupt only isolated attacks: followers cannot confront or retaliate against their player for witnessed crimes, and an already-hostile village or repeated player attacks and kills flow directly into retaliation instead of more forced dialogue. Directly attacking a follower still dismisses the betrayed villager as before.
- Fixed UI scale rendering issues for gui scale 1, 2, 4, and auto.
- Fixed Mac user UI rendering (i think lol).
- Fixed duplicate villager and wandering-trader skill trades caused by repeated trade registration against mutable or previously registered trade collections.
- Fixed gift-page hit detection, hover states, and button placement drifting out of sync when UI scale or text scale changed.
- Fixed profile-page and option-list hover/click regions using unscaled screen coordinates, which could cause incorrect selection and tooltip behavior at non-default scales.
- Fixed interaction-screen tooltips rendering off-screen or at inconsistent positions when the UI was transformed or scaled.

## 1.0.0-beta.12 - 2026-06-01

Beta.12 is a major beta.11 follow-up focused on villager profiles, skills, quests, skill-trade restocking, and datapack authoring cleanup. It is also a manual datapack-retargeting release: beta.11 packs should keep targeting the beta.11 wiki snapshot until they have been reviewed against the beta.12 pack surface.

### Added

- Added persistent villager profiles with Social Attributes (Knowledge, Guts, Proficiency, Kindness, and Charm), skill data, profession-aware generation, profile/skill sync payloads, client caches, and Profile/Skills pages in the interaction screen.
- Added profile and skill debug commands under `/villagerretaliation profile ...` and `/villagerretaliation skill ...`.
- Added temporary villager moods and Social Attribute dialogue filters for normal dialogue lines: `mood`, `moods`, `min_mood_intensity`, `requires_high_*`, and exact `min_*` / `max_*` score ranges.
- Added data-driven quests with `quest_action` dialogue options, start/remind/turn-in/abandon actions, quest progress tracking, target/proof checks, rewards, notifications, active/expiration rules, and quest-triggered tracker or forced-dialogue actions.
- Added 21 built-in beta.12 quest content across Dangerous Commissions, Lost Civilization, Old Roads, Village Defense, and Village Supply questlines, with authored dialogue trees, quest-specific loot tables, offer gates, proof items, and balanced reputation/XP rewards.
- Added quest item requirements and turn-in hand-ins, so quests can require extra delivered items in addition to target proof.
- Added Quest Journal and Quest Tracker keybinds, smooth-scrolling quest UI, tracker HUD controls, quest debug tools, target dimensions, hint text, and shader outlines/highlights for quest items.
- Added data-driven dialogue trees for branching villager scenes, including display metadata, narrative metadata, leave options, generic villager actions, shared quest actions, tracker flashes, and forced-dialogue handoffs.
- Added individual trade-slot refresh requests for skill-generated villager trades. Random refreshes mature on the next Minecraft day and can open data-driven ready follow-up/interjection dialogue before the trade menu opens.
- Added high-reputation Special Orders for directly requesting targetable skill-trade definitions, including wait times, cooldowns, extra costs, up to three active requests per villager/player, status dialogue, and ready-order fulfillment.
- Added beta.12 dialogue authoring features: `conditions` blocks on normal dialogue lines and options, line `priority`, line `category`, and `text_key` message indirection.
- Added path-aware folderized dialogue loading under `dialogue/<locale>/...`, including typed `options`, `lines`, `messages`, `openings`, `closings`, and `pacify` folders, optional `type` in option files, profession defaults from paths, and namespaced custom profession paths.
- Added `/villagerretaliation dialogue explain` and `/villagerretaliation datapack diagnostics` for in-game dialogue and datapack debugging.
- Added beta.12 Datapack Generator support for the new target, folderized dialogue import/export, versioned wiki snapshots, and the downloadable dialogue folder template.
- Added a player-facing static wiki under `tools/player-wiki` with generated quest walkthroughs, rewards, gifts, reputation, skill trades, watched-container guidance, advancements, search, and command/keybind references.
- Added persistent per-villager/per-player last-seen memory with absence-aware opening dialogue placeholders: `{days_since_seen}`, `{day_or_days}`, and `{days_since_seen_phrase}`.
- Added persisted village event and village encounter memories so dialogue can react to recent hostile events and prior village visits without rescanning every interaction.
- Added recruitment left-behind follow-up dialogue and biome filters through `recruitment_memory_biome` / `recruitment_memory_biomes`.
- Added data-driven watched-container resources and a generated-container allowlist for pack-controllable village chest confrontation behavior.
- Do not talk to Edmundo if you are named LoudLitten

### Changed

- Switched the project to the NeoForge-only layout and moved the interaction UI stack onto ToucanLib-backed screens, scrolling, camera behavior, shader chrome, animated dialogue text, and profession-colored panels.
- Built-in dialogue resources now use the beta.12 folderized layout under `dialogue/<locale>/global`, `groups`, and `professions/<profession>` instead of the previous large bundle-style authoring shape.
- Built-in normal dialogue lines and family/relationship options now use `conditions` where practical while keeping the beta.11 helper fields as compatibility inputs.
- Normal dialogue selection now applies explicit `priority` tiers before weighted random selection. Existing packs keep the default `priority: 0`.
- The Datapack Generator version selector is now a target selector only. The beta.12 target no longer attempts beta.11-to-beta.12 conversion.
- First-conversation and first-village opening lines now respect persisted seen-memory after world leave/join cycles.
- The default diamond-sword proximity forced-dialogue witness radius was reduced from 8 blocks to 4 blocks.

### Fixed

- Fixed Special Order active-request limits so the fourth request shows the limit dialogue immediately and the cap is hard-clamped to three.
- Fixed multiple ready random refreshes so all accepted ready refreshes can fulfill in one pass instead of leaving completed requests behind.
- Fixed vanilla villager trade-preview hand behavior causing nearby players holding trade costs to replace, duplicate, or drop held villager items.
- Fixed inconsistent natural hostile detection paths between villagers and wandering traders by making both use the same shared hostile-mob classification rules.
- Deferred first-load social graph profile warmup across staggered villager ticks, reducing join-time TPS spikes when teleporting into villages that were first loaded after installing the mod.
- Reduced repeated smith-villager repair path recalculations around damaged iron golems, especially when the golem is nearby but not immediately reachable.
- Throttled natural-hostile targeting eligibility checks so stand-ground and nearby-weapon lookups no longer run every tick for every villager.
- Throttled shared dialogue scans, story-discovery scans, and village-event check-ins with lazy memory/status caching so routine conversations no longer repeatedly rescan the same nearby world state.
- Skipped passive combat cleanup work for villagers that have no active armorer, ranged, temporary-weapon, or borrowed-weapon state.
- Reduced the default follower lost distance from 64 blocks to 32 blocks.
- Reduced idle villager tick overhead by staggering persisted-anger restore probes, passive combat-survival checks, and no-op trade-pool checks.

### Removed

- Attempted to remove Edmundo
- Removed the Datapack Generator's beta.11-to-beta.12 Convert workflow. Beta.12 is a manual retargeting boundary, not a marker-only migration.
- Removed quest compatibility aliases and advancement-style `criteria` / `requirements` inference from the quest loader.
- Removed Edmundo
- Removed beta.11-only quest/action compatibility shapes from the beta.12 quest surface. The maintained action fields are `type`, `quest`, `action`, `amount`, `memory_event`, `loot_table`, `notification`, `text`, `forced_dialogue`, and `flash_tracker`, documented shorthand such as `xp`, `rep`, `notify`, and inline unique action fields still load where the shared action parser lists them.
- Removed top-level dialogue metadata aliases from maintained beta.12 dialogue and dialogue-tree authoring.
- No beta.12 runtime JSON fields, triggers, or placeholders are removed solely because of the folderized dialogue layout.
- Edmundo came back
- Removed Edmundo (i think)

### Migration Notes For Pack Authors

- Keep existing beta.11 datapacks on `villagerretaliation.pack_version: "1.0.0-beta.11"` until you have manually reviewed dialogue, forced dialogue, notifications, quests, and skill trades against the beta.12 wiki.
- Do not migrate by only changing `villagerretaliation.pack_version` to `1.0.0-beta.12`. The marker selects the editor/runtime target, it does not reorganize dialogue files, audit ids, update compatibility fields, or validate new beta.12 behavior.
- Prefer new beta.12 dialogue packs under `data/villagerretaliation/dialogue/<locale>/global`, `groups`, or `professions/<profession>`, using typed folders such as `options`, `lines`, `messages`, `openings`, `closings`, and `pacify`.
- Treat `options`, `lines`, `messages`, `openings`, `closings`, and `pacify` as reserved section folder names below `dialogue/<locale>/`.
- Split old monolithic dialogue files by ownership and purpose. Keep bundle files only when several related entries are easier to maintain together.
- Review intentional overrides carefully: Minecraft resource replacement still happens by exact resource path before Villager Retaliation merges entries, so copying an old built-in monolith can replace more beta.12 content than intended.
- Use `conditions` for new memory, family, relationship, recruitment, quest, and event-gated dialogue. The older flat helper fields still load in beta.12 but are planned for beta.13 deprecation.
- Convert quests to the canonical beta.12 schema before testing: `criteria` / `requirements`, singular `profession`, array-form `skills`, `loot`, `memory`, and `once` are no longer accepted by the quest loader.
- Convert quest and dialogue-tree actions to canonical beta.12 fields before testing: use `type: "quest"` plus `action: "start" | "remind" | "turn_in" | "abandon"`, `amount` for numeric rewards, `memory_event`, `loot_table`, and `forced_dialogue`.
- Put dialogue narrative metadata under `metadata` instead of top-level `topic`, `tags`, `questline`, `quest`, `stage`, or `notes`.
- Use `priority` when one matched line should reliably win, and use `weight` only to tune random odds inside the same priority tier.
- Use `text_key` when translators should replace message text without copying the full line filters.
- For skill-trade packs, use `request.targetable: true` plus request metadata only for trades that should appear as Special Orders. The queued order stores the trade definition id, so later cost/result/config changes affect future fulfillment.
- For trade-refresh dialogue overrides, review the `trade_refresh.*` message keys and forced-dialogue entries in the beta.12 wiki instead of copying beta.11 dialogue files forward.
- See [Pack Format Changes](wiki/Pack-Format-Changes.md), [Dialogue JSON](wiki/Dialogue.md), [Forced Dialogue JSON](wiki/Forced-Dialogue.md), [Skill Trades](wiki/Skill-Trades.md), and [Quests](wiki/Quests.md) for the full pack-facing migration surface.

## 1.0.0-beta.11-hotfix.1 - 2026-05-26

### Changed

- Villagers no longer generate arbitrary profession weapons when attacked. They now fight with weapons they already hold, weapons stored in their inventory, or eligible weapons they can pick up from the ground.
- Villagers can now swap a held non-weapon item with an inventory weapon for combat, then restore the held item after the borrowed weapon is returned.
- Villagers with a held non-weapon can now pick up eligible ground weapons, the held item is stored first and only drops if storage is truly full.
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
- Added VR pack-version support to the datapack builder, beta.11+ exports write `villagerretaliation.pack_version` in `pack.mcmeta`, and imports use it to restore the target generator version.
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
- General player-selected conversation now uses `question`, `small_talk` is no longer a separate request.
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
- Removed request values from dialogue option `type`, dialogue options now use `type: "dialogue_option"` plus `request`, and dialogue lines use `request`.
- Removed `_chat` forced-dialogue triggers. Use the normal trigger with `output.mode: "chat"`.

### Notes For Pack Authors

- See [Pack Format Changes](wiki/Pack-Format-Changes.md) for the pack-facing migration log.
- See [Forced Dialogue JSON](wiki/Forced-Dialogue.md) for the full forced-dialogue schema.
- See [Dialogue JSON](wiki/Dialogue.md) for reputation-gated dialogue options and lines.
