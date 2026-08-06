---
title: Builder & Blueprints
order: 40
icon: item:villagerretaliation:construction_blueprint
keywords:
  - construction
  - blueprint
  - builder
  - structure
  - materials
---

# Builder & Blueprints

Builder is a paid, one-off construction project-not a daily worker role. Any adult villager can take an order; Masonry and Crafting determine their aptitude. The structure must be in the server's catalog, and the site must pass validation.

:::steps
:::step{title="Choose a structure"}
The interaction screen creates a pending :item[villagerretaliation:construction_blueprint] with size, required materials, and a currency quote.
:::
:::step{title="Place the preview"}
Right-click a block to move it. Scroll to move; add Alt to strafe, Shift for height, or Alt+Shift to rotate. The placement-lock key starts unbound.
:::
:::step{title="Supply every block"}
The builder uses carried or assigned materials and reports what is missing.
:::
:::step{title="Keep the site reachable"}
The builder travels to each target and respects obstacles, borders, protection rules, entities, foundations, and pathfinding.
:::
:::step{title="Finish or cancel"}
The order ends when planned blocks are complete or the project is cancelled under its refund rules.
:::
:::

Default limits are **4096 planned blocks**, **28 blocks** from builder to site, and a **32-block** material-storage search. The default fee is 8 currency items plus 3 per 64 planned blocks. The built-in currency is emeralds. The final quote is authoritative.

Cancelling before any block is placed returns the full builder payment. After placement begins, payment is released and the villager cannot change roles until the project is resolved.

:::notice{type="warning"}
Blueprints do not capture arbitrary builds or import general schematics. Builders do not create free materials, force-load the site, or bypass protected blocks.
:::

