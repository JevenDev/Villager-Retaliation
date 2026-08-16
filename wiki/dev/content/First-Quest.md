# First Quest Guide

This guide walks through the smallest complete quest that feels playable in game.

For new packs, start with one owner bundle. The inline JSON in this guide is browser-builder input; beta.13 export replaces player-facing strings with localized references and writes exhaustive English beside `quest.json`.

## What You Are Making

This example adds a farmer quest named `Bread Delivery`.

The player can:

1. Talk to a farmer and accept the quest.
2. Gather 16 bread.
3. Track the objective in the quest HUD and journal.
4. Return to the same quest giver and turn it in.

## Builder Input And Exported Files

Create:

```text
data/my_pack/quests/village_supply/bread_delivery/quest.json
data/my_pack/quests/village_supply/bread_delivery/locales/en_us.json
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
| `ui` | Tracker text, icon, and optional title/outline colors |

## Validation And Diagnostics

The repository validator accepts a quest file directly:

```text
node tools/validate-dialogue-data.mjs --quest path/to/data/my_pack/quests/village_supply/bread_delivery/quest.json
```

Runtime diagnostics are available through:

```text
/vr admin datapack diagnostics
/vr admin quest providers
/vr admin quest whyAvailable my_pack:bread_delivery <provider>
/vr admin quest inspect my_pack:bread_delivery
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
| Advanced item objective highlights the wrong stack | Ensure the active objective and synchronized tracker were refreshed after the datapack reload; highlights now use components, enchantments, durability, and custom-data requirements |

## When To Add More Files

The exported owner bundle always includes `quest.json` and exhaustive `locales/en_us.json`. Add `locales/<locale>.json` when you have translated messages; it may be partial and falls back per key to effective English.

Add a private persistent scene, encounter, or wrapped reward only when the quest behavior needs one:

```text
data/my_pack/quests/village_supply/bread_delivery/scenes/<scene>.json
data/my_pack/quests/village_supply/bread_delivery/encounters/<encounter>.json
data/my_pack/quests/village_supply/bread_delivery/rewards/<reward>.json
```

Keep offer, reminder, turn-in, response, and branch structure in `quest.json`. Put reusable companion definitions or absolute shared messages under `quests/_shared/` instead of referencing another quest's private files.

Forced dialogue is a separate global system for event-driven interruptions, warnings, or confrontations outside the Talk menu. A quest may call a stable forced-dialogue ID, but that definition is not part of the owner bundle and cannot replace structural quest dialogue.

## Legacy Layout Note

Beta.13 diagnoses loose v1 and v2 quest files and old companion roots as unsupported; it does not load them. Convert them offline to an owner bundle before changing the pack target.

## Next Steps

- [Quests](Quests.md) covers stages, transitions, branches, targets, bundle companions, forced-dialogue actions, and diagnostics.
- [Dialogue Trees](Dialogue-Trees.md) covers standalone branching conversations and the quest ownership boundary.
- [Dialogue And Quests](Dialogue-And-Quests.md) covers bundle transactions, structural dialogue, and locale ownership.
- [Localization](Localization.md) covers locale catalogs and message-key resolution.
