package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
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
    private static final String STORAGE_FULL_STATUS_SHOWN_TAG = "StorageFullStatusShown";
    private static final long LOOK_TARGET_MEMORY_TICKS = 24L;
    private static final int MAX_TARGETS_TO_PATHFIND = 64;
    private static final int ROAM_CANDIDATE_ATTEMPTS = 16;
    private static final double APPROACH_CENTER_SETTLE_SQR = 0.04D;

    static void clearSharedRuntimeState() {
        HiredPathMemory.clear();
    }

    @Override
    public void maintain(ServerLevel level, Villager villager, HiredWorkContext context) {
        if (!context.hasWorkArea()) {
            return;
        }
        HiredWorkerBrain.Snapshot worker = HiredWorkerBrain.snapshot(context.state(), level.getGameTime());
        if (worker.taskState() == HiredWorkerTaskState.MOVING_TO_STORAGE
                || worker.taskState() == HiredWorkerTaskState.DEPOSITING
                || worker.taskState() == HiredWorkerTaskState.PAUSED_STORAGE_FULL
                || (!context.hasOutputSpace() && context.hasOutputToDeposit())) {
            return;
        }
        if (context.progressTicks() <= 0) {
            return;
        }
        HiredPathTarget target = activeWorkTarget(level, context, villager);
        if (target == null || !context.isLoaded(level, target.blockPos()) || level.getBlockState(target.blockPos()).isAir()) {
            return;
        }
        if (canWorkFromCurrentPosition(level, villager, context, target)) {
            holdWorkPosition(villager, target);
            return;
        }
        if (!ensureNavigationRemainsInsideWorkArea(context, villager)) {
            return;
        }
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

    @Override
    public void stop(ServerLevel level, Villager villager, HiredWorkContext context) {
        HiredWorkPlan.clear(context);
        clearActiveBreakingTarget(level, context, villager);
        setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
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
        villager.getLookControl().setLookAt(target.x, target.y, target.z, 60.0F, 60.0F);
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
            BlockPos previousTarget = BlockPos.of(context.state().getLong(ACTIVE_BLOCK_POS_TAG));
            clearBreakProgress(level, villager, previousTarget);
            HiredPathMemory.releaseTarget(level, villager, previousTarget);
            context.setProgressTicks(0);
        }
        context.state().putLong(ACTIVE_BLOCK_POS_TAG, packedPos);
        HiredPathMemory.reserveTarget(level, villager, pos);
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
        HiredPathMemory.releaseAll(villager);
        HiredPathMemory.clearNavigationProgress(villager);
        HiredWorkerBrain.clearTarget(context);
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

    protected WorkResult waitForWorkAreaAssignment(ServerLevel level, Villager villager, HiredWorkContext context) {
        HiredWorkPlan.clear(context);
        clearActiveBreakingTarget(level, context, villager);
        HiredWorkerBrain.setFailure(context, "no_work_area", 0L);
        HiredWorkerBrain.setLastTargetScanResult(context, "no_work_area");
        setTaskState(context, HiredWorkerTaskState.NO_WORK_AREA);
        return WorkResult.idle("No work area assigned.");
    }

    protected HiredPathTarget plannedTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            Predicate<BlockPos> validator,
            int maxPlanTargets) {
        Predicate<BlockPos> safeValidator = validator == null ? ignored -> true : validator;
        HiredWorkPlan.retainMatching(context, safeValidator, maxPlanTargets);
        for (BlockPos planned : HiredWorkPlan.targets(context)) {
            HiredPathTarget target = bestWorkTarget(level, villager, context, planned);
            if (target != null && safeValidator.test(target.blockPos())) {
                return target;
            }
        }
        HiredWorkPlan.clear(context);
        return null;
    }

    protected HiredPathTarget rebuildPlannedTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            Iterable<BlockPos> candidates,
            Predicate<BlockPos> validator,
            BlockPos planOrigin,
            int maxPlanTargets) {
        List<BlockPos> ordered = HiredWorkPlan.routeOrder(
                planOrigin == null ? villager.blockPosition() : planOrigin,
                candidates,
                maxPlanTargets);
        HiredWorkPlan.replace(context, ordered, maxPlanTargets);
        return plannedTarget(level, villager, context, validator, maxPlanTargets);
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

    protected void setTaskState(HiredWorkContext context, HiredWorkerTaskState state) {
        HiredWorkerBrain.setState(context, state);
    }

    protected void setTaskState(HiredWorkContext context, HiredWorkerTaskState state, BlockPos target) {
        HiredWorkerBrain.setState(context, state, target);
    }

    protected boolean roamInsideWorkArea(ServerLevel level, Villager villager, HiredWorkContext context, double speed) {
        if (!ensureNavigationRemainsInsideWorkArea(context, villager)) {
            return false;
        }
        BlockPos currentTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone()
                && currentTarget != null
                && context.isInsideWorkArea(currentTarget)) {
            return true;
        }

        int minX = context.workMin().getX();
        int minY = context.workMin().getY();
        int minZ = context.workMin().getZ();
        int maxX = context.workMax().getX();
        int maxY = context.workMax().getY();
        int maxZ = context.workMax().getZ();

        for (int attempt = 0; attempt < ROAM_CANDIDATE_ATTEMPTS; attempt++) {
            BlockPos candidate = new BlockPos(
                    Mth.nextInt(villager.getRandom(), minX, maxX),
                    Mth.nextInt(villager.getRandom(), minY, maxY),
                    Mth.nextInt(villager.getRandom(), minZ, maxZ));
            if (!context.isLoaded(level, candidate) || !HiredMoveToBlockFaceJob.isValidApproachPosition(level, candidate)) {
                continue;
            }
            Path path = villager.getNavigation().createPath(candidate, 0);
            if (path != null && path.canReach() && pathStaysInsideWorkArea(path, context)) {
                boolean moved = villager.getNavigation().moveTo(path, speed);
                if (moved) {
                    HiredPathMemory.rememberNavigationProgress(level, villager, candidate, villager.distanceToSqr(candidate.getCenter()));
                    setTaskState(context, HiredWorkerTaskState.IDLE);
                    return true;
                }
            }
        }
        return false;
    }

    protected boolean moveToTarget(ServerLevel level, Villager villager, HiredWorkContext context, HiredPathTarget target, double speed) {
        HiredPathTarget currentTarget = target;
        if (!context.isInsideWorkArea(target.blockPos())
                || !context.isInsideWorkArea(target.approachPos())
                || !context.isLoaded(level, target.blockPos())
                || !context.isLoaded(level, target.approachPos())) {
            return false;
        }
        if (canWorkFromCurrentPosition(level, villager, context, currentTarget)) {
            holdWorkPosition(villager, currentTarget);
            return true;
        }
        if (!ensureNavigationRemainsInsideWorkArea(context, villager)) {
            return false;
        }

        if (villager.distanceToSqr(currentTarget.approachPos().getCenter()) <= 2.25D
                && !canMineFromCurrentPosition(level, villager, currentTarget)) {
            HiredPathTarget repickedTarget = bestWorkTarget(level, villager, context, currentTarget.blockPos());
            if (repickedTarget != null
                    && (!repickedTarget.approachPos().equals(currentTarget.approachPos())
                    || !repickedTarget.hitPos().equals(currentTarget.hitPos()))) {
                prepareBreakingTarget(level, context, villager, repickedTarget);
                currentTarget = repickedTarget;
            } else if (settleIntoApproach(villager, currentTarget, speed)) {
                HiredPathMemory.rememberNavigationProgress(
                        level,
                        villager,
                        currentTarget.approachPos(),
                        villager.distanceToSqr(currentTarget.approachPos().getCenter()));
                return true;
            } else {
                villager.getNavigation().stop();
                HiredPathMemory.clearNavigationProgress(villager);
                return false;
            }
        }

        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone() && currentTarget.approachPos().equals(navigationTarget)) {
            if (HiredPathMemory.isNavigationBlocked(
                    level,
                    villager,
                    currentTarget.approachPos(),
                    villager.distanceToSqr(currentTarget.approachPos().getCenter()))) {
                villager.getNavigation().stop();
                HiredPathMemory.clearNavigationProgress(villager);
                return false;
            }
            return true;
        }
        Path path = villager.getNavigation().createPath(currentTarget.approachPos(), 0);
        if (path != null && path.canReach() && pathStaysInsideWorkArea(path, context)) {
            boolean moved = villager.getNavigation().moveTo(path, speed);
            if (moved) {
                HiredPathMemory.rememberNavigationProgress(
                        level,
                        villager,
                        currentTarget.approachPos(),
                        villager.distanceToSqr(currentTarget.approachPos().getCenter()));
            } else {
                HiredPathMemory.clearNavigationProgress(villager);
            }
            return moved;
        }
        if (villager.distanceToSqr(currentTarget.approachPos().getCenter()) <= 2.25D
                && settleIntoApproach(villager, currentTarget, speed)) {
            HiredPathMemory.rememberNavigationProgress(
                    level,
                    villager,
                    currentTarget.approachPos(),
                    villager.distanceToSqr(currentTarget.approachPos().getCenter()));
            return true;
        }
        HiredPathMemory.clearNavigationProgress(villager);
        return false;
    }

    private boolean settleIntoApproach(Villager villager, HiredPathTarget target, double speed) {
        Vec3 center = target.approachPos().getCenter();
        double horizontalDistanceSqr = Mth.square(villager.getX() - center.x)
                + Mth.square(villager.getZ() - center.z);
        if (horizontalDistanceSqr <= APPROACH_CENTER_SETTLE_SQR
                && Math.abs(villager.getY() - target.approachPos().getY()) <= 0.125D) {
            return false;
        }
        villager.getMoveControl().setWantedPosition(center.x, target.approachPos().getY(), center.z, speed);
        return true;
    }

    protected DepositResult depositOutputsOrMoveToStorage(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            double speed) {
        if (!context.autoDepositOutputs() || !context.hasOutputToDeposit()) {
            HiredWorkerBrain.clearStorageTarget(context);
            clearStorageFullStatus(context);
            context.state().remove(HiredWorkContext.OUTPUT_DEPOSITED_THIS_STORAGE_TRIP_TAG);
            return DepositResult.NOT_NEEDED;
        }
        BlockPos storage = context.nearestDepositStorage(level, villager);
        if (storage == null) {
            HiredWorkerBrain.setFailure(context, "missing_or_unreachable_storage", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_NO_STORAGE);
            return DepositResult.UNAVAILABLE;
        }
        if (!context.isLoaded(level, storage)) {
            HiredWorkerBrain.setFailure(context, "storage_unloaded", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_NO_STORAGE);
            return DepositResult.UNAVAILABLE;
        }
        HiredWorkerBrain.Snapshot worker = HiredWorkerBrain.snapshot(context.state(), level.getGameTime());
        if (worker.storageTargetPos() != null && !worker.storageTargetPos().equals(storage)) {
            context.state().remove(HiredWorkContext.OUTPUT_DEPOSITED_THIS_STORAGE_TRIP_TAG);
        }
        HiredWorkerBrain.setStorageTarget(context, storage);
        HiredStorageNavigationGoal.Result moveResult = HiredStorageNavigationGoal.moveToStorageTarget(
                level,
                context,
                villager,
                storage,
                speed);
        if (moveResult == HiredStorageNavigationGoal.Result.MOVING) {
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
            return DepositResult.MOVING;
        }
        if (moveResult == HiredStorageNavigationGoal.Result.FAILED) {
            HiredWorkerBrain.setFailure(context, "storage_path_failed", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_NO_STORAGE);
            return DepositResult.UNAVAILABLE;
        }
        faceBlock(villager, Vec3.atCenterOf(storage));
        if (context.depositOutputsAtStorage(villager, storage)) {
            HiredWorkerBrain.clearFailure(context);
            clearStorageFullStatus(context);
            context.state().putBoolean(HiredWorkContext.OUTPUT_DEPOSITED_THIS_STORAGE_TRIP_TAG, true);
            setTaskState(context, HiredWorkerTaskState.DEPOSITING);
            if (!context.hasOutputToDeposit()) {
                AssignedStorageService.closeStorageFeedback(level, storage);
                HiredWorkerBrain.clearStorageTarget(context);
                HiredStorageNavigationGoal.clearStorageNavigationState(context);
                context.state().remove(HiredWorkContext.OUTPUT_DEPOSITED_THIS_STORAGE_TRIP_TAG);
                stopWorkNavigation(villager);
                setTaskState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, context.workCenter());
            }
            swingWorkTool(villager);
            return DepositResult.DEPOSITED;
        }
        AssignedStorageService.closeStorageFeedback(level, storage);
        HiredWorkerBrain.setFailure(context, "storage_full_or_unavailable", level.getGameTime() + 100L);
        HiredWorkerBrain.setStorageTarget(context, storage);
        setTaskState(context, HiredWorkerTaskState.PAUSED_STORAGE_FULL);
        HiredStorageNavigationGoal.wanderNearStorage(level, context, villager, storage, speed);
        return DepositResult.STORAGE_FULL;
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

    protected boolean isCloseEnough(Villager villager, HiredPathTarget target) {
        return HiredMoveToBlockFaceJob.isCloseEnough(villager, target);
    }

    protected String storageFullStatus(HiredWorkContext context) {
        if (!context.state().getBoolean(STORAGE_FULL_STATUS_SHOWN_TAG)) {
            context.state().putBoolean(STORAGE_FULL_STATUS_SHOWN_TAG, true);
            if (context.state().getBoolean(HiredWorkContext.OUTPUT_DEPOSITED_THIS_STORAGE_TRIP_TAG)) {
                return "I deposited what I could, but storage is full now.";
            }
            return "I have no where left to deposit these collected items.";
        }
        return "I am staying near storage and waiting for room to deposit collected items.";
    }

    protected void clearStorageFullStatus(HiredWorkContext context) {
        context.state().remove(STORAGE_FULL_STATUS_SHOWN_TAG);
    }

    protected void holdWorkPosition(Villager villager, HiredPathTarget target) {
        stopWorkNavigation(villager);
        faceBlock(villager, target);
        villager.setDeltaMovement(villager.getDeltaMovement().multiply(0.0D, 1.0D, 0.0D));
    }

    private boolean ensureNavigationRemainsInsideWorkArea(HiredWorkContext context, Villager villager) {
        if (!context.isInsideWorkArea(villager.blockPosition())) {
            stopWorkNavigation(villager);
            return false;
        }
        Path path = villager.getNavigation().getPath();
        if (path != null && !pathStaysInsideWorkArea(path, context)) {
            stopWorkNavigation(villager);
            return false;
        }
        return true;
    }

    private boolean pathStaysInsideWorkArea(Path path, HiredWorkContext context) {
        return HiredMoveToBlockFaceJob.pathStaysInsideFilter(path, context::isInsideWorkArea);
    }

    protected void stopWorkNavigation(Villager villager) {
        if (!villager.getNavigation().isDone()) {
            villager.getNavigation().stop();
        }
        HiredPathMemory.clearNavigationProgress(villager);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.PATH);
    }

    protected boolean canMineFromCurrentPosition(ServerLevel level, Villager villager, HiredPathTarget target) {
        return HiredMoveToBlockFaceJob.canReachFromCurrentPosition(level, villager, target);
    }

    protected void damageTool(HiredWorkContext context, Villager villager, ItemStack tool) {
        if (!tool.isEmpty() && tool.isDamageableItem()) {
            tool.hurtAndBreak(1, villager, EquipmentSlot.MAINHAND);
            context.inventory().syncMainHandEquipment();
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

    protected boolean hasLineOfSightToBlock(ServerLevel level, Villager villager, Vec3 start, BlockPos target, Vec3 hitPos) {
        return HiredMoveToBlockFaceJob.hasLineOfSightToBlock(level, villager, start, target, hitPos);
    }

    protected enum DepositResult {
        NOT_NEEDED,
        DEPOSITED,
        MOVING,
        STORAGE_FULL,
        UNAVAILABLE
    }

}
