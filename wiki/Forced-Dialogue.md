# Forced Dialogue JSON

Forced dialogue JSON controls event-driven conversation moments and event-driven villager chat lines. Use it for scenes like a villager catching the player stealing from a village chest, sending a villager-styled chat warning when retaliation starts, or stopping to warn the player just before retaliation turns into a fight.

## Paths

Forced dialogue files must be in the `villagerretaliation` namespace:

```text
data/villagerretaliation/forced_dialogue/default.json
data/villagerretaliation/forced_dialogue/my_pack_events.json
```

Use a unique file name for addon entries. A datapack file at `data/villagerretaliation/forced_dialogue/default.json` replaces the mod's built-in default file at the Minecraft resource layer, so only use that exact path when you intentionally want a full-file override.

Forced dialogue text is server-side datapack text. Button labels and villager responses in forced dialogue entries are not resource-pack language keys.

The built-in trade-refresh UI also uses forced-dialogue data for its locked follow-up conversations. Those entries live under the same `forced_dialogue` path and use `trigger: "trade_refresh"`, but they are selected by the trade-refresh service rather than by a world event witness search.

## Top-Level Shape

A forced dialogue file can be a single entry:

```json
{
  "id": "my_pack.theft_warning",
  "trigger": "container_theft",
  "output": {
    "mode": "forced_dialogue"
  },
  "lines": [
    "Stop right there.",
    "I saw what you took.",
    "That chest was not yours to empty."
  ]
}
```

or an `entries` array:

```json
{
  "entries": [
    {
      "id": "my_pack.theft_warning",
      "trigger": "container_theft",
      "output": {
        "mode": "forced_dialogue"
      },
      "lines": [
        "Stop right there.",
        "I saw what you took.",
        "That chest was not yours to empty."
      ]
    }
  ]
}
```

## Entry Fields

