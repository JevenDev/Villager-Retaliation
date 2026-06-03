package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

abstract class AbstractBlockWorker implements HiredRoleWorker {
    private static final String ACTIVE_BLOCK_POS_TAG = "ActiveWorkBlockPos";
    private static final String ACTIVE_APPROACH_POS_TAG = "ActiveWorkApproachPos";
    private static final String ACTIVE_HIT_X_TAG = "ActiveWorkHitX";
    private static final String ACTIVE_HIT_Y_TAG = "ActiveWorkHitY";
    private static final String ACTIVE_HIT_Z_TAG = "ActiveWorkHitZ";
    private static final long LOOK_TARGET_MEMORY_TICKS = 24L;
    private static final int MAX_TARGETS_TO_PATHFIND = 64;
    private static final int STORAGE_APPROACH_SEARCH_RADIUS = 4;

    static void clearSharedRuntimeState() {
        HiredPathMemory.clear();
    }

    @Override
    public void maintain(ServerLevel level, Villager villager, HiredWorkContext context) {
        if (context.progressTicks() <= 0) {
            return;
        }
        HiredPathTarget target = activeWorkTarget(level, context, villager);
        if (target == null || !context.isLoaded(level, target.blockPos()) || level.getBlockState(target.blockPos()).isAir()) {
            return;
        }
        if (canWorkFromCurrentPosition(level, villager, context, target)) {
            holdMiningPosition(villager, target);
            return;
        }
        keepWalkTarget(villager, target.approachPos(), 0.55D, 1);
        faceBlock(villager, target);
    }

    protected static Iterable<BlockPos> positionsNear(BlockPos center, int radius) {
        return BlockPos.betweenClosed(
                center.offset(-radius, -Math.min(radius, 8), -radius),
                center.offset(radius, Math.min(radius, 8), radius));
    }

    protected boolean storeDrops(ServerLevel level, HiredWorkContext context, Villager villager, BlockPos pos, ItemStack tool) {
        HiredPathTarget target = bestWorkTarget(level, villager, context, pos);
        return target != null && storeDrops(level, context, villager, target, tool);
    }

    protected boolean storeDrops(ServerLevel level, HiredWorkContext context, Villager villager, HiredPathTarget target, ItemStack tool) {
        if (!context.isInsideWorkArea(target.blockPos())
                || !context.isInsideWorkArea(target.approachPos())
                || !context.isLoaded(level, target.blockPos())
                || !context.isLoaded(level, target.approachPos())
                || !canWorkFromCurrentPosition(level, villager, context, target)) {
            return false;
        }
        BlockState state = level.getBlockState(target.blockPos());
        List<ItemStack> drops = Block.getDrops(state, level, target.blockPos(), level.getBlockEntity(target.blockPos()), villager, tool);
        if (!context.canStoreOutputs(drops)) {
            context.depositOutputs(villager);
        }
        if (!context.canStoreOutputs(drops)) {
            return false;
        }
        for (ItemStack drop : drops) {
            if (!context.storeOutputAfterDepositIfFull(villager, drop).isEmpty()) {
                return false;
            }
        }
        faceBlock(villager, target);
        swingWorkTool(villager);
        EnchantmentHelper.onHitBlock(level, tool, villager, villager, EquipmentSlot.MAINHAND, target.hitPos(), state, ignored -> {
        });
        level.destroyBlock(target.blockPos(), false, villager);
        level.destroyBlockProgress(villager.getId(), target.blockPos(), -1);
        damageTool(context, villager, tool);
        HiredPathMemory.rememberRecent(level, target.blockPos());
        return true;
    }

    protected void faceBlock(Villager villager, BlockPos pos) {
        faceBlock(villager, Vec3.atCenterOf(pos));
    }

