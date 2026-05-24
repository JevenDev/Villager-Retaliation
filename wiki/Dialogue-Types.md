# Dialogue Types

Dialogue `type` values connect menu options to the kind of response the villager should give. The same value is used in two places:

- `options[].type` decides what request is sent when the player clicks that option.
- `lines[].type` decides which pool a response belongs to.

For a custom option, the option `id` and the line `option` / `option_ids` should usually be paired. Built-in options such as `adult_share_story` can be targeted directly by addon lines.

Enum values are case-insensitive in code, but lowercase snake case is recommended.

```json
{
  "options": [
    {
      "id": "my_pack.ask_weather",
      "label": "Ask About Weather",
      "type": "question"
    }
  ],
  "lines": [
    {
      "id": "my_pack.weather.clear",
      "option": "my_pack.ask_weather",
      "type": "question",
      "text": "Clear skies make honest roads."
    }
  ]
}
```

## Quick Reference

| Type | Common use |
| --- | --- |
| `chat` | General conversation. |
| `greeting` | Greeting-style replies. |
| `question` | Answers to player questions. |
| `gift_preferences` | Gift preference hints and advice. |
| `gift_advice_followup` | Follow-up after gift advice is tested. |
| `map_report` | Reports after cartographer map discoveries. |
| `story_hint_report` | Reports after structure or biome rumor discoveries. |
| `combat_survival_report` | Follow-up after a villager survives combat. |
| `gear_report` | Follow-up after gifted gear is used or kept. |
| `recruitment_followup` | Follow-up after recruitment or follower outcomes. |
| `cured_recognition` | Recognition after curing a known zombie villager. |
| `village_event_report` | Reports about recent village events. |
| `apology` | Apology conversations after remembered harm. |
| `village_defense_report` | Thanks or reactions after village defense. |
| `story` | General stories or rumors. |
| `share_story` | Story lines tied to discovered structures or biomes. |
| `joke` | Joke responses. |
| `insult` | Insult responses. |

## Dropdown Examples

Each dropdown starts with a minimal implementation, then an expanded version that shows realistic filters, placeholders, or companion fields.

<details>
<summary><strong>chat</strong></summary>

Use `chat` for general conversation that does not need a special system report.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.chat.simple",
      "type": "chat",
      "text": "Some days are quieter than others."
    }
  ]
}
```

Expanded:

```json
{
  "options": [
    {
      "id": "my_pack.ask_small_talk",
      "label": "Make Small Talk",
      "type": "chat",
      "order": 12
    }
  ],
  "lines": [
    {
      "id": "my_pack.chat.farmer_morning",
      "type": "chat",
      "option": "my_pack.ask_small_talk",
      "professions": ["farmer"],
      "times": ["morning"],
      "dispositions": ["friendly", "respectful", "neutral"],
      "text": "Morning is when the fields tell the truth.",
      "weight": 20
    }
  ]
}
```

</details>

<details>
<summary><strong>greeting</strong></summary>

Use `greeting` for hello-style responses, especially when a custom option should feel like starting a conversation.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.greeting.simple",
      "type": "greeting",
      "text": "Good to see you."
    }
  ]
}
```

Expanded:

```json
{
  "lines": [
    {
      "id": "my_pack.greeting.trusted_librarian",
      "type": "greeting",
      "professions": ["librarian"],
      "dispositions": ["friendly", "respectful"],
      "first_conversation_only": true,
      "text": "Ah, a familiar face. That is better than a quiet shelf.",
      "weight": 18
    }
  ]
}
```

</details>

<details>
<summary><strong>question</strong></summary>

Use `question` for custom questions and answer pools.

Simple:

```json
{
  "options": [
    {
      "id": "my_pack.ask_work",
      "label": "Ask About Work",
      "type": "question"
    }
  ],
  "lines": [
    {
      "id": "my_pack.question.work",
      "type": "question",
      "option": "my_pack.ask_work",
      "text": "Work goes better when nobody tramples the floor."
    }
  ]
}
```

Expanded:

