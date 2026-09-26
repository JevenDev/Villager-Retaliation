package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public record SettlementBrewingOrder(ResourceLocation item, ResourceLocation potion, int amount, boolean continuous) {
    public static final int MAX_AMOUNT = 4096;

    public SettlementBrewingOrder {
        Objects.requireNonNull(item);
        Objects.requireNonNull(potion);
        if (amount < 1 || amount > MAX_AMOUNT) {
            throw new IllegalArgumentException("Brewing quantity must be between 1 and " + MAX_AMOUNT);
        }
    }

    static SettlementBrewingOrder read(CompoundTag state) {
        if (!BrewingWorker.hasOrder(state)) {
            return null;
        }
        var item = ResourceLocation.tryParse(state.getString("BrewingTargetItem"));
        var potion = ResourceLocation.tryParse(state.getString("BrewingTargetPotion"));
        int amount = state.getInt("BrewingRemaining");
        boolean continuous = state.getBoolean("BrewingContinuous");
        return item == null || potion == null || !continuous && (amount < 1 || amount > MAX_AMOUNT)
                ? null : new SettlementBrewingOrder(item, potion, continuous ? 1 : amount, continuous);
    }

    void apply(CompoundTag state) {
        BrewingWorker.setOrder(state, this.item, this.potion, this.amount, this.continuous);
    }
}
