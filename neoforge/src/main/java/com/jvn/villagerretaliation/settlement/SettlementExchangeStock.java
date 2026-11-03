package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

final class SettlementExchangeStock {
    private SettlementExchangeStock() { }
    record Debit(UUID depot, ItemStack item) { }
    record Plan(List<Debit> debits, String problem) { }
    static SettlementDemandService.Snapshot demands(ServerLevel level, SettlementRecord town) {
        SettlementDemandService.Snapshot[] result = {null};
        var work = SettlementDemandService.planAid(level, town, value -> result[0] = value);
        while (!SettlementWork.complete(work)) work.run();
        return result[0];
    }
    static Plan plan(ServerLevel level, SettlementRecord town, SettlementExchangeTerms.Bundle bundle) {
        if (town.economy().constructionFunds().compareTo(bundle.funds()) < 0) return new Plan(List.of(), "funds_unavailable");
        var demands = demands(level, town); var debits = new ArrayList<Debit>();
        long food = bundle.items().stream().mapToLong(SettlementAidStock::nutrition).sum();
        if (food > 0 && food > SettlementAidStock.nutrition(town) - SettlementAidStock.reserve(level, town, SettlementSavedData.get(level).aid().policy(town)))
            return new Plan(List.of(), "protected_food_reserve");
        for (var wanted : bundle.items()) {
            int remaining = wanted.getCount();
            for (var depot : SettlementAidStock.depots(town, false)) {
                if (!depot.rulesObserved() || SettlementSupplyStock.contents(level, town, depot.position()) == null) continue;
                int prior = debits.stream().filter(debit -> debit.depot.equals(depot.id()) && ItemStack.isSameItemSameComponents(debit.item, wanted))
                        .mapToInt(debit -> debit.item.getCount()).sum();
                int actual = 0;
                for (int slot = 0; slot < depot.getContainerSize(); slot++)
                    if (ItemStack.isSameItemSameComponents(depot.getItem(slot), wanted)) actual += depot.getItem(slot).getCount();
                int allowance = SettlementLogisticsService.framedAllowance(level, depot, wanted, VillagerFilterPolicy.TransferOperation.PROVIDE, true);
                int count = Math.min(remaining, Math.min(Math.max(0, Math.min(actual, allowance) - prior), demands.sellable(depot, wanted)));
                if (count <= 0) continue;
                var item = wanted.copyWithCount(count); debits.add(new Debit(depot.id(), item)); demands.sold(depot, item); remaining -= count;
                if (remaining == 0) break;
            }
            if (remaining != 0) return new Plan(List.of(), "goods_reserved_or_unavailable");
        }
        return new Plan(List.copyOf(debits), "");
    }
    static void debit(SettlementRecord town, SettlementExchangeTerms.Bundle bundle, Plan plan) {
        if (!plan.problem.isEmpty() || !town.economy().withdraw(bundle.funds())) throw new IllegalStateException("Exchange debit changed");
        for (var debit : plan.debits) {
            var depot = town.depots().get(debit.depot); int remaining = debit.item.getCount();
            for (int slot = 0; slot < depot.getContainerSize() && remaining > 0; slot++) {
                if (!ItemStack.isSameItemSameComponents(depot.getItem(slot), debit.item)) continue;
                int count = Math.min(remaining, depot.getItem(slot).getCount()); depot.removeItem(slot, count); remaining -= count;
            }
            if (remaining != 0) throw new IllegalStateException("Validated exchange reservation changed during debit");
        }
    }
    static void unload(ServerLevel level, SettlementRecord town, SettlementNegotiatedTrade.Cargo cargo, UUID depot, boolean refund) {
        if (cargo.empty()) { cargo.custody = SettlementNegotiatedTrade.Custody.CLOSED; return; }
        var remaining = new ArrayList<ItemStack>();
        for (var item : cargo.remaining) {
            var rest = depot == null ? SettlementAidStock.deposit(level, town, item, null) : SettlementAidStock.depositAt(level, town, item, depot);
            int count = item.getCount() - rest.getCount();
            if (count > 0) (refund ? cargo.returned : cargo.delivered).add(item.copyWithCount(count));
            if (!rest.isEmpty()) remaining.add(rest);
        }
        cargo.remaining.clear(); cargo.remaining.addAll(remaining);
        if (!cargo.funds.isZero()) {
            town.economy().credit(cargo.funds);
            if (refund) cargo.returnedFunds = cargo.returnedFunds.add(cargo.funds);
            else cargo.deliveredFunds = cargo.deliveredFunds.add(cargo.funds);
            cargo.funds = com.jvn.villagerretaliation.sell.CurrencyAmount.ZERO;
        }
        if (cargo.empty()) cargo.custody = SettlementNegotiatedTrade.Custody.CLOSED;
    }
}