```json
{
  "options": [
    {
      "id": "my_pack.ask_family",
      "label": "Ask About Family",
      "type": "question",
      "requires_known_family": true,
      "show_for_babies": false,
      "order": 18
    }
  ],
  "lines": [
    {
      "id": "my_pack.question.family_child",
      "type": "question",
      "option": "my_pack.ask_family",
      "requires_known_child": true,
      "dispositions": ["friendly", "respectful", "neutral"],
      "text": "{child} has started copying the way I walk. I am trying to deserve that.",
      "weight": 25
    }
  ]
}
```

</details>

<details>
<summary><strong>gift_preferences</strong></summary>

Use `gift_preferences` for lines about what villagers like or dislike as gifts.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.gift_preferences.simple",
      "type": "gift_preferences",
      "text": "Useful gifts tend to last longer than fancy ones."
    }
  ]
}
```

Expanded:

```json
{
  "lines": [
    {
      "id": "my_pack.gift_preferences.profession_liked",
      "type": "gift_preferences",
      "gift_advice": "profession_liked",
      "professions": ["fletcher"],
      "text": "For a fletcher, {gift_item} is not just a gift. It is a better workday.",
      "weight": 24
    }
  ]
}
```

</details>

<details>
<summary><strong>gift_advice_followup</strong></summary>

Use `gift_advice_followup` after the player tests advice from a villager.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.gift_advice_followup.simple",
      "type": "gift_advice_followup",
      "text": "So, how did that gift advice turn out?"
    }
  ]
}
```

Expanded:

```json
{
  "options": [
    {
      "id": "my_pack.report_gift_advice",
      "label": "Talk About Gift Advice",
      "type": "gift_advice_followup",
      "requires_unreported_gift_advice_result": true,
      "order": 24
    }
  ],
  "lines": [
    {
      "id": "my_pack.gift_advice_followup.failed",
      "type": "gift_advice_followup",
      "option": "my_pack.report_gift_advice",
      "text": "If {gift_item} missed the mark, try {alternative_gift} for {gift_subject} next time.",
      "weight": 25
    }
  ]
}
```

</details>

<details>
<summary><strong>map_report</strong></summary>

Use `map_report` after a player finds a cartographer map target and reports it.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.map_report.simple",
      "type": "map_report",
      "text": "So the map was honest after all."
    }
  ]
}
```

Expanded:

```json
{
  "options": [
    {
      "id": "my_pack.report_map",
      "label": "Report Map Discovery",
      "type": "map_report",
      "requires_unreported_cartographer_map_discovery": true,
      "order": 20
    }
  ],
  "lines": [
    {
      "id": "my_pack.map_report.cartographer",
      "type": "map_report",
      "option": "my_pack.report_map",
      "professions": ["cartographer"],
      "text": "Good. A map earns its ink when someone comes back from the place it promised.",
      "weight": 25
    }
  ]
}
```

</details>

<details>
<summary><strong>story_hint_report</strong></summary>

Use `story_hint_report` after a player confirms a discovered story hint.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.story_hint_report.simple",
      "type": "story_hint_report",
      "text": "So the rumor had a road under it."
    }
  ]
}
```

Expanded:

```json
{
  "options": [
    {
      "id": "my_pack.report_story_hint",
      "label": "Report Rumor Discovery",
      "type": "story_hint_report",
      "requires_unreported_story_hint_discovery": true
    }
  ],
  "lines": [
    {
      "id": "my_pack.story_hint_report.ancient_city",
      "type": "story_hint_report",
      "option": "my_pack.report_story_hint",
      "text": "You found {target_article}. I will remember that name carefully.",
      "weight": 25
    }
  ]
}
```

</details>

<details>
<summary><strong>combat_survival_report</strong></summary>

Use `combat_survival_report` after a villager survives a combat situation worth reporting.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.combat_survival.simple",
      "type": "combat_survival_report",
      "text": "Still standing. That counts for something."
    }
  ]
}
```

Expanded:

```json
{
  "options": [
    {
      "id": "my_pack.ask_survival",
      "label": "Ask If They Are Alright",
      "type": "combat_survival_report",
      "requires_unreported_combat_survival_report": true
    }
  ],
  "lines": [
    {
      "id": "my_pack.combat_survival.cleric",
      "type": "combat_survival_report",
      "option": "my_pack.ask_survival",
      "professions": ["cleric"],
      "event_tags": ["night_attack", "raid"],
      "text": "A little pain is easier to treat than a village full of grief.",
      "weight": 24
    }
  ]
}
```

</details>

<details>
<summary><strong>gear_report</strong></summary>

Use `gear_report` after the player gives a villager useful armor or weapons.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.gear_report.simple",
      "type": "gear_report",
      "text": "The gear helps."
    }
  ]
}
```

