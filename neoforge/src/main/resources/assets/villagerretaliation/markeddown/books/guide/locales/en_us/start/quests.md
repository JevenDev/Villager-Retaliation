---
title: Quests
order: 20
icon: item:minecraft:filled_map
keywords:
  - journal
  - tracker
  - objectives
  - turn in
  - quest giver
---

# Quests

Quest offers are resolved from the current player, villager, and world state. A built-in quest existing does not mean every villager can offer it.

## How to complete a quest

1. Talk to the correct, available villager.
2. Read the profession, trade-level, skill, reputation, prerequisite, and repeatability requirements.
3. Open the Journal with :key[key.villagerretaliation.open_quest_journal].
4. Toggle live objectives with :key[key.villagerretaliation.toggle_quest_tracker].
5. Complete every objective, then return to the required quest giver with the exact proof and consumable items.

Quest content can include tracked coordinates, highlighted quest items, HUD notices, authored scenes, choices, exclusive branches, cooldowns, protected actors, encounters, and structure or dimension requirements.

:::details{id="missing-quest" title="Why a quest is unavailable"}
- The villager has the wrong profession, trade level, or hidden skill score.
- Your personal reputation is outside the offer's allowed range.
- A prerequisite, earlier branch choice, cooldown, location, or active quest state does not match.
- The giver is fighting, controlled, recovering, downed, or otherwise unavailable.
- The server's datapacks added, replaced, or removed the built-in content.
:::

:::spoiler{label="Quest troubleshooting hint"}
If progress looks stuck, read the active tracker literally: check whether the objective requires carrying an item, surrendering it at turn-in, visiting a target, defeating a specific entity, or returning to the original giver.
:::

## Daily quest-board offers

Built-in quest-board requests rotate separately for each tracked village once per Minecraft day. A village shows at most three currently eligible board quests, avoids its previous two rotations when enough alternatives exist, and limits how many combat, distant, expedition, hard, or extreme requests appear together. Another village can have a different board; story quests outside the board keep their normal availability.

Quest completion can grant experience, personal and gossip reputation, loot, story facts, and access to later quests.

