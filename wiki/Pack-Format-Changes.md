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
- [Dialogue Types](Dialogue-Types.md)
- [Event Tags](Event-Tags.md)
- [Notifications JSON](Notifications.md)
- [Notification Triggers](Notification-Triggers.md)
- [Localization Guide](Localization.md)
- [Gift JSON](Gifts.md)
- [Pacification JSON](Pacification.md)
- [Profession Loot JSON](Profession-Loot.md)
- [Story Discovery JSON](Story-Discovery.md)
- [Resource Pack Models And Textures](Resource-Pack-Models.md)

## 1.0.0-beta.11 - Unreleased

Pack-facing beta.11 changes focus on making data-driven behavior easier to extend without full-file copies.

### Added

- Added [Forced Dialogue JSON](Forced-Dialogue.md) under `data/villagerretaliation/forced_dialogue/` for event-driven locked dialogue moments.
- Added built-in `container_theft` forced dialogue trigger for witnessed chest, barrel, and shulker theft.
- Added built-in `container_opened` forced dialogue trigger for configs that confront players when they open watched containers.
- Added chat-only forced-dialogue triggers `container_theft_chat`, `container_opened_chat`, and `retaliation_started_chat` for villager-styled event lines that do not open the locked interaction screen.
- Added non-player target support for `retaliation_started_chat`. When the retaliation target is not a player, the line is broadcast to nearby players instead of opening a player-facing conversation.
- Added notification trigger `combat.flee_started` for villagers that keep fleeing a hostile instead of standing ground.
- Added forced dialogue entry `chance` for `_chat` triggers so event callouts can be occasional instead of firing every time.
- Added forced dialogue witness equipment filters `requires_witness_unarmed` / `witness_unarmed` and `requires_witness_armed` / `witness_armed`.
- Added villager equipment filters `requires_villager_unarmed` / `villager_unarmed` and `requires_villager_armed` / `villager_armed` anywhere a pack rule is evaluated against a villager: dialogue options, lines, messages, openings, closings, pacify lines, notifications, gift preferences, gift rewards, pacification payments, and profession loot rules.
- Added forced dialogue entry fields: `trigger`, `event`, `line`, `lines`, `priority`, `chance`, `witness_radius`, `witness_profession`, `witness_professions`, `requires_witness_unarmed`, `requires_witness_armed`, `requires_line_of_sight`, `initiate_dialogue`, `aggro_immediately`, `force_camera_towards_villager`, `reputation`, `loot_table`, `loot_tables`, `options`, `leave_option`, and `leave_options`.
- Added forced dialogue option fields: `id`, `label`, `response`, `reputation`, `aggro`, `aggro_chance`, `end_conversation`, `order`, and `take_items`.
- Added shared reputation condition fields `reputation_level`, `reputation_levels`, `min_reputation`, and `max_reputation` to dialogue options, dialogue lines, and forced dialogue options.
- Added forced dialogue `take_items` support for removing a total `count` of matching item ids or tags from the player's inventory, with separate failure response, reputation, end-conversation, and aggro behavior.
- Added `take_items.destination`, `take_items.overflow_destination`, and `take_items.require_space` so removed items can be discarded, stored in the witnessing villager's inventory, returned to the source container, or dropped at the villager/container.
- Added forced dialogue `take_stolen_items` / `return_stolen_items` support for removing the specific stacks stolen during `container_theft` and moving them into the villager inventory, source container, or another item destination.
- Added forced dialogue placeholders: `{villager}`, `{player}`, `{container}`, `{count}`, `{item}`, `{item_id}`, `{item_count}`, `{item_stack}`, `{items}`, `{loot_table}`, `{payment_count}`, `{payment_items}`, `{stolen_item}`, `{stolen_item_id}`, `{stolen_count}`, `{stolen_item_count}`, `{stolen_stack}`, `{stolen_items}`, `{x}`, `{y}`, and `{z}`.
- Added `player_container_theft` village memory tag, `requires_container_theft_to_self`, `requires_container_theft_from_other`, and theft-memory placeholders `{stolen_item}`, `{stolen_item_id}`, `{stolen_count}`, `{stolen_item_count}`, `{stolen_stack}`, `{stolen_container}`, `{stolen_loot_table}`, `{theft_witness}`, and `{theft_witness_possessive}`.
- Added forced dialogue editing, import, preview, validation, starter data, and export support to the [Datapack Generator](Datapack-Generator.md), including line variations, witness professions, custom leave options, `take_items`, `take_stolen_items`, item destinations, and reputation-gated option validation.
- Added `small_talk` as the required general conversation dialogue request type.
- Added a VR version selector to the Datapack Generator. Exported beta.11+ packs write `villagerretaliation.pack_version` in `pack.mcmeta`, and import uses it to restore the matching generator target.
- Added more built-in dialogue lines for reputation tiers, retaliation aftermath, apologies, village defense, raids, golem loss, fire, gifts, gear reports, recruitment memories, and container-theft gossip.
- Added built-in `retaliation_started_chat` combat barks for player targets, raiders, undead, monsters, generic retaliation targets, and unarmed villagers.
- Added loot-table-specific built-in forced dialogue scenes for vanilla village profession chests, with profession-specific robbery responses and lower-priority village/general fallbacks.
- Added documentation for resource-pack language keys used by the interaction GUI, generated family and relationship rows, reputation overlays, villager chat labels, gender labels, mood labels, and fallback profession labels.
- Added [Localization Guide](Localization.md) to explain how datapack locale folders and resource-pack language files work together.
- Added namespaced custom profession support for dialogue defaults, dialogue filters, notification filters, gift filters, pacification filters, gift-knowledge keys, and profession display fallbacks.
- Added [Profession Loot JSON](Profession-Loot.md) rule files under `data/villagerretaliation/profession_loot/`.
- Added loot-table-backed profession drops through `loot_table` references. Loot tables can live in any namespace.
- Added `id`, `remove`, and top-level `replace` support for gift preferences and gift rewards.
- Added additive villager name files under `data/villagerretaliation/villager_names/`, plus top-level `replace` support.

