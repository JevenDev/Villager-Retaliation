# Sell Box And Daily Market

The Sell Box is a public one-slot market container. Put a saleable stack in its slot and press **Sell** to convert the whole stack at today's village price.

Putting another valid stack into an occupied box sells the old stack as one transaction. The new stack remains in the box until it is sold.

The box keeps an exact shared balance. **Collect** moves only whole primary-currency items that fit in the player's inventory. Any fraction smaller than one item stays in the balance until later sales add enough value.

Hoppers and other item handlers insert sale items through the top or sides. They extract whole primary-currency items from the bottom. An assigned output courier can deposit saleable items. A courier can collect currency from assigned Supplies storage. Pending sale items are never exposed as courier supplies.

## Price Definition Path

Add one JSON file for each item or item-tag price:

```text
data/<namespace>/sell_prices/<path>.json
```

The namespace and path become the definition ID. For example:

```text
data/my_pack/sell_prices/coal.json
```

creates `my_pack:coal`.

## Fixed Price Example

```json
{
  "item": "minecraft:coal",
  "item_count": 15,
  "currency_count": 1,
  "market_group": "villagerretaliation:fuel"
}
```

Before daily demand and local supply adjustments, 15 coal are worth one primary-currency item. A stack of 30 coal starts from a value of two.

Market adjustments can produce a fractional result. For example, a final value of 1.75 adds that exact amount to the box balance. The player can collect one item now, while 0.75 remains for later.

## Item Tag Example

```json
{
  "item": "#minecraft:logs",
  "item_count": 5,
  "currency_count": 1,
  "market_group": "villagerretaliation:logs"
}
```

Prefix an item tag with `#` to apply one price definition to every item currently in that tag, including modded members. Tags are resolved again after `/reload`. If `market_group` is omitted, a tag definition defaults to the tag ID without the `#`.

An unknown or empty tag is rejected and reported by datapack diagnostics.

## Daily Price Range Example

```json
{
  "item": "minecraft:coal",
  "item_count": {
    "min": 15,
    "max": 24
  },
  "currency_count": 1,
  "market_group": "villagerretaliation:fuel"
}
```

This allows the daily base offer to range from 15 coal per currency item through 24 coal per currency item. Each village chooses a daily value from the valid range.

The choice is stable for that village and day. Reloading does not reroll it. Villages can have different prices on the same day, and a multi-value definition does not use the same choice on two consecutive days in one village.

## Fields

| Field | Required | Meaning |
| --- | --- | --- |
| `item` | Yes | Registered item ID or `#`-prefixed item tag that can be sold. |
| `item_count` | Yes | Fixed positive count or an inclusive `min` and `max` range. This is the amount sold. |
| `currency_count` | Yes | Fixed positive count or an inclusive range. This is the base currency value. |
| `market_group` | No | Demand and supply group shared with related items. Defaults to the item ID, or to the tag ID for a tag selector. |
| `enabled` | No | Set to `false` to disable a lower-priority definition at the same resource path. |

`item_count` and its maximum cannot exceed 256. Each count range can contain at most 256 values.

When both counts use ranges, the mod considers every distinct valid value of `currency_count / item_count`. Equivalent fractions count as one price. The village then chooses one of those prices for the day.

## Market Groups And Supply Pressure

Items in the same `market_group` share two village-specific adjustments:

- Daily demand can raise or lower the group's base rate.
- Completed sales add supply pressure, which lowers later payouts until the pressure recovers over subsequent days.

Other villages keep separate rates and supply pressure. If you replace a built-in grouped item, keep its built-in `market_group` unless you intentionally want it to use a separate market.

The primary currency item and every item matched by the configured currency tags are never saleable. A sell-price file cannot override that safety rule.

Item matching uses the item ID or current item-tag membership. Durability and data components do not create separate prices.

## Add, Replace, Or Disable

Add a new resource path to add a price.

Use the same namespace and path in a higher-priority pack to replace a definition.

Disable a lower-priority definition by replacing the same resource path with:

```json
{
  "enabled": false
}
```

If two different definition IDs select the same item, including through overlapping tags, the ID that sorts later wins. The server also reports the conflict in datapack diagnostics. Pack priority decides replacement only when both packs use the same resource path.

Invalid ranges, unknown items or tags, and unknown fields are reported during reload.

## Built-In Price Basis

Built-in definitions are based on direct Minecraft 1.21.1 villager and wandering-trader offers. They do not copy live trade demand, reputation discounts, mod-added offers, or secondary recipe inputs. The village market applies its own daily demand and supply pressure after the base definition.
