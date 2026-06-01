# Skill Trades

Skill trades are extra merchant offers gated by a villager's Skills rather than only by vanilla trade level.

## Path

```text
data/<namespace>/skill_trades/<file>.json
```

## Minimal Entry

```json
{
  "entries": [
    {
      "id": "my_pack.cartographer_basic_map",
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

## Common Usage Areas

### Low-Skill Extra Stock

Use `min_rank` plus `max_rank` to keep a trade in the low tier only.

```json
"min_rank": "novice",
"max_rank": "apprentice"
```

### High-Skill Specialty Offer

```json
{
  "id": "my_pack.farmer_master_hoe",
  "professions": ["minecraft:farmer"],
  "skills": ["villagerretaliation:farming"],
  "min_rank": "master",
  "villager_level": 5,
  "cost": { "item": "minecraft:emerald", "count": 18 },
  "result": {
    "item": "minecraft:diamond_hoe",
    "count": 1
  },
  "conditions": {
    "config_flags": ["skillTradeAllowHighTierEquipment"]
  }
}
```

### Targetable Special Order

Beta.12 lets high-reputation players request specific skill trades directly.

```json
"request": {
  "targetable": true,
  "display_priority": 20,
  "min_reputation": "respected",
  "wait_days": 2,
  "cooldown_days": 3
}
```

### Wandering Trader Entry

Use the wandering trader profession id:

```json
{
  "id": "my_pack.wandering_trader_shell",
  "professions": ["minecraft:wandering_trader"],
  "skills": ["villagerretaliation:trading"],
  "min_rank": "master",
  "chance": 0.3,
  "cost": { "item": "minecraft:emerald", "count": 15 },
  "result": { "item": "minecraft:nautilus_shell", "count": 1 }
}
```

## Main Fields

| Field | Use |
| --- | --- |
| `professions` | Which villagers can roll the entry |
| `skills` | Which skill ids gate it |
| `min_rank` / `max_rank` | Skill rank band |
| `villager_level` | Vanilla trade level gate |
| `cost` | Input item and count |
| `result` | Output item and count |
| `chance` / `weight` | Selection tuning |
| `quality_scaling` | Rank-based improvements |
| `request` | Special Order metadata |

## Best Practice

Model skill trades in bands:

- low tier for novice or apprentice villagers
- mid tier for skilled or expert villagers
- rare high tier for expert or master villagers

That gives progression without flooding early villagers with endgame stock.
