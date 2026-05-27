# Pack Format Changes

This page tracks JSON, tag, trigger, path, placeholder, and pack-behavior changes that matter to datapack and resource-pack authors.

This is a migration log rather than a full release changelog. Player-facing features can stay in release notes; this page focuses on the pack author question: "Will my pack still work, and what changed?"

## How To Read This Page

Each version section uses the same categories so changes stay easy to scan:

- **Added** - New fields, tags, triggers, paths, placeholders, files, or supported values.
- **Modified** - Existing behavior that changed but still exists.
- **Deprecated** - Still supported for now, but pack authors should move away from it.
- **Removed** - No longer supported.
- **Migration Notes** - Practical steps for updating existing packs.

Breaking changes should call out the old form, the new form, and the practical migration path.

## Current Pack Surface

These pages describe the current supported format:

- [JSON Reference](JSON-Reference.md)
- [Dialogue JSON](Dialogue.md)
- [Forced Dialogue JSON](Forced-Dialogue.md)
- [Dialogue Requests](Dialogue-Requests.md)
- [Event Tags](Event-Tags.md)
- [Notifications JSON](Notifications.md)
- [Notification Triggers](Notification-Triggers.md)
- [Localization Guide](Localization.md)
- [Gift JSON](Gifts.md)
- [Pacification JSON](Pacification.md)
- [Profession Loot JSON](Profession-Loot.md)
- [Skill Trades](Skill-Trades.md)
- [Story Discovery JSON](Story-Discovery.md)
- [Resource Pack Models And Textures](Resource-Pack-Models.md)

## 1.0.0-beta.12 - Unreleased

Pack-facing beta.12 changes introduce persistent Social Attributes and temporary villager moods as optional dialogue filters. Beta.11 datapacks continue to target the beta.11 wiki snapshot and should not use these fields.

### Added

- Added temporary villager mood values for dialogue line filtering: `neutral`, `content`, `grateful`, `afraid`, `angry`, `suspicious`, `grieving`, `protective`, `hopeful`, `stressed`, `proud`, and `lonely`.
- Added dialogue line fields `mood`, `moods`, and `min_mood_intensity`.
- Added Social Attribute dialogue line shorthands `requires_high_knowledge`, `requires_high_guts`, `requires_high_proficiency`, `requires_high_kindness`, and `requires_high_charm`. Each requires the matching profile score to be at least 60.
- Added exact Social Attribute dialogue line ranges: `min_knowledge`, `max_knowledge`, `min_guts`, `max_guts`, `min_proficiency`, `max_proficiency`, `min_kindness`, `max_kindness`, `min_charm`, and `max_charm`.
- Added resource-pack language keys for temporary mood labels under `villagerretaliation.mood.*`, Profile page attribute labels/descriptions under `villagerretaliation.profile.attribute.*`, profile ranks under `villagerretaliation.profile.rank.*`, and Profile UI rows/tooltips under `villagerretaliation.gui.profile.*`.
- Added villager trade refresh support for skill-generated trades. Refresh requests are stored per villager and per offer slot, mature on the next Minecraft day, and replace the selected slot with an eligible skill-trade offer when ready.
- Added persistent per-villager, per-profession skill-trade pools. Villagers remember known skill-trade definition ids for professions they have actually held and reuse that memory when reacquiring the same profession.
- Added Respected Special Orders: high-reputation players can choose targetable skill-trade definitions for longer, cooldown-gated restock requests, up to three active requests per villager/player.
- Added an `Ask about orders` dialogue branch for active Special Orders, with a data-driven root dialogue option, dynamic per-order status options, and eight stock-time response variations.
- Added recruitment-memory biome line filters `recruitment_memory_biome` and `recruitment_memory_biomes` for recruitment follow-up dialogue.
- Added normal dialogue line `priority` and `category` fields. Higher `priority` lines win before weighted random selection; `category` is an author/debug label shown by dialogue explain.
- Added optional skill-trade `request` metadata: `targetable`, `display_priority`, `min_reputation`, `wait_days`, `cooldown_days`, and `extra_cost`.
- Added the forced-dialogue trigger value `trade_refresh` for data-driven trade-refresh option templates.
- Added built-in forced-dialogue entries `trade_refresh.ready_options`, `trade_refresh.available_options`, `trade_refresh.unavailable_options`, `trade_refresh.revered_options`, `trade_refresh.special_order_select_options`, `trade_refresh.special_order_confirm_options`, and `trade_refresh.special_order_status_options`.
- Added dialogue message keys `trade_refresh.ready`, `trade_refresh.accept`, `trade_refresh.already_pending`, `trade_refresh.not_ready`, `trade_refresh.unavailable`, `trade_refresh.revered_prompt`, and `trade_refresh.special_order_*` for trade-refresh and Special Order opening/follow-up lines, including ready restock prompts, queue responses, active-request cap responses, and order-status responses.
- Added a `1.0.0-beta.12` target to the Datapack Generator and built-in website wiki. The beta.11 snapshot remains separate.

