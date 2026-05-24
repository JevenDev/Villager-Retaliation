# Dialogue JSON

Dialogue JSON controls conversation choices, villager replies, one-off messages, opening lines, closing lines, and pacification responses.

For event-driven locked conversations such as a villager catching the player stealing from a chest, see [Forced Dialogue JSON](Forced-Dialogue.md). Forced dialogue uses a separate datapack path and does not add normal Talk menu options.

## Paths

Dialogue files must be in the `villagerretaliation` namespace:

```text
data/villagerretaliation/dialogue/en_us/my_pack_dialogue.json
data/villagerretaliation/dialogue/en_us/professions/farmer.json
data/villagerretaliation/dialogue/en_us/professions/farmer/share_stories.json
data/villagerretaliation/dialogue/en_us/professions/examplemod/alchemist.json
data/villagerretaliation/dialogue/fr_fr/my_pack_dialogue.json
```

Files under `professions/<profession>.json` and `professions/<profession>/...json` automatically default entries to that profession unless the entry supplies its own `professions` filter. For custom professions, use `professions/<namespace>/<path>.json`; for example, `professions/examplemod/alchemist.json` defaults to `examplemod:alchemist`.

Use a unique file name for addon dialogue. A datapack file at `data/villagerretaliation/dialogue/en_us/global.json` replaces the mod's built-in `global.json`, which can hide default interaction-menu options, keyed messages, openings, closings, and other built-in dialogue data. Only use that exact path when you intentionally want a full-file override.

Dialogue files translate villager speech and keyed dialogue messages. They do not translate the client GUI around the conversation, such as Talk, Trade, Gift, Gender, Mood, Family Tree, or generated relationship rows. Put those strings in a resource-pack language file; see [Localization Guide](Localization.md).

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

## Text And Line Variations

Dialogue entries that output speech can use either `text` for one line or `lines` for several equal line variations. This applies to `lines`, `messages`, `openings`, `closings`, and `pacify` entries. The entry is selected by its normal `weight` first; if it has `lines`, one variation is then selected at random.

Use `lines` when several entries would otherwise have the same filters and weight:

```json
{
  "lines": [
    {
      "id": "my_pack.weather_rain_farmer",
      "option": "my_pack.ask_weather",
      "type": "question",
      "weather": ["rain"],
      "lines": [
        "Good for wheat, bad for boots.",
        "Rain keeps the fields honest.",
        "The rows will like this more than travelers do."
      ],
      "weight": 30
    }
  ]
}
```

Older `text` entries still work and are still clearer for single-line entries.

## Dialogue Request Types

Use these values in `type`:

