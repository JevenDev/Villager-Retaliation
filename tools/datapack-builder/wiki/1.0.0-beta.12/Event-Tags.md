# Event Tags

Event tags are recent village-memory markers that dialogue lines can use with `event_tags` and `player_event_tags`.

Use `event_tags` when the line should react to something that happened near the villager or in their resolved village area. Use `player_event_tags` when the line should only react if the current player is attached to that remembered event. The memory window is short: current code keeps these events for about 10 minutes of game time, stores up to 80 events per dimension, and considers events in the village area or within roughly 48 blocks of the villager.

Enum values are case-insensitive in code, but lowercase snake case is the recommended datapack style.

```json
{
  "event_tags": [
    "raid"
  ],
  "player_event_tags": [
    "player_defended_raid"
  ]
}
```

## Quick Reference

| Tag | Usually remembered when | Good fit for |
| --- | --- | --- |
| `baby_born` | A villager baby is created and linked to parents. | Family, village growth, gossip after breeding. |
| `baby_villager_attacked` | A player damages a baby villager. | Child-specific hurt reactions, adult concern, and baby-only follow-up dialogue. |
| `iron_golem_defeated_mob` | An iron golem kills a hostile mob. | Relief after golem defense. |
| `thunderstorm` | A thunderstorm is noticed near a villager in a rainy biome. | Weather anxiety and storm chatter. |
| `sandstorm` | A thunderstorm is noticed near a villager in a dry biome with no precipitation. | Desert or badlands storm flavor. |
| `snowstorm` | A thunderstorm is noticed near a villager in a cold snowy biome. | Snow and cold-weather danger. |
| `village_fire` | A villager is damaged by fire. | Panic after fire, lava, or flame damage. |
| `night_attack` | A hostile mob attacks a villager at night, or a hostile mob dies at night nearby. | Night-safety comments. |
| `raid` | A raider hurts a villager during an active raid, or a raider dies during an active raid. | Raid fear, aftermath, warnings. |
| `villager_death` | A villager or wandering trader dies. | Mourning, blame, shock. |
| `villager_attacked` | A villager or wandering trader takes damage. | Recent harm reports. |
| `player_attacked_villager` | A player damages a villager or wandering trader. | Direct accusations and apology hooks. |
| `player_defended_village` | A player kills a hostile mob. | Thanks after general defense. |
| `player_defended_raid` | A player kills a raider during an active raid. | Stronger raid-defense thanks. |
| `player_cured_villager` | A zombie villager is cured by a player. | Recognition and gratitude. |
| `golem_created` | Accepted by the parser, but no built-in writer is present in current source. | Reserved/custom-code use. |
| `golem_killed` | An iron golem dies. | Alarm after village protection is lost. |
| `nearby_hostile_mob` | Accepted by the parser, but no built-in writer is present in current source. | Reserved/custom-code use. |
| `reputation_changed` | Reputation changes for a villager and player. | Social temperature, trust, suspicion. |
| `player_gave_loved_gift` | A player gives a gift with `loved` reaction. | Warm gift gossip. |
| `player_gave_liked_gift` | A player gives a gift with `liked` reaction. | Positive gift gossip. |
| `player_gave_neutral_gift` | A player gives a gift with `neutral` reaction. | Mild gift reactions. |
| `player_gave_disliked_gift` | A player gives a gift with `disliked` reaction. | Awkward or annoyed gift gossip. |
| `player_gave_hated_gift` | A player gives a gift with `hated` reaction. | Serious offense after a bad gift. |
| `player_container_theft` | A player is witnessed taking items from a watched container. | Theft accusations, restitution, and village gossip. |
| `villager_retaliation_started` | A villager or wandering trader acquires a new retaliation target. | Gossip after a fight, follow-up warnings, or reactive combat chatter. |

## Dropdown Examples

Each dropdown has one small line you can paste into an existing dialogue file, then an expanded line showing how the same tag can be combined with profession, dialogue disposition, player-specific memory, or placeholders.

<details>
<summary><strong>baby_born</strong></summary>