### Modified

- The Datapack Generator now exposes beta.12 mood and Social Attribute line filters only when the selected VR version is `1.0.0-beta.12`.
- The generator's Convert flow can move beta.11 packs to beta.12 by updating `pack.mcmeta` with the beta.12 VR version marker. Existing beta.11 JSON fields are not renamed or removed.
- Documentation now distinguishes dialogue `dispositions` from beta.12 temporary `mood` / `moods` filters.
- Normal dialogue line selection now has an explicit priority tier before weighted random selection. Existing packs keep the default `priority: 0`.
- Forced-dialogue option ids should remain unique among simultaneously visible options, but reputation-filtered variants can reuse an id when their filters are mutually exclusive. The built-in trade-refresh option templates use this to provide tier-specific responses for the same visible button.
- Skill-trade replacement selection now avoids giving a refresh result item that already exists in the villager's current offer list.
- Built-in villager skill-trade entries are Special Order targetable by default. Wandering trader skill-trade entries are not targetable.
- Ready trade-refresh follow-ups can now open a forced-dialogue session tied to the speaking villager, and conversation validation allows that forced session target while forced-session rules still pass.

### Deprecated

- No beta.12 pack-facing fields are deprecated.

### Removed

- No beta.12 pack-facing fields, triggers, paths, or placeholders are removed.

### Migration Notes

- Existing beta.11 datapacks do not need JSON changes to load under beta.12.
- Packs that want to use mood or Social Attribute line filters should set the builder target to `1.0.0-beta.12` or add `villagerretaliation.pack_version: "1.0.0-beta.12"` in `pack.mcmeta`.
- Packs that customize skill trades can make better refresh results available by adding eligible higher-rank entries. A villager cannot refresh into a result item they already offer.
- Packs that customize skill trades can add `request.targetable: true` to make an entry directly requestable as a Special Order. The queued request stores the trade definition id, so cost, result, enchantment, skill gates, level gates, and config flags remain tied to the original entry.
- Packs that customize trade-refresh dialogue should override the `trade_refresh.*` dialogue message keys for opening lines and follow-ups, and the `trade_refresh.ready_options`, `trade_refresh.available_options`, `trade_refresh.unavailable_options`, `trade_refresh.revered_options`, `trade_refresh.special_order_select_options`, and `trade_refresh.special_order_confirm_options` forced-dialogue entries for button responses.
- Keep using `dispositions` for reputation-derived tone filters. Use `mood` / `moods` only for temporary event-driven emotional state.
- Use `priority` when one matched line should reliably win over another. Use `weight` to tune random odds inside the same priority tier.
- Use `requires_high_*` when a simple score of 60+ is enough. Use `min_*` and `max_*` score ranges when a line needs exact attribute bands.

## 1.0.0-beta.11 - 2026-05-26

Pack-facing beta.11 changes focus on making data-driven behavior easier to extend without full-file copies.

### Added

