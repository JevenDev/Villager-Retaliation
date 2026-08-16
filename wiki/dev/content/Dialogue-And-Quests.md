# Dialogue And Quests

Beta.13 quest content is owned by a bundle. Structural offer, reminder, turn-in, response, and branch dialogue stays in `quest.json`; player-facing wording lives in the bundle locale catalog.

## Bundle Layout

```text
data/<namespace>/quests/
  _shared/
    locales/en_us.json
    pools/*.json
    scenes/*.json
    encounters/*.json
    rewards/*.json
  <questline>/<quest-slug>/
    quest.json
    locales/en_us.json
    locales/<locale>.json
    scenes/*.json
    encounters/*.json
    rewards/*.json
```

A bundle directory defines ownership only. Every quest, scene, encounter, reward, and pool still declares an explicit stable ID. The quest namespace must match `data/<namespace>`, `metadata.questline` must match `<questline>`, and the one-segment quest ID path must match `<quest-slug>`.

The quest structure, private companions, bundled rewards, and effective English form one owner-bundle transaction. An invalid higher layer is rejected as a unit and the lower valid layer remains active. Each optional non-English locale overlay is validated independently.

## Structural Dialogue

Keep dialogue shape and ordering in `quest.json`. Use localized references for every schema-designated player-facing field:

```json
{
  "schema": "villagerretaliation:quest/v2",
  "id": "my_pack:road_ledger",
  "localization_prefix": "my_pack.quest.road_ledger",
  "metadata": {
    "title": { "key": "#title" },
    "description": { "key": "#description" },
    "questline": "old_roads"
  },
  "provider": {
    "type": "villagerretaliation:villager",
    "filters": {
      "professions": ["minecraft:cartographer"]
    }
  },
  "entry_stage": "start",
  "stages": [
    {
      "id": "start",
      "objectives": [],
      "dialogue": {
        "offer": {
          "label": { "key": "#stage.start.dialogue.offer.label" },
          "lines": { "key": "#stage.start.dialogue.offer.lines" },
          "responses": [
            {
              "id": "complete",
              "label": { "key": "#stage.start.dialogue.offer.response.complete.label" },
              "complete": true
            }
          ]
        }
      }
    }
  ]
}
```

`#title` expands relative to the required immutable `localization_prefix`. Existing absolute message IDs remain supported. A new third-party prefix must begin with its namespace and be globally unique.

There is no public quest `dialogues` root and no `external`, `external_scene`, `external_entry`, or `external_scenes` escape hatch. Long structural branches still belong in `quest.json`; moving source files must not change persistent IDs or generated localization keys.

## Locale Ownership

The introducing layer supplies exhaustive English for its bundle-owned references:

```json
{
  "schema": "villagerretaliation:quest_locale/v1",
  "messages": {
    "my_pack.quest.road_ledger.title": {
      "lines": ["Road Ledger"]
    },
    "my_pack.quest.road_ledger.description": {
      "lines": ["Recover a ledger from the old road."]
    },
    "my_pack.quest.road_ledger.stage.start.dialogue.offer.label": {
      "lines": ["Road Ledger"]
    },
    "my_pack.quest.road_ledger.stage.start.dialogue.offer.lines": {
      "lines": ["Paper survives rain worse than stone does."]
    },
    "my_pack.quest.road_ledger.stage.start.dialogue.offer.response.complete.label": {
      "lines": ["Mark that down."]
    }
  }
}
```

Other locales may be partial. Resolution falls back per message ID to effective `en_us` at the existing per-player boundary, so two players can see the same catalog snapshot in different locales. Rich variants preserve authored ordering, formatting, placeholders, weights, and conditions.

Each expanded message ID has one canonical owner bundle or `_shared`. A higher datapack may override the same owner, but duplicate ownership inside one pack or moving ownership between bundles is an error. Put reusable absolute messages in `_shared`; keep private wording with its quest.

## Scene, Encounter, And Reward Companions

Persistent runtime scenes, encounters, and bundled reward tables may live in their owning quest directory. Reference them by their explicit stable IDs. Private companions cannot be referenced by another quest; reusable definitions belong under `_shared`.

These companions are not extracted structural dialogue trees. A scene companion models persistent runtime steps and actors, while `quest.json` continues to own Talk-menu dialogue and branches.

## Forced Dialogue

Forced dialogue is a separate global event system, not a quest-bundle companion. A quest action may reference a stable forced-dialogue ID when an event-driven interruption needs live player and provider context:

```json
{
  "events": [
    {
      "id": "storm_reminder",
      "event": "near_provider",
      "radius": 10,
      "cooldown_seconds": 120,
      "actions": [
        {
          "type": "forced_dialogue",
          "forced_dialogue": "my_pack.quest.road_ledger.storm_warning"
        }
      ]
    }
  ]
}
```

The global forced-dialogue definition keeps its own stable ID. It does not participate in the bundle transaction and must not be used to replace offer, reminder, turn-in, or response structure.

## Legacy Layout

Beta.13 diagnoses loose v1 or v2 quest JSON, `quest_messages`, `quest_scenes`, `quest_encounters`, `quest_pools`, old quest reward roots, and quest-owned dialogue trees as unsupported. The runtime never silently falls back to them. Convert legacy content offline or import it into the browser builder, then export a bundle containing `pack.mcmeta` and `data/`.
