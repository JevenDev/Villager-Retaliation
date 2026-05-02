# Villager Retaliation

**Villager Retaliation** is a Vanilla+ NeoForge mod for Minecraft 1.21.1 that makes villagers and wandering traders feel less helpless. Adult villagers can drop profession-flavoured loot, defend themselves when threatened, rally nearby allies after a kill, and use role-specific combat behaviour inspired by vanilla illagers and witches.

The mod still keeps the village fantasy intact: babies and unarmed nitwits remain non-combatants, retaliation is temporary, and angry villagers only remember the actual attacker instead of turning every village into a permanent hostile zone.

## Features

### Villager retaliation

- Adult villagers can retaliate when damaged.
- By default, hitting a villager only angers that specific villager.
- Killing an adult villager can anger nearby adult villagers within a configurable radius.
- Anger expires after a configurable duration.
- Villagers remember their retaliation target through the mod's persistent hostility data.
- Creative and spectator players can be ignored by retaliation when configured.
- Trading is blocked while a villager or wandering trader is hostile toward the player.
- Hostile villagers and wandering traders can be pacified by interacting with them using enough emeralds.
- Baby villagers never fight back.
- Nitwits act as alarm villagers unless they have acquired a usable weapon.

### Profession combat roles

Different professions can respond with different temporary combat loadouts or defensive behaviours:

| Profession | Behaviour |
| --- | --- |
| Weaponsmith | Fights with an iron sword and has a slightly faster attack cooldown. |
| Armorer | Fights with an iron sword and gains brief Resistance when hurt. |
| Toolsmith | Fights with an iron axe. |
| Mason | Fights with an iron pickaxe. |
| Butcher | Fights with an iron axe. |
| Fletcher | Uses a bow or crossbow. |
| Farmer | Uses an iron hoe, with a small chance to hold bread, and can heal with bread while hurt. |
| Cleric | Uses splash potions and self-support potions. |
| Librarian | Can fight with a book. |
| Nitwit | Normally keeps fleeing behaviour unless holding a usable weapon. |

### Ranged combat

- Fletchers and any villager/trader holding a valid ranged weapon can use ranged combat.
- Supports bows, crossbows, and tridents.
- Bow behaviour is inspired by illusioners.
- Crossbow behaviour is inspired by pillagers, including charge, hold, and fire states.
- Crossbows fall back to a default arrow when no projectile is available.
- Tridents are thrown with a Drowned-style attack and damage the held trident after use.

### Cleric potion behaviour

Clerics have the most advanced support kit:

- Can drink defensive/self-support potions while threatened.
- Can throw harmful or slowing splash potions at attackers.
- Uses safer potion selection against undead or inverted-healing targets.
- Reduces witch-resistant damage types against clerics.
- Avoids throwing offensive splash potions when friendly civilians are inside the splash radius.
- Can heal injured villagers or wandering traders with splash healing potions.
- Idle clerics can passively look for injured allies and heal them out of combat.
- Passive healing range, health threshold, and line-of-sight requirement are configurable.

### Fleeing behaviour changes

- Adult non-nitwit villagers suppress vanilla panic/flee/hide behaviour so they can stand their ground.
- Baby villagers keep vanilla fleeing behaviour.
- Unarmed nitwits keep vanilla fleeing behaviour.
- Nitwits and baby villagers can still act as alarm witnesses, causing nearby adult villagers to rally after attacks or deaths.
- Raid/hide/panic memories are cleared for villagers that should fight instead of flee.

### Weapon pickup and temporary weapons

- Villagers and wandering traders can search nearby dropped items for usable weapons when threatened.
- Ranged weapons are prioritized over melee weapons when scavenging.
- Picked-up weapons are tracked and restored across state changes.
- Picked-up weapons are guaranteed to drop back on death.
- Temporary profession weapons are restored or discarded correctly when retaliation ends.
- Combat weapons can roll as drops outside combat, with configurable drop and enchant chances.

## Villager drops

Villager Retaliation adds configurable Vanilla+ drops for adult villagers.

### Base drops

