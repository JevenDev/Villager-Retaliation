# Changelog
- Hardened multiblock assigned storage so either half of a double chest shares assignment lookup, removal, route tethering, visit tracking, cooldown clearing, and courier pickup without relinking.
- Fixed severe integrated-server stutter from output backpressure checks repeatedly rescanning assigned containers and nearby item frames for every courier inventory slot.
- Added hired-work output backpressure: workers pause without warning when every assigned output is full or filter-capped, resume automatically when capacity returns, and couriers preflight and collect only the downstream allowance.
- Fixed animal handlers and fishermen leaving excess work experience orbs behind after their Mending equipment reached full durability; they now attract and consume the remaining work XP without retaining it.
- Added clipboard actions to remove only selected container or payment-box assignments and to change selected assignments to the clipboard type.
- Fixed long-route branch couriers skipping unloaded branch containers by prioritizing storage near the active branch within the bounded chunk-ticket window.
- Fixed terminal branches being traversed twice at route reversal, gave overlapping branch storage priority at branch endpoints, and extended active yellow route highlighting onto branches.
- Added clipboard Branch Mode for one-node route extensions, including light-blue normal and debug previews and courier input and output servicing along branch endpoints.
- Wired branch anchors and endpoints into the courier traversal as ordinary out-and-back route nodes, so branch containers use the established route-node servicing path.
- Fixed newly added branches disappearing from the clipboard preview until the route was assigned to a villager.
- Fixed long-route couriers stalling at the endpoint after an unavailable output sweep by retracing route nodes before retrying from the start.
- Improved courier node stops so they visit every eligible assigned container tethered to that node before returning to the route.
- Added cooperative villager traffic handling: villagers approaching the same narrow passage or route now use stable right-of-way, briefly queue when blocked, and sidestep when nearby terrain provides a safe passing space.
- Fixed checkbox and icon option labels being clipped instead of wrapping, and added an "Assign and Keep Selection" clipboard action for reusing selected containers across villagers.
- Fixed party follow reconciliation repeatedly restarting an active follow command and interfering with combat navigation, which could cause extreme movement-speed spikes after retaliation began.
- Added each party villager's gender above their profession in the inventory name-hover tooltip.
- Fixed mounted party villagers having their Move To horse route canceled as a stay order; they now hold at the destination and automatically regroup when the commander returns within three blocks.
- Fixed unscoped profession greetings and farewells bleeding into reputation-specific opening and closing pools.
- Fixed hired-work checkbox options being rejected by the recruitment trust gate instead of reporting the updated setting naturally.
- Fixed currency gift names such as Emerald appearing lowercase in the known-gifts tooltip.
- Fixed disliked and hated gifts being accepted or consumed instead of being rejected while still applying their negative reputation.
- Fixed empty villager hunger being harmless; villagers now take player-equivalent starvation damage based on world difficulty.
- Removed accidental two-line dialogue combinations introduced during the dialogue expansion while preserving the individual authored lines.
- Fixed the Stay Here interaction remaining locked for a villager's active hirer when their reputation was below Trusted.
- Added composable villager downed-state settings for universal, raid, hired, and party eligibility, plus independent player, mob/entity, and environmental lethal-damage filters.

- Added villager hunger to the overhead reputation debug view and made the vanilla Hunger status effect drain villager hunger through a default-on balance option.
- Fixed normal conversation openings that could stitch two separate greetings together.
- Fixed awake hired villagers refusing to follow during the vanilla rest schedule; follow navigation now clears stale Brain pathing state and retries the owner's coordinates before falling back to entity pathing.
- Reverted the clipboard-wide state reset introduced in `af9892fd`; applying a work-area draft now clears only that draft and preserves the selected clipboard mode and other clipboard state.
- Fixed the entity-specific interaction guard intercepting clipboard, construction-blueprint, and item-filter interactions before their dedicated villager handlers could open.
- Routed payment-box assignment through the same villager clipboard menu as every storage assignment mode, added 20 unique opening lines before a choice is made, and replaced doubled assignment-result text with single unique villager responses.
- Fixed stale assignment-only villager data making a failed hire appear successful, leaving the apparent hirer unable to issue follow commands or assign storage and courier routes.

- Rebalanced couriers to collect a separate skill-scaled allowance from every assigned input container: one guaranteed stack plus up to one bonus stack, capped at two stacks per container.

- Refined courier routes so assigned input and output containers tether to their nearest route node within 16 blocks, use node-to-container-and-back detours, collect from later inputs consistently, and retrace route nodes on the return trip.

- Moved remaining player-facing party-adjacent, interaction, quest, inventory, clipboard, village-naming, map, and raid text into localization entries.

