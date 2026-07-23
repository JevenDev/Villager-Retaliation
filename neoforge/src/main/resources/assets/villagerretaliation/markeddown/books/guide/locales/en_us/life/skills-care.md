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

Job Stats explains qualification and throughput:

- Primary + support skill must total **61**, unless a matching profession bypass applies.
- Aptitude weights the primary skill at **70%** and support at **30%**.
- Skill-controlled work speed ranges from **75–125%**.
- Supported transfer capacity ranges from **50–150%**.

Five persistent social attributes-**Knowledge, Guts, Proficiency, Kindness, and Charm**-shape reactions when their server settings are enabled. Skills describe capability; attributes describe personality.

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

