# Quests

Quest module v2 is the preferred shape for new quest datapacks. A v2 module can define the provider, availability, lifecycle, stages, objectives, dialogue, responses, transitions, events, rewards, and tracker UI in one file.

Legacy v1 quest files are still supported. Keep existing v1 packs working, but use v2 for new simple quests and for migrations where you want dialogue and quest state to live together.

Each quest run receives a saved unique ID before its first actions run. A solo run belongs to one player. A party run uses one shared ID for the party. Persistent scenes use this saved ID so a reload resumes the same scene instead of starting a duplicate.

## Paths

```text
data/<namespace>/quests/<quest>.json
data/<namespace>/quests/<module>/<quest>.json
```

The folder path is for organization and overrides. It does not create a questline by itself.

## One-File Quest

This is a complete playable quest. It needs no external dialogue tree.

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

Validate standalone quest examples with:

```text
node tools/validate-dialogue-data.mjs --quest path/to/quest.json
```

## Main Parts

| Section | Purpose |
| --- | --- |
| `schema` | Must be `villagerretaliation:quest/v2` for v2 modules |
| `id` | Stable quest resource id used by saves, commands, dialogue, and overrides |
| `metadata` | Player-facing title, description, questline, tags, and legacy parent convenience |
| `provider` | Who can offer or own the quest |
| `availability` | Ordered prerequisites, repeat limits, abandonment, cooldowns, locking, and active gates |
| `target` | Optional world target such as a structure search |
| `entry_stage` | Authoritative first stage id. A later stage named `started` does not override it |
| `stages` | Objectives, stage-local dialogue, responses, scenes, events, and UI |
| `events` | Quest-level triggers that run while the quest exists |
| `rewards` | XP, reputation, gossip, loot, memory events, or reward actions |
| `ui` | Tracker text, icon, progress, placeholders, color, and priority |
| `external_scenes` | Optional external dialogue scene resources used by this module |

## Quest Tag Taxonomy

`metadata.tags` classifies quests for journal filters, quest-board pools, authoring tools, diagnostics, and content tests. Tags describe a quest; they do not replace authoritative fields such as `availability.repeatable`, `completion_scope`, `ui.hidden`, `ui.priority`, provider filters, or objective definitions.

## Journal Presentation

Root `ui` fields are live journal data, not authoring-only hints:

```json
"ui": {
  "icon": "minecraft:filled_map",
  "color": "#d4a35a",
  "priority": 25,
  "hidden": false,
  "tracker_text": "Survey the old road."
}
```

`priority` sorts otherwise equivalent journal entries, `color` tints their titles, and `hidden` suppresses the quest from the journal and HUD. The synchronized journal also carries `metadata.questline` and `metadata.tags`; press `/` in the journal to search titles, descriptions, objectives, questlines, and tags.

Active quests with `availability.expiration.after_ticks` show a live remaining-time countdown. A located structure or location objective publishes its saved dimension and coordinates as a waypoint in both the selected journal entry and tracked HUD, including live distance while the player is in the same dimension. Completed journal-history entries show how long ago that run completed.

## Quest Pools

Quest pools turn quest tags or explicit quest IDs into bounded rotating offers. Put pool resources under `data/<namespace>/quest_pools/`. A pool only controls quests that match one of its selectors; quests not claimed by any pool keep their normal availability.

```json
{
  "schema": "villagerretaliation:quest_pool/v1",
  "id": "my_pack:daily_commissions",
  "scope": "village",
  "refresh_days": 1,
  "max_offers": 3,
  "anti_repeat_rotations": 2,
  "any_tags": ["pool.daily", "pool.commission"],
  "exclude_tags": ["difficulty.extreme"],
  "weights": {
    "my_pack:urgent_repairs": 4
  }
}
```

Selection is deterministic for the pool, scope key, and refresh epoch. `scope` accepts `player`, `village`, `provider`, or `world`. `quests`, `any_tags`, and `all_tags` select candidates; `exclude_quests` and `exclude_tags` remove candidates. Selection is weighted without replacement, and `anti_repeat_rotations` avoids recent selections when the pool has enough alternatives.

## Generic Criterion Objectives

Use a `criterion` objective when another gameplay system should advance a quest without adding a dedicated objective type. The namespaced criterion identifies the event, `match` contains exact string/number/boolean filters, and the normal `item`, entity selectors, location, dimension, and `count` fields add optional constraints.

