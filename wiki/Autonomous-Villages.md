# Autonomous villages

This system manages existing natural village identities and reuses settlement workers, housing surveys, territory ownership, trade, construction escrow and bank storage. Player-founded settlements retain their existing behavior.

## Operation

A natural village is admitted when its center chunk and a living resident are available. Its actual section footprint is preserved, including vertical boundaries. Admission rejects conflicting claims, empty footprints, footprints over 65,536 sections and rosters over 128 residents. It creates no resident, money, material or block. Village service capability is enabled without granting autonomous free expansion: every planner expansion is paid.

The persisted planner observes existing beds and work POIs, staffs eligible loaded adults, maintains care and trade, assigns one ruler and waits for sustained development needs. Existing personal commitments are respected. A child ruler uses the same planning system; the ruler need not personally be a worker.

Development priorities are a food-producing facility, enough validated housing for current residents, a kitchen, then a workshop. Housing shortages still include residents above the configured population target. Low stored food defers discretionary workshop construction. Existing food-reserve and nutrition configuration governs that check. Staffed production and budgeted bread purchases can replenish permitted depots. An empty or resource-poor village is allowed to stall; the planner does not manufacture supplies to hide that condition.

Only one unfinished autonomous project is allowed. New improvements stop at the configured lifetime cap. A completed authored building becomes a normal surveyed facility, with temporary builder staging removed and its worker returned to automatic staffing. Existing housing validation remains authoritative: usable floor, entrance, light, clearance and reachable beds; one usable bed per resident. A blueprint containing a bed does not by itself grant housing capacity.

The planner keeps the last food worker when borrowing an adult for construction. A village with no spare adult can visibly wait for a builder. Native workers execute their existing tasks and schedules; this feature does not add a universal recipe-production or resource-extraction strategy. Kitchen/workshop output still depends on supported work settings, tools, ingredients, stations and storage access.

### Construction, repairs and boundaries

Candidates use one loaded chunk-center surface position per planning observation. Existing placement, claim, world-border, interaction and belongings checks remain authoritative. No terrain or storage is overwritten simply to fit a blueprint. Missing materials, tools, fill or support block normal construction; manual clearing can unblock a protected site. Full quoted materials and fees use the existing reservation system. Submitted materials do not recolor authored blocks. Cancellation/refunds retain the existing exact-stack provenance and persistent refund-awaiting-storage behavior.

The village maintains its own completed autonomous structures. A repair can restore missing air cells but refuses altered non-air blocks or new containers. It waits for sustained damage, reserves the full authored quote and uses ordinary construction escrow. Matching existing cells are retained; normal construction handles unused escrow. The planner does not restore arbitrary pre-existing village buildings for which it has no authored provenance. Repairs do not consume the new-improvement cap. Their provenance is persisted separately from the latest maintained project.

After a complete unsuccessful site traversal and sustained need, the planner may buy one adjacent loaded chunk column. It checks the existing ownership/reservation index and capacity, pays the configured price above its reserve, and adds three vertical sections around the village center height. Existing sections remain unchanged. Claim, expansion counter and treasury debit serialize in the same settlement record; the allegiance footprint is reconstructed from that record. Expansion does not load terrain. The hard paid-expansion cap is independent of the inherited stage claim credits, which autonomous planning never spends.

### Ruler and player leadership

Exactly one ruler UUID, or an explicit vacant state, is persisted per autonomous village. A death event triggers succession; periodic reconciliation also handles departures. Candidates must be current residents, not departing, and verified living by a loaded entity or the existing persisted family record. A known death is never overwritten merely because its entity is still momentarily loaded.

Succession order:

1. Living spouse or active exclusive partner (including the surviving widowed partner).
2. Living children/descendants, adults before children. A child is allowed when no adult descendant qualifies.
3. Other living adult blood relatives through parent/child/sibling links.
4. A random eligible adult resident, using the server random source.
5. Vacant if no eligible successor exists.

Equal family-priority candidates use UUID order as a stable default. The unrelated-resident fallback is expressly random. Family traversal stops at 4,096 visited identities; an oversized unresolved graph leaves the position vacant instead of choosing an incorrect lower-priority candidate. There is no regent, invented heir or unloaded age simulation.