- 1-5 emeralds, controlled by config chance.
- 1-3 bread, controlled by config chance.
- Baby villagers do not drop custom loot by default.
- Profession-specific loot can require a player-caused kill.

### Profession loot pools

Profession loot is rolled from themed pools instead of dropping everything at once.

| Profession | Possible drops |
| --- | --- |
| Farmer | Wheat, beetroot, carrots, potatoes, apples, pumpkin seeds, melon seeds, wheat seeds, suspicious stew. |
| Leatherworker | Leather, rabbit hide, damaged leather armor, dyes, very rare saddle. |
| Fisherman | Cod, salmon, damaged fishing rod, string, sticks, rare tropical fish, rare pufferfish, rare lily pad, very rare nautilus shell. |
| Librarian | Paper, books, ink sacs, feathers, rare bookshelf, very rare enchanted book, including possible sold enchanted books. |
| Shepherd | Wool, random wool, damaged shears, wheat, dyes, carpets, rare banners. |
| Butcher | Beef, porkchop, chicken, mutton, rabbit, leather, rare smoker. |
| Cleric | Rotten flesh, redstone, lapis lazuli, glowstone dust, rare bottles o' enchanting, rare healing/regeneration potions, very rare ender pearl. |
| Cartographer | Paper, filled maps, compass, map, ink sacs, feathers, rare cartography table. |
| Toolsmith | Damaged iron pickaxe/shovel/hoe, iron ingots, coal, sticks, flint, rare smithing table. |
| Weaponsmith | Iron ingots, coal, sticks, rare grindstone. |
| Armorer | Damaged iron armor, iron ingots, coal, chains, rare blast furnace. |
| Fletcher | Arrows, flint, feathers, sticks, string, tripwire hooks. |
| Mason | Clay balls, bricks, quartz, stone, terracotta, rare flower pot, rare stonecutter. |
| Nitwit | Bread, sticks, poisonous potato, flowers, dirt, rare emeralds. |
| Unemployed | Bread, sticks, wheat seeds, rare emeralds, rare apple. |

## Wandering traders

Wandering traders are included in both drop and retaliation systems.

- Can retaliate when attacked.
- Can rally nearby wandering traders.
- Can become hostile when their trader llama is attacked.
- Can block trading while hostile.
- Can be pacified with emeralds.
- Can pick up and use weapons when threatened.
- Can drop 1-5 emeralds.
- Can drop an invisibility potion.
- Can drop a safe copy of one current trade result, with the count capped by the amount sold by that trade.

## Configuration

The mod registers a common config with grouped settings.

### `general`

- Enable or disable villager drops.
- Enable or disable wandering trader drops.
- Enable or disable villager/trader retaliation.

### `balance`

- Toggle baby villager custom loot support. Disabled by default.
- Require player kills for profession loot.
- Configure emerald drop chance.
- Configure bread drop chance.
- Configure profession loot chance.
- Configure rare drop chance.
- Configure very rare drop chance.

### `retaliation`

- Configure whether attacks only anger the hit villager.
- Configure whether killing a villager angers nearby villagers.
- Configure the nearby kill aggro radius.
- Configure anger duration in ticks.
- Configure whether nearby villagers ignore creative/spectator players.

### `combat`

- Toggle combat roles for weaponsmiths, toolsmiths, armorers, fletchers, and butchers.
- Configure temporary combat weapon drop chance.
- Configure hard-mode combat weapon enchant chance.
- Toggle farmer bread/self-healing behaviour.
- Toggle cleric potion behaviour.
- Configure passive cleric ally healing range.
- Configure passive cleric ally healing health threshold.
- Configure whether passive cleric ally healing requires line of sight.

### `wanderer`

- Toggle wandering trader emerald drops.
- Toggle wandering trader invisibility potion drops.
- Toggle wandering trader random current trade drops.
- Configure random current trade drop chance.

## Compatibility notes

- Built for NeoForge 1.21.1.
- Uses vanilla entity events and AI/memory adjustments rather than replacing villager entities.
- Uses NeoForge item tags for melee, mining, bow, spear, and mace weapon detection where possible.
- Retaliation is temporary and target-specific; the mod does not make all villagers permanently hostile.

## License

GNU General Public License v3.0.