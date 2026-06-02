package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public final class LoggingWorker extends AbstractBlockWorker {
    @Override
    public HiredVillagerRole role() {
        return HiredVillagerRole.LOGGING;
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        WorkTarget target = findTreeLog(level, villager, context.radius(), context.state().getString("LoggingFilter"));
        if (target == null) {
            context.setProgressTicks(0);
            context.depositOutputs(villager);
            return WorkResult.idle("No safe tree logs found in radius.");
        }

        BlockState targetState = level.getBlockState(target.blockPos());
        ItemStack axe = context.inventory().equipBestTool(
                stack -> stack.is(ItemTags.AXES),
                stack -> effectiveDestroySpeed(stack, targetState));
        if (axe.isEmpty()) {
            context.setProgressTicks(0);
            clearBreakProgress(level, villager, target.blockPos());
            return WorkResult.idle("Paused: logging needs an axe in job gear or supplies.");
        }

        if (!moveToTarget(villager, target, 0.55D)) {
            if (context.progressTicks() > 0) {
                context.setProgressTicks(0);
                clearBreakProgress(level, villager, target.blockPos());
            }
            return WorkResult.progressed("Moving to reachable tree face.");
        }
        holdMiningPosition(villager, target);

        int needed = breakProgressGoal(level, target.blockPos(), axe);
        int progress = context.progressTicks() + 1;
        if (progress < needed) {
            context.setProgressTicks(progress);
            villager.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            showBreakProgress(level, villager, target.blockPos(), progress, needed);
            return WorkResult.progressed("Logging tree: " + progress + "/" + needed + ".");
        }

        context.setProgressTicks(0);
        if (!storeDrops(level, context, villager, target.blockPos(), axe)) {
            return WorkResult.idle("Paused: output storage is full.");
        }
        return WorkResult.completed("Cut 1 log.");
    }

    private WorkTarget findTreeLog(ServerLevel level, Villager villager, int radius, String filter) {
        java.util.List<BlockPos> candidates = new java.util.ArrayList<>();
        BlockPos center = villager.blockPosition();
        for (BlockPos rawPos : positionsNear(center, radius)) {
            BlockPos pos = rawPos.immutable();
            BlockState state = level.getBlockState(pos);
            if (!state.is(BlockTags.LOGS) || !matchesFilter(state, filter) || !hasNearbyLeaves(level, pos)) {
                continue;
            }
            candidates.add(pos);
        }
        return chooseReachableTarget(level, villager, candidates);
    }

    private static boolean matchesFilter(BlockState state, String filter) {
        if (filter == null || filter.isBlank() || "any".equals(filter)) {
            return true;
        }
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(filter);
    }

    private static boolean hasNearbyLeaves(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(pos.relative(direction)).is(BlockTags.LEAVES)) {
                return true;
            }
        }
        for (BlockPos rawPos : BlockPos.betweenClosed(pos.offset(-2, 0, -2), pos.offset(2, 3, 2))) {
            if (level.getBlockState(rawPos).is(BlockTags.LEAVES)) {
                return true;
            }
        }
        return false;
    }
}
