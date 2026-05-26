# Skill Trades

Skill trades are extra villager and wandering trader offers unlocked by a villager's profile Skills. Reputation still handles trust and price pressure separately; skill trades decide what extra stock can appear, how good that stock is, and how rare it is.

## File Path

Put skill trade files in:

```text
data/<namespace>/skill_trades/<file>.json
```

The built-in pack uses:

```text
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
      "id": "example:farmer_bone_meal",
      "professions": ["minecraft:farmer"],
      "skills": ["villagerretaliation:farming"],
      "min_rank": "apprentice",
      "villager_level": 2,
      "cost": { "item": "minecraft:emerald", "count": 2 },
      "result": { "item": "minecraft:bone_meal", "count": 16 },
      "max_uses": { "base": 12 },
      "xp": 4,
      "price_multiplier": 0.05
    }
  ]
}
```

## Full Example

```json
{
  "replace": false,
  "entries": [
    {
      "id": "example:farmer_master_diamond_hoe",
      "professions": ["minecraft:farmer"],
      "skills": ["villagerretaliation:farming"],
      "min_rank": "master",
      "villager_level": 5,
      "chance": 0.45,
      "weight": 10,
      "cost": {
        "item": "minecraft:emerald",
        "count": 16,
        "skill_discount": {
          "enabled": true,
          "max_percent": 20
        }
      },
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
      "max_uses": {
        "base": 2,
        "bonus_by_skill": true,
        "max_bonus": 2
      },
      "xp": 20,
      "price_multiplier": 0.1,
      "conditions": {
        "config_flags": ["skillTradeAllowHighTierEquipment"]
      }
    }
  ]
}
```

## Fields

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `id` | string | generated fallback | Unique entry id used in logs and replacement. |
| `professions` | string or array | any | Villager professions such as `minecraft:farmer`. Use `minecraft:wandering_trader` for wandering trader entries. |
| `skills` | string or array | required | Skill ids such as `villagerretaliation:farming`, `smithing`, or `trading`. If several are listed, the highest matching skill value is used. |
| `min_rank` | string | `novice` | `novice`, `apprentice`, `skilled`, `expert`, or `master`. |
| `villager_level` | integer | `1` | Vanilla villager trade level, 1 through 5. Ignored by wandering traders. |
| `chance` | number | `1.0` | Per-offer chance before weighted selection. Values are clamped to 0..1. Rare chances are affected by `skillTradeRareChanceMultiplier` and a small skill bonus. |
| `weight` | integer | `1` | Weight when more than one eligible skill offer can appear for the same profession and level. |
| `cost` | object | 1 emerald | Primary cost item and count. |
| `result` | object | required | Result item and count. Use `items` to randomly choose one item from a list. |
| `max_uses` | integer or object | `6` | Static or skill-scaled stock. |
| `xp` | integer | `0` | Villager XP reward. |
| `price_multiplier` | number | `0.05` | Vanilla merchant price multiplier. Reputation pricing uses this separately. |
| `conditions` | object | none | Optional config gates. |

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

`skill_discount` only changes this skill-generated offer. It reduces the base cost as the relevant skill rises above `min_rank`, up to `max_percent`. Reputation discounts and penalties are still applied afterward by the reputation pricing system.

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

Generated enchantment levels are capped by the lower of the entry `max_level`, the enchantment's own max level, and the `skillTradeMaxEnchantmentLevel` config value. Incompatible enchantments are skipped instead of crashing.

Fixed enchantments can be written as an object:

```json
"enchantments": {
  "mode": "fixed",
  "fixed": {
    "minecraft:unbreaking": 2
  }
}
```

## Max Uses

```json
"max_uses": {
  "base": 2,
  "bonus_by_skill": true,
  "max_bonus": 2
}
```

`bonus_by_skill` adds stock as the relevant skill rises above the minimum rank, capped by `max_bonus`.

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
  "chance": 0.55,
  "cost": { "item": "minecraft:emerald", "count": 14 },
  "result": { "item": "minecraft:nautilus_shell", "count": 1 },
  "max_uses": { "base": 2 },
  "xp": 10,
  "price_multiplier": 0.05
}
```

## Replace And Merge

Files merge additively by default. Later entries with the same `id` replace earlier entries.

```json
{ "replace": true, "entries": [] }
```

`replace: true` clears the accumulated skill trade pool globally before the file's entries are applied. Use it only when you intentionally want to rebuild the full skill trade set.

## Selection Behavior

For each villager profession and trade level, the mod adds one skill-trade pool listing. At offer generation time it:

1. Finds entries matching the profession, level, config conditions, and skill rank.
2. Rolls each entry's chance.
3. Selects one passing entry by weight.
4. Builds a normal `MerchantOffer`.

Because the final offer is a normal merchant offer, vanilla mechanics and Villager Retaliation reputation pricing continue to work on top of it.