- Added [Forced Dialogue JSON](Forced-Dialogue.md) under `data/villagerretaliation/forced_dialogue/` for event-driven locked dialogue moments.
- Added built-in `container_theft` forced dialogue trigger for witnessed chest, barrel, and shulker theft.
- Added built-in `container_opened` forced dialogue trigger for configs that confront players when they open watched containers.
- Added forced-dialogue `output` objects with `mode` and optional `radius`, so the event trigger says what happened and the output says how the line is delivered.
- Added chat output for normal forced-dialogue triggers such as `container_theft`, `container_opened`, `container_broken`, and `retaliation_started` by setting `output.mode` to `chat`.
- Added non-player target support for `retaliation_started` chat output. When the retaliation target is not a player, the line is broadcast to nearby players instead of opening a player-facing conversation.
- Added notification trigger `combat.flee_started` for villagers that keep fleeing a hostile instead of standing ground.
- Added `lines` array support to normal dialogue entries, keyed dialogue messages, conversation openings, conversation closings, pacify lines, and notifications. These entries can still use `text` for a single line.
- Added forced dialogue entry `chance` so event callouts can be occasional instead of firing every time.
- Added forced dialogue witness equipment filters `requires_witness_unarmed` / `witness_unarmed` and `requires_witness_armed` / `witness_armed`.
- Added villager equipment filters `requires_villager_unarmed` / `villager_unarmed` and `requires_villager_armed` / `villager_armed` anywhere a pack rule is evaluated against a villager: dialogue options, lines, messages, openings, closings, pacify lines, notifications, gift preferences, gift rewards, pacification payments, and profession loot rules.
- Added forced dialogue entry fields: `trigger`, `event`, `output`, `line`, `lines`, `priority`, `chance`, `witness_radius`, `witness_profession`, `witness_professions`, `requires_witness_unarmed`, `requires_witness_armed`, `requires_line_of_sight`, `initiate_dialogue`, `aggro_immediately`, `force_camera_towards_villager`, `reputation`, `loot_table`, `loot_tables`, `options`, `leave_option`, and `leave_options`.
- Added forced dialogue option fields: `id`, `label`, `response`, `reputation`, `aggro`, `aggro_chance`, `end_conversation`, `order`, and `take_items`.
- Added shared reputation condition fields `reputation_level`, `reputation_levels`, `min_reputation`, and `max_reputation` to dialogue options, dialogue lines, and forced dialogue options.
- Added normal dialogue option `give_items` hand-ins, with `take_items` and `payment` aliases, so selecting a talk option can remove configured item(s) from the player and store, discard, or drop them.
- Added normal dialogue hand-in placeholders `{given_count}`, `{given_item}`, `{given_item_id}`, `{given_stack}`, `{given_items}`, `{payment_item}`, `{payment_item_id}`, and `{payment_stack}`.
- Added forced dialogue `take_items` support for removing a total `count` of matching item ids or tags from the player's inventory, with separate failure response, reputation, end-conversation, and aggro behavior.
- Added `take_items.destination`, `take_items.overflow_destination`, and `take_items.require_space` so removed items can be discarded, stored in the witnessing villager's inventory, returned to the source container, or dropped at the villager/container.
- Added forced dialogue `take_stolen_items` / `return_stolen_items` support for removing the specific stacks stolen during `container_theft` and moving them into the villager inventory, source container, or another item destination.
- Added forced dialogue placeholders: `{villager}`, `{player}`, `{container}`, `{count}`, `{item}`, `{item_id}`, `{item_count}`, `{item_stack}`, `{items}`, `{loot_table}`, `{payment_count}`, `{payment_items}`, `{stolen_item}`, `{stolen_item_id}`, `{stolen_count}`, `{stolen_item_count}`, `{stolen_stack}`, `{stolen_items}`, `{x}`, `{y}`, and `{z}`.
- Added `player_container_theft` village memory tag, `requires_container_theft_to_self`, `requires_container_theft_from_other`, and theft-memory placeholders `{stolen_item}`, `{stolen_item_id}`, `{stolen_count}`, `{stolen_item_count}`, `{stolen_stack}`, `{stolen_container}`, `{stolen_loot_table}`, `{theft_witness}`, and `{theft_witness_possessive}`.
- Added `baby_villager_attacked` village memory tag for player attacks against baby villagers.
- Added forced dialogue editing, import, preview, validation, starter data, and export support to the [Datapack Generator](Datapack-Generator.md), including line variations, witness professions, custom leave options, `take_items`, `take_stolen_items`, item destinations, and reputation-gated option validation.
- Added `dialogue_option` as the required `options[].type` value and moved the dialogue request into `options[].request` and `lines[].request`.
- Added a VR version selector to the Datapack Generator. Exported beta.11+ packs write `villagerretaliation.pack_version` in `pack.mcmeta`, and import uses it to restore the matching generator target.
- Added reload warnings for common dialogue, forced-dialogue, and notification authoring mistakes: content in the wrong system folder, unsupported fields, wrong trigger families, inert player item slot filters, and unknown profession ids.
- Added more built-in dialogue lines for reputation tiers, retaliation aftermath, apologies, village defense, raids, golem loss, fire, gifts, gear reports, recruitment memories, and container-theft gossip.
- Added built-in profession-group dialogue files for shared multi-profession reactions, while single-profession dialogue lives in the matching `professions/<profession>.json` files.
- Added built-in `retaliation_started` chat-output combat barks for player targets, raiders, undead, monsters, generic retaliation targets, and unarmed villagers.
- Added built-in baby-only alert text for baby villagers being hit and for baby villagers witnessing a villager death.
- Added loot-table-specific built-in forced dialogue scenes for vanilla village profession chests, with profession-specific robbery responses and lower-priority village/general fallbacks.
- Added documentation for resource-pack language keys used by the interaction GUI, generated family and relationship rows, reputation overlays, villager chat labels, gender labels, mood labels, and fallback profession labels.
- Added [Localization Guide](Localization.md) to explain how datapack locale folders and resource-pack language files work together.
- Added namespaced custom profession support for dialogue defaults, dialogue filters, notification filters, gift filters, pacification filters, gift-knowledge keys, and profession display fallbacks.
- Added [Profession Loot JSON](Profession-Loot.md) rule files under `data/villagerretaliation/profession_loot/`.
- Added loot-table-backed profession drops through `loot_table` references. Loot tables can live in any namespace.
- Added top-level `replace` support for dialogue and notification files.
- Added `id`, `remove`, and top-level `replace` support for gift preferences and gift rewards.
- Added additive villager name files under `data/villagerretaliation/villager_names/`, plus top-level `replace` support.