Use this after a villager baby is created and linked into the family graph. If the birth was caused by a player, `player_event_tags` can target that player.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.event.baby_born.simple",
      "request": "question",
      "event_tags": [
        "baby_born"
      ],
      "text": "There is a new little voice in the village today."
    }
  ]
}
```

Expanded:

```json
{
  "lines": [
    {
      "id": "my_pack.event.baby_born.family_farmer",
      "request": "question",
      "option": "ask_about_family",
      "professions": [
        "farmer"
      ],
      "dispositions": [
        "friendly",
        "respectful"
      ],
      "player_event_tags": [
        "baby_born"
      ],
      "conditions": [
        {
          "type": "family",
          "relation": "child"
        }
      ],
      "text": "You helped bring {child} into this village. The fields will know that kindness.",
      "weight": 30
    }
  ]
}
```

</details>

<details>
<summary><strong>baby_villager_attacked</strong></summary>

Use this after a player damages a baby villager. It is usually paired with `player_event_tags: ["player_attacked_villager"]` when the line should be about the current player's action.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.event.baby_villager_attacked.simple",
      "request": "village_event_report",
      "event_tags": [
        "baby_villager_attacked"
      ],
      "text": "Someone small got hurt. That sound stays in a village."
    }
  ]
}
```

Expanded:

```json
{
  "lines": [
    {
      "id": "my_pack.event.baby_villager_attacked.player",
      "request": "apology",
      "player_event_tags": [
        "player_attacked_villager"
      ],
      "event_tags": [
        "baby_villager_attacked"
      ],
      "show_for_babies": false,
      "dispositions": [
        "cautious",
        "rude",
        "hostile"
      ],
      "lines": [
        "You made a child cry. There is no brave version of that story.",
        "Everyone heard the little one yell. Start explaining there."
      ],
      "weight": 40
    }
  ]
}
```

</details>

<details>
<summary><strong>player_container_theft</strong></summary>

Use this when a player is witnessed taking items from a watched container. Lines can use `{stolen_item}`, `{stolen_item_id}`, `{stolen_count}`, `{stolen_item_count}`, `{stolen_stack}`, `{stolen_container}`, `{stolen_loot_table}`, `{theft_witness}`, and `{theft_witness_possessive}`.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.event.container_theft.simple",
      "request": "question",
      "player_event_tags": [
        "player_container_theft"
      ],
      "text": "I heard about the {stolen_stack}. Village stores are not souvenirs."
    }
  ]
}
```

Expanded:

```json
{
  "lines": [
    {
      "id": "my_pack.event.container_theft.gossip",
      "request": "greeting",
      "conditions": [
        {
          "type": "memory",
          "tag": "player_container_theft",
          "source": "other_villager",
          "player": true
        }
      ],
      "dispositions": [
        "cautious",
        "rude",
        "hostile"
      ],
      "text": "{theft_witness} told me about you stealing {stolen_stack}. Not cool.",
      "weight": 35
    },
    {
      "id": "my_pack.event.container_theft.direct",
      "request": "question",
      "conditions": [
        {
          "type": "memory",
          "tag": "player_container_theft",
          "source": "this_villager",
          "player": true
        }
      ],
      "dispositions": [
        "cautious",
        "rude",
        "hostile"
      ],
      "text": "I still remember you trying to take my {stolen_item}.",
      "weight": 35
    }
  ]
}
```

</details>

<details>
<summary><strong>villager_retaliation_started</strong></summary>

Use this after a villager or wandering trader acquires a new retaliation target. Lines can use `{retaliation_target}`, `{retaliation_target_name}`, `{retaliation_target_kind}`, `{retaliation_target_type}`, `{retaliation_witness}`, and `{retaliation_witness_possessive}`. Use a `conditions` memory block with `source: "this_villager"` or `source: "other_villager"`, plus `retaliation_target_entity_types` when you want more specific filtering.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.event.retaliation_started.simple",
      "request": "question",
      "event_tags": [
        "villager_retaliation_started"
      ],
      "text": "{retaliation_witness} still looks ready to finish {retaliation_target}."
    }
  ]
}
```

Expanded:

```json
{
  "lines": [
    {
      "id": "my_pack.event.retaliation_started.self_zombie",
      "request": "greeting",
      "event_tags": [
        "villager_retaliation_started"
      ],
      "conditions": [
        {
          "type": "memory",
          "tag": "villager_retaliation_started",
          "source": "this_villager"
        }
      ],
      "retaliation_target_entity_types": [
        "minecraft:zombie",
        "minecraft:husk"
      ],
      "text": "I almost buried my axe in that {retaliation_target_kind}.",
      "weight": 30
    },
    {
      "id": "my_pack.event.retaliation_started.player_target",
      "request": "question",
      "player_event_tags": [
        "villager_retaliation_started"
      ],
      "dispositions": [
        "hostile",
        "fearful"
      ],
      "text": "{retaliation_witness} has not forgotten choosing you as the threat.",
      "weight": 30
    }
  ]
}
```

