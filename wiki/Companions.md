# Persistent companions and multiplayer parties

A party supports four players and four active villager companions. Eligibility does not occupy a slot. A player chooses to recruit or rejoin a specific villager. Companion service offers use those same four slots, with separate temporary terms. Existing scripted quest actors retain their follower directives and narrative leases; authors should use `companion_service` for an optional party companion.

## Recruitment and economy

The default upfront recruitment payment is 32 emeralds, configured by `dialogue.companionRecruitmentCost`. It replaces recurring party rental payments. Revered reputation can recruit for free using the existing configurable reputation threshold. An authored grant can also waive payment or require a minimum reputation. Returning to the same recruiting player is free. Worker hire contracts retain their existing economy.

Existing active party contracts migrate to permanent companionship without another charge. The original recruiter, contract identifier, payment totals, duration and historical expiry remain saved. Their active expiry becomes unlimited. The existing villager entity retains its UUID, inventory, skill progression and relationships. No companion or inventory is reconstructed from an archive.

## Authority and membership

Recruiter and commander are distinct persisted fields. Promotion changes party leadership only. The recruiter controls recruitment management and goodbye. Ordinary party administrators gain neither equipment access nor dismissal permission. A recruiter can entrust command to another party member with `/vr companion entrust <villager UUID> <player>`.

Equipment access stays with the recruiter by default. `/vr companion equipment <villager UUID> true` explicitly grants it to the current commander. Changing commanders clears this grant. Command delegation never grants goodbye permission.

Leaving or removal moves a player's owned companions into their personal party. Delegations to the departing member revert to the recruiter. A solo player can accept a party invitation and carry the same companion records back, subject to the combined four-villager cap. A rejected invitation leaves their personal party intact. A player cannot recruit or hire another player's associated companion.

Disbanding says goodbye only to the leader's own companions, subject to the usual combat restriction. Those companions must be loaded so their safety can be checked. Other members' companions move to their owners' personal parties with the same active records, even if they are fighting or downed. The former leader cannot use disband to dismiss or take equipment from them. Their commands revert to their owners.

## Wait, separation and goodbye

Wait Here retains an active slot. Logout waits safely for the assigned commander. Entrusted companions can continue with an online party member. Quick movement and loot orders pause while the assigned commander is unavailable. Another dimension produces a clear waiting reason and preserves the assignment.

After player death, a companion defends its position. After `dialogue.companionDeathWaitTicks`, default 1200 ticks, it can attempt a safe home return if combat has ended. The active slot and personal association remain. A returned companion resumes village life and waits for an explicit Follow command.

Goodbye frees the active slot immediately. It is blocked while downed, in combat or within 200 ticks of receiving damage. The companion walks away before a safe return. The default minimum departure time is 200 ticks. The bounded path attempt is 1200 ticks, configured by `dialogue.companionDepartureMinimumTicks` and `dialogue.companionDepartureTimeoutTicks`.

A hidden return checks **all players, including spectators**, independently at departure and arrival. Anyone within 32 blocks blocks it, even through walls; anyone within 64 blocks with an unobstructed ray to the body or eyes blocks it. Arrival rays target the actual destination, not the villager's previous position. The destination must be loaded, within the world border and build height, supported by a safe floor, clear of collisions and fluids, and clear of nearby hostile mobs. Neither entities nor items are manually copied.

Home comes from the villager's registered bed or known village registry. A missing bed, archived village, unloaded home chunk or unsafe destination uses an explicitly agreed fallback if one is valid; otherwise it produces Safe waiting with a reason. `/vr companion safehome <villager UUID>` agrees a safe standing position near the nearby villager as a fallback and **preserves the registered home**. The owner must be within eight blocks and out of combat. No camp or settlement is invented. Old saves that already designated a home retain that designation.

The departure clock advances only while the real villager is loaded and out of danger. After 200 ticks it may relocate only if both endpoints are unobserved and the destination is valid. After 1200 ticks it stops its bounded walk attempt and waits in place, checking again every 20 loaded ticks. A player who stays nearby can keep it waiting indefinitely; the timeout never overrides observation or safety. A villager that physically reaches home can visibly resume ordinary life. Combat pauses departure and retains normal defensive behavior. An unloaded entity is never replaced or force-loaded by the return service.

