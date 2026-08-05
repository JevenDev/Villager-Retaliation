# Villager Event Triggers

Villager event triggers run actions when the village memory system records an event. Use them when a remembered event should start a quest action, show a notice, write another fact, or launch a persistent scene.

This system reacts to memory records. It does not add new Minecraft or NeoForge event listeners.

## Path And ID

```text
data/<namespace>/villager_events/<path>.json
```

The file path becomes the trigger ID unless the JSON supplies `id`.

For example:

```text
data/my_pack/villager_events/raid_thanks.json
```

creates `my_pack:raid_thanks`.

## Minimal Example

```json
{
  "memory": "villagerretaliation:player_defended_raid",
  "scope": "player",
  "cooldown": 24000,
  "actions": [
    {
      "type": "notification",
      "trigger": "quest.updated",
      "text": "The village remembers what {player} did during the raid."
    }
  ]
}
```

When the named memory is written, this trigger can notify the involved player. Its cooldown is tracked separately for each player.

## Main Fields

| Field | Default | Meaning |
| --- | --- | --- |
| `id` | File path ID | Stable namespaced trigger ID. |
| `listen` | `memory_written` | Event family to listen to. Memory writes are the current supported family. |
| `memory`, `tag`, or `tags` | Any memory | One or more memory tags. Use a narrow list for predictable behavior. |
| `scope` | `village` | Where cooldown and one-time state are tracked. |
| `conditions` | None | Normal dialogue conditions that must all pass. |
| `actions` | None | One or more shared actions. At least one is required. |
| `cooldown` | `0` | Delay in ticks before the same trigger can run again in the selected scope. |
| `repeatable` | `true` | Set to `false` to run once in each selected scope. |
| `once` or `run_once` | `false` | Compatibility aliases that invert `repeatable`. |

Available scopes:

| Scope | State is tracked for |
| --- | --- |
| `player` | The player attached to the memory event. |
| `source_villager` | The villager that wrote the memory. `source` and `villager` are accepted aliases. |
| `village` | The resolved village area. This is the default. |

## Conditions Need Live Context

Conditions need both the source villager and player to be loaded. If either is missing, a trigger with conditions does not run.

Actions have different context needs. Notifications, tracker flashes, positive experience grants, and memory actions can still run when their required target is available. Provider-bound actions such as forced dialogue and some quest actions need the player and villager loaded.

Use [JSON Reference](JSON-Reference.md#shared-actions) for the shared action fields.

## Placeholders

Action text can use:

```text
{memory}
{memory_tag}
{event}
{event_x}
{event_y}
{event_z}
{event_dimension}
{villager}
{villager_profession}
{player}
```

Villager and player placeholders are available only when those entities can be resolved.

## Quest Fact Example

```json
{
  "id": "my_pack:remember_first_defense",
  "tags": ["villagerretaliation:player_defended_village"],
  "scope": "player",
  "repeatable": false,
  "actions": [
    {
      "type": "set_tag",
      "scope": "player",
      "tag": "my_pack:first_village_defense"
    },
    {
      "type": "notification",
      "trigger": "quest.updated",
      "text": "A village now knows you as a defender."
    }
  ]
}
```

## Avoid Trigger Loops

A memory action can write another memory, which can run another trigger. Keep the chain short and do not create two triggers that write each other's memory tags.
