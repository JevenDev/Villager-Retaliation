# JSON Reference

This page lists shared JSON conventions used across Villager Retaliation's data-driven systems.

For migration notes between versions, see [Pack Format Changes](Pack-Format-Changes.md).

## Resource Location Shortcuts

For gift and pacification item ids, unnamespaced values are treated as Minecraft ids:

```json
"bread"
```

is equivalent to:

```json
"minecraft:bread"
```

For structure, biome, model, and story target ids, use full resource locations unless a page explicitly says a shortcut is supported.

## Strings Or Arrays

Many fields accept either a single string or an array of strings.

```json
"professions": "farmer"
```

and:

```json
"professions": ["farmer"]
```

are both accepted by the loaders that use `professions`, filters, item lists, tags, and story target lists. Arrays are clearer for pack documentation and future edits.

## Common Professions

Use lowercase ids. `minecraft:` is optional for vanilla professions.

```text
armorer
butcher
cartographer
cleric
farmer
fisherman
fletcher
leatherworker
librarian
mason
nitwit
shepherd
toolsmith
weaponsmith
none
unemployed
```

`none` and `unemployed` both target villagers with no profession.

Modded professions are supported anywhere a `professions` filter is accepted. Use the full registered id:

```json
{
  "professions": ["examplemod:alchemist"]
}
```

The profession must already be registered by a mod; Villager Retaliation JSON can reference professions, but it does not create them.

## Common Filters

Dialogue, notifications, gifts, pacification, and rewards share a few ideas even when the exact field list differs by page.

Most filters are additive within a field: if you list several professions, any one of those professions can match. Different filter fields stack together: a line with both `professions` and `dispositions` must pass both filters.

```json
{
  "professions": ["farmer", "fisherman"],
  "dispositions": ["friendly", "respectful"],
  "show_for_adults": true,
  "show_for_babies": false
}
```

Player item filters accept item ids and item tags. Prefix a tag with `#` inside `player_items`, or use `player_item_tag` / `player_item_tags`.

```json
{
  "player_items": ["minecraft:bow", "#minecraft:arrows"],
  "player_item_slots": ["hotbar", "inventory"]
}
```

If `player_items` is set and no slot filter is supplied, the current default is `hands`.

## Reputation Levels

These values are used by notifications and gift rewards:

```text
royalty
revered
respected
trusted
neutral
suspicious
hostile
despised
feared
```

Default thresholds can be changed in the mod config, so packs should use level names for meaning rather than assuming a fixed numeric reputation.

## Dialogue Dispositions

Dialogue filters use dispositions, which are derived from reputation and current context:

```text
friendly
respectful
neutral
cautious
rude
hostile
fearful
```

Leave `dispositions` empty or omit it when a line should work in any mood.

## Dialogue Types And Notification Triggers

Dialogue `type` values and notification `trigger` values have their own expandable example catalogs:

- [Dialogue Types](Dialogue-Types.md) covers every current `options[].type` and `lines[].type` value.
- [Notification Triggers](Notification-Triggers.md) covers every built-in notification `trigger` value from the current data files.

## Village Event Tags

Dialogue lines can filter recent village memories with `event_tags` and player-specific recent memories with `player_event_tags`.

```json
{
  "event_tags": ["raid"],
  "player_event_tags": ["player_defended_raid"]
}
```

For the full current list, when each value is remembered, and dropdown examples for simple and expanded uses, see [Event Tags](Event-Tags.md).

## Weight And Chance

`weight` controls weighted random selection among matching entries. Higher values are more likely. Missing weights usually default to `10`, and values below `1` are clamped or ignored depending on the system.

Notifications also support `chance`, a number from `0.0` to `1.0`:

```json
{
  "chance": 0.25
}
```

That entry passes its random chance gate roughly 25 percent of the time before weighted selection.

## Adult And Baby Filters

Most dialogue and notification entries support:

```json
{
  "show_for_adults": true,
  "show_for_babies": false
}
```

Both default to `true`.

## Stable IDs

Dialogue, notifications, gifts, and profession loot can generate fallback ids from file path and entry order, but explicit ids are strongly recommended:

```json
{
  "id": "my_pack.farmer.weather_rain_01"
}
```

Use stable ids when:

- You plan to translate a line.
- You plan to override a built-in or pack-provided line.
- You want entries to stay stable when you reorder JSON arrays.
- You plan to remove or replace one gift rule or profession loot rule from another file.

## Common Color Values

Notifications accept named colors:

```text
white
gray
grey
dark_gray
black
red
dark_red
green
dark_green
blue
aqua
yellow
gold
purple
light_purple
```

They also accept `#RRGGBB`, `0xRRGGBB`, `#AARRGGBB`, or `0xAARRGGBB`.

## Validation Gotchas

- JSON comments are not valid.
- Trailing commas are not valid.
- A misspelled enum value is silently ignored by many loaders.
- A misspelled or unloaded custom profession id is ignored by profession filters.
- A missing required field usually causes only that entry to be skipped.
- A broken model JSON falls back to the built-in model, and logs a warning.