</details>

<details>
<summary><strong>iron_golem_defeated_mob</strong></summary>

Use this after an iron golem kills a hostile mob.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.event.golem_defense.simple",
      "request": "story",
      "event_tags": [
        "iron_golem_defeated_mob"
      ],
      "text": "The golem handled trouble before it reached the doors."
    }
  ]
}
```

Expanded:

```json
{
  "lines": [
    {
      "id": "my_pack.event.golem_defense.armorer",
      "request": "village_event_report",
      "professions": [
        "armorer"
      ],
      "event_tags": [
        "iron_golem_defeated_mob"
      ],
      "dispositions": [
        "neutral",
        "cautious",
        "friendly"
      ],
      "text": "That iron frame bought us time. I should check its plates before nightfall.",
      "weight": 25
    }
  ]
}
```

</details>

<details>
<summary><strong>thunderstorm</strong></summary>

Use this when thunder is noticed near a villager in a biome that has rain.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.event.thunderstorm.simple",
      "request": "question",
      "event_tags": [
        "thunderstorm"
      ],
      "text": "Thunder makes every roof sound thinner."
    }
  ]
}
```

Expanded:

```json
{
  "lines": [
    {
      "id": "my_pack.event.thunderstorm.librarian_night",
      "request": "village_event_report",
      "professions": [
        "librarian"
      ],
      "times": [
        "night"
      ],
      "weather": [
        "thunder"
      ],
      "event_tags": [
        "thunderstorm"
      ],
      "text": "Storms at night make old stories feel less old.",
      "weight": 20
    }
  ]
}
```

</details>

<details>
<summary><strong>sandstorm</strong></summary>

Use this when thunder is noticed near a villager in a dry biome with no precipitation, such as desert-like terrain.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.event.sandstorm.simple",
      "request": "question",
      "event_tags": [
        "sandstorm"
      ],
      "text": "The wind has teeth today."
    }
  ]
}
```

Expanded:

```json
{
  "lines": [
    {
      "id": "my_pack.event.sandstorm.cartographer",
      "request": "village_event_report",
      "professions": [
        "cartographer"
      ],
      "event_tags": [
        "sandstorm"
      ],
      "times": [
        "afternoon",
        "evening"
      ],
      "text": "Sand moved over the road marks. I would not trust yesterday's path.",
      "weight": 25
    }
  ]
}
```

</details>

<details>
<summary><strong>snowstorm</strong></summary>

Use this when thunder is noticed near a villager in a cold biome where precipitation falls as snow.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.event.snowstorm.simple",
      "request": "question",
      "event_tags": [
        "snowstorm"
      ],
      "text": "Snow can hide a lot of bad footsteps."
    }
  ]
}
```

Expanded:

```json
{
  "lines": [
    {
      "id": "my_pack.event.snowstorm.fisherman",
      "request": "village_event_report",
      "professions": [
        "fisherman"
      ],
      "event_tags": [
        "snowstorm"
      ],
      "weather": [
        "thunder"
      ],
      "show_for_babies": false,
      "text": "Cold like this turns water into a promise it may not keep.",
      "weight": 20
    }
  ]
}
```

</details>

<details>
<summary><strong>village_fire</strong></summary>

Use this after fire damage hits a villager. This can be player-caused if the damage source carries a player.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.event.village_fire.simple",
      "request": "question",
      "event_tags": [
        "village_fire"
      ],
      "text": "Smoke near a village is never just smoke."
    }
  ]
}
```

Expanded:

```json
{
  "lines": [
    {
      "id": "my_pack.event.village_fire.player_warning",
      "request": "apology",
      "player_event_tags": [
        "village_fire"
      ],
      "dispositions": [
        "rude",
        "hostile",
        "fearful"
      ],
      "requires_unapologized_remembered_harm": true,
      "text": "Fire follows choices. Do not pretend it wandered here alone.",
      "weight": 35
    }
  ]
}
```

</details>

<details>
<summary><strong>night_attack</strong></summary>

Use this after hostile activity at night, either a hostile attacking a villager or a hostile dying nearby.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.event.night_attack.simple",
      "request": "story",
      "event_tags": [
        "night_attack"
      ],
      "text": "Night brought trouble close again."
    }
  ]
}
```