| Field | Type | Default | Purpose |
| --- | --- | --- | --- |
| `id` | string | generated from file path | Stable id for replacement by later-loading files. |
| `trigger` | enum | required | Event that can start this forced dialogue. |
| `event` | enum | none | Legacy alias for `trigger`; new packs should write `trigger`. |
| `line` | string | required unless `lines` is set | Villager line shown when the event fires. |
| `lines` | array | required unless `line` is set | Alternate villager lines. One is selected at random when the event fires. |
| `priority` | integer | `0` | Lower values win when multiple entries match the same trigger. |
| `chance` | number | `1.0` | Random chance from `0.0` to `1.0` before a matching event entry runs. |
| `witness_radius` | number | `12.0` | Search radius for a witnessing villager. |
| `witness_profession` | string | any | Restricts this entry to a witnessing villager profession. |
| `witness_professions` | array | any | Restricts this entry to one of several witnessing villager professions. `professions` is also accepted as an alias. |
| `requires_witness_unarmed` | boolean | `false` | Requires the witnessing villager to have no usable weapon in either hand. `witness_unarmed` is also accepted as an alias. |
| `requires_witness_armed` | boolean | `false` | Requires the witnessing villager to have a usable weapon in either hand. `witness_armed` is also accepted as an alias. |
| `player_items` | string or array | none | For `player_item_proximity`, requires the nearby player to carry one matching item or item tag. Prefix tags with `#`. Aliases: `player_item`, `player_item_tag`, `player_item_tags`. |
| `player_item_slots` | string or array | `hands` when `player_items` is set | Slots to check for player item filters: `main_hand`, `off_hand`, `hands`, `armor`, `hotbar`, `inventory`, `equipment`, or `any`. Alias: `player_item_slot`. |
| `min_player_item_durability` | integer | none | Minimum remaining durability on the matched player item. Alias: `min_held_item_durability`. |
| `max_player_item_durability` | integer | none | Maximum remaining durability on the matched player item. Alias: `max_held_item_durability`. |
| `min_player_item_durability_percent` | integer | none | Minimum remaining durability percent on the matched player item. Alias: `min_held_item_durability_percent`. |
| `max_player_item_durability_percent` | integer | none | Maximum remaining durability percent on the matched player item. Alias: `max_held_item_durability_percent`. |
| `player_item_enchantment` | string | none | Requires the matched player item to have this enchantment. Alias: `held_item_enchantment`. |
| `player_item_enchantments` | string, object, or array | none | Requires one matching enchantment. String entries use top-level level filters; object entries can use `id`, `min_level`, and `max_level`. Alias: `held_item_enchantments`. |
| `min_player_item_enchantment_level` | integer | none | Minimum level for string enchantment filters. Alias: `min_held_item_enchantment_level`. |
| `max_player_item_enchantment_level` | integer | none | Maximum level for string enchantment filters. Alias: `max_held_item_enchantment_level`. |
| `requires_line_of_sight` | boolean | `true` | Requires the witness to see the player. Container triggers also require sight to the event block. |
| `output` | object | `{ "mode": "forced_dialogue" }` | Delivery channel for the event line. See Output Fields below. |
| `initiate_dialogue` | boolean | `true` | Opens the locked interaction screen when `output.mode` is `forced_dialogue`. If false, only the line is sent. |
| `aggro_immediately` | boolean | `false` | Makes the witness attack immediately after the event line. |
| `force_camera_towards_villager` | boolean | `false` | Smoothly turns the player's camera toward the witnessing villager while this forced dialogue is active. |
| `reputation` | integer | `0` | Reputation change applied to the witnessing villager when the event is caught. |
| `reputation_level` | string or array | any | Alias for `reputation_levels`. |
| `reputation_levels` | string or array | any | Allows this entry only for the player's current reputation tier with the witnessing villager. |
| `min_reputation` | integer | none | Minimum exact reputation value with the witnessing villager. |
| `max_reputation` | integer | none | Maximum exact reputation value with the witnessing villager. |
| `loot_table` | string | none | Optional single loot table id this entry can match. |
| `loot_tables` | array | none | Optional loot table ids this entry can match. If omitted, the entry can match any watched container. |
| `target_entity_type` | string | none | Optional single retaliation target entity id such as `minecraft:player`. |
| `target_entity_types` | array | none | Optional retaliation target entity ids. `target_entities` is still accepted as an alias. |
| `min_recent_container_thefts` | integer | `0` | Minimum remembered container thefts by this player near the witness's village before this entry can trigger. |
| `max_recent_container_thefts` | integer | unlimited | Maximum remembered container thefts by this player near the witness's village before this entry can trigger. |
| `min_recent_retaliations` | integer | `0` | Minimum earlier `villager_retaliation_started` memories for this player near the villager's village. Useful for escalation. |
| `max_recent_retaliations` | integer | unlimited | Maximum earlier `villager_retaliation_started` memories for this player near the villager's village. |
| `options` | array | generated Leave option | Choices shown in the forced dialogue screen. |
| `leave_option` | object | generated Leave option | Outcome used by the visible Leave choice, Escape, and unexpected client closes. Uses the same fields as an option except the id is always `leave`. |
| `leave_options` | array | generated theft return options for `container_theft`, otherwise generated Leave option | Reputation-filtered Leave/Escape outcomes. The first matching option by `order` is used. |

When multiple entries match, lower `priority` wins. If priority is tied, an entry with matching `loot_table` or `loot_tables` wins over a generic entry.

Use `lines` when an event can happen often. The selected line is resolved through the same placeholders as `line`, so variations can reference `{stolen_stack}`, `{container}`, `{villager}`, and the other forced-dialogue tokens.

Use `chance` when a callout should be occasional. If the chance roll fails, the event stays silent and does not fall through to lower-priority entries.

Built-in trigger values:

```text
container_theft
container_opened
container_broken
retaliation_started
player_item_proximity
trade_refresh
```

`trade_refresh` is an internal forced-dialogue option-template trigger used by the villager trade refresh button. It does not fire from a generic world event by itself.

## Output Modes

Use `output.mode` to choose how the event line is delivered. This keeps `trigger` focused on what happened and `output` focused on how players see it.

| Mode | Behavior |
| --- | --- |
| `forced_dialogue` | Opens the locked interaction screen when `initiate_dialogue` is true. This is the default. |
| `chat` | Sends the line as villager-styled chat without opening the interaction screen. |

