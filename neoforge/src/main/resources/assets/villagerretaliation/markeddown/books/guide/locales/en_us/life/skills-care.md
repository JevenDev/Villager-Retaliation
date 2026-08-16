---
title: Skills, Attributes & Care
order: 0
icon: item:minecraft:experience_bottle
expanded: true
keywords:
  - skills
  - attributes
  - mood
  - food
  - healing
  - efficiency
---

# Skills, Attributes & Care

## Skills and Job Stats

Every villager has persistent skill scores from 1–100: Farming, Fishing, Smithing, Crafting, Trading, Medicine, Archery, Guarding, Cooking, Animal Handling, Cartography, Scholarship, Gathering, Masonry, Mining, Leatherworking, Diplomacy, and Survival.

Job Stats explains availability and throughput:

- Every adult can perform any ordinary job; only explicit restrictions such as nitwit-only work still apply.
- Aptitude weights the primary skill at **70%** and support at **30%**.
- Farming, Animal Handling, Fishing, and Nitwit work use a **50–125%** action curve; aptitude 60 is standard.
- Mining and Logging combine tool and block stats with a modest **85–110%** aptitude modifier. Builder construction and Hunter tracking use the same narrow range.
- Craftsman, Cook, Smelter, and Brewer collection capacity ranges from **50–150%**, with aptitude 60 as standard.
- Courier aptitude changes pickup capacity instead of speed: **1, 2, 4, 8, 16, 32, 64, 96, or 128 items** per input container. Aptitude 60 carries 64; aptitude 100 carries 128.
- Guarding slightly improves melee speed and damage and unlocks axe shield-breaking at 60. Archery improves ranged speed and accuracy.

Five persistent social attributes—**Knowledge, Guts, Composure, Kindness, and Charm**—shape choices, moods, and reactions when their server settings are enabled. Skills describe learned capability; personality never supplies technical work or combat performance.

## Food and recovery

Villagers track food from 0–20 plus saturation. With the `naturalRegeneration` gamerule enabled, injured villagers can consume accessible food and heal; saturation speeds recovery. Low-health villagers may use golden apples or healing and regeneration potions. Sleep can also restore health up to a configurable cap.

:::notice{type="warning"}
Food supports healing but is not a separate work-efficiency bonus. Disabling `naturalRegeneration` disables the ordinary food-based healing path.
:::

## Mood and work

- Content, Grateful, Proud, or Hopeful: +8 efficiency points by default.
- Suspicious or Lonely: -8 points.
- Angry, Afraid, Stressed, or Grieving: -15 points.
- A missing required tool: -20 points.

Displayed efficiency is clamped to server limits-25–175% by default. Danger, combat, sleep, recovery, party orders, an offline hirer, unloaded chunks, and incomplete setup can all pause work regardless of efficiency.

