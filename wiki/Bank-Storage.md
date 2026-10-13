# Bank storage, vaults, safes and physical keys

Vaults, safes and physical keys provide lockable storage and settlement treasury inventories.

## Using storage

Creative blocks / command IDs: `villagerretaliation:vault`, `villagerretaliation:vault_controller`, `villagerretaliation:safe`. These blocks are available through creative mode or commands.

Every vault block and controller contributes 27 slots. Complete rectangular groups can be 1–3 blocks along each axis, up to 27 blocks / 729 slots. A safe is a separate 54-slot block and never connects. Every item is accepted, including noncurrency items with custom components.

New placements join compatible adjacent rectangles in deterministic Y/Z/X order, preferring the largest complete rectangular merge. A merge must include whole existing groups, remain inside 3×3×3, and contain at most one controller. Two controlled vaults can touch without merging their inventories or key identities. Placement order can determine which rectangle an ordinary block joins when several arrangements are valid. After removal, the affected group reforms within its former boundary; it does not steal blocks from a neighboring controlled vault. Empty corners produce smaller rectangles, not invisible missing storage cells.

Inventories belong permanently to individual physical members. Reformation never redistributes items. An unavailable chunk/member causes the combined menu and automation to fail closed until the membership can be checked again; unloading is not interpreted as removal.

Right-click opens storage. A locked container requires its matching key somewhere in the player's main inventory or offhand. Sneak-right-click a controller/safe with its matching key to toggle ordinary locking. Controllerless vaults are open to everybody. Keys have the same appearance; anvil names do not affect matching.

The shared GUI shows 54 slots per page, with a scrollbar and mouse-wheel paging anywhere on the screen. All real slot indices remain fixed on client and server (including index 728). Paging only changes visibility. Shift-click can fill or empty slots across the whole inventory. Server clicks recheck access and the exact member/backing-inventory list; reshaping, registration or loss of access invalidates an old menu.

## Key defaults and copying

- A new safe/controller generates a server UUID and gives one original key to the placing player. A full player inventory drops that key at the player. The issued flag is saved and carried by the dropped block item.
- A command/automation-placed block without a player gives its original key to the first player who interacts. Replacing an already-issued block never issues another original.
- Pickup preserves the same UUID, issued flag, damage stage and unlocked setting. A breached container remains breached after placement. Placement clears repair and inherited mining deadlines and any mounted/burning charge.
- There is no owner/master-key bypass or free reissue command. Make spare keys before losing the last one. After total loss, breach to retrieve items, or slowly mine the container and retain its identity. Mining does not manufacture a replacement key; a new container has a new identity. No original/copy distinction affects security.

Craft a matching **key + clay ball → key mold**. The original key remains, with its existing components/name. Craft **mold + gold ingot → unfired key cast**; the mold is consumed. Smelt the cast in a furnace to produce one matching copied key; the cast is consumed and no mold remains. Identity passes through custom server recipes, including the furnace's dynamic assembly path. Blank creative keys/casts have no identity and cannot open locks or produce a usable key. Commands/creative NBT editing are administrative powers, not an adversarial security boundary.

## Mounted charges, repair and mining

Right-click TNT on the front face of a controller/safe. A thin framed TNT model shows the mounted charge. Ignite with flint and steel or a fire charge. Its four-second loaded fuse causes only a particle/sound effect and one stage of lock damage, never a terrain explosion. Ordinary vault members cannot hold charges or be repaired.

A safe breaches after one charge. A controller goes through intact → damaged → heavily damaged → breached after three. Breached storage is open to everybody, including registered village funds. Copper/exposed-copper/redstone patches currently indicate damage on the placeholder models. Vaults are barrel-like; safes use the iron-block texture.

Each hit sets the controller/safe's repair deadline to 6,000 ticks after the current overworld game time. This is five minutes of server simulation ticks, persists through unload/restart, progresses while the storage chunk is unloaded, and does not count time while the server is stopped. The mounted fuse itself pauses while its chunk is unloaded.

After the deadline, use one iron block on the front for each repair stage. The first repair closes the breach using the original lock identity; subsequent repairs can happen consecutively. A fully breached controller costs three iron blocks, a safe one. Repairs also reset the ordinary unlocked toggle to locked. A burning charge prevents repair.

