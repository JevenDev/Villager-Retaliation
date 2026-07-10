package com.jvn.villagerretaliation.interaction.work.mining;

import com.jvn.villagerretaliation.interaction.work.HiredStorageNavigationGoal;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.interaction.work.WorkResult;
import com.jvn.villagerretaliation.interaction.work.mining.MiningSupportManager.BackingNeed;
import com.jvn.villagerretaliation.interaction.work.mining.MiningSupportManager.SupportType;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/** Owns excavation infrastructure inventory requirements and assigned-storage trips. */
final class MiningSupportSupplies {
    private MiningSupportSupplies() {
    }

    static WorkResult requireLadder(ServerLevel level, Villager villager, HiredWorkContext context) {
        Integer currentLayerY = MiningBlockRules.currentExcavationLayer(level, context);
        if (currentLayerY == null) {
            return null;
        }
        if (!MiningExcavationShaft.requiresLadder(level, context, currentLayerY)
                || MiningSupportManager.hasInventorySupply(context, SupportType.LADDER)
                || MiningExcavationShaft.hasCompleteRouteToLayer(level, context, currentLayerY)) {
            return null;
        }
        MiningWorkerState.set(context, MiningWorkerState.Phase.BLOCKED_MISSING_SUPPLIES);
        HiredWorkerBrain.setFailure(context, "missing_ladders", 0L);
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS);
        return WorkResult.idle("interaction.work.mining.support.missing_ladders");
    }

    static WorkResult gather(ServerLevel level, Villager villager, HiredWorkContext context) {
        if (!context.useAssignedStorageForSupplies()) {
            return null;
        }
        Integer currentLayerY = MiningBlockRules.currentExcavationLayer(level, context);
        if (currentLayerY == null) {
            return null;
        }
        boolean needsLadders = MiningExcavationShaft.requiresLadder(level, context, currentLayerY)
                && context.inventory().findSupply(SupportType.LADDER::matchesSupply).isEmpty()
                && !MiningExcavationShaft.hasCompleteRouteToLayer(level, context, currentLayerY)
                && AssignedStorageService.countItems(villager, SupportType.LADDER::matchesSupply) > 0;
        if (needsLadders && !MiningSupportManager.canAcceptSupply(context, SupportType.LADDER)) {
            return blockOnFullInventory(level, context);
        }

        int supportFloorY = MiningSupportManager.supportFloorY(level, context, currentLayerY);
        BackingNeed backingNeed = MiningSupportManager.pendingBackingNeed(
                level,
                villager,
                context,
                supportFloorY,
                currentLayerY);
        boolean needsBacking = !needsLadders
                && backingNeed != null
                && !MiningSupportManager.hasInventoryBacking(level, context, backingNeed)
                && MiningSupportManager.hasStoredBacking(level, villager, backingNeed);
        if (needsBacking && !context.hasOutputSpace()) {
            return blockOnFullInventory(level, context);
        }

        boolean wantsTorches = context.inventory().findSupply(SupportType.TORCH::matchesSupply).isEmpty()
                && MiningSupportManager.canAcceptSupply(context, SupportType.TORCH)
                && MiningSupportManager.hasPendingTorchPlacement(level, villager, context, supportFloorY)
                && AssignedStorageService.countItems(villager, SupportType.TORCH::matchesSupply) > 0;
        if (!needsLadders && !needsBacking && !wantsTorches) {
            return null;
        }

        BlockPos storage = needsBacking
                ? AssignedStorageService.nearestAssignedNonPaymentStoragePosContaining(
                level,
                villager,
                stack -> MiningSupportManager.canUseBacking(
                        level,
                        backingNeed.backingPos(),
                        backingNeed.supportFace(),
                        stack))
                : AssignedStorageService.nearestAssignedStoragePosContaining(
                        level,
                        villager,
                        needsLadders ? SupportType.LADDER::matchesSupply : SupportType.TORCH::matchesSupply);
        if (storage == null) {
            return null;
        }
        HiredWorkerBrain.setStorageTarget(context, storage);
        HiredStorageNavigationGoal.Result moveResult = HiredStorageNavigationGoal.moveToStorageTarget(
                level,
                context,
                villager,
                storage,
                0.55D);
        if (moveResult == HiredStorageNavigationGoal.Result.MOVING) {
            MiningWorkerState.set(context, MiningWorkerState.Phase.GATHER_SUPPLIES);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
            return WorkResult.progressed("interaction.work.mining.support.gathering_supplies");
        }
        if (moveResult == HiredStorageNavigationGoal.Result.FAILED) {
            HiredStorageNavigationGoal.clearStorageTarget(context);
            if (needsLadders) {
                MiningWorkerState.set(context, MiningWorkerState.Phase.BLOCKED_MISSING_SUPPLIES);
                HiredWorkerBrain.setFailure(
                        context,
                        "mining_support_storage_path_failed",
                        level.getGameTime() + 100L);
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.PAUSED_NO_STORAGE, storage);
                return WorkResult.idle("interaction.work.mining.support.missing_ladders");
            }
            return null;
        }

        int moved = 0;
        if (needsLadders) {
            moved += AssignedStorageService.transferItemsAtAssignedStorage(
                    villager,
                    storage,
                    SupportType.LADDER::matchesSupply,
                    64,
                    context.inventory()::insertSupplyFromStorage);
        } else if (needsBacking) {
            moved += AssignedStorageService.transferItemsAtAssignedNonPaymentStorage(
                    villager,
                    storage,
                    stack -> MiningSupportManager.canUseBacking(
                            level,
                            backingNeed.backingPos(),
                            backingNeed.supportFace(),
                            stack),
                    1,
                    context.inventory()::insertOutput);
        }
        if (!needsBacking && wantsTorches) {
            moved += AssignedStorageService.transferItemsAtAssignedStorage(
                    villager,
                    storage,
                    SupportType.TORCH::matchesSupply,
                    16,
                    context.inventory()::insertSupplyFromStorage);
        }
        HiredStorageNavigationGoal.clearStorageTarget(context);
        if (moved > 0) {
            MiningWorkerState.set(context, MiningWorkerState.Phase.GATHER_SUPPLIES);
            HiredWorkerBrain.setState(
                    context,
                    HiredWorkerTaskState.RETURNING_TO_WORK_AREA,
                    context.workCenter());
            return WorkResult.progressed("interaction.work.mining.support.gathered_supplies");
        }
        return needsLadders ? blockOnFullInventory(level, context) : null;
    }

    private static WorkResult blockOnFullInventory(ServerLevel level, HiredWorkContext context) {
        HiredStorageNavigationGoal.clearStorageTarget(context);
        MiningWorkerState.set(context, MiningWorkerState.Phase.BLOCKED_MISSING_SUPPLIES);
        HiredWorkerBrain.setFailure(context, "support_inventory_full", level.getGameTime() + 100L);
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY);
        return WorkResult.idle("interaction.work.mining.support.inventory_full");
    }
}
