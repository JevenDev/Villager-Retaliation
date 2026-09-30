package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue;
import net.minecraft.server.level.ServerLevel;

public final class SettlementProductionStock {
    private SettlementProductionStock() { }

    public static long count(ServerLevel level, SettlementRecord town, HiredProductionQueue queue, HiredProductionOrder order) {
        return count(level, town, queue, order, new SettlementSupplyStock.Snapshot(level, town));
    }

    static long count(ServerLevel level, SettlementRecord town, HiredProductionQueue queue, HiredProductionOrder order,
                      SettlementSupplyStock.Snapshot snapshot) {
        if (order.mode() != HiredProductionOrder.Mode.MAINTAIN_STOCK) return 0L;
        if (!SettlementSupplyStock.permitsOutput(level, town, queue.owner(), order.storage())) return -1L;
        var facts = snapshot.observe(order.storage(), order.output(), queue);
        if (!facts.known()) return -1;
        long aliases = queue.allocations().stream().filter(allocation -> {
            var allocated = queue.order(allocation.order());
            return allocated.matches(order.output()) && !java.util.Objects.equals(allocated.storage(), order.storage())
                    && SettlementSupplyStock.sameStorage(level, allocated.storage(), order.storage());
        }).mapToLong(HiredProductionQueue.Allocation::remaining).sum();
        return facts.stored() + facts.incoming() + aliases;
    }

    static long incoming(ServerLevel level, SettlementRecord town, net.minecraft.core.BlockPos destination,
                         net.minecraft.world.item.ItemStack sample, HiredProductionQueue excluded) {
        return SettlementSupplyStock.observe(level, town, destination, sample, excluded).incoming();
    }
}
