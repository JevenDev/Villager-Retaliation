package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

final class HiredItemPickup {
    private HiredItemPickup() {
    }

    static WorkResult collectNearestOutputItem(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            AbstractBlockWorker worker,
            Predicate<ItemStack> itemFilter,
            double reachSqr,
            double speed,
            Messages messages) {
        ItemEntity item = findNearestOutputItem(level, villager, context, itemFilter);
        if (item == null) {
            return null;
        }

        context.setProgressTicks(0);
        BlockPos itemPos = item.blockPosition();
        if (!context.hasOutputSpace()) {
            AbstractBlockWorker.OutputFullHandling outputFull = worker.handleOutputFullInventory(
                    level,
                    context,
                    villager,
                    speed,
                    itemPos,
                    messages.outputFullDepositing(),
                    messages.outputFullBlocked());
            if (outputFull.deposited()) {
                return WorkResult.progressed(messages.outputFullDepositing());
            }
            return outputFull.result();
        }

        if (!canCollectFromCurrentPosition(villager, context, item, reachSqr)) {
            worker.setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, itemPos);
            if (!moveToItem(level, villager, context, worker, item, reachSqr, speed)) {
                if (worker.recordWorkPathFailure(level, villager, itemPos)) {
                    HiredWorkerBrain.setFailure(context, messages.unreachableFailure(), level.getGameTime() + 20L * 30L);
                    worker.setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, itemPos);
                    return WorkResult.idle(messages.unreachable());
                }
                return WorkResult.progressed(messages.repositioning());
            }
            return WorkResult.progressed(messages.moving());
        }

        worker.clearWorkPathFailure(villager, itemPos);
        worker.stopWorkNavigation(villager);
        worker.faceBlock(villager, item.position());
        HiredWorkerBrain.clearFailure(context);
        worker.setTaskState(context, HiredWorkerTaskState.COLLECTING_OUTPUT, itemPos);

        ItemStack stack = item.getItem();
        ItemStack remainder = context.storeOutputAfterDepositIfFull(villager, stack.copy());
        int moved = stack.getCount() - remainder.getCount();
        if (moved <= 0) {
            HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
            worker.setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, itemPos);
            return WorkResult.idle(messages.outputFullBlocked());
        }

        if (remainder.isEmpty()) {
            item.discard();
        } else {
            item.setItem(remainder);
        }
        worker.swingWorkTool(villager);
        if (messages.idleAfterCollected()) {
            worker.setTaskState(context, HiredWorkerTaskState.IDLE);
        }
        return messages.completed()
                ? WorkResult.completed(messages.collected(), Map.of("count", Integer.toString(moved)))
                : WorkResult.progressed(messages.collected(), Map.of("count", Integer.toString(moved)));
    }

    private static ItemEntity findNearestOutputItem(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            Predicate<ItemStack> itemFilter) {
        Predicate<ItemStack> safeFilter = itemFilter == null ? ignored -> true : itemFilter;
        ArrayList<ItemEntity> items = new ArrayList<>(level.getEntitiesOfClass(
                ItemEntity.class,
                workAreaBounds(context),
                item -> isCollectableOutputItem(level, context, villager, item, safeFilter)));
        items.sort(Comparator.comparingDouble(villager::distanceToSqr));
        return items.isEmpty() ? null : items.getFirst();
    }

    private static boolean isCollectableOutputItem(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            ItemEntity item,
            Predicate<ItemStack> itemFilter) {
        BlockPos pos = item.blockPosition();
        return item.isAlive()
                && itemFilter.test(item.getItem())
                && context.isInsideWorkArea(pos)
                && context.isLoaded(level, pos)
                && !HiredPathMemory.isAvoided(level, villager, pos);
    }

    private static boolean canCollectFromCurrentPosition(
            Villager villager,
            HiredWorkContext context,
            ItemEntity item,
            double reachSqr) {
        return context.isInsideWorkArea(villager.blockPosition())
                && context.isInsideWorkArea(item.blockPosition())
                && villager.distanceToSqr(item) <= reachSqr;
    }

    private static boolean moveToItem(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            AbstractBlockWorker worker,
            ItemEntity item,
            double reachSqr,
            double speed) {
        if (!context.isInsideWorkArea(villager.blockPosition())
                || !context.isInsideWorkArea(item.blockPosition())) {
            worker.stopWorkNavigation(villager);
            return false;
        }
        if (villager.distanceToSqr(item) <= reachSqr) {
            worker.stopWorkNavigation(villager);
            worker.faceBlock(villager, item.position());
            return true;
        }

        BlockPos targetPos = item.blockPosition();
        Path currentPath = villager.getNavigation().getPath();
        if (currentPath != null && !HiredMoveToBlockFaceJob.pathStaysInsideFilter(currentPath, context::isInsideWorkArea)) {
            worker.stopWorkNavigation(villager);
            return false;
        }
        Path path = villager.getNavigation().createPath(targetPos, 0);
        if (path != null && path.canReach() && HiredMoveToBlockFaceJob.pathStaysInsideFilter(path, context::isInsideWorkArea)) {
            villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(targetPos));
            boolean moved = VillagerTaskNavigationUtil.moveToHiredPath(villager, path, targetPos, speed, 0);
            if (moved) {
                HiredPathMemory.rememberNavigationProgress(level, villager, targetPos, villager.distanceToSqr(targetPos.getCenter()));
            }
            return moved;
        }
        HiredPathMemory.clearNavigationProgress(villager);
        return false;
    }

    private static AABB workAreaBounds(HiredWorkContext context) {
        return new AABB(
                context.workMin().getX(),
                context.workMin().getY(),
                context.workMin().getZ(),
                context.workMax().getX() + 1.0D,
                context.workMax().getY() + 1.0D,
                context.workMax().getZ() + 1.0D);
    }

    record Messages(
            String outputFullDepositing,
            String outputFullBlocked,
            String unreachableFailure,
            String unreachable,
            String repositioning,
            String moving,
            String collected,
            boolean completed,
            boolean idleAfterCollected) {
    }
}
