# Quests

Quest module v2 is the structural definition inside a beta.13 quest bundle. A bundle owns its quest, English locale, and private scene, encounter, and reward companions as one transactional unit.

Loose quest JSON and the old `quest_messages`, `quest_scenes`, `quest_encounters`, `quest_pools`, and `loot_table/quest` roots are unsupported at runtime. `/reload` reports them and never silently loads them.

Each quest run receives a saved unique ID before its first actions run. A solo run belongs to one player. A party run uses one shared ID for the party. Persistent scenes use this saved ID so a reload resumes the same scene instead of starting a duplicate.

## Paths

```text
data/<namespace>/quests/
  _shared/
    locales/en_us.json
    pools/*.json
    scenes/*.json
    encounters/*.json
    rewards/*.json
  <questline>/<quest-slug>/
    quest.json
    locales/en_us.json
    locales/<locale>.json
    scenes/*.json
    encounters/*.json
    rewards/*.json
```

Every definition still has an explicit stable `id`; file names and pack names never become persistent IDs. The quest ID path must be one segment, `<quest-slug>` must equal that path, `metadata.questline` must equal the directory, and the quest ID namespace must equal `data/<namespace>`. `_shared` is reserved.

`quest.json` keeps structural dialogue, but every schema-designated player-facing field uses a reference such as `{"key":"#metadata.title"}`. Relative keys expand from the required immutable `localization_prefix`; absolute message IDs remain supported. The sibling `locales/en_us.json` uses `schema: "villagerretaliation:quest_locale/v1"` and a `messages` object. English is exhaustive for a new bundle, other locales may be partial, and lookup falls back per message ID to effective English at the player boundary.

Bundled rewards use `schema: "villagerretaliation:quest_reward/v1"`, an explicit stable `id`, and a registry-aware vanilla `table` whose type is `minecraft:generic`. Bundle rewards take precedence over external registry tables for both execution and roll-producing previews. Nested vanilla table references remain normal registry references.

Each datapack layer replaces whole structural definitions by stable ID. The quest structure, companions, rewards, and effective English are one owner-bundle transaction: an invalid higher layer is rejected as a unit and the lower valid bundle remains active. Each optional non-English locale layer is independent. Duplicate IDs, cross-owner moves, duplicate message ownership, or private cross-bundle companion references are errors.

File paths, JSON member ordering, pack names, and localized payloads do not change persistent structural identity. Behaviorally ordered arrays remain ordered, and migration-equivalence fingerprints additionally include localized variants. Removing an active definition makes saved content unresolved and dormant; progress is preserved and resumes unchanged when the definition returns.

## Builder Authoring Input

The browser builder accepts the convenient inline authoring form below, then exports localized `quest.json` and `locales/en_us.json` files. Do not copy the inline form directly into a beta.13 datapack.

Importing a canonical bundle's `quest.json` together with its sibling `locales/en_us.json` materializes localized references back into editable authoring text and preserves the canonical bundle paths on export. Imported loose quest files and legacy `quest_messages`, `quest_scenes`, `quest_encounters`, `quest_pools`, or `loot_table/quest` roots are retained only so the validator can report an **Unsupported quest layout** error; move or convert them before export.

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
| `provider` | Who can offer or own the quest, plus provider-wide death protection and hiring policy |
| `availability` | Ordered prerequisites, repeat limits, abandonment, cooldowns, locking, and active gates |
| `target` | Optional world target such as a structure search |
| `entry_stage` | Authoritative first stage id. A later stage named `started` does not override it |
| `stages` | Objectives, stage-local dialogue, responses, scenes, events, and UI |
| `events` | Quest-level triggers that run while the quest exists |
| `rewards` | XP, reputation, gossip, loot, memory events, or reward actions |
| `ui` | Tracker text, icon, progress, placeholders, title and outline colors, and priority |

## Quest Tag Taxonomy

`metadata.tags` classifies quests for journal filters, quest-board pools, authoring tools, diagnostics, and content tests. Tags describe a quest; they do not replace authoritative fields such as `availability.repeatable`, `completion_scope`, `ui.hidden`, `ui.priority`, provider filters, or objective definitions.

## Journal Presentation

Root `ui` fields are live journal data, not authoring-only hints:

```json
{
"ui": {
  "icon": "minecraft:filled_map",
  "color": "#d4a35a",
  "outline_color": "#201408",
  "priority": 25,
  "hidden": false,
  "tracker_text": "Survey the old road."
}
}
```

`priority` sorts otherwise equivalent journal entries, `color` tints their titles, `outline_color` opts into a ToucanLib 1px title outline, and `hidden` suppresses the quest from the journal and HUD. When omitted, title text defaults to unoutlined black. Both authored colors accept `#RRGGBB` or named Minecraft chat colors. The synchronized journal also carries `metadata.questline` and `metadata.tags`; press `/` in the journal to search titles, descriptions, objectives, questlines, and tags.

