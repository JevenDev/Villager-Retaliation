package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.sell.CurrencyAmount;
import com.jvn.villagerretaliation.sell.SellPriceResources;
import com.jvn.villagerretaliation.sell.VillageSellMarket;
import net.minecraft.server.level.ServerLevel;

public final class SettlementTradeEvaluation {
    private SettlementTradeEvaluation() { }
    public record Evaluation(CurrencyAmount received, CurrencyAmount required, CurrencyAmount transport, String problem) {
        public boolean acceptable() { return problem.isEmpty() && received.compareTo(required) >= 0; }
    }
    static CurrencyAmount value(ServerLevel level, SettlementExchangeTerms.Bundle bundle, SettlementDemandService.Snapshot needs) {
        var total = bundle.funds();
        var demands = needs.demands(); var missing = demands.stream().mapToLong(SettlementDemandService.Demand::missing).toArray();
        for (var item : bundle.items()) {
            var definition = SellPriceResources.definition(level.getServer(), item).orElse(null);
            if (definition == null) return null;
            var base = VillageSellMarket.sharedBasePrice(level.getServer(), definition).multiply(item.getCount());
            long wanted = 0;
            for (int i = 0; i < demands.size() && wanted < item.getCount(); i++) {
                long units = demands.get(i).requirement().value(item);
                if (missing[i] <= 0 || units <= 0) continue;
                long used = Math.min(item.getCount() - wanted, (missing[i] + units - 1) / units);
                wanted += used; missing[i] = Math.max(0, missing[i] - used * units);
            }
            total = total.add(base).add(base.multiplyRatio(wanted, 2L * item.getCount()));
        }
        return total;
    }
    static Evaluation evaluate(ServerLevel level, SettlementRecord first, SettlementRecord second, SettlementExchangeTerms terms, boolean firstEvaluates) {
        if (java.util.stream.Stream.concat(terms.first().items().stream(), terms.second().items().stream())
                .anyMatch(item -> com.jvn.villagerretaliation.interaction.VillagerCurrencyResources.isCurrency(level.getServer(), item)))
            return new Evaluation(CurrencyAmount.ZERO, CurrencyAmount.ZERO, CurrencyAmount.ZERO, "currency_as_goods");
        if (java.util.stream.Stream.concat(terms.first().items().stream(), terms.second().items().stream())
                .anyMatch(item -> SellPriceResources.definition(level.getServer(), item).isEmpty()))
            return new Evaluation(CurrencyAmount.ZERO, CurrencyAmount.ZERO, CurrencyAmount.ZERO, "unpriced_item");
        var town = firstEvaluates ? first : second; var needs = SettlementExchangeStock.demands(level, town);
        var firstItems = new java.util.ArrayList<>(terms.first().items()); var secondItems = new java.util.ArrayList<>(terms.second().items());
        for (var firstItem : firstItems) for (var secondItem : secondItems) {
            if (!net.minecraft.world.item.ItemStack.isSameItemSameComponents(firstItem, secondItem)) continue;
            int shared = Math.min(firstItem.getCount(), secondItem.getCount()); firstItem.shrink(shared); secondItem.shrink(shared);
        }
        var firstBundle = new SettlementExchangeTerms.Bundle(firstItems.stream().filter(item -> !item.isEmpty()).toList(), terms.first().funds());
        var secondBundle = new SettlementExchangeTerms.Bundle(secondItems.stream().filter(item -> !item.isEmpty()).toList(), terms.second().funds());
        var gives = firstEvaluates ? firstBundle : secondBundle; var receives = firstEvaluates ? secondBundle : firstBundle;
        var outgoing = value(level, gives, needs); var incoming = value(level, receives, needs);
        if (outgoing == null || incoming == null) return new Evaluation(CurrencyAmount.ZERO, CurrencyAmount.ZERO, CurrencyAmount.ZERO, "unpriced_item");
        long distance = (long) Math.ceil(Math.sqrt(first.center().distSqr(second.center())));
        var outgoingGoods = outgoing.subtract(gives.funds()); var incomingGoods = incoming.subtract(receives.funds());
        var transport = outgoingGoods.add(incomingGoods).multiplyRatio(Math.min(20, 5 + distance / 128), 100);
        boolean sends = terms.firstSends() == firstEvaluates;
        int standing = com.jvn.villagerretaliation.diplomacy.VillageDiplomacySavedData.get(level).standing(first.id(), second.id());
        var required = gives.funds().add(outgoingGoods.multiplyRatio(100 - Math.min(10, Math.max(0, standing) / 10), 100));
        return new Evaluation(sends ? incoming : incoming.add(transport), sends ? required.add(transport) : required, transport, "");
    }
    static SettlementExchangeTerms quote(ServerLevel level, SettlementRecord first, SettlementRecord second, SettlementExchangeTerms draft, boolean firstEvaluates) {
        var evaluation = evaluate(level, first, second, draft, firstEvaluates);
        if (!evaluation.problem().isEmpty()) return null;
        boolean pays = evaluation.acceptable();
        var extra = pays ? evaluation.received().subtract(evaluation.required()) : evaluation.required().subtract(evaluation.received());
        var bundle = firstEvaluates == pays ? draft.first() : draft.second();
        var funds = bundle.funds().add(extra);
        if (funds.compareTo(CurrencyAmount.of(1_000_000, 1)) > 0 || funds.numerator().bitLength() > 64 || funds.denominator().bitLength() > 32) return null;
        var adjusted = new SettlementExchangeTerms.Bundle(bundle.items(), funds);
        return new SettlementExchangeTerms(firstEvaluates == pays ? adjusted : draft.first(), firstEvaluates == pays ? draft.second() : adjusted, draft.firstSends());
    }
}