The existing earned-leadership path remains available at the management table: existing reputation and sustained support requirements apply. Acceptance transfers the same settlement, funds, residents and projects to the player and ends NPC planning. Stable internal civic authority is retained for in-flight projects. A player-led village continues to use its physical-income accounting mode so pending deposits cannot become a second spendable balance. Player-founded settlements retain legacy virtual-income fallback. Manual lifecycle operations involving an autonomous village require taking leadership first, preventing section-boundary corruption through player-column relocation/merge semantics. No new takeover quest or reputation system was added.

## Treasury and supplies

Villages use multiple existing vaults/safes, not a new bank building. Containers still accept all items. Tagged currency contributes one currency unit per accepted item; other contents are never spent as funds. Intact registered treasuries allow ordinary withdrawal only by the player leader, with existing key/lock checks. Physical breach permits public theft and immediately reduces available physical funds.

Autonomous work income and trade revenue first become **pending deposits**. Whole units move into available loaded registered storage. Full, missing or unloaded storage leaves a persisted, visible, nonspendable pending amount. Fractions accumulate there until depositable. Spending uses loaded physical funds plus exact virtual change returned by fractional purchases. Physical totals, spendable funds, reserve and pending amounts are displayed separately. Construction reservations are removed from spendable funds by the existing escrow system; pending income cannot pay for them.

Work income samples actual living, loaded, ticking, on-duty adults. Trading clerks and builders are excluded because trade proceeds and construction fees already account for their work. It is a modest abstract service-income source, not an item sale or a second payment for the same trade. Interval/day counters and credit save together. Replaying an interval cannot pay twice. First observation gives no income; long gaps reset the observation without catch-up. The funds cap applies to abstract income, including physical funds, virtual change and pending deposits. Real sales and donations are not truncated by that cap.

Default standing trade buys bread and sells surplus wheat, carrots and potatoes through existing clerk, lectern, finite-market, exact-price, stock-protection and daily-budget rules. Sales remove real permitted stock. Bread purchases consume spendable treasury funds and put real items in permitted supplies. A missing clerk, depot, price definition or budget blocks trading. Crop stock targets are `population_target * 16` per item; bread target is `population_target * 3`. Existing orders are preserved rather than overwritten every observation.

### Supplying a village

Inspect with `/vr settlement autonomy` while inside the village, or `/vr settlement <village UUID> autonomy`. The response includes the village UUID, ruler, current blocker, intended next action, money, depot/food counts, plots, paid expansions and completed improvements. Management-table interaction also reports autonomous status. Planning does not broadcast repetitive chat notifications.

A nearby player can explicitly donate storage with:

```
/vr settlement <village UUID> donateStorage <x> <y> <z>
```

The block must be loaded and inside the actual village boundary, and the player must be within eight blocks. An unbound settlement depot becomes permitted village supply/output storage. An unregistered safe/controller requires normal access and the matching key; all group members must be inside the village. Donation gives the village treasury control, so the donor cannot normally withdraw funds without becoming its leader. Breach behavior remains unchanged. Existing role filters and permissions apply. The planner does not silently take ownership of nearby player containers.

Storage blocks retain creative/command acquisition. This implementation adds no bank structure assets or container crafting recipes. A village without suitable supplied depots or treasury storage can work locally and accumulate capped pending income, but development will wait for its actual missing resources. Supplying infrastructure is not an ongoing need to direct each worker/project.

### Recoverable bank accounting

Once registered, each bank identity has one authoritative inventory in the settlement root even after detachment/unregistration. Registration copies the initial local stock and durably writes that root **before** discarding the chunk-local copy. Loaded menus, locks and automation resolve authority by indexed bank identity, so stale chunk snapshots cannot revive a pre-registration stock or stale treasury permission. Unregistration durably marks stocks inactive; it never copies their contents back into a separately saved chunk. Detached stock remains accessible through its physical block but is excluded from village funds. Normal mining removes that stock using the existing pickup behavior.

This deliberately changes the earlier detachment implementation. Detached registered-once containers remain associated with their original settlement ledger until mined; direct registration into a different settlement is rejected. Lifecycle release/dissolution treats those retained stocks as outstanding physical storage, so unregistering alone is no longer sufficient to remove that obligation. Multiple banks are supported; new registration is capped at 512 member inventories per settlement.

