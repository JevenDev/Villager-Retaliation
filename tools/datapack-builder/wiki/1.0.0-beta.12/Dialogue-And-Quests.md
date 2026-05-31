# Dialogue And Quests

Dialogue and quests use a module-first authoring convention. A module is one quest or story beat whose files share the same relative path across the quest, dialogue-tree, ambient-dialogue, and forced-dialogue roots.

## Recommended Layout

Use this layout for new quest content:

```text
data/<namespace>/quests/<questline>/<quest>.json
data/<namespace>/dialogue_trees/<locale>/quests/<questline>/<quest>.json
data/<namespace>/dialogue/<locale>/quests/<questline>/<quest>.json
data/<namespace>/forced_dialogue/quests/<questline>/<quest>.json
```

Only create the files the module actually needs. A simple quest with one branching scene usually needs the quest file and the dialogue tree. Add ambient dialogue only when the quest contributes reusable talk-menu options, keyed messages, openings, closings, or request lines. Add forced dialogue only when a quest trigger, theft event, retaliation event, or trade-refresh flow needs a locked scene.

## What The Path Does

Quest files still use normal quest JSON. If `id` is omitted, the quest id falls back to the file path under `quests/`.

Dialogue tree files under `dialogue_trees/<locale>/quests/` are treated as quest-scoped trees. If the tree has no `id`, the loader drops the leading `quests/` path segment so this tree:

```text
data/example/dialogue_trees/en_us/quests/lost_civilization/tales.json
```

falls back to:

```text
example:lost_civilization/tales
```

When a quest-scoped tree has an explicit `id`, that id is also the default quest id for local quest conditions and quest actions. This means local branches can write:

```json
{ "type": "quest", "state": "available" }
```

and:

```json
{ "action": "start" }
```

instead of repeating the same `quest` field in every condition and action.

Quest files also supply their own id as the default quest id inside objectives, active rules, expiration rules, and triggers.

## What Not To Duplicate

Do not repeat quest offer gates in the dialogue tree. `type: "quest", state: "available"` already checks the quest offer rules, including profession, villager level, skill requirements, repeat limits, cooldowns, and completion limits.

For example, prefer:

```json
{
  "id": "offer",
  "label": "Lost Civilization",
  "conditions": [
    { "type": "quest", "state": "available" }
  ],
  "show_for_babies": false,
  "start": "offer"
}
```

over repeating the same `offer.professions`, `offer.min_villager_level`, and `offer.skills` gates in both quest JSON and dialogue tree JSON.

## Links And Metadata

`links` and root `metadata` are optional authoring aids. They are useful for documentation, validation, and tools, but they are not required runtime wiring.

Runtime behavior comes from:

- quest definitions under `quests/`
- dialogue tree entries with quest conditions
- dialogue tree node or response actions
- quest trigger actions
- forced-dialogue entries referenced by actions

Use `links` when you want a quest file to document the dialogue surfaces it owns. Omit it when the mirrored module path is clearer.

## Module Dialogue Files

Ambient dialogue loading is recursive, so a module can keep small local pools beside the quest:

```text
data/example/dialogue/en_us/quests/lost_civilization/tales.json
```

That file can bundle sections when they are easier to maintain together:

```json
{
  "options": [
    {
      "id": "lost_civilization.ask_ruins",
      "label": "Ask about the old city",
      "request": "story"
    }
  ],
  "lines": [
    {
      "id": "lost_civilization.ruins_hint",
      "option": "lost_civilization.ask_ruins",
      "request": "story",
      "text": "There are maps that remember roads no one walks anymore."
    }
  ]
}
```

Split a module into typed folders when it grows:

```text
data/example/dialogue/en_us/quests/lost_civilization/tales/options/00_options.json
data/example/dialogue/en_us/quests/lost_civilization/tales/lines/00_lines.json
```

## Migration Notes

The old separate folders still load. Existing packs do not need to move immediately.

For new content, use mirrored module paths and omit repeated local quest ids. For old content, migrate one module at a time:

1. Move the quest under `quests/<questline>/`.
2. Move its tree under `dialogue_trees/<locale>/quests/<questline>/`.
3. Move any quest-triggered forced scenes under `forced_dialogue/quests/<questline>/`.
4. Remove duplicate quest conditions, duplicate quest action ids, and optional `links` that only restate the path.
5. Run `node tools/validate-dialogue-data.mjs`.
