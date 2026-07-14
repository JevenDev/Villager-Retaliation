# Localization

Villager Retaliation uses two different localization layers.

## 1. Datapack Locale Files

Use datapack locale folders for authored speech and notification text:

```text
data/my_pack/dialogue/en_us/global/messages/00_weather.json
data/my_pack/dialogue/fr_fr/global/messages/00_weather.json
data/villagerretaliation/notifications/en_us/my_pack_notifications.json
data/villagerretaliation/notifications/fr_fr/my_pack_notifications.json
```

Example translated message:

```json
{
  "id": "my_pack.message.weather",
  "key": "my_pack.message.weather",
  "text": "Rain keeps the fields honest."
}
```

```json
{
  "id": "my_pack.message.weather",
  "key": "my_pack.message.weather",
  "text": "La pluie garde les champs honnetes."
}
```

Use the same `id` so the locale-specific entry replaces the fallback.

## Item Counts In Dialogue

Generated item phrases such as `two emeralds` are controlled by locale-scoped datapack files:

```text
data/<namespace>/item_text/en_us/items.json
data/<namespace>/item_text/fr_fr/items.json
```

The built-in English definition uses numeric counts, but a locale can define any count categories it needs. Forms are tested in order and the final form is the fallback:

```json
{
  "forms": [
    { "id": "one", "count_pattern": "1", "format": "{item}" },
    { "id": "few", "count_pattern": "(?:2|3|4)", "format": "{count} {item}" },
    { "id": "other", "format": "{count} {item}" }
  ],
  "currency": {
    "one": "emerald",
    "few": "emeralds",
    "other": "emeralds"
  },
  "items": {
    "minecraft:bread": {
      "one": "bread",
      "few": "loaves of bread",
      "other": "loaves of bread"
    }
  },
  "rules": [
    {
      "forms": ["few", "other"],
      "pattern": "(?i)(.*[^aeiou])y$",
      "replacement": "$1ies"
    }
  ]
}
```

- `count_pattern` is a regular expression matched against the integer count.
- `format` supports `{count}` and `{item}`. A locale may omit `{count}` or spell out particular values with additional forms.
- `currency` provides form-specific names for the configured currency item.
- `items` provides form-specific names by item ID and is the preferred way to translate irregular or uncountable nouns.
- `rules` are ordered regular-expression fallbacks. The first matching rule for the selected form wins.

Files merge in resource order. The requested locale inherits `en_us`, then overrides the sections and entries it supplies. Run `/reload` after changing these files.

## 2. Resource-Pack Language Files

Use a resource pack for GUI and generated labels:

```text
assets/villagerretaliation/lang/en_us.json
assets/villagerretaliation/lang/fr_fr.json
```

This is where buttons, profile labels, relationship rows, reputation text, mood names, and profession labels belong.

Example:

```json
{
  "villagerretaliation.gui.root.talk": "Parler",
  "villagerretaliation.gui.root.trade": "Commercer",
  "villagerretaliation.reputation.value_format": "Reputation : %s"
}
```

## When To Use `text_key`

If several filtered dialogue rules should share one translated line, keep the logic in `lines` and the wording in `messages`:

```json
{
  "id": "my_pack.line.weather_rain",
  "request": "question",
  "text_key": "my_pack.message.weather"
}
```

That lets translators touch one keyed message instead of copying every filter block.

## Quests

Quest JSON can keep its objectives, rewards, and rules in one file while moving player-facing words into locale message files:

```json
{
  "display": {
    "title": "Bread Delivery",
    "title_key": "quest.village_supply.bread_delivery.title"
  },
  "dialogue": {
    "start": ["Bring me 16 bread."],
    "start_key": "quest.village_supply.bread_delivery.dialogue.start"
  }
}
```

Translate the matching keys under the locale:

```text
data/my_pack/dialogue/fr_fr/quests/messages/00_quest_text.json
```

Quest key fields include `title_key`, `description_key`, tracker `text_key`, objective `text_key` / `complete_text_key`, dialogue `<stage>_key`, and expiration `text_key`.

## Forced Dialogue

Forced dialogue supports the same message catalog:

```json
{
  "message_prefix": "forced.my_pack.theft.warning",
  "line": "Hands off that {container}.",
  "options": [
    {
      "label": "Apologize",
      "response": "Then prove it."
    }
  ]
}
```

With `message_prefix`, VR looks for `.line`, `.option.<id>.label`, `.option.<id>.response`, `.option.<id>.take_items.success`, and matching failure or stolen-item keys. Explicit fields such as `line_key`, `label_key`, and `response_key` still work and override the generated keys.

Translate entry lines, option labels, option responses, and payment/stolen-item success or failure responses with message entries in `data/<namespace>/dialogue/<locale>/.../messages/*.json`.

## Profession Names

Vanilla professions use Minecraft's own language keys:

```json
{
  "entity.minecraft.villager.farmer": "Farmer"
}
```

Custom professions follow the same pattern with namespace and dotted path:

```json
{
  "entity.minecraft.villager.my_mod.crystal_smith": "Crystal Smith"
}
```

## Rule Of Thumb

- Dialogue, notifications, and authored lines: datapack locale folders
- Quest titles, quest tracker text, quest lifecycle dialogue, and forced-dialogue labels/responses: datapack locale folders with message keys
- UI labels, profile text, family rows, profession names: resource-pack language files
