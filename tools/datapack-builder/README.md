# Villager Retaliation Datapack Builder

Open [Villager Retaliation Datapack Generator](https://jevendev.github.io/Villager-Retaliation/) to create, import, preview, and export Villager Retaliation datapacks.

The builder is a static site. It emits the selected Villager Retaliation pack version's wiki paths for dialogue, forced dialogue events, notifications, gifts, pacification payments, story discovery, preset names, and `pack.mcmeta`, then exports them as a datapack zip with root-level `pack.mcmeta`.

Exports from beta.11 onward include a `villagerretaliation.pack_version` marker in `pack.mcmeta`. Import uses that marker to restore the target VR version automatically. If an older or hand-written pack does not include the marker, choose the intended VR version in Pack Setup before editing or exporting.
