# Dialogue And Quests

# Villager Retaliation Dialogue + Quest Refactor

## Summary

Dialogue, dialogue trees, forced dialogue, and quests now share the same author-facing metadata vocabulary.

- Ambient dialogue files use a root `metadata` block so one file can describe its topic, tags, and scope without repeating that metadata on every entry.
- Quest JSON now exposes the dialogue relationship directly through a `links` block, so pack authors can see the menu scene and forced follow-ups without tracing Java.
- Shipped dialogue and quest content has been normalized to include metadata tags that describe content type, scope, and questline linkage.

This refactor keeps the existing runtime model intact: ambient pools still live in dialogue files, authored scenes still live in dialogue trees, forced scenes still live in forced-dialogue files, and quest state still lives in quest JSON. The change is that those files now read like one content framework instead of separate systems.

## Current Issues

The pre-refactor content had four practical inconsistencies.

- Quests knew nothing explicit about which dialogue tree offered, reminded, or turned them in.
- Dialogue trees already had metadata, but ambient dialogue files did not have a shared root metadata convention.
- Forced dialogue and quest content used different author-facing organization cues even when they belonged to the same questline.
- Documentation described the formats separately, which made pack authors infer the quest-to-dialogue relationship by reading examples instead of reading one rule.

## Proposed Unified Standard

Use these rules for all new authored content.

- Keep JSON indented with 2-space JSON formatting when editing by hand.
- Put shared file-level classification in a root `metadata` block.
- Use lowercase dotted tags in `metadata.tags`.
- Use `metadata.questline` and `metadata.quest` whenever a file belongs to a questline.
- Use quest `links` to expose the dialogue tree entry points and forced follow-ups that belong to a quest.
- Keep dialogue choices, conditions, and quest actions explicit in the file that owns them instead of hiding them in naming conventions.
- Use questline or story-module subfolders when one feature owns several related text surfaces.
- Use one bundled module file for small and medium authored units, and typed folders for large reusable pools.

## Validation And Tooling

The source of truth for validation and formatting rules is the Node tooling under `tools/`.

- Run `node tools/validate-dialogue-data.mjs` to validate shipped or pack-authored JSON.
- Run `node tools/normalize-dialogue-and-quests.mjs` to apply the stable 2-space formatter and shared metadata normalization rules.
- `tools/normalize-dialogue-and-quests.ps1` is a Windows fallback for environments where Node is unavailable. When Node is installed, the PowerShell script delegates to the Node normalizer instead of maintaining a second formatting authority.

Current environment note:

- If Node is missing, treat the PowerShell script as a recovery tool for metadata repair, not as the canonical formatter.
- If Java is missing, Gradle compile/test validation is not available and must be reported as not run.

## Tag Taxonomy

`metadata.tags` are author-facing classification tags. They are not the same as runtime `event_tags` or `player_event_tags`.

