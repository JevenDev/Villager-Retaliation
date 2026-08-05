# Datapack Generator

Villager Retaliation ships a local browser-based datapack builder at:

```text
tools/datapack-builder/index.html
```

It is a static page. It does not need a server and it exports a normal datapack zip.

## What It Is Good For

Use the generator when you want to:

- create a starter pack quickly
- import an existing pack and inspect its structure
- preview exact output paths before exporting
- validate common JSON mistakes without hand-editing every file

## Current Target

The generator keeps structured authoring targets for `1.0.0-beta.11` and `1.0.0-beta.12`. Its quest editor also understands the current quest module v2 surface used by beta.13.

- Use this live developer wiki as the source of truth for hand-authored `1.0.0-beta.13` packs.
- For Minecraft 1.21.1, set `pack_format` to `48` before export.
- Choose `1.0.0-beta.12` in the generator for the folderized dialogue, notification, gift, pacification, story, name, loot, and skill-trade surfaces it currently writes.
- Keep using the `1.0.0-beta.11` snapshot for older packs that have not been manually migrated.
- The builder does not convert beta.11 packs to beta.12 for you.
- For beta.13 persistent scenes and encounter orchestration, start from the repository example packs and [Persistent Quest Scenes](Quest-Scenes.md).

## What It Writes

| Builder area | Output root |
| --- | --- |
| Quests | `data/<namespace>/quests/` |
| Skill Trades | `data/<namespace>/skill_trades/` |
| Dialogue | `data/<namespace>/dialogue/<locale>/` |
| Forced dialogue | `data/<namespace>/forced_dialogue/` |
| Imported dialogue trees | `data/<namespace>/dialogue_trees/<locale>/` |
| Notifications | `data/villagerretaliation/notifications/<locale>/` |
| Gifts | `data/villagerretaliation/gifts/` |
| Pacification | `data/villagerretaliation/pacification/` |
| Story structures | `data/<namespace>/story_structures/` |
| Story biomes | `data/<namespace>/story_biomes/` |
| Names | `data/villagerretaliation/villager_names/` |

## Fast Workflow

1. Open `tools/datapack-builder/index.html`.
2. Set pack name, namespace, locale, and output file slug.
3. Add one system at a time.
4. Watch the preview panel to confirm path and JSON.
5. Export the zip.
6. Put it in the world's `datapacks` folder and run `/reload`.

## Best Starting Preset

The `Preset` button is the fastest way to start:

- `Starter Pack` gives you a small editable beta.12 pack.
- `Dialogue Folder Template` gives you the full folderized template from `example-packs/dialogue-folder-template/`.

That template already includes examples for quest module v2, dialogue, forced dialogue, notifications, gifts, pacification, profession loot, story discovery, and names.

## Example Use

If you want one new dialogue option:

1. Open the Dialogue tab.
2. Pick `Typed folders`.
3. Create an option with `request: story`.
4. Create a matching line pointing back to that option id.
5. Export.

You should end up with output similar to:

```text
data/my_pack/dialogue/en_us/my_pack/options/00_rumor.json
data/my_pack/dialogue/en_us/my_pack/lines/00_rumor.json
```

If you want one simple quest:

1. Open the Quests tab.
2. Click `Add Example`.
3. Edit the quest id, provider filters, objective, dialogue, rewards, and tracker text in the JSON editor.
4. Keep `Scene mode` on `Inline scenes` unless the scene should live in a separate dialogue tree.
5. Export.

You should end up with output similar to:

```text
data/my_pack/quests/first_steps.json
```

## Import Notes

Import works best when your pack already follows the documented folder layout.

- Files under `dialogue/<locale>/` import as dialogue.
- Files under `forced_dialogue/` import as forced dialogue.
- Quest module v2 files under `quests/` import as editable Quests tab modules.
- Skill-trade files under `skill_trades/` import as editable Skill Trades entries and retain their namespace and nested source path.
- Legacy v1 quest files under `quests/` are preserved as JSON pass-through files with migration suggestions.
- Files under `dialogue_trees/<locale>/` are recognized and preserved as JSON pass-through files.
- Files under `notifications/<locale>/` import as notifications.

If an older handwritten pack mixed several systems into one file, split those files first. The game itself also treats those paths as separate loaders.

Legacy quest and dialogue-tree pass-through means the builder keeps those files in the pack and export zip without overwriting them. Use the migration suggestions as a prompt to run the v1-to-v2 tooling separately.

## Good Safety Checks

Before exporting, confirm:

- The namespace is correct.
- The locale is correct.
- Tags start with `#`.
- Structure and biome ids are fully namespaced.
- Stable `id` values are present on entries you may translate or override later.
- Skill-trade ids, skills, professions, items, rank bounds, and Special Order request fields pass inline validation.
- Quest modules use `schema: "villagerretaliation:quest/v2"`.
- Response transitions use only one transition source.

The builder is a convenience layer. It does not register new items, professions, structures, or biomes on its own.
