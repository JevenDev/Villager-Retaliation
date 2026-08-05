# Example Packs

These examples are intentionally small. Use them as starter files, then add filters and entries as needed.

## Minimal Datapack Layout

```text
VillagerRetaliationExample/
  pack.mcmeta
  data/
    villagerretaliation/
      dialogue/
        en_us/
          examplepack_dialogue.json
      forced_dialogue/
        examplepack_events.json
      notifications/
        en_us/
          examplepack_notifications.json
      gifts/
        example_gifts.json
      profession_loot/
        example_loot.json
    examplepack/
      loot_table/
        villager/profession/alchemist/common.json
      story_structures/
        haunted_places.json
      story_biomes/
        crystal_biomes.json
```

`pack.mcmeta`:

```json
{
  "pack": {
    "pack_format": 48,
    "description": "Villager Retaliation example datapack"
  }
}
```

## Dialogue Example

```text
data/villagerretaliation/dialogue/en_us/examplepack_dialogue.json
```

```json
{
  "options": [
    {
      "id": "examplepack.ask_local_rumors",
      "label": "Ask Local Rumors",
      "type": "dialogue_option",
      "request": "story",
      "order": 30,
      "show_for_babies": false
    }
  ],
  "lines": [
    {
      "id": "examplepack.rumor.generic",
      "option": "examplepack.ask_local_rumors",
      "request": "story",
      "text": "Roads keep secrets. Villages keep better ones.",
      "weight": 10
    },
    {
      "id": "examplepack.low_rep_question",
      "request": "question",
      "reputation_levels": [
        "hostile",
        "despised",
        "feared"
      ],
      "text": "You will find fewer open doors here than you remember.",
      "weight": 20
    },
    {
      "id": "examplepack.share_story.haunted_keep",
      "request": "share_story",
      "option": "adult_share_story",
      "story_structure": "examplemod:haunted_keep",
      "text": "{target_article}. If you found it, walk home before dark.",
      "weight": 30
    }
  ],
  "messages": [
    {
      "id": "examplepack.sleep.broken_bed",
      "key": "sleep.broken_bed",
      "text": "That bed had a name in this house.",
      "weight": 15
    },
    {
      "id": "examplepack.gift.librarian.rare_book",
      "key": "examplepack.gift.librarian.rare_book",
      "text": "{gift_item}? This belongs near a reading lamp, not forgotten in a chest."
    }
  ]
}
```

## Event Tag Dialogue Example

Use event tags when a line should only appear after a recent village memory. The full tag list and per-tag dropdown examples are in [Event Tags](Event-Tags.md).

```json
{
  "lines": [
    {
      "id": "examplepack.raid.aftermath",
      "request": "village_event_report",
      "event_tags": [
        "raid"
      ],
      "text": "The banners are gone, but the village still hears them.",
      "weight": 20
    },
    {
      "id": "examplepack.raid.thanked_player",
      "request": "village_defense_report",
      "player_event_tags": [
        "player_defended_raid"
      ],
      "requires_unreported_village_defense": true,
      "text": "You stood with us when it mattered.",
      "weight": 30
    }
  ]
}
```

## Forced Dialogue Example

```text
data/villagerretaliation/forced_dialogue/examplepack_events.json
```

```json
{
  "entries": [
    {
      "id": "examplepack.strict_theft_warning",
      "trigger": "container_theft",
      "witness_radius": 10,
      "requires_line_of_sight": true,
      "initiate_dialogue": true,
      "reputation": -6,
      "line": "Hands off that {container}. I saw what you took.",
      "options": [
        {
          "id": "return_and_apologize",
          "label": "Apologize",
          "response": "Then prove it next time before the village has to ask.",
          "reputation": 2,
          "aggro": false,
          "order": 0
        },
        {
          "id": "trusted_warning",
          "label": "Accept warning",
          "response": "You have helped us before, so I will treat this as a mistake. Close it.",
          "reputation_levels": [
            "trusted",
            "respected",
            "revered",
            "royalty"
          ],
          "reputation": 1,
          "order": 1
        },
        {
          "id": "offer_payment",
          "label": "Offer payment",
          "response": "Payment does not make it yours, but it can make things right.",
          "reputation_levels": [
            "neutral",
            "suspicious"
          ],
          "take_items": {
            "items": [
              "minecraft:emerald"
            ],
            "count": 8,
            "destination": "villager_inventory",
            "overflow_destination": "drop_at_villager",
            "failure_response": "Do not offer emeralds you do not have.",
            "failure_reputation": -2,
            "failure_end_conversation": false
          },
          "reputation": 2,
          "order": 2
        },
        {
          "id": "talk_back",
          "label": "Talk back",
          "response": "Wrong answer.",
          "reputation_levels": [
            "hostile",
            "despised",
            "feared"
          ],
          "reputation": -6,
          "aggro": true,
          "order": 3
        }
      ]
    }
  ]
}
```