`output.radius` controls how far chat output is broadcast from the speaking villager. If omitted, the configured forced-dialogue chat distance is used. `retaliation_started` chat output can respond to player or non-player retaliation targets. For example, a villager can speak when retaliation starts:

```json
{
  "id": "my_pack.retaliation_warning",
  "trigger": "retaliation_started",
  "lines": [
    "You should have stayed away, {player}.",
    "The village remembers what you did.",
    "No more warnings."
  ],
  "output": {
    "mode": "chat",
    "radius": 24
  }
}
```

Use the same trigger with different `output.mode` values when an event should both emit a chat callout and open a locked forced-dialogue scene. Chat entries can play first, then a separate `forced_dialogue` entry for the same trigger can still run.

For direct reactions that should require an actual witness, prefer a forced-dialogue entry with `output.mode: "chat"` over ambient notification text. That gives the reaction access to `witness_radius`, `requires_line_of_sight`, witness profession filters, chance, priority, and target filters while still delivering the result as normal villager chat instead of opening the interaction screen. Use notifications for HUD/world-text feedback that does not need forced-dialogue witness selection, locked-screen options, stolen-item return behavior, or forced-dialogue item payments.

Use `trigger: "player_item_proximity"` for held or worn item reactions when a player walks near a villager. The trigger requires a `player_items` or `player_item_tags` filter so it does not fire for every nearby player:

```json
{
  "id": "my_pack.diamond_sword_warning",
  "trigger": "player_item_proximity",
  "player_items": ["minecraft:diamond_sword"],
  "player_item_slots": ["main_hand"],
  "witness_radius": 8,
  "requires_line_of_sight": true,
  "lines": [
    "Easy with {held_item}, {player}.",
    "That blade is hard to miss.",
    "Keep {held_item} pointed away from the village."
  ],
  "output": {
    "mode": "chat",
    "radius": 16
  }
}
```

Durability filters can split item callouts into threshold-style branches. Use `priority` when ranges overlap, or use `max_player_item_durability` / `max_player_item_durability_percent` to make ranges exclusive:

```json
{
  "entries": [
    {
      "id": "my_pack.netherite_sword_fresh",
      "trigger": "player_item_proximity",
      "priority": 0,
      "player_items": ["minecraft:netherite_sword"],
      "player_item_slots": ["main_hand"],
      "min_player_item_durability": 500,
      "line": "{held_item} still has {held_item_durability} durability. That blade is ready.",
      "output": { "mode": "chat" }
    },
    {
      "id": "my_pack.netherite_sword_usable",
      "trigger": "player_item_proximity",
      "priority": 1,
      "player_items": ["minecraft:netherite_sword"],
      "player_item_slots": ["main_hand"],
      "min_player_item_durability": 200,
      "max_player_item_durability": 499,
      "line": "{held_item} has {held_item_durability} durability left. Mend it soon.",
      "output": { "mode": "chat" }
    },
    {
      "id": "my_pack.netherite_sword_low",
      "trigger": "player_item_proximity",
      "priority": 2,
      "player_items": ["minecraft:netherite_sword"],
      "player_item_slots": ["main_hand"],
      "max_player_item_durability": 199,
      "line": "{held_item} is nearly spent. Do not trust it with a long fight.",
      "output": { "mode": "chat" }
    }
  ]
}
```

Enchantment filters can be plain ids with shared top-level level bounds, or objects with per-enchantment bounds:

```json
{
  "id": "my_pack.sharp_weapon",
  "trigger": "player_item_proximity",
  "priority": 0,
  "player_items": ["#minecraft:swords"],
  "player_item_slots": ["main_hand"],
  "player_item_enchantments": [
    {
      "id": "minecraft:sharpness",
      "min_level": 3
    }
  ],
  "line": "{held_item} carries {held_item_enchantment_full}. That edge has intent.",
  "output": { "mode": "chat" }
}
```

If a `container_theft` entry does not define `leave_option` or `leave_options`, the generated default Leave outcome takes the stolen stacks back with `villager_inventory_then_source_container`, applies a reputation penalty, and rolls an aggro chance based on reputation: trusted or better is low risk, neutral/suspicious is moderate risk, and hostile/despised/feared is high risk.