Candidate standing positions are within four horizontal and two vertical blocks of home/fallback. Hazard checks also cover adjacent blocks, not just the floor: fire, lava, magma, cactus, campfires, berry bushes, wither roses, powder snow and adjacent fluids are rejected. Datapacks can extend `data/villagerretaliation/tags/block/unsafe_companion_return.json` for modded hazards. Unknown mod hazards are not automatically recognized.

Cross-dimension home return requires explicit owner authorization through `/vr companion dimensionreturn <villager UUID> true`. Both destination terrain **and entity storage** must already be loaded. It uses Minecraft's dimension transition with the same UUID and saved inventory; the engine may replace the Java object during that transition. Without authorization or ready destination storage, the companion waits with a reason. Unloaded companions resume when their real entity loads. No replacement entity is spawned to recover an unloaded companion.

`/vr companion` lists personal companions, status, reason and last-known dimension and position. Returning home, At home, Safe waiting and Unavailable are persisted separately from active party membership.

## Content hooks and personal bond

Quest stage and dialogue actions can grant eligibility to their specific villager and player:

```json
{"type":"companion_unlock","free":true,"minimum_reputation":0}
```

The containing quest or dialogue conditions select when to grant it. The action itself never joins the party. A free grant may require a negative or positive minimum reputation, within -1000 to 1000.

A meaningful authored shared experience can grant personal bond once:

```json
{"type":"companion_bond","experience_id":"example:helped_restore_home","amount":5}
```

Amounts are 1 to 5. The unique experience identifier is a persistent receipt per villager and player, so repeating the same dialogue does not grind the bond. Bond is stored separately from general reputation and exposed to existing facts through the player variable `companion_bond/<villager UUID>`.

A completed rescue grants 3 bond at most once per 24000 world ticks per player and villager. Rescue rewards are suppressed for a day after attacking the companion. Existing dialogue conditions can read the `villagerretaliation:companion_rescued` player tag and `rescued_companion` variable.

Small reputation changes never erase the personal association. Direct attacks warn and reduce bond, with a 200-tick spacing to suppress repeated damage spam. Three such attacks suspend following until the recruiter restores Revered trust. Raiding the companion's home also suspends trust. Equipment and progression remain on the villager.

## Chosen temporary service

An authored quest or dialogue outcome can offer free, one-use service to its specific player and villager:

```json
{"type":"companion_service","service_id":"example:fieldwork","text":"Fieldwork","duration_ticks":24000,"minimum_reputation":0}
```

This only creates an offer. The notice links to `/vr companion stories <villager UUID>`, which previews the duration, reputation requirement and slot cost. The player chooses its Accept service link, or runs `/vr companion service <villager UUID> example:fieldwork`, while within eight blocks. Joining uses the existing ownership, availability, combat and four-slot checks. An unavailable/full party does not spend the offer. Service requires the ordinary neutral trust floor as well as its authored minimum reputation.

Duration is 200–168000 overworld game ticks, starts on acceptance, and persists through restart/unloading. A repeated action with the same service ID cannot refresh or reuse it. The built-in fieldwork offer lasts 24000 ticks (one Minecraft day, nominally 20 real minutes). These are upfront free terms, not rent. An author should use a new stable ID for a different opportunity.

Expiry is independent of quest completion or failure: this is a fixed period of availability promised by a quest outcome. It does not cancel permanent eligibility, previous recruitment, equipment, growth or bond. On expiry, goodbye occurs when the entity is loaded and can safely part; an unloaded, downed or fighting companion retains its active slot until then. Owners can end service early with ordinary goodbye. There is no automatic conversion to permanent companionship. A service-only relationship has no free-rejoining entitlement; normal recruitment still uses its own fee/reputation/grant terms. Existing personal association and equipment claims remain protected after service.

## Playable personal stories and capability growth

