# Pack Format Changes

This page is the migration note for pack authors, not the player-facing changelog.

## Current Target

The current repo wiki targets `1.0.0-beta.12`.

If you are still maintaining a beta.11 pack, keep using the beta.11 snapshot in `tools/datapack-builder/wiki/1.0.0-beta.11/` until you are ready to retarget manually.

## Beta.11 To Beta.12 Checklist

Beta.12 is not a marker-only update. Review these areas before changing pack target:

1. Dialogue layout: beta.12 strongly prefers folderized dialogue such as `options/`, `lines/`, `messages/`, `openings/`, `closings/`, and `pacify/`.
2. Dialogue requests: options use `request`, and typed option files can omit `type` entirely.
3. Complex logic: newer content should prefer `conditions` over older one-off helper fields.
4. Dialogue filtering: beta.12 adds temporary mood filters, Social Attribute score filters, `priority`, `category`, and `text_key`.
5. Quests: quest module v2 is preferred for new quests. Legacy v1 quest JSON remains supported through the compatibility adapter.
6. Skill trades: beta.12 adds trade refresh behavior, persistent trade pools, and targetable Special Orders.
7. Builder structures: eligible hired-builder structures are now data driven through `data/<namespace>/builder_structures/`.
8. Builder workflow: there is no automatic beta.11 to beta.12 conversion pass.

## Most Important Authoring Differences

### 1. Dialogue Is Easier To Split

Old style:

```text
data/my_pack/dialogue/en_us/global.json
```

Preferred beta.12 style:

```text
data/my_pack/dialogue/en_us/global/options/00_rumor.json
data/my_pack/dialogue/en_us/global/lines/00_rumor.json
data/my_pack/dialogue/en_us/global/messages/00_shared_text.json
```

### 2. `conditions` Are The Long-Term Shape

Instead of stacking many special-purpose booleans, move new work toward:

```json
{
  "id": "my_pack.line.family_storm",
  "request": "question",
  "conditions": [
    { "type": "family", "relation": "child" },
    { "type": "weather", "state": "thunder" }
  ],
  "text": "Storm nights are worse when you have children to worry about."
}
```

### 3. Quests Prefer Central Modules

Preferred quest module v2 shape:

```json
{
  "schema": "villagerretaliation:quest/v2",
  "id": "my_pack:bread_delivery",
  "metadata": {
    "title": "Bread Delivery",
    "description": "Bring 16 bread to the village stores."
  },
  "provider": {
    "type": "villagerretaliation:villager",
    "filters": {
      "professions": ["minecraft:farmer"]
    }
  },
  "entry_stage": "gather",
  "stages": [
    {
      "id": "gather",
      "objectives": [
        {
          "id": "bring_bread",
          "type": "item_check",
          "item": "minecraft:bread",
          "count": 16
        }
      ]
    }
  ]
}
```

V1 quest files without `schema: "villagerretaliation:quest/v2"` still load. New simple quests should start as one v2 file with inline dialogue, and only extract dialogue trees or forced dialogue when the scene is large or event-driven.

### 4. Skill Trades Can Power Special Orders

Entries can now expose direct requests:

```json
"request": {
  "targetable": true,
  "display_priority": 20,
  "min_reputation": "respected",
  "wait_days": 2,
  "cooldown_days": 3
}
```

### 5. Builder Structures Can Include Modded Templates

Add builder-menu structures through normal datapack files:

```json
{
  "entries": [
    {
      "structure": "examplemod:village/houses/carpenter_house",
      "category": "Modded Village",
      "label": "Carpenter House",
      "base_cost": 18
    }
  ]
}
```

See [Builder Structures](Builder-Structures.md) for remove and replace examples.

## Safe Migration Plan

1. Leave the pack on beta.11 while you review it.
2. Move dialogue into folderized beta.12 paths if the current files are large.
3. Leave working v1 quests in place unless you are intentionally migrating them.
4. Convert new or migrated simple quests to quest module v2 first; extract dialogue trees only when needed.
5. Replace older helper-heavy logic with `conditions` where practical.
6. Test each system separately.
7. Only then change the pack target to beta.12.

## What Did Not Change

These habits are still correct:

- Use stable `id` values.
- Use exact path overrides only when you really want to replace built-in content.
- Keep notifications, dialogue, and forced dialogue in their own loaders.
- Keep v1 quest files and dialogue trees when they are still the authoritative source.
- Use a resource pack for GUI text and models.

## When In Doubt

Use the beta.12 example pack and builder template as the source of truth for new content. They are easier to trust than trying to "incrementally guess" a beta.11 file into the new surface.