## Option Fields

| Field | Type | Default | Purpose |
| --- | --- | --- | --- |
| `id` | string | required | Choice id. Keep ids unique among options that can be visible at the same time. Reputation-filtered variants may reuse an id when the filters are mutually exclusive. |
| `label` | string | required | Button text shown to the player. |
| `response` | string | none | Villager response after the player chooses this option. When `responses` is also set, this is included as the first possible variation. |
| `responses` | array | none | Additional villager response variations for this option. One response is selected at random. |
| `reputation` | integer | `0` | Reputation change applied after this option. |
| `aggro` | boolean | `false` | Makes the villager attack after this option. |
| `aggro_chance` | number | `0.0` | Chance from `0.0` to `1.0` that the villager attacks after this option. |
| `end_conversation` | boolean | `true` | Closes the forced dialogue after this option. |
| `order` | integer | option index | Sort order in the locked option list. |
| `reputation_level` | string or array | any | Alias for `reputation_levels`. |
| `reputation_levels` | string or array | any | Shows this option only for the player's current reputation tier with the witnessing villager. |
| `min_reputation` | integer | none | Minimum exact reputation value with the witnessing villager. |
| `max_reputation` | integer | none | Maximum exact reputation value with the witnessing villager. |
| `conditions` | array | none | Shows this option only when all dialogue condition blocks match. Supports the same `conditions` block types as normal dialogue, including `social_attribute` and `skill`. |
| `take_items` | object | none | Removes a configured payment from the player's inventory before the option succeeds. |
| `take_stolen_items` | boolean or object | none | For `container_theft`, removes the specific item stacks stolen from the source container before the option succeeds. |

Use reputation filters on entries to swap the whole event by rank, or on options to change the choices available inside one event. For example, the built-in container opening prompts only catch neutral and suspicious players on opening, hostile/despised/feared players get harsher opening responses, and trusted or better players can get a nearby trusted villager to vouch for them instead of starting a locked confrontation.

Use `conditions` on options for personality-aware or skill-aware choices. For example, a deception option can require a low-intellect witness:

```json
{
  "id": "my_pack.confuse_theft_witness",
  "label": "What chest?",
  "responses": [
    "No chest? Hm. I may have accused the wrong moment."
  ],
  "conditions": [
    {
      "type": "social_attribute",
      "attribute": "intellect",
      "max": 35
    }
  ]
}
```

Escape does not bypass forced dialogue. Pressing Escape activates the entry's matching `leave_option` / `leave_options` outcome, so pack makers can attach response text, reputation changes, stolen-item returns, aggro chance, or other outcomes to leaving.

## Trade Refresh Dialogue

Beta.12 uses forced-dialogue data to control the choice lists shown after a player asks a villager to refresh one trade slot. The opening sentence is still a normal dialogue message, so it can be localized through `data/villagerretaliation/dialogue/<locale>/`. The options and villager replies are forced-dialogue entries.

The built-in option-template ids are:

```text
trade_refresh.ready_options
trade_refresh.available_options
trade_refresh.unavailable_options
trade_refresh.revered_options
trade_refresh.special_order_select_options
trade_refresh.special_order_confirm_options
trade_refresh.special_order_status_options
```

Ready trade-refresh follow-ups can chain into a shared forced-dialogue group. If another nearby villager also has ready refreshes for the same player, the current locked screen can be replaced with an interjection from that villager using the dialogue message key `trade_refresh.ready_interjection`. If the player is already in a container confrontation, ready refreshes use `trade_refresh.ready_theft_interjection` instead. The second and third speakers try `.second` and `.third` message-key suffixes first, then fall back to the base key if a pack does not define them.

`trade_refresh.ready_options` is used when queued refreshes or Special Orders become ready for the interacting player. It normally shows:

```text
Trade
Thanks!
What got restocked?
Leave
```

`trade_refresh.available_options` is used for accepted refresh requests and already-pending requests. It normally shows:

```text
Trade
Thanks!
Why so long?
Leave
```

