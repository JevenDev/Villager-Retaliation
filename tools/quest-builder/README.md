# Villager Retaliation Quest Builder

The Quest Builder is a standalone static site for authoring `villagerretaliation:quest/v2` files and `villagerretaliation:scene/v1` resources. It supports multiple quests and persistent scenes per local project, linear and branching quest templates, a visual stage flow, a structured stable-ID actor/step editor, transition validation, browser-side validation, JSON review, local draft recovery, and datapack ZIP export.

Serve the `tools` directory over HTTP when developing locally so the builder can load the shared quest registry metadata:

```bash
python -m http.server 4173 --directory tools
```

Then open `http://127.0.0.1:4173/quest-builder/`.

Run the focused checks with:

```bash
node tools/test-quest-builder-model.mjs
node tools/test-quest-builder-zip.mjs
node tools/test-quest-builder-output.mjs
node --check tools/quest-builder/app.js
```

The builder loads `quest-registry-metadata.json` from the datapack generator so both tools use the same registered actor, scene-step, objective, action, condition, and trigger IDs. Browser-available descriptors are offered by the structured editor; runtime-only extension descriptors remain visible but are explicitly labelled. Update the shared metadata at its existing source rather than creating a quest-builder-only copy.
