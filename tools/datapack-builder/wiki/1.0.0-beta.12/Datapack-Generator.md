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

The current rewrite in this repo targets `VR 1.0.0-beta.12`.

- Choose `1.0.0-beta.12` for new work against this wiki.
- Keep using the `1.0.0-beta.11` snapshot for older packs that have not been manually migrated.
- The builder does not convert beta.11 packs to beta.12 for you.

## What It Writes

| Builder area | Output root |
| --- | --- |
| Dialogue | `data/<namespace>/dialogue/<locale>/` |
| Forced dialogue | `data/<namespace>/forced_dialogue/` |
| Imported quests | `data/<namespace>/quests/` |
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

That template already includes examples for dialogue, forced dialogue, notifications, gifts, pacification, profession loot, story discovery, and names.

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

## Import Notes

Import works best when your pack already follows the documented folder layout.

- Files under `dialogue/<locale>/` import as dialogue.
- Files under `forced_dialogue/` import as forced dialogue.
- Files under `quests/` and `dialogue_trees/<locale>/` are recognized and preserved as JSON pass-through files.
- Files under `notifications/<locale>/` import as notifications.

If an older handwritten pack mixed several systems into one file, split those files first. The game itself also treats those paths as separate loaders.

Quest and dialogue-tree pass-through means the builder keeps those files in the pack and export zip. It does not yet provide full structured editors for quest objectives, quest rules, or branching tree nodes.

## Good Safety Checks

Before exporting, confirm:

- The namespace is correct.
- The locale is correct.
- Tags start with `#`.
- Structure and biome ids are fully namespaced.
- Stable `id` values are present on entries you may translate or override later.

The builder is a convenience layer. It does not register new items, professions, structures, or biomes on its own.
