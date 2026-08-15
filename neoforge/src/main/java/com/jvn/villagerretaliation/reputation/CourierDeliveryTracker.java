package com.jvn.villagerretaliation.reputation;

import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Carries lightweight courier provenance on delivered stacks until another worker consumes them.
 */
public final class CourierDeliveryTracker {
    private static final String TAG = "VillagerRetaliationCourierDelivery";
    private static final String TAG_COURIER = "Courier";
    private static final String TAG_HIRER = "Hirer";

    private CourierDeliveryTracker() {
    }

    public static ItemStack markDelivered(ItemStack stack, Villager courier) {
        if (stack == null || stack.isEmpty() || courier == null
                || !(courier.level() instanceof ServerLevel level)) {
            return stack;
        }
        Optional<UUID> hirerId = HiredVillagerContractService.getHirer(level, courier);
        if (hirerId.isEmpty()) {
            return stack;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
            CompoundTag delivery = new CompoundTag();
            delivery.putUUID(TAG_COURIER, courier.getUUID());
            delivery.putUUID(TAG_HIRER, hirerId.get());
            root.put(TAG, delivery);
        });
        return stack;
    }

    public static void onMaterialUsed(Villager worker, ItemStack stack) {
        if (worker == null || stack == null || stack.isEmpty()
                || !(worker.level() instanceof ServerLevel level)) {
            return;
        }
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty() || !customData.contains(TAG)) {
            return;
        }
        CompoundTag root = customData.copyTag();
        if (!root.contains(TAG, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag delivery = root.getCompound(TAG);
        if (!delivery.hasUUID(TAG_COURIER)
                || !delivery.hasUUID(TAG_HIRER)
                || delivery.getUUID(TAG_COURIER).equals(worker.getUUID())) {
            return;
        }
        UUID hirerId = delivery.getUUID(TAG_HIRER);
        if (HiredVillagerContractService.getHirer(level, worker).filter(hirerId::equals).isEmpty()) {
            return;
        }
        ServerPlayer hirer = level.getServer().getPlayerList().getPlayer(hirerId);
        if (hirer != null) {
            VillagerReputationAdvancements.onCourierMaterialUsed(hirer);
        }
    }
}