```json
{
  "id": "forge_blades",
  "type": "criterion",
  "criterion": "villagerretaliation:crafted",
  "item": "minecraft:iron_sword",
  "match": {
    "item": "minecraft:iron_sword"
  },
  "count": 3
}
```

Built-in criteria are `villagerretaliation:crafted`, `villagerretaliation:smelted`, `villagerretaliation:entity_interacted`, `villagerretaliation:damage_dealt`, and `villagerretaliation:dimension_changed`. Their match keys include `item`, `entity`, `damage_type`, `from`, and `to` as appropriate.

Other mods can publish their own namespaced criteria through `QuestCriterionApi.trigger`. Supplying an item or entity lets datapacks reuse the standard item, entity type/tag, dimension, and location matching rules. Match data is an open string map, so integrations can evolve independently of this mod's quest schema.

Built-in quests use these families:

| Family | Values | Use |
| --- | --- | --- |
| `group.*` | Questline or umbrella grouping | Catalog organization and related-content searches |
| `role.*` | `story`, `side`, `request`, `tutorial` | Journal sections, offer priority, and onboarding |
| `activity.*` | `gather`, `deliver`, `combat`, `explore`, `build`, `trade`, `social`, `choice` | Multi-select gameplay filters and player preferences |
| `destination.*` | `village`, `overworld`, `nether`, `end`, plus optional `remote` | Travel planning and dimension filters |
| `tier.*` | `early`, `mid`, `late`, `endgame` | World-progression recommendations |
| `difficulty.*` | `easy`, `normal`, `hard`, `extreme` | Danger and complexity guidance |
| `commitment.*` | `quick`, `standard`, `expedition` | Expected time and travel commitment |
| `theme.*` | `community`, `defense`, `mystery`, `craftsmanship`, `exploration` | Curated collections and thematic search |
| `feature.*` | `branching`, `scene`, `encounter`, `recoverable` | Special presentation, tooling, and regression-test coverage |
| `party.*` | `recommended`, `challenge` | Advisory group-play guidance without changing completion scope |
| `pool.*` | `commission`, `daily`, `quest_board` | Eligibility for rotating or curated offer pools |

Use the full prefix in JSON:

```json
"tags": [
  "group.village_supply",
  "role.request",
  "activity.deliver",
  "activity.gather",
  "destination.village",
  "tier.early",
  "difficulty.easy",
  "commitment.quick",
  "theme.community",
  "pool.daily",
  "pool.quest_board"
]
```

For built-in content:

- use exactly one `group.*`, `role.*`, `tier.*`, `difficulty.*`, and `commitment.*` tag
- use exactly one primary destination; add `destination.remote` when travel is a material part of the quest
- use one or more `activity.*` and `theme.*` tags when multiple descriptions apply
- use `feature.*`, `party.*`, and `pool.*` only when the corresponding capability or curation rule applies
- keep `role.tutorial`, `activity.social`, and `feature.recoverable` available for future content instead of applying them to unrelated quests

Datapacks may add their own tags. Avoid redefining the documented built-in prefixes with different meanings, and keep mechanics in their dedicated fields so metadata cannot drift from runtime behavior.

## Dialogue And Scenes

Stage `dialogue` slots normally use these names:

| Slot | When it appears |
| --- | --- |
| `offer` | Quest can be started |
| `reminder` | Quest is active but not ready |
| `turn_in` | Objectives are complete |
| `already_completed` | Player already completed a non-repeatable quest |
| `unavailable` | Provider is known but availability gates fail |
| `inactive` | Accepted quest is paused by active conditions |
| `missing_target`, `missing_proof`, `locate_failed` | Target/proof helper states |

Inline scenes stay inside the quest module. Use `external` or `external_scene` only when the scene is large, shared, localized separately, or deliberately owned by another datapack resource.

## Objective Composition And Bonuses

Stages use `all` completion by default. Set `completion.mode` to `any` when one predicate is enough, or `at_least` with `count` for k-of-n goals. Both objective references and condition predicates in `complete_when` participate in the count.

```json
{
  "complete_when": ["secure_gate", "rescue_smith", "recover_ledger"],
  "completion": { "mode": "at_least", "count": 2 },
  "bonuses": [
    {
      "id": "save_everyone",
      "when": ["secure_gate", "rescue_smith", "recover_ledger"],
      "mode": "all",
      "actions": [
        { "type": "experience", "amount": 100 },
        { "type": "reputation", "amount": 5 }
      ]
    }
  ]
}
```

