package com.jvn.villagerretaliation.settlement;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public record SettlementHaulTask(UUID source, UUID destination, ItemStack sample, int target, boolean surplus) {
    public SettlementHaulTask {
        if (source == null || destination == null || sample == null || sample.isEmpty() || target < 1) {
            throw new IllegalArgumentException("Hauling task requires depots, an item and a stock target");
        }
        sample = sample.copyWithCount(1);
    }

    @Override
    public ItemStack sample() {
        return this.sample.copy();
    }

    public boolean matches(ItemStack stack) {
        return ItemStack.isSameItemSameComponents(this.sample, stack);
    }

    CompoundTag save(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Source", this.source);
        tag.putUUID("Destination", this.destination);
        tag.put("Sample", this.sample.save(provider));
        tag.putInt("Target", this.target);
        tag.putBoolean("Surplus", this.surplus);
        return tag;
    }

    static SettlementHaulTask load(CompoundTag tag, HolderLookup.Provider provider) {
        if (!tag.hasUUID("Source") || !tag.hasUUID("Destination")) {
            return null;
        }
        ItemStack sample = ItemStack.parseOptional(provider, tag.getCompound("Sample"));
        return sample.isEmpty() || tag.getInt("Target") < 1 ? null : new SettlementHaulTask(
                tag.getUUID("Source"), tag.getUUID("Destination"), sample, tag.getInt("Target"), tag.getBoolean("Surplus"));
    }
}