Autonomous lock repairs wait for the existing cooldown and no burning charge, consume one permitted depot iron block per stage, and persist the debit plus a repair UUID/target before changing the native lock. A chunk receipt prevents a replay from erasing a later hit. The intent retires only after the chunk checkpoint is durable. A missing iron block leaves the lock damaged; no material is minted.

This closes the specifically identified registration/detachment accounting gap and provides idempotent automatic repair/deposit behavior. It is not a whole-world power-loss guarantee: player/item entity transfers, physical mining/drops, administrator edits and unrelated Minecraft save files still have their own lifecycle.

## Defaults and bounded work

Override `data/villagerretaliation/autonomous_villages/default.json` in a data pack and reload resources. Invalid rules disable planning with a server error. Existing persisted decisions/counters remain; catalog changes do not rewrite active authored plans.

| Setting | Default | Meaning |
| --- | ---: | --- |
| enabled | true | Admit/plan natural villages |
| cadence_ticks | 1,200 | Minimum time between a village's plans |
| sustained_observations | 3 | Consecutive same need before development/repair |
| population_target | 16 | Birth ceiling, also bounded by existing population requirements |
| project_limit | 8 | Lifetime new autonomous improvements |
| expansion_limit | 4 | Lifetime paid chunk additions |
| expansion_price | 32 | Currency per addition |
| reserve | 8 | Funds retained before construction, expansion or trade |
| daily_budget | 16 | Trade purchasing allowance per 24,000 game ticks |
| income_interval | 2,400 | Minimum interval between work-income samples |
| daily_income_cap | 8 | Maximum abstract currency per game day |
| funds_cap | 256 | Stop abstract income at this total, including pending |
| food_unit_price_limit | 2 | Maximum unit price for bread purchases |

One village is visited per 20 server ticks. The natural-village ID list refreshes every 200 ticks. Territory candidates are cached by claim revision. Each visit surveys one existing chunk and at most 16 POIs; a planning turn may survey one additional chunk. Each plan tries at most one new site and one expansion candidate. It checks at most one maintained structure (holding repair focus across observations), and attempts at most one lock repair. Authored planning accepts at most 4,096 blocks and an 8,192-cell bounding volume. Care/allocation/trade/building scans reuse the existing resumable work queue (64–256 steps, checked against its 3 ms budget between indivisible operations). Controller-triggered overflow retries are coalesced to one per settlement per 20 ticks, and zero whole-unit deposits skip storage traversal. Synchronous saves and individual scans can exceed a tick budget; no TPS guarantee is claimed.

Loaded physical operation requires a ticking center and at least one actual living ticking resident. Construction additionally requires an actual eligible ticking builder; staffing never fabricates saved-entity workers. Autonomous settlements are excluded from abstract background production/population simulation. Unloaded depots and bank stocks are unavailable for autonomous spending. Building surveys, physical worker AI and material transfer retain their existing bounded mechanisms. No chunk tickets are added and no broad world/entity scan runs every tick.

When the area unloads, planning and physical work pause. Partial construction, reservations, ruler, boundary, counters and pending funds persist. There is no offline production, aging, birth, income multiplier or catch-up construction. Server-stopped time does nothing. Existing lock repair deadlines are expressed in game ticks and may elapse while the server runs with that storage chunk unloaded; the actual repair still needs loaded storage and supplies. With many villages, round-robin scheduling can make planning slower than its configured minimum.

## Authored catalog and migration

The default catalog references the existing vanilla plains small house, small farm, butcher shop and toolsmith templates. They are complete authored structures, not modular generation. The existing builder catalog resolves their IDs and pricing/material definitions. To add real exports, install their structure resources and existing builder JSON definitions (tag costs, explicit materials and exact-material mode are supported), then map the desired facility type to that catalog ID in the autonomous rules. Keep the template inside the planning bounds and ensure its actual fixtures/housing pass validation.

Settlement root format advances from 73 to 74; allegiance registry format advances to 6. Autonomous state version is 1. Existing settlements without that optional state stay player-managed. Legacy bank stocks default to registered; missing pending income defaults to zero. Authority indexes and registry footprints are reconstructed from saved records. The network protocol remains 110 because no packet fields were added. Older binaries must not write these newer saves.
