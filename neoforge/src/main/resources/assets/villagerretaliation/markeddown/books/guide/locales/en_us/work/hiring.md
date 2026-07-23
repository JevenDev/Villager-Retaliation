---
title: Hiring & Contracts
order: 10
icon: item:minecraft:emerald
keywords:
  - wage
  - contract
  - payment box
  - renewal
  - refund
---

# Hiring & Contracts

Hiring is available through an adult villager's interaction screen. A matching profession automatically qualifies for its associated role; otherwise the role's primary and support skills must total at least **61**. Courier is open to every adult, Nitwit work is nitwit-only, and Builder uses a separate project order.

## Price and duration

- Ordinary work is prepaid for **1–30 Minecraft days**.
- The default base wage is **12 emeralds per day**, clamped to a default **4–128** range after skill and reputation adjustments.
- The full selected duration is paid before work begins.
- An active ordinary worker can change roles without buying a new contract.
- Builder is quoted per project instead.

:::notice{type="info"}
Always trust the price shown in the confirmation screen. Servers can change every wage and duration value.
:::

## Automatic renewal

:::steps
:::step{title="Place and fund a Payment Box"}
Craft and fill a :item[villagerretaliation:payment_box] with the configured currency. It has 27 slots and is a wage source, not a secure lockbox.
:::
:::step{title="Assign it"}
Use the :item[villagerretaliation:clipboard] in **Payment** mode on the box, then on the hired villager.
:::
:::step{title="Enable recurring payment"}
When paid time expires, the worker tries to buy one more day.
:::
:::step{title="Resolve unpaid warnings"}
If renewal fails, work pauses for a one-day grace period. Restore or fund the assigned box before the grace ends.
:::
:::

:::recipe{id="villagerretaliation:payment_box" align="left"}
:::

Early cancellation refunds unused prepaid value at a configurable rate-**50% by default**. Unreturned job items can be claimed by the former controller for three Minecraft days. A hired contract alone does **not** grant downed-state protection.

