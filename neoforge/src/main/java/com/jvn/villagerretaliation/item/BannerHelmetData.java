package com.jvn.villagerretaliation.item;

import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Stores a complete, single banner stack inside a helmet without replacing the helmet itself. */
public final class BannerHelmetData {
    private static final String ATTACHED_BANNER_TAG = "VillagerRetaliationAttachedBanner";

    private BannerHelmetData() {
    }

    public static boolean isHelmet(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() instanceof ArmorItem armorItem
                && armorItem.getEquipmentSlot() == EquipmentSlot.HEAD;
    }

    public static boolean canAttach(ItemStack helmet, ItemStack banner) {
        return isHelmet(helmet)
                && banner.getItem() instanceof BannerItem
                && !hasAttachedBanner(helmet);
    }

    public static boolean hasAttachedBanner(ItemStack helmet) {
        if (helmet.isEmpty()) {
            return false;
        }
        CustomData customData = helmet.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return !customData.isEmpty() && customData.contains(ATTACHED_BANNER_TAG);
    }

    public static void attach(ItemStack helmet, ItemStack banner, HolderLookup.Provider registries) {
        if (!canAttach(helmet, banner)) {
            throw new IllegalArgumentException("A banner can only be attached to a helmet without one");
        }
        ItemStack storedBanner = banner.copyWithCount(1);
        CustomData.update(DataComponents.CUSTOM_DATA, helmet,
                tag -> tag.put(ATTACHED_BANNER_TAG, storedBanner.save(registries)));
    }

    public static Optional<ItemStack> getAttachedBanner(ItemStack helmet, HolderLookup.Provider registries) {
        if (helmet.isEmpty()) {
            return Optional.empty();
        }
        CustomData customData = helmet.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty() || !customData.contains(ATTACHED_BANNER_TAG)) {
            return Optional.empty();
        }
        return ItemStack.parse(registries, customData.copyTag().getCompound(ATTACHED_BANNER_TAG))
                .filter(stack -> stack.getItem() instanceof BannerItem)
                .map(stack -> stack.copyWithCount(1));
    }

    public static void removeAttachedBanner(ItemStack helmet) {
        if (helmet.isEmpty()) {
            return;
        }
        CustomData customData = helmet.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty() || !customData.contains(ATTACHED_BANNER_TAG)) {
            return;
        }
        CompoundTag tag = customData.copyTag();
        tag.remove(ATTACHED_BANNER_TAG);
        if (tag.isEmpty()) {
            helmet.remove(DataComponents.CUSTOM_DATA);
        } else {
            helmet.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }
}
