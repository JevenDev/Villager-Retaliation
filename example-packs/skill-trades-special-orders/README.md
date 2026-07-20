# Skill Trades and Special Orders

This example adds four custom skill trades under the `trade_examples` namespace. It demonstrates:

- a normal farmer trade with skill-based quality scaling
- a targetable, prepaid farmer Special Order
- a targetable cartographer order with multiple possible results
- an enchanted fletcher trade gated by a config flag

Install the folder in a world's `datapacks` directory, ensure the Skill Trade Overhaul and Special Orders are enabled, and run `/reload`. Existing loaded villagers reconcile the new definitions into their current weighted request cycle; you do not need to spawn replacement villagers.

The `chance` field affects initial natural trade generation. Requested random refreshes use `weight` to order the persistent without-replacement cycle and consider every otherwise eligible entry. A larger weight makes a definition likely to appear earlier, but it does not allow that definition to repeat before the current eligible pool is exhausted.

Special Orders require `request.targetable: true`, the configured reputation level, and an unlocked villager skill rank. The `extra_cost` is prepaid when the order is accepted. If the definition is removed or becomes invalid before fulfillment, the same villager holds that payment until its owner next interacts with it.

Validate either resource directly:

```text
node tools/validate-dialogue-data.mjs --skill-trade example-packs/skill-trades-special-orders/data/trade_examples/skill_trades/farming_orders.json
node tools/validate-dialogue-data.mjs --skill-trade example-packs/skill-trades-special-orders/data/trade_examples/skill_trades/profession_specialties.json
```

Every entry uses a stable namespaced `id`. Change the `trade_examples` namespace and ids together when adapting this pack.