Bonus outcomes are one-shot per quest run and stage. Their claimed IDs are saved before actions run, so reconnects and reloads cannot duplicate rewards. A bonus supports the same `all`, `any`, and `at_least` modes and can use any normal action type.

## Transition Rules

Keep each response to one transition source. Pick one of:

- direct response fields such as `next`, `stage`, `scene`, `complete`, `abandon`, or `fail`
- a `transition` object with `stage`, `scene`, `response`, `complete`, `abandon`, or `fail`
- a transition action such as `quest_transition`

Do not combine direct transition fields with a transition action on the same response. Put side effects, such as `set_variable`, `notification`, or `reputation`, in `actions`, then put the single stage or scene move in `transition`.

`fail` and `abandon` are different terminal outcomes. Failure records `FAILED`, runs only `lifecycle.on_fail`, stores a normalized failure code and time, and never grants completion rewards or increments completion/abandonment counts. Voluntary abandonment records `ABANDONED` (or `CONSUMED` when authored that way) and runs only `lifecycle.on_abandon`.

## Prerequisites And Restart Rules

Use `prerequisite_cooldown` (or its `_ticks`, `_seconds`, and `_days` forms) when a follow-up quest should wait after its prerequisites are completed. The cooldown is checked against every prerequisite, so the quest unlocks only after the most recently completed prerequisite has aged past the configured duration.

Put every required quest in `availability.prerequisites`. The list is ordered for journal/debug presentation and every entry must be completed. `metadata.parent` remains a singular compatibility and organization field for older content.

Quest module v2 also carries the full runtime rule set used by v1. Put active-state gates, expiration policy, and branch exclusion under `availability`:

```json
"availability": {
  "active": {
    "conditions": [{ "type": "quest_fact", "tag": "my_pack:road_open" }],
    "hide_when_unmet": false,
    "pause_progress_when_unmet": true
  },
  "expiration": {
    "after_days": 3,
    "consume": false,
    "allow_repickup": true,
    "notify": true
  },
  "branch": {
    "exclusive_group": "my_pack:route_choice",
    "exclusive_on": "completed",
    "blocks_on_completion": ["my_pack:other_route"]
  }
}
```

`complete_when` may mix objective references with condition predicates. Objective UI also accepts `tracker_complete_text` and `tracker_complete_text_key`, and reward memory events retain `memory_scope`. The v1-to-v2 migration tool preserves these rules, stage tracker steps, and objective completion copy.

## Definition Revisions And Live-Save Migration

Increment `metadata.revision` whenever an update changes stage or objective identity for a quest that players may already have active. The migration policy is applied once when a save first sees the newer revision and the result is persisted for audit/debug output.

```json
"metadata": {
  "revision": 3,
  "migration": {
    "active_policy": "keep",
    "stage_aliases": {
      "find_ruins": "survey_ruins"
    },
    "objective_aliases": {
      "find_ruins.old_map": "survey_ruins.map_fragment"
    }
  }
}
```

Policies are:

- `keep`: remap aliases, retain progress, and safely move to the entry stage only if the saved stage no longer exists
- `reset_stage`: remap aliases, then clear objective and bonus progress owned by the current stage
- `restart`: keep the quest run and provider binding but clear objective, target, and bonus progress and return to the entry stage
- `fail`: terminate the active quest with a revision-specific failure code and run the normal failure integrations

Legacy saves without a stored revision adopt the current revision without destructive migration. A datapack rollback never re-applies an older revision over a newer persisted one. For v1 files, put `revision` and `migration` at the quest root; the v1-to-v2 migration tool moves them into `metadata`.

```json
{
"availability": {
  "prerequisites": [
    "my_pack:first_steps",
    "my_pack:earn_their_trust",
    "my_pack:find_the_map"
  ]
}
}
```

Failed quests can restart only when `repeatable` is true. `max_starts`, `max_completions`, provider locking, and completion scope still apply. Failure does not consume the quest by itself. Abandoned quests continue to follow `abandonment`, abandonment cooldown, and `consume_on_abandonment`.

## Missing Providers And Rebind

Active progress remains in the journal using the saved provider name, profession, location, and UUID when the live villager is gone. The journal's **Abandon quest** action works without the live provider. If abandonment or expiration has an authored lifecycle hook, the runtime persists that event instead of dropping its provider-bound actions. It replays the event once when the original provider is live again, or immediately after an operator supplies a compatible replacement. Turning in through another matching provider is allowed only with `cross_villager_compatible: true`. The runtime never chooses a nearby villager automatically.

