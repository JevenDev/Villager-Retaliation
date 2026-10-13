package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

public final class SettlementSupplyRecovery {
    private SettlementSupplyRecovery() { }

    public enum Cause { READY, MISSING_STOCK, COMMITTED, CONNECTION, FILTER, CAPACITY, STATION, STAFFING, PAUSED, UNKNOWN, UNREACHABLE, ALLOCATED }
    public record Recovery(SettlementDemandService.Demand demand, Cause cause, String advice, BlockPos focus,
                           UUID courier, HiredTransferQueue.Endpoint source, HiredTransferQueue.Endpoint destination, ItemStack sample) {
        public Recovery { sample = sample.copy(); }
        @Override public ItemStack sample() { return sample.copy(); }
    }

    public static List<Recovery> inspect(ServerLevel level, SettlementRecord town, SettlementDemandService.Snapshot demand) {
        var stock = demand.stock();
        var positions = new LinkedHashSet<BlockPos>();
        SettlementStock.depots(town, com.jvn.villagerretaliation.interaction.HiredVillagerRole.COURIER).forEach(depot -> positions.add(depot.position()));
        for (var plan : town.worksitePlans().values()) for (var link : plan.settings().storage().entries())
            positions.add(SettlementSupplyStock.positions(level, link.position()).getFirst());
        var contents = new java.util.LinkedHashMap<BlockPos, List<ItemStack>>();
        for (var position : positions) contents.put(position, demand.visible(position) ? SettlementSupplyStock.contents(level, town, position) : null);
        var result = new ArrayList<Recovery>();
        for (var entry : demand.demands()) result.add(explain(level, town, stock, contents, demand, entry));
        return List.copyOf(result);
    }

