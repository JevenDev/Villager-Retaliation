# Skill Trades

Skill trades are extra villager and wandering trader offers controlled by a villager's profile Skills. Reputation still handles trust and price pressure separately; Skills decide what extra stock can appear, the quality ceiling of that stock, and how rare it is.

Low skill ranks are represented with explicit low-tier entries. Higher skill ranks use separate medium and high-tier entries plus optional quality scaling.

## File Path

Put skill trade files in:

```text
data/<namespace>/skill_trades/<file>.json
```

The built-in pack uses:

```text
data/villagerretaliation/skill_trades/cartographer.json
data/villagerretaliation/skill_trades/farmer.json
data/villagerretaliation/skill_trades/fisherman.json
data/villagerretaliation/skill_trades/fletcher.json
data/villagerretaliation/skill_trades/librarian.json
data/villagerretaliation/skill_trades/cleric.json
data/villagerretaliation/skill_trades/smithing.json
data/villagerretaliation/skill_trades/other_professions.json
data/villagerretaliation/skill_trades/wandering_trader.json
```

## Minimal Example

```json
{
  "entries": [
    {
      "id": "example:cartographer_low_basic_maps",
      "professions": ["minecraft:cartographer"],
      "skills": ["villagerretaliation:cartography"],
      "min_rank": "novice",
      "max_rank": "apprentice",
      "villager_level": 1,
      "chance": 0.8,
      "weight": 12,
      "cost": { "item": "minecraft:emerald", "count": 8 },
      "result": { "item": "minecraft:map", "count": 1 },
      "max_uses": { "base": 4 },
      "xp": 4,
      "price_multiplier": 0.05,
      "quality_scaling": true
    }
  ]
}
```

`max_rank` makes this a low-skill pool entry: novice and apprentice cartographers can roll it, but skilled, expert, and master cartographers move on to better pools.

## Master Farmer Example

```json
{
  "id": "example:farmer_master_diamond_hoe",
  "professions": ["minecraft:farmer"],
  "skills": ["villagerretaliation:farming"],
  "min_rank": "master",
  "villager_level": 5,
  "chance": 0.32,
  "weight": 5,
  "cost": { "item": "minecraft:emerald", "count": 18 },
  "result": {
    "item": "minecraft:diamond_hoe",
    "count": 1,
    "enchantments": {
      "mode": "random_from",
      "candidates": [
        "minecraft:unbreaking",
        "minecraft:efficiency",
        "minecraft:fortune"
      ],
      "level_by_skill": true,
      "min_level": 1,
      "max_level": 3
    }
  },
  "max_uses": { "base": 2 },
  "xp": 20,
  "price_multiplier": 0.1,
  "conditions": {
    "config_flags": ["skillTradeAllowHighTierEquipment"]
  },
  "quality_scaling": {
    "enabled": true,
    "count_by_skill": true,
    "cost_by_skill": true,
    "max_uses_by_skill": true,
    "xp_by_skill": false,
    "rare_chance_by_skill": true,
    "enchantments_by_skill": true
  }
}
```

## Fields

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `id` | string | generated fallback | Unique entry id used in logs and replacement. |
| `professions` | string or array | any | Villager professions such as `minecraft:farmer`. Use `minecraft:wandering_trader` for wandering trader entries. |
| `skills` | string or array | required | Skill ids such as `villagerretaliation:farming`, `smithing`, or `trading`. If several are listed, the highest matching skill value is used. |
| `min_rank` | string | `novice` | `novice`, `apprentice`, `skilled`, `expert`, or `master`. |
| `max_rank` | string | none | Optional inclusive upper rank. If absent, the entry is eligible at `min_rank` and above. If present below `min_rank`, the entry is skipped with a warning. |
| `villager_level` | integer | `1` | Vanilla villager trade level, 1 through 5. Ignored by wandering traders. |
| `chance` | number | `1.0` | Per-offer chance before weighted selection. Values are clamped to 0..1. |
| `weight` | integer | `1` | Weight when more than one eligible skill offer can appear for the same profession and level. |
| `cost` | object | 1 emerald | Primary cost item and count. |
| `result` | object | required | Result item and count. Use `items` to randomly choose one item from a list. |
| `max_uses` | integer or object | `6` | Static or skill-scaled stock. |
| `xp` | integer | `0` | Villager XP reward. |
| `price_multiplier` | number | `0.05` | Vanilla merchant price multiplier. Reputation pricing uses this separately. |
| `conditions` | object | none | Optional config gates. |
| `quality_scaling` | boolean or object | disabled | Optional rank-based quality scaling for this entry. |

