# Villager Retaliation Datapack Builder

Open [Villager Retaliation Datapack Generator](https://jevendev.github.io/Villager-Retaliation/) to create, import, preview, and export Villager Retaliation datapacks.

The builder is a static site. It emits the selected Villager Retaliation pack version's wiki paths for dialogue, forced dialogue events, chat event lines, notifications, gifts, pacification payments, story discovery, preset names, and `pack.mcmeta`, then exports them as a datapack zip with root-level `pack.mcmeta`.

The Wiki button opens a versioned, built-in wiki snapshot for the selected Villager Retaliation pack version. Press `Alt+Q` to open or close it. The wiki window can be dragged, resized, closed, and reopened at its previous position.

Exports from beta.11 onward include a `villagerretaliation.pack_version` marker in `pack.mcmeta`. Import uses that marker to restore the target VR version automatically. If an older or hand-written pack does not include the marker, choose the intended VR version in Pack Setup before editing or exporting.

The builder writes dialogue options as `type: "dialogue_option"` with a separate `request`, and writes event chat through `output.mode: "chat"` on normal forced-dialogue triggers. It supports watched-container events, `retaliation_started` chat lines, and `player_item_proximity` item callouts.

Dialogue, forced-opening, and notification text fields accept one variation per line where the runtime supports `lines`. The Forced tab's Options JSON editor supports option `response` / `responses`, plus payment and stolen-item outcome `success_response` / `success_responses` and `failure_response` / `failure_responses`.
