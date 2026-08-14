# Dialogue

Normal dialogue powers the Talk menu, reusable reply pools, keyed text, openings, closings, and pacify lines.

## Paths

Dialogue can live anywhere under:

```text
data/<namespace>/dialogue/<locale>/
```

Beta.13 works best with typed folders:

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

## Rich Text Variants

A `lines` or `variants` array can contain objects instead of plain strings. This lets each wording have its own stable ID, eligibility conditions, priority, chance, weight, metadata, and durable usage policy:

```json
{
  "id": "my_pack.line.market_rumor",
  "request": "story",
  "metadata": {
    "topic": "market_rumors",
    "tags": ["dialogue.ambient", "tone.friendly"],
    "routing_tags": ["route.market"],
    "anti_repeat_groups": ["rotation.market"]
  },
  "variants": [
    {
      "id": "rain",
      "text": "Rain makes every market promise sound urgent.",
      "priority": 10,
      "weight": 3,
      "chance": 0.75,
      "conditions": [
        { "type": "weather", "state": "rain" }
      ],
      "usage": {
        "cooldown_days": 1,
        "anti_repeat": true,
        "scope": "player_villager"
      }
    },
    {
      "id": "clear",
      "text_key": "my_pack.market_rumor.clear",
      "weight": 1
    }
  ]
}
```

Plain string arrays remain valid. Their generated stable IDs are the entry ID for a single line or `<entry-id>#line_<index>` for multiple lines. An explicit variant ID becomes `<entry-id>#<variant-id>`, which is safer when lines are reordered later.

Variant selection uses the same contract across normal dialogue and dialogue-tree text:

1. Remove variants whose conditions or usage policy do not match.
2. Try higher `priority` tiers first.
3. Apply each candidate's `chance`.
4. Select among surviving candidates by `weight`.
5. If every candidate in a priority tier misses its chance, try the next tier.

`weight: 0` disables a candidate. `chance` is clamped from `0.0` to `1.0`.

## Durable Usage Rules

Use `usage` when a line should rest, run only a limited number of times, or avoid immediately repeating:

```json
{
  "id": "my_pack.line.first_warning",
  "request": "question",
  "text": "I will explain this once.",
  "usage": {
    "once": true,
    "cooldown_seconds": 30,
    "anti_repeat": true,
    "scope": "player_villager"
  }
}
```

| Field | Meaning |
| --- | --- |
| `cooldown`, `cooldown_ticks`, `cooldown_seconds`, `cooldown_days` | Minimum saved time before the same stable text ID can be selected again |
| `max_uses` | Lifetime use limit in the selected scope; `0` means unlimited |
| `once` | Shorthand for `max_uses: 1` |
| `anti_repeat` | Prefer a different recent variant when one is available; defaults to `true` |
| `scope` | `player_villager` (default), `player`, `villager`, `village`, `dimension`, or `world` |

Usage counts and timestamps are stored in world save data. Put `usage` on the parent entry to provide defaults to all variants, then override it on an individual variant when needed. The legacy top-level `cooldown*`, `max_uses`, and `once` fields on normal dialogue lines remain supported.

## Dialogue Metadata And Tags

`metadata` is inherited from the file root into entries and then into rich variants. It keeps classification, routing, and anti-repeat intent separate:

| Field | Purpose |
| --- | --- |
| `topic` | Human-readable normalized topic; also the fallback anti-repeat group |
| `tags` | General classification tags |
| `routing_tags` | Tags used specifically to route candidates |
| `anti_repeat_groups` | Groups whose recent use should discourage repetition |
| `questline`, `quest`, `stage` | Optional quest ownership and stage context |
| `notes` | Authoring notes |

Tags are normalized to lowercase while preserving meaningful separators such as `:`, `/`, `.`, and `-`. When `routing_tags` is omitted, general tags beginning with `route.`, `route:`, `routing.`, or `routing:` act as routing tags.

## Dialogue Frequency Tuning

Built-in conversation probabilities are datapack values under:

```text
data/<namespace>/dialogue_tuning/<file>.json
```

```json
{
  "schema": "villagerretaliation:dialogue_tuning/v1",
  "values": {
    "story_hint.vague_chance": 0.2,
    "reputation.joke.neutral_chance": 0.35
  }
}
```

Files are additive. When several resources define the same normalized key, the later loaded value wins. Values must be finite numbers; probability consumers clamp them to `0.0` through `1.0`.

Current built-in keys and defaults:

| Key | Default |
| --- | ---: |
| `opening.long_absence.minimum_days` | 3 |
| `opening.long_absence.base_chance` | 0.4 |
| `opening.long_absence.chance_per_day` | 0.1 |
| `opening.long_absence.max_chance` | 0.85 |
| `memory.gift.question_chance` | 0.45 |
| `memory.gift.greeting_chance` | 0.35 |
| `memory.gift.opening_chance` | 0.3 |
| `memory.container_theft.question_chance` | 0.4 |
| `memory.container_theft.greeting_chance` | 0.25 |
| `memory.container_theft.opening_chance` | 0.25 |
| `story_hint.vague_chance` | 0.12 |
| `story_hint.biome_name_chance` | 0.24 |
| `story_hint.precise_biome_chance` | 0.34 |
| `story_hint.structure_rumor_chance` | 0.46 |
| `story_hint.precise_structure_chance` | 0.58 |
| `cartographer_map.royalty_chance` | 0.09 |
| `cartographer_map.revered_chance` | 0.06 |
| `cartographer_map.respected_chance` | 0.03 |
| `raid.story_chance` | 0.35 |
| `reputation.repeat_greeting_chance` | 0.15 |
| `reputation.question.trusted_chance` | 0.55 |
| `reputation.question.neutral_chance` | 0.3 |
| `reputation.story.trusted_chance` | 0.65 |
| `reputation.story.neutral_chance` | 0.4 |
| `reputation.story.profession_bonus` | 0.15 |
| `reputation.joke.royalty_chance` | 0.85 |
| `reputation.joke.trusted_chance` | 0.7 |
| `reputation.joke.neutral_chance` | 0.5 |
| `reputation.joke.suspicious_chance` | 0.35 |
| `reputation.joke.hostile_chance` | 0.25 |
| `reputation.joke.despised_chance` | 0.15 |
| `reputation.joke.nitwit_bonus` | 0.1 |
| `reputation.joke.missed_response_chance` | 0.3333333333 |
| `reputation.positive_response_chance` | 0.25 |

## Good Defaults

- Keep one idea per file when possible.
- Use stable `id` values.
- Give rich variants explicit stable IDs before shipping a pack.
- Prefer `conditions` once several helper flags are stacking up.

For request-specific patterns, see [Dialogue Requests](Dialogue-Requests.md).

The command `/vr admin dialogue explain <villager> <request> [option_id]` reports which request and filters caused a line to match or be rejected.