`/vr companion stories <villager UUID>` shows the nearby villager's recorded profession, personal bond, service offers, story requirements and completed choices. Available stories offer two clickable choices. The equivalent command is `/vr companion grow <villager UUID> <story ID> <choice ID>`. Only the associated owner can choose, within eight blocks, out of combat and with trust intact. Each story permits one choice **per villager**, persisted across dismissal, rejoining, save/load and dimension travel. Repeating dialogue, switching professions or changing players cannot stack both rewards.

| Story | Real prerequisite | Choice and capability effect |
| --- | --- | --- |
| After the fall | Successful rescue; personal bond at least 3 | Guarding +4 (`hold_the_line`) or Survival +4 (`find_a_way`) |
| A place to return | Farmer history; completed safe homecoming; bond at least 2 | Farming +6 (`tend_the_fields`) or Cooking +6 (`feed_the_road`) |
| The first folio | Cartographer history; actual **Blank Map Promise** completion; bond at least 3 | Cartography +6 (`careful_maps`) or Survival +6 (`unknown_country`) |

These increase the existing permanent skills and their existing gameplay effects, capped at the normal skill maximum. They do not introduce a class system or change jobs. The earliest profession observed by the companion/story system is saved as history; older companions without that field capture their current profession on reconciliation. Existing saved bond receipts and rescue history qualify. A completed safe return grants 2 bond once per player/villager. Repeated home trips cannot grind it.

**Blank Map Promise** keeps its existing materials, stages and rewards. Starting it offers optional one-day fieldwork service. Completing it adds free recruitment eligibility (minimum reputation 0), 3 bond and the folio story. Neither starting nor completing it automatically recruits or converts service. Rescue and homecoming stories work outside that quest. Story choices currently use the server chat interface; the existing inventory and skill screens remain in place.

Authors can add or override `data/<namespace>/companion_stories/<id>.json`. Reload validates the entire new registry before replacing it; invalid stories fail reload. The generated `tools/datapack-builder/companion-story.schema.json` describes the format. For example:

```json
{
  "title": "The first folio",
  "text": "What should we learn from our first journey?",
  "profession": "minecraft:cartographer",
  "experience_id": "example:shared_journey",
  "requirement": "Complete the shared journey with this villager",
  "minimum_bond": 3,
  "choices": [
    {"id":"maps","text":"Make maps we can trust.","skill":"cartography","points":6},
    {"id":"paths","text":"Find a safe way home.","skill":"survival","points":6}
  ]
}
```

Omit `profession` for any profession. Use 2–4 choices, existing skill names, 1–10 points, and stable story/choice/experience IDs. Supply a readable `requirement` for players; internal experience IDs are not shown in the story menu. The optional service `text` names the offer. `companion_bond` records its experience for story requirements. Built-in event IDs are `villagerretaliation:rescued` and `villagerretaliation:returned_home`. Saved choices retain their original skill/point receipts even if content is later changed or removed. Display text is authored in these resources; there is no new dedicated story screen or full personal quest catalog.

## Equipment, supplies and quest contributions

The existing inventory menus remain in use. Equipped companion armor and hands are slots 0 through 5. They cannot contribute to a shared turn-in. Carried supplies occupy slots 6 through 41.

A shared turn-in uses the submitting player's inventory plus explicit offers. Other players' inventories and companion supplies are excluded by default. Designate supplies with `/vr companion offersupplies <villager UUID> <quest ID> <slot> <count>`. A player can designate their own main inventory with `/vr companion offer <quest ID> <slot> <count>`.

Each offer previews the item, slot, count and quest. Count 0 withdraws it. An offer lasts ten minutes, applies to the current stack, and is consumed once. It expires on server restart. The contributor and companion must be online or loaded, in the same dimension, in the current party and within 16 blocks of the submitter at settlement. Equipped companion slots cannot be designated.

The turn-in previews sources, then checks permissions, count, item components and the exact stack still occupying each slot before any removal. A moved or replaced stack, withdrawn offer, changed party or lost proximity cancels settlement without partial consumption. Dismissal preserves belongings. The owner can exchange equipment through the current inventory UI before parting when safe.

## Rescue and compatibility

