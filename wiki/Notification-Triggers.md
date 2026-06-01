# Notification Triggers

`trigger` decides when a notification entry is eligible to appear. The entry still has to pass its filters, `chance`, and weight.

## High-Value Trigger Groups

| Area | Common triggers |
| --- | --- |
| Quests | `quest.started`, `quest.updated`, `quest.location_reached`, `quest.completed`, `quest.abandoned`, `quest.expired` |
| Gifts | `gift.liked`, `gift.neutral`, `gift.disliked`, `gift.received_item` |
| Discoveries | `dialogue.map.found`, `dialogue.rumor.found` |
| Recruiting | `recruitment.follow_start`, `recruitment.follow_stop`, `recruitment.hired`, `recruitment.fired`, `recruitment.follower_death` |
| Reputation shifts | `reputation.tier.<tier>.improved`, `reputation.tier.<tier>.worsened` |
| Ambient world text | `ambient.murmur` |
| Trading | `trade.completed`, `trade.refused` |

## Example: Quest Trigger

```json
{
  "id": "my_pack.quest.started",
  "trigger": "quest.started",
  "text": "Quest started: {quest}",
  "kind": "quest"
}
```

## Example: Discovery Trigger

```json
{
  "id": "my_pack.rumor.found",
  "trigger": "dialogue.rumor.found",
  "text": "Found rumored place: {target}",
  "kind": "map_discovery",
  "color": "#55AAFF"
}
```

## Example: Ambient Trigger

```json
{
  "id": "my_pack.ambient.revered",
  "trigger": "ambient.murmur",
  "lines": [
    "There they are",
    "Good omen"
  ],
  "world_text_kind": "murmur",
  "reputation_levels": ["revered"]
}
```

## Example: Reputation Trigger

```json
{
  "id": "my_pack.rep.trusted.improved",
  "trigger": "reputation.tier.trusted.improved",
  "text": "You feel yourself gaining {villager_possessive} trust.",
  "color": "green"
}
```

## Choosing A Trigger

- Use a `quest.*` trigger when the line is about state change in a quest.
- Use `ambient.murmur` when the line is just floating flavor text.
- Use a `gift.*` trigger when the player just gave or received an item.
- Use `trade.refused` or `trade.completed` when the line should be tied to the trade UI.

If you already know what happened in code or through another system, choose the smallest matching trigger and then do the nuance with filters.
