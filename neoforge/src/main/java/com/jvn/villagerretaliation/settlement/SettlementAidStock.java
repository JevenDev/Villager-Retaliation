package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

final class SettlementAidStock {
    private SettlementAidStock() { }
    record Quote(long revision, List<SettlementFoodOffer.Parcel> parcels, String blocker) {
        Quote { parcels = List.copyOf(parcels); }
        long nutrition() { return parcels.stream().mapToLong(parcel -> SettlementAidStock.nutrition(parcel.item())).sum(); }
        static Quote blocked(SettlementRecord town, String reason) { return new Quote(town.revision(), List.of(), reason); }
    }
    static List<SettlementDepot> depots(SettlementRecord town, boolean receive) {
        return SettlementStock.registered(town, null).stream()
                .filter(depot -> receive ? depot.purpose().acceptsOutputs() : depot.purpose().providesSupplies()).toList();
    }
    static long nutrition(ItemStack stack) {
        return SettlementFoodService.isMeal(stack) ? (long) stack.getCount() * stack.get(DataComponents.FOOD).nutrition() : 0;
    }
    static long nutrition(SettlementRecord town) {
        long result = 0;
        for (var depot : depots(town, false)) for (int slot = 0; slot < depot.getContainerSize(); slot++) result += nutrition(depot.getItem(slot));
        return result;
    }
    static long reserve(ServerLevel level, SettlementRecord town, SettlementAidPolicy policy) {
        int population = com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData.get(level).residentCount(town.id(), level.getGameTime());
        return Math.max(SettlementFoodService.reserveNutrition(level, town), (long) population * SettlementFoodService.dailyNutrition() * policy.reserveDays());
    }
    static Quote quote(ServerLevel level, SettlementRecord town, SettlementAidPolicy policy, SettlementDemandService.Snapshot demands,
                       long capturedRevision, long requestedNutrition, int maximumItems) {
        return quote(level, town, policy, demands, capturedRevision, requestedNutrition, maximumItems, null);
    }
    static Quote quote(ServerLevel level, SettlementRecord town, SettlementAidPolicy policy, SettlementDemandService.Snapshot demands,
                       long capturedRevision, long requestedNutrition, int maximumItems, UUID sourceDepot) {
        if (town.revision() != capturedRevision) return Quote.blocked(town, "stock_changed");
        long available = Math.min(requestedNutrition, Math.max(0, nutrition(town) - reserve(level, town, policy)));
        if (available <= 0) return Quote.blocked(town, "protected_food_reserve");
        int remainingItems = Math.min(maximumItems, policy.maximumItems());
        var parcels = new ArrayList<SettlementFoodOffer.Parcel>();
        for (var depot : depots(town, false)) {
            if (sourceDepot != null && !sourceDepot.equals(depot.id())) continue;
            if (!depot.rulesObserved() || SettlementSupplyStock.contents(level, town, depot.position()) == null) continue;
            for (int slot = 0; slot < depot.getContainerSize() && parcels.size() < 27 && remainingItems > 0; slot++) {
                var item = depot.getItem(slot);
                if (!SettlementFoodService.isMeal(item)) continue;
                int points = item.get(DataComponents.FOOD).nutrition();
                int allowed = Math.min(item.getCount(), demands.sellable(depot, item));
                int prior = parcels.stream().filter(parcel -> parcel.depot().equals(depot.id()) && ItemStack.isSameItemSameComponents(parcel.item(), item))
                        .mapToInt(parcel -> parcel.item().getCount()).sum();
                allowed = Math.min(allowed, Math.max(0, SettlementLogisticsService.framedAllowance(level, depot, item, VillagerFilterPolicy.TransferOperation.PROVIDE, true) - prior));
                int count = (int) Math.min(Math.min(remainingItems, allowed), available / points);
                if (count <= 0) continue;
                var reserved = item.copyWithCount(count);
                parcels.add(new SettlementFoodOffer.Parcel(depot.id(), reserved)); demands.sold(depot, reserved);
                available -= (long) count * points; remainingItems -= count;
            }
        }
        return parcels.isEmpty() ? Quote.blocked(town, "food_allocated_or_unavailable") : new Quote(town.revision(), parcels, "");
    }