Active quests with `availability.expiration.after_ticks` show a live remaining-time countdown. A located structure or location objective publishes its saved dimension and coordinates as a waypoint in both the selected journal entry and tracked HUD, including live distance while the player is in the same dimension. Completed journal-history entries show how long ago that run completed.

## Quest Pools

Quest pools turn quest tags or explicit quest IDs into bounded rotating offers. Put reusable pool resources under `data/<namespace>/quests/_shared/pools/`. A pool only controls quests it claims; quests not claimed by any currently matching pool keep their normal availability.

```json
{
  "schema": "villagerretaliation:quest_pool/v1",
  "id": "my_pack:daily_commissions",
  "scope": "village",
  "refresh_days": 1,
  "max_offers": 3,
  "anti_repeat_rotations": 2,
  "default_weight": 10,
  "match": "any",
  "priority": 20,
  "exclusive": false,
  "any_tags": ["pool.daily", "pool.commission"],
  "exclude_tags": ["difficulty.extreme"],
  "weights": {
    "my_pack:urgent_repairs": 40,
    "my_pack:disabled_today": 0
  },
  "weight_rules": [
    {
      "any_tags": ["activity.build", "activity.trade"],
      "multiplier": 2,
      "conditions": [
        { "type": "weather", "state": "clear" }
      ]
    }
  ],
  "tag_quotas": {
    "activity.combat": 1,
    "destination.remote": 1
  },
  "conditions": [
    { "type": "dimension", "dimension": "minecraft:overworld" }
  ]
}
```

Selection is deterministic for the pool, scope key, and refresh epoch. `scope` accepts `player`, `village`, `provider`, `dimension`, or `world`. `refresh` and its `_ticks`, `_seconds`, and `_days` forms set the epoch length. `anti_repeat_rotations` avoids recent deterministic selections when enough alternatives exist, then backfills so avoidable repeats do not leave the board short.

Candidate controls:

| Field | Meaning |
| --- | --- |
| `quests` | Explicit quest IDs |
| `any_tags` | Quests with at least one listed tag |
| `all_tags` | Quests with every listed tag |
| `exclude_quests`, `exclude_tags` | Remove candidates before selection |
| `match` | `any` (default) lets any non-empty selector claim a quest; `all` requires every supplied selector family |
| `max_offers` | Maximum selected quests, from 1 through 64 |
| `enabled` | Disable the definition without deleting it |
| `remove` | Remove a previously loaded pool with the same `id` |

A pool with no positive selector claims no quests. Quest availability, provider filters, active capacity, prerequisites, and other ordinary start rules are evaluated before pool selection.

Weighting and diversity:

- An explicit `weights` value replaces the calculated weight for that quest; `0` excludes it.
- Otherwise the weight is `default_weight` multiplied by the quest's `availability.weight` or `selection_weight`.
- Every matching `weight_rules` multiplier is applied. A rule can filter by tags and shared `conditions`.
- `tag_quotas` caps how many selected quests may carry each listed tag.
- Selection is weighted without replacement.

Pool-level `conditions` decide whether a pool participates for the current player, provider, village, dimension, and world context. When several pools claim the same quest, selection by any applicable non-exclusive pool allows it. If an applicable claiming pool is `exclusive: true`, only exclusive pools at the highest exclusive `priority` are considered for that quest.

The built-in `villagerretaliation:quest_board` is village-scoped, refreshes every Minecraft day, shows at most three eligible `pool.quest_board` quests, avoids the previous two rotations where possible, and limits combat, remote, expedition, hard, and extreme offers for variety.

## Active Quest Capacity

A quest can limit how many quests the same player may already have active when they try to start it:

```json
{
  "availability": {
    "max_active_quests": 6,
    "max_active_by_tag": {
      "role.story": 2,
      "commitment.expedition": 1
    }
  }
}
```

`max_active_quests` is a total per-player cap enforced by this candidate quest. `max_active_by_tag` is checked only for keys also present in the candidate's `metadata.tags`; it counts active quests carrying the same normalized tag. `0` means no cap. These gates prevent a new start but do not cancel an already active quest.

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
{
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
}
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

