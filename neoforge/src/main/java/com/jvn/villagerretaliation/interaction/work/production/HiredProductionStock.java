package com.jvn.villagerretaliation.interaction.work.production;

import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.settlement.SettlementProductionStock;
import com.jvn.villagerretaliation.settlement.SettlementWorkforceService;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class HiredProductionStock {
    private static final String DELIVERY_STORAGE = "ProductionDeliveryStorage";

    private HiredProductionStock() {
    }

    public static ItemStack product(ItemStack stack) {
        var result = stack.copy();
        var data = result.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!data.contains("VillagerRetaliationJobItem") && !data.contains("VillagerRetaliationCourierDelivery")) return result;
        data.remove("VillagerRetaliationJobItem");
        data.remove("VillagerRetaliationCourierDelivery");
        if (data.isEmpty()) result.remove(DataComponents.CUSTOM_DATA);
        else result.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return result;
    }

    public static boolean matches(ItemStack sample, ItemStack candidate) {
        return ItemStack.isSameItemSameComponents(product(sample), product(candidate));
    }

    public static BlockPos destination(CompoundTag state) {
        return state.contains(DELIVERY_STORAGE, Tag.TAG_LONG) ? BlockPos.of(state.getLong(DELIVERY_STORAGE)) : null;
    }

    public static void clearDestination(CompoundTag state) {
        state.remove(DELIVERY_STORAGE);
    }

    public static void copyDelivery(CompoundTag source, CompoundTag target) {
        clearDestination(target);
        if (source.contains(DELIVERY_STORAGE, Tag.TAG_LONG)) target.putLong(DELIVERY_STORAGE, source.getLong(DELIVERY_STORAGE));
        target.remove("CraftsmanProductionAllocation");
    }

    public static boolean permitsDestination(CompoundTag state, BlockPos position) {
        var destination = destination(state);
        return destination == null || destination.equals(position);
    }

    public static boolean prepareDestination(CompoundTag state, boolean carryingOutput, HiredProductionOrder order) {
        if (carryingOutput && !Objects.equals(destination(state), order.storage())) return false;
        if (order.storage() == null) state.remove(DELIVERY_STORAGE);
        else state.putLong(DELIVERY_STORAGE, order.storage().asLong());
        return true;
    }

    public static long count(ServerLevel level, Villager villager, HiredProductionQueue queue, HiredProductionOrder order) {
        var duty = SettlementWorkforceService.duty(villager).orElse(null);
        if (duty != null) {
            if (order.storage() != null && !com.jvn.villagerretaliation.settlement.SettlementSupplyStock.workerOutput(level, duty.town(), villager, order.storage())) return -1L;
            return SettlementProductionStock.count(level, duty.town(), queue, order);
        }
        if (order.mode() != HiredProductionOrder.Mode.MAINTAIN_STOCK) return 0L;
        var commitments = HiredProductionCommitments.get(level);
        HiredProductionCommitments.observe(villager);
        commitments.refresh(level, order.storage());
        long incoming = commitments.count(order.storage(), order.output(), villager.getUUID());
        if (incoming < 0L) return -1L;
        var contributors = commitments.contributors(order.storage(), order.output());
        contributors.add(villager.getUUID());
        long stored = AssignedStorageService.productionStockAt(villager, order.storage(), stack -> matches(order.output(), stack), contributors);
        if (stored < 0L) return -1L;
        stored += incoming;
        var inventory = HiredJobInventory.getJobInventory(villager);
        if (order.storage().equals(destination(HiredWorkStateStore.state(villager)))) {
            for (int slot : inventory.outputSlots()) {
                var stack = inventory.getItem(slot);
                if (matches(order.output(), stack)) stored += stack.getCount();
            }
        }
        return stored;
    }

}
