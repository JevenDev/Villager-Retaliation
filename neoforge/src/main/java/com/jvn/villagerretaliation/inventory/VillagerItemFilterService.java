package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

/** Central access point for a hired villager's assigned item filter. */
public final class VillagerItemFilterService {
    private VillagerItemFilterService() {
    }

    public static ItemStack assignedFilter(Villager villager) {
        if (villager == null || villager.isBaby()) {
            return ItemStack.EMPTY;
        }
        ItemStack filter = HiredJobInventory.getJobInventory(villager).getItem(HiredJobInventory.FILTER_SLOT);
        return VillagerRetaliationItems.isItemFilter(filter) ? filter : ItemStack.EMPTY;
    }

    public static boolean mayWithdraw(Villager villager, ItemStack candidate) {
        ItemStack filter = assignedFilter(villager);
        return filter.isEmpty() || VillagerItemFilterData.matches(filter, candidate);
    }

    /** Replaces the assigned filter with a single configured copy and returns the prior filter. */
    public static ItemStack replaceFilter(Villager villager, ItemStack replacement) {
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        ItemStack oldFilter = inventory.getItem(HiredJobInventory.FILTER_SLOT).copy();
        ItemStack stored = VillagerRetaliationItems.isItemFilter(replacement)
                ? replacement.copyWithCount(1)
                : ItemStack.EMPTY;
        inventory.setItem(HiredJobInventory.FILTER_SLOT, stored);
        return oldFilter;
    }
}
