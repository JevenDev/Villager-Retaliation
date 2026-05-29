# JSON Reference

This page lists shared JSON conventions used across Villager Retaliation's data-driven systems.

For version-specific breaking changes and manual retargeting notes, see [Pack Format Changes](Pack-Format-Changes.md).

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

## Canonical Names And Aliases

The wiki tables list canonical field names first. Shorter or older aliases still load where documented, but new packs should prefer canonical names so examples stay consistent:

| Prefer | Accepted aliases |
| --- | --- |
| `trigger` | `event` in forced dialogue |
| `player_items`, `player_item_slots`, `player_item_enchantments` | `player_item`, `player_item_tag`, `player_item_tags`, `player_item_slot`, `held_item_enchantment`, `held_item_enchantments`, and other `held_item_*` range aliases |
| `give_items` for normal dialogue hand-ins | `take_items`, `payment` |
| `destination: "villager_inventory"` | `store_in_villager_inventory`, `store_in_inventory`, `store_items`, `store` |
| `requires_villager_armed`, `requires_villager_unarmed` | `villager_armed`, `villager_unarmed` |
| `requires_witness_armed`, `requires_witness_unarmed` | `witness_armed`, `witness_unarmed` |
| `world_text_kind` | `style` in notifications |

Compatibility aliases are meant for old packs and quick hand-authored JSON. The Datapack Generator writes canonical names when possible.

## Text Or Lines

Speech-like entries usually accept `text` for one output or `lines` for several equal variations.

```json
{
  "text": "Good to see you."
}
```

```json
{
  "lines": [
    "Good to see you.",
    "Welcome back.",
    "Hello again."
  ]
}
```

Normal dialogue `lines`, keyed dialogue `messages`, `openings`, `closings`, `pacify` entries, notifications, and forced dialogue entries all support this pattern. Keep `text` for single-line entries. Use `lines` when multiple variants share the same filters and weight.

Selection is entry-first: filters and `chance` are checked, `weight` chooses a matching entry, and then one value from `lines` is selected at random. If you collapse several old entries into one `lines` entry, set the new `weight` to the old total when you want the same overall odds.

## Quests

Quest files live in `data/<namespace>/quests/`. They combine advancement-like `criteria` with explicit offer, target, and reward sections. Dialogue trees run quest actions and show or hide entries with `conditions` using `type: "quest"`.

See [Quest JSON](Quests.md) for the first supported quest schema and the built-in `Tales of a Lost Civilization` pattern.

## Dialogue Trees

Dialogue tree files live in `data/<namespace>/dialogue_trees/<locale>/`. Use them for authored branching scenes, quest offers, reminders, turn-ins, and future questline chapters. Ambient reusable villager lines still live in `data/villagerretaliation/dialogue/<locale>/`.

See [Dialogue Tree JSON](Dialogue-Trees.md) for entries, nodes, responses, actions, and quest action status lines.

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

## Skill Trade Requests

Skill trade entries can include optional Special Order metadata under `request`. Entries without `request.targetable: true` are still valid normal skill trades, but Respected+ players cannot directly choose them from the Special Order list.

```json
"request": {
  "targetable": true,
  "display_priority": 10,
  "min_reputation": "respected",
  "wait_days": 2,
  "cooldown_days": 3,
  "extra_cost": {
    "item": "minecraft:emerald",
    "count": 12
  }
}
```

Special Orders store the selected skill-trade definition id, not a raw item id. Profession, villager level, skill rank, config gates, quality scaling, costs, results, and enchantment behavior still come from the original skill-trade entry. If a requested definition is missing later, the request is skipped safely.

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

Player item filters accept item ids and item tags. Prefix a tag with `#` inside canonical `player_items`. The older `player_item_tag` / `player_item_tags` fields still load as aliases.

```json
{
  "player_items": ["minecraft:bow", "#minecraft:arrows"],
  "player_item_slots": ["hotbar", "inventory"]
}
```

If `player_items` is set and no slot filter is supplied, the current default is `hands`.

`player_item_slots` narrows an item condition; it does not create one by itself. Pair slot filters with `player_items`, an item tag, a durability range, or an enchantment filter.

Player item filters can also check remaining durability. Use `min_player_item_durability` / `max_player_item_durability` for exact remaining durability, or `min_player_item_durability_percent` / `max_player_item_durability_percent` for ranges that work across different tool tiers. The older `held_item` aliases are accepted for the same fields.

```json
{
  "player_items": ["minecraft:netherite_sword"],
  "player_item_slots": ["main_hand"],
  "min_player_item_durability": 500
}
```

