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

Use Convert to migrate a loaded pack to a newer registered format. The beta.11 -> beta.12 conversion updates the pack version marker and leaves existing beta.11 JSON fields intact, because beta.12 only adds optional dialogue line filters. The builder keeps a schema registry for each supported VR version and runs migrations one adjacent version at a time, so a future beta.11 to beta.16 conversion can apply the beta.11 -> beta.12, beta.12 -> beta.13, and later documented steps in order.

Import follows the same strict folder rules as the game for known Villager Retaliation roots. Dialogue files stay dialogue, notification files stay notifications, and forced-dialogue files stay forced dialogue; mixed old packs should be split into the documented folders before export.

The builder writes dialogue options as `type: "dialogue_option"` with a separate `request`, and writes event chat through `output.mode: "chat"` on normal forced-dialogue triggers. It supports watched-container events, `retaliation_started` chat lines, and `player_item_proximity` item callouts. When targeting beta.12, dialogue lines can also use `mood` / `moods`, `min_mood_intensity`, `requires_high_*`, exact Social Attribute score ranges, priority/category selection metadata, and `text_key` message indirection. The beta.12 wiki snapshot also documents the data-driven `trade_refresh` forced-dialogue option templates used by villager trade refresh requests.

Dialogue, forced-opening, and notification text fields accept one variation per line where the runtime supports `lines`. The Forced tab's Options JSON editor supports option `response` / `responses`, plus payment and stolen-item outcome `success_response` / `success_responses` and `failure_response` / `failure_responses`.