| Tag | Scope | Purpose | Example | Compatibility Notes |
| --- | --- | --- | --- | --- |
| `content.dialogue` | Dialogue, dialogue tree, forced dialogue | Marks any dialogue-authored content file. | `"tags": ["content.dialogue"]` | New shared metadata tag. |
| `content.quest` | Quest | Marks quest definition files. | `"tags": ["content.quest"]` | New shared metadata tag. |
| `dialogue.ambient` | Ambient dialogue | Identifies reusable pool-based talk content. | `"tags": ["dialogue.ambient", "section.lines"]` | Used on `dialogue/<locale>/...` files. |
| `dialogue.scene` | Dialogue tree | Identifies branching authored scenes. | `"tags": ["dialogue.scene", "quest.linked"]` | Used on `dialogue_trees/<locale>/...` files. |
| `dialogue.forced` | Forced dialogue | Identifies event-driven locked scenes. | `"tags": ["dialogue.forced", "quest.linked"]` | Used on `forced_dialogue/...` files. |
| `dialogue.linked` | Quest | Marks quest files that have dialogue surfaces. | `"tags": ["content.quest", "dialogue.linked"]` | Use on quests with menu or forced dialogue hooks. |
| `quest.linked` | Dialogue tree, forced dialogue | Marks dialogue that belongs to a questline. | `"tags": ["dialogue.scene", "quest.linked"]` | Use with `metadata.questline` and `metadata.quest`. |
| `section.options` | Ambient dialogue | File contains talk-menu options. | `"tags": ["section.options"]` | Root metadata classification only. |
| `section.lines` | Ambient dialogue | File contains ambient or request-based lines. | `"tags": ["section.lines"]` | Root metadata classification only. |
| `section.messages` | Ambient dialogue | File contains keyed message lines. | `"tags": ["section.messages"]` | Root metadata classification only. |
| `section.openings` | Ambient dialogue | File contains opening lines. | `"tags": ["section.openings"]` | Root metadata classification only. |
| `section.closings` | Ambient dialogue | File contains closing lines. | `"tags": ["section.closings"]` | Root metadata classification only. |
| `section.pacify` | Ambient dialogue | File contains pacification lines. | `"tags": ["section.pacify"]` | Root metadata classification only. |
| `scope.global` | Ambient dialogue | File applies globally. | `"tags": ["scope.global"]` | Derived from `dialogue/<locale>/global/...`. |
| `scope.group.<name>` | Ambient dialogue | File applies to a shared profession group. | `"tags": ["scope.group.village_trades"]` | Derived from `dialogue/<locale>/groups/...`. |
| `scope.profession.<name>` | Ambient dialogue | File applies to one profession family. | `"tags": ["scope.profession.cartographer"]` | Derived from `dialogue/<locale>/professions/...`. |
| `scope.quest_module` | Ambient dialogue | File is organized around a quest or story module instead of a broad reusable pool. | `"tags": ["scope.quest_module"]` | Use for custom module folders such as `dialogue/<locale>/quests/<questline>/...`. |
| `scope.quest_scene` | Dialogue tree | Scene is a quest-facing authored conversation. | `"tags": ["scope.quest_scene"]` | Used on shipped quest trees. |
| `questline.<id>` | Dialogue tree, forced dialogue, quest | Declares the owning questline plainly. | `"tags": ["questline.lost_civilization"]` | Keep the suffix stable and lowercase. |

## ID And Naming Rules

The applied refactor keeps the shipped ids stable. No runtime ids were renamed in this pass.

- Keep quest ids stable and readable.
- Keep dialogue tree entry ids short and stage-like: `offer`, `reminder`, `turn_in`.
- Keep forced-dialogue ids descriptive and dotted: `quest.lost_civilization.storm_reminder`.
- Put the stronger grouping signal in `metadata.questline`, `metadata.quest`, and `links` rather than forcing a repo-wide id rename.

If a future pass introduces path-style ids such as `villagerretaliation:quest/lost_civilization/...`, do it as a deliberate migration. This refactor stops short of that because the larger win was explicit metadata and links, not id churn.

## File Organization

The authored content is now organized as one framework with four surfaces.

```text
data/villagerretaliation/dialogue/en_us/...              ambient dialogue pools
data/villagerretaliation/dialogue_trees/en_us/...        branching authored scenes
data/villagerretaliation/forced_dialogue/...             event-driven locked scenes
data/villagerretaliation/quests/...                      quest state, rewards, and dialogue links
```

Use these ownership rules.

- Put reusable ambient talk in `dialogue`.
- Put related ambient options, lines, messages, openings, closings, or pacify lines in a shared module folder when that makes authorship clearer.
- Put a multi-step authored menu scene in `dialogue_trees`.
- Put event-driven or quest-triggered locked scenes in `forced_dialogue`.
- Put quest rules, objectives, rewards, and dialogue ownership in `quests`.

For example, both shapes are valid:

```text
data/villagerretaliation/dialogue/en_us/professions/cartographer/lines/00_questions.json
data/villagerretaliation/dialogue/en_us/quests/lost_civilization/00_lost_civilization.json
```

The first is best for broad profession pools. The second is best when one authored module owns nearby options and lines.

## Dialogue Convention

### Ambient Dialogue

Ambient dialogue files now support a root `metadata` block in addition to their existing entries.

```json
{
  "metadata": {
    "topic": "global.event_dialogue",
    "tags": [
      "content.dialogue",
      "dialogue.ambient",
      "scope.global",
      "section.lines"
    ]
  },
  "lines": [
    {
      "id": "default_event_player_attacked_villager_direct",
      "request": "village_event_report",
      "event_tags": ["player_attacked_villager"],
      "player_event_tags": ["player_attacked_villager"],
      "lines": ["You hurt one of ours."],
      "weight": 44
    }
  ]
}
```

