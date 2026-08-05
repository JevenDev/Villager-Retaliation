# Duel Kits

Duel kits control the temporary equipment offered when a player challenges a villager or another player to a duel. A kit can use vanilla or modded items.

## Path And ID

```text
data/<namespace>/duel_kits/<path>.json
```

The file path becomes the kit ID. For example:

```text
data/my_pack/duel_kits/champion.json
```

creates `my_pack:champion`.

A higher-priority datapack can replace a kit by using the same namespace and path. There is no separate `id` field.

## Small Melee Kit

```json
{
  "name": "iron practice gear",
  "description": "Iron swords and shields.",
  "sort_order": 50,
  "combat_style": "melee",
  "player": {
    "inventory": [
      {
        "slot": 0,
        "stack": {
          "id": "minecraft:iron_sword"
        }
      }
    ],
    "equipment": {
      "offhand": {
        "id": "minecraft:shield"
      }
    }
  },
  "villager": {
    "equipment": {
      "mainhand": {
        "id": "minecraft:iron_sword"
      },
      "offhand": {
        "id": "minecraft:shield"
      }
    }
  }
}
```

This kit puts an iron sword in the player's first inventory slot. It equips the villager with a sword and gives both sides a shield.

## Main Fields

| Field | Required | Meaning |
| --- | --- | --- |
| `name` | Yes | Short name shown for the selected kit. Maximum 128 characters. |
| `description` | Yes | Explanation shown in the kit list. Maximum 512 characters. |
| `sort_order` | No | Lower values appear first. The default is `100`. The kit ID breaks ties. |
| `combat_style` | No | `melee` or `ranged`. The default is `melee`. This controls the skill trained by the villager. |
| `bring_your_own` | No | When `true`, both sides keep their current gear. Do not include `player` or `villager` item sections. |
| `player` | No | Temporary items assigned to the player. |
| `villager` | No | Temporary items assigned to the villager. |

Each participant can have:

| Field | Meaning |
| --- | --- |
| `inventory` | Items placed in numbered inventory slots. Each slot can be used only once. Valid slots are `0` through `255`. |
| `equipment` | Items equipped in `mainhand`, `offhand`, `feet`, `legs`, `chest`, or `head`. |

`stack` uses Minecraft 1.21.1 item stack JSON. A count belongs beside the item ID:

```json
{
  "slot": 1,
  "stack": {
    "id": "minecraft:arrow",
    "count": 64
  }
}
```

Components can add enchantments or modded item data:

```json
{
  "id": "minecraft:diamond_sword",
  "components": {
    "minecraft:enchantments": {
      "levels": {
        "minecraft:sharpness": 3
      }
    }
  }
}
```

Invalid kit files are skipped and identified in the server log. The complete enchanted example is in `example-packs/custom-duel-kits/`.