They can also check enchantments. Use a string when one shared level range is enough, or an object when the level range belongs to a specific enchantment. Enchantment checks look at normal item enchantments and stored enchanted-book enchantments.

```json
{
  "player_items": ["#minecraft:swords"],
  "player_item_slots": ["main_hand"],
  "player_item_enchantments": [
    {
      "id": "minecraft:sharpness",
      "min_level": 3
    }
  ]
}
```

Dialogue options can remove item(s) from the player when the option is selected. Use canonical `give_items` for a hand-in; it defaults to storing items in the villager inventory. The older `take_items` / `payment` aliases still load and default to discarding the removed items.

```json
{
  "give_items": {
    "item": "minecraft:nether_star",
    "count": 1,
    "store_in_villager_inventory": true,
    "failure_response": "Bring me the star first."
  }
}
```

`give_items` accepts `item` / `items`, `tag` / `tags`, `count` / `amount`, `destination`, `overflow_destination`, `require_space`, `success_response` / `success_responses`, and `failure_response` / `failure_responses`. Normal dialogue destinations are `villager_inventory`, `discard`, and `drop_at_villager`.

Villager equipment filters are available anywhere the rule is evaluated against a specific villager: dialogue options, lines, messages, openings, closings, pacify lines, notifications, gift preferences, gift rewards, pacification payments, and profession loot rules. Use canonical `requires_villager_armed` to require a usable weapon in either hand, or canonical `requires_villager_unarmed` to require no usable weapon. The shorter aliases `villager_armed` and `villager_unarmed` are still accepted.

```json
{
  "requires_villager_armed": true
}
```

Forced dialogue entries use witness-specific names for the same check: `requires_witness_armed` / `witness_armed` and `requires_witness_unarmed` / `witness_unarmed`.

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

Dialogue filters use dispositions, which are derived from reputation and current context. These are the legacy dialogue tone buckets, not the beta.12 temporary mood system:

```text
friendly
respectful
neutral
cautious
rude
hostile
fearful
```

Leave `dispositions` empty or omit it when a line should work with any dialogue disposition.

## Beta.12 Moods And Social Attributes

Beta.12 adds optional line filters for temporary mood and long-term social profile attributes. These fields are only for dialogue `lines`; beta.11 packs should not use them.

Temporary mood values:

```text
neutral
content
grateful
afraid
angry
suspicious
grieving
protective
hopeful
stressed
proud
lonely
```

Use `mood` for one value or `moods` for several values. Add `min_mood_intensity` when the line should only appear while the mood is strong enough.

```json
{
  "mood": "protective",
  "min_mood_intensity": 25
}
```

Social Attributes are persistent villager profile scores from 1 to 100:

```text
knowledge
guts
proficiency
kindness
charm
```

Use `requires_high_knowledge`, `requires_high_guts`, `requires_high_proficiency`, `requires_high_kindness`, or `requires_high_charm` as shorthand for a score of 60 or higher. Use exact ranges when a pack needs tighter control, such as `min_kindness`, `max_kindness`, `min_charm`, or `max_charm`.

```json
{
  "moods": ["grateful", "hopeful"],
  "min_mood_intensity": 20,
  "requires_high_kindness": true,
  "min_charm": 45
}
```

Dialogue options, lines, and forced-dialogue entries can also check the player's current reputation with the specific villager using `reputation_level`, `reputation_levels`, `min_reputation`, and `max_reputation`. These fields let packs show different choices, lines, or whole event responses for trusted, neutral, suspicious, hostile, or exact numeric reputation ranges without writing a new event system.

## Dialogue Requests And Notification Triggers

Dialogue `request` values and notification `trigger` values have their own expandable example catalogs:

- [Dialogue Requests](Dialogue-Requests.md) covers every current `options[].request` and `lines[].request` value.
- [Notification Triggers](Notification-Triggers.md) covers every built-in notification `trigger` value from the current data files.

## Forced Dialogue Events

For the full field reference, trigger behavior, and examples, see [Forced Dialogue JSON](Forced-Dialogue.md).

Forced dialogue files live under:

```text
data/villagerretaliation/forced_dialogue/*.json
```