### Modified

- The interaction screen now has a locked forced-dialogue mode for event moments. In this mode, normal root actions such as Talk, Trade, Gift, Inventory, Recruit, Family, and Relationships are hidden until the event option resolves.
- The built-in container forced-dialogue config now defaults to opening generated containers, and the default forced-dialogue pack targets vanilla village chest loot tables for village chest confrontations.
- The built-in village chest forced-dialogue options now vary by reputation: high-reputation players can receive warnings, mid-reputation players can offer normal payment, and low-reputation players can face higher payment costs or harsher outcomes.
- Built-in dialogue tone now emphasizes the mod's memory and consequence loop: villagers react to personal reputation, remember harm, gossip about theft, and treat defense as meaningful without instantly erasing past behavior.
- Built-in dialogue data and wiki examples now use `question` for general Talk menu conversation, with event chat separated into forced-dialogue `output.mode`.
- Built-in dialogue output entries now use `lines` arrays with at least three variants where possible, so default conversations repeat less often.
- Profession-filtered keyed messages, openings, and closings now default to adult-only unless `show_for_babies: true` is supplied, so job-site and profession flavor does not appear on baby villagers by accident.
- Baby villagers can now participate in witnessed-death flee alerts when `retaliation.babyVillagersFleeWitnessedDeaths` is enabled. The built-in data keeps adult and baby alert wording separate with age filters.
- Hitting a baby villager now records both `player_attacked_villager` and `baby_villager_attacked`, and built-in immediate alert/chat wording uses child-specific lines.
- Villager profession and gender labels used by the interaction GUI are now documented as localization-friendly client values instead of server-supplied English display strings.
- Villager dialogue speaker labels are now documented as client-localized GUI text instead of datapack text.
- Built-in profession loot is now declared through datapack rule files and Minecraft loot tables instead of hardcoded Java pools.
- Gift files can replace or remove individual rules by stable `id`; same-id later entries replace earlier rules.
- Villager name files are additive by default instead of requiring packs to copy `preset_names.json` just to append names.
- The Datapack Generator now imports known Villager Retaliation roots using the same strict folder rules as the game. Files under `dialogue/<locale>/`, `notifications/<locale>/`, and `forced_dialogue/` are imported as that system only.
- Datapack Generator zip and folder imports now normalize backslash paths and read zip central-directory entries by directory size for more reliable imports across zip tools and folder pickers.

### Deprecated

- Full-file gift and name overrides still work, but individual ids and additive files are preferred for small changes.

### Removed

- Removed `small_talk` as a distinct dialogue request. Use `question` for general player-selected conversation.
- Removed request values from dialogue `type` fields. Dialogue options must use `type: "dialogue_option"` plus `request`, and dialogue lines must use `request`.
- Removed `_chat` forced-dialogue triggers. Use the normal event trigger with `output.mode: "chat"`.

### Migration Notes