Bad entries log useful warnings and are skipped instead of crashing the load.

## Rank Bands

Use `min_rank` and `max_rank` to model profession competence:

| Pool | Suggested ranks | Example role |
| --- | --- | --- |
| Low | `min_rank: novice`, `max_rank: apprentice` | Basic stock, lower counts, weaker stock, lower uses. |
| Medium | `min_rank: skilled`, `max_rank: expert` | Normal or improved profession stock. |
| High | `min_rank: expert` or `master` | Rare, specialty, or higher-quality offers. |

High-skill villagers do not automatically roll low-skill entries when those entries have `max_rank`. This is how a master farmer avoids novice-only bread or bone meal offers and instead rolls golden carrots, rare stew, or high-tier tool entries.

## Quality Scaling

`quality_scaling: true` enables default scaling:

```json
"quality_scaling": true
```

Use an object for per-entry control:

```json
"quality_scaling": {
  "enabled": true,
  "count_by_skill": true,
  "cost_by_skill": true,
  "max_uses_by_skill": true,
  "xp_by_skill": false,
  "rare_chance_by_skill": true,
  "enchantments_by_skill": true
}
```

Quality scaling is conservative:

- Novice and apprentice ranks can receive lower result counts, higher emerald base costs, lower stock, lower rare chance, and weak enchantment quality.
- Skilled rank is the baseline.
- Expert and master ranks can receive modest count, stock, rare chance, and enchantment quality bonuses.
- Emerald cost scaling only changes the skill trade's base emerald cost. Reputation pricing is applied separately afterward.
- Enchantment levels are still capped by the entry `max_level`, the enchantment's own max level, and `skillTradeMaxEnchantmentLevel`.

If `skillTradeQualityScaling` is disabled, entries still load but the new quality-scaling adjustments are bypassed. If `skillTradeLowSkillPenalties` is disabled, low-rank penalties are suppressed while high-rank bonuses can still apply.

## Cost

```json
"cost": {
  "item": "minecraft:emerald",
  "count": 16,
  "skill_discount": {
    "enabled": true,
    "max_percent": 20
  }
}
```

`skill_discount` only changes this skill-generated offer. It reduces the base cost as the relevant skill rises above `min_rank`, up to `max_percent`. Quality scaling can then adjust the skill trade's base emerald cost. Reputation discounts and penalties are still applied afterward by the reputation pricing system.

## Result And Enchantments

Use one item:

```json
"result": { "item": "minecraft:fishing_rod", "count": 1 }
```

Or choose one item randomly:

```json
"result": {
  "items": ["minecraft:bow", "minecraft:crossbow"],
  "count": 1
}
```

Enchantments support `none`, `random_from`, and `fixed`.

```json
"enchantments": {
  "mode": "random_from",
  "candidates": ["minecraft:luck_of_the_sea", "minecraft:lure", "minecraft:unbreaking"],
  "level_by_skill": true,
  "min_level": 1,
  "max_level": 3
}
```

Incompatible enchantments are skipped instead of crashing.

## Max Uses

```json
"max_uses": {
  "base": 2,
  "bonus_by_skill": true,
  "max_bonus": 2
}
```

`bonus_by_skill` adds stock as the relevant skill rises above the minimum rank, capped by `max_bonus`. `quality_scaling.max_uses_by_skill` can then apply the broader rank quality multiplier.

## Conditions

The first supported condition type is config flags:

```json
"conditions": {
  "config_flags": ["skillTradeAllowHighTierEquipment"]
}
```

Use `disabled_config_flags` when an entry should only appear while a flag is off:

```json
"conditions": {
  "disabled_config_flags": ["skillTradeAllowHighTierEquipment"]
}
```

