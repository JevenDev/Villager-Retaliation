package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.interaction.HiredJobSite;
import com.jvn.villagerretaliation.interaction.HiredRoute;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

public record HiredWorkContext(
        HiredJobInventory inventory,
        CompoundTag state,
        BlockPos workCenter,
        BlockPos workMin,
        BlockPos workMax,
        int radius,
        int verticalRadius,
        boolean hasWorkArea,
        int aptitude,
        int skillWorkSpeedPercent,
        int transferCapacityPercent,
        int efficiency,
        boolean autoDepositOutputs,
        boolean useAssignedStorageForSupplies,
        HiredWorkAssignment assignment) {
    public static final String OUTPUT_DEPOSITED_THIS_STORAGE_TRIP_TAG = "OutputDepositedThisStorageTrip";

    public HiredWorkContext(
            HiredJobInventory inventory,
            CompoundTag state,
            BlockPos workCenter,
            BlockPos workMin,
            BlockPos workMax,
            int radius,
            int verticalRadius,
            boolean hasWorkArea,
            int efficiency,
            boolean autoDepositOutputs,
            boolean useAssignedStorageForSupplies) {
        this(
                inventory,
                state,
                workCenter,
                workMin,
                workMax,
                radius,
                verticalRadius,
                hasWorkArea,
                50,
                100,
                100,
                efficiency,
                autoDepositOutputs,
                useAssignedStorageForSupplies,
                HiredWorkAssignment.of(HiredJobSite.fromWorkArea(new HiredWorkArea(
                        workCenter,
                        workMin,
                        workMax,
                        radius,
                        verticalRadius,
                        hasWorkArea,
                        hasWorkArea)), HiredRoute.empty()));
    }

    public HiredWorkContext(
            HiredJobInventory inventory,
            CompoundTag state,
            BlockPos workCenter,
            BlockPos workMin,
            BlockPos workMax,
            int radius,
            int verticalRadius,
            boolean hasWorkArea,
            int aptitude,
            int skillWorkSpeedPercent,
            int transferCapacityPercent,
            int efficiency,
            boolean autoDepositOutputs,
            boolean useAssignedStorageForSupplies) {
        this(
                inventory,
                state,
                workCenter,
                workMin,
                workMax,
                radius,
                verticalRadius,
                hasWorkArea,
                aptitude,
                skillWorkSpeedPercent,
                transferCapacityPercent,
                efficiency,
                autoDepositOutputs,
                useAssignedStorageForSupplies,
                HiredWorkAssignment.of(HiredJobSite.fromWorkArea(new HiredWorkArea(
                        workCenter,
                        workMin,
                        workMax,
                        radius,
                        verticalRadius,
                        hasWorkArea,
                        hasWorkArea)), HiredRoute.empty()));
    }

    public HiredWorkContext(
            HiredJobInventory inventory,
            CompoundTag state,
            BlockPos workCenter,
            BlockPos workMin,
            BlockPos workMax,
            int radius,
            int verticalRadius,
            boolean hasWorkArea,
            int efficiency,
            boolean autoDepositOutputs,
            boolean useAssignedStorageForSupplies,
            HiredJobSite jobSite,
            HiredRoute route) {
        this(
                inventory,
                state,
                workCenter,
                workMin,
                workMax,
                radius,
                verticalRadius,
                hasWorkArea,
                50,
                100,
                100,
                efficiency,
                autoDepositOutputs,
                useAssignedStorageForSupplies,
                HiredWorkAssignment.of(jobSite, route));
    }

    public HiredWorkContext(
            HiredJobInventory inventory,
            CompoundTag state,
            BlockPos workCenter,
            BlockPos workMin,
            BlockPos workMax,
            int radius,
            int verticalRadius,
            boolean hasWorkArea,
            int aptitude,
            int skillWorkSpeedPercent,
            int transferCapacityPercent,
            int efficiency,
            boolean autoDepositOutputs,
            boolean useAssignedStorageForSupplies,
            HiredJobSite jobSite,
            HiredRoute route) {
        this(
                inventory,
                state,
                workCenter,
                workMin,
                workMax,
                radius,
                verticalRadius,
                hasWorkArea,
                aptitude,
                skillWorkSpeedPercent,
                transferCapacityPercent,
                efficiency,
                autoDepositOutputs,
                useAssignedStorageForSupplies,
                HiredWorkAssignment.of(jobSite, route));
    }

    public HiredWorkContext {
        if (assignment == null) {
            HiredWorkArea area = new HiredWorkArea(
                    workCenter,
                    workMin,
                    workMax,
                    radius,
                    verticalRadius,
                    hasWorkArea,
                    hasWorkArea);
            assignment = HiredWorkAssignment.of(HiredJobSite.fromWorkArea(area), HiredRoute.empty());
        }
        aptitude = Math.clamp(aptitude, 0, 100);
        skillWorkSpeedPercent = Math.clamp(skillWorkSpeedPercent, 50, 125);
        transferCapacityPercent = Math.clamp(transferCapacityPercent, 50, 150);
        efficiency = Math.max(1, efficiency);
    }

    public int transferLimit(int baseItems) {
        if (baseItems <= 0) {
            return 0;
        }
        return Math.max(1, Math.round(baseItems * this.transferCapacityPercent / 100.0F));
    }

    public int progressTicks() {
        return this.state.getInt("ProgressTicks");
    }

    public void setProgressTicks(int ticks) {
        this.state.putInt("ProgressTicks", Math.max(0, ticks));
    }

    public int verticalRadius() {
        return Math.max(1, this.verticalRadius);
    }

    public int horizontalSearchRadius() {
        return Math.max(1, this.radius);
    }

    public Iterable<BlockPos> workAreaPositions() {
        return BlockPos.betweenClosed(this.workMin, this.workMax);
    }

    public HiredWorkArea workArea() {
        return this.assignment.workArea();
    }

    public HiredJobSite jobSite() {
        return this.assignment.jobSite();
    }

    public HiredRoute route() {
        return this.assignment.route();
    }

    public boolean isLoaded(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos);
    }

    public boolean isInsideWorkArea(BlockPos pos) {
        return this.assignment.isInsideWorkArea(pos);
    }

    public boolean hasRoute() {
        return this.assignment.hasRoute();
    }

    public boolean isInsideRouteArea(BlockPos pos) {
        return this.assignment.isInsideRouteArea(pos);
    }

    public boolean isInsideWorkAreaOrRoute(BlockPos pos) {
        return this.assignment.isInsideWorkAreaOrRoute(pos);
    }

    public AABB collectionBounds() {
        return this.assignment.collectionBounds();
    }

    public boolean hasNavigationTether() {
        return this.assignment.hasNavigationTether();
    }

    public boolean isInsideNavigationTether(BlockPos pos, int horizontalPadding, int verticalPadding) {
        return this.assignment.isInsideNavigationTether(pos, horizontalPadding, verticalPadding);
    }

    public boolean depositOutputs(Villager villager) {
        return this.autoDepositOutputs && this.inventory.depositOutputToNearbyAssignedStorage();
    }

    public boolean depositOutputsAtStorage(Villager villager, BlockPos storagePos) {
        return this.autoDepositOutputs
                && this.inventory.depositOutputToAssignedStorageAt(storagePos);
    }

    public boolean hasOutputToDeposit() {
        return this.inventory.hasOutputItems();
    }

    public boolean hasOutputSpace() {
        return this.inventory.hasOutputSpace();
    }

    public boolean canDepositOutputsNow(Villager villager) {
        return this.autoDepositOutputs
                && this.inventory.hasOutputItems()
                && AssignedStorageService.canInteractWithAssignedOutputStorage(villager);
    }

    public BlockPos nearestDepositStorage(ServerLevel level, Villager villager) {
        return AssignedStorageService.nearestAssignedOutputStoragePos(level, villager);
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
            consumed += AssignedStorageService.consumeItems(villager, predicate, remaining);
        }
        return consumed;
    }
}