```text
small_talk
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

Packs must use `small_talk` for this dialogue request type. Older `chat` values are no longer accepted, so update existing dialogue options and lines before targeting beta.11+.

See [Dialogue Types](Dialogue-Types.md) for simple and expanded dropdown examples for every current `type` value.

## Option Fields

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `id` | string | required | Stable option id. |
| `label` | string | required | Text shown in the talk menu. |
| `type` | enum | required | Dialogue request sent when selected. |
| `order` | integer | array index | Lower values appear earlier. |
| `professions` | string or array | any | Filters by villager profession. |
| `dispositions` | string or array | any | Filters by mood/disposition. |
| `requires_villager_unarmed` | boolean | `false` | Requires the villager to have no usable weapon in either hand. `villager_unarmed` is also accepted as an alias. |
| `requires_villager_armed` | boolean | `false` | Requires the villager to have a usable weapon in either hand. `villager_armed` is also accepted as an alias. |
| `reputation_level` | string or array | any | Alias for `reputation_levels`. |
| `reputation_levels` | string or array | any | Filters by the player's current reputation tier with this villager: `royalty`, `revered`, `respected`, `trusted`, `neutral`, `suspicious`, `hostile`, `despised`, or `feared`. |
| `min_reputation` | integer | none | Minimum exact reputation value with this villager. |
| `max_reputation` | integer | none | Maximum exact reputation value with this villager. |
| `player_items` | string or array | none | Requires the player to have one matching item or item tag. Prefix tags with `#`. |
| `player_item_slots` | string or array | `hands` when `player_items` is set | Slots to check: `main_hand`, `off_hand`, `hands`, `armor`, `hotbar`, `inventory`, `equipment`, or `any`. |
| `force_camera_towards_villager` | boolean | `false` | Smoothly turns the player's camera toward this villager while the selected response is shown. |
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
| `requires_known_family` | boolean | `false` | Shows when the villager has any known family relationship. |
| `requires_known_parent` | boolean | `false` | Shows when the villager has a known parent. |
| `requires_known_sibling` | boolean | `false` | Shows when the villager has a known sibling. |
| `requires_known_spouse` | boolean | `false` | Shows when the villager has a known family spouse. |
| `requires_known_child` | boolean | `false` | Shows when the villager has a known child. |
| `requires_known_grandparent` | boolean | `false` | Shows when the villager has a known grandparent. |
| `requires_known_grandchild` | boolean | `false` | Shows when the villager has a known grandchild. |
| `requires_known_descendant` | boolean | `false` | Shows when the villager has a known descendant. |
| `requires_known_aunt_uncle` | boolean | `false` | Shows when the villager has a known aunt or uncle. |
| `requires_known_cousin` | boolean | `false` | Shows when the villager has a known cousin. |
| `requires_known_niece_nephew` | boolean | `false` | Shows when the villager has a known niece or nephew. |
| `requires_known_extended_family` | boolean | `false` | Shows when the villager has known extended family. |
| `requires_known_deceased_family` | boolean | `false` | Shows when the villager has a known deceased family member. |
| `requires_known_relationship` | boolean | `false` | Shows when the villager has any known romantic relationship state. |
| `requires_known_current_relationship` | boolean | `false` | Shows when the villager has a current romantic partner. |
| `requires_known_past_relationship` | boolean | `false` | Shows when the villager has a past romantic partner. |
| `requires_known_crush` | boolean | `false` | Shows when the villager has a known crush. |
| `requires_known_dating_partner` | boolean | `false` | Shows when the villager is dating someone. |
| `requires_known_fiance` | boolean | `false` | Shows when the villager is engaged. |
| `requires_known_romantic_spouse` | boolean | `false` | Shows when the villager has a romantic spouse. |
| `requires_known_separated_partner` | boolean | `false` | Shows when the villager has a separated partner. |
| `requires_known_widowed_partner` | boolean | `false` | Shows when the villager has a late partner. |

## Line Fields