Expanded:

```json
{
  "lines": [
    {
      "id": "my_pack.event.night_attack.cleric",
      "request": "village_event_report",
      "professions": [
        "cleric"
      ],
      "times": [
        "night",
        "morning"
      ],
      "event_tags": [
        "night_attack"
      ],
      "text": "I counted breaths after the attack. More than I feared, fewer than I wanted.",
      "weight": 25
    }
  ]
}
```

</details>

<details>
<summary><strong>raid</strong></summary>

Use this after raid harm or raid defense near the village.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.event.raid.simple",
      "request": "story",
      "event_tags": [
        "raid"
      ],
      "text": "When banners come over the hill, every door learns fear."
    }
  ]
}
```

Expanded:

```json
{
  "lines": [
    {
      "id": "my_pack.event.raid.weaponsmith_defense",
      "request": "village_defense_report",
      "professions": [
        "weaponsmith"
      ],
      "event_tags": [
        "raid"
      ],
      "player_event_tags": [
        "player_defended_raid"
      ],
      "requires_unreported_village_defense": true,
      "text": "You stood when the banners came. That is the sort of edge I respect.",
      "weight": 35
    }
  ]
}
```

</details>

<details>
<summary><strong>villager_death</strong></summary>

Use this after a villager or wandering trader dies nearby.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.event.villager_death.simple",
      "request": "question",
      "event_tags": [
        "villager_death"
      ],
      "text": "A quiet house feels louder than it should."
    }
  ]
}
```

Expanded:

```json
{
  "lines": [
    {
      "id": "my_pack.event.villager_death.family",
      "request": "question",
      "option": "ask_about_family",
      "event_tags": [
        "villager_death"
      ],
      "requires_known_deceased_family": true,
      "dispositions": [
        "cautious",
        "rude",
        "hostile"
      ],
      "text": "Do not make me say {deceased_family} like a lesson.",
      "weight": 30
    }
  ]
}
```

</details>

<details>
<summary><strong>villager_attacked</strong></summary>

Use this after any successful damage to a villager or wandering trader.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.event.villager_attacked.simple",
      "request": "question",
      "event_tags": [
        "villager_attacked"
      ],
      "text": "Someone was hurt. That changes the air."
    }
  ]
}
```

Expanded:

```json
{
  "lines": [
    {
      "id": "my_pack.event.villager_attacked.player_item",
      "request": "apology",
      "player_event_tags": [
        "villager_attacked"
      ],
      "player_items": [
        "#minecraft:swords"
      ],
      "player_item_slots": [
        "main_hand"
      ],
      "text": "Put {held_item} away before asking me to believe this is peace.",
      "weight": 25
    }
  ]
}
```

</details>

<details>
<summary><strong>player_attacked_villager</strong></summary>

Use this when the current player directly damaged a villager or wandering trader.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.event.player_attacked_villager.simple",
      "request": "apology",
      "player_event_tags": [
        "player_attacked_villager"
      ],
      "text": "I remember your hand in this."
    }
  ]
}
```

Expanded:

```json
{
  "lines": [
    {
      "id": "my_pack.event.player_attacked_villager.direct_hit",
      "request": "apology",
      "player_event_tags": [
        "player_attacked_villager"
      ],
      "conditions": [
        {
          "type": "memory",
          "kind": "recent_direct_hit"
        }
      ],
      "dispositions": [
        "rude",
        "hostile",
        "fearful"
      ],
      "text": "You struck me, then came back with words. Words are lighter than bruises.",
      "weight": 40
    }
  ]
}
```

</details>

<details>
<summary><strong>player_defended_village</strong></summary>

Use this when a player kills a hostile mob near village memory.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.event.player_defended_village.simple",
      "request": "question",
      "player_event_tags": [
        "player_defended_village"
      ],
      "text": "You kept trouble from our doors."
    }
  ]
}
```

Expanded:

```json
{
  "lines": [
    {
      "id": "my_pack.event.player_defended_village.trusted",
      "request": "village_defense_report",
      "player_event_tags": [
        "player_defended_village"
      ],
      "dispositions": [
        "friendly",
        "respectful"
      ],
      "requires_unreported_village_defense": true,
      "text": "I saw what you did out there. Trust grows faster when it has proof.",
      "weight": 30
    }
  ]
}
```

</details>

<details>
<summary><strong>player_defended_raid</strong></summary>

Use this when a player kills a raider during an active raid.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.event.player_defended_raid.simple",
      "request": "village_defense_report",
      "player_event_tags": [
        "player_defended_raid"
      ],
      "text": "You stood against the raid. We saw."
    }
  ]
}
```

