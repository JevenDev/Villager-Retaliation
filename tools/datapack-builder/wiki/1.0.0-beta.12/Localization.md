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
- UI labels, profile text, family rows, profession names: resource-pack language files
