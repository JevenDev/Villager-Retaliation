package com.jvn.villagerretaliation.interaction.work.production;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

public final class HiredProductionEligibility {
    private HiredProductionEligibility() {
    }

    public static String blocker(ServerLevel level, HiredVillagerRole role, HiredProductionQueue queue,
            HiredProductionOrder order, UUID worker, long stock, boolean carryingOutput, BlockPos destination) {
        if (stock < 0L) return "output_storage_unavailable";
        var allocation = queue.allocation(worker);
        long pending = allocation != null && allocation.order().equals(order.id()) ? allocation.remaining() : 0L;
        if (queue.available(order.id(), stock) + pending <= 0L) return "quantity_reserved_or_stock_met";
        if (carryingOutput && !Objects.equals(destination, order.storage())) return "awaiting_output_delivery";
        if (!order.recipe().supports(level, role) || !ItemStack.matches(order.output(), order.recipe().output(level))) {
            return "recipe_unavailable";
        }
        return "";
    }
}