- Fixed the dialogue gift inventory shrinking independently from the interaction container at GUI scale 4.

- Added GUI scale 4 support for the player party inventory, centering the player inventory and its adaptive left-side villager panels together as one group.

- Added optional EMI compatibility so recipe/item panels wrap around villager inventory tabs and expanded party inventory panels instead of covering them.

- Added a live hired-contract countdown beside the villager timer stat, with a full remaining-time tooltip in villager and party inventory views.

- Fixed duel participants transferring items during a fight: player drop input is now rejected before inventory removal, and both players and villagers are prevented from picking up or dropping world items for the duration of the duel.
- Fixed assigned duels allowing party villagers to pull weapons or ammunition from party/job inventory; duel combat now isolates its live loadout from every external work-inventory read, write, synchronization, and equipment-maintenance path.

- Added a default-on Player Raid horn confirmation. The first horn use warns the player in chat, and a second use by the same player in the same village within 30 seconds declares the raid.

- Fixed recruited-villager retaliation limits ignoring high reputation; Revered recruits now allow five hits before disciplining the player, while Royalty recruits retain their retaliation bypass.

- Improved heavy clipboard and hitbox debug-preview performance by culling off-screen containers, job sites, routes, markers, and text; limiting worst-case visible route and label work; and caching terrain-following route geometry between frames. The visible node, label, and route-segment limits are configurable in the Debug Overlay settings.

- Hidden Job Inventory and Storage job-menu entries until the player has hired that villager; former hirers still see Job Inventory while reclaimable contract gear remains.
- Job-inventory stacks deposited into assigned storage after completed work or contract expiry now shed their job metadata so they merge with identical normal items.

- Added item-frame filters for courier output storage. Attached frames apply automatically: couriers prioritize framed containers for matching cargo, route other items to other outputs, and treat both halves of a double chest as one filtered container. A Villager Item Filter displayed in a frame now applies its configured allowlist or denylist to that output chest. Clipboard storage previews now outline a connected chest with one combined box for clearer feedback.

- Fixed hired party villagers duplicating party-owned weapons and armor into personal inventory while attempting to scavenge ground upgrades; authoritative party equipment now rejects both path selection and stale pickup completion.
- Fixed recruited and hired villagers sometimes opening the vanilla trading menu; controlled contract states now consistently suppress trading.

- Villagers in combat can now pursue and equip stronger dropped armor, preserving displaced pieces in their personal inventory when space is available.
- Improved dropped-weapon scavenging so villagers prefer melee weapons over ranged weapons, choose the strongest eligible nearby upgrade before distance, and revalidate drops at pickup time.

- Added The Mark You Chose advancement for starting a villager conversation while displaying the ominous banner on a worn helmet or held shield.

- Added automatic Totem of Undying handling for adult villagers. Carried totems now take priority in the off-hand unless a player explicitly assigned that slot, replenish after use, and remain equipped while party villagers raise a stored shield from the main hand.
- Expanded Raise Shields combat behavior so guarded villagers swap to melee weapons only for in-range attacks and raise the shield again afterward, while bow and crossbow users retain their weapon through each complete shot cycle and guard between shots.

- Prevented gift reputation farming with persisted per-player/per-villager daily caps, sharply reduced gains from repeated stacks of the same item, and a server-side gift request cooldown. Added server config controls for all three limits.

- Reworked hired-job qualification around two relevant skills: ordinary roles now require a cumulative total above 60, while canonical vanilla professions automatically qualify for their matching role. Courier remains universal for adults, Nitwit remains profession-restricted, and Builder remains a one-off service.
- Added skill-based hired-work throughput. Weighted 70/30 role aptitude now scales worker cadence, block work, fishing waits, hired attack recovery, courier cargo, and facility collection trips without changing vanilla workstation processing timers.
- Added a third Job Stats interaction view with role readiness, qualification progress, profession overrides, aptitude, work speed, transfer capacity, and contributing-skill details.
- Successful hired work now preserves each action's practice budget while splitting it between the role's primary and support skills at 70/30.

- Fixed villagers attempting to collect seeds and other unrelated item drops while fighting or fleeing; combat threat state now suspends vanilla, hired-farming, and party drop pickup so higher-priority behavior keeps control.

