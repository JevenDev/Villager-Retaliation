---
title: Downed Villagers
order: 10
icon: item:minecraft:totem_of_undying
keywords:
  - downed
  - revive
  - second wind
  - recovery
  - essential
---

# Downed Villagers

Downed is a configurable death-protection rule. Servers can independently protect every villager, raid participants, hired workers, and party members. Current quest givers, scripted-scene actors, or villagers marked essential can also qualify.

Ordinary lethal damage leaves a protected villager at 1 health and incapacitates them. They dismount and stop AI, navigation, pickup, work, combat, and normal interaction.

Damage-source settings are applied after those eligibility rules. Player damage, mob or other entity damage, and environmental damage such as fire, lava, falling, drowning, or suffocation can each be enabled independently. For example:

- To make ordinary lethal damage down every villager, enable `allVillagersUseDownedState` and leave all three damage-source settings enabled.
- To down only raid, hired, or party villagers, enable the desired context settings and leave `allVillagersUseDownedState` disabled.
- To let lava and falls kill eligible villagers while attacks down them, disable `environmentalDamageDownsEligibleVillagers`.
- To let lava and falls down eligible villagers while players and mobs can kill them, enable only `environmentalDamageDownsEligibleVillagers`.

The context settings combine with OR: a villager needs to match at least one enabled context or built-in quest/scene/essential protection. The damage settings then filter the lethal hit. Operator kill, void, and other invulnerability-bypassing damage remain intentional finishers.

Default recovery rules:

- Minimum downed time: **160 ticks / 8 seconds**.
- Threat check radius: **16 blocks**.
- Required quiet period: **60 ticks / 3 seconds**.
- Recovery health: **25% of maximum**.
- Continued danger resets the quiet countdown.

Void, out-of-world, generic kill-style, and other invulnerability-bypassing damage can still kill. Losing the protection rule before a later lethal hit also removes the safety net.

## Second Wind compatibility

Automatic downing and recovery work without Second Wind. With **Second Wind**, a player can channel an early revive and the villager can use its crawl presentation. Villager Retaliation still decides who is protected, when downing happens, automatic recovery, health, and lethal bypasses.

