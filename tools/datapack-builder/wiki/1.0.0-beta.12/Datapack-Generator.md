# Datapack Generator

Villager Retaliation includes a local browser-based datapack generator at:

```text
tools/datapack-builder/index.html
```

Open that file in a modern browser to create, import, preview, validate, and export Villager Retaliation datapacks without writing every JSON file by hand.

The generator is a static page. It does not need a server, does not upload pack data anywhere, and exports a normal datapack zip with `pack.mcmeta` at the root.

Use the VR version selector in Pack Setup when creating a pack. Exported beta.11 and newer packs include `villagerretaliation.pack_version` in `pack.mcmeta`, so importing them later restores the matching generator target automatically. Older or hand-written packs may not have that marker; select the intended VR version manually before continuing.

Choose `VR 1.0.0-beta.12` when using beta.12 Social Attribute or temporary mood dialogue filters. Choose `VR 1.0.0-beta.11` when maintaining a beta.11-compatible pack; the built-in Wiki button keeps a separate beta.11 snapshot so beta.11 authors do not see beta.12-only fields as current for their target.

## What It Builds

The generator writes these datapack paths:

| Tab | Generated path |
| --- | --- |
| Overview | `pack.mcmeta` |
| Dialogue | `data/villagerretaliation/dialogue/<locale>/<file>.json` |
| Forced | `data/villagerretaliation/forced_dialogue/<file>.json` |
| Notifications | `data/villagerretaliation/notifications/<locale>/<file>.json` |
| Gifts | `data/villagerretaliation/gifts/<file>.json` |
| Pacification | `data/villagerretaliation/pacification/<file>.json` |
| Stories - Structures | `data/<namespace>/story_structures/<file>.json` |
| Stories - Biomes | `data/<namespace>/story_biomes/<file>.json` |
| Names | `data/villagerretaliation/villager_names/preset_names.json` |

Forced dialogue entries use the Forced tab. Use it for event-driven conversations such as witnessed container opening, breaking, or theft, for nearby player item callouts through `player_item_proximity`, and for chat event lines through `output.mode: "chat"` on triggers such as `retaliation_started`. It supports line variations, witness profession filters, line-of-sight checks, generated-container loot-table targeting, player item and slot filters, chat-event chance, forced camera focus, immediate aggro, dialogue options, custom Leave/Escape outcomes, reputation changes, stolen-item returns, item payments, and aggro or aggro chance after specific responses. See [Forced Dialogue JSON](Forced-Dialogue.md) for the raw schema.

The Forced tab changes its fields based on `output.mode`. `chat` entries show chat delivery fields such as output radius and hide locked-conversation options, while `forced_dialogue` entries show dialogue options, Leave/Escape outcomes, reputation changes, aggro controls, and camera controls.

The Dialogue tab supports reputation-gated options and lines through `reputation_levels`, `min_reputation`, and `max_reputation`. It also exposes armed and unarmed villager filters for options, lines, messages, openings, closings, and pacify lines. When the target is `VR 1.0.0-beta.12`, dialogue lines also expose temporary mood filters and Social Attribute score filters. Dialogue lines, keyed messages, openings, closings, pacify lines, forced openings, and notifications accept one variation per line in their visual text fields; the builder writes `text` or `line` for one non-empty value and `lines` for multiple values. Forced dialogue options use the same reputation fields inside the Options JSON editor, which lets one event show a warning to trusted players, a normal payment to neutral players, and a harsher response to low-reputation players.

The Forced, Notifications, Gifts, and Pacification tabs expose armed/unarmed villager filters where the game has a specific villager to evaluate. Forced dialogue uses witness filters; the other tabs use villager filters.

The generator is meant for datapacks. Use a resource pack separately for GUI language keys, villager textures, and model JSON.

## Quick Start

1. Open `tools/datapack-builder/index.html` in a browser.
2. In Overview, set the pack name, `pack_format`, default namespace, file slug, locale, and description.
3. Use the tabs to add dialogue, forced dialogue events, notifications, gift preferences, high-reputation rewards, pacification payments, story targets, or preset villager names.
4. Watch the preview panel to confirm the generated file path and JSON.
5. Use the validation panel to catch missing selectors, bad ids, and common path mistakes.
6. Click Export to download the datapack zip.
7. Put the zip in the world's `datapacks` folder, then run `/reload`.

For a fast starting point, use the generator's starter pack action, then edit or delete entries that do not fit your pack.

## Importing Existing Work

Use Import to load an existing datapack zip or one or more JSON files. Use Import Folder to load an unpacked datapack directory when your browser supports directory selection.

The generator recognizes Villager Retaliation dialogue, forced dialogue, notifications, gifts, pacification, story discovery, preset names, and `pack.mcmeta`. Unknown files are preserved as extra files when possible, so imported datapacks can usually be exported again without losing unrelated datapack content.

For files under known Villager Retaliation roots, import follows the same folder rules as the game. A file in `dialogue/<locale>/` is imported only as dialogue, a file in `notifications/<locale>/` is imported only as notifications, and a file in `forced_dialogue/` is imported only as forced dialogue. If an old hand-written pack mixed notification or forced-dialogue sections into a dialogue file, move those sections to the documented folders before importing or exporting.

When importing dialogue, the generator can merge files from normal locale folders and profession subfolders. Check the preview paths after import, especially if the original pack used several files for the same system.

## Working With The Preview

The right-side preview shows the file that will be written for the active tab. Use it to check:

- The file is under the expected namespace.
- The locale folder is correct, such as `en_us` or `fr_fr`.
- Custom ids are stable and namespaced to your pack.
- Story structures and biomes use full ids like `minecraft:ancient_city`.
- Item tags include `#`, such as `#minecraft:arrows`.
- Reputation filters use known tiers such as `trusted`, `neutral`, or `hostile`, unless you intentionally use numeric `min_reputation` / `max_reputation`.

The single-file download button saves only the currently previewed JSON file. Export saves the full datapack zip.

## Testing In Game

After exporting:

1. Copy the zip into `<world>/datapacks/`.
2. Run `/reload`.
3. Trigger the feature you changed.
4. If nothing changes, check the latest log for JSON warnings and confirm the generated path matches the wiki page for that system.

The generator helps with structure and syntax, but it cannot register new items, professions, structures, or biomes. Those ids must already exist through Minecraft, another datapack, or a mod.
