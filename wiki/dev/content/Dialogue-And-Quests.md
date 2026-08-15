# Dialogue And Quests

Quest module v2 makes one-file quests the default starting point. Keep the quest's stages, objectives, short offer/reminder/turn-in dialogue, responses, transitions, events, rewards, and tracker UI together in:

```text
data/<namespace>/quests/<module>/<quest>.json
```

Extract extra files only when the module benefits from separate ownership, long authored scenes, shared localization, or event-driven forced dialogue.

## Recommended Module Layout

```text
data/<namespace>/quests/<module>/<quest>.json
data/<namespace>/dialogue_trees/<locale>/quests/<module>/<quest>.json
data/<namespace>/dialogue/<locale>/quests/<module>/<quest>/messages/*.json
data/<namespace>/forced_dialogue/quests/<module>/<quest>.json
```

Only create the files the module actually needs. A simple playable v2 quest does not need a dialogue tree.

## What Each File Does

| File | Job |
| --- | --- |
| Quest module v2 | Provider, availability, lifecycle, stages, objectives, inline scenes, responses, transitions, events, rewards, and tracker UI |
| Dialogue tree | Optional extracted branch scene referenced by `external` or `external_scene` |
| Normal dialogue messages | Optional reusable localized text referenced by `text_key`, `label_key`, or metadata key fields |
| Forced dialogue | Optional locked event scene triggered by a `forced_dialogue` action |

## One-File Ownership

For most small quests, keep the whole playable flow in the quest module:

```json
{
  "schema": "villagerretaliation:quest/v2",
  "id": "my_pack:road_ledger",
  "metadata": {
    "title": "Road Ledger",
    "tags": ["group.old_roads"]
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
          "label": "Road Ledger",
          "request": "question",
          "lines": ["Paper survives rain worse than stone does."],
          "responses": [
            {
              "id": "complete",
              "label": "Mark that down.",
              "complete": true
            }
          ]
        }
      }
    }
  ]
}
```

## Extracted Scene Ownership

Use `external_scene` when a scene belongs in a dialogue tree:

```json
{
  "external_scenes": ["my_pack:quests/old_roads/road_ledger"],
  "stages": [
    {
      "id": "start",
      "objectives": [],
      "dialogue": {
        "offer": {
          "label": "Road Ledger",
          "request": "question",
          "external_scene": {
            "tree": "my_pack:quests/old_roads/road_ledger",
            "entry": "offer"
          }
        }
      }
    }
  ]
}
```

Then put the authored branch under:

```text
data/my_pack/dialogue_trees/en_us/quests/old_roads/road_ledger.json
```

This is useful when the tree is long, when translators should work in a separate file, or when another datapack should be able to replace only the scene without replacing the quest's objective logic.

## Message Ownership

Use normal dialogue message files for shared localized text:

```json
{
  "id": "my_pack.message.road_ledger_hint",
  "key": "quest.my_pack.road_ledger.hint",
  "text": "Paper survives rain worse than stone does."
}
```

Reference that key from quest module fields such as `metadata.title_key`, `metadata.description_key`, `ui.tracker_text_key`, dialogue `text_key`, and response `label_key`.

## Forced Dialogue Ownership

Add forced dialogue only when the quest needs:

- a locked event scene
- an interruption during progress
- a trigger-based confrontation
- authored quest chatter outside the Talk menu

Trigger it from a quest event:

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

`forced_dialogue` is a live-context action. It needs the player and provider loaded. If the issuer is unloaded, the runtime records diagnostics instead of pretending the action succeeded.

## Do Not Duplicate Gates

Do not repeat quest offer requirements in several files.

If the quest module already says the quest is only for farmers, keep the dialogue scene focused on scene ownership:

```json
{
  "provider": {
    "type": "villagerretaliation:villager",
    "filters": {
      "professions": ["minecraft:farmer"]
    }
  }
}
```

In legacy dialogue trees, use the quest condition for state:

```json
{ "type": "quest", "state": "available" }
```

The quest system resolves provider filters, parent locks, cooldowns, branch locks, and completion limits.

## Legacy Layout

V1 quests still use a quest JSON file plus a dialogue tree:

```text
data/<namespace>/quests/<module>/<quest>.json
data/<namespace>/dialogue_trees/<locale>/quests/<module>/<quest>.json
```

That layout remains supported. New v2 modules should start as one quest file and extract only when needed.