Expanded:

```json
{
  "options": [
    {
      "id": "my_pack.ask_gear",
      "label": "Ask About Gear",
      "type": "gear_report",
      "requires_unreported_gear_report": true
    }
  ],
  "lines": [
    {
      "id": "my_pack.gear_report.used",
      "type": "gear_report",
      "option": "my_pack.ask_gear",
      "requires_gear_report_used_in_combat": true,
      "text": "You were right to hand it over. It has already done honest work.",
      "weight": 25
    }
  ]
}
```

</details>

<details>
<summary><strong>recruitment_followup</strong></summary>

Use `recruitment_followup` after follower dismissal, betrayal, or travel memories.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.recruitment_followup.simple",
      "type": "recruitment_followup",
      "text": "I made it back. That matters."
    }
  ]
}
```

Expanded:

```json
{
  "options": [
    {
      "id": "my_pack.ask_followup",
      "label": "Ask About The Trip",
      "type": "recruitment_followup",
      "requires_unreported_recruitment_followup": true
    }
  ],
  "lines": [
    {
      "id": "my_pack.recruitment_followup.ocean",
      "type": "recruitment_followup",
      "option": "my_pack.ask_followup",
      "requires_recruitment_memory": true,
      "requires_recruitment_ocean_crossing": true,
      "min_recruitment_follow_distance": 600,
      "text": "I followed you through {follow_biome} for {follow_distance}. That is more road than most promises survive.",
      "weight": 30
    }
  ]
}
```

</details>

<details>
<summary><strong>cured_recognition</strong></summary>

Use `cured_recognition` after a player cures a known zombie villager.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.cured_recognition.simple",
      "type": "cured_recognition",
      "text": "{cured_villager} remembers daylight because of you."
    }
  ]
}
```

Expanded:

```json
{
  "options": [
    {
      "id": "my_pack.ask_cured",
      "label": "Ask About The Cure",
      "type": "cured_recognition",
      "requires_unreported_cured_recognition": true
    }
  ],
  "lines": [
    {
      "id": "my_pack.cured_recognition.grateful",
      "type": "cured_recognition",
      "option": "my_pack.ask_cured",
      "player_event_tags": ["player_cured_villager"],
      "dispositions": ["friendly", "respectful", "neutral"],
      "text": "{cured_villager_possessive} second chance belongs partly to you.",
      "weight": 30
    }
  ]
}
```

</details>

<details>
<summary><strong>village_event_report</strong></summary>

Use `village_event_report` for recent village events such as storms, fire, raids, or night attacks.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.village_event.simple",
      "type": "village_event_report",
      "event_tags": ["night_attack"],
      "text": "Last night came too close."
    }
  ]
}
```

Expanded:

```json
{
  "options": [
    {
      "id": "my_pack.ask_recent_event",
      "label": "Ask What Happened",
      "type": "village_event_report",
      "requires_recent_village_event": true
    }
  ],
  "lines": [
    {
      "id": "my_pack.village_event.raid_mason",
      "type": "village_event_report",
      "option": "my_pack.ask_recent_event",
      "professions": ["mason"],
      "event_tags": ["raid"],
      "text": "Walls are easier to rebuild than nerves.",
      "weight": 25
    }
  ]
}
```

</details>

<details>
<summary><strong>apology</strong></summary>

Use `apology` when the player has remembered harm to answer for.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.apology.simple",
      "type": "apology",
      "text": "An apology is a start."
    }
  ]
}
```

Expanded:

```json
{
  "options": [
    {
      "id": "my_pack.apologize",
      "label": "Apologize",
      "type": "apology",
      "requires_unapologized_remembered_harm": true
    }
  ],
  "lines": [
    {
      "id": "my_pack.apology.bed",
      "type": "apology",
      "option": "my_pack.apologize",
      "requires_recent_broken_bed_memory": true,
      "dispositions": ["cautious", "rude", "hostile"],
      "text": "That was my bed. I can accept sorry, but I will not pretend it was nothing.",
      "weight": 30
    }
  ]
}
```

