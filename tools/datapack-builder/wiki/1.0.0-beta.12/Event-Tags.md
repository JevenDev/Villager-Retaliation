# Event Tags

Event tags are short-lived village memories. Dialogue can react to them with `event_tags` and `player_event_tags`.

## Which Field To Use

- `event_tags`: the villager remembers something happened nearby or in the village.
- `player_event_tags`: the current player is specifically tied to that memory.

Example:

```json
{
  "id": "my_pack.line.raid_thanks",
  "request": "village_defense_report",
  "event_tags": ["raid"],
  "player_event_tags": ["player_defended_raid"],
  "text": "You stood with us when the banners came over the hill."
}
```

## Common Tag Areas

| Area | Tags |
| --- | --- |
| Family and village life | `baby_born` |
| Child harm | `baby_villager_attacked` |
| Weather and danger | `thunderstorm`, `sandstorm`, `snowstorm`, `village_fire`, `night_attack` |
| Raids and combat | `raid`, `villager_attacked`, `villager_death`, `villager_retaliation_started`, `iron_golem_defeated_mob`, `golem_killed` |
| Player crimes | `player_attacked_villager`, `player_killed_villager`, `player_container_theft` |
| Player help | `player_defended_village`, `player_defended_raid`, `player_cured_villager` |
| Social changes | `reputation_changed`, `player_gave_loved_gift`, `player_gave_liked_gift`, `player_gave_neutral_gift`, `player_gave_disliked_gift`, `player_gave_hated_gift` |

The parser also accepts `golem_created` and `nearby_hostile_mob`, but those are reserved-style values unless your pack or code writes them.

## Example: Family Life

```json
{
  "id": "my_pack.line.new_baby",
  "request": "question",
  "event_tags": ["baby_born"],
  "text": "There is a new little voice in the village today."
}
```

## Example: Crime Memory

```json
{
  "id": "my_pack.line.theft_memory",
  "request": "apology",
  "player_event_tags": ["player_container_theft"],
  "text": "Village stores are not souvenirs."
}
```

## Example: Defense Memory

```json
{
  "id": "my_pack.line.raid_defense",
  "request": "village_defense_report",
  "player_event_tags": ["player_defended_raid"],
  "text": "The village still talks about the way you fought that raid."
}
```

Event tags are strongest when paired with normal filters like reputation, disposition, profession, or conditions.
