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

Downed is a special death-protection rule, not a universal villager feature. Active party members may be protected when enabled; current quest givers, scripted-scene actors, or villagers marked essential can also qualify. **Hiring alone does not protect a worker.**

Ordinary lethal damage leaves a protected villager at 1 health and incapacitates them. They dismount and stop AI, navigation, pickup, work, combat, and normal interaction.

Default recovery rules:

- Minimum downed time: **160 ticks / 8 seconds**.
- Threat check radius: **16 blocks**.
- Required quiet period: **60 ticks / 3 seconds**.
- Recovery health: **25% of maximum**.
- Continued danger resets the quiet countdown.

Void, out-of-world, generic kill-style, and other invulnerability-bypassing damage can still kill. Losing the protection rule before a later lethal hit also removes the safety net.

## Second Wind compatibility

Automatic downing and recovery work without Second Wind. With **Second Wind**, a player can channel an early revive and the villager can use its crawl presentation. Villager Retaliation still decides who is protected, when downing happens, automatic recovery, health, and lethal bypasses.

