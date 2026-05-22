# JSON Reference

This page lists shared JSON conventions used across Villager Retaliation's data-driven systems.

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

Use lowercase names. `minecraft:` is optional for supported vanilla professions.

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

Dialogue and notifications can generate fallback ids from file path and entry order, but explicit ids are strongly recommended:

```json
{
  "id": "my_pack.farmer.weather_rain_01"
}
```

Use stable ids when:

- You plan to translate a line.
- You plan to override a built-in or pack-provided line.
- You want entries to stay stable when you reorder JSON arrays.

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
- A missing required field usually causes only that entry to be skipped.
- A broken model JSON falls back to the built-in model, and logs a warning.
