---
title: Sell Box & Daily Market
order: 50
icon: item:villagerretaliation:sell_box
keywords:
  - sell box
  - market
  - prices
  - currency
  - courier
---

# Sell Box & Daily Market

Craft a :item[villagerretaliation:sell_box] from a barrel and an item in the server's currency tag. Place it **inside a village**: a Sell Box outside any tracked or recognizable village cannot quote or accept a sale.

:::steps
:::step{title="Insert a supported item"}
Open the box and place one stack in the pending slot. The screen shows the exact current value.
:::
:::step{title="Confirm the sale"}
Choose **Sell**. The stack is consumed and its exact value moves into the box's saved balance.
:::
:::step{title="Collect the proceeds"}
Choose **Withdraw**. Whole currency items move into your inventory; any fractional value stays in the balance for later sales.
:::
:::

:::recipe{id="villagerretaliation:sell_box" align="left"}
:::

## Local prices

Each village has its own daily market. The same item can pay differently in another village or on another Minecraft day. Items in the same demand group rise and fall together.

Selling adds local supply pressure. Repeated sales in one village gradually reduce later payouts for that group, and the pressure recovers over later days. Always trust the exact quote in the Sell Box screen.

:::notice{type="info"}
Server datapacks control which items are saleable and their base ranges. Configured currency items cannot be sold.
:::

## Workers and automation

- Assign a Sell Box as **Output** storage so workers can deposit saleable output.
- Assign it as **Supplies** storage so a Courier can collect whole currency proceeds.
- Hoppers and item handlers insert sale items from the top or sides.
- Hoppers and item handlers extract available whole currency from the bottom.
- Replacing an occupied pending slot with another valid stack sells the previous stack first.

The pending slot and exact balance are preserved when the box is picked up. Treat a Sell Box as public automation storage rather than a secure personal lockbox.