    static boolean reserve(ServerLevel level, SettlementRecord donor, SettlementFoodRequest request, Quote quote,
                           SettlementFoodOffer.Arrangement arrangement, UUID courier) {
        var data = SettlementSavedData.get(level);
        var aid = data.aid();
        if (!level.getServer().isSameThread() || data.readOnly() || !aid.writable() || aid.request(request.id()) != request
                || data.get(request.donor()).orElse(null) != donor || request.terminal()
                || request.offer != null || quote.parcels().isEmpty() || !quote.blocker().isEmpty() || donor.revision() != quote.revision()
                || quote.nutrition() > request.nutrition() || quote.nutrition() > nutrition(donor) - reserve(level, donor, aid.policy(donor))) return false;
        var stock = new SettlementSupplyStock.Snapshot(level, donor);
        var checked = new ArrayList<SettlementFoodOffer.Parcel>();
        for (var parcel : quote.parcels()) {
            var depot = donor.depots().get(parcel.depot()); var item = parcel.item();
            if (depot == null || !depots(donor, false).contains(depot) || !depot.rulesObserved()) return false;
            var facts = stock.observe(depot.position(), item, null);
            long prior = checked.stream().filter(value -> value.depot().equals(depot.id()) && ItemStack.isSameItemSameComponents(value.item(), item))
                    .mapToLong(value -> value.item().getCount()).sum();
            int allowance = SettlementLogisticsService.framedAllowance(level, depot, item, VillagerFilterPolicy.TransferOperation.PROVIDE, true);
            if (!facts.known() || facts.available() < prior + item.getCount() || allowance < prior + item.getCount()) return false;
            checked.add(parcel);
        }
        var offer = new SettlementFoodOffer(arrangement, courier, quote.parcels());
        for (var parcel : quote.parcels()) {
            var depot = donor.depots().get(parcel.depot()); var item = parcel.item(); int remaining = item.getCount();
            for (int slot = 0; slot < depot.getContainerSize() && remaining > 0; slot++) {
                if (!ItemStack.isSameItemSameComponents(depot.getItem(slot), item)) continue;
                int count = Math.min(remaining, depot.getItem(slot).getCount()); depot.removeItem(slot, count); remaining -= count;
            }
            if (remaining != 0) throw new IllegalStateException("Validated food reservation changed during debit");
        }
        request.offer = offer; aid.changed(request); data.setDirty();
        return true;
    }

    static int deliver(ServerLevel level, SettlementRecord recipient, SettlementFoodRequest request) {
        var data = SettlementSavedData.get(level); var aid = data.aid();
        if (!level.getServer().isSameThread() || data.readOnly() || !aid.writable() || aid.request(request.id()) != request
                || data.get(request.requester()).orElse(null) != recipient || request.terminal()
                || request.offer == null || request.offer.custody() != SettlementFoodOffer.Custody.COURIER || aid.receipts.size() >= SettlementAidLedger.RECEIPT_LIMIT)
            return 0;
        var remaining = new ArrayList<SettlementFoodOffer.Parcel>(); int delivered = 0;
        long nutrition = 0;
        for (var parcel : request.offer.remaining()) {
            var item = parcel.item(); var rest = deposit(level, recipient, item, null);
            int count = item.getCount() - rest.getCount(); delivered += count;
            nutrition += (long) count * item.get(DataComponents.FOOD).nutrition();
            if (!rest.isEmpty()) remaining.add(new SettlementFoodOffer.Parcel(parcel.depot(), rest));
        }
        if (delivered == 0) return 0;
        request.offer.replaceRemaining(remaining); request.deliveredNutrition += nutrition;
        if (remaining.isEmpty()) request.offer.custody = SettlementFoodOffer.Custody.DELIVERED;
        int earned = (int) Math.min(3, (request.deliveredNutrition * 3 + request.nutrition() - 1) / request.nutrition());
        if (earned > request.creditedStanding) aid.receipt(request, earned - request.creditedStanding, level.getServer().overworld().getGameTime());
        else aid.changed(request);
        data.setDirty();
        return delivered;
    }

    static boolean refund(ServerLevel level, SettlementRecord donor, SettlementFoodRequest request) {
        var data = SettlementSavedData.get(level);
        if (!level.getServer().isSameThread() || data.readOnly() || !data.aid().writable() || data.aid().request(request.id()) != request
                || data.get(request.donor()).orElse(null) != donor
                || request.offer == null || request.offer.custody() != SettlementFoodOffer.Custody.RESERVED) return false;
        var remaining = new ArrayList<SettlementFoodOffer.Parcel>();
        for (var parcel : request.offer.remaining()) {
            var rest = deposit(level, donor, parcel.item(), parcel.depot());
            if (!rest.isEmpty()) remaining.add(new SettlementFoodOffer.Parcel(parcel.depot(), rest));
        }
        request.offer.replaceRemaining(remaining);
        if (remaining.isEmpty()) request.offer.custody = SettlementFoodOffer.Custody.RETURNED;
        data.aid().changed(request); data.setDirty();
        return remaining.isEmpty();
    }

    private static ItemStack deposit(ServerLevel level, SettlementRecord town, ItemStack item, UUID preferred) {
        var remaining = item.copy();
        var depots = new ArrayList<>(depots(town, true));
        depots.sort(java.util.Comparator.comparing(depot -> !depot.id().equals(preferred)));
        for (var depot : depots) {
            if (!depot.rulesObserved() || SettlementSupplyStock.contents(level, town, depot.position()) == null) continue;
            int count = Math.min(remaining.getCount(), SettlementLogisticsService.framedAllowance(level, depot, remaining,
                    VillagerFilterPolicy.TransferOperation.RECEIVE, true));
            if (count <= 0) continue;
            var rest = depot.deposit(remaining.copyWithCount(count)); remaining.shrink(count - rest.getCount());
            if (remaining.isEmpty()) break;
        }
        return remaining;
    }
}
