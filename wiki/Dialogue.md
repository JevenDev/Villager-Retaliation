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

Dialogue option IDs and labels are limited to 128 characters so they can be sent safely to clients.

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

Openings and closings can also react when the player displays an ominous banner: worn directly in the head slot, attached to a worn helmet, or applied to a shield in either hand. Use `requires_ominous_banner`, and optionally narrow the speaker's durable village allegiance with `village_allegiance` or `village_allegiances` (`known`, `unknown`, or `unaffiliated`):

```json
{
  "id": "my_pack.opening.ominous_resident",
  "requires_ominous_banner": true,
  "village_allegiance": "known",
  "reputation_levels": ["suspicious", "hostile", "despised"],
  "text": "Do not carry that raider mark through my village."
}
```

Two item tags make ominous-symbol recognition extensible:

| Tag | Purpose |
| --- | --- |
| `villagerretaliation:ominous_banner_pattern_carriers` | Items whose `banner_patterns` component should be compared with the vanilla ominous design. It contains banners and shields by default. Add compatible modded shields or wearable banner items here. |
| `villagerretaliation:ominous_banner_equivalents` | Items that always count as displaying the ominous symbol, without requiring banner-pattern components. Add custom insignia, uniforms, masks, or other modded gear here. |

For other gear-specific dialogue, openings and closings accept the same `player_item`, `player_items`, `player_item_tag`, `player_item_tags`, `player_item_slot`, and `player_item_slots` filters as normal dialogue lines. Slots can be `main_hand`, `off_hand`, `hands`, `armor`, `hotbar`, `inventory`, `accessories`, `equipment`, or `any`. The `accessories` slot reads equipped Curios or Accessories items when either optional mod is installed; `equipment` and `any` include them as well. Item-filtered conversation text can use placeholders such as `{player_item}`, `{player_item_id}`, and `{player_item_slot}`.

These filters can require stack data with `player_item_components`, `player_item_custom_data`, or `player_item_nbt`; `held_item_components`, `held_item_custom_data`, and `held_item_nbt` are aliases. Component and custom-data predicates combine with the existing item, tag, slot, durability, and enchantment checks. See [Item stack predicates](JSON-Reference.md#item-stack-predicates).

```json
{
  "id": "my_pack.opening.custom_uniform",
  "player_item_tag": "my_pack:village_guard_uniforms",
  "player_item_slots": ["armor"],
  "text": "I recognize that {player_item}."
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
- Prefer `conditions` once several helper flags are stacking up.

For request-specific patterns, see [Dialogue Requests](Dialogue-Requests.md).

The command `/vr admin dialogue explain <villager> <request> [option_id]` reports which request and filters caused a line to match or be rejected.
