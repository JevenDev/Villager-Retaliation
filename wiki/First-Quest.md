# First Quest Guide

This guide walks through the smallest complete quest that feels playable in game.

Important: a quest file does not create a Talk menu button by itself. A playable quest needs two files:

```text
data/<namespace>/quests/<module>/<quest>.json
data/<namespace>/dialogue_trees/<locale>/quests/<module>/<quest>.json
```

The quest file stores the rules. The dialogue tree lets the player accept it, ask for a reminder, and turn it in.

## What You Are Making

This example adds a farmer quest named `Bread Delivery`.

The player can:

1. Talk to a farmer and accept the quest.
2. Gather 16 bread.
3. Track the objective in the quest HUD and journal.
4. Return to the same quest giver and turn it in.

## File 1: Quest JSON

Create:

```text
data/my_pack/quests/village_supply/bread_delivery.json
```

```json
{
  "id": "my_pack:bread_delivery",
  "display": {
    "title": "Bread Delivery",
    "description": "Bring 16 bread to the village stores."
  },
  "tags": ["group.village_supply"],
  "offer": {
    "professions": ["minecraft:farmer"],
    "min_villager_level": "novice"
  },
  "objectives": [
    {
      "id": "bring_bread",
      "type": "item_check",
      "item": "minecraft:bread",
      "count": 16,
      "tracker": {
        "text": "Gather 16 bread for the village stores.",
        "complete_text": "The bread is packed and ready."
      }
    }
  ],
  "tracker": {
    "title": "Bread Delivery",
    "steps": {
      "proof": {
        "text": "Bring 16 bread back to the quest giver.",
        "progress": 0.7
      },
      "return": {
        "text": "Return to the quest giver with the bread.",
        "progress": 1.0
      }
    }
  },
  "rewards": {
    "experience": 60,
    "reputation": 5,
    "gossip_reputation": 2
  },
  "rules": {
    "repeatable": true,
    "completion_cooldown_days": 1
  }
}
```

What this file does:

| Section | Meaning |
| --- | --- |
| `id` | The stable quest id used by dialogue, commands, and saves |
| `display` | The title and description shown to players |
| `offer` | Which villagers are allowed to offer the quest |
| `objectives` | What the player must do |
| `tracker` | Text shown in the HUD and quest journal |
| `rewards` | What the player receives on turn-in |
| `rules` | Repeat, cooldown, abandonment, and locking behavior |

This file is enough for the quest system to understand the quest, but the player still cannot accept it. Add the dialogue tree next.

## File 2: Dialogue Tree

Create:

```text
data/my_pack/dialogue_trees/en_us/quests/village_supply/bread_delivery.json
```

```json
{
  "id": "my_pack:bread_delivery",
  "display": {
    "title": "Bread Delivery",
    "description": "Offer, reminder, and turn-in scene for Bread Delivery."
  },
  "entries": [
    {
      "id": "offer",
      "label": "Bread Delivery",
      "request": "question",
      "conditions": [
        { "type": "quest", "state": "available" }
      ],
      "start": "offer"
    },
    {
      "id": "reminder",
      "label": "About Bread Delivery",
      "request": "question",
      "conditions": [
        { "type": "quest", "state": "in_progress" }
      ],
      "start": "active_menu"
    },
    {
      "id": "turn_in",
      "label": "About Bread Delivery",
      "request": "question",
      "conditions": [
        { "type": "quest", "state": "ready" }
      ],
      "start": "turn_in"
    }
  ],
  "nodes": {
    "offer": {
      "lines": [
        "The bins are low. Sixteen bread would quiet a lot of worried stomachs."
      ],
      "responses": [
        {
          "id": "accept",
          "label": "I can help stock the larder.",
          "next": "start_quest"
        },
        {
          "id": "decline",
          "label": "Another time.",
          "next": "decline"
        }
      ]
    },
    "start_quest": {
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
      ],
      "end": true
    },
    "active_menu": {
      "lines": [
        "Bread Delivery is still open."
      ],
      "responses": [
        {
          "id": "details",
          "label": "Remind me what to do.",
          "next": "reminder_details"
        },
        {
          "id": "abandon",
          "label": "Abandon quest.",
          "next": "abandon_confirm"
        },
        {
          "id": "leave",
          "label": "Never mind.",
          "next": "leave_active"
        }
      ]
    },
    "reminder_details": {
      "actions": [
        {
          "type": "quest",
          "action": "remind",
          "lines": {
            "reminder": [
              "Bring 16 bread back to me. The tracker has the count."
            ],
            "unavailable": [
              "That bread delivery is not in your hands right now."
            ]
          }
        }
      ],
      "end": true
    },
    "turn_in": {
      "lines": [
        "If that pack smells like fresh bread, you may have saved me an argument."
      ],
      "responses": [
        {
          "id": "complete",
          "label": "Show what I brought.",
          "next": "complete_quest"
        },
        {
          "id": "abandon",
          "label": "Abandon quest.",
          "next": "abandon_confirm"
        },
        {
          "id": "leave",
          "label": "Never mind.",
          "next": "leave_turn_in"
        }
      ]
    },
    "complete_quest": {
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
      ],
      "end": true
    },
    "abandon_confirm": {
      "lines": [
        "Put Bread Delivery aside for now?"
      ],
      "responses": [
        {
          "id": "confirm",
          "label": "Abandon quest.",
          "next": "abandon_quest"
        },
        {
          "id": "cancel",
          "label": "Keep the quest.",
          "next": "leave_active"
        }
      ]
    },
    "abandon_quest": {
      "actions": [
        {
          "type": "quest",
          "action": "abandon",
          "lines": {
            "abandoned": [
              "I will clear the bread tally. Come back if the village still needs stocking."
            ],
            "unavailable": [
              "There is no bread tally here for me to close."
            ]
          }
        }
      ],
      "end": true
    },
    "decline": {
      "text": "Then I will keep counting crumbs and pretending it is planning.",
      "end": true
    },
    "leave_active": {
      "text": "Keep the bread close until you are ready.",
      "end": true
    },
    "leave_turn_in": {
      "text": "Count the loaves once more, then we can close this properly.",
      "end": true
    }
  }
}
```