    protected void faceBlock(Villager villager, HiredPathTarget target) {
        villager.getBrain().setMemoryWithExpiry(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target.blockPos()), LOOK_TARGET_MEMORY_TICKS);
        faceBlock(villager, target.hitPos());
    }

    protected void faceBlock(Villager villager, Vec3 target) {
        villager.getLookControl().setLookAt(target.x, target.y, target.z, 90.0F, 90.0F);
        double dx = target.x - villager.getX();
        double dy = target.y - villager.getEyeY();
        double dz = target.z - villager.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) -(Mth.atan2(dy, horizontal) * 180.0D / Math.PI);
        villager.setYRot(yaw);
        villager.setXRot(Mth.clamp(pitch, -60.0F, 60.0F));
        villager.yBodyRot = yaw;
        villager.yHeadRot = yaw;
    }

    protected void swingWorkTool(Villager villager) {
        if (!villager.swinging) {
            villager.swing(InteractionHand.MAIN_HAND, true);
        }
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

    protected void prepareBreakingTarget(ServerLevel level, HiredWorkContext context, Villager villager, BlockPos pos) {
        long packedPos = pos.asLong();
        if (context.state().contains(ACTIVE_BLOCK_POS_TAG) && context.state().getLong(ACTIVE_BLOCK_POS_TAG) != packedPos) {
            clearBreakProgress(level, villager, BlockPos.of(context.state().getLong(ACTIVE_BLOCK_POS_TAG)));
            context.setProgressTicks(0);
        }
        context.state().putLong(ACTIVE_BLOCK_POS_TAG, packedPos);
    }

    protected void prepareBreakingTarget(ServerLevel level, HiredWorkContext context, Villager villager, HiredPathTarget target) {
        prepareBreakingTarget(level, context, villager, target.blockPos());
        context.state().putLong(ACTIVE_APPROACH_POS_TAG, target.approachPos().asLong());
        context.state().putDouble(ACTIVE_HIT_X_TAG, target.hitPos().x);
        context.state().putDouble(ACTIVE_HIT_Y_TAG, target.hitPos().y);
        context.state().putDouble(ACTIVE_HIT_Z_TAG, target.hitPos().z);
    }

    protected void clearActiveBreakingTarget(ServerLevel level, HiredWorkContext context, Villager villager) {
        if (context.state().contains(ACTIVE_BLOCK_POS_TAG)) {
            clearBreakProgress(level, villager, BlockPos.of(context.state().getLong(ACTIVE_BLOCK_POS_TAG)));
            if (!villager.getNavigation().isDone()) {
                villager.getNavigation().stop();
            }
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            villager.getBrain().eraseMemory(MemoryModuleType.PATH);
            context.state().remove(ACTIVE_BLOCK_POS_TAG);
            context.state().remove(ACTIVE_APPROACH_POS_TAG);
            context.state().remove(ACTIVE_HIT_X_TAG);
            context.state().remove(ACTIVE_HIT_Y_TAG);
            context.state().remove(ACTIVE_HIT_Z_TAG);
        }
        HiredPathMemory.clearNavigationProgress(villager);
        context.setProgressTicks(0);
    }

    protected HiredPathTarget activeWorkTarget(ServerLevel level, HiredWorkContext context, Villager villager) {
        HiredPathTarget target = storedWorkTarget(context.state());
        if (target == null || HiredPathMemory.isAvoided(level, villager, target.blockPos())) {
            return null;
        }
        if (!context.isInsideWorkArea(target.blockPos()) || !context.isInsideWorkArea(target.approachPos())) {
            return null;
        }
        if (!context.isLoaded(level, target.blockPos()) || !context.isLoaded(level, target.approachPos())) {
            return null;
        }
        if (canMineFromCurrentPosition(level, villager, target)) {
            return target;
        }
        if (!HiredMoveToBlockFaceJob.isValidApproachPosition(level, target.approachPos())) {
            return null;
        }
        Vec3 approachEye = new Vec3(
                target.approachPos().getX() + 0.5D,
                target.approachPos().getY() + villager.getEyeHeight(),
                target.approachPos().getZ() + 0.5D);
        if (!HiredMoveToBlockFaceJob.hasLineOfSightToBlock(level, villager, approachEye, target.blockPos(), target.hitPos())) {
            return null;
        }
        return target;
    }

    protected boolean canWorkFromCurrentPosition(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredPathTarget target) {
        return context.isInsideWorkArea(villager.blockPosition())
                && context.isInsideWorkArea(target.blockPos())
                && context.isLoaded(level, target.blockPos())
                && context.isLoaded(level, target.approachPos())
                && canMineFromCurrentPosition(level, villager, target);
    }

    protected HiredPathTarget chooseReachableTarget(ServerLevel level, Villager villager, Iterable<BlockPos> targets) {
        return new HiredMoveToBlockFaceJob(level, villager, targets, MAX_TARGETS_TO_PATHFIND).search().target();
    }

    protected HiredPathTarget chooseReachableTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            Iterable<BlockPos> targets) {
        return new HiredMoveToBlockFaceJob(
                level,
                villager,
                targets,
                MAX_TARGETS_TO_PATHFIND,
                context::isInsideWorkArea).search().target();
    }

    protected boolean recordWorkPathFailure(ServerLevel level, Villager villager, BlockPos pos) {
        return HiredPathMemory.recordFailure(level, villager, pos);
    }

    protected void clearWorkPathFailure(Villager villager, BlockPos pos) {
        HiredPathMemory.clearFailure(villager, pos);
    }

    protected boolean isTemporarilyAvoidedTarget(ServerLevel level, Villager villager, BlockPos pos) {
        return HiredPathMemory.isAvoided(level, villager, pos);
    }

    protected void expireWorkPathMemory(ServerLevel level) {
        HiredPathMemory.expire(level);
    }

    protected boolean moveToTarget(ServerLevel level, Villager villager, HiredWorkContext context, HiredPathTarget target, double speed) {
        faceBlock(villager, target);
        if (!context.isInsideWorkArea(target.approachPos())
                || !context.isLoaded(level, target.blockPos())
                || !context.isLoaded(level, target.approachPos())) {
            return false;
        }
        if (canWorkFromCurrentPosition(level, villager, context, target)) {
            holdMiningPosition(villager, target);
            return true;
        }
        keepWalkTarget(villager, target.approachPos(), speed, 1);
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone() && target.approachPos().equals(navigationTarget)) {
            if (HiredPathMemory.isNavigationBlocked(level, villager, target.approachPos(), villager.distanceToSqr(target.approachPos().getCenter()))) {
                villager.getNavigation().stop();
                HiredPathMemory.clearNavigationProgress(villager);
                return false;
            }
            return true;
        }
        Path path = villager.getNavigation().createPath(target.approachPos(), 0);
        if (path != null && path.canReach()) {
            boolean moved = villager.getNavigation().moveTo(path, speed);
            if (moved) {
                HiredPathMemory.rememberNavigationProgress(level, villager, target.approachPos(), villager.distanceToSqr(target.approachPos().getCenter()));
            } else {
                HiredPathMemory.clearNavigationProgress(villager);
            }
            return moved;
        }
        HiredPathMemory.clearNavigationProgress(villager);
        return false;
    }

    protected DepositResult depositOutputsOrMoveToStorage(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            double speed) {
        if (!context.autoDepositOutputs() || !context.hasOutputToDeposit()) {
            return DepositResult.NOT_NEEDED;
        }
        if (context.depositOutputs(villager)) {
            return DepositResult.DEPOSITED;
        }
        BlockPos storage = context.nearestDepositStorage(level, villager);
        if (storage == null) {
            return DepositResult.UNAVAILABLE;
        }
        if (!context.isInsideWorkArea(storage)) {
            return DepositResult.UNAVAILABLE;
        }
        if (!context.isLoaded(level, storage)) {
            return DepositResult.UNAVAILABLE;
        }
        StorageMoveResult moveResult = moveToStorageTarget(level, context, villager, storage, speed);
        if (moveResult == StorageMoveResult.MOVING) {
            return DepositResult.MOVING;
        }
        if (moveResult == StorageMoveResult.FAILED) {
            return DepositResult.UNAVAILABLE;
        }
        return context.depositOutputs(villager) ? DepositResult.DEPOSITED : DepositResult.UNAVAILABLE;
    }

    protected DepositResult depositOutputsForFullInventory(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            double speed) {
        if (!context.canDepositOutputsNow(villager)) {
            clearActiveBreakingTarget(level, context, villager);
        }
        return depositOutputsOrMoveToStorage(level, context, villager, speed);
    }

    private StorageMoveResult moveToStorageTarget(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            BlockPos storage,
            double speed) {
        if (!context.isLoaded(level, storage)) {
            return StorageMoveResult.FAILED;
        }
        faceBlock(villager, Vec3.atCenterOf(storage));
        if (context.canDepositOutputsNow(villager)) {
            if (!villager.getNavigation().isDone()) {
                villager.getNavigation().stop();
            }
            HiredPathMemory.clearNavigationProgress(villager);
            return StorageMoveResult.ARRIVED;
        }

        BlockPos approach = bestStorageApproach(level, context, villager, storage);
        if (approach == null) {
            HiredPathMemory.clearNavigationProgress(villager);
            return StorageMoveResult.FAILED;
        }

        keepWalkTarget(villager, approach, speed, 1);
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        double distanceSqr = villager.distanceToSqr(approach.getCenter());
        if (!villager.getNavigation().isDone() && approach.equals(navigationTarget)) {
            if (HiredPathMemory.isNavigationBlocked(level, villager, approach, distanceSqr)) {
                villager.getNavigation().stop();
                HiredPathMemory.clearNavigationProgress(villager);
                return StorageMoveResult.FAILED;
            }
            return StorageMoveResult.MOVING;
        }

        Path path = villager.getNavigation().createPath(approach, 0);
        if (path != null && path.canReach()) {
            boolean moved = villager.getNavigation().moveTo(path, speed);
            if (moved) {
                HiredPathMemory.rememberNavigationProgress(level, villager, approach, distanceSqr);
            } else {
                HiredPathMemory.clearNavigationProgress(villager);
            }
            return moved ? StorageMoveResult.MOVING : StorageMoveResult.FAILED;
        }
        HiredPathMemory.clearNavigationProgress(villager);
        return StorageMoveResult.FAILED;
    }

    private BlockPos bestStorageApproach(ServerLevel level, HiredWorkContext context, Villager villager, BlockPos storage) {
        if (!context.isLoaded(level, storage)) {
            return null;
        }
        if (AssignedStorageService.isInInteractionRange(villager, storage) && context.isInsideWorkArea(villager.blockPosition())) {
            return villager.blockPosition().immutable();
        }

        List<StorageApproach> candidates = new ArrayList<>();
        for (BlockPos rawCandidate : BlockPos.betweenClosed(
                storage.offset(-STORAGE_APPROACH_SEARCH_RADIUS, -2, -STORAGE_APPROACH_SEARCH_RADIUS),
                storage.offset(STORAGE_APPROACH_SEARCH_RADIUS, 2, STORAGE_APPROACH_SEARCH_RADIUS))) {
            BlockPos candidate = rawCandidate.immutable();
            if (!context.isLoaded(level, candidate)
                    || !HiredMoveToBlockFaceJob.isValidApproachPosition(level, candidate)
                    || !context.isInsideWorkArea(candidate)
                    || candidate.getCenter().distanceToSqr(storage.getCenter()) > 25.0D) {
                continue;
            }
            candidates.add(new StorageApproach(candidate, storageApproachScore(level, villager, candidate, storage)));
        }
        candidates.sort(Comparator.comparingDouble(StorageApproach::score));
        for (StorageApproach candidate : candidates) {
            Path path = villager.getNavigation().createPath(candidate.pos(), 0);
            if (path != null && path.canReach()) {
                return candidate.pos();
            }
        }
        return null;
    }

    private double storageApproachScore(ServerLevel level, Villager villager, BlockPos approach, BlockPos storage) {
        double distance = villager.distanceToSqr(approach.getCenter());
        int vertical = Math.abs(approach.getY() - villager.blockPosition().getY());
        double reachSlack = approach.getCenter().distanceToSqr(storage.getCenter());
        return distance
                + vertical * vertical * 3.0D
                + reachSlack * 0.25D
                + HiredMoveToBlockFaceJob.terrainCost(level, approach);
    }

    protected boolean isCloseEnough(Villager villager, HiredPathTarget target) {
        return HiredMoveToBlockFaceJob.isCloseEnough(villager, target);
    }

    protected void holdMiningPosition(Villager villager, HiredPathTarget target) {
        if (!villager.getNavigation().isDone()) {
            villager.getNavigation().stop();
        }
        HiredPathMemory.clearNavigationProgress(villager);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.PATH);
        faceBlock(villager, target);
        villager.setDeltaMovement(villager.getDeltaMovement().multiply(0.0D, 1.0D, 0.0D));
    }

    private void keepWalkTarget(Villager villager, BlockPos pos, double speed, int closeEnoughDistance) {
        villager.getBrain().setMemoryWithExpiry(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(new BlockPosTracker(pos), (float) speed, closeEnoughDistance),
                30L);
    }

    protected boolean canMineFromCurrentPosition(ServerLevel level, Villager villager, HiredPathTarget target) {
        return HiredMoveToBlockFaceJob.canReachFromCurrentPosition(level, villager, target);
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

    protected HiredPathTarget storedWorkTarget(CompoundTag state) {
        if (!state.contains(ACTIVE_BLOCK_POS_TAG)
                || !state.contains(ACTIVE_APPROACH_POS_TAG)
                || !state.contains(ACTIVE_HIT_X_TAG)
                || !state.contains(ACTIVE_HIT_Y_TAG)
                || !state.contains(ACTIVE_HIT_Z_TAG)) {
            return null;
        }
        BlockPos blockPos = BlockPos.of(state.getLong(ACTIVE_BLOCK_POS_TAG));
        BlockPos approachPos = BlockPos.of(state.getLong(ACTIVE_APPROACH_POS_TAG));
        Vec3 hitPos = new Vec3(
                state.getDouble(ACTIVE_HIT_X_TAG),
                state.getDouble(ACTIVE_HIT_Y_TAG),
                state.getDouble(ACTIVE_HIT_Z_TAG));
        return new HiredPathTarget(blockPos, approachPos, hitPos);
    }

    protected HiredPathTarget bestWorkTarget(ServerLevel level, Villager villager, BlockPos target) {
        return chooseReachableTarget(level, villager, List.of(target));
    }

    protected HiredPathTarget bestWorkTarget(ServerLevel level, Villager villager, HiredWorkContext context, BlockPos target) {
        if (!context.isInsideWorkArea(target)) {
            return null;
        }
        return chooseReachableTarget(level, villager, context, List.of(target));
    }

    private boolean isInsideWorkArea(HiredWorkContext context, BlockPos pos) {
        return context.isInsideWorkArea(pos);
    }

    protected boolean hasLineOfSightToBlock(ServerLevel level, Villager villager, Vec3 start, BlockPos target, Vec3 hitPos) {
        return HiredMoveToBlockFaceJob.hasLineOfSightToBlock(level, villager, start, target, hitPos);
    }

    protected enum DepositResult {
        NOT_NEEDED,
        DEPOSITED,
        MOVING,
        UNAVAILABLE
    }

    private enum StorageMoveResult {
        ARRIVED,
        MOVING,
        FAILED
    }

    private record StorageApproach(BlockPos pos, double score) {
    }
}