They define event-driven dialogue moments that can interrupt the player with a locked option list. Built-in triggers include `container_theft`, fired when a player removes items from a chest, barrel, or shulker box after a villager witnesses the theft with line of sight, `container_opened`, fired when the server config watches container opening instead of theft, `container_broken`, fired when a player breaks a watched container, `retaliation_started`, fired when a villager acquires the current player as a retaliation target, `player_item_proximity`, fired when a nearby visible player carries a matching held or worn item, and `trade_refresh`, used internally by the beta.12 trade-refresh button to load data-driven forced-dialogue option sets. The default config watches opening and breaking of generated containers, applies a large break reputation penalty plus additional loss per generated item dropped, and the built-in default pack targets vanilla village chest loot tables. Built-in opening prompts are reputation-gated: neutral/suspicious players get the standard opening warning, hostile/despised/feared players get harsher responses, and trusted or better players are only interrupted if they take items.

```json
{
  "id": "witnessed_container_theft",
  "trigger": "container_theft",
  "witness_radius": 12.0,
  "requires_line_of_sight": true,
  "initiate_dialogue": true,
  "aggro_immediately": false,
  "reputation": -8,
  "lines": [
    "Stop right there. That {container} is not yours to empty.",
    "I saw what you took. Put {stolen_stack} back."
  ],
  "options": [
    {
      "id": "apologize",
      "label": "Apologize",
      "response": "Words are easy after the lid closes.",
      "reputation": 2,
      "aggro": false,
      "end_conversation": true
    },
    {
      "id": "deny",
      "label": "Deny it",
      "response": "I watched you take from it.",
      "reputation": -4,
      "aggro": true,
      "end_conversation": true
    }
  ]
}
```

Forced dialogue supports either one root object or an `entries` array. `priority` chooses between multiple matching definitions, with lower numbers winning. Entries can use `line` for one opening or `lines` for random opening variations. They can also use `witness_profession`, `witness_professions`, or `professions` to require a specific witnessing villager profession, `force_camera_towards_villager` to smoothly focus the player camera during the forced conversation, `loot_table` or `loot_tables` to match specific generated container loot tables, and `target_entity_type` / `target_entity_types` to match retaliation targets such as `minecraft:player`. `min_recent_retaliations` and `max_recent_retaliations` let packs escalate repeated aggro incidents. The mod remembers a generated container's original loot table after first detection so later opens can still match after Minecraft clears the live loot table.

Forced-dialogue entries and options can use `reputation_level`, `reputation_levels`, `min_reputation`, and `max_reputation` to appear only for matching current reputation with the witness. Options can use `response` plus `responses` for random response variations, and `take_items` / `take_stolen_items` outcomes can use `success_responses` and `failure_responses` alongside the singular response fields. Options can also use `take_items` to remove a total `count` of matching `item` / `items` or `tag` / `tags` from the player's inventory before the option succeeds, or `take_stolen_items` / `return_stolen_items` to remove the exact stacks stolen during a `container_theft` event. Removed items can be discarded, moved into the witnessing villager's inventory, returned to the source container, moved into the villager inventory and then the source container, or dropped at the villager/container through `destination` and `overflow_destination`. `aggro_chance` gives any option a 0.0 to 1.0 chance to aggro after its outcome.

Escape and unexpected closes use `leave_option` or the first matching `leave_options` entry, so leaving can have its own response, reputation, stolen-item return, aggro chance, and end-conversation behavior. If a `container_theft` entry does not define either leave field, the generated default returns stolen stacks through `villager_inventory_then_source_container` and rolls an aggro chance based on the player's reputation tier.

Template tokens currently include `{villager}`, `{player}`, `{target}`, `{target_name}`, `{target_kind}`, `{target_type}`, `{container}`, `{count}`, `{item}`, `{item_id}`, `{item_count}`, `{item_stack}`, `{items}`, `{loot_table}`, `{prior_retaliations}`, `{retaliation_offense}`, `{payment_count}`, `{payment_items}`, `{payment_item}`, `{payment_item_id}`, `{payment_stack}`, `{given_count}`, `{given_item}`, `{given_item_id}`, `{given_stack}`, `{given_items}`, `{stolen_item}`, `{stolen_item_id}`, `{stolen_count}`, `{stolen_item_count}`, `{stolen_stack}`, `{stolen_items}`, `{days_since_seen}`, `{day_or_days}`, `{days_since_seen_phrase}`, `{x}`, `{y}`, and `{z}`.

## Village Event Tags

Dialogue lines can filter recent village memories with `event_tags` and player-specific recent memories with `player_event_tags`.

```json
{
  "event_tags": ["raid"],
  "player_event_tags": ["player_defended_raid"]
}
```

