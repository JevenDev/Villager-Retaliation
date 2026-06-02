package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public final class MiningWorker extends AbstractBlockWorker {
    @Override
    public HiredVillagerRole role() {
        return HiredVillagerRole.MINING;
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        WorkTarget target = findExposedOre(level, villager, context.radius());
        if (target == null) {
            context.setProgressTicks(0);
            context.depositOutputs(villager);
            return WorkResult.idle("No exposed ores found in radius.");
        }

        BlockState targetState = level.getBlockState(target.blockPos());
        ItemStack pickaxe = context.inventory().equipBestTool(
                stack -> stack.is(ItemTags.PICKAXES) && stack.isCorrectToolForDrops(targetState),
                stack -> effectiveDestroySpeed(stack, targetState));
        if (pickaxe.isEmpty()) {
            context.setProgressTicks(0);
            clearBreakProgress(level, villager, target.blockPos());
            return WorkResult.idle("Paused: no pickaxe can harvest the exposed ore.");
        }

        if (!moveToTarget(villager, target, 0.55D)) {
            if (context.progressTicks() > 0) {
                context.setProgressTicks(0);
                clearBreakProgress(level, villager, target.blockPos());
            }
            return WorkResult.progressed("Moving to reachable ore face.");
        }
        holdMiningPosition(villager, target);

        int needed = breakProgressGoal(level, target.blockPos(), pickaxe);
        int progress = context.progressTicks() + 1;
        if (progress < needed) {
            context.setProgressTicks(progress);
            villager.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            showBreakProgress(level, villager, target.blockPos(), progress, needed);
            return WorkResult.progressed("Mining exposed ore: " + progress + "/" + needed + ".");
        }

        context.setProgressTicks(0);
        if (!storeDrops(level, context, villager, target.blockPos(), pickaxe)) {
            return WorkResult.idle("Paused: output storage is full.");
        }
        return WorkResult.completed("Mined 1 exposed ore.");
    }

    private WorkTarget findExposedOre(ServerLevel level, Villager villager, int radius) {
        java.util.List<BlockPos> candidates = new java.util.ArrayList<>();
        BlockPos center = villager.blockPosition();
        for (BlockPos rawPos : positionsNear(center, radius)) {
            BlockPos pos = rawPos.immutable();
            BlockState state = level.getBlockState(pos);
            if (!isOre(state) || !isExposed(level, pos)) {
                continue;
            }
            candidates.add(pos);
        }
        return chooseReachableTarget(level, villager, candidates);
    }

    private static boolean isOre(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.endsWith("_ore") || path.equals("ancient_debris");
    }

    private static boolean isExposed(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockState neighbor = level.getBlockState(pos.relative(direction));
            if (neighbor.isAir() || neighbor.liquid()) {
                return true;
            }
        }
        return false;
    }
}
