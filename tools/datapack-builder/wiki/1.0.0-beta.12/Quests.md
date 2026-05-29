# Quest JSON

Quests live under:

```text
data/<namespace>/quests/<quest_id>.json
```

Each quest is one JSON file with a stable `id`, an advancement-like `criteria` block for author clarity, and explicit runtime sections for offer rules, target tracking, rewards, and dialogue.

## Minimal Shape

```json
{
  "id": "example:tales_of_a_lost_civilization",
  "display": {
    "title": "Tales of a Lost Civilization",
    "description": "Find an Ancient City center and bring back proof."
  },
  "questline": "lost_civilization",
  "offer": {
    "profession": "minecraft:cartographer",
    "min_villager_level": "journeyman",
    "skills": [{ "skill": "cartography", "min": 50 }]
  },
  "target": {
    "structure": "minecraft:ancient_city",
    "pieces": [
      "ancient_city/city_center/city_center_1",
      "ancient_city/city_center/city_center_2",
      "ancient_city/city_center/city_center_3"
    ],
    "search_radius": 256,
    "discovery_radius": 128,
    "proof_item": "minecraft:echo_shard"
  },
  "rewards": {
    "experience": 350,
    "reputation": 18,
    "gossip_reputation": 8,
    "memory": "player_completed_quest",
    "loot": "example:quest/lost_civilization"
  },
  "dialogue": {
    "start": ["Travel {direction} toward {target_x}, {target_z}. Bring back {proof_item}."],
    "reminder": ["Find the center of {target}, then return with {proof_item}."],
    "turn_in": ["You found it. This village will remember that."]
  }
}
```

## Dialogue Options

Normal dialogue options trigger quest actions with `quest_action`:

```json
{
  "id": "example.lost_civilization.offer",
  "label": "Lost Civilization",
  "type": "dialogue_option",
  "request": "story",
  "conditions": [
    { "type": "quest", "quest": "example:tales_of_a_lost_civilization", "state": "available" }
  ],
  "quest_action": {
    "quest": "example:tales_of_a_lost_civilization",
    "action": "start"
  }
}
```

Supported actions are `start`, `remind`, and `turn_in`. Pair them with quest states:

```text
available
not_started
in_progress
ready
completed
not_completed
```

## Runtime Notes

- `target.structure` is located through Minecraft's structure search, then stored in player quest progress.
- `target.pieces` checks the actual structure piece the player stands in. Omit it when any piece of the structure should count.
- `proof_item` must be in the player's inventory at turn-in time.
- `reputation` changes the returning cartographer's relationship with the player.
- `gossip_reputation` spreads a smaller reputation change through villager gossip.
- `memory` creates a village event memory, so later dialogue can reference the completed quest with `player_completed_quest`.
- `loot` points at a normal loot table under `data/<namespace>/loot_table/`.

## Quest Dialogue Placeholders

Quest dialogue supports:

```text
{quest}
{quest_id}
{target}
{target_x}
{target_z}
{direction}
{distance}
{proof_item}
{visited_target}
{has_proof}
```