</details>

<details>
<summary><strong>village_defense_report</strong></summary>

Use `village_defense_report` after the player defends the village, especially during raids.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.village_defense.simple",
      "type": "village_defense_report",
      "player_event_tags": ["player_defended_village"],
      "text": "You helped us today."
    }
  ]
}
```

Expanded:

```json
{
  "options": [
    {
      "id": "my_pack.report_defense",
      "label": "Talk About The Defense",
      "type": "village_defense_report",
      "requires_unreported_village_defense": true
    }
  ],
  "lines": [
    {
      "id": "my_pack.village_defense.raid",
      "type": "village_defense_report",
      "option": "my_pack.report_defense",
      "player_event_tags": ["player_defended_raid"],
      "event_tags": ["raid"],
      "text": "You stood between us and the banners. That is not forgotten quickly.",
      "weight": 35
    }
  ]
}
```

</details>

<details>
<summary><strong>story</strong></summary>

Use `story` for general stories, rumors, and local flavor that is not tied to a discovered target.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.story.simple",
      "type": "story",
      "text": "Every village has a story it tells too often."
    }
  ]
}
```

Expanded:

```json
{
  "options": [
    {
      "id": "my_pack.ask_old_story",
      "label": "Ask For A Story",
      "type": "story",
      "show_for_babies": false,
      "order": 30
    }
  ],
  "lines": [
    {
      "id": "my_pack.story.night_raid",
      "type": "story",
      "option": "my_pack.ask_old_story",
      "event_tags": ["raid", "night_attack"],
      "times": ["evening", "night"],
      "text": "Some stories wait until dark because they know the listener will believe them then.",
      "weight": 24
    }
  ]
}
```

</details>

<details>
<summary><strong>share_story</strong></summary>

Use `share_story` for discovered structure or biome stories. These lines are usually paired with built-in `adult_share_story` or baby story options.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.share_story.simple",
      "type": "share_story",
      "option": "adult_share_story",
      "text": "{target_article}. That place has a name for a reason."
    }
  ]
}
```

Expanded:

```json
{
  "lines": [
    {
      "id": "my_pack.share_story.ancient_city_librarian",
      "type": "share_story",
      "option": "adult_share_story",
      "professions": ["librarian"],
      "story_structure": "minecraft:ancient_city",
      "dispositions": ["friendly", "respectful", "neutral"],
      "text": "{target_article}. Some places are not ruins. They are warnings that learned architecture.",
      "weight": 30
    }
  ]
}
```

</details>

<details>
<summary><strong>joke</strong></summary>

Use `joke` for playful responses. The system can still filter by disposition, profession, age, and context.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.joke.simple",
      "type": "joke",
      "text": "I would tell you a trade secret, but then I would have to discount it."
    }
  ]
}
```

Expanded:

```json
{
  "options": [
    {
      "id": "my_pack.tell_joke",
      "label": "Tell A Joke",
      "type": "joke",
      "dispositions": ["friendly", "respectful", "neutral"],
      "order": 45
    }
  ],
  "lines": [
    {
      "id": "my_pack.joke.fisherman_rain",
      "type": "joke",
      "option": "my_pack.tell_joke",
      "professions": ["fisherman"],
      "weather": ["rain"],
      "text": "Rain is just the sky trying to join my profession.",
      "weight": 20
    }
  ]
}
```

</details>

<details>
<summary><strong>insult</strong></summary>

Use `insult` for rude or hostile responses. Keep the tone appropriate for your pack.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.insult.simple",
      "type": "insult",
      "text": "I have heard better arguments from a broken door."
    }
  ]
}
```

Expanded:

```json
{
  "options": [
    {
      "id": "my_pack.provoke",
      "label": "Provoke",
      "type": "insult",
      "show_for_babies": false,
      "order": 80
    }
  ],
  "lines": [
    {
      "id": "my_pack.insult.hostile_weapon",
      "type": "insult",
      "option": "my_pack.provoke",
      "dispositions": ["rude", "hostile", "fearful"],
      "player_items": ["#minecraft:swords", "#minecraft:axes"],
      "player_item_slots": ["main_hand"],
      "text": "Holding {held_item} does not make you brave. It makes you obvious.",
      "weight": 25
    }
  ]
}
```

</details>

