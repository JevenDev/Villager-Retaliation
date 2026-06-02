package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

public record HiredWorkContext(
        HiredJobInventory inventory,
        CompoundTag state,
        int radius,
        int efficiency,
        boolean autoDepositOutputs,
        boolean useAssignedStorageForSupplies) {
    public String status() {
        return this.state.getString("Status");
    }

    public int progressTicks() {
        return this.state.getInt("ProgressTicks");
    }

    public void setProgressTicks(int ticks) {
        this.state.putInt("ProgressTicks", Math.max(0, ticks));
    }

    public ItemStack storeOutput(Villager villager, ItemStack stack) {
        return this.inventory.insertOutput(stack);
    }

    public boolean depositOutputs(Villager villager) {
        return this.autoDepositOutputs && this.inventory.depositOutputToAssignedStorage();
    }

    public ItemStack storeOutputAfterDepositIfFull(Villager villager, ItemStack stack) {
        ItemStack remainder = this.inventory.insertOutput(stack);
        if (!remainder.isEmpty() && depositOutputs(villager)) {
            remainder = this.inventory.insertOutput(remainder);
        }
        return remainder;
    }
}
