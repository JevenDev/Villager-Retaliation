# Currency And Item Text

Currency data chooses the item used by Villager Retaliation payment systems. Item-text data controls how item counts are written in dialogue and notices.

Use both when replacing emeralds with another currency or when a locale needs plural forms that English rules cannot produce correctly.

## Currency Path

```text
data/villagerretaliation/currency/default.json
```

Currency is fixed to the `villagerretaliation` namespace. Override `default.json` in a higher-priority datapack so there is one clear final definition.

## Currency Example

```json
{
  "item": "examplemod:copper_coin",
  "accepted_items": [
    "examplemod:copper_coin",
    "examplemod:silver_coin"
  ],
  "accepted_tags": [
    "examplemod:coins"
  ],
  "name": "copper coin",
  "plural_name": "copper coins",
  "wallet_label": "Coins",
  "icon_sprite": "examplemod:item/copper_coin",
  "text_color": "#D9824A"
}
```

`item` is the primary currency. Systems that create a payment, refund, wallet withdrawal, or currency drop create this item.

`accepted_items` and `accepted_tags` add items that can be used as payment. They do not change which item the mod creates when paying the player. The primary item is always accepted even if it is omitted from `accepted_items`.

## Currency Fields

| Field | Required | Meaning |
| --- | --- | --- |
| `item` | Yes | Registered primary item ID. An invalid item makes the file unusable. |
| `accepted_items` or `items` | No | Extra registered item IDs accepted as payment. |
| `accepted_tags` or `tags` | No | Item tags accepted as payment. A leading `#` is optional. |
| `name` | No | Singular name used in notices. Defaults to the item's display name. |
| `plural_name` | No | Plural name. Defaults to the singular name plus `s`. |
| `wallet_label` | No | Label in the villager interaction wallet row. |
| `icon_sprite` | No | GUI sprite ID. `textures/` and `.png` are optional in the value. |
| `text_color` | No | Named color or hex RGB color for the wallet amount. |

## Keep The Currency Tag In Sync

Recipes, payment boxes, and client hints also use:

```text
data/villagerretaliation/tags/item/currency.json
```

If the primary or accepted currency items should work in those places, override or extend that item tag too.

```json
{
  "replace": false,
  "values": [
    "examplemod:copper_coin",
    "examplemod:silver_coin"
  ]
}
```

The currency definition and item tag serve different code paths.

## Item Text Path

```text
data/villagerretaliation/item_text/<locale>/<file>.json
```

`en_us` is the fallback. A player's locale is loaded on top of it.

## Simple Item Name Example

Use explicit forms for items with irregular or uncountable names:

```json
{
  "items": {
    "examplemod:copper_coin": {
      "one": "copper coin",
      "other": "copper coins"
    },
    "minecraft:bread": {
      "one": "bread",
      "other": "bread"
    }
  }
}
```

This changes text such as `{held_item}` or a counted payment name. It does not rename the item stack in normal Minecraft tooltips.

## Count Forms

English uses `one` for a count of 1 and `other` for everything else:

```json
{
  "forms": [
    {
      "id": "one",
      "count_pattern": "1",
      "format": "{item}"
    },
    {
      "id": "other",
      "format": "{count} {item}"
    }
  ]
}
```

`count_pattern` is a regular expression matched against the number. The final form without a pattern acts as the fallback.

Locales with more forms can define them in order:

```json
{
  "forms": [
    {
      "id": "one",
      "count_pattern": "1",
      "format": "{item}"
    },
    {
      "id": "few",
      "count_pattern": "[2-4]",
      "format": "{count} {item}"
    },
    {
      "id": "other",
      "format": "{count} {item}"
    }
  ]
}
```

Each item and currency name can then supply values for `one`, `few`, and `other`.

Currency wording for the locale uses the same form IDs:

```json
{
  "currency": {
    "one": "copper coin",
    "few": "copper coins",
    "other": "copper coins"
  }
}
```

## Automatic Word Rules

`rules` are regular-expression replacements used only when an item does not define an explicit name for the selected form. They are useful for broad language rules, but explicit item entries are safer for exceptions.

```json
{
  "rules": [
    {
      "forms": ["other"],
      "pattern": "(?i)(.*[^aeiou])y$",
      "replacement": "$1ies"
    }
  ]
}
```

Invalid regular expressions are ignored.
