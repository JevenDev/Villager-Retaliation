---
title: Villager Duels
order: 15
icon: item:minecraft:iron_sword
keywords:
  - duel
  - challenge
  - wager
  - guts
  - arena
---

# Villager Duels

Eligible adult villagers can accept a controlled, non-lethal duel. Open the villager interaction screen and choose **Duel**. The option is hidden when the villager's Guts is below the server threshold -- **60 by default**.

A villager must be alive, awake, nearby, out of combat, and available. Hired, recruited, downed, death-protected, trading, or otherwise busy villagers cannot duel. Creative and spectator players cannot issue a normal challenge.

## Choose the terms

- **Bare Handed:** fists only.
- **Melee:** iron swords and shields.
- **Ranged:** bows and 64 arrows.
- **Armored:** full iron armor, iron swords, iron axes, and shields.
- **Bring Your Own:** uses existing equipment and is disabled by default.

Wagers can be 0, 8, 16, 32, or 64 currency items, or the maximum both sides can cover. The villager pays from their wallet. A winner receives both stakes; a draw or cancelled duel refunds each side.

## Arena rules

- Countdown: **3 seconds**.
- Arena radius: **16 blocks** from the midpoint.
- Boundary grace: **10 seconds** outside the ring.
- Time limit: **5 minutes**, followed by a draw.
- Rematch cooldown: **3 Minecraft days**.
- Spectators: up to **16 villagers** found within **48 blocks**.

The assigned duel loadout temporarily replaces inventory, equipment, health, hunger, and effects, then restores the saved state. The duel blocks normal container access, item dropping, and outside interference.

A knockout decides the result without ordinary attack reputation loss. Losing to a villager briefly slows the player and prevents attacks. A player win can grant **+2 reputation** with each eligible spectator by default.

:::notice{type="warning"}
A villager can permanently refuse more challenges from the same player after losing three consecutive duels by default.
:::

Server settings can change eligibility, loadouts, timing, arena size, spectators, reputation, cooldowns, and refusal limits.
