package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue;
import net.minecraft.server.level.ServerLevel;

public final class SettlementProductionStock {
    private SettlementProductionStock() { }

    public static long count(ServerLevel level, SettlementRecord town, HiredProductionQueue queue, HiredProductionOrder order) {
        if (order.mode() != HiredProductionOrder.Mode.MAINTAIN_STOCK) return 0L;
        if (!SettlementSupplyStock.permitsOutput(level, town, queue.owner(), order.storage())) return -1L;
        var facts = SettlementSupplyStock.observe(level, town, order.storage(), order.output(), queue);
        return facts.known() ? facts.stored() + facts.incoming() : -1L;
    }

    static long incoming(ServerLevel level, SettlementRecord town, net.minecraft.core.BlockPos destination,
                         net.minecraft.world.item.ItemStack sample, HiredProductionQueue excluded) {
        return SettlementSupplyStock.observe(level, town, destination, sample, excluded).incoming();
    }
}
