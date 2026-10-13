# Persistent companions and multiplayer parties

A party supports four players and four active villager companions. Eligibility does not occupy a slot. A player chooses to recruit or rejoin a specific villager. Temporary quest escorts continue to use the existing follower directives and leases. They do not become permanent companions automatically.

## Recruitment and economy

The default upfront recruitment payment is 32 emeralds, configured by `dialogue.companionRecruitmentCost`. It replaces recurring party rental payments. Revered reputation can recruit for free using the existing configurable reputation threshold. An authored grant can also waive payment or require a minimum reputation. Returning to the same recruiting player is free. Worker hire contracts retain their existing economy.

Existing active party contracts migrate to permanent companionship without another charge. The original recruiter, contract identifier, payment totals, duration and historical expiry remain saved. Their active expiry becomes unlimited. The existing villager entity retains its UUID, inventory, skill progression and relationships. No companion or inventory is reconstructed from an archive.

## Authority and membership

Recruiter and commander are distinct persisted fields. Promotion changes party leadership only. The recruiter controls recruitment management and goodbye. Ordinary party administrators gain neither equipment access nor dismissal permission. A recruiter can entrust command to another party member with `/vr companion entrust <villager UUID> <player>`.

Equipment access stays with the recruiter by default. `/vr companion equipment <villager UUID> true` explicitly grants it to the current commander. Changing commanders clears this grant. Command delegation never grants goodbye permission.

Leaving or removal moves a player's owned companions into their personal party. Delegations to the departing member revert to the recruiter. A solo player can accept a party invitation and carry the same companion records back, subject to the combined four-villager cap. A rejected invitation leaves their personal party intact. A player cannot recruit or hire another player's associated companion.

## Wait, separation and goodbye

Wait Here retains an active slot. Logout waits safely for the assigned commander. Entrusted companions can continue with an online party member. Quick movement and loot orders pause while the assigned commander is unavailable. Another dimension produces a clear waiting reason and preserves the assignment.

After player death, a companion defends its position. After `dialogue.companionDeathWaitTicks`, default 1200 ticks, it can attempt a safe home return if combat has ended. The active slot and personal association remain. A returned companion resumes village life and waits for an explicit Follow command.

Goodbye frees the active slot immediately. It is blocked while downed, in combat or within 200 ticks of receiving damage. The companion walks away before a safe return. The default minimum departure time is 200 ticks. The bounded path attempt is 1200 ticks, configured by `dialogue.companionDepartureMinimumTicks` and `dialogue.companionDepartureTimeoutTicks`.

A hidden return requires every nearby non-spectating player to be farther than 32 blocks. Players within 64 blocks with line of sight also block it. The destination must be loaded, within the world border, supported by a safe floor, clear of collisions and fluids, and clear of nearby hostile mobs. Neither entities nor items are copied.

Home comes from the villager's registered bed or known village registry. A missing bed, unloaded home chunk or unsafe destination produces Safe waiting with a reason. The return resumes when the destination becomes available. `/vr companion safehome <villager UUID>` agrees the nearby villager's current safe location as an alternative home.

Cross-dimension home return requires explicit owner authorization through `/vr companion dimensionreturn <villager UUID> true`. It uses Minecraft's dimension transition with the same UUID and saved inventory. Without authorization the companion waits with the destination dimension in its reason. Unloaded companions resume when their real entity loads. No replacement entity is spawned to recover an unloaded companion.

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

## Equipment, supplies and quest contributions

The existing inventory menus remain in use. Equipped companion armor and hands are slots 0 through 5. They cannot contribute to a shared turn-in. Carried supplies occupy slots 6 through 41.

A shared turn-in uses the submitting player's inventory plus explicit offers. Other players' inventories and companion supplies are excluded by default. Designate supplies with `/vr companion offersupplies <villager UUID> <quest ID> <slot> <count>`. A player can designate their own main inventory with `/vr companion offer <quest ID> <slot> <count>`.

Each offer previews the item, slot, count and quest. Count 0 withdraws it. An offer lasts ten minutes, applies to the current stack, and is consumed once. It expires on server restart. The contributor and companion must be online or loaded, in the same dimension, in the current party and within 16 blocks of the submitter at settlement. Equipped companion slots cannot be designated.

The turn-in previews sources, then checks permissions, count, item components and the exact stack still occupying each slot before any removal. A moved or replaced stack, withdrawn offer, changed party or lost proximity cancels settlement without partial consumption. Dismissal preserves belongings. The owner can exchange equipment through the current inventory UI before parting when safe.

## Rescue and compatibility

Sneak and right-click a downed companion with an empty main hand, then keep Sneak held and stay still within three blocks. Default help-up duration is 80 ticks, configured by `combat.reviveHelpTicks`. Damage, movement, lost line of sight, dimension change, logout, unloading, opening a menu or using an item interrupts it. Another party member can cover the reviver with existing combat and Stand Guard commands.

`combat.reviveRequiresBread` defaults to false. If enabled, one bread from the reviver's own inventory is checked at the start and consumed only on completion. Interrupted attempts spend nothing. Recovery uses the existing health rule and does not introduce permanent death stakes. Automatic quiet recovery remains the fallback. Second Wind's presentation, exact recovery-health override and external revive control remain supported.
