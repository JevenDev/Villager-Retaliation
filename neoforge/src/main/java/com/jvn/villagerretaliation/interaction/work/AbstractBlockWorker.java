package com.jvn.villagerretaliation.interaction.work;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

abstract class AbstractBlockWorker implements HiredRoleWorker {
    protected static Iterable<BlockPos> positionsNear(BlockPos center, int radius) {
        return BlockPos.betweenClosed(
                center.offset(-radius, -Math.min(radius, 8), -radius),
                center.offset(radius, Math.min(radius, 8), radius));
    }

    protected boolean storeDrops(ServerLevel level, HiredWorkContext context, Villager villager, BlockPos pos, ItemStack tool) {
        WorkTarget target = new WorkTarget(pos, bestApproachPos(level, villager, pos), true);
        if (target.approachPos() == null || !canMineFromCurrentPosition(level, villager, target)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), villager, tool);
        for (ItemStack drop : drops) {
            if (!context.storeOutputAfterDepositIfFull(villager, drop).isEmpty()) {
                return false;
            }
        }
        faceBlock(villager, pos);
        villager.swing(InteractionHand.MAIN_HAND, true);
        EnchantmentHelper.onHitBlock(level, tool, villager, villager, EquipmentSlot.MAINHAND, Vec3.atCenterOf(pos), state, ignored -> {
        });
        level.destroyBlock(pos, false, villager);
        level.destroyBlockProgress(villager.getId(), pos, -1);
        damageTool(context, villager, tool);
        return true;
    }

    protected void faceBlock(Villager villager, BlockPos pos) {
        villager.getLookControl().setLookAt(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 30.0F, 30.0F);
    }

    protected int breakProgressGoal(ServerLevel level, BlockPos pos, ItemStack tool) {
        BlockState state = level.getBlockState(pos);
        float speed = Math.max(0.1F, effectiveDestroySpeed(tool, state));
        float hardness = Math.max(0.2F, state.getDestroySpeed(level, pos));
        return Math.clamp(Math.round(hardness * 6.0F / speed), 1, 24);
    }

    protected float effectiveDestroySpeed(ItemStack tool, BlockState state) {
        float speed = tool.getDestroySpeed(state);
        int efficiency = efficiencyLevel(tool);
        if (efficiency > 0 && speed > 1.0F) {
            speed += efficiency * efficiency + 1;
        }
        return speed;
    }

    protected void showBreakProgress(ServerLevel level, Villager villager, BlockPos pos, int progress, int needed) {
        int stage = Math.clamp((int) Math.floor(progress * 10.0D / Math.max(1, needed)), 0, 9);
        level.destroyBlockProgress(villager.getId(), pos, stage);
    }

    protected void clearBreakProgress(ServerLevel level, Villager villager, BlockPos pos) {
        if (pos != null) {
            level.destroyBlockProgress(villager.getId(), pos, -1);
        }
    }

    protected WorkTarget chooseReachableTarget(ServerLevel level, Villager villager, Iterable<BlockPos> targets) {
        WorkTarget best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos target : targets) {
            BlockPos approach = bestApproachPos(level, villager, target);
            if (approach == null) {
                continue;
            }
            double distance = villager.distanceToSqr(approach.getCenter());
            Path path = villager.getNavigation().createPath(approach, 0);
            boolean reachable = path != null && path.canReach();
            if (!reachable && distance > 9.0D) {
                continue;
            }
            double score = distance + (reachable ? 0.0D : 256.0D);
            if (score < bestScore) {
                best = new WorkTarget(target.immutable(), approach.immutable(), reachable);
                bestScore = score;
            }
        }
        return best;
    }

    protected boolean moveToTarget(Villager villager, WorkTarget target, double speed) {
        faceBlock(villager, target.blockPos());
        if (canMineFromCurrentPosition((ServerLevel) villager.level(), villager, target)) {
            holdMiningPosition(villager, target);
            return true;
        }
        if (isCloseEnough(villager, target)) {
            if (!villager.getNavigation().isDone()) {
                villager.getNavigation().stop();
            }
            return false;
        }
        Path path = villager.getNavigation().createPath(target.approachPos(), 0);
        if (path != null) {
            return villager.getNavigation().moveTo(path, speed);
        }
        return villager.getNavigation().moveTo(
                target.approachPos().getX() + 0.5D,
                target.approachPos().getY(),
                target.approachPos().getZ() + 0.5D,
                speed);
    }

    protected boolean isCloseEnough(Villager villager, WorkTarget target) {
        return villager.distanceToSqr(target.approachPos().getCenter()) <= 4.5D;
    }

    protected void holdMiningPosition(Villager villager, WorkTarget target) {
        if (!villager.getNavigation().isDone()) {
            villager.getNavigation().stop();
        }
        villager.getLookControl().setLookAt(
                target.blockPos().getX() + 0.5D,
                target.blockPos().getY() + 0.5D,
                target.blockPos().getZ() + 0.5D,
                30.0F,
                30.0F);
        villager.setDeltaMovement(villager.getDeltaMovement().multiply(0.0D, 1.0D, 0.0D));
    }

    protected boolean canMineFromCurrentPosition(ServerLevel level, Villager villager, WorkTarget target) {
        if (!isCloseEnough(villager, target) || !isExposedFromApproach(level, target)) {
            return false;
        }
        Vec3 start = villager.getEyePosition();
        Vec3 end = Vec3.atCenterOf(target.blockPos());
        BlockHitResult hit = level.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                villager));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target.blockPos());
    }

    protected void damageTool(HiredWorkContext context, Villager villager, ItemStack tool) {
        if (!tool.isEmpty() && tool.isDamageableItem()) {
            tool.hurtAndBreak(1, villager, EquipmentSlot.MAINHAND);
            context.inventory().setChanged();
        }
    }

    private int efficiencyLevel(ItemStack stack) {
        return enchantmentLevel(stack.getEnchantments(), "efficiency");
    }

    private int enchantmentLevel(ItemEnchantments enchantments, String path) {
        int level = 0;
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            if (holder.unwrapKey().map(key -> key.location().getPath().equals(path)).orElse(false)) {
                level = Math.max(level, entry.getIntValue());
            }
        }
        return level;
    }

    private BlockPos bestApproachPos(ServerLevel level, Villager villager, BlockPos target) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Direction direction : Direction.values()) {
            BlockPos candidate = target.relative(direction);
            if (!level.getBlockState(candidate).isAir() && !level.getBlockState(candidate).liquid()) {
                continue;
            }
            BlockPos standingPos = candidate.getY() < target.getY() ? candidate : candidate.below();
            if (!level.getBlockState(standingPos).isSolid()) {
                continue;
            }
            double distance = villager.distanceToSqr(candidate.getCenter());
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean isExposedFromApproach(ServerLevel level, WorkTarget target) {
        if (!target.approachPos().closerToCenterThan(target.blockPos().getCenter(), 1.75D)) {
            return false;
        }
        BlockState approachState = level.getBlockState(target.approachPos());
        return approachState.isAir() || approachState.liquid();
    }

    protected record WorkTarget(BlockPos blockPos, BlockPos approachPos, boolean reachable) {
    }
}
