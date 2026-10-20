# In-Game Pack Authoring

The in-game authoring editor replaces the standalone browser datapack and quest builders.

Open it with **F9** while in a world. The editor requires the optional `imguimc` mod. The key can be changed in Minecraft's controls.

Use the editor to manage pack documents, validate changes, preview dialogue and quests, and export a datapack. Keep using the developer wiki and repository example packs when writing JSON by hand.

- [Pack Development](Pack-Development.md) covers pack layout, namespaces, overrides, and reloads.
- [First Quest](First-Quest.md) introduces quest authoring.
- [Persistent Quest Scenes](Quest-Scenes.md) covers scenes and encounters.
- [Example Packs](Example-Packs.md) provides complete starting points.

For Minecraft 1.21.1, use `pack_format: 48`. Install exported packs in the world's `datapacks` folder and run `/reload`.

Shared JSON Schemas and quest registry metadata remain tracked under `tools/schemas/`. The runtime exporters, Node validators, and regression tests use those contracts.
