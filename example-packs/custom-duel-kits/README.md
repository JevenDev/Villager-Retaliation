# Custom Duel Kits

Place this folder in a world's `datapacks` directory and run `/reload`. The `duel_examples:champion` kit is then added to the duel loadout option list.

A kit lives at `data/<namespace>/duel_kits/<path>.json`. That path becomes its stable ID. Higher-priority datapacks can replace a kit by using the same ID.

- `name`: short text shown in the selected-kit summary.
- `description`: text shown in the kit option list.
- `sort_order`: lower values appear first. The ID breaks ties.
- `combat_style`: `melee` or `ranged`. Controls the villager skill trained.
- `bring_your_own`: optional. When true, both item sections must be absent.
- `player` and `villager`: optional temporary item assignments.
- `inventory`: entries with a zero-based `slot` and native Minecraft
  `ItemStack` in `stack`.
- `equipment`: supports `mainhand`, `offhand`, `feet`, `legs`, `chest`,
  and `head`.

Stacks use Minecraft 1.21.1's native item-stack JSON format. This means modded items, enchantments, and data components work without special integration when their mods are installed on the server. For example, replace `minecraft:diamond_sword` with `examplemod:dueling_blade`, or add a modded enchantment to the `minecraft:enchantments.levels` object by its namespaced ID.

Invalid files are skipped with a warning identifying the kit and source pack. A duel request is resolved against the current server registry, so clients cannot submit removed or unknown kit IDs. Original player and villager items, effects, health, food, and equipment are restored after the duel or crash recovery.
