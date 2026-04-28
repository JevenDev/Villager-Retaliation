# Commonfolk

Commonfolk is a Vanilla+ villager consistency mod for NeoForge 1.21.1. It gives villagers profession-based drops and temporary defensive behavior so they can finally fend for themselves.

Farmers carry crops, fletchers carry arrows, clerics use potions, and skilled tradespeople can fight back when attacked. Hit one villager and only that villager retaliates. Kill one, and nearby adult villagers remember.

## Features

- Adult villager base drops for emeralds and bread, controlled by config chances
- Profession-specific loot pools for every vanilla villager profession
- Baby villagers never drop loot
- Wandering Traders can drop emeralds, an invisibility potion, and one of their current trades
- Temporary villager retaliation with per-villager anger tracking
- Killing a villager can anger nearby adult villagers in a configurable radius
- Profession-related combat roles for weaponsmiths, toolsmiths, armorers, fletchers, butchers, farmers, and clerics
- Common config categories for general toggles, balance, retaliation, combat, and wanderer drops

## Profession Drops

Profession loot is rolled from Vanilla+ pools instead of dropping everything at once.

- Farmers: crops, seeds, apples, and suspicious stew
- Leatherworkers: leather, rabbit hide, leather armor, dyes, and very rarely saddles
- Fishermen: fish, rods, string, sticks, lily pads, and very rarely nautilus shells
- Librarians: paper, books, ink, feathers, bookshelves, and very rarely enchanted books
- Shepherds: wool, shears, wheat, dyes, carpets, and rarely banners
- Butchers: meats, leather, smokers, and rarely worn iron axes
- Clerics: rotten flesh, redstone, lapis, glowstone, potions, bottles o' enchanting, and very rarely ender pearls
- Cartographers: paper, maps, compasses, ink, feathers, and rarely cartography tables
- Toolsmiths, weaponsmiths, and armorers: worn iron gear, ingots, coal, sticks, and rarely workstation blocks
- Fletchers: bows, crossbows, arrows, flint, feathers, sticks, string, and tripwire hooks
- Masons: clay, bricks, quartz, stone, terracotta, flower pots, and stonecutters
- Nitwits and unemployed villagers: typical villager items like emeralds and bread

## Retaliation Rules

- Damaging a villager only angers that specific adult villager
- Killing an adult villager can anger nearby adult villagers within the configured radius
- Anger expires after the configured duration
- Baby villagers never retaliate
- Creative and spectator players can be ignored by retaliation when configured
- Villagers do not become globally hostile and do not target unrelated players

## Wandering Trader Drops

When enabled, Wandering Traders can drop:

- 1-5 emeralds
- 1 invisibility potion
- One random current trade result, with the dropped count capped by the amount the trade sells

Trade costs are never duplicated, and the trade result is copied safely so merchant offers are not mutated.

## Config

- `general`: enables villager drops, trader drops, and retaliation
- `balance`: controls drop chances, rare chances, and profession loot player-kill requirements
- `retaliation`: controls nearby kill aggro, radius, duration, and creative-player handling
- `combat`: controls which professions can use defensive or combat behavior
- `wanderer`: controls Wandering Trader emerald, potion, and trade-result drops