Expanded:

```json
{
  "lines": [
    {
      "id": "my_pack.event.player_defended_raid.hostile",
      "request": "village_defense_report",
      "player_event_tags": [
        "player_defended_raid"
      ],
      "event_tags": [
        "raid"
      ],
      "dispositions": [
        "cautious",
        "rude",
        "hostile"
      ],
      "requires_unreported_village_defense": true,
      "text": "You helped us today. That does not erase everything, but it does matter.",
      "weight": 35
    }
  ]
}
```

</details>

<details>
<summary><strong>player_cured_villager</strong></summary>

Use this after a player cures a zombie villager. Lines using this memory can also use `{cured_villager}` and `{cured_villager_possessive}` when the cured-villager report is available.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.event.player_cured_villager.simple",
      "request": "cured_recognition",
      "player_event_tags": [
        "player_cured_villager"
      ],
      "text": "{cured_villager} walks in daylight because of you."
    }
  ]
}
```

Expanded:

```json
{
  "lines": [
    {
      "id": "my_pack.event.player_cured_villager.cleric",
      "request": "cured_recognition",
      "professions": [
        "cleric"
      ],
      "player_event_tags": [
        "player_cured_villager"
      ],
      "requires_unreported_cured_recognition": true,
      "dispositions": [
        "friendly",
        "respectful",
        "neutral"
      ],
      "text": "{cured_villager_possessive} second chance will be remembered here.",
      "weight": 35
    }
  ]
}
```

</details>

<details>
<summary><strong>golem_created</strong></summary>

This value is accepted by the parser, but current built-in handlers do not write it. It is mainly useful for future compatibility or custom code that calls the village memory system.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.event.golem_created.simple",
      "request": "story",
      "event_tags": [
        "golem_created"
      ],
      "text": "A new iron watcher changes how a village sleeps."
    }
  ]
}
```

Expanded:

```json
{
  "lines": [
    {
      "id": "my_pack.event.golem_created.armorer",
      "request": "village_event_report",
      "professions": [
        "armorer"
      ],
      "event_tags": [
        "golem_created"
      ],
      "dispositions": [
        "friendly",
        "respectful"
      ],
      "text": "Fresh iron, fresh duty. I hope whoever made it understands both.",
      "weight": 20
    }
  ]
}
```

</details>

<details>
<summary><strong>golem_killed</strong></summary>

Use this after an iron golem dies.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.event.golem_killed.simple",
      "request": "question",
      "event_tags": [
        "golem_killed"
      ],
      "text": "The village feels smaller without that iron step."
    }
  ]
}
```

Expanded:

```json
{
  "lines": [
    {
      "id": "my_pack.event.golem_killed.mason",
      "request": "village_event_report",
      "professions": [
        "mason"
      ],
      "event_tags": [
        "golem_killed"
      ],
      "dispositions": [
        "cautious",
        "rude",
        "hostile"
      ],
      "text": "When iron falls, stone walls start feeling thin.",
      "weight": 25
    }
  ]
}
```

</details>

<details>
<summary><strong>nearby_hostile_mob</strong></summary>

This value is accepted by the parser, but current built-in handlers do not write it. For normal gameplay, use `night_attack`, `player_defended_village`, or `raid` when those match your situation.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.event.nearby_hostile_mob.simple",
      "request": "story",
      "event_tags": [
        "nearby_hostile_mob"
      ],
      "text": "Something unfriendly came close enough to count."
    }
  ]
}
```

Expanded:

```json
{
  "lines": [
    {
      "id": "my_pack.event.nearby_hostile_mob.fletcher",
      "request": "village_event_report",
      "professions": [
        "fletcher"
      ],
      "event_tags": [
        "nearby_hostile_mob"
      ],
      "player_items": [
        "#minecraft:arrows",
        "minecraft:bow"
      ],
      "player_item_slots": [
        "inventory",
        "hotbar"
      ],
      "text": "Keep {player_item} close. Trouble already knows the way here.",
      "weight": 20
    }
  ]
}
```

</details>

<details>
<summary><strong>reputation_changed</strong></summary>