Operators can explicitly repair a missing binding with:

```text
/villagerretaliation quest debug rebind <quest_id> <provider_name>
```

The command refuses a rebind while the current provider is live, verifies the provider type and authored filters, retains the previous snapshot in save history, and reports the accepted or rejected audit result. A terminal quest can be rebound only while it has deferred lifecycle work. The rebind consumes that work after one dispatch without reopening the quest. The debug inspector lists pending lifecycle events alongside provider history.

## Branch Example

This module records a route choice, moves to the chosen stage, and completes from either branch.

```json
{
  "schema": "villagerretaliation:quest/v2",
  "id": "my_pack:choose_supply_route",
  "metadata": {
    "title": "Choose Supply Route",
    "description": "Choose how the village will move supplies.",
    "questline": "village_supply",
    "tags": ["group.village_supply"]
  },
  "provider": {
    "type": "villagerretaliation:villager",
    "filters": {
      "professions": ["minecraft:cartographer"]
    }
  },
  "availability": {
    "repeatable": false,
    "max_completions": 1,
    "locked_to_villager": true
  },
  "entry_stage": "choose_route",
  "stages": [
    {
      "id": "choose_route",
      "objectives": [
        {
          "id": "choose_route",
          "type": "choice",
          "choices": ["river", "ridge"],
          "tracker": {
            "text": "Choose a supply route.",
            "complete_text": "Route chosen: {objective_choice_value}."
          }
        }
      ],
      "dialogue": {
        "offer": {
          "label": "Choose Supply Route",
          "request": "question",
          "lines": [
            "The village needs a safer supply route. River or ridge,"
          ],
          "responses": [
            {
              "id": "river",
              "label": "Use the river.",
              "actions": [
                {
                  "type": "set_variable",
                  "scope": "quest",
                  "key": "choice",
                  "value": "river"
                }
              ],
              "transition": {
                "stage": "river_route"
              }
            },
            {
              "id": "ridge",
              "label": "Use the ridge.",
              "actions": [
                {
                  "type": "set_variable",
                  "scope": "quest",
                  "key": "choice",
                  "value": "ridge"
                }
              ],
              "transition": {
                "stage": "ridge_route"
              }
            }
          ]
        }
      }
    },
    {
      "id": "river_route",
      "objectives": [],
      "dialogue": {
        "turn_in": {
          "label": "River Route",
          "request": "question",
          "lines": ["The river road will move quietly."],
          "responses": [
            {
              "id": "complete",
              "label": "Mark the river route.",
              "complete": true
            }
          ]
        }
      }
    },
    {
      "id": "ridge_route",
      "objectives": [],
      "dialogue": {
        "turn_in": {
          "label": "Ridge Route",
          "request": "question",
          "lines": ["The ridge road will keep watch over the valley."],
          "responses": [
            {
              "id": "complete",
              "label": "Mark the ridge route.",
              "complete": true
            }
          ]
        }
      }
    }
  ],
  "ui": {
    "tracker_text": "Choose a route.",
    "icon": "minecraft:map"
  }
}
```

## Structure Target Example

Root `target` fields define a structure search, discovery radius, and proof item. Stages can combine a visit objective with a proof-item objective.