Locked storage uses vanilla reinforced-deepslate mining progress. Unlocked storage uses hardness 50 (obsidian) with the iron-or-better pickaxe requirement. The minimum pick requirement also applies to survival pickup of locked storage. Connected members inherit their controller's slow mining protection. Pistons cannot move the blocks; high blast resistance and vanilla wither/dragon immunity tags protect normal destructive paths.

**Conservative transient deadline:** a member that has inherited a controller's repair deadline remembers only that maximum mining deadline. If split off or if the controller is mined, it becomes accessible without a key but remains slow to mine until that existing deadline expires. It has no damage stage, charge or repair operation. Joining another rectangle cannot shorten this marker. Controller repair eligibility and damage remain local to that controller. A member still controlled by the damaged controller always retains the controller's unchanged deadline. These transient markers persist in world block data, but **never in pickup items**: successful mining and replacement clears them.

Mining a vault member drops only its own 27 assigned slots as loose items plus its block item. The block item carries identity/state, not inventory. All other member slots stay put. Removing a safe emits one safe item holding its 54 exact slots, including gaps and components. Normal safe removal uses one path rather than both a loot-table copy and an on-remove spill.

## Village treasury

Inside an established settlement's territory, the current leader carries the matching key and sneak-right-clicks a safe/controller with a configured currency item (emerald by default) to register the entire current group as treasury storage. The currency item is a reusable registration gesture and is not consumed. Repeat to unregister. Multiple controllers/safes can be registered. There is no bank-building requirement or new building asset.

Normal direct treasury access requires both leadership and the ordinary lock's key/unlocked condition. Delegated `TREASURY` permission does not authorize a normal withdrawal through settlement management. A breach explicitly overrides leadership and key access. Keys alone cannot authorize another player to withdraw village funds.

Public automation rechecks the current group/lock on every call, even on a previously cached capability. Every connected face addresses the full combined inventory. Locked public insert/extract is denied. Unlocked ordinary storage permits both. Unlocked registered treasury permits public insertion but denies actorless hopper withdrawal; breached treasury permits both. Internal village income deposits use a separate privileged accounting path, not a public hopper capability.

Registered per-block inventories live authoritatively in `SettlementEconomy.BankStorage`, beside the virtual balance. Block entities reference those stocks rather than saving a second spendable copy. Currency valuation uses the existing `VillagerCurrencyResources` definition and tags (one unit for each accepted currency item). Noncurrency items are retained and never spent as funds.

Registration explicitly migrates existing whole virtual units into available physical slots and subtracts those units from the virtual remainder. New income automatically takes the same deposit route. Existing physical currency in the storage becomes part of treasury funds once registered. Spendable balance is physical currency plus the exact virtual remainder, counted once. Spending removes real currency; fractional change remains exact virtual currency. Theft immediately reduces the physical portion, so the same money cannot also be spent virtually.

Absent/full storage retains funds in the existing virtual balance, including fractions. No overflow is deleted or dropped, and the fallback does not create an additional item copy. Income/registration and the loaded controller's periodic check retry available capacity. Registered unloaded stocks remain in settlement accounting, like existing settlement depots, so income and authorized civic spending can continue while those chunks are unloaded.

Unregistering or splitting away from a registered controller returns the exact inventory to the block and removes it from village accounting; it does not add that value back virtually. Removing registered blocks removes only those stocks from the account. Registered inventories merge with existing settlement lifecycle mergers, including noncurrency items. Registered bank plots cannot be released/relocated, and dissolution requires unregistering/mining them first.

## Persistence boundary and limitations

Settlement save format is 73 and network protocol is 110. Older supported settlement saves load without requiring treasury registration or destroying their virtual balance. Do not use an older binary to write these newer saves.

The implementation covers normal save/restart, unload, reshape, pickup, ordinary explosions and sequential server access. Treasury stock and its virtual accounting save together. Minecraft still saves block chunks, player/item entities and settlement data independently; there is no power-loss atomicity across those files or immunity to external world editors, administrative item duplication, or third-party code bypassing APIs. The registration/detachment transition between block-local and settlement storage uses normal Minecraft save ordering, not the construction system's durable cross-file escrow protocol. Abrupt crashes during those transitions can leave inconsistent state.
