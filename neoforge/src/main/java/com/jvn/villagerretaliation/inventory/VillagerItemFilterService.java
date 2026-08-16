package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.item.VillagerFilterMatcher;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
        return VillagerRetaliationItems.isFilter(filter) ? filter : ItemStack.EMPTY;
    }

    public static boolean mayWithdraw(Villager villager, ItemStack candidate) {
        ItemStack filter = assignedFilter(villager);
        return filter.isEmpty()
                || VillagerRetaliationItems.isRecipeFilter(filter)
                || VillagerFilterMatcher.matches(villager.level(), filter, candidate);
    }

    /** Replaces the assigned filter with a single configured copy and returns the prior filter. */
    public static ItemStack replaceFilter(Villager villager, ItemStack replacement) {
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        ItemStack oldFilter = inventory.getItem(HiredJobInventory.FILTER_SLOT).copy();
        ItemStack stored = VillagerRetaliationItems.isFilter(replacement)
                ? replacement.copyWithCount(1)
                : ItemStack.EMPTY;
        inventory.setItem(HiredJobInventory.FILTER_SLOT, stored);
        return oldFilter;
    }

    /**
     * Performs the authoritative held-stack mutation for dialogue assignment.
     * Conversation and distance validation remain the responsibility of the dialogue handler.
     */
    public static AssignmentResult assignHeldFilter(
            ServerPlayer player,
            Villager villager,
            VillagerItemFilterData.Mode mode) {
        if (player == null
                || villager == null
                || villager.isBaby()
                || mode == null
                || !(villager.level() instanceof ServerLevel level)
                || player.serverLevel() != level
                || !HiredVillagerContractService.isHired(level, villager)
                || !HiredVillagerContractService.isHiredBy(level, villager, player)) {
            return AssignmentResult.REJECTED;
        }
        ItemStack heldFilter = player.getMainHandItem();
        if (!VillagerRetaliationItems.isFilter(heldFilter)) {
            return AssignmentResult.REJECTED;
        }

        ItemStack assignedFilter = heldFilter.copyWithCount(1);
        VillagerFilterPolicy.ListMode listMode = mode == VillagerItemFilterData.Mode.DENYLIST
                ? VillagerFilterPolicy.ListMode.DENY_MATCHING
                : VillagerFilterPolicy.ListMode.ALLOW_MATCHING;
        VillagerFilterPolicy.applyChange(
                assignedFilter, VillagerFilterPolicy.PolicyField.LIST_MODE, listMode.networkId());
        ItemStack oldFilter = replaceFilter(villager, assignedFilter);
        if (!player.getAbilities().instabuild) {
            heldFilter.shrink(1);
        }

        boolean hadOldFilter = !oldFilter.isEmpty();
        boolean droppedOldFilter = false;
        if (hadOldFilter && !player.getInventory().add(oldFilter)) {
            player.drop(oldFilter, false);
            droppedOldFilter = true;
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        return new AssignmentResult(true, hadOldFilter, droppedOldFilter);
    }

    public record AssignmentResult(boolean assigned, boolean replaced, boolean droppedOldFilter) {
        private static final AssignmentResult REJECTED = new AssignmentResult(false, false, false);
    }
}
