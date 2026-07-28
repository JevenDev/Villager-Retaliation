---
title: Clipboard & Storage
order: 20
icon: item:villagerretaliation:clipboard
keywords:
  - clipboard
  - work area
  - route
  - supplies
  - output
  - item filter
---

# Clipboard & Storage

Use a :item[villagerretaliation:clipboard] in the air to open the workforce dashboard. It shows hired villagers, jobs, warnings, wages, time remaining, storage, payment, work areas, routes, targets, and recent diagnostics.

## Assignment modes

- **General:** fallback supplies and returned contract items.
- **Supplies:** tools, weapons, ammunition, ladders, job equipment, ingredients, fuel, seeds, breeding food, raw materials, and courier pickup.
- **Output:** produced items, catches, drops, and courier delivery.
- **Payment:** a Payment Box used for renewal.
- **Work Area Preview / Set Work Area:** inspect or commit a three-dimensional job area.
- **Route:** ordered nodes for Combat, Hunting, or Courier work.

Ctrl+mouse-wheel cycles storage-purpose modes. A Clipboard can hold up to eight selected containers. Shift-use in the air clears its current selection.

## Work areas and routes

Right-click to place or recenter an area. Mouse wheel moves it; add Shift for height, Ctrl to resize horizontally, Alt to strafe, or Ctrl+Alt to resize vertically. Shift-left-click clears the draft.

For a route, right-click blocks to add nodes, use the first node again to toggle looping, shift-right-click a node to remove it, or shift-right-click the air to clear the draft.

:::notice{type="warning"}
Assignments are dimension-specific. Work does not bypass unloaded chunks, walls, world borders, protection rules, full inventories, restrictive filters, or unreachable container faces.
:::

## Inventories and filters

Authorized players may see separate Personal, Job, and Party inventories. Job inventory includes live equipment, a 27-slot grid, 9-slot hotbar, and an :item[villagerretaliation:item_filter] slot.

- **Allowlist:** only listed items are handled.
- **Denylist:** listed items are skipped.

Use an :item[villagerretaliation:attribute_filter] to inspect a reference item and select one property such as a tag, creative group, fuel status, recipe capability, enchantment, color, name, or container state. Attribute filters work directly in a villager's filter slot or an output-container item frame.

Inside an Item Filter, ordinary items are alternatives while every nested filter is required. Attribute Filters add property constraints. Nested Item Filters add their complete allowlist or denylist result, so an allowlist and denylist can be combined. Nesting is limited to eight levels. A filter containing a wooden pickaxe, is furnace fuel, and is not stackable therefore accepts only wooden pickaxes satisfying both attributes. Potion entries retain their exact potion contents instead of matching every potion with the same bottle type. The outer Item Filter's mode applies after all constraints are evaluated.

A filter narrows supported work; it does not add recipes, targets, or storage behavior.

:::details{id="worker-troubleshooting" title="Worker troubleshooting order" open="true"}
1. Check **Work Enabled** and payment.
2. Check that the area or route is committed, loaded, and reachable.
3. Check the exact required tool, station, ammunition, fuel, or material.
4. Check Supplies, Output, General, and Payment assignments.
5. Check capacity, filters, and incompatible station contents.
:::