- Datapacks that already translate dialogue and notifications should keep using `data/villagerretaliation/dialogue/<locale>/` and `data/villagerretaliation/notifications/<locale>/`.
- Packs that want to translate interaction buttons, generated relationship/family labels, reputation labels, or profession display names should add a resource pack with `assets/villagerretaliation/lang/<locale>.json`.
- Pack authors can organize single-profession dialogue under `dialogue/<locale>/professions/<profession>.json` or subfolders. Shared multi-profession dialogue can live in any normal dialogue file, such as a `groups/` folder, as long as the entries keep their `professions` filters.
- Existing unnamespaced vanilla profession filters continue to work. New custom-profession filters should use full ids such as `examplemod:alchemist`.
- Packs that copied `gifts/default.json` only to remove or change one rule can now add a smaller file with matching `id` or `"remove": true`.
- Packs that copied `villager_names/preset_names.json` only to add names can now add a separate file under `villager_names/`.
- Packs that want to change profession drops should add or remove `profession_loot` rules and point them at normal Minecraft loot tables.
- Packs that want to change the built-in theft confrontation can add an entry under `forced_dialogue/`, or intentionally override `data/villagerretaliation/forced_dialogue/default.json`.
- Packs that mixed `notifications` or forced-dialogue `entries` into dialogue files should split those sections into `data/villagerretaliation/notifications/<locale>/...json` or `data/villagerretaliation/forced_dialogue/...json`.
- Forced-dialogue theft confrontations should use `trigger: "container_theft"`. Notification-style trigger names such as `alert.player_container_theft` are not forced-dialogue triggers.
- Slot-only player item filters should add an actual selector, such as `player_items`, `player_item_tag`, durability bounds, or enchantment filters.
- Packs that use dialogue option `type` for a request must move that value to `request` and set `type` to `dialogue_option`.
- Packs that use dialogue line `type` for a request must rename it to `request`.
- Packs that use `small_talk` must migrate those entries to `question`.
- Packs that only want an event line in villager chat should keep the normal trigger, such as `container_theft` or `retaliation_started`, and set `output.mode` to `chat`. Add `output.radius` to control the broadcast range.
- Packs with several identical dialogue, message, opening, closing, pacify, or notification entries can collapse them into one entry with `lines`. To preserve the old overall selection odds, set the new entry's `weight` to the sum of the old entry weights.
- Packs that intentionally want profession-filtered openings or keyed messages for baby villagers should now set `show_for_babies: true` explicitly.

## 2026-05 Documentation Baseline

This is the first wiki baseline for pack-format tracking. It reflects the current source and built-in data as of May 2026.

### Added

- Added dedicated reference pages for every current dialogue request, event tag, and built-in notification `trigger`.
- Added expanded examples for current `event_tags` and `player_event_tags` values.
- Added documentation for current family and relationship dialogue filters:
  - `requires_known_family`
  - `requires_known_parent`
  - `requires_known_sibling`
  - `requires_known_spouse`
  - `requires_known_child`
  - `requires_known_grandparent`
  - `requires_known_grandchild`
  - `requires_known_descendant`
  - `requires_known_aunt_uncle`
  - `requires_known_cousin`
  - `requires_known_niece_nephew`
  - `requires_known_extended_family`
  - `requires_known_deceased_family`
  - `requires_known_relationship`
  - `requires_known_current_relationship`
  - `requires_known_past_relationship`
  - `requires_known_crush`
  - `requires_known_dating_partner`
  - `requires_known_fiance`
  - `requires_known_romantic_spouse`
  - `requires_known_separated_partner`
  - `requires_known_widowed_partner`
- Added documentation for family and relationship placeholders used by matching dialogue lines.
- Added documentation for `player_item_tags` as an accepted player item filter alias.

### Modified

- Event-tag examples now use `Expanded:` instead of `Complex:` so deeper examples read as implementation detail, not difficulty.
- Pack authors now have per-value reference catalogs instead of long example lists on the main system pages.

### Deprecated

- No pack-facing fields, tags, triggers, or paths are marked deprecated in this baseline.
- Legacy pacification placeholders `{emerald_cost}` and `{emeralds}` are still supported aliases, but new packs should prefer `{payment_cost}`, `{payment_item}`, and `{payment_items}`.

### Removed

- Nothing recorded in this baseline.

### Migration Notes

- Existing packs do not need changes for this documentation baseline.
- If a pack uses pacification text, prefer the newer payment placeholder names so the same text works cleanly with non-emerald payment items.
- If a pack uses event tags, check [Event Tags](Event-Tags.md) for which accepted tags are currently emitted by built-in handlers. `golem_created` and `nearby_hostile_mob` are accepted by the parser but are not currently written by built-in event code.

## Entry Template

Use this structure for future version sections.

````markdown
## Version X.Y.Z - YYYY-MM-DD

Short summary of pack-facing changes in this release.

### Added

- New field/tag/trigger/path: `example`.

### Modified

- Changed `old_behavior` so it now does `new_behavior`.

### Deprecated

- Deprecated `old_field`. Use `new_field` instead.

### Removed

- Removed `removed_field`.

### Migration Notes

- Replace:

```json
{
  "old_field": "value"
}
```

with:

```json
{
  "new_field": "value"
}
```
````