`trade_refresh.unavailable_options` is used when the villager cannot refresh that slot yet. It normally shows:

```text
Trade
What do you need?
Leave
```

`trade_refresh.revered_options` is used before scheduling anything when a Respected+ player clicks a refresh button. It normally shows:

```text
Surprise me.
I'm looking for something specific.
Trade
Never mind.
```

`trade_refresh.special_order_select_options` supplies the cancel/leave wording for the dynamic Special Order list. The trade rows themselves are generated from targetable `skill_trades` definitions.

`trade_refresh.special_order_confirm_options` supplies the final confirmation buttons after the player chooses a target definition.

`trade_refresh.special_order_status_options` supplies the leave wording for the dynamic order-status list. The rows are generated from the player's active Special Orders with that villager.

The trade option must use this id if it should return to the merchant screen:

```text
trade_refresh.trade
```

The built-in data also uses:

```text
trade_refresh.ready
trade_refresh.ready_interjection
trade_refresh.ready_theft_interjection
trade_refresh.thanks
trade_refresh.why_so_long
trade_refresh.requirements
trade_refresh.surprise_me
trade_refresh.special_order
trade_refresh.confirm_special_order
trade_refresh.special_order.status
leave
```

To replace the built-in wording, add a forced-dialogue file with entries using the same ids. The trade-refresh service keeps the actual opening line from the selected dialogue message and copies the options from the matching forced-dialogue entry. Special Order opening, unavailable, queued, active-limit, cooldown, payment, selection, confirmation, order-status, and interjection lines remain normal dialogue message keys under `data/villagerretaliation/dialogue/<locale>/`.

```json
{
  "entries": [
    {
      "id": "trade_refresh.available_options",
      "trigger": "trade_refresh",
      "line": "Trade refresh available option set.",
      "force_camera_towards_villager": true,
      "requires_line_of_sight": false,
      "options": [
        {
          "id": "trade_refresh.trade",
          "label": "Trade",
          "order": 0,
          "end_conversation": false,
          "reputation_levels": ["trusted", "respected", "revered", "royalty"],
          "responses": [
            "Of course. Let us get back to business.",
            "The counter is open. I will keep this orderly."
          ]
        },
        {
          "id": "trade_refresh.thanks",
          "label": "Thanks!",
          "order": 1,
          "end_conversation": true,
          "responses": [
            "You are welcome. Come back tomorrow."
          ]
        }
      ]
    }
  ]
}
```

When reusing the same option id for different reputation tiers, make the `reputation_levels`, `min_reputation`, or `max_reputation` filters mutually exclusive so only one version of that button is visible to a player.

### `take_items`

Forced dialogue options can take items from the player before applying the option's normal response, reputation, aggro, and end-conversation behavior.

```json
{
  "id": "offer_payment",
  "label": "Offer payment",
  "response": "That will help replace what you disturbed.",
  "take_items": {
    "items": [
      "minecraft:emerald"
    ],
    "count": 8,
    "destination": "villager_inventory",
    "overflow_destination": "drop_at_villager",
    "success_response": "Eight emeralds is enough for me to believe you mean it.",
    "failure_response": "You do not have eight emeralds to offer.",
    "failure_reputation": -2,
    "failure_end_conversation": false
  },
  "reputation": 2,
  "end_conversation": true
}
```

`take_items` accepts `item` or `items`, plus `tag` or `tags`. Tags can also be written with a `#` prefix inside `items`. `count` is the total number removed across matching stacks, so `128` removes two full stacks when enough items exist. The removal checks the player's inventory and offhand, and transferred stacks keep their item components.

`destination` controls where removed items go:

| Value | Behavior |
| --- | --- |
| `discard` | Removes the items from the player. This is the default. |
| `villager_inventory` | Moves the items into the witnessing villager's inventory. |
| `villager_inventory_then_source_container` | Moves as much as possible into the witnessing villager's inventory, then returns the rest to the source container. |
| `source_container` | Moves the items into the container that started the forced dialogue. |
| `drop_at_villager` | Drops the items at the witnessing villager. |
| `drop_at_container` | Drops the items at the source container. |