Root metadata is a default, not a lock. Entry-level `metadata` still applies and overrides string fields while unioning tags.

```json
{
  "metadata": {
    "topic": "professions.cartographer.share_stories",
    "questline": "lost_civilization",
    "tags": [
      "content.dialogue",
      "dialogue.ambient",
      "scope.profession.cartographer",
      "section.lines"
    ]
  },
  "lines": [
    {
      "id": "cartographer.ancient_city_rumor",
      "request": "share_story",
      "metadata": {
        "stage": "rumor",
        "tags": ["quest.linked"]
      },
      "text": "There is an old city under the map lines."
    }
  ]
}
```

In that example, the entry inherits `topic` and `questline`, keeps the root tags, and adds the `quest.linked` tag plus its own `stage`.

### Module Dialogue Folders

Dialogue loading is recursive under `dialogue/<locale>/`, so packs can create subfolders for storylines, questlines, towns, NPC sets, or any other authoring unit.

A module file can bundle several dialogue sections together:

```json
{
  "metadata": {
    "topic": "quests.lost_civilization.cartographer",
    "questline": "lost_civilization",
    "tags": [
      "content.dialogue",
      "dialogue.ambient",
      "quest.linked",
      "scope.quest_module"
    ]
  },
  "options": [
    {
      "id": "lost_civilization.ask_ruins",
      "label": "Ask about the old city",
      "type": "dialogue_option",
      "request": "story"
    }
  ],
  "lines": [
    {
      "id": "lost_civilization.ruins_hint",
      "option": "lost_civilization.ask_ruins",
      "request": "story",
      "text": "There are maps that remember roads no one walks anymore."
    }
  ]
}
```

Use this bundled shape when the entries are read and maintained together. Split into typed folders such as `options/`, `lines/`, and `messages/` when a module grows large enough that separate files become easier to scan.

### Dialogue Trees

Dialogue trees should keep quest ownership visible through metadata and explicit quest actions.

```json
{
  "id": "villagerretaliation:tales_of_a_lost_civilization",
  "display": {
    "title": "Tales of a Lost Civilization",
    "description": "Cartographer quest offer, reminder, and turn-in scene."
  },
  "metadata": {
    "topic": "ancient_city",
    "questline": "lost_civilization",
    "quest": "villagerretaliation:tales_of_a_lost_civilization",
    "tags": [
      "content.dialogue",
      "dialogue.scene",
      "quest.linked",
      "questline.lost_civilization",
      "scope.quest_scene"
    ]
  },
  "entries": [
    {
      "id": "offer",
      "label": "Lost Civilization",
      "conditions": [
        {
          "type": "quest",
          "state": "available"
        }
      ],
      "start": "offer"
    }
  ]
}
```

When a dialogue tree has `metadata.quest`, quest conditions and quest actions inside that tree can omit `quest` / `quest_id`. The tree metadata supplies the default quest id. Use an explicit quest id only when one tree intentionally touches another quest.

### Forced Dialogue

Quest-linked forced dialogue should also declare the questline at the file root.

```json
{
  "entries": [
    {
      "id": "quest.lost_civilization.storm_reminder",
      "trigger": "quest",
      "output": {
        "mode": "forced_dialogue"
      }
    }
  ],
  "metadata": {
    "topic": "quest.lost_civilization.storm_reminder",
    "questline": "lost_civilization",
    "tags": [
      "content.dialogue",
      "dialogue.forced",
      "quest.linked",
      "questline.lost_civilization"
    ]
  }
}
```

## Quest Convention

Quest files now carry both shared metadata and explicit dialogue links.

```json
{
  "id": "villagerretaliation:tales_of_a_lost_civilization",
  "display": {
    "title": "Tales of a Lost Civilization",
    "description": "Follow a cartographer's rumor to an Ancient City and return with an Echo Shard."
  },
  "metadata": {
    "topic": "quests.lost_civilization",
    "questline": "lost_civilization",
    "quest": "villagerretaliation:tales_of_a_lost_civilization",
    "tags": [
      "content.quest",
      "dialogue.linked",
      "questline.lost_civilization"
    ]
  },
  "links": {
    "dialogue_tree": "villagerretaliation:tales_of_a_lost_civilization",
    "offer": "offer",
    "reminder": "reminder",
    "turn_in": "turn_in",
    "forced_dialogue": [
      "quest.lost_civilization.storm_reminder"
    ]
  }
}
```