Use this after a villager's reputation toward a player changes.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.event.reputation_changed.simple",
      "request": "question",
      "player_event_tags": [
        "reputation_changed"
      ],
      "text": "People notice patterns."
    }
  ]
}
```

Expanded:

```json
{
  "lines": [
    {
      "id": "my_pack.event.reputation_changed.friendly",
      "request": "question",
      "option": "ask_about_reputation",
      "player_event_tags": [
        "reputation_changed"
      ],
      "dispositions": [
        "friendly",
        "respectful"
      ],
      "text": "You have been better to this place than most passing names.",
      "weight": 25
    }
  ]
}
```

</details>

<details>
<summary><strong>player_gave_loved_gift</strong></summary>

Use this when a player gives a gift that evaluates to `loved`.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.event.gift_loved.simple",
      "request": "question",
      "player_event_tags": [
        "player_gave_loved_gift"
      ],
      "text": "That gift will be talked about for a while."
    }
  ]
}
```

Expanded:

```json
{
  "lines": [
    {
      "id": "my_pack.event.gift_loved.friend",
      "request": "gift_preferences",
      "player_event_tags": [
        "player_gave_loved_gift"
      ],
      "dispositions": [
        "friendly",
        "respectful"
      ],
      "text": "A gift like that says you listened before you reached into your pack.",
      "weight": 30
    }
  ]
}
```

</details>

<details>
<summary><strong>player_gave_liked_gift</strong></summary>

Use this when a player gives a gift that evaluates to `liked`.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.event.gift_liked.simple",
      "request": "question",
      "player_event_tags": [
        "player_gave_liked_gift"
      ],
      "text": "That was a decent gift."
    }
  ]
}
```

Expanded:

```json
{
  "lines": [
    {
      "id": "my_pack.event.gift_liked.profession",
      "request": "gift_preferences",
      "professions": [
        "farmer",
        "fisherman"
      ],
      "player_event_tags": [
        "player_gave_liked_gift"
      ],
      "text": "Useful things are often kinder than shiny ones.",
      "weight": 24
    }
  ]
}
```

</details>

<details>
<summary><strong>player_gave_neutral_gift</strong></summary>

Use this when a player gives a gift that evaluates to `neutral`.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.event.gift_neutral.simple",
      "request": "question",
      "player_event_tags": [
        "player_gave_neutral_gift"
      ],
      "text": "A gift is still a choice, even when it misses."
    }
  ]
}
```

Expanded:

```json
{
  "lines": [
    {
      "id": "my_pack.event.gift_neutral.advice",
      "request": "gift_preferences",
      "player_event_tags": [
        "player_gave_neutral_gift"
      ],
      "gift_advice": "already_known",
      "text": "You already know that one lands softly, not warmly.",
      "weight": 22
    }
  ]
}
```

</details>

<details>
<summary><strong>player_gave_disliked_gift</strong></summary>

Use this when a player gives a gift that evaluates to `disliked`.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.event.gift_disliked.simple",
      "request": "question",
      "player_event_tags": [
        "player_gave_disliked_gift"
      ],
      "text": "Some gifts make silence work harder."
    }
  ]
}
```

Expanded:

```json
{
  "lines": [
    {
      "id": "my_pack.event.gift_disliked.cautious",
      "request": "gift_preferences",
      "player_event_tags": [
        "player_gave_disliked_gift"
      ],
      "dispositions": [
        "cautious",
        "rude",
        "hostile"
      ],
      "text": "If that was meant kindly, kindness needs better aim.",
      "weight": 28
    }
  ]
}
```

</details>

<details>
<summary><strong>player_gave_hated_gift</strong></summary>

Use this when a player gives a gift that evaluates to `hated`.

Simple:

```json
{
  "lines": [
    {
      "id": "my_pack.event.gift_hated.simple",
      "request": "question",
      "player_event_tags": [
        "player_gave_hated_gift"
      ],
      "text": "That gift did damage without a blade."
    }
  ]
}
```

Expanded:

```json
{
  "lines": [
    {
      "id": "my_pack.event.gift_hated.apology",
      "request": "apology",
      "player_event_tags": [
        "player_gave_hated_gift"
      ],
      "dispositions": [
        "rude",
        "hostile",
        "fearful"
      ],
      "requires_unapologized_remembered_harm": true,
      "text": "You cannot hand someone rot and then call the apology generous.",
      "weight": 35
    }
  ]
}
```

</details>

