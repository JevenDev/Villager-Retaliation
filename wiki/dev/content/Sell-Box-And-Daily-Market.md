# Sell Box And Daily Market

The Sell Box is a one-slot village market. Put a saleable stack in the slot and press **Sell**; the box uses that village's price for the day and sells the whole stack in one transaction.

If you insert a second valid stack while the slot is occupied, the first stack sells and the new one becomes pending. The box keeps the exact balance, including fractions. **Collect** gives the player as many whole currency items as will fit and leaves the remainder in the box.

Automation follows the same rules as the screen. Hoppers and other item handlers insert through the top or sides and extract whole currency items from the bottom. Assigned output couriers can deliver sale items, and supply couriers can collect the finished currency. None of these routes bypasses the stack checks described below.

## Where Price Files Go

Put sell-price files here:

```text
data/<namespace>/sell_prices/<path>.json
```

The namespace and path become the definition ID. A file at `data/my_pack/sell_prices/coal.json` creates `my_pack:coal`.

## Use Explicit Rates For New Packs

New definitions should put prices in a `rates` array. Each entry is one exchange-rate family, so its `item_count` always stays paired with its `currency_count`.

```json
{
  "item": "minecraft:coal",
  "rates": [
    {
      "item_count": 15,
      "currency_count": 1
    }
  ],
  "market_group": "villagerretaliation:fuel"
}
```

Before demand and supply adjustments, 15 coal are worth one currency item. A stack of 30 starts from a value of two.

Either side can be a fixed positive integer or an inclusive `min` and `max` range:

```json
{
  "item": "#minecraft:logs",
  "rates": [
    {
      "item_count": {
        "min": 5,
        "max": 8
      },
      "currency_count": 1
    }
  ],
  "market_group": "villagerretaliation:logs"
}
```

Here, each village can choose from the generated rates of 5, 6, 7, or 8 logs per currency. The choice is stable for that village and day; reloading does not reroll it.

A currency range works the same way:

```json
{
  "item": "minecraft:diamond",
  "rates": [
    {
      "item_count": 1,
      "currency_count": {
        "min": 10,
        "max": 15
      }
    }
  ]
}
```

When both sides of one rate are ranges, every combination belongs to that one family. Equivalent fractions are collapsed before the daily choice is made.

## Discrete Prices Stay Discrete

Use separate rate entries when only a few exact prices should be possible:

```json
{
  "item": "#vr-food-sellbox:cocktails",
  "rates": [
    {
      "item_count": 1,
      "currency_count": 10
    },
    {
      "item_count": 1,
      "currency_count": 15
    },
    {
      "item_count": 1,
      "currency_count": 25
    }
  ],
  "market_group": "vfs:cocktails"
}
```

That produces 10, 15, or 25 currency per cocktail. It does not quietly fill in 11 through 24. There is no index pairing between separate item-count and currency-count arrays; the rate object is the association.

Different shapes can live beside each other:

```json
{
  "item": "minecraft:iron_ingot",
  "rates": [
    {
      "item_count": 5,
      "currency_count": 1
    },
    {
      "item_count": 10,
      "currency_count": 3
    },
    {
      "item_count": 20,
      "currency_count": 8
    }
  ]
}
```

## Existing beta.13 Files Still Work

The older top-level pair remains supported:

```json
{
  "item": "minecraft:charcoal",
  "item_count": {
    "min": 12,
    "max": 20
  },
  "currency_count": 1
}
```

It loads as one rate with the same two counts. Keep old packs as they are if they are working, but prefer `rates` when writing something new. A file cannot use `rates` and the legacy top-level pricing fields together; the server reports the conflict and skips that definition.

## Items And Tags

`item` accepts a registered item ID or a `#`-prefixed item tag. One tag definition applies to every current member, including modded members, so a large tag does not need one JSON file per item.

Tags are resolved again on `/reload`. If `market_group` is omitted, an item definition defaults to the item ID and a tag definition defaults to the tag ID without `#`. Unknown or empty tags are reported and skipped.

## Matching Item Components

Use `components` when two stacks of the same item should have different prices. Values are decoded with the registered Minecraft 1.21.1 data-component codec, including component types added by other mods.

For example, a level-six Miner's Star from Kaleidoscope Tavern can have its own price (for pink lol):

```json
{
  "item": "kaleidoscope_tavern:miners_star",
  "components": {
    "kaleidoscope_tavern:brew_level": 6
  },
  "priority": 10,
  "rates": [
    {
      "item_count": 1,
      "currency_count": 15
    }
  ],
  "market_group": "kaleidoscope_tavern:miners_stars"
}
```

Exact values use the component's normal codec representation. Numeric component values can use inclusive ranges:

```json
{
  "item": "example:graded_gem",
  "components": {
    "example:quality": {
      "min": 3,
      "max": 6
    }
  },
  "rates": [
    {
      "item_count": 1,
      "currency_count": 8
    }
  ]
}
```

