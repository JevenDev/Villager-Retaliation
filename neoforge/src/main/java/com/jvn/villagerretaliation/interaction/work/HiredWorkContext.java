package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

public record HiredWorkContext(
        HiredJobInventory inventory,
        CompoundTag state,
        BlockPos workCenter,
        BlockPos workMin,
        BlockPos workMax,
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

    public int verticalRadius() {
        return Math.max(1, Math.max(
                Math.abs(this.workCenter.getY() - this.workMin.getY()),
                Math.abs(this.workMax.getY() - this.workCenter.getY())));
    }

    public int horizontalSearchRadius() {
        return Math.max(1, Math.max(
                Math.max(Math.abs(this.workCenter.getX() - this.workMin.getX()), Math.abs(this.workMax.getX() - this.workCenter.getX())),
                Math.max(Math.abs(this.workCenter.getZ() - this.workMin.getZ()), Math.abs(this.workMax.getZ() - this.workCenter.getZ()))));
    }

    public Iterable<BlockPos> workAreaPositions() {
        return BlockPos.betweenClosed(this.workMin, this.workMax);
    }

    public boolean isLoaded(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos);
    }

    public boolean isInsideWorkArea(BlockPos pos) {
        return pos.getX() >= this.workMin.getX()
                && pos.getX() <= this.workMax.getX()
                && pos.getY() >= this.workMin.getY()
                && pos.getY() <= this.workMax.getY()
                && pos.getZ() >= this.workMin.getZ()
                && pos.getZ() <= this.workMax.getZ();
    }

    public ItemStack storeOutput(Villager villager, ItemStack stack) {
        return this.inventory.insertOutput(stack);
    }

    public boolean depositOutputs(Villager villager) {
        return this.autoDepositOutputs && this.inventory.depositOutputToNearbyAssignedStorage(this::isInsideWorkArea);
    }

    public boolean depositOutputsAtStorage(Villager villager, BlockPos storagePos) {
        return this.autoDepositOutputs
                && this.isInsideWorkArea(storagePos)
                && this.inventory.depositOutputToAssignedStorageAt(storagePos);
    }

    public boolean hasOutputToDeposit() {
        return this.inventory.hasOutputItems();
    }

    public boolean canDepositOutputsNow(Villager villager) {
        return this.autoDepositOutputs
                && this.inventory.hasOutputItems()
                && AssignedStorageService.canInteractWithAssignedStorage(villager, this::isInsideWorkArea);
    }

    public boolean canDepositOutputsAtStorageNow(Villager villager, BlockPos storagePos) {
        return this.autoDepositOutputs
                && this.inventory.hasOutputItems()
                && this.isInsideWorkArea(storagePos)
                && AssignedStorageService.canInteractWithAssignedStorage(villager, storagePos);
    }

    public BlockPos nearestDepositStorage(ServerLevel level, Villager villager) {
        return AssignedStorageService.nearestAssignedStoragePos(level, villager, this::isInsideWorkArea);
    }

    public boolean canStoreOutputs(List<ItemStack> stacks) {
        return this.inventory.canStoreOutputs(stacks);
    }

    public ItemStack storeOutputAfterDepositIfFull(Villager villager, ItemStack stack) {
        ItemStack remainder = this.inventory.insertOutput(stack);
        if (!remainder.isEmpty() && depositOutputs(villager)) {
            remainder = this.inventory.insertOutput(remainder);
        }
        return remainder;
    }

    public int consumeSupply(Villager villager, Predicate<ItemStack> predicate, int count) {
        int consumed = this.inventory.consumeSupply(predicate, count);
        int remaining = Math.max(0, count - consumed);
        if (remaining > 0 && this.useAssignedStorageForSupplies) {
            consumed += AssignedStorageService.consumeItems(villager, predicate, remaining, this::isInsideWorkArea);
        }
        return consumed;
    }
}