- Fixed defending Player Raid villagers recognizing only their current player target as hostile; every participating raider player now remains an aggressor while defenders fight other party players or recruited villagers.
- Fixed hired villagers rejecting Follow Me and Stay Here commands from their own hirer as though another player held the contract, then clearing or interfering with accepted follow routes; hired work now pauses before follower navigation begins and yields to the ordinary follow AI while they follow or hold position, while commands from other players remain blocked.
- Prevented a Ride On rear passenger from damaging an allied front-seat driver, covering players and villagers, melee, projectiles, indirect damage, mutual parties and alliances, vanilla teams, shared village allegiance, and active hirers.
- Fixed recruited villagers duplicating party equipment into their personal inventory when party gear replaced a temporarily borrowed personal weapon; party/job main-hand equipment now retains sole authority.
- Fixed party villager contracts locking the recruiter out of leftover supplied gear after expiry, dismissal, party disbanding, or raid-driven release; the recruiter now receives the existing three-day job-inventory claim window.
- Fixed completed Player Raids allowing the same village to be raided again after a player victory or relog; the configured village cooldown now persists after either outcome.
- Fixed permanently removed defenders leaving Player Raid boss bars stuck at one, and added a disabled-by-default debug option to make loaded tracked defenders glow.
- Fixed damaged Mending armor and held equipment on villagers, including fishing rods, not attracting and absorbing nearby experience orbs.
- Restored hired-fishing catch experience for fishermen with an equipped Mending fishing rod while keeping catch experience disabled for ordinary rods.
- Fixed builders showing a missing-storage warning when they already carry the materials needed to continue building.
- Fixed couriers stopping when assigned input storage was empty; they now keep patrolling their route and collect items that appear at later inputs.
- Fixed unemployed recruited villagers claiming job-site professions while actively in a party; rejected job sites are released and can be claimed normally after the villager leaves the party.

- Fixed villagers created during village world generation being permanently marked as Wanderers before their village POIs and footprint became available; initial allegiance discovery now uses the bounded retry queue until the surrounding observation is complete.
- Reworked assigned mounts to use Minecraft's native mob-jockey control path without requiring a companion mod, while optionally integrating with Ride On 1.0.0-pre-release.3 or newer for two villagers or a player and villager on one mount.
- Added coordinated Ride On seat transitions: the front passenger controls the mount, rear villagers do not compete for movement or rotation, authorized players can take the driver seat even when both seats are occupied, and assigned villagers promote and remount after the player leaves.
- Fixed mounted party followers retaining only their last regroup route; ordinary follow now keeps the villager mounted, refreshes the horse's route against the moving leader, and uses full horse speed with catch-up scaling.
- Lowered mounted villagers into the saddle using vanilla's humanoid mob-rider offset instead of leaving them hovering above horses, mules, and donkeys.
- Mounted villagers now walk at normal mount speed within eight blocks of their leader or movement target and switch to the mount's catch-up sprint beyond that range.
- Fixed mounted villagers retaining combat targets without landing melee attacks; saddle combat now measures reach from the controlled mount's body while the villager remains the attacker.
- Fixed mounted ranged villagers failing to complete crossbow attacks; committed shots now reserve their projectile, retain the crossbow through the full charge/load/fire cycle, and synchronize the loaded stack with the job inventory.
- Added persisted one-to-one mount assignments, party and hired-worker controls, long-distance mounted travel, precise-work dismounting, unload/remount retry behavior, parking anchors, and cleanup for terminal villager, mount, and contract lifecycle events.
- Added GameTests for single-seat assignment, vanilla controlling-passenger selection, rider-to-horse navigation delegation, travel, parking, lifecycle cleanup, and remount behavior.
- Kept authorized player takeover for saddled assigned mounts, with automatic villager remounting after the player yields the horse.
- Added chat-command party membership and mutual alliances under `/villagerretaliation party`; allied players and recruited villagers no longer target or retaliate against one another.
- Added a mercy stage to Player Raids: after armed defenders fall, raiders can right-click snapshotted babies and nitwits to spare them, leave them for a manual kill, or say nothing. Spared villagers survive with exactly `-1000` reputation toward every raider player, while nearby unresolved survivors occasionally plead for their lives.
- Added 15 victory reactions and 15 loss reactions for recruited party villagers at the end of Player Raids.
- Added built-in optional Second Wind compatibility for every VR-protected villager. Second Wind can channel an early player revive, while VR remains authoritative and villagers continue to recover automatically instead of bleeding out.
- Added a persisted fourth downed presentation using Second Wind's crawl posture alongside sitting, side-lying, and hands-and-knees variants.
- Prevented a held Second Wind revive input from immediately opening the recovered villager's interaction menu.
- Kept widened downed-villager hitboxes out of adjacent solid blocks, including a compact-pose fallback for tight spaces.