    private static Recovery explain(ServerLevel level, SettlementRecord town, SettlementSupplyStock.Snapshot stock,
                                    java.util.Map<BlockPos, List<ItemStack>> contents, SettlementDemandService.Snapshot projection, SettlementDemandService.Demand demand) {
        var area = town.areas().get(demand.area());
        var worker = area == null ? level.getEntity(demand.area()) : null;
        var focus = area != null ? area.center() : worker != null ? worker.blockPosition() : town.center();
        String item = com.jvn.villagerretaliation.interaction.work.production.WorkSupplyEvaluation.name(demand.requirement());
        String prefix = demand.name() + " needs " + item + ". ";
        String blocker = demand.blocker();
        if (demand.missing() < 0) return result(demand, Cause.UNKNOWN, prefix + "Observe the configured storage or station to determine what is available.", focus);
        var competing = demand.supply().blocker().competingConsumer();
        if (competing != null) {
            var other = projection.demands().stream().filter(candidate -> candidate.consumer().equals(competing)
                    && candidate.requirement().alternatives().stream().anyMatch(sample -> demand.requirement().value(sample) > 0))
                    .findFirst().orElse(null);
            if (other != null) return result(demand, Cause.ALLOCATED,
                    net.minecraft.network.chat.Component.translatable("villagerretaliation.gui.settlement.demand_allocated",
                            other.name(), other.supply().priority(), other.supply().protectedMinimum()).getString(), focus);
        }
        if (blocker.contains("unreachable") || blocker.contains("path_failed"))
            return result(demand, Cause.UNREACHABLE, prefix + "The worker could not reach its target. Inspect its existing path diagnostic.", focus);
        if (blocker.contains("station") || blocker.contains("furnace") || blocker.contains("crafting_table") || blocker.contains("brewing_stand"))
            return result(demand, Cause.STATION, prefix + "Check the configured station and its current batch.", focus);
        if (blocker.contains("full") || blocker.contains("capacity"))
            return result(demand, Cause.CAPACITY, prefix + "Free space in the configured destination.", focus);
        if (demand.missing() == 0) {
            if (demand.immediateMissing() > 0) return result(demand, Cause.COMMITTED,
                    net.minecraft.network.chat.Component.translatable("villagerretaliation.gui.settlement.demand_incoming", prefix).getString(), focus);
            if (demand.kind() == com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.FOOD
                    || demand.kind() == com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.STOCK_RESERVE
                    || demand.kind() == com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.PLANTING)
                return result(demand, Cause.READY,
                        net.minecraft.network.chat.Component.translatable("villagerretaliation.gui.settlement.demand_reserve_ready").getString(), focus);
            if (area != null && town.duties().values().stream().noneMatch(demand.area()::equals))
                return result(demand, Cause.STAFFING, "Inputs are covered. Assign a worker to " + demand.name() + ".", focus);
            return result(demand, Cause.READY, "Inputs are covered for " + demand.name() + ".", focus);
        }
        Recovery fallback = result(demand, Cause.MISSING_STOCK, prefix + "No available matching stock was observed. Configure production, purchasing, or a delivery.", focus);
        for (var entry : contents.entrySet()) {
            if (entry.getValue() == null) continue;
            for (var sample : entry.getValue()) {
                if (demand.requirement().value(sample) <= 0) continue;
                var facts = stock.observe(entry.getKey(), sample, null);
                if (!facts.known()) continue;
                boolean connected = demand.sources().stream().anyMatch(position -> SettlementSupplyStock.sameStorage(level, position, entry.getKey()));
                if (facts.available() == 0 && facts.stored() > 0) {
                    fallback = result(demand, Cause.COMMITTED, prefix + "Matching stock at " + entry.getKey().toShortString() + " is committed to other work.", entry.getKey());
                    continue;
                }
                if (facts.available() <= 0) continue;
                var depot = town.depots().values().stream().filter(value -> value.position().equals(entry.getKey())).findFirst().orElse(null);
                int allowance = depot == null ? AssignedStorageService.unreservedFramedAllowance(level, entry.getKey(), sample, VillagerFilterPolicy.TransferOperation.PROVIDE)
                        : AssignedStorageService.observedTransferAllowance(level, entry.getKey(), depot, depot.observedRules(), sample, VillagerFilterPolicy.TransferOperation.PROVIDE);
                if (allowance <= 0) {
                    fallback = result(demand, Cause.FILTER, prefix + "Matching stock exists at " + entry.getKey().toShortString() + " but its source filter or reserve prevents pickup.", entry.getKey());
                    continue;
                }
                if (connected) continue;
                if (facts.available() <= projection.covered(entry.getKey(), sample)) {
                    fallback = result(demand, Cause.COMMITTED, prefix + "Matching stock at " + entry.getKey().toShortString() + " is covered by other active consumers.", entry.getKey());
                    continue;
                }
                var destination = demand.sources().stream().filter(position -> contents.get(position) != null).findFirst().orElse(null);
                if (destination == null) return result(demand, Cause.CONNECTION, prefix + "Stock exists at " + entry.getKey().toShortString() + ". Configure permitted input storage for this consumer.", entry.getKey());
                var sourceEndpoint = endpoint(town, entry.getKey());
                var destinationEndpoint = endpoint(town, destination);
                var candidate = HiredTransferQueue.Order.create(sourceEndpoint, destinationEndpoint, sample, 1);
                for (var queue : town.transferQueues().values()) for (var order : queue.orders()) {
                    if (!order.matches(sample) || order.cancelled() || order.complete()
                            || !SettlementSupplyStock.sameStorage(level, order.source().position(), entry.getKey())
                            || !SettlementSupplyStock.sameStorage(level, order.destination().position(), destination)) continue;
                    if (order.paused()) return result(demand, Cause.PAUSED, prefix + "The configured delivery is paused.", destination);
                    if (order.blocker().contains("unreachable")) return result(demand, Cause.UNREACHABLE,
                            prefix + "The configured courier could not reach its endpoint. Inspect its path diagnostic.", destination);
                    if (order.blocker().contains("destination_full")) return result(demand, Cause.CAPACITY,
                            prefix + "The configured delivery has no destination capacity. Free space at its endpoint.", destination);
                    if (order.blocker().contains("unobserved") || order.blocker().contains("unloaded")) return result(demand, Cause.UNKNOWN,
                            prefix + "The configured delivery endpoint is unavailable to this worker. Observe the endpoint or use a loaded courier.", destination);
                    if (town.duties().values().stream().noneMatch(queue.owner()::equals)) return result(demand, Cause.STAFFING,
                            prefix + "Assign a courier to the configured delivery worksite.", destination);
                    return result(demand, Cause.CONNECTION, prefix + "A delivery is configured. Check its courier, filters, and destination capacity.", destination);
                }
                var courier = town.areas().values().stream().filter(value -> value.enabled() && value.type() == SettlementAreaType.DEPOT
                        && town.worksitePlans().containsKey(value.id()) && SettlementTransferService.endpoints(level, town, value.id(), candidate, null))
                        .map(SettlementArea::id).sorted().findFirst().orElse(null);
                return new Recovery(demand, Cause.CONNECTION, prefix + "Stock is available at " + entry.getKey().toShortString()
                        + ". No configured delivery supplies " + destination.toShortString() + ".", entry.getKey(), courier,
                        sourceEndpoint, destinationEndpoint, sample.copyWithCount(1));
            }
        }
        return fallback;
    }

    private static Recovery result(SettlementDemandService.Demand demand, Cause cause, String advice, BlockPos focus) {
        return new Recovery(demand, cause, advice, focus, null, null, null, ItemStack.EMPTY);
    }

    private static HiredTransferQueue.Endpoint endpoint(SettlementRecord town, BlockPos position) {
        var depot = town.depots().values().stream().filter(value -> value.position().equals(position)).findFirst().orElse(null);
        return new HiredTransferQueue.Endpoint(position, depot == null ? null : depot.id());
    }
}
