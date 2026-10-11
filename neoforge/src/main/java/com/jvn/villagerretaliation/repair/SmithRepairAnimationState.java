package com.jvn.villagerretaliation.repair;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;

public interface SmithRepairAnimationState {
    long APPROACHING = -1;
    long RETURNING = -2;
    BlockPos villagerretaliation$repairStation();

    ItemStack villagerretaliation$repairItem();

    long villagerretaliation$repairStarted();

    void villagerretaliation$repairAnimation(BlockPos station, ItemStack item, long started);

    static boolean isActive(AbstractVillager villager) {
        return villager instanceof SmithRepairAnimationState state && state.villagerretaliation$repairStation() != null;
    }

    static boolean isWorking(AbstractVillager villager) {
        if (!isActive(villager)) return false;
        long started = ((SmithRepairAnimationState) villager).villagerretaliation$repairStarted();
        return started != APPROACHING && started != RETURNING;
    }
}