- Added party quick commands that send participating villagers to gather nearby ground drops or loot containers around the ping location; Move To and Regroup supersede either gathering order.
- Made active drop-gathering and container-looting orders override villager combat targeting, and placed the command wheel directly below the vanilla chat layer so translucent chat no longer masks it out.
- Fixed shared-story dialogue resolving the location placeholder `{target}` as a recent retaliation target, such as calling a Pillager a place instead of naming the Pillager Outpost.
- Tightened village footprints so POI padding stays horizontal and tagged paths extend bounds only when their actual blocks connect, eliminating empty sky/depth sections and unrelated section-level path bridges.
- Made generated village names additive and data-driven through `data/villagerretaliation/village_names/`, and expanded the built-in prefix/suffix pool from 1,944 to 7,998 possible combinations.
- Reworked village homes: spawned villagers join only when created inside a village, outside spawns become Wanderers, Wanderers settle after one continuous day, party members do not settle automatically, and Revered same-party players can explicitly choose a party villager's home.
- Reworked newborn allegiance so birthplace takes priority, while babies born outside a village inherit the first parent's known home; replaced the technical Allegiance readout with player-facing questions and villager dialogue about home and local belonging.
- Optimized village-home maintenance with indexed section lookups, priority-scheduled villagers and uncertainty retries, hard per-tick work budgets, O(1) same-village residency checks, and staggered lifecycle scans instead of periodic full entity sweeps.
- Improved village allegiance assignment with durable uncertainty retries, evidence scoring, parent-aware inheritance, protected mixed parentage, shrinkable current footprints, conservative automatic merges, merge recovery, residency-based reassignment, confirmation prompts, bounded assignment history, stale-roster filtering, and explain/repair diagnostics.
- Added global and per-villager party combat modes for kill on sight, attack with party, or self defense, plus target modes for animals, hostiles, players, other parties, or all targets. New parties and recruits default to attack with party.
- Added a server-authoritative downed state for protected villagers. Active party members, quest-v2 providers, protected scene actors, and villagers tagged `villagerretaliation_essential` survive ordinary lethal damage, suspend AI and interactions, and recover after a configurable quiet period.
- Added quest-v2 provider `death_protection` policies (`none`, `while_active`, and `after_start`) plus scene-v1 actor `lethal_damage_policy` values (`normal` and `downed`), including generated schemas, durable state, client synchronization, and an incapacitated whole-body pose.

- Hardened persistent quest scenes with player-scoped and shared-party run IDs, immutable startup ordering, durable legacy operation aliases, exact overall deadline wakes, centralized failure/cancellation policy transitions, uniform terminal cleanup, persisted `wait_for_result` continuations, typed quest transitions, and bounded terminal tombstones.
- Added regression coverage for scene resource diagnostics, repeatable and party run identity, repeated legacy migrations, operation reuse, waiting/blocked deadlines, policy recovery, cleanup queuing, continuation reload/outcomes, quest-transition self-cancellation, and replay-safe history compaction.

- Added receipt-guarded per-wave, phase, and completion encounter rewards, named trophy items, validated loot-table grants, and retry-safe normal/suppress/authored-only/trophy-only mob drop policies.
- Added participant-only, dimension-aware encounter navigation with coordinate privacy, durable discovery and arrival, distance and compass tracker values, temporary HUD markers, directional particles, and terminal cleanup.
- Added bounded encounter-owned environmental cues and temporary blocks, including participant sounds/music, particles, glowing columns, persisted block ownership, reload-safe reconciliation, and cleanup that never overwrites later player edits.
- Added deterministic weighted encounter variants on selector templates and `start_encounter` steps, with persisted seeds, selected IDs, resolved templates, recursive-reference diagnostics, scene branches, tracker placeholders, and operator inspection.
- Added datapack-authored encounter failure handling for player or protected-actor death, with bounded fail, wave reset, full restart, timed pause, and receipt-guarded scene-branch actions plus durable attempts, deadlines, and retained progress.
- Added controlled encounter allies from entity definitions or bound scene actors, with separate durable identity, survival, invulnerability, revival, replacement, completion-gating, targeting, and cleanup policies.
- Added composable `all`/`any` encounter completion objectives for survival, protection, entry defense, escorts, target destruction, leader defeats, item retrieval, area control, and legacy enemy-clear conditions, with durable tracker and operator state.
- Added durable mid-fight encounter phases triggered by waves, remaining enemy percentage, elapsed time, or named elite defeats, with receipt-guarded notifications, dialogue, fact sets, and scene transitions.
- Added named encounter spawn points sourced from scene actors, marker aliases, or explicit coordinates, with durable random, sequential, weighted, participant-distance, and member-group selection.
- Added allowlisted elite and boss encounter members with safe names, visibility, glow, persistence, bounded combat attributes, correct post-initialization health, and participant-only reload-safe boss bars.
- Added explicit raid-wave compositions with stable wave IDs, per-wave members, delays, triggers, boss-bar titles, equipment defaults, and durable participant hooks while retaining `members` plus `wave_count` shorthand.
- Added optional, durable encounter areas with bounded horizontal and vertical radii, participant `ignore`, `warn`, `pause`, and `fail` leave policies, and owned-mob `ignore`, `return`, and timed `teleport` policies.
- Added datapack-authored encounter equipment, enchantments, drop chances, and `group`, `near_player`, `fixed`, and persistent `raid_waves` spawn modes.
- Added participant-only raid-wave boss bars, enabled by default and configurable with the encounter template's `boss_bar` field.
- Updated Standing Watch into a two-wave village defense, moved Night Run's attack to a surfaced destination away from its quest giver, and made the remaining built-in encounter placement modes explicit.

