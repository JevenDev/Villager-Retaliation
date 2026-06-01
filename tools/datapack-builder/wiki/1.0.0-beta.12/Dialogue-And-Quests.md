# Dialogue And Quests

The cleanest beta.12 quest authoring style is module-based. One quest or story beat gets a matching set of files that share the same relative path.

## Recommended Module Layout

```text
data/<namespace>/quests/<questline>/<quest>.json
data/<namespace>/dialogue_trees/<locale>/quests/<questline>/<quest>.json
data/<namespace>/dialogue/<locale>/quests/<questline>/<quest>/...
data/<namespace>/forced_dialogue/quests/<questline>/<quest>.json
```

Only create the files the module actually needs.

## Example Module

```text
data/my_pack/quests/old_roads/road_ledger.json
data/my_pack/dialogue_trees/en_us/quests/old_roads/road_ledger.json
data/my_pack/dialogue/en_us/quests/old_roads/road_ledger/messages/00_shared.json
data/my_pack/forced_dialogue/quests/old_roads/road_ledger.json
```

## What Each File Does

| File | Job |
| --- | --- |
| Quest JSON | Rules, objectives, rewards, tracker text, triggers |
| Dialogue tree | The branching offer/reminder/turn-in scene |
| Normal dialogue | Reusable extra talk, keyed messages, or follow-up flavor |
| Forced dialogue | Locked quest scenes triggered by events or quest actions |

## Example Ownership Split

Use the quest file for quest state:

```json
{
  "id": "my_pack:road_ledger",
  "questline": "old_roads"
}
```

Use the dialogue tree for the player-facing scene:

```json
{
  "entries": [
    {
      "id": "offer",
      "label": "Road Ledger",
      "conditions": [
        { "type": "quest", "state": "available" }
      ],
      "start": "offer"
    }
  ]
}
```

Use normal dialogue only when the quest also adds reusable talk outside the tree:

```json
{
  "id": "my_pack.message.road_ledger_hint",
  "key": "my_pack.message.road_ledger_hint",
  "text": "Paper survives rain worse than stone does."
}
```

## Good Rule

Do not repeat quest offer requirements in three places.

If the quest file already says the quest is only for farmers, let the dialogue tree use:

```json
{ "type": "quest", "state": "available" }
```

instead of duplicating the profession gate again in the tree.

## When To Add Forced Dialogue

Add forced dialogue only when the quest needs:

- a locked event scene
- an interruption during progress
- a trigger-based confrontation
- authored quest chatter outside the Talk menu

If the entire interaction can happen through one tree, keep it in the tree.