### Modified

- The interaction screen now has a locked forced-dialogue mode for event moments. In this mode, normal root actions such as Talk, Trade, Gift, Inventory, Recruit, Family, and Relationships are hidden until the event option resolves.
- The built-in container forced-dialogue config now defaults to opening generated containers, and the default forced-dialogue pack targets vanilla village chest loot tables for village chest confrontations.
- The built-in village chest forced-dialogue options now vary by reputation: high-reputation players can receive warnings, mid-reputation players can offer normal payment, and low-reputation players can face higher payment costs or harsher outcomes.
- Built-in dialogue tone now emphasizes the mod's memory and consequence loop: villagers react to personal reputation, remember harm, gossip about theft, and treat defense as meaningful without instantly erasing past behavior.
- Built-in dialogue data and wiki examples now use `small_talk` instead of `chat` for normal Talk menu conversation, keeping that request type distinct from villager-styled event chat lines.
- Villager profession and gender labels used by the interaction GUI are now documented as localization-friendly client values instead of server-supplied English display strings.
- Villager dialogue speaker labels are now documented as client-localized GUI text instead of datapack text.
- Built-in profession loot is now declared through datapack rule files and Minecraft loot tables instead of hardcoded Java pools.
- Gift files can replace or remove individual rules by stable `id`; same-id later entries replace earlier rules.
- Villager name files are additive by default instead of requiring packs to copy `preset_names.json` just to append names.

### Deprecated

- Full-file gift and name overrides still work, but individual ids and additive files are preferred for small changes.

### Removed

- Removed support for the legacy dialogue request type value `chat`. Use `small_talk` instead.

### Migration Notes

- Datapacks that already translate dialogue and notifications should keep using `data/villagerretaliation/dialogue/<locale>/` and `data/villagerretaliation/notifications/<locale>/`.
- Packs that want to translate interaction buttons, generated relationship/family labels, reputation labels, or profession display names should add a resource pack with `assets/villagerretaliation/lang/<locale>.json`.
- Existing unnamespaced vanilla profession filters continue to work. New custom-profession filters should use full ids such as `examplemod:alchemist`.
- Packs that copied `gifts/default.json` only to remove or change one rule can now add a smaller file with matching `id` or `"remove": true`.
- Packs that copied `villager_names/preset_names.json` only to add names can now add a separate file under `villager_names/`.
- Packs that want to change profession drops should add or remove `profession_loot` rules and point them at normal Minecraft loot tables.
- Packs that want to change the built-in theft confrontation can add an entry under `forced_dialogue/`, or intentionally override `data/villagerretaliation/forced_dialogue/default.json`.
- Packs that use dialogue `type: "chat"` must migrate to `type: "small_talk"` before targeting beta.11+. This applies to both dialogue `options` and `lines`.
- Packs that only want an event line in villager chat should use the `_chat` forced-dialogue triggers. Keep `container_theft`, `container_opened`, and `retaliation_started` for locked forced-dialogue scenes or outcomes.

## 2026-05 Documentation Baseline

This is the first wiki baseline for pack-format tracking. It reflects the current source and built-in data as of May 2026.

### Added

- Added dedicated reference pages for every current dialogue `type`, event tag, and built-in notification `trigger`.
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