## Unreleased

- Consolidated hired ownership, commands, roles, anchors, lifecycle timestamps, schema, and state revisions into a canonical villager assignment; recruitment now validates stale state and returns authoritative success/failure snapshots instead of optimistically toggling the client.

### Fixed

- Fixed recruited party villagers failing to target defending iron golems during active Player Raids; the Villagers attack mode now includes iron golems.
- Fixed active Player Raids becoming difficult to finish when a remaining defender is hidden or invisible; banner-helmet raiders can now reuse a goat horn to reveal nearby tracked defenders.
- Fixed the party attack quick-wheel losing or rejecting its crosshair target, including Player Raid defenders excluded by a villager's normal attack mode.
- Fixed outside-spawned recruited villagers incorrectly inheriting a nearby village's grievance and confronting their own party leader.
- Fixed villager trade-level XP previews drifting from the actual award during rapid or shift-click trading, including when server multiplier settings differ from the client.
- Fixed low-XP trades consuming fractional trade-level XP banked by earlier trades without awarding that progress.

### Added

- Reworked villager skill growth around successful practice: hired workers now learn only from measurable completed actions, larger jobs train more than trivial ones, repeated equivalent work has diminishing returns, and higher skill levels take longer to improve. Existing villager skill values are preserved.
- Added bounded soft loading for assigned payment boxes when automatic contract renewal is due; payment chunks load at FULL-only status without block or entity ticking, release immediately after charging, and use short timeouts plus per-renewal and server-wide rate caps.
- Added `/villagerretaliation debug raid win` and `/villagerretaliation debug raid lose` operator commands for settling the relevant Player Raid.
- Expanded personal, job, and party villager inventories with nine-slot hotbars; assigned job tools now prefer mainhand then hotbar storage, while ordinary supplies, outputs, and party drops use the hotbar only for overflow.
- Added durable POI-backed village identities with generated names, canonical aliases, resident rosters, automatic connected-footprint merging, loaded-only empty-grace observation, and archival after 72,000 fully observed ticks.
- Added permanent villager and natural-golem allegiance, neutral Wanderers for outside spawns, lazy v1 reset-on-load migration, conversion handling, and trusted reassignment from the Allegiance interaction page.
- Added allegiance-aware combat and community response: recruits can fight Wanderers or foreign villages without the victim having a party, same-party and same-village damage is blocked, actual landed damage rallies only the victim's village, and foreign golems become reactive defense targets.
- Added banner-on-bell village naming with server-side validation and a Revered-or-Royalty gate covering at least half of tracked living adult residents; operators can bypass the trust gate.
- Added `debugOverlay.showVillageBounds`, a bounded subscription-based POI-section outline preview with canonical village names shown at the top center of the HUD while inside a synchronized footprint.
- Added allegiance village administration commands for inspecting, listing, and renaming tracked villages.
- Expanded village footprints to include every tagged village structure piece and connected tagged terrain such as vanilla dirt paths, with datapack tags for modded village structures and terrain blocks.
- Added separate horizontal and vertical excavation orders for hired miners. Horizontal excavation uses five-block reach, cuts and later removes temporary access stairs for tall spaces, avoids ladder shafts, and can patch unsafe floors with mined or user-supplied blocks.
- Added 15 repeatable village commissions spanning early, mid, and late game, with distinct gathering, trading, building, mining, combat, structure-survey, Nether, End, Ancient City, and Wither objectives.
- Added five four-quest branching questlines: Green Thumb, Deep Delvers, Redstone Works, Nether Routes, and End Survey. Each line records a player choice, provides two playable stage routes, and unlocks a different fact-gated finale.
- Added dedicated built-in quest-content validation for title length, repeatable dialogue variation, hand-in consumption, cooldowns, loot references, parent graphs, stage reachability, objective predicates, terminal turn-ins, choice routes, branch children, and expansion objective uniqueness.

### Changed

