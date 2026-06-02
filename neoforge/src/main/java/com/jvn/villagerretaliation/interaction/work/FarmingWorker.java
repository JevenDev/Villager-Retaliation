package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class FarmingWorker extends AbstractBlockWorker {
    @Override
    public HiredVillagerRole role() {
        return HiredVillagerRole.FARMING;
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        BlockPos target = findMatureCrop(level, villager.blockPosition(), context.radius());
        if (target == null) {
            context.setProgressTicks(0);
            return WorkResult.idle("No mature crops found in radius.");
        }

        int needed = Math.max(1, 5 - Math.max(0, context.efficiency() - 75) / 30);
        int progress = context.progressTicks() + 1;
        if (progress < needed) {
            context.setProgressTicks(progress);
            villager.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, 0.45D);
            return WorkResult.progressed("Harvesting crop: " + progress + "/" + needed + ".");
        }

        context.setProgressTicks(0);
        BlockState state = level.getBlockState(target);
        if (!(state.getBlock() instanceof CropBlock crop)) {
            return WorkResult.idle("Crop target changed.");
        }
        ItemStack tool = context.inventory().findTool(stack -> true);
        if (!storeDrops(level, context, villager, target, tool)) {
            return WorkResult.idle("Paused: output storage is full.");
        }
        replant(level, target, crop, context);
        return WorkResult.completed("Harvested mature crop.");
    }

    private static BlockPos findMatureCrop(ServerLevel level, BlockPos center, int radius) {
        for (BlockPos rawPos : positionsNear(center, radius)) {
            BlockPos pos = rawPos.immutable();
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                return pos;
            }
        }
        return null;
    }

    private static void replant(ServerLevel level, BlockPos pos, CropBlock crop, HiredWorkContext context) {
        ItemStack seed = seedForCrop(crop);
        if (seed.isEmpty()) {
            return;
        }
        int consumed = context.inventory().consumeSupply(stack -> stack.is(seed.getItem()), 1);
        if (consumed > 0) {
            level.setBlock(pos, crop.defaultBlockState(), 3);
        }
    }

    private static ItemStack seedForCrop(CropBlock crop) {
        if (crop == net.minecraft.world.level.block.Blocks.WHEAT) {
            return new ItemStack(Items.WHEAT_SEEDS);
        }
        if (crop == net.minecraft.world.level.block.Blocks.CARROTS) {
            return new ItemStack(Items.CARROT);
        }
        if (crop == net.minecraft.world.level.block.Blocks.POTATOES) {
            return new ItemStack(Items.POTATO);
        }
        if (crop == net.minecraft.world.level.block.Blocks.BEETROOTS) {
            return new ItemStack(Items.BEETROOT_SEEDS);
        }
        return ItemStack.EMPTY;
    }
}
