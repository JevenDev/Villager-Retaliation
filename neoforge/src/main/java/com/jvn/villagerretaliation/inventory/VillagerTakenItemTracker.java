package com.jvn.villagerretaliation.inventory;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class VillagerTakenItemTracker {
    private static final String TAG = "VillagerRetaliationTakenVillagerItem";
    private static final String OWNER_VILLAGER_TAG = "OwnerVillager";
    private static final String OWNER_VILLAGER_NAME_TAG = "OwnerVillagerName";
    private static final String SOURCE_KIND_TAG = "SourceKind";

    private VillagerTakenItemTracker() {
    }

    static void markTakenFromVillager(ItemStack stack, CompoundTag trackingTag, String sourceKind) {
        if (stack.isEmpty()
                || trackingTag == null
                || !trackingTag.hasUUID(OWNER_VILLAGER_TAG)
                || !trackingTag.contains(OWNER_VILLAGER_NAME_TAG)) {
            return;
        }

        CompoundTag tag = new CompoundTag();
        tag.putUUID(OWNER_VILLAGER_TAG, trackingTag.getUUID(OWNER_VILLAGER_TAG));
        tag.putString(OWNER_VILLAGER_NAME_TAG, trackingTag.getString(OWNER_VILLAGER_NAME_TAG));
        tag.putString(SOURCE_KIND_TAG, sourceKind == null ? "" : sourceKind);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> data.put(TAG, tag));
    }

    public static Optional<TakenItemOwner> owner(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }

        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty() || !customData.contains(TAG)) {
            return Optional.empty();
        }

        CompoundTag tag = customData.copyTag().getCompound(TAG);
        if (!tag.hasUUID(OWNER_VILLAGER_TAG) || !tag.contains(OWNER_VILLAGER_NAME_TAG)) {
            return Optional.empty();
        }
        return Optional.of(new TakenItemOwner(
                tag.getUUID(OWNER_VILLAGER_TAG),
                tag.getString(OWNER_VILLAGER_NAME_TAG),
                tag.getString(SOURCE_KIND_TAG)));
    }

    public static void clear(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty() || !customData.contains(TAG)) {
            return;
        }

        CompoundTag tag = customData.copyTag();
        tag.remove(TAG);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
            return;
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public record TakenItemOwner(UUID villagerId, String villagerName, String sourceKind) {
    }
}