- Rebalanced skill-based trade leveling to scale linearly across a villager's profession skill, giving average-skilled villagers fairer progress while retaining the configured minimum and maximum multipliers.
- Upgraded ten built-in quests with persistent scenes. Standing Watch, The Night Ward, The Night Run, and The Atlas Test's risky route now use controlled cleanup-safe encounters; Choose the Horizon, Choose a Road, and Choose a Star use choice-aware scene branches; and Trial Chamber Recall, House of Ill Omens, and After the Roar receive durable cinematic briefings.
- Rewrote the remaining built-in v1 quests as self-contained quest module v2 resources while keeping their stable quest IDs, rewards, provider requirements, completion triggers, and saved quest identity.
- Replaced repeated template dialogue with concise, Minecraft-specific offer, acceptance, reminder, decline, abandonment, and turn-in variations.
- Rebalanced Trial Chamber Recall to consume its Trial Key and Breeze Rod, reduced its excessive repeat payout, and made every structure/proof requirement explicit in the quest stages.
- Rebalanced Gilded Debt so completing the village's agreement grants positive gossip instead of an unexplained penalty.
- Renamed overlong or awkward display titles to Stronghold Eye, Dark Roof Ink, Lost Civilization, and Choose the Horizon. All built-in quest titles now use one to four words.
- Expanded the built-in catalog from 50 to 85 quests and regenerated the player-wiki quest data.

### Fixed

- Fixed controlled quest scenes becoming operator-blocked or leaving provider-locked quests stranded when the issuing villager died during the authored attack. Combat scenes now retain their saved anchor, skip unavailable presentation safely, and allow a compatible quest giver to finish the work.
- Fixed `start_scene` actions rejecting the documented `scene` field even though the quest authoring tools and example pack emit it.
- Fixed provider-bound abandonment and expiration hooks being lost when their issuing villager was unavailable. Deferred lifecycle events now survive saves and replay once after provider return or an audited compatible rebind.
- Fixed Bread Delivery, Village Lanterns, and Trial Chamber Recall allowing repeat turn-ins without consuming their required items.
- Fixed End City Survey lacking a parent link to Lost Civilization.
- Fixed the dialogue validator rejecting the live work-status placeholders `{cap}` and `{types}`.
- Fixed miners forcing direct ladder entries through blocked corners, abandoning persisted shafts after impossible obstructions, and stalling while extending a shaft beneath an existing ladder.
- Fixed excavation safety state around unloaded chunks so unknown fluid faces remain blocked and permanent hazard seals are not forgotten while their chunks are unavailable.

## 1.0.0-beta.13 - 2026-06-21

The Hired Help update turns recruitment into a full hired-worker system with contracts, job roles, assigned storage, work areas, payment boxes, wallets, and workforce management, while also expanding quests, dialogue authoring, villager AI, UI, and pack-development tooling.

- Added the Smelter hired role. Smelters use furnaces or blast furnaces inside their work area, retrieve raw iron, copper, or gold and fuel from job supplies or assigned storage, and deposit finished ingots as job output.
- Added the universally available Courier hired role, including for unemployed villagers. Couriers collect up to 64 items per trip from assigned input storage, follow their required route to assigned output storage, deposit the delivery, and follow the route back for another load.
- Storage and payment-container assignments can now be shared by multiple hired villagers without one villager removing another villager's assignment.

### Added