When the destination is an inventory or container, `require_space` defaults to `true`, so the option fails unless the full payment can fit. Set `overflow_destination` to a drop or discard destination to allow overflow while keeping the payment successful.

`villager_inventory_then_source_container` first tries to put returned items into the witnessing villager's inventory, then puts any remainder back into the source container. This is useful for stolen-item return choices.

Payment outcomes can use `success_response` / `success_responses` and `failure_response` / `failure_responses`. When both the singular and array forms are present, the singular response is included as the first possible variation.

If the player does not have enough matching items, the normal option response and reputation do not apply. Instead, `failure_response`, `failure_responses`, `failure_reputation`, `failure_end_conversation`, and `failure_aggro` control what happens. Leaving `failure_end_conversation` false keeps the forced dialogue open so the player can choose another response.

### `take_stolen_items`

`take_stolen_items` is for `container_theft` options such as "Return it". It removes the exact item stacks that were missing from the container snapshot. It can be `true` for defaults, or an object with these fields:

| Field | Type | Default | Purpose |
| --- | --- | --- | --- |
| `destination` | enum | `villager_inventory_then_source_container` | Where the returned stolen items go. |
| `overflow_destination` | enum | none | Optional fallback if the destination leaves a remainder. |
| `require_space` | boolean | `true` | Fails unless the destination can accept the items. |
| `success_response` | string | option `response` | Response after the stolen items are successfully removed. When `success_responses` is also set, this is included as the first possible variation. |
| `success_responses` | array | none | Additional successful-return response variations. |
| `failure_response` | string | none | Response if the player no longer has the stolen items. When `failure_responses` is also set, this is included as the first possible variation. |
| `failure_responses` | array | none | Additional failed-return response variations. |
| `success_reputation` | integer | `0` | Extra reputation change after a successful return. |
| `failure_reputation` | integer | `0` | Reputation change after a failed return. |
| `failure_end_conversation` | boolean | `false` | Closes the forced dialogue after a failed return. |
| `failure_aggro` | boolean | `false` | Makes the villager attack after a failed return. |

## Triggers

### `container_theft`

Fires when a player opens a watched container and closes it with fewer items than it had when opened.

### `container_opened`

Fires when a player opens a watched container. This trigger is used when the server config's container forced-dialogue trigger is set to `OPENING`.

The built-in default pack gates opening prompts by reputation: neutral and suspicious players are stopped with the standard warning before they can continue browsing, hostile/despised/feared players get more severe warnings, and trusted or better players can have a nearby trusted villager vouch for them. The vouch roll scales with exact reputation: an allowed vouch leaves the chest open, while a denied vouch closes it with a short chat line and no locked forced-dialogue prompt. Taking items still triggers `container_theft` for every rank.

Additional nearby witnesses can join an active container confrontation. Theft backups use `container_theft.backup_interjection`; opening-only backups use `container_opened.backup_interjection`. Like trade-refresh interjections, second and third speakers try `.second` and `.third` message-key suffixes before falling back to the base key. Each additional theft witness applies the matching forced-dialogue definition's witness consequences, including reputation and theft memory.

### `container_broken`

Fires when a player breaks a watched container, using the same watched-container eligibility as opening/theft checks. Like piglin guarded-container anger, the block break itself can provoke a response; unlike piglins, matching entries still use Villager Retaliation's witness filters and `requires_line_of_sight` rules.

When a `container_broken` entry matches, the mod applies the configured `containerBreakReputationLoss` to the witnessing villager. If the container has a generated loot table, the mod unpacks it before the break snapshot and also applies `generatedContainerBreakItemReputationLoss` once per dropped item count. Any `reputation` value on the forced-dialogue entry is added on top of that built-in penalty.

Watched containers:

```text
chests
barrels
shulker boxes
```

The built-in events require a villager witness with line of sight to the player and the container block. If no adult villager can witness the event, no forced dialogue starts.

Server config controls whether generated watched containers trigger on actual theft or on opening. By default, Villager Retaliation watches `OPENING`, so the built-in village chest confrontation fires when a player opens a generated village chest. The mod records the container's original loot table the first time it sees one, allowing later opens to keep matching generated-container forced dialogue after Minecraft unpacks and clears the live loot table. Servers can switch back to theft-only behavior.