Container theft memories use `player_container_theft` and can be narrowed with a `conditions` memory block using `source: "this_villager"` or `source: "other_villager"`. The older `requires_container_theft_to_self` and `requires_container_theft_from_other` line fields still load in beta.12, but are deprecated for removal in beta.13. Theft lines can reference `{stolen_item}`, `{stolen_count}`, `{stolen_stack}`, `{stolen_container}`, `{theft_witness}`, and `{theft_witness_possessive}`.

Retaliation memories use `villager_retaliation_started` and can be narrowed with a `conditions` memory block using `source: "this_villager"` or `source: "other_villager"`, plus `retaliation_target_entity_types` for target type filters. The older `requires_retaliation_to_self` and `requires_retaliation_from_other` fields still load in beta.12, but are deprecated for removal in beta.13. Retaliation lines can reference `{retaliation_target}`, `{retaliation_target_name}`, `{retaliation_target_kind}`, `{retaliation_target_type}`, `{retaliation_witness}`, and `{retaliation_witness_possessive}`.

Baby villager hit memories use `baby_villager_attacked`. Pair it with `player_event_tags: ["player_attacked_villager"]` when a line should accuse or react to the current player.

For the full current list, when each value is remembered, and dropdown examples for simple and expanded uses, see [Event Tags](Event-Tags.md).

## Weight And Chance

`weight` controls weighted random selection among matching entries. Higher values are more likely. Missing weights usually default to `10`, and values below `1` are clamped or ignored depending on the system.

Normal dialogue `lines` also support `priority`, `category`, and `text_key` in beta.12+. Higher `priority` values narrow the candidate pool before weighted random selection. `category` is only an author/debug label surfaced by `/villagerretaliation dialogue explain`. `text_key` resolves the line text from a keyed dialogue message, which lets locale packs override wording without copying the line's rule filters.

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

For keyed dialogue `messages`, `openings`, and `closings`, entries with a profession filter default to adult-only unless they explicitly set `show_for_babies: true`. This prevents profession/job-site lines from appearing on baby villagers unless a pack opts in.

Baby villagers can flee from witnessed villager deaths when `retaliation.babyVillagersFleeWitnessedDeaths` is enabled. Built-in notification data separates adult and baby witness-death alert text with `show_for_adults` and `show_for_babies`, and baby-hit alerts use the same age filters on `alert.player_attacked_villager` / `alert.villager_damaged`.

Dialogue `lines` may include expressive chat text effects. Inline tags are the preferred format for partial emphasis:

```json
{
  "id": "expressive_line",
  "request": "question",
  "text": "<wavy><italics>Hey Traveller!</italics></wavy> Care to see my wares?"
}
```

Supported tags: `<wavy>` / `<wave>`, `<shake>` / `<shaky>`, `<pulse>` / `<pulsing>`, `<jump>` / `<jumping>` / `<bounce>`, `<rainbow>`, `<italics>` / `<italic>` / `<i>`, `<bold>` / `<b>`, `<underlined>` / `<underline>` / `<u>`, `<strikethrough>` / `<strike>` / `<s>`, `<obfuscated>` / `<obfuscate>` / `<magic>`, vanilla color names like `<red>`, `<color:#ffcc66>`, and `<gradient:#ff7a7a:#7aa8ff>`. Incoming chat messages containing these tags are styled client-side too, so raw markup is converted before it appears in chat.

Whole-line shorthand fields are also accepted:

```json
{
  "id": "expressive_line",
  "request": "question",
  "text": "That is... not a normal cave sound.",
  "italics": true,
  "wavy": true,
  "rainbow": true,
  "shake": true
}
```

The grouped form is also accepted:

```json
"text_effects": {
  "italic": true,
  "bold": true,
  "underlined": true,
  "rainbow": true,
  "wavy": true,
  "pulse": true
}
```

If `rainbow` is combined with `color` or `gradient_start` / `gradient_end`, the rainbow colors take precedence.

These effects apply to villager chat response text only, not in-world text indicators.

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
- The datapack path decides the system loader. Dialogue, forced dialogue, and notifications must be in their documented folders; top-level keys are not rerouted between systems.
- A misspelled enum value is ignored by many loaders. Current dialogue, forced-dialogue, and notification files log warnings for several common wrong-family trigger and unsupported-field mistakes.
- A misspelled or unloaded custom profession id is ignored by profession filters and logs a warning in current dialogue, forced-dialogue, and notification loaders.
- A `player_item_slots` field without an item, tag, durability, or enchantment selector cannot match anything by itself and logs a warning in current dialogue, forced-dialogue, and notification loaders.
- A missing required field usually causes only that entry to be skipped.
- A broken model JSON falls back to the built-in model, and logs a warning.
