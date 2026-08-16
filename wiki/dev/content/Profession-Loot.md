# Profession Loot

Profession loot adds datapack loot-table rolls when a villager dies. Rules can target vanilla or modded professions, and several matching rules can roll from the same death.

By default, `balance.requirePlayerKillForProfessionLoot` requires a player-caused kill before profession loot runs.

## Paths

The rule file must use the `villagerretaliation` namespace:

```text
data/villagerretaliation/profession_loot/<file>.json
```

The referenced loot table can use any namespace:

```text
data/<namespace>/loot_table/<path>.json
```

A rule value such as `my_pack:villager/profession/alchemist/common` points to:

```text
data/my_pack/loot_table/villager/profession/alchemist/common.json
```

## Complete Example

Create the rule file:

```text
data/villagerretaliation/profession_loot/my_pack_alchemist.json
```

```json
{
  "tables": [
    {
      "id": "my_pack.alchemist.common",
      "professions": ["examplemod:alchemist"],
      "loot_table": "my_pack:villager/profession/alchemist/common",
      "chance": "always"
    }
  ]
}
```

Then create the loot table:

```text
data/my_pack/loot_table/villager/profession/alchemist/common.json
```

```json
{
  "type": "minecraft:entity",
  "pools": [
    {
      "rolls": 1,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "minecraft:amethyst_shard"
        }
      ]
    }
  ]
}
```

When an alchemist villager dies and the server's player-kill requirement passes, the rule always rolls this loot table.

## Rule Fields

| Field | Required | Meaning |
| --- | --- | --- |
| `id` | Recommended | Stable rule ID used for replacement and removal. |
| `professions` | No | One or more villager profession IDs. An omitted or empty list matches every profession. Vanilla IDs can omit `minecraft:`. |
| `loot_table` | Yes | Namespaced Minecraft loot table ID. |
| `chance` | No | `always`, `rare`, `very_rare`, or a number from `0.0` to `1.0`. The default is `always`. |
| `requires_villager_armed` | No | Match only villagers with a usable weapon. |
| `requires_villager_unarmed` | No | Match only villagers without a usable weapon. |
| `remove` | No | Remove an earlier rule with the same `id`. |

`rare` and `very_rare` use the server's configured rare-drop chances. A numeric chance is clamped to the range from 0 to 1.

Every matching rule rolls independently. Use this to separate common, rare, and very rare drops for one profession.

## Add, Replace, Or Remove

Files are combined in resource load order.

Reuse an `id` to replace an earlier rule:

```json
{
  "tables": [
    {
      "id": "villagerretaliation.profession_loot.farmer.rare",
      "professions": ["minecraft:farmer"],
      "loot_table": "my_pack:villager/profession/farmer/rare",
      "chance": 0.2
    }
  ]
}
```

Remove one rule:

```json
{
  "tables": [
    {
      "id": "villagerretaliation.profession_loot.farmer.rare",
      "remove": true
    }
  ]
}
```

Clear every rule loaded before the current file:

```json
{
  "replace": true,
  "tables": []
}
```

Give every rule an explicit ID. Rules without one receive a generated ID based on file path and array position, which is harder to override safely.

## Loot Table Context

Profession loot uses Minecraft's entity loot context. The table can inspect the dead villager, death position, damage source, attacking entity, direct attacking entity, and last player damage when available. Player luck is included for player kills.

Keep the loot table type as `minecraft:entity` unless a specific integration requires another supported shape.
