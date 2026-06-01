# Dialogue

Normal dialogue powers the Talk menu, reusable reply pools, keyed text, openings, closings, and pacify lines.

## Paths

Dialogue can live anywhere under:

```text
data/<namespace>/dialogue/<locale>/
```

Beta.12 works best with typed folders:

```text
data/my_pack/dialogue/en_us/global/options/00_rumor.json
data/my_pack/dialogue/en_us/global/lines/00_rumor.json
data/my_pack/dialogue/en_us/global/messages/00_shared.json
data/my_pack/dialogue/en_us/professions/farmer/openings/00_openings.json
data/my_pack/dialogue/en_us/professions/farmer/closings/00_closings.json
data/my_pack/dialogue/en_us/professions/farmer/pacify/00_pacify.json
```

Bundle files still work, but folderized files are easier to translate and override.

## Sections

| Section | Use it for |
| --- | --- |
| `options` | Talk menu buttons shown to the player |
| `lines` | Villager replies and response pools |
| `messages` | Shared keyed text used by `text_key` or other systems |
| `openings` | First line when a conversation starts |
| `closings` | Final line when a conversation ends |
| `pacify` | Spoken lines used while calming a hostile villager |

## Example: Custom Talk Option

```text
data/my_pack/dialogue/en_us/global/options/00_rumor.json
data/my_pack/dialogue/en_us/global/lines/00_rumor.json
```

```json
{
  "id": "my_pack.option.ask_rumor",
  "label": "Ask For A Rumor",
  "request": "story"
}
```

```json
{
  "id": "my_pack.line.rumor",
  "request": "story",
  "option": "my_pack.option.ask_rumor",
  "text": "Roads carry stories faster than traders do.",
  "weight": 10
}
```

## Example: Shared Message Text

Use `messages` when several rules should share the same wording.

```json
{
  "id": "my_pack.message.rain_warning",
  "key": "my_pack.message.rain_warning",
  "lines": [
    "Rain makes bad roads worse.",
    "Rain keeps the careful indoors."
  ]
}
```

Then point a line at it:

```json
{
  "id": "my_pack.line.rain_warning",
  "request": "question",
  "text_key": "my_pack.message.rain_warning"
}
```

## Example: Opening

```json
{
  "id": "my_pack.opening.trusted_farmer",
  "professions": ["minecraft:farmer"],
  "reputation_levels": ["trusted", "respected", "revered", "royalty"],
  "text": "Good to see you. The fields have been calmer lately."
}
```

## Example: Closing

```json
{
  "id": "my_pack.closing.friendly",
  "dispositions": ["friendly", "respectful"],
  "text": "Travel safe."
}
```

## Example: Pacify Line

The items used for pacification live in [Pacification](Pacification.md). The spoken line lives in dialogue.

```json
{
  "id": "my_pack.pacify.neutral",
  "professions": ["minecraft:toolsmith"],
  "text": "Fine. Leave the payment and walk away slower next time."
}
```

## Example: Profession-Specific Line

Folder paths can communicate ownership clearly:

```text
data/my_pack/dialogue/en_us/professions/cartographer/lines/00_map_talk.json
```

```json
{
  "id": "my_pack.line.map_talk",
  "request": "question",
  "text": "A good map is just a promise written carefully."
}
```

You can still include explicit `professions` filters when needed, but the path itself is already a good organizational hint.

## Good Defaults

- Keep one idea per file when possible.
- Use stable `id` values.
- Start without filters, then add filters after the line works.
- Prefer `conditions` once several helper flags are stacking up.

For request-specific patterns, see [Dialogue Requests](Dialogue-Requests.md).