Generated-container detection initially checks for an unresolved loot table through Minecraft's `RandomizableContainer` interface, so modded generated containers can participate when they expose loot tables the same way vanilla generated containers do.

The built-in `default.json` includes village-specific entries for vanilla village chest loot tables, plus lower-priority generic theft and break fallbacks for packs or configs that still want broad detection.

### `retaliation_started`

Fires when a villager acquires a new retaliation target and that target is the current player. This lets datapacks intercept the moment just before combat fully commits, so a villager can warn, accuse, demand payment, or go straight to violence depending on the selected entry.

`retaliation_started` uses the retaliating villager as the witness, so `witness_profession`, `requires_witness_unarmed`, `requires_witness_armed`, `requires_line_of_sight`, `witness_radius`, `target_entity_type`, `target_entity_types`, `min_recent_retaliations`, and `max_recent_retaliations` are the most relevant filters. Because forced dialogue is player-facing, this trigger currently only starts a forced conversation when the retaliation target is a player.

Use `trigger: "retaliation_started"` with `output.mode: "chat"` for a separate chat line on the same event. Chat output can also fire for non-player targets and broadcasts that line to nearby players.

Forced dialogue entries can optionally filter by generated container loot table:

```json
{
  "id": "examplepack.armorer_chest_opened",
  "trigger": "container_opened",
  "witness_professions": [
    "armorer"
  ],
  "loot_tables": [
    "minecraft:chests/village/village_armorer"
  ],
  "lines": [
    "That chest belongs to the armorer.",
    "Close the armory chest. Those supplies are counted."
  ],
  "force_camera_towards_villager": true
}
```

## Placeholders

Forced dialogue `line`, `lines`, option `response` / `responses`, `leave_option.response` / `leave_option.responses`, and payment or return outcome response text can use:

```text
{villager}
{player}
{target}
{target_name}
{target_kind}
{target_type}
{container}
{count}
{item}
{item_id}
{item_count}
{item_stack}
{items}
{loot_table}
{previous_villager}
{current_villager}
{interrupted_villager}
{interjection_index}
{interjection_ordinal}
{speaker_index}
{speaker_ordinal}
{prior_container_thefts}
{container_theft_offense}
{container_theft_again_phrase}
{container_theft_time_word}
{prior_retaliations}
{retaliation_offense}
{payment_count}
{payment_items}
{stolen_item}
{stolen_item_id}
{stolen_count}
{stolen_item_count}
{stolen_count_word}
{stolen_item_pronoun}
{stolen_item_reference}
{stolen_stack}
{stolen_items}
{witnessed_container_thefts}
{x}
{y}
{z}
```

`{target}` / `{target_name}` is the retaliation target display name, `{target_kind}` is a lowercased entity description such as `player`, `{target_type}` is the entity id, `{container}` is the block display name, `{item}` / `{stolen_item}` is the representative removed item name or matched player item for `player_item_proximity`, `{item_stack}` / `{stolen_stack}` includes the representative item count, `{items}` / `{stolen_items}` lists all removed stacks, `{count}` / `{stolen_count}` is the representative removed stack count for `container_theft`, `{stolen_count_word}` is `item` or `items`, `{stolen_item_pronoun}` and `{stolen_item_reference}` are readable theft references for backup witnesses, `{loot_table}` is the matched generated loot table id when one exists, `{previous_villager}`, `{current_villager}`, and `{interrupted_villager}` name forced-dialogue speakers during shared interjections, `{interjection_index}` / `{speaker_index}` and `{interjection_ordinal}` / `{speaker_ordinal}` describe the interjection order, `{player_item}` / `{held_item}` is the matched player item name for item-filtered entries, `{player_item_id}` / `{held_item_id}` is the matched item id, `{player_item_slot}` / `{held_item_slot}` is the matched slot, `{player_item_durability}` / `{held_item_durability}` is remaining durability, `{player_item_max_durability}` / `{held_item_max_durability}` is max durability, `{player_item_damage}` / `{held_item_damage}` is current damage, `{player_item_durability_percent}` / `{held_item_durability_percent}` is remaining durability percent, `{player_item_enchantment}` / `{held_item_enchantment}` is the matched enchantment name, `{player_item_enchantment_full}` / `{held_item_enchantment_full}` includes the level, `{player_item_enchantment_id}` / `{held_item_enchantment_id}` is the enchantment id, `{player_item_enchantment_level}` / `{held_item_enchantment_level}` is the matched level, `{prior_container_thefts}` is the number of remembered earlier container thefts by this player near the witness's village, `{container_theft_offense}` is that count plus the current theft, `{witnessed_container_thefts}`, `{container_theft_again_phrase}`, and `{container_theft_time_word}` describe repeat theft memories for backup witnesses, `{prior_retaliations}` is the number of earlier remembered retaliation starts for the same player near this village, `{retaliation_offense}` is that count plus the current retaliation, `{payment_count}` and `{payment_items}` describe a `take_items` option, and `{x}`, `{y}`, `{z}` are the container or villager position.