| Field | Type | Default | Notes |
| --- | --- | --- | --- |
| `id` | string | generated | Stable line id. |
| `type` | enum | required | Must match the requested dialogue type. |
| `text` | string | required unless `lines` is set | The response text. |
| `lines` | array | required unless `text` is set | Alternate response texts. One is selected at random after this entry wins weighted selection. |
| `option` | string or array | none | Restricts the line to option id(s). |
| `option_ids` | string or array | none | Same purpose as `option`. |
| `professions` | string or array | inherited/any | Filters by profession. |
| `dispositions` | string or array | any | Filters by disposition. |
| `requires_villager_unarmed` | boolean | `false` | Requires the speaking villager to have no usable weapon in either hand. `villager_unarmed` is also accepted as an alias. |
| `requires_villager_armed` | boolean | `false` | Requires the speaking villager to have a usable weapon in either hand. `villager_armed` is also accepted as an alias. |
| `reputation_level` | string or array | any | Alias for `reputation_levels`. |
| `reputation_levels` | string or array | any | Filters by the player's current reputation tier with this villager. |
| `min_reputation` | integer | none | Minimum exact reputation value with this villager. |
| `max_reputation` | integer | none | Maximum exact reputation value with this villager. |
| `weather` | string or array | any | `clear`, `rain`, or `thunder`. |
| `times` | string or array | any | `morning`, `afternoon`, `evening`, or `night`. |
| `event_tags` | string or array | any | Requires a recent nearby event with a matching tag. |
| `player_event_tags` | string or array | any | Requires a recent event associated with the player. |
| `requires_container_theft_to_self` | boolean | `false` | Requires recent player container-theft memory witnessed by this villager. |
| `requires_container_theft_from_other` | boolean | `false` | Requires recent player container-theft memory witnessed by another villager. |
| `requires_retaliation_to_self` | boolean | `false` | Requires recent retaliation-start memory from this villager. |
| `requires_retaliation_from_other` | boolean | `false` | Requires recent retaliation-start memory from another villager. |
| `retaliation_target_entity_types` | string or array | any | Restricts retaliation-memory lines to target entity ids such as `minecraft:player` or `minecraft:zombie`. |
| `player_items` | string or array | none | Requires the player to have one matching item or item tag. Prefix tags with `#`. |
| `player_item_slots` | string or array | `hands` when `player_items` is set | Slots to check: `main_hand`, `off_hand`, `hands`, `armor`, `hotbar`, `inventory`, `equipment`, or `any`. |
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
| `requires_known_family` | boolean | `false` | Requires any known family relationship. |
| `requires_known_parent` | boolean | `false` | Requires a known parent. |
| `requires_known_sibling` | boolean | `false` | Requires a known sibling. |
| `requires_known_spouse` | boolean | `false` | Requires a known family spouse. |
| `requires_known_child` | boolean | `false` | Requires a known child. |
| `requires_known_grandparent` | boolean | `false` | Requires a known grandparent. |
| `requires_known_grandchild` | boolean | `false` | Requires a known grandchild. |
| `requires_known_descendant` | boolean | `false` | Requires a known descendant. |
| `requires_known_aunt_uncle` | boolean | `false` | Requires a known aunt or uncle. |
| `requires_known_cousin` | boolean | `false` | Requires a known cousin. |
| `requires_known_niece_nephew` | boolean | `false` | Requires a known niece or nephew. |
| `requires_known_extended_family` | boolean | `false` | Requires known extended family. |
| `requires_known_deceased_family` | boolean | `false` | Requires a known deceased family member. |
| `requires_known_relationship` | boolean | `false` | Requires any known romantic relationship state. |
| `requires_known_current_relationship` | boolean | `false` | Requires a current romantic partner. |
| `requires_known_past_relationship` | boolean | `false` | Requires a past romantic partner. |
| `requires_known_crush` | boolean | `false` | Requires a known crush. |
| `requires_known_dating_partner` | boolean | `false` | Requires a dating partner. |
| `requires_known_fiance` | boolean | `false` | Requires an engaged partner. |
| `requires_known_romantic_spouse` | boolean | `false` | Requires a romantic spouse. |
| `requires_known_separated_partner` | boolean | `false` | Requires a separated partner. |
| `requires_known_widowed_partner` | boolean | `false` | Requires a late partner. |
| `gift_advice` | enum | none | See gift advice kinds below. |
| `show_for_adults` | boolean | `true` | Adult visibility. |
| `show_for_babies` | boolean | `true` | Baby visibility. |
| `weight` | integer | `10` | Weighted selection. |

Reputation filters on options and lines check the player's current reputation with the specific villager being spoken to. Use `reputation_levels` for tier-based behavior, or `min_reputation` / `max_reputation` when you need an exact numeric boundary.

Player item filters can also use aliases `player_item`, `player_item_tag`, `player_item_tags`, and `player_item_slot`. Dialogue text can use `{player_item}`, `{held_item}`, `{player_item_id}`, `{held_item_id}`, `{player_item_slot}`, and `{held_item_slot}` when the selected line has a player item filter.

Family-aware dialogue text can use `{parent}`, `{sibling}`, `{spouse}`, `{child}`, `{grandparent}`, `{ancestor}`, `{grandchild}`, `{descendant}`, `{aunt_uncle}`, `{cousin}`, `{niece_nephew}`, `{deceased_family}`, `{extended_relative}`, `{relative}`, and the matching `_possessive` variants.

Relationship-aware dialogue text can use `{partner}`, `{crush}`, `{dating_partner}`, `{fiance}`, `{romantic_spouse}`, `{ex_partner}`, `{late_partner}`, and the matching `_possessive` variants.

Recruitment memory lines can use `{follow_biome}` and `{follow_distance}`.

Container theft memory lines can use `{stolen_item}`, `{stolen_item_id}`, `{stolen_count}`, `{stolen_item_count}`, `{stolen_stack}`, `{stolen_container}`, `{stolen_loot_table}`, `{theft_witness}`, and `{theft_witness_possessive}`. Use `player_event_tags: ["player_container_theft"]` to target the memory, then add `requires_container_theft_to_self` for lines like "my {stolen_item}" or `requires_container_theft_from_other` for gossip like "{theft_witness} told me about {stolen_stack}."