Sneak and right-click a downed companion with an empty main hand, then keep Sneak held and stay still within three blocks. Default help-up duration is 80 ticks, configured by `combat.reviveHelpTicks`. Damage, movement, lost line of sight, dimension change, logout, unloading, opening a menu or using an item interrupts it. Another party member can cover the reviver with existing combat and Stand Guard commands.

`combat.reviveRequiresBread` defaults to false. If enabled, one bread from the reviver's own inventory is checked at the start and consumed only on completion. Interrupted attempts spend nothing. Recovery uses the existing health rule and does not introduce permanent death stakes. Automatic quiet recovery remains the fallback. Second Wind's presentation, exact recovery-health override and external revive control remain supported.

## Observations and quiet homecomings

Companions can privately point out exposed structures to nearby party members. Recognition uses current blocks, coherent architecture and unobstructed rays in loaded chunks. A structure reference supplies identity only; it does not reveal buried rooms. Darkness, water, weather, range, combat, conversation and stale evidence can suppress a line. Recognition intentionally misses damaged or unfamiliar building variants. Buried treasure is disabled. Unknown modded structures are skipped unless a datapack explicitly maps their ID or an unambiguous tag to a supported geometry adapter; an opt-out wins over mappings.

The 26 pools contain 260 lines: 20 vanilla structure families, three opt-in generic descriptions and three homecoming situations. There are no added menu topics, camera actions, rewards or growth choices. Each recipient gets their own direction. Speaker, recipient and party histories coordinate a single attempt, with a shared two-minute cooldown. A reserved attempt is consumed even if the connection closes before delivery.

Homecoming requires a valid registered home, settled presence, at least five minutes of shared loaded-world absence, 256 blocks of shared travel and 30 continuous seconds outside the outer boundary. The inner/outer radii are 24/64 blocks, with five seconds of return dwell. Teleports, offline gaps, idle listeners and short boundary crossings cannot satisfy travel. Only participants recorded on departure can hear that journey's line. Independent arrival/reunion additionally requires dismissal and observed separation. A ready farmer homecoming story takes priority; suppression consumes the encounter without choosing the story or awarding anything. Nearby returns to the same registered home share a persisted arrival episode.

Runtime data lives in `data/villagerretaliation/companion_ambient/default.json`, using `tools/datapack-builder/companion-ambient-v1.schema.json`. Cooldown, quiet periods, observation range, dwell, mappings and pools are datapack fields; home journey thresholds are fixed policy in `CompanionAmbientService`. Unknown fields or invalid content fail reload before replacing the current snapshot.

The world stores `data/villagerretaliation_companion_ambient.dat` and its `.identity` receipt together. Back up and restore both from the same snapshot. History has no expiry or size eviction. Missing, corrupt or mismatched receipts suppress callouts and log the problem rather than silently forgetting discoveries. This ledger does not replace companion, party, inventory or story saves.

## Paired ordinary dialogue

All 32 remaining ordinary option IDs now have compatible staged player/NPC pairs behind their existing choices, alongside the Greet and Ask a question bindings. The runtime loads 1,782 eligibility/outcome records containing 1,760 distinct spoken pairs. No option definitions, labels, conditions, actions or ordering were changed. Authored source owners still control eligibility; original prose remains selectable where appropriate.

The player speaks, the NPC replies, and the normal menu or existing end action follows automatically after readable holds. These pairs do not add Continue entries. Back, replacement notices and stale request tokens cancel queued playback. Existing decision trees retain their own choices and behavior. The server still validates the live conversation and original option, computes consequences once, then chooses presentation from the real outcome. Gift discoveries/reports, weapon durability, order timing, age, reputation, contact history and factual family/relationship records supply context; unresolved or unsafe text falls back to the original response. Pair rotation persists in the existing dialogue usage save.

The runtime format is `data/villagerretaliation/paired_dialogue/<locale>/*.json`, described by `tools/datapack-builder/paired-dialogue-v1.schema.json`. Locale overrides replace matching pair IDs; duplicate IDs within a locale fail loading. Data adds text bindings only, never options or actions.