What this file does:

| Part | Meaning |
| --- | --- |
| `entries` | Talk menu buttons that can appear when the villager is clicked |
| `conditions` | Decide which button appears for the current quest state |
| `nodes` | The conversation screens inside the tree |
| `responses` | Player buttons inside a node |
| `actions` | Start, remind, turn in, abandon, block, or write story facts |

## Why The Tree Has Three Entries

Most quests need three player-facing states:

| State | Use it when | Typical button |
| --- | --- | --- |
| `available` | The player can accept the quest now | `Bread Delivery` |
| `in_progress` | The quest is active but not ready | `About Bread Delivery` |
| `ready` | The quest can be turned in | `About Bread Delivery` |

Do not duplicate the quest's profession or skill requirements in the tree. If the quest file says only farmers can offer it, this is enough:

```json
{ "type": "quest", "state": "available" }
```

The quest condition asks the quest system whether all offer rules, parent requirements, cooldowns, branch locks, and completion limits allow the quest right now.

## Test It In Game

1. Put both files in your datapack.
2. Run `/reload`.
3. Talk to a farmer.
4. Choose `Bread Delivery`.
5. Accept the quest.
6. Press `J` to open the Quest Journal.
7. Gather 16 bread.
8. Return to the quest giver and choose `About Bread Delivery`.
9. Turn it in.

If the button does not appear, run:

```text
/villagerretaliation quest debug inspect my_pack:bread_delivery
```

The debug inspector shows saved state, availability, active conditions, issuer data, objective counters, cooldowns, and branch locks.

## Common Quest States

Use these in dialogue-tree conditions:

| State | Meaning |
| --- | --- |
| `available` | The quest can be started from this villager now |
| `in_progress` | The quest is active and not ready to turn in |
| `ready` | The quest is active and all required objectives are complete |
| `active` | The quest is active and visible, whether ready or not |
| `inactive` | The quest is active but paused or hidden by active conditions |
| `completed` | The player has completed it at least once |
| `abandoned` | The player abandoned it and it may be recoverable |
| `expired` | The quest timed out and may be recoverable |
| `branch_locked` | Another choice closed this path |
| `not_completed` | The player has not completed it |

For most quest trees, use only `available`, `in_progress`, and `ready`.

## Common Mistakes

| Symptom | Likely cause |
| --- | --- |
| Quest never appears in Talk menu | Missing dialogue tree entry with `{ "type": "quest", "state": "available" }` |
| Quest starts but cannot be turned in | Missing `ready` entry, missing `turn_in` action, or objective is not actually complete |
| Any villager offers the quest | `offer.professions` is missing or too broad |
| Quest appears for the wrong story branch | Missing `parent`, `offer.conditions`, or branch-lock rules |
| Tracker text is vague | Add `tracker.steps` or objective `tracker.text` |
| Player cannot find the quest giver | Keep `locked_to_villager: true` for personal favors; use `cross_villager_compatible: true` only when another villager should be able to continue the same quest |
| Advanced item objective highlights the wrong stack | The client highlights by item id; explain enchantment, durability, or custom-data requirements in tracker text |

## When To Add More Files

Start with only the two files above.

Add normal dialogue messages when you want reusable localized text:

```text
data/my_pack/dialogue/en_us/quests/village_supply/bread_delivery/messages/00_text.json
```

Add forced dialogue only when the quest needs an event-driven interruption, warning, confrontation, or scene outside the Talk menu:

```text
data/my_pack/forced_dialogue/quests/village_supply/bread_delivery.json
```

## Next Steps

After this quest works, read:

- [Quests](Quests.md) for every objective type, reward, rule, trigger, stage, and branch field.
- [Dialogue Trees](Dialogue-Trees.md) for larger branching scenes.
- [Dialogue And Quests](Dialogue-And-Quests.md) for module layout and file ownership.
- [Localization](Localization.md) when you are ready to replace inline English with message keys.