Retaliation memory lines can use `{retaliation_target}`, `{retaliation_target_name}`, `{retaliation_target_kind}`, `{retaliation_target_type}`, `{retaliation_witness}`, and `{retaliation_witness_possessive}`. Use `event_tags: ["villager_retaliation_started"]` for village gossip, `player_event_tags: ["villager_retaliation_started"]` when the current player was the target, and add `requires_retaliation_to_self`, `requires_retaliation_from_other`, or `retaliation_target_entity_types` when you want direct/self-or-other or mob-type-specific lines.

Example option and line for a player holding a sword:

```json
{
  "options": [
    {
      "id": "ask_about_weapon",
      "label": "About my weapon",
      "type": "question",
      "player_items": ["#minecraft:swords"],
      "player_item_slots": ["main_hand"],
      "order": 8
    }
  ],
  "lines": [
    {
      "id": "weapon_warning_1",
      "type": "question",
      "option": "ask_about_weapon",
      "player_items": ["#minecraft:swords"],
      "player_item_slots": ["main_hand"],
      "text": "Careful where you point {held_item}.",
      "weight": 20
    }
  ]
}
```

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
baby_villager_attacked
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
player_container_theft
villager_retaliation_started
```

Example reputation-gated line:

```json
{
  "lines": [
    {
      "id": "my_pack.low_rep_warning",
      "type": "small_talk",
      "reputation_levels": ["hostile", "despised", "feared"],
      "text": "People here still remember what you cost us.",
      "weight": 20
    }
  ]
}
```

See [Event Tags](Event-Tags.md) for simple and expanded dropdown examples for every current tag, plus notes on which tags are currently remembered by built-in handlers.

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
| `text` | string | required unless `lines` is set | Message text. |
| `lines` | array | required unless `text` is set | Alternate message texts. One is selected at random after this message entry wins weighted selection. |
| `professions` | string or array | inherited/any | Profession filter. |
| `dispositions` | string or array | any | Disposition filter. |
| `requires_villager_unarmed` | boolean | `false` | Requires the villager to have no usable weapon in either hand. |
| `requires_villager_armed` | boolean | `false` | Requires the villager to have a usable weapon in either hand. |
| `show_for_adults` | boolean | `true` | Adult visibility. |
| `show_for_babies` | boolean | `true`, or `false` on profession-filtered messages | Baby visibility. |
| `weight` | integer | `10` | Weighted selection. |

Gift preference rules can set `response_key` to point at any message key. Those custom gift messages can use `{gift_item}`, `{item}`, `{gift_item_id}`, and `{item_id}` placeholders. If the custom key has no matching message, the default reaction message is used instead.

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

Openings and closings support `id`, `text`, `lines`, `professions`, `dispositions`, `requires_villager_unarmed`, `requires_villager_armed`, `show_for_adults`, `show_for_babies`, `first_conversation_only`, `first_village_interaction_only`, and `weight`.

For `messages`, `openings`, and `closings`, entries with a profession filter default to adult-only unless they explicitly set `show_for_babies: true`. This keeps profession/job-site flavor from being selected for baby villagers by accident. Unfiltered entries still default to both adults and babies.

## Pacify Lines

```json
{
  "pacify": [
    {
      "id": "my_pack.pacify.accepted",
      "lines": [
        "Fine. {payment_cost} {payment_items}, and we try peace again.",
        "That pays for peace today. Do not make me price it twice."
      ],
      "outcomes": ["success"],
      "weight": 10
    }
  ]
}
```

Pacify text supports:

```text
{payment_cost}
{payment_item}
{payment_items}
```

For older packs, `{emerald_cost}` still aliases `{payment_cost}`, and `{emeralds}` still aliases `{payment_items}`.

The `outcomes` field filters by the internal pacification result enum. If omitted, the line can match any result. Pacify lines also support `text`, `lines`, `professions`, `dispositions`, `requires_villager_unarmed`, `requires_villager_armed`, and `weight`.

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
data/villagerretaliation/dialogue/en_us/my_pack_dialogue.json
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
data/villagerretaliation/dialogue/fr_fr/my_pack_dialogue.json
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