- Added hired villager contracts with role and duration selection, reputation/skill-based daily pricing, early-end refunds, recurring payment handling, unpaid/loaded-chunk status tracking, and dedicated hire menus.
- Added hired roles for Combat, Mining, Logging, Farming, Fishing, Brewing, Builder, Animal Handling, and Nitwit, with profession preferences, skill unlock thresholds, role scoring, optional skill growth, and configurable efficiency.
- Added hired worker sessions, work plans, focus tracking, bounded work areas, path reservations, blacklisting, stall detection, return-to-area behavior, storage navigation, and batched target scans so jobs can run with bounded server work instead of constant world scans.
- Added split villager inventory management with Personal and Job views, job equipment slots, protected worker gear, supply and output slots, full-output handling, assigned output storage, and safeguards for preserving or displacing gear without duplication.
- Added persistent villager wallets, lifetime earnings/spending/deposits, natural worker income, assigned-storage deposits, wallet UI, wallet-capacity tuning, and optional unlimited wallets.
- Added the Payment Box block with item, menu, screen, recipe, loot-table, and currency-tag support for recurring worker pay and stored currency.
- Added clipboard workforce management with assigned storage, payment storage, work-area drafting, move/resize/clear controls, storage and work-area previews, synced workforce snapshots, status pages, and warnings for missing storage, missing areas, full inventories, unpaid contracts, or straying workers.
- Added Stay Here recruit behavior so recruited villagers can hold position when ordered instead of only following the player.
- Added hired combat work with guard, roaming, attack-all, and hunting modes for patrolling assigned areas and targeting non-villager threats or animals without treating players, villagers, golems, or tamed animals as job targets.
- Added mining, logging, and farming workers with exposed-ore and excavation mining modes, support placement, natural-tree harvesting, log filters, optional stripping/leaf handling/sapling replanting, mature-crop harvesting, replanting, and output delivery.
- Added fishing, brewing, builder, animal-handling, and nitwit hired work, including villager fishing hooks, brewing orders and potion variants, construction blueprint placement/previews, data-driven build sites, animal breeding and product gathering, and lightweight nitwit job status behavior.
- Added construction blueprints, builder placement controls, material and tool checks, paid build jobs, block-entity sanitization, material-storage lookup, and a data-driven default structure catalog based on vanilla village houses.
- Added 14 built-in quests, bringing the built-in quest set to 35 with the Cartographer's Atlas questline, Standing Watch, and additional Village Supply requests for beetroots, bottles, eggs, feathers, and torches.
- Added five new built-in questlines with 12 quests total: Hearthbound, Field Medicine, Workshop Oaths, Courier Roads, and the end-game Last Ember line.
- Added quest module v2 support with one-file quest modules, providers, availability rules, lifecycle data, stages, stage aliases, branching responses, inline or extracted quest scenes, rewards, scoped completion limits, and legacy v1 compatibility.
- Added new quest objective, trigger, fact, condition, and action support for locations, structure visits, mob kills, block break/place/interact tasks, memory events, trades, gifts, reputation checks, choices, facts, and condition-backed progress.
- Added quest journal and HUD support for completion history, multiple tracked quests, selected-quest highlight mode, persisted quest selection and scroll position, tab-specific empty states, count badges, bookmarks, status styling, and scrollbar/highlight assets.
- Added shared dialogue and quest action execution support, localized text keys, message prefixes, dialogue control flags, forced-dialogue replacement/removal controls, configurable dialogue text speed, dialogue blip audio, and configurable villager chat broadcasting.
- Added non-binary villager gender support, broader deterministic name selection, opposite-gender breeding configuration, and `/villagerretaliation profile set_gender` for operator profile correction.
- Added villager rendering and resource-pack support for dual-arm layouts, combat-capable model behavior, profession/type/level overlays, and vanilla OptiFine CEM/EMF-style model compatibility.

### Changed

- Expanded recruitment from follow/inventory management into contract-driven hired help with role selection, job inventory, assigned storage, payment, work areas, status pages, and workforce controls in the interaction UI.
- Changed villager trades, wallets, deposits, payment boxes, villager drops, hire refunds, worker deposits, and trade costs to use the shared currency resource and `villagerretaliation:currency` item tag instead of assuming an emerald-only economy.
- Reworked the villager interaction screen with new container art, currency icons, pixel option buttons, portrait/nameplate ornament assets, expanded work pages, synced status text, and currency-colored wallet labels.
- Changed merchant trade leveling to support skill-based trade XP scaling, persisted fractional progress, synced profile payloads, and adjusted client-side merchant XP displays.
- Reworked the quest runtime and tracker around staged progress, branch locking, current-stage persistence, blocker reasons, village-scoped facts, deterministic response transitions, choice history, and saved-condition evaluation without requiring a live issuer.
- Changed the quest journal from the older single-surface tracker into a tabbed journal with better active/completed/history views, tracked-quest selection, scrolling behavior, status badges, and highlight rendering.
- Reworked forced dialogue, quest dialogue, and authored dialogue trees to share localized text metadata, action execution, dry-run support, payload codecs, and runtime clear/reload behavior.
- Changed villager AI and combat suppression so armed, angered, hired, or actively controlled villagers can avoid conflicting vanilla panic, flee, hide, bell, raid, food-sharing, hero-gift, and trader-avoidance behaviors when custom retaliation or work logic should be in charge.
- Reworked retaliation and support behavior with extracted combat tactics, hostile-tier harassment throws, armorer/smith/golem repair support, passive cleric ally healing line-of-sight, and throttled natural hostile targeting.
- Changed villager job, chest, pickup, and storage behavior so workers prefer assigned storage, recover from missing or blocked containers, extract dropped items more reliably, and keep job supplies/outputs separate from personal inventory.
- Changed villager social and profile behavior with non-binary gender labels, breeding compatibility controls, persisted gender overrides, and expanded profile command support.
- Changed the `despised` reputation threshold default to `-400`, making the most hostile reputation tier easier to reach than in older betas.
- Changed config coverage with hire balance, worker food, per-role efficiency, skill growth, builder, storage, currency, dialogue animation, dialogue audio, and villager chat broadcast options.
- Changed player and pack documentation for the larger feature surface, including updated README/wiki counts, quest authoring guidance, builder structure notes, resource-pack model notes, and generated datapack-builder metadata.

