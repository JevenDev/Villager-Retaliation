# Dialogue JSON

Dialogue JSON controls conversation choices, villager replies, one-off messages, opening lines, closing lines, and pacification responses.

## Paths

Dialogue files must be in the `villagerretaliation` namespace:

```text
data/villagerretaliation/dialogue/en_us/global.json
data/villagerretaliation/dialogue/en_us/professions/farmer.json
data/villagerretaliation/dialogue/en_us/professions/farmer/share_stories.json
data/villagerretaliation/dialogue/fr_fr/global.json
```

Files under `professions/<profession>.json` and `professions/<profession>/...json` automatically default entries to that profession unless the entry supplies its own `professions` filter.

## Top-Level Sections

A dialogue file can contain any mix of these arrays:

| Key | Purpose |
| --- | --- |
| `options` | Adds choices to the villager talk menu. |
| `lines` | Adds responses selected for a dialogue request type. |
| `messages` | Adds keyed one-off text used by specific systems. |
| `openings` | Adds conversation opening lines. |
| `closings` | Adds conversation closing lines. |
| `pacify` | Adds lines shown when pacifying a hostile villager. |

## Minimal Option And Line

```json
{
  "options": [
    {
      "id": "my_pack.ask_weather",
      "label": "Ask About Weather",
      "type": "question",
      "order": 40
    }
  ],
  "lines": [
    {
      "id": "my_pack.weather_rain_farmer",
      "option": "my_pack.ask_weather",
      "type": "question",
      "weather": ["rain"],
      "text": "Good for wheat, bad for boots.",
      "weight": 20
    }
  ]
}
```

The option id is what the player clicks. The line's `option` or `option_ids` links it to that choice.

## Dialogue Request Types

Use these values in `type`:

```text
chat
greeting
question
gift_preferences
gift_advice_followup
map_report
story_hint_report
combat_survival_report
gear_report
recruitment_followup
cured_recognition
village_event_report
apology
village_defense_report
story
share_story
joke
insult
```

## Option Fields

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `id` | string | required | Stable option id. |
| `label` | string | required | Text shown in the talk menu. |
| `type` | enum | required | Dialogue request sent when selected. |
| `order` | integer | array index | Lower values appear earlier. |
| `professions` | string or array | any | Filters by villager profession. |
| `dispositions` | string or array | any | Filters by mood/disposition. |
| `show_for_adults` | boolean | `true` | Adult visibility. |
| `show_for_babies` | boolean | `true` | Baby visibility. |
| `requires_unreported_cartographer_map_discovery` | boolean | `false` | Shows after an unreported cartographer map discovery. |
| `requires_unreported_story_hint_discovery` | boolean | `false` | Shows after an unreported story hint discovery. |
| `requires_unreported_combat_survival_report` | boolean | `false` | Shows after a combat survival report is waiting. |
| `requires_unreported_gear_report` | boolean | `false` | Shows after a gear report is waiting. |
| `requires_unreported_recruitment_followup` | boolean | `false` | Shows after a recruitment follow-up is waiting. |
| `requires_unreported_cured_recognition` | boolean | `false` | Shows after cured villager recognition is waiting. |
| `requires_recent_village_event` | boolean | `false` | Shows when a nearby remembered village event matters. |
| `requires_unreported_gift_advice_result` | boolean | `false` | Shows after the player tests gift advice. |
| `requires_unapologized_remembered_harm` | boolean | `false` | Shows after remembered harm that has not been apologized for. |
| `requires_unreported_village_defense` | boolean | `false` | Shows after the player defends the village. |
| `requires_shareable_story` | boolean | `false` | Shows when the villager has a discovered structure or biome story. |