Supported flags:

```text
enableSkillTradeOverhaul
skillTradeAllowHighTierEquipment
skillTradeAllowSpecialArrows
skillTradeAllowRareSpecialtyTrades
```

The global `enableSkillTradeOverhaul` config disables all skill-generated offers before entry conditions are checked.

## Wandering Trader Entries

Use `minecraft:wandering_trader` as the profession. Set `pool` to `generic` or `rare`.

```json
{
  "id": "example:wandering_trader_master_shell",
  "professions": ["minecraft:wandering_trader"],
  "pool": "rare",
  "skills": ["villagerretaliation:trading"],
  "min_rank": "master",
  "chance": 0.3,
  "cost": { "item": "minecraft:emerald", "count": 15 },
  "result": { "item": "minecraft:nautilus_shell", "count": 1 },
  "max_uses": { "base": 2 },
  "xp": 10,
  "price_multiplier": 0.05,
  "quality_scaling": true
}
```

Wandering traders can use skill trade pools, but vanilla trade-level milestone growth currently applies only to normal villagers.

## Skill Growth From Trade Levels

Each completed normal villager trade can also add slow fractional progress to the villager's primary profession skill. By default, each trade adds `0.1` progress, so ten regular trades add one visible skill point. Fractional progress is stored on the villager profile as `RegularTradeSkillGrowthProgress`; skill values still display and sync as whole numbers from 1..100.

Regular trade growth is profession-primary only. A farmer's completed trades slowly improve Farming, a cartographer's completed trades slowly improve Cartography, and smithing professions improve their mapped primary skill. This is intentionally separate from reputation pricing and does not affect player trust.

When a normal villager reaches a new vanilla trade level, the mod can improve relevant Skills once for each newly crossed milestone:

```text
1 = Novice
2 = Apprentice
3 = Journeyman
4 = Expert
5 = Master
```

The profile stores `HighestSkillGrowthTradeLevelAwarded`. If the current trade level is greater than the stored value, each missing milestone is awarded once, then the stored value is updated. Repeated trades at the same level do not grant more skill growth.

Default primary growth follows the milestone:

```text
Apprentice: +1 to +2 primary skill
Journeyman: +2 to +3 primary skill
Expert: +2 to +4 primary skill
Master: +3 to +5 primary skill
```

One related secondary skill may also gain a small configurable amount. All skill values clamp to 1..100.

Examples:

- Cartographers mainly improve Cartography; Scholarship, Survival, or Trading can improve secondarily.
- Farmers mainly improve Farming; Cooking, Gathering, or Animal Handling can improve secondarily.
- Armorers and weaponsmiths mainly improve Smithing; toolsmiths mainly improve Crafting.

## Config

Trade and skill quality settings:

```text
enableSkillTradeOverhaul
skillTradeQualityScaling
skillTradeLowSkillPenalties
skillTradeMaxEnchantmentLevel
skillTradeRareChanceMultiplier
skillTradeAllowHighTierEquipment
skillTradeAllowSpecialArrows
skillTradeAllowRareSpecialtyTrades
enableSkillGrowthFromTradingLevels
enableRegularTradeSkillGrowth
regularTradeSkillGrowthAmount
enableSkillGrowthFeedback
skillGrowthPrimaryMin
skillGrowthPrimaryMax
skillGrowthSecondaryChance
skillGrowthSecondaryMax
```

## Replace And Merge

Files merge additively by default. Later entries with the same `id` replace earlier entries.

```json
{ "replace": true, "entries": [] }
```

`replace: true` clears the accumulated skill trade pool globally before the file's entries are applied. Use it only when you intentionally want to rebuild the full skill trade set.

## Selection Behavior

For each villager profession and trade level, the mod adds one skill-trade pool listing. At offer generation time it:

1. Finds entries matching the profession, level, config conditions, and skill rank range.
2. Rolls each entry's chance, including optional quality scaling.
3. Selects one passing entry by weight.
4. Builds a normal `MerchantOffer`.

Because the final offer is a normal merchant offer, vanilla mechanics and Villager Retaliation reputation pricing continue to work on top of it.