```json
{
  "schema": "villagerretaliation:quest/v2",
  "id": "my_pack:trail_marker",
  "metadata": {
    "title": "Trail Marker",
    "description": "Find nearby Trail Ruins and return with a brush.",
    "tags": ["group.old_roads"]
  },
  "provider": {
    "type": "villagerretaliation:villager",
    "filters": {
      "professions": ["minecraft:cartographer", "minecraft:mason"]
    }
  },
  "availability": {
    "repeatable": false,
    "max_completions": 1,
    "locked_to_villager": true
  },
  "target": {
    "structure": "minecraft:trail_ruins",
    "dimension": "minecraft:overworld",
    "search_radius": 192,
    "discovery_radius": 96,
    "proof_item": "minecraft:brush"
  },
  "entry_stage": "survey",
  "stages": [
    {
      "id": "survey",
      "objectives": [
        {
          "id": "visit_ruins",
          "type": "structure_visit",
          "structure": "minecraft:trail_ruins",
          "tracker": {
            "text": "Find the Trail Ruins near {target_x}, {target_z}.",
            "complete_text": "You found the old road."
          }
        },
        {
          "id": "bring_brush",
          "type": "item_check",
          "item": "minecraft:brush",
          "count": 1,
          "tracker": {
            "text": "Bring a brush back from the ruins.",
            "complete_text": "The brush is ready."
          }
        }
      ],
      "complete_when": ["visit_ruins", "bring_brush"],
      "dialogue": {
        "offer": {
          "label": "Trail Marker",
          "request": "question",
          "lines": ["The old road left a mark under the dust."],
          "responses": [
            {
              "id": "accept",
              "label": "Mark the ruins.",
              "scene": "start_quest"
            }
          ]
        },
        "turn_in": {
          "label": "Trail Marker",
          "request": "question",
          "lines": ["You found the mark and brought a brush."],
          "responses": [
            {
              "id": "complete",
              "label": "Hand over the notes.",
              "complete": true
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
                "started": ["The ruins should be near {target_x}, {target_z}."],
                "locate_failed": ["The old road is hiding from the map today."]
              }
            }
          ]
        }
      ]
    }
  ],
  "rewards": {
    "experience": 80,
    "reputation": 6
  },
  "ui": {
    "tracker_text": "Find the Trail Ruins.",
    "icon": "minecraft:brush"
  }
}
```

## Forced Or External Scene Example

Use external scenes when another file owns a long conversation. Use `forced_dialogue` actions when the quest needs an event-driven locked scene. These actions need live player and provider context. If the quest giver is unloaded, the runtime records diagnostics and waits until it can safely run the live action.

```json
{
  "schema": "villagerretaliation:quest/v2",
  "id": "my_pack:storm_warning",
  "metadata": {
    "title": "Storm Warning",
    "description": "Ask a cleric about a storm omen.",
    "questline": "lost_civilization",
    "tags": ["group.lost_civilization"]
  },
  "provider": {
    "type": "villagerretaliation:villager",
    "filters": {
      "professions": ["minecraft:cleric"]
    }
  },
  "availability": {
    "repeatable": false,
    "max_completions": 1,
    "locked_to_villager": true
  },
  "external_scenes": ["my_pack:quests/storm_warning"],
  "entry_stage": "ask",
  "stages": [
    {
      "id": "ask",
      "objectives": [
        {
          "id": "hear_warning",
          "type": "choice",
          "choices": ["heard"],
          "tracker": {
            "text": "Hear the storm warning.",
            "complete_text": "The warning is clear."
          }
        }
      ],
      "dialogue": {
        "offer": {
          "label": "Storm Warning",
          "request": "question",
          "external_scene": {
            "tree": "my_pack:quests/storm_warning",
            "entry": "offer"
          }
        },
        "turn_in": {
          "label": "Storm Warning",
          "request": "question",
          "lines": ["The storm warning is clear now."],
          "responses": [
            {
              "id": "complete",
              "label": "I understand the omen.",
              "complete": true
            }
          ]
        }
      }
    }
  ],
  "events": [
    {
      "id": "storm_reminder",
      "event": "near_provider",
      "radius": 10,
      "cooldown_seconds": 120,
      "conditions": [
        { "type": "weather", "state": "thunder" }
      ],
      "actions": [
        {
          "type": "forced_dialogue",
          "forced_dialogue": "my_pack.quest.storm_warning.reminder"
        }
      ]
    }
  ],
  "ui": {
    "tracker_text": "Hear the storm warning.",
    "icon": "minecraft:lightning_rod"
  }
}
```

The external scene above can live in `data/my_pack/dialogue_trees/en_us/quests/storm_warning.json`:

```json
{
  "id": "my_pack:quests/storm_warning",
  "metadata": {
    "quest": "my_pack:storm_warning",
    "questline": "lost_civilization"
  },
  "entries": [
    {
      "id": "offer",
      "label": "Storm Warning",
      "request": "question",
      "start": "offer"
    }
  ],
  "nodes": {
    "offer": {
      "lines": [
        "Thunder is not the omen. The silence after it is."
      ],
      "responses": [
        {
          "id": "heard",
          "label": "I will listen for it.",
          "actions": [
            {
              "type": "set_variable",
              "scope": "quest",
              "key": "choice",
              "value": "heard"
            }
          ],
          "end": true
        }
      ]
    }
  }
}
```

The forced quest scene above can live in `data/my_pack/forced_dialogue/quests/lost_civilization/storm_warning.json`:

```json
{
  "metadata": {
    "quest": "my_pack:storm_warning",
    "questline": "lost_civilization"
  },
  "entries": [
    {
      "id": "my_pack.quest.storm_warning.reminder",
      "trigger": "quest",
      "output": {
        "mode": "forced_dialogue"
      },
      "lines": [
        "Storms make old warnings easier to hear. Stay close to shelter."
      ],
      "requires_line_of_sight": true,
      "force_camera_towards_villager": true,
      "options": [
        {
          "id": "my_pack.quest.storm_warning.ok",
          "label": "I understand.",
          "response": "Then keep the warning near your feet.",
          "end_conversation": true
        }
      ]
    }
  ]
}
```

## Localization

Inline text is a fallback. Use `*_key` fields when you want datapack-localized text:

```json
{
  "metadata": {
    "title": "Bread Delivery",
    "title_key": "quest.my_pack.bread_delivery.title",
    "description": "Bring 16 bread.",
    "description_key": "quest.my_pack.bread_delivery.description"
  },
  "ui": {
    "tracker_text": "Bring 16 bread.",
    "tracker_text_key": "quest.my_pack.bread_delivery.tracker"
  }
}
```

Dialogue slots and scenes also accept `text_key`, `label_key`, and keyed lines where the generated schema lists them. Put keyed text in normal dialogue message files under `data/<namespace>/dialogue/<locale>/.../messages/*.json`.

## Capabilities And Live Context

Conditions and actions come from the generated quest registries. The datapack builder reads `tools/datapack-builder/quest-registry-metadata.json`. The Node validator and Java schema generator use the same runtime metadata.

Some registry entries need live entities:

- provider-live conditions, such as villager equipment or live mood checks, need the quest giver loaded
- player-live actions, such as notifications, forced dialogue, loot, XP, and reputation changes, need a player context
- provider-live actions, such as forced dialogue and gossip, need the issuing villager loaded

Prefer saved-state conditions for active quest gates that must continue while the villager is unloaded. Use live-context actions from events only when the event is expected to run near the player and provider.

## Diagnostics And Trace Commands

Available diagnostic commands:

```text
/villagerretaliation datapack diagnostics
/villagerretaliation quest debug providers [radius]
/villagerretaliation quest debug why_available <quest_id> <provider_name>
/villagerretaliation quest debug why_hidden <quest_id> [provider_name]
/villagerretaliation quest debug inspect <quest_id>
/villagerretaliation quest debug rebind <quest_id> <provider_name>
/villagerretaliation quest debug objectives <quest_id>
/villagerretaliation quest debug trace on
/villagerretaliation quest debug trace show [limit]
/villagerretaliation quest debug trace capture <quest_id> <provider_name>
/villagerretaliation quest debug fire_trigger <quest_id> <event>
/villagerretaliation quest debug actions dry_run <quest_id> <trigger_id>
```

Use `inspect` for saved state, issuer context, target context, repeat rules, objective counters, current stage, and fact values. Use `trace` for indexed trigger dispatch, condition traces, action diagnostics, and bounded recent events.

## Legacy V1 Compatibility

V1 quest JSON remains supported when the file has no `schema: "villagerretaliation:quest/v2"`. V1 fields such as `display`, `offer`, top-level `objectives`, `rules`, `tracker`, `triggers`, and separate dialogue trees still load through the compatibility adapter.

Legacy override rules still apply:

- a higher-priority datapack can replace a built-in quest by writing the same quest id
- a v2 module can replace a v1 quest with the same id
- old dialogue tree resources under `data/<namespace>/dialogue_trees/<locale>/quests/...` still work
- `remove` and `replace` on dialogue trees still remove or replace legacy/extracted scenes

Do not delete v1 resources just because v2 exists. Migrate intentionally, validate the generated v2 file, and keep any external dialogue tree only when it is still needed.

## Extraction Guidance

Start with one v2 quest file. Extract only when the file becomes hard to maintain:

```text
data/<namespace>/quests/<module>/<quest>.json
data/<namespace>/dialogue_trees/<locale>/quests/<module>/<quest>.json
data/<namespace>/dialogue/<locale>/quests/<module>/<quest>/messages/*.json
data/<namespace>/forced_dialogue/quests/<module>/<quest>.json
```

Use the quest module for quest state, stages, objective readiness, rewards, transitions, and short scenes. Use external dialogue trees for long authored branches or shared localization. Use forced dialogue only for event-driven locked scenes outside the normal Talk flow.
