# Dialogue Requests

`request` chooses which dialogue pool a line belongs to. It also tells the runtime what kind of conversation the player is asking for.

## Basic Pattern

```json
{
  "id": "my_pack.option.ask_weather",
  "label": "Ask About Weather",
  "request": "question"
}
```

```json
{
  "id": "my_pack.line.weather",
  "request": "question",
  "option": "my_pack.option.ask_weather",
  "text": "Clear skies never last as long as confident people think."
}
```

## Current Request Families

| Request | Use it for | Example line |
| --- | --- | --- |
| `greeting` | hello-style replies | `"Good to see you."` |
| `question` | custom questions and general talk | `"Work goes better when nobody panics."` |
| `gift_preferences` | hints about liked and disliked gifts | `"Useful gifts last longer than flashy ones."` |
| `gift_advice_followup` | talking after advice was tested | `"So, did the gift land well?"` |
| `map_report` | reporting a cartographer map discovery | `"So the map was honest after all."` |
| `story_hint_report` | reporting a rumor or discovery lead | `"Then the rumor had a real road under it."` |
| `combat_survival_report` | talking after a villager survives danger | `"Still standing. That counts."` |
| `gear_report` | talking after giving armor or weapons | `"The gear helped more than you know."` |
| `recruitment_followup` | talking after following the player | `"I made it back. That matters."` |
| `cured_recognition` | reacting to a cured villager | `"{cured_villager} remembers you kindly."` |
| `village_event_report` | recent village news and aftermath | `"The village is still talking about last night."` |
| `apology` | remembered harm and making amends | `"Apologies are better when they change what comes next."` |
| `village_defense_report` | thanking or reacting to defense | `"You stood with us when it mattered."` |
| `story` | rumors, lore, personal stories | `"Roads keep secrets. Villages keep better ones."` |
| `share_story` | lines tied to discovered structures or biomes | `"{target_article}. Walk carefully if you go back."` |
| `joke` | lighter one-liners | `"If the wheat starts gossiping, we have bigger problems."` |
| `insult` | hostile or sharp responses | `"You bring trouble faster than traders bring wool."` |

## Example: Report-Style Request

Built-in report requests usually pair well with requirement flags or event memory.

```json
{
  "id": "my_pack.line.map_report",
  "request": "map_report",
  "text": "Good. A map earns its ink when someone returns from the place it promised."
}
```

## Example: `share_story`

`share_story` is where story discovery data and dialogue meet.

```json
{
  "id": "my_pack.line.haunted_keep",
  "request": "share_story",
  "option": "adult_share_story",
  "story_structure": "examplemod:haunted_keep",
  "text": "{target_article}. If you found it, leave before dark."
}
```

## Example: Social Request

```json
{
  "id": "my_pack.line.apology",
  "request": "apology",
  "player_event_tags": ["player_attacked_villager"],
  "text": "If you mean that apology, start by not making me need another one."
}
```

Pick the request that matches the player's intent first. Then add filters for profession, mood, reputation, memory, or conditions.