Forced-dialogue responses also keep session-scoped replacements when a session advances. For example, a trade-refresh interjection option response can still use the `{restocked_summary}` replacements from the interjection that opened the current locked screen, while payment or return-specific replacements are layered on top.

## Example

```json
{
  "entries": [
    {
      "id": "examplepack.witnessed_chest_theft",
      "trigger": "container_theft",
      "priority": 0,
      "witness_radius": 12,
      "requires_line_of_sight": true,
      "initiate_dialogue": true,
      "force_camera_towards_villager": true,
      "aggro_immediately": false,
      "reputation": -8,
      "lines": [
        "Stop right there. I watched you take {stolen_stack}.",
        "That {container} is not yours to empty. Put {stolen_stack} back.",
        "Village stores are not free supplies. Return {stolen_stack}."
      ],
      "options": [
        {
          "id": "return_items",
          "label": "Return it",
          "response": "Good. A returned item is easier to forgive than a hidden one.",
          "reputation": 4,
          "end_conversation": true,
          "order": 0,
          "take_stolen_items": {
            "destination": "villager_inventory_then_source_container",
            "failure_response": "You do not have {stolen_stack} to return.",
            "failure_reputation": -2,
            "failure_end_conversation": false
          }
        },
        {
          "id": "apologize",
          "label": "Apologize",
          "response": "Words are easy after the lid closes. Put your hands to better use.",
          "reputation": 2,
          "aggro": false,
          "end_conversation": true,
          "order": 1
        },
        {
          "id": "deny",
          "label": "Deny it",
          "response": "I watched you take from it. Do not make me repeat myself.",
          "reputation": -4,
          "aggro": true,
          "end_conversation": true,
          "order": 2
        },
        {
          "id": "threaten",
          "label": "Threaten them",
          "response": "Then we are past talking.",
          "reputation": -8,
          "aggro": true,
          "end_conversation": true,
          "order": 3
        }
      ],
      "leave_options": [
        {
          "label": "Leave",
          "response": "I will take {stolen_items} back. Walking away does not make this settled.",
          "reputation_levels": [
            "neutral",
            "suspicious"
          ],
          "reputation": -4,
          "aggro_chance": 0.25,
          "end_conversation": true,
          "order": 1000,
          "take_stolen_items": {
            "destination": "villager_inventory_then_source_container",
            "failure_response": "You no longer have {stolen_items}. Then we are past excuses.",
            "failure_reputation": -5,
            "failure_aggro": true,
            "failure_end_conversation": true
          }
        }
      ]
    }
  ]
}
```

## Behavior Notes

Forced dialogue opens the normal Villager Retaliation interaction screen in a locked mode. The player cannot navigate to Talk, Trade, Gift, Inventory, Recruit, Family, or Relationships from that moment; only the forced event options are available.

If `aggro_immediately` is true, the villager says the event line and attacks without opening the dialogue screen. If `initiate_dialogue` is false and `aggro_immediately` is false, the villager only says the event line. For event callouts that should never open the interaction screen, use `output.mode: "chat"`.
