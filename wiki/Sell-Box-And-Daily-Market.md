# Sell Box and Daily Market

The Sell Box is a public one-slot market container. Put a saleable stack in its slot and press
**Sell** to convert the whole stack at today's price. Putting another valid stack into an occupied
box sells the old stack first, as one transaction, and leaves the new stack pending.

The box keeps an exact shared balance. **Collect** moves only whole primary-currency items that fit
in the player's inventory; any fractional remainder stays in the box. Hoppers and other item
handlers insert through the top or sides and extract whole primary-currency items from the bottom.
An assigned output courier can deposit saleable items, while a courier can collect currency from
assigned Supplies storage. Pending sale items are never exposed as courier supplies.

## Price definitions

Add one JSON file per definition at:

```text
data/<namespace>/sell_prices/<path>.json
```

The resource path is the definition ID. A fixed price uses positive integer counts:

```json
{
  "item": "minecraft:coal",
  "item_count": 15,
  "currency_count": 1
}
```

Either count may instead be an inclusive range:

```json
{
  "item": "minecraft:coal",
  "item_count": {
    "min": 15,
    "max": 24
  },
  "currency_count": 1
}
```

The unit price is `currency_count / item_count`. Every distinct reduced ratio in the configured
ranges is a candidate. The server selects one deterministically from the world seed, definition ID,
and global overworld day. A definition with multiple candidates does not repeat the same candidate
on consecutive days.

The active currency item and every item matched by the configured currency tags are always
unsaleable, even if a price file names them. Item matching ignores durability and components.

## Override, add, or disable

- Add a new resource path to add an item.
- Use the same namespace and resource path in a higher-priority pack to replace a definition.
- Replace the same resource path with the following file to remove the lower-priority definition:

```json
{
  "enabled": false
}
```

If two active definitions use different resource paths for the same item, the lexicographically later
resource ID wins deterministically and the server reports a datapack diagnostic. Pack priority only
controls replacement at the same resource path. Invalid ranges, unknown items, and unknown fields are
also reported during reload.

The built-in pack contains definitions derived from the direct Minecraft 1.21.1 villager and
wandering-trader offers. Demand changes, reputation discounts, mod-added trades, and standalone
auxiliary inputs are not included.
