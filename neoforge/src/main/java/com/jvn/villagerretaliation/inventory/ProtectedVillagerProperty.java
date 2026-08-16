package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class ProtectedVillagerProperty {
    private static final String TAG = "VillagerRetaliationProtectedProperty";
    private static final String OWNER_UUID_TAG = "OwnerVillager";
    private static final String OWNER_NAME_TAG = "OwnerVillagerName";
    private static final String REASON_TAG = "Reason";

    private ProtectedVillagerProperty() {
    }

    public static ItemStack mark(ItemStack stack, Villager owner, String reason) {
        if (stack.isEmpty() || owner == null) {
            return stack;
        }
        return mark(stack, owner.getUUID(), VillagerPresetNameRegistry.resolveDisplayName(owner).getString(), reason);
    }

    public static ItemStack mark(ItemStack stack, UUID ownerId, String ownerName, String reason) {
        if (stack.isEmpty() || ownerId == null) {
            return stack;
        }

        CompoundTag propertyTag = new CompoundTag();
        propertyTag.putUUID(OWNER_UUID_TAG, ownerId);
        propertyTag.putString(OWNER_NAME_TAG, ownerName == null || ownerName.isBlank() ? "Villager" : ownerName);
        propertyTag.putString(REASON_TAG, reason == null || reason.isBlank() ? "unknown" : reason);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(TAG, propertyTag));
        return stack;
    }

    public static boolean isProtected(ItemStack stack) {
        return read(stack).isPresent();
    }

    public static Optional<Property> read(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty() || !customData.contains(TAG)) {
            return Optional.empty();
        }
        CompoundTag propertyTag = customData.copyTag().getCompound(TAG);
        if (!propertyTag.hasUUID(OWNER_UUID_TAG)) {
            return Optional.empty();
        }
        return Optional.of(new Property(
                propertyTag.getUUID(OWNER_UUID_TAG),
                propertyTag.getString(OWNER_NAME_TAG),
                propertyTag.getString(REASON_TAG)
        ));
    }

    public static void remove(ItemStack stack) {
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
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    public static MutableComponent tooltip(Property property) {
        String ownerName = property.ownerName() == null || property.ownerName().isBlank()
                ? "Villager"
                : property.ownerName();
        return Component.literal(ownerName + "'s property");
    }

    public record Property(UUID ownerId, String ownerName, String reason) {
    }
}
