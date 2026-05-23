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
- [Dialogue Types](Dialogue-Types.md)
- [Event Tags](Event-Tags.md)
- [Notifications JSON](Notifications.md)
- [Notification Triggers](Notification-Triggers.md)
- [Localization Guide](Localization.md)
- [Gift JSON](Gifts.md)
- [Pacification JSON](Pacification.md)
- [Story Discovery JSON](Story-Discovery.md)
- [Resource Pack Models And Textures](Resource-Pack-Models.md)

## Unreleased

This section collects pack-facing changes for the next release. Once that release ships, these notes can move into a dated version section.

### Added

- Added documentation for resource-pack language keys used by the interaction GUI, generated family and relationship rows, reputation overlays, villager chat labels, gender labels, mood labels, and fallback profession labels.
- Added [Localization Guide](Localization.md) to explain how datapack locale folders and resource-pack language files work together.

### Modified

- Villager profession and gender labels used by the interaction GUI are now documented as localization-friendly client values instead of server-supplied English display strings.
- Villager dialogue speaker labels are now documented as client-localized GUI text instead of datapack text.

### Deprecated

- Nothing recorded yet.

### Removed

- Nothing recorded yet.

### Migration Notes

- Datapacks that already translate dialogue and notifications should keep using `data/villagerretaliation/dialogue/<locale>/` and `data/villagerretaliation/notifications/<locale>/`.
- Packs that want to translate interaction buttons, generated relationship/family labels, reputation labels, or profession display names should add a resource pack with `assets/villagerretaliation/lang/<locale>.json`.

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
