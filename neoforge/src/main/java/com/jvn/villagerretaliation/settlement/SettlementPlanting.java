package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions;
import com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions.Crop;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

public final class SettlementPlanting {
    private SettlementPlanting() { }

    public static Crop crop(Villager villager, BlockPos soil) {
        var duty = SettlementWorkforceService.duty(villager).orElse(null);
        return duty == null || duty.area().type() != SettlementAreaType.FARM ? Crop.ANY : duty.area().crops().crop(duty.area(), soil);
    }

    public static boolean planned(Villager villager) {
        return SettlementWorkforceService.duty(villager).map(duty -> duty.area().crops().enabled()).orElse(false);
    }

    public static boolean permits(CompoundTag state, Crop crop, ItemStack seed) {
        return HiredFarmingOptions.permitsSeed(state, seed) && (crop == Crop.ANY || seed.is(crop.seed()));
    }
}