A range can have only `min` or only `max`. Ranges are rejected for components whose decoded value is not numeric.

For NBT-like custom item data, use the vanilla `minecraft:custom_data` component:

```json
{
  "item": "example:keepsake",
  "components": {
    "minecraft:custom_data": {
      "maker": "ada",
      "edition": 3
    }
  },
  "rates": [
    {
      "item_count": 1,
      "currency_count": 4
    }
  ]
}
```

Custom-data objects use subset matching. The stack must contain the required keys and values, but unrelated custom data does not prevent a match.

Invalid component IDs, malformed codec values, and nonnumeric range mistakes produce a datapack diagnostic and skip the bad definition instead of breaking the reload.

## Exact Component Shorthand

For a specific item with exact component values, vanilla item syntax is also accepted:

```json
{
  "item": "kaleidoscope_tavern:miners_star[kaleidoscope_tavern:brew_level=6]",
  "rates": [
    {
      "item_count": 1,
      "currency_count": 15
    }
  ]
}
```

Minecraft's own item parser handles this form. Use the structured `components` object for ranges, custom-data predicates, and tag selectors.

## Remaining Durability

`durability` means durability remaining, not raw damage:

```json
{
  "item": "#example:valuable_pickaxes",
  "components": {
    "example:quality": {
      "min": 3
    }
  },
  "durability": {
    "min": 250
  },
  "rates": [
    {
      "item_count": 1,
      "currency_count": 5
    },
    {
      "item_count": 1,
      "currency_count": 8
    },
    {
      "item_count": 1,
      "currency_count": 12
    }
  ],
  "market_group": "example:tools"
}
```

`min: 250` means at least 250 durability remains. `max: 25` means 25 or less remains. You can provide both bounds. Non-damageable items never match a durability predicate, and negative, reversed, or impossible conditions are reported during reload.

## Overlapping Definitions And Priority

Several definitions can target the same base item. This is useful for a broad fallback plus a component-specific price:

```json
{
  "item": "kaleidoscope_tavern:miners_star",
  "rates": [
    {
      "item_count": 1,
      "currency_count": 5
    }
  ]
}
```

```json
{
  "item": "kaleidoscope_tavern:miners_star",
  "components": {
    "kaleidoscope_tavern:brew_level": 6
  },
  "priority": 10,
  "rates": [
    {
      "item_count": 1,
      "currency_count": 15
    }
  ]
}
```

The server first gathers item and tag selectors that include the stack, then checks components and durability. The matching definition with the highest `priority` wins. The default priority is `0`; having more predicates does not raise it automatically.

Equal-priority ties are deterministic: the later-sorting definition ID wins, matching the old beta.13 conflict behavior. If the predicates can overlap, reload diagnostics ask you to set an explicit priority rather than leaving that tie accidental.

## Fields

| Field | Required | Meaning |
| --- | --- | --- |
| `item` | Yes | Registered item ID, exact item-component shorthand, or `#`-prefixed item tag. |
| `rates` | New files | One or more associated `item_count` and `currency_count` families. |
| `item_count` + `currency_count` | Legacy alternative | Backward-compatible top-level pair, treated as one rate. Do not combine it with `rates`. |
| `components` | No | Exact codec-backed values or numeric min/max predicates. |
| `durability` | No | Remaining-durability `min`, `max`, or both. |
| `priority` | No | Explicit winner among matching definitions. Defaults to `0`. |
| `market_group` | No | Demand and supply group shared by related definitions. |
| `enabled` | No | Set to `false` to disable a lower pack's resource at the same path. |

Count values must be positive. `item_count` cannot exceed 256, each count range can contain at most 256 values, a definition can contain at most 256 rates, and total generated combinations are capped. These limits keep a malformed pack from creating an enormous candidate list.

## The Village Market Still Works The Same Way

Predicates decide whether a stack is eligible and which definition wins. After that selection, the existing market does the rest:

- resolve the local village
- choose the village's daily demand band
- select one candidate base rate deterministically
- apply that village's current supply pressure
- calculate the exact payout
- record the completed sale's pressure

`market_group` is still the key that makes related goods share demand and supply pressure. Different villages keep separate pressure. Adding a component predicate does not create a second economy.

The configured primary currency and accepted currency tags remain unsaleable, even if a sell-price file targets them.

## Add, Replace, Or Disable

A new resource path adds a definition. Reusing the same namespace and path in a higher-priority datapack replaces that resource. To disable a lower pack's file, replace the same path with:

```json
{
  "enabled": false
}
```

Invalid ranges, selectors, components, and priorities are reported during reload. A bad entry is skipped; it does not crash the server.

## Built-In Price Basis

Built-in definitions are based on direct Minecraft 1.21.1 villager and wandering-trader offers. They do not copy live trade demand, reputation discounts, mod-added offers, or secondary recipe inputs. The village market applies its own daily demand and supply pressure after choosing the matching definition.