## Line Fields

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `id` | string | generated | Stable line id. |
| `type` | enum | required | Must match the requested dialogue type. |
| `text` | string | required | The response text. |
| `option` | string or array | none | Restricts the line to option id(s). |
| `option_ids` | string or array | none | Same purpose as `option`. |
| `professions` | string or array | inherited/any | Filters by profession. |
| `dispositions` | string or array | any | Filters by disposition. |
| `weather` | string or array | any | `clear`, `rain`, or `thunder`. |
| `times` | string or array | any | `morning`, `afternoon`, `evening`, or `night`. |
| `event_tags` | string or array | any | Requires a recent nearby event with a matching tag. |
| `player_event_tags` | string or array | any | Requires a recent event associated with the player. |
| `story_structure` | string or array | any | Restricts `share_story` to one structure id. |
| `story_structures` | string or array | any | Multiple structure ids. |
| `story_biome` | string or array | any | Restricts `share_story` to one biome id. |
| `story_biomes` | string or array | any | Multiple biome ids. |
| `requires_recent_broken_bed_memory` | boolean | `false` | Requires recent bed harm memory. |
| `requires_recent_direct_hit_memory` | boolean | `false` | Requires direct hit memory. |
| `requires_gear_report_used_in_combat` | boolean | `false` | Requires gear that has been used in combat. |
| `requires_gear_report_unused_in_combat` | boolean | `false` | Requires gifted gear not yet used in combat. |
| `recruitment_followup_scenarios` | string or array | any | Scenario ids stored by recruitment follow-up logic. |
| `requires_recruitment_memory` | boolean | `false` | Requires recruitment memory. |
| `recruitment_memory_scenarios` | string or array | any | Scenario ids stored by recruitment memory logic. |
| `min_recruitment_follow_distance` | integer | `0` | Minimum followed distance in blocks. |
| `requires_recruitment_boat_trip` | boolean | `false` | Requires boat trip memory. |
| `requires_recruitment_ocean_crossing` | boolean | `false` | Requires ocean crossing memory. |
| `requires_recruitment_swim_trip` | boolean | `false` | Requires swim trip memory. |
| `excludes_recruitment_ocean_crossing` | boolean | `false` | Rejects ocean crossing memory. |
| `first_conversation_only` | boolean | `false` | Only appears in the first conversation. |
| `gift_advice` | enum | none | See gift advice kinds below. |
| `show_for_adults` | boolean | `true` | Adult visibility. |
| `show_for_babies` | boolean | `true` | Baby visibility. |
| `weight` | integer | `10` | Weighted selection. |

## Gift Advice Kinds

Use these in `gift_advice`:

```text
global_liked
global_disliked
profession_liked
profession_disliked
already_known
```

Gift advice line text can use:

```text
{gift_item}
{gift_subject}
```

## Event Tags

Use these in `event_tags` or `player_event_tags`:

```text
baby_born
iron_golem_defeated_mob
thunderstorm
sandstorm
snowstorm
village_fire
night_attack
raid
villager_death
villager_attacked
player_attacked_villager
player_defended_village
player_defended_raid
player_cured_villager
golem_created
golem_killed
nearby_hostile_mob
reputation_changed
player_gave_loved_gift
player_gave_liked_gift
player_gave_neutral_gift
player_gave_disliked_gift
player_gave_hated_gift
```

Lines that use `player_cured_villager` can use:

```text
{cured_villager}
{cured_villager_possessive}
```

## Messages

Messages are keyed text looked up by code:

```json
{
  "messages": [
    {
      "id": "my_pack.bed_warning_farmer",
      "key": "sleep.broken_bed",
      "text": "That was my bed. The field remembers every footprint.",
      "professions": ["farmer"],
      "weight": 20
    }
  ]
}
```

Message fields:

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `id` | string | generated | Use for translations and overrides. |
| `key` | string | required | Must match a key emitted by the mod. |
| `text` | string | required | Message text. |
| `professions` | string or array | inherited/any | Profession filter. |
| `dispositions` | string or array | any | Disposition filter. |
| `show_for_adults` | boolean | `true` | Adult visibility. |
| `show_for_babies` | boolean | `true` | Baby visibility. |
| `weight` | integer | `10` | Weighted selection. |

## Openings And Closings

```json
{
  "openings": [
    {
      "id": "my_pack.opening_farmer_trusted",
      "text": "Good to see a steady face.",
      "professions": ["farmer"],
      "dispositions": ["friendly", "respectful"]
    }
  ],
  "closings": [
    {
      "id": "my_pack.closing_farmer",
      "text": "Mind the rows on your way out.",
      "professions": ["farmer"]
    }
  ]
}
```

Openings and closings support `id`, `text`, `professions`, `dispositions`, `show_for_adults`, `show_for_babies`, and `weight`.

## Pacify Lines

```json
{
  "pacify": [
    {
      "id": "my_pack.pacify.accepted",
      "text": "Fine. {emerald_cost} {emeralds}, and we try peace again.",
      "outcomes": ["success"],
      "weight": 10
    }
  ]
}
```

Pacify text supports:

```text
{emerald_cost}
{emeralds}
```

The `outcomes` field filters by the internal pacification result enum. If omitted, the line can match any result.

Valid pacify outcomes are:

```text
not_applicable
success
not_enough_emeralds
blocked_by_reputation
```

## Story Placeholders

`share_story` lines can use:

```text
{target}
{target_article}
```

`{target}` is the configured structure or biome display name. `{target_article}` includes the article generated by the story system, such as "an Ancient City" or "a Deep Dark", and is capitalized automatically at sentence starts.

## Locale Overlay Example

English fallback:

```text
data/villagerretaliation/dialogue/en_us/global.json
```

```json
{
  "lines": [
    {
      "id": "my_pack.question.weather.clear",
      "type": "question",
      "text": "Clear skies make honest roads."
    }
  ]
}
```

French replacement:

```text
data/villagerretaliation/dialogue/fr_fr/global.json
```

```json
{
  "lines": [
    {
      "id": "my_pack.question.weather.clear",
      "type": "question",
      "text": "Un ciel clair rend les routes honnetes."
    }
  ]
}
```
