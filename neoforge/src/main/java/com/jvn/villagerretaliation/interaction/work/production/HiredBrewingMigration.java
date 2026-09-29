package com.jvn.villagerretaliation.interaction.work.production;

import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker;
import com.jvn.villagerretaliation.interaction.work.brewing.HiredBrewingRecipeCatalog;
import com.jvn.villagerretaliation.settlement.SettlementBrewingStandState;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

final class HiredBrewingMigration {
    private HiredBrewingMigration() {
    }

    static String append(ServerLevel level, Villager villager, UUID contract, HiredProductionOrder requested) {
        var state = HiredWorkStateStore.state(villager);
        if (state.hasUUID("BrewingOrderContractId") && !contract.equals(state.getUUID("BrewingOrderContractId"))) {
            return "The legacy brewing order belongs to another contract";
        }
        var item = ResourceLocation.tryParse(state.getString("BrewingTargetItem"));
        var potion = ResourceLocation.tryParse(state.getString("BrewingTargetPotion"));
        var route = HiredBrewingRecipeCatalog.find(level, item, potion).orElse(null);
        boolean continuous = state.getBoolean("BrewingContinuous");
        int amount = continuous ? 1 : state.getInt("BrewingRemaining");
        if (route == null || amount < 1 || amount > HiredProductionOrder.MAX_AMOUNT) return "The legacy brewing order requires recovery";
        var stations = HiredProductionStations.get(level);
        if (stations.readOnly()) return "Production station data requires recovery";
        var queue = new HiredProductionQueue(contract, HiredProductionQueue.Scope.PERSONAL, HiredVillagerRole.BREWING, ignored -> {});
        var legacy = HiredProductionOrder.create(level, HiredProductionRecipe.brewing(item, potion),
                continuous ? HiredProductionOrder.Mode.CONTINUOUS : HiredProductionOrder.Mode.BATCH, amount, null);
        queue.append(legacy);
        if (!queue.append(requested)) return "The production order already exists";
        BrewingStandBlockEntity stand = null;
        if (state.contains("BrewingCachedStandPos", Tag.TAG_LONG)) {
            var position = BlockPos.of(state.getLong("BrewingCachedStandPos"));
            var area = HiredWorkArea.fromState(state, villager.blockPosition());
            if (!area.usable() || !area.contains(position)) return "Restore the brewer's work area before converting its active order";
            if (!level.hasChunkAt(position)) return "Load the brewer's active stand before converting its order";
            if (level.getBlockEntity(position) instanceof BrewingStandBlockEntity brewingStand) stand = brewingStand;
        }
        if (stand != null && BrewingWorker.loadedBottleCount(stand) > 0) {
            if (!stations.available(level, stand.getBlockPos()) || !BrewingWorker.canMigrateBatch(level, stand, route)) {
                return "The active brewing batch cannot be matched to its legacy order";
            }
            int count = BrewingWorker.loadedBottleCount(stand);
            for (int slot = 0; slot < 3; slot++) if (stand.getItem(slot).getCount() > 1) return "The active brewing batch requires recovery";
            var allocation = queue.reserve(villager.getUUID(), legacy.id(), count, 1, 0L, stand.getBlockPos());
            if (allocation == null || !queue.bindMachine(allocation.id(), SettlementBrewingStandState.identity(stand)) || !queue.start(allocation.id())
                    || !stations.reserve(level, queue, queue.allocation(villager.getUUID()), SettlementBrewingStandState.identity(stand))) {
                return "The active brewing stand could not be reserved";
            }
        }
        state.put(HiredProductionService.STATE_TAG, queue.save(level.registryAccess()));
        BrewingWorker.clearOrder(state);
        HiredProductionCommitments.observe(villager);
        HiredWorkSession.invalidate(villager);
        return "";
    }
}