## Notifications Example

```text
data/villagerretaliation/notifications/en_us/examplepack_notifications.json
```

```json
{
  "notifications": [
    {
      "id": "examplepack.ambient.trusted_farmer",
      "trigger": "ambient.murmur",
      "text": "Good harvest follows good neighbors",
      "world_text_kind": "murmur",
      "professions": [
        "farmer"
      ],
      "reputation_levels": [
        "trusted",
        "respected",
        "revered",
        "royalty"
      ],
      "color": "#DCEBA6",
      "weight": 20
    },
    {
      "id": "examplepack.trade.refused.hostile",
      "trigger": "trade.refused",
      "text": "Not today",
      "world_text_kind": "negative",
      "reputation_levels": [
        "hostile",
        "despised",
        "feared"
      ],
      "color": "red"
    },
    {
      "id": "examplepack.combat.player_killed",
      "trigger": "combat.player_killed",
      "text": "{player} should have listened",
      "world_text_kind": "alert",
      "reputation_levels": [
        "hostile",
        "despised",
        "feared"
      ],
      "color": "#FFD166",
      "weight": 20
    }
  ]
}
```

## Gifts Example

```text
data/villagerretaliation/gifts/example_gifts.json
```

```json
{
  "preferences": [
    {
      "professions": [
        "librarian"
      ],
      "reaction": "loved",
      "items": [
        "minecraft:enchanted_book",
        "minecraft:name_tag"
      ],
      "response_key": "examplepack.gift.librarian.rare_book",
      "priority": 20
    },
    {
      "reaction": "disliked",
      "items": [
        "minecraft:cobweb"
      ],
      "reputation_per_item": -1
    }
  ],
  "rewards": [
    {
      "professions": [
        "librarian"
      ],
      "reputation_levels": [
        "revered",
        "royalty"
      ],
      "item": "minecraft:book",
      "min_count": 2,
      "max_count": 5,
      "weight": 10
    }
  ]
}
```

## Profession Loot Example

```text
data/villagerretaliation/profession_loot/example_loot.json
```

```json
{
  "tables": [
    {
      "id": "examplepack.alchemist.common",
      "professions": [
        "examplemod:alchemist"
      ],
      "loot_table": "examplepack:villager/profession/alchemist/common",
      "chance": "always"
    }
  ]
}
```

```text
data/examplepack/loot_table/villager/profession/alchemist/common.json
```

```json
{
  "type": "minecraft:entity",
  "pools": [
    {
      "rolls": 1,
      "bonus_rolls": 0,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "minecraft:amethyst_shard",
          "functions": [
            {
              "function": "minecraft:set_count",
              "count": {
                "type": "minecraft:uniform",
                "min": 1,
                "max": 3
              }
            }
          ]
        }
      ]
    }
  ]
}
```

## Story Structure Example

```text
data/examplepack/story_structures/haunted_places.json
```

```json
{
  "radius": 128,
  "entries": [
    {
      "structure": "examplemod:haunted_keep",
      "name": "Haunted Keep"
    }
  ]
}
```

## Story Biome Example

```text
data/examplepack/story_biomes/crystal_biomes.json
```

```json
{
  "entries": [
    {
      "biome": "examplemod:crystal_marsh",
      "name": "Crystal Marsh"
    }
  ]
}
```

## Minimal Resource Pack Layout

```text
VillagerRetaliationResourceExample/
  pack.mcmeta
  assets/
    minecraft/
      textures/entity/villager/villager.png
      textures/entity/wandering_trader.png
    villagerretaliation/
      textures/entity/villager/villager.png
      textures/entity/wandering_trader/wandering_trader.png
      models/entity/villager/combat_villager.json
```

Use the built-in `combat_villager.json` as the safest starting point, then change part dimensions or decorative children gradually.