Structural Talk-menu dialogue and its branches always stay in `quest.json`. Stage-local `scenes` keep larger inline branches organized inside that structure. Persistent runtime scene companions live under the owning bundle's `scenes/` directory and are referenced only by explicit stable ID; they are not external dialogue trees.

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
{
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
}
```

`complete_when` may mix objective references with condition predicates. Objective UI also accepts `tracker_complete_text` and `tracker_complete_text_key`, and reward memory events retain `memory_scope`. The v1-to-v2 migration tool preserves these rules, stage tracker steps, and objective completion copy.

## Definition Revisions And Live-Save Migration

Increment `metadata.revision` whenever an update changes stage or objective identity for a quest that players may already have active. The migration policy is applied once when a save first sees the newer revision and the result is persisted for audit/debug output.

```json
{
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

## Provider Protection And Hiring Policy

Villager providers can declare two independent world-facing policies:

- `death_protection: "none"` leaves normal lethal behavior unchanged. This is the default.
- `death_protection: "while_active"` protects the issuing villager while the quest is active.
- `death_protection: "after_start"` protects that issuing villager permanently after this player starts the quest from them.
- `blocks_hiring: true` prevents every villager matching the provider filters from beginning a new paid hire contract.

```json
{
  "provider": {
    "type": "villagerretaliation:villager",
    "death_protection": "while_active",
    "blocks_hiring": true,
    "filters": {
      "professions": ["minecraft:cartographer"]
    }
  }
}
```

The hiring rule is deliberately provider-wide. It does not depend on a player's quest state, offer visibility, prerequisites, pool selection, or active-quest capacity: the author is declaring that the matching NPC role must remain available as a quest provider. Existing paid contracts are not cancelled, and party recruitment is not affected. The field defaults to `false`.

Keep filters narrow when enabling `blocks_hiring`. A broad profession-only filter can reserve every villager of that profession in the world. A player who tries to hire a reserved provider receives a quest-provider notice, and the Hire option is hidden when the client receives the restriction.

## Missing Providers And Rebind

Active progress remains in the journal using the saved provider name, profession, location, and UUID when the live villager is gone. The journal's **Abandon quest** action works without the live provider. If abandonment or expiration has an authored lifecycle hook, the runtime persists that event instead of dropping its provider-bound actions. It replays the event once when the original provider is live again, or immediately after an operator supplies a compatible replacement. Turning in through another matching provider is allowed only with `cross_villager_compatible: true`. The runtime never chooses a nearby villager automatically.

Operators can explicitly repair a missing binding with:

```text
/vr admin quest rebind <quest_id> <provider>
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

## Random Item Objectives

The existing fixed selector remains unchanged:

```json
{
  "type": "item_check",
  "item": "minecraft:bread",
  "count": 8
}
```

To choose one required item when a quest run starts, set `selection` to `random` and provide a non-empty `items` array. A string beginning with `#` expands the current item tag:

```json
{
  "type": "item_check",
  "items": [
    "minecraft:wheat",
    "minecraft:sugar_cane",
    "#c:tools"
  ],
  "selection": "random",
  "count": 8
}
```

Entries may be weighted. The default weight is `1`:

```json
{
  "type": "item_check",
  "items": [
    { "item": "minecraft:wheat", "weight": 5 },
    { "item": "minecraft:lava_bucket", "weight": 1 },
    { "tag": "c:swords", "weight": 2 }
  ],
  "selection": "random",
  "count": 1
}
```

Weights select an entry first. If the selected entry is a tag, one item is then chosen uniformly from that tag. A large tag therefore has the same entry probability as a one-item tag with the same weight. The resolved concrete item is stored in quest saved data and is not rerolled by reloads, reconnects, restarts, tracker refreshes, or objective checks. A new repeatable quest run resolves a new item.

`{objective_item}` and `{objective_item_id}` display the resolved name and ID in objective tracker text, UI text, and quest dialogue. Item `components`, `durability`, enchantment, `custom_data`, and `nbt` requirements apply to the resolved item exactly as they do to a fixed `item`.

## Structure Target Example

Root `target` fields define a structure search, discovery radius, and proof item. Stages can combine a visit objective with a proof-item objective.

An `item_check` objective can add `components`, `durability`, `custom_data`, or `nbt` to its item selector. These combine with the existing enchantment and durability-percentage requirements. Root proof items use the prefixed forms `proof_item_components`, `proof_item_durability`, `proof_item_custom_data`, and `proof_item_nbt`. Progress counting, hand-in consumption, tracker synchronization, tooltips, slot highlights, held-item glow, and dropped-item outlines all use the same requirements; advanced objectives no longer highlight a nonmatching variant of the same item ID.

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

## Forced Dialogue Action

Structural quest dialogue remains in `quest.json`. Use a `forced_dialogue` action only for a separate event-driven interruption that needs live player and provider context:

```json
{
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
  ]
}
```

The referenced definition is part of the separate global forced-dialogue system and keeps its own stable ID. It does not become a private bundle companion and cannot replace offer, reminder, turn-in, response, or branch structure. If the player or quest provider is unavailable, the runtime reports the live-entity failure instead of advancing unsafely.

## Quest Trigger Arbitration And Payloads

Quest `events` can react to `player_tick`, `proximity`, `started`, `progress`, `mob_kill`, `block_break`, `block_place`, `block_interact`, `memory_event`, `gift`, `trade`, `reputation`, `criterion`, `stage_changed`, `completed`, `failed`, `abandoned`, and `expired`.

```json
{
  "events": [
    {
      "id": "crafted_blade_reaction",
      "event": "criterion",
      "stages": ["forge"],
      "priority": 20,
      "chance": 0.75,
      "weight": 3,
      "exclusive": true,
      "cooldown_seconds": 30,
      "repeatable": true,
      "conditions": [
        {
          "type": "trigger_payload",
          "all": {
            "criterion": ["villagerretaliation:crafted"],
            "criterion_item": ["minecraft:iron_sword"]
          }
        }
      ],
      "actions": [
        {
          "type": "notification",
          "trigger": "quest.updated",
          "text": "The smith noticed your finished blade."
        }
      ]
    }
  ]
}
```

The dispatcher snapshots the canonical event, dispatch stage, game time, player/provider identity, position, and scalar payload values. Objective payloads therefore remain available to conditions and actions for that dispatch instead of being reduced to a bare event name. See [Trigger Payload Conditions](JSON-Reference.md#trigger-payload-conditions) for the query shape.

Matching triggers are grouped by `priority`. `chance` gates each trigger, `weight` orders surviving triggers inside a tier, and `weight: 0` disables a trigger. Every selected trigger normally runs; after a successfully executed `exclusive: true` trigger, lower-priority work stops. `cooldown` and its duration suffixes are stored per trigger and quest run. `repeatable: false` permits one successful execution for that run.

## Localization

Every schema-designated player-facing quest, scene, and encounter field uses a localized reference. Relative references expand from the quest's required immutable `localization_prefix`:

```json
{
  "localization_prefix": "my_pack.quest.bread_delivery",
  "metadata": {
    "title": { "key": "#title" },
    "description": { "key": "#description" }
  },
  "ui": {
    "tracker_text": { "key": "#ui.tracker_text" }
  }
}
```

The introducing bundle layer includes exhaustive `locales/en_us.json` entries for its owned references:

```json
{
  "schema": "villagerretaliation:quest_locale/v1",
  "messages": {
    "my_pack.quest.bread_delivery.title": {
      "lines": ["Bread Delivery"]
    },
    "my_pack.quest.bread_delivery.description": {
      "lines": ["Bring 16 bread."]
    },
    "my_pack.quest.bread_delivery.ui.tracker_text": {
      "lines": ["Bring 16 bread."]
    }
  }
}
```

Absolute existing message IDs remain supported. Other locales may be partial and fall back per message ID to effective English at the per-player resolution boundary. Variant ordering, formatting, placeholders, weights, and conditions are preserved exactly. Shared absolute wording belongs in `_shared/locales/en_us.json`; private bundle wording cannot move between owners.

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
/vr admin datapack diagnostics
/vr admin quest providers [radius]
/vr admin quest whyAvailable <quest_id> <provider>
/vr admin quest whyHidden <quest_id> [provider]
/vr admin quest inspect <quest_id>
/vr admin quest rebind <quest_id> <provider>
/vr admin quest objectives <quest_id>
/vr admin quest trace on
/vr admin quest trace show [limit]
/vr admin quest trace capture <quest_id> <provider>
/vr admin quest fireTrigger <quest_id> <event>
/vr admin quest actions dryRun <quest_id> <trigger_id>
```

Use `inspect` for saved state, issuer context, target context, repeat rules, objective counters, current stage, and fact values. Use `trace` for indexed trigger dispatch, condition traces, action diagnostics, and bounded recent events.

## Unsupported Legacy Layout

Beta.13 does not run loose v1 or v2 quest JSON, old quest companions, or old private reward tables. The reload report names every unsupported path and the lower live catalog remains active if built-in content or a catalog-wide invariant fails. Use the optional offline converter or the browser builder to produce a bundle; there is no runtime fallback.

Quest-to-quest prerequisites and follow-ups remain legal. Only private scene, encounter, and reward access is restricted to the owning quest.

## Extraction Guidance

Start with one owner bundle and add private companions only when the behavior needs them:

```text
data/<namespace>/quests/<questline>/<quest-slug>/quest.json
data/<namespace>/quests/<questline>/<quest-slug>/locales/en_us.json
data/<namespace>/quests/<questline>/<quest-slug>/scenes/<scene>.json
data/<namespace>/quests/<questline>/<quest-slug>/encounters/<encounter>.json
data/<namespace>/quests/<questline>/<quest-slug>/rewards/<reward>.json
```

Keep all structural quest dialogue and branches in `quest.json`, regardless of length. Put localized wording in `locales/`, persistent runtime scenes in `scenes/`, encounter templates in `encounters/`, and wrapped loot tables in `rewards/`. Reusable definitions belong in `_shared`. Use the separate forced-dialogue system only for event-driven locked scenes outside normal Talk flow.
