# Villager Retaliation Datapack Builder

Open [Villager Retaliation Datapack Generator](https://jevendev.github.io/Villager-Retaliation/) to create, import, preview, and export Villager Retaliation datapacks.

The builder is a static site. It emits the selected Villager Retaliation pack version's wiki paths for dialogue, forced dialogue events, chat event lines, notifications, gifts, pacification payments, story discovery, preset names, and `pack.mcmeta`, then exports them as a datapack zip with root-level `pack.mcmeta`.

The Wiki button opens a versioned, built-in wiki snapshot for the selected Villager Retaliation pack version. Press `Alt+Q` to open or close it. The wiki window can be dragged, resized, closed, and reopened at its previous position. Beta.11 and beta.12 snapshots are kept separate so beta.11 authors do not see beta.12-only mood and Social Attribute fields as current for their target.

When editing files under `tools/datapack-builder/wiki/`, regenerate the bundled snapshot with:

```bash
node tools/datapack-builder/build-wiki-snapshot.mjs
```

Use `node tools/datapack-builder/build-wiki-snapshot.mjs --check` in validation scripts to fail if the markdown and bundled wiki have drifted.

Exports from beta.11 onward include a `villagerretaliation.pack_version` marker in `pack.mcmeta`. Import uses that marker to restore the target VR version automatically. If an older or hand-written pack does not include the marker, choose the intended VR version in Pack Setup before editing or exporting.

The builder does not offer beta.11 to beta.12 migration support. Beta.12 is a manual retargeting release with a new folderized dialogue authoring layout, so changing only the `pack.mcmeta` marker is not treated as a valid migration. Keep beta.11 packs on the beta.11 target until they have been manually reviewed against the beta.12 wiki.

The Preset button opens a template picker. `Starter Pack` loads a small editable beta.12 example, and `Dialogue Folder Template` loads the beta.12 folderized skeleton that mirrors `example-packs/dialogue-folder-template/` with one `example` option and line for every dialogue request.

Import follows the same strict folder rules as the game for known Villager Retaliation roots. Dialogue files stay dialogue, notification files stay notifications, and forced-dialogue files stay forced dialogue; mixed old packs should be split into the documented folders before export.

The builder writes beta.12 dialogue through the Dialogue tab's Layout selector. `Typed folders` creates section paths such as `<folder>/options/00_*.json` and `<folder>/lines/00_*.json`, while `Single bundle file` keeps all dialogue sections in one JSON file. The builder also imports beta.12 typed `options/` files where `type` is omitted and infers custom profession defaults from paths such as `professions/examplemod/alchemist/lines/...json`. It writes event chat through `output.mode: "chat"` on normal forced-dialogue triggers. It supports watched-container events, `retaliation_started` chat lines, and `player_item_proximity` item callouts. Entry forms adapt to the selected VR version: beta.11 hides beta.12-only fields, while beta.12 shows option and line `conditions`, line `mood` / `moods`, `min_mood_intensity`, `requires_high_*`, exact Social Attribute score ranges, priority/category selection metadata, narrative metadata for future quest organization, and `text_key` message indirection. The beta.12 wiki snapshot also documents the folderized dialogue layout and the data-driven `trade_refresh` forced-dialogue option templates used by villager trade refresh requests.

Imported dialogue fields that still work in beta.12 but are planned for beta.13 deprecation are preserved and shown as blue `Marked for deprecation` notices. The structured forms show the replacement path, usually `conditions`, instead of making the planned-deprecated fields the normal editing surface.

Structured controls prefer canonical field names such as `trigger`, `player_items`, `give_items`, `requires_villager_*`, `requires_witness_*`, and `world_text_kind`. Compatibility aliases are preserved when imported raw JSON still owns them.

Dialogue, forced-opening, and notification text fields accept one variation per line where the runtime supports `lines`. The Forced tab's Options JSON editor supports option `response` / `responses`, plus payment and stolen-item outcome `success_response` / `success_responses` and `failure_response` / `failure_responses`.