Use `links` for author clarity, not as a replacement for the actual tree actions or trigger conditions.

Current runtime scope:

- `links` are parsed and validated.
- Gameplay still starts, reminds, turns in, and abandons quests through dialogue-tree quest actions and quest trigger actions.
- That means `links` are currently a metadata and validation contract, not the authoritative runtime selector for quest dialogue.
- Quest-scoped dialogue trees and quest triggers can inherit the current quest id, so repeated local `quest` fields are not required unless the file references another quest.

## Dialogue-Quest Linking

Use this pattern consistently.

- Dialogue tree entry exposes quest availability through a quest condition.
- Dialogue tree node action performs the quest state change.
- Quest `links.dialogue_tree` points back to the owning tree.
- Quest `links.offer`, `links.reminder`, and `links.turn_in` document which tree entries handle those lifecycle stages.
- Quest `links.forced_dialogue` lists any forced scenes the quest may trigger.
- In quest-scoped dialogue trees, omit repeated `quest` fields from local quest conditions and quest actions unless the file intentionally references another quest.

Applied example:

```text
villagerretaliation:tales_of_a_lost_civilization
  -> links.dialogue_tree
  -> villagerretaliation:tales_of_a_lost_civilization

quest links.offer
  -> offer

quest links.forced_dialogue
  -> quest.lost_civilization.storm_reminder
```

## Migration Map

No runtime ids or file paths were renamed in this pass.

| Old | New | Reason | Risk | Compatibility |
| --- | --- | --- | --- | --- |
| none | none | The refactor focused on metadata and explicit links instead of id churn. | low | stable |

## Changes Made

- Added quest-side `metadata` and `links` parsing in the Java loader.
- Added root metadata inheritance for ambient dialogue option and line files.
- Added quest-id inheritance for quest conditions and quest actions inside quest-scoped dialogue trees and quest triggers.
- Allowed forced dialogue files to carry the same root `metadata` block.
- Normalized shipped ambient dialogue, dialogue tree, forced dialogue, and quest JSON to include shared metadata.
- Added explicit quest dialogue links for `tales_of_a_lost_civilization`.

## Modpack Author Guide

### Add A New Ambient Dialogue File

- Put the file under `data/villagerretaliation/dialogue/<locale>/...`.
- Add a root `metadata` block with `topic` and `tags`.
- Keep the actual matching logic in the entries, not in the metadata.

### Add A New Quest

- Create a quest JSON file under `data/<namespace>/quests/`.
- Add `metadata.questline`, `metadata.quest`, and `metadata.tags`.
- Add a `links` block that points at the dialogue tree entry ids you expect authors to edit.

### Add A Linked Dialogue Scene

- Create a dialogue tree under `data/<namespace>/dialogue_trees/<locale>/...`.
- Set `metadata.questline` and `metadata.quest` to the owning quest.
- Use quest conditions on the entries and quest actions inside nodes. If they refer to the owning quest, the local `quest` field can be omitted.
- Keep the tree entry ids lifecycle-shaped and stable.

### Add A Quest Follow-Up Scene

- Put any triggered reminder scene in `forced_dialogue`.
- Add `metadata.questline` and a `quest.linked` tag.
- Reference the forced-dialogue id from the quest `links.forced_dialogue` array and from the relevant quest trigger action.

## Testing Checklist

- Parse every JSON file after edits.
- Run `node tools/validate-dialogue-data.mjs` when Node is available.
- Run `./gradlew compileJava` and `./gradlew test` when Java is available.
- Check for `metadata.tags: null` or `links.forced_dialogue: null`.
- Confirm each quest with dialogue has a `links` block.
- Confirm each quest-linked dialogue tree has `metadata.questline` and `metadata.quest`.
- Confirm each quest-triggered forced-dialogue file carries questline metadata.
- Check for duplicate quest ids.
- Check for duplicate dialogue tree ids.
- Check that every `links.dialogue_tree` id resolves to a real dialogue tree and that `links.offer`, `links.reminder`, and `links.turn_in` resolve to real entry ids inside that tree.
- Check for dialogue tree entry ids that no longer match quest `links.offer`, `links.reminder`, or `links.turn_in`.
- Check for forced-dialogue ids in `links.forced_dialogue` that no longer exist.
- Validate that ambient dialogue still loads when a file has only root metadata plus bundled entries.
