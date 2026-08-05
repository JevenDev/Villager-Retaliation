# First Quest Guide

This guide walks through the smallest complete quest that feels playable in game.

For new packs, use one quest module v2 file first. Add external dialogue trees later only when the scene grows large or needs separate ownership.

## What You Are Making

This example adds a farmer quest named `Bread Delivery`.

The player can:

1. Talk to a farmer and accept the quest.
2. Gather 16 bread.
3. Track the objective in the quest HUD and journal.
4. Return to the same quest giver and turn it in.

## File: Quest Module V2

Create:

```text
data/my_pack/quests/village_supply/bread_delivery.json
```

```json
{
  "schema": "villagerretaliation:quest/v2",
  "id": "my_pack:bread_delivery",
  "metadata": {
    "title": "Bread Delivery",
    "description": "Bring 16 bread to the village stores.",
    "questline": "village_supply",
    "tags": ["group.village_supply"]
  },
  "provider": {
    "type": "villagerretaliation:villager",
    "filters": {
      "professions": ["minecraft:farmer"],
      "min_villager_level": "novice"
    }
  },
  "availability": {
    "repeatable": true,
    "completion_cooldown_days": 1,
    "locked_to_villager": true,
    "cross_villager_compatible": false,
    "abandonment": "allow_repickup",
    "consume_on_completion": true
  },
  "entry_stage": "gather",
  "stages": [
    {
      "id": "gather",
      "objectives": [
        {
          "id": "bring_bread",
          "type": "item_check",
          "item": "minecraft:bread",
          "count": 16,
          "tracker": {
            "text": "Bring 16 bread back to the quest giver.",
            "complete_text": "The bread is packed and ready.",
            "show_progress": true,
            "progress": 0.75
          }
        }
      ],
      "dialogue": {
        "offer": {
          "label": "Bread Delivery",
          "request": "question",
          "order": -20,
          "show_for_babies": false,
          "lines": [
            "The bins are low. Sixteen bread would quiet a lot of worried stomachs."
          ],
          "responses": [
            {
              "id": "accept",
              "label": "I can help stock the larder.",
              "scene": "start_quest"
            },
            {
              "id": "decline",
              "label": "Another time.",
              "scene": "decline"
            }
          ]
        },
        "reminder": {
          "label": "About Bread Delivery",
          "request": "question",
          "order": -20,
          "show_for_babies": false,
          "lines": [
            "Bread Delivery is still open. The tracker has the count."
          ],
          "responses": [
            {
              "id": "leave",
              "label": "I'll keep looking.",
              "scene": "end"
            }
          ]
        },
        "turn_in": {
          "label": "About Bread Delivery",
          "request": "question",
          "order": -20,
          "show_for_babies": false,
          "lines": [
            "If that pack smells like fresh bread, you may have saved me an argument."
          ],
          "responses": [
            {
              "id": "complete",
              "label": "Show what I brought.",
              "scene": "complete_quest"
            },
            {
              "id": "leave",
              "label": "Not yet.",
              "scene": "end"
            }
          ]
        }
      },
      "scenes": [
        {
          "id": "start_quest",
          "actions": [
            {
              "type": "quest",
              "action": "start",
              "lines": {
                "started": [
                  "Good. Bring the bread back when the count is ready."
                ],
                "unavailable": [
                  "The larder is not asking you for bread right now."
                ]
              }
            }
          ]
        },
        {
          "id": "complete_quest",
          "actions": [
            {
              "type": "quest",
              "action": "turn_in",
              "lines": {
                "completed": [
                  "Good. A full shelf makes brave talk sound less hollow."
                ],
                "missing_objectives": [
                  "Bread Delivery is still short. The tracker has the exact count."
                ],
                "unavailable": [
                  "This bread delivery is not ready to close yet."
                ]
              }
            }
          ]
        },
        {
          "id": "decline",
          "text": "Then I will keep counting crumbs and pretending it is planning."
        },
        {
          "id": "end",
          "text": "Keep the bread close until you are ready."
        }
      ]
    }
  ],
  "rewards": {
    "experience": 60,
    "reputation": 5,
    "gossip_reputation": 2
  },
  "ui": {
    "tracker_text": "Bring 16 bread.",
    "icon": "minecraft:bread",
    "color": "#DCEBA6"
  }
}
```

What this file does:

| Section | Meaning |
| --- | --- |
| `schema` | Selects quest module v2 |
| `id` | The stable quest id used by dialogue, commands, saves, and overrides |
| `metadata` | Title, description, questline, and tags |
| `provider` | Which villagers can offer the quest |
| `availability` | Repeat, cooldown, abandonment, and locking behavior |
| `entry_stage` | The first stage |
| `stages[].objectives` | What the player must do |
| `stages[].dialogue` | Offer, reminder, and turn-in Talk menu scenes |
| `stages[].scenes` | Action scenes reached from response buttons |
| `rewards` | What the player receives on turn-in |
| `ui` | Tracker text, icon, and color |

## Validation And Diagnostics

The repository validator accepts a quest file directly:

```text
node tools/validate-dialogue-data.mjs --quest path/to/data/my_pack/quests/village_supply/bread_delivery.json
```

Runtime diagnostics are available through:

```text
/villagerretaliation datapack diagnostics
/villagerretaliation quest debug providers
/villagerretaliation quest debug why_available my_pack:bread_delivery <provider_name>
/villagerretaliation quest debug inspect my_pack:bread_delivery
```

The debug inspector reports saved state, availability, active conditions, issuer data, objective counters, cooldowns, current stage, and branch locks.

## Common Mistakes

| Symptom | Likely cause |
| --- | --- |
| Quest never appears in Talk menu | Missing `schema`, wrong path, bad provider filters, or availability gates fail |
| Quest starts but cannot be turned in | Objective is not complete, turn-in scene is missing, or turn-in action is unavailable |
| Any villager offers the quest | `provider.filters.professions` is missing or too broad |
| Quest appears for the wrong story branch | Missing `metadata.parent`, `availability.conditions`, or branch-lock rules |
| Tracker text is vague | Add `ui.tracker_text` or objective `tracker.text` |
| Player cannot find the quest giver | Keep `locked_to_villager: true` for personal favors. Use `cross_villager_compatible: true` only when another villager should continue the same quest |
| Advanced item objective highlights the wrong stack | The client highlights by item id. Explain enchantment, durability, or custom-data requirements in tracker text |

## When To Add More Files

Start with the one quest module file above.

Add a normal dialogue message file when you want reusable localized text:

```text
data/my_pack/dialogue/en_us/quests/village_supply/bread_delivery/messages/00_text.json
```

Add an external dialogue tree only when the quest scene is too large for the quest file or another pack should own that scene:

```text
data/my_pack/dialogue_trees/en_us/quests/village_supply/bread_delivery.json
```

Add forced dialogue only when the quest needs an event-driven interruption, warning, confrontation, or scene outside the Talk menu:

```text
data/my_pack/forced_dialogue/quests/village_supply/bread_delivery.json
```

## Legacy V1 Note

Older v1 quests still work with a quest file plus a matching dialogue tree. Do not rewrite a working v1 pack just to load it on current builds. Use v2 when creating new quests, when migrating intentionally, or when you want a simple quest to be playable from one file.

## Next Steps

- [Quests](Quests.md) covers stages, transitions, branches, targets, forced/external scenes, diagnostics, and v1 compatibility.
- [Dialogue Trees](Dialogue-Trees.md) covers extracted authored scenes.
- [Dialogue And Quests](Dialogue-And-Quests.md) covers file ownership and extraction paths.
- [Localization](Localization.md) covers replacing inline English with message keys.