### Fixed

- Fixed duplicate skill-trade registration paths so repeated registration no longer appends duplicate villager or wandering-trader offers.
- Fixed UI scale, text scale, tooltip bounds, hover regions, gift-page controls, profile/options hit detection, and merchant-screen XP display drift across common GUI scale settings.
- Fixed merchant trade XP desync and fractional adjusted-progress loss after trades, offer refreshes, and merchant result/container updates.
- Fixed hired worker inventory edge cases around protected gear, trade previews, hero gifts, food sharing, legacy overflow migration, output deposits, missing supplies, full storage, and equipment restoration.
- Fixed worker pathing and job reliability issues around water, ladders, unreachable targets, blocked storage, returning to assigned areas, stale targets, storage recovery, pickup extraction, leaf bridges, and farming/logging/mining flow interruptions.
- Fixed quest tracker, completion-history, objective-count, trigger-index, branch-locking, action-result, diagnostic-buffer, and payload-size edge cases that could leave progress stale, hidden, overreported, or unsafe to sync.
- Fixed forced-dialogue and dialogue reload cases involving global replacement, removal files, localized text metadata, control-only dialogue entries, payload codec clearing, and action execution diagnostics.
- Fixed villager AI compatibility issues where vanilla flee, avoid, panic, hide, raid, bell, gift, and food-sharing behaviors could fight custom combat, anger, retaliation, or hired-work behavior.
- Fixed performance hot spots with spread tick work, gossip-distance checks, item-count caches, storage scans, hostile target polling, work-area scans, and worker target searches.
- Fixed resource-pack and rendering compatibility around villager name rendering, profession/type/level overlays, combat model layout, and OptiFine CEM/EMF-style model behavior.

### Technical / Pack Dev

- Added data-driven currency definitions at `data/villagerretaliation/currency/`, currency item tags, configurable currency text color, and shared lookup paths for wallets, trades, drops, payment boxes, refunds, deposits, and hire costs.
- Added data-driven builder structure definitions at `data/<namespace>/builder_structures/`, synced builder structure catalogs, placement/material diagnostics, and default generated village-house structure entries.
- Added quest module v2 schemas, docs, migration helpers, diagnostics, trace/explain tooling, datapack-builder support, embedded/extracted scene handling, and compatibility adapters so v1 quests continue to load while new packs can target v2.
- Added or expanded `/villagerretaliation` debug commands for hired-worker previews and target lines, quest provider/start/inspect/availability/trace/objective/stage/action/fact diagnostics, village registry inspection/merge tools, dialogue diagnostics, and profile gender overrides.
- Added networking and save support for hired-worker, quest, dialogue, and builder systems, including protocol version `25`, server config sync, builder catalog sync, quest tracker sync, clipboard storage/work-area/workforce previews, construction blueprint placement, hired role settings, dialogue responses, completion history, choice history, current quest stages, and bounded payload collection reads.
- Added structured quest, dialogue, action, objective, trigger, condition, village-scope, and builder validation warnings so datapacks fail with clearer diagnostics instead of silent partial loads.
- Added GameTest and lightweight regression coverage for hired workers, villager inventories, quest v1/v2 compatibility, quest registries, completion history, tracker presentation, deterministic choices, suppressing vanilla trade/gift behavior, and worker storage/job flows.
- Added pack-facing language keys, assets, GUI textures, tooltip text, config comments, and wiki snapshots for hired workers, quest v2 authoring, builder structures, villager models, dialogue controls, and the expanded quest journal.

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
- Removed beta.11-only quest/action compatibility shapes from the beta.12 quest surface. The maintained action fields are `type`, `quest`, `action`, `amount`, `memory_event`, `loot_table`, `notification`, `text`, `forced_dialogue`, and `flash_tracker`; documented shorthand such as `xp`, `rep`, `notify`, and inline unique action fields still load where the shared action parser lists them.
- Removed top-level dialogue metadata aliases from maintained beta.12 dialogue and dialogue-tree authoring.
- No beta.12 runtime JSON fields, triggers, or placeholders are removed solely because of the folderized dialogue layout.
- Edmundo came back
- Removed Edmundo (i think)

### Migration Notes For Pack Authors

- Keep existing beta.11 datapacks on `villagerretaliation.pack_version: "1.0.0-beta.11"` until you have manually reviewed dialogue, forced dialogue, notifications, quests, and skill trades against the beta.12 wiki.
- Do not migrate by only changing `villagerretaliation.pack_version` to `1.0.0-beta.12`. The marker selects the editor/runtime target; it does not reorganize dialogue files, audit ids, update compatibility fields, or validate new beta.12 behavior.
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
