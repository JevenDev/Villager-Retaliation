package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.entity.VillagerFishingHook;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.ItemAbilities;

public final class FishingWorker extends AbstractBlockWorker {
    private static final String ACTIVE_HOOK_ID_TAG = "FishingActiveHookId";
    private static final String ACTIVE_WATER_POS_TAG = "FishingWaterPos";
    private static final String ACTIVE_APPROACH_POS_TAG = "FishingApproachPos";
    private static final String CATCH_COMPLETED_TAG = "FishingCatchCompleted";
    private static final String CATCH_OVERFLOW_TAG = "FishingCatchOverflow";
    private static final String COLLECTING_ROD_TAG = "FishingCollectingRod";
    private static final String DEPOSITING_OUTPUTS_TAG = "FishingDepositingOutputs";
    private static final int MAX_WATER_PATH_ATTEMPTS = 32;
    private static final int APPROACH_RADIUS = 6;
    private static final int WATER_SEARCH_MARGIN = 8;
    private static final double MAX_CAST_DISTANCE_SQR = 64.0D;
    private static final double IDEAL_CAST_HORIZONTAL_DISTANCE = 5.0D;
    private static final double CLOSE_CAST_HORIZONTAL_DISTANCE = 3.0D;
    private static final double CAST_SPEED = 0.45D;

    @Override
    public HiredVillagerRole role() {
        return HiredVillagerRole.FISHING;
    }

    @Override
    public void maintain(ServerLevel level, Villager villager, HiredWorkContext context) {
        expireWorkPathMemory(level);
        VillagerFishingHook hook = activeHook(level, context);
        if (hook == null) {
            return;
        }
        if (!hasEquippedFishingRod(context)) {
            hook.discard();
            clearHookState(context);
            return;
        }
        faceBlock(villager, hook.position());
        if (hook.isBiting()) {
            retrieveCatch(level, villager, context, hook);
        }
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        if (context.state().getBoolean(CATCH_COMPLETED_TAG)) {
            return finishCompletedCatch(level, villager, context);
        }
        context.state().remove(CATCH_OVERFLOW_TAG);

        if (!context.hasWorkArea()) {
            clearFishingTarget(context);
            return waitForWorkAreaAssignment(level, villager, context);
        }

        WorkResult depositResult = depositFullOutputIfNeeded(level, villager, context);
        if (depositResult != null) {
            return depositResult;
        }

        VillagerFishingHook hook = activeHook(level, context);
        if (hook != null && !hasEquippedFishingRod(context)) {
            hook.discard();
            clearHookState(context);
            hook = null;
        }

        ItemStack rod = ensureFishingRod(level, villager, context);
        if (rod.isEmpty()) {
            if (context.state().getBoolean(COLLECTING_ROD_TAG)) {
                return WorkResult.progressed("interaction.work.fishing.collecting_rod");
            }
            return WorkResult.idle("interaction.work.fishing.missing_rod");
        }
        context.state().remove(COLLECTING_ROD_TAG);

        if (hook != null) {
            if (hook.isBiting()) {
                retrieveCatch(level, villager, context, hook);
                return finishCompletedCatch(level, villager, context);
            }
            setTaskState(context, HiredWorkerTaskState.WORKING, hook.blockPosition());
            faceBlock(villager, hook.position());
            return WorkResult.progressed("interaction.work.fishing.waiting_for_bite");
        }
        clearHookState(context);

        FishingSpot spot = findFishingSpot(level, villager, context);
        if (spot == null) {
            clearFishingTarget(context);
            if (roamInsideWorkArea(level, villager, context, 0.35D)) {
                return WorkResult.progressed("interaction.work.fishing.roaming");
            }
            HiredWorkerBrain.setFailure(context, "no_open_water", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
            return WorkResult.idle("interaction.work.fishing.no_open_water");
        }

        rememberFishingTarget(context, spot);
        if (!canCastFromCurrentPosition(villager, context, spot)) {
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, spot.water());
            if (!moveToApproach(level, villager, context, spot.approach(), CAST_SPEED)) {
                HiredWorkerBrain.setFailure(context, "water_unreachable", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, spot.water());
                return WorkResult.idle("interaction.work.fishing.water_unreachable");
            }
            return WorkResult.progressed("interaction.work.fishing.moving_to_water");
        }

        castRod(level, villager, context, rod, spot);
        setTaskState(context, HiredWorkerTaskState.WORKING, spot.water());
        return WorkResult.progressed("interaction.work.fishing.cast");
    }

    private WorkResult depositFullOutputIfNeeded(ServerLevel level, Villager villager, HiredWorkContext context) {
        boolean hasStackSpace = context.hasOutputSpace();
        boolean hasEmptyOutputSpace = context.inventory().hasEmptyOutputSpace();
        boolean depositingOutputs = context.state().getBoolean(DEPOSITING_OUTPUTS_TAG);
        if (!depositingOutputs && hasStackSpace && hasEmptyOutputSpace) {
            return null;
        }
        if (context.hasOutputToDeposit()) {
            context.state().putBoolean(DEPOSITING_OUTPUTS_TAG, true);
            DepositResult depositResult = depositOutputsOrMoveToStorage(level, context, villager, CAST_SPEED);
            if (depositResult == DepositResult.MOVING) {
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
                return WorkResult.progressed("interaction.work.fishing.depositing_output");
            }
            if (depositResult == DepositResult.DEPOSITED && context.hasOutputToDeposit()) {
                return WorkResult.progressed("interaction.work.fishing.depositing_output");
            }
            if (depositResult == DepositResult.STORAGE_FULL) {
                return WorkResult.idle(storageFullStatus(context));
            }
            if (!context.hasOutputToDeposit()) {
                context.state().remove(DEPOSITING_OUTPUTS_TAG);
            }
        }
        if (!context.hasOutputSpace() || !context.inventory().hasEmptyOutputSpace()) {
            HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY);
            return WorkResult.idle("interaction.work.fishing.output_full_blocked");
        }
        context.state().remove(DEPOSITING_OUTPUTS_TAG);
        return null;
    }

    private WorkResult finishCompletedCatch(ServerLevel level, Villager villager, HiredWorkContext context) {
        WorkResult depositResult = depositFullOutputIfNeeded(level, villager, context);
        if (depositResult != null) {
            return depositResult;
        }
        boolean overflow = context.state().getBoolean(CATCH_OVERFLOW_TAG);
        context.state().remove(CATCH_COMPLETED_TAG);
        context.state().remove(CATCH_OVERFLOW_TAG);
        clearFishingTarget(context);
        setTaskState(context, HiredWorkerTaskState.IDLE);
        return overflow
                ? WorkResult.completed("interaction.work.fishing.completed_overflow")
                : WorkResult.completed("interaction.work.fishing.completed");
    }

    @Override
    public void stop(ServerLevel level, Villager villager, HiredWorkContext context) {
        VillagerFishingHook hook = activeHook(level, context);
        if (hook != null) {
            hook.discard();
        }
        clearFishingTarget(context);
        stopWorkNavigation(villager);
        super.stop(level, villager, context);
    }

    private ItemStack ensureFishingRod(ServerLevel level, Villager villager, HiredWorkContext context) {
        ItemStack rod = context.inventory().equipBestTool(this::isFishingRod, stack -> rodScore(level, villager, stack));
        if (!rod.isEmpty()) {
            HiredStorageNavigationGoal.clearStorageTarget(context);
            HiredWorkerBrain.clearFailure(context);
            return rod;
        }
        if (!context.useAssignedStorageForSupplies()) {
            context.state().remove(COLLECTING_ROD_TAG);
            HiredWorkerBrain.setFailure(context, "missing_fishing_rod", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_MISSING_TOOL);
            return ItemStack.EMPTY;
        }
        BlockPos storage = AssignedStorageService.nearestAssignedToolStoragePosContaining(level, villager, this::isFishingRod);
        if (storage == null) {
            context.state().remove(COLLECTING_ROD_TAG);
            HiredWorkerBrain.setFailure(context, "missing_fishing_rod", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_MISSING_TOOL);
            return ItemStack.EMPTY;
        }
        HiredStorageNavigationGoal.Result result = HiredStorageNavigationGoal.moveToStorageTarget(level, context, villager, storage, CAST_SPEED);
        if (result == HiredStorageNavigationGoal.Result.MOVING) {
            context.state().putBoolean(COLLECTING_ROD_TAG, true);
            HiredWorkerBrain.setStorageTarget(context, storage);
            HiredWorkerBrain.clearFailure(context);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
            return ItemStack.EMPTY;
        }
        if (result == HiredStorageNavigationGoal.Result.FAILED) {
            context.state().remove(COLLECTING_ROD_TAG);
            HiredWorkerBrain.setFailure(context, "fishing_rod_storage_unreachable", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, storage);
            return ItemStack.EMPTY;
        }
        int moved = AssignedStorageService.transferToolAtAssignedStorage(
                villager,
                storage,
                this::isFishingRod,
                offered -> context.inventory().insertToolFromStorage(offered));
        rod = context.inventory().equipBestTool(this::isFishingRod, stack -> rodScore(level, villager, stack));
        if (moved <= 0 || rod.isEmpty()) {
            context.state().remove(COLLECTING_ROD_TAG);
            HiredWorkerBrain.setFailure(context, "fishing_rod_inventory_full", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, storage);
            return ItemStack.EMPTY;
        }
        context.state().remove(COLLECTING_ROD_TAG);
        HiredStorageNavigationGoal.clearStorageTarget(context);
        HiredWorkerBrain.clearFailure(context);
        setTaskState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, context.workCenter());
        return rod;
    }

    private boolean isFishingRod(ItemStack stack) {
        return !stack.isEmpty() && stack.canPerformAction(ItemAbilities.FISHING_ROD_CAST);
    }

    private boolean hasEquippedFishingRod(HiredWorkContext context) {
        return isFishingRod(context.inventory().getItem(HiredJobInventory.MAINHAND_SLOT));
    }

    private double rodScore(ServerLevel level, Villager villager, ItemStack stack) {
        int luck = EnchantmentHelper.getFishingLuckBonus(level, stack, villager);
        float lure = EnchantmentHelper.getFishingTimeReduction(level, stack, villager);
        int durability = stack.getMaxDamage() <= 0 ? 1000 : stack.getMaxDamage() - stack.getDamageValue();
        return luck * 10000.0D + lure * 1000.0D + durability;
    }

    private void castRod(ServerLevel level, Villager villager, HiredWorkContext context, ItemStack rod, FishingSpot spot) {
        stopWorkNavigation(villager);
        Vec3 target = castTarget(villager, spot.water());
        faceCastTarget(villager, target);
        int lure = (int)(EnchantmentHelper.getFishingTimeReduction(level, rod, villager) * 20.0F);
        int luck = EnchantmentHelper.getFishingLuckBonus(level, rod, villager);
        discardExistingOwnedHooks(level, villager);
        VillagerFishingHook hook = new VillagerFishingHook(villager, level, target, luck, lure);
        level.addFreshEntity(hook);
        context.state().putInt(ACTIVE_HOOK_ID_TAG, hook.getId());
        level.playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        villager.swing(InteractionHand.MAIN_HAND, true);
        villager.gameEvent(GameEvent.ITEM_INTERACT_START);
    }

    private void faceCastTarget(Villager villager, Vec3 target) {
        double dx = target.x - villager.getX();
        double dz = target.z - villager.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal > 0.0001D) {
            float yaw = (float)(Mth.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
            villager.setYRot(yaw);
            villager.yRotO = yaw;
            villager.yBodyRot = yaw;
            villager.yBodyRotO = yaw;
            villager.yHeadRot = yaw;
            villager.yHeadRotO = yaw;
        }
        double dy = target.y - villager.getEyeY();
        float pitch = (float)(-(Mth.atan2(dy, horizontal) * 180.0D / Math.PI));
        villager.setXRot(pitch);
        villager.xRotO = pitch;
        faceBlock(villager, target);
    }

    private Vec3 castTarget(Villager villager, BlockPos water) {
        Vec3 waterCenter = water.getCenter();
        Vec3 horizontal = new Vec3(waterCenter.x - villager.getX(), 0.0D, waterCenter.z - villager.getZ());
        Vec3 outward = horizontal.lengthSqr() > 0.0001D ? horizontal.normalize().scale(0.35D) : Vec3.ZERO;
        return waterCenter.add(outward.x, 0.45D, outward.z);
    }

    private void discardExistingOwnedHooks(ServerLevel level, Villager villager) {
        for (VillagerFishingHook hook : level.getEntitiesOfClass(
                VillagerFishingHook.class,
                villager.getBoundingBox().inflate(40.0D),
                hook -> hook.getOwner() == villager)) {
            hook.discard();
        }
    }

    private void retrieveCatch(ServerLevel level, Villager villager, HiredWorkContext context, VillagerFishingHook hook) {
        ItemStack rod = context.inventory().getItem(HiredJobInventory.MAINHAND_SLOT);
        VillagerFishingHook.CatchResult result = hook.retrieve(rod);
        boolean overflow = false;
        for (ItemStack item : result.items()) {
            ItemStack remainder = context.storeOutputAfterDepositIfFull(villager, item.copy());
            if (!remainder.isEmpty()) {
                overflow = true;
                villager.spawnAtLocation(remainder);
            }
        }
        if (result.rodDamage() > 0 && !rod.isEmpty()) {
            rod.hurtAndBreak(result.rodDamage(), villager, EquipmentSlot.MAINHAND);
            context.inventory().syncMainHandEquipment();
            context.inventory().setChanged();
        }
        level.playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 1.0F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        villager.swing(InteractionHand.MAIN_HAND, true);
        villager.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
        context.state().putBoolean(CATCH_COMPLETED_TAG, true);
        if (overflow) {
            context.state().putBoolean(CATCH_OVERFLOW_TAG, true);
        }
        clearHookState(context);
    }

    private VillagerFishingHook activeHook(ServerLevel level, HiredWorkContext context) {
        int id = context.state().getInt(ACTIVE_HOOK_ID_TAG);
        if (id <= 0) {
            return null;
        }
        return level.getEntity(id) instanceof VillagerFishingHook hook && hook.isAlive() ? hook : null;
    }

    private FishingSpot findFishingSpot(ServerLevel level, Villager villager, HiredWorkContext context) {
        FishingSpot remembered = rememberedFishingSpot(level, context);
        if (remembered != null) {
            return remembered;
        }
        List<BlockPos> water = new ArrayList<>();
        for (BlockPos raw : waterSearchPositions(context)) {
            BlockPos pos = raw.immutable();
            if (isUsableFishingWater(level, context, pos)) {
                water.add(pos);
            }
        }
        BlockPos currentPos = villager.blockPosition().immutable();
        water.sort(Comparator.comparingDouble(pos -> waterCastScore(currentPos, pos)));
        for (BlockPos waterPos : water) {
            if (canCastFromPosition(level, villager, context, currentPos, waterPos)) {
                return new FishingSpot(waterPos, currentPos);
            }
        }
        int attempts = 0;
        for (BlockPos waterPos : water) {
            if (attempts++ >= MAX_WATER_PATH_ATTEMPTS) {
                break;
            }
            BlockPos approach = bestApproach(level, villager, context, waterPos);
            if (approach != null) {
                return new FishingSpot(waterPos, approach);
            }
        }
        return null;
    }

    private FishingSpot rememberedFishingSpot(ServerLevel level, HiredWorkContext context) {
        if (!context.state().contains(ACTIVE_WATER_POS_TAG) || !context.state().contains(ACTIVE_APPROACH_POS_TAG)) {
            return null;
        }
        BlockPos water = BlockPos.of(context.state().getLong(ACTIVE_WATER_POS_TAG));
        BlockPos approach = BlockPos.of(context.state().getLong(ACTIVE_APPROACH_POS_TAG));
        if (isUsableFishingWater(level, context, water)
                && context.isInsideWorkArea(approach)
                && isDryApproachPosition(level, approach)
                && hasCastLine(level, approach, water)) {
            return new FishingSpot(water, approach);
        }
        clearFishingTarget(context);
        return null;
    }

    private boolean isUsableFishingWater(ServerLevel level, HiredWorkContext context, BlockPos pos) {
        return context.isLoaded(level, pos)
                && level.getFluidState(pos).is(Fluids.WATER)
                && !level.getFluidState(pos.above()).is(FluidTags.WATER);
    }

    private Iterable<BlockPos> waterSearchPositions(HiredWorkContext context) {
        BlockPos min = context.workMin().offset(-WATER_SEARCH_MARGIN, -WATER_SEARCH_MARGIN, -WATER_SEARCH_MARGIN);
        BlockPos max = context.workMax().offset(WATER_SEARCH_MARGIN, WATER_SEARCH_MARGIN, WATER_SEARCH_MARGIN);
        return BlockPos.betweenClosed(min, max);
    }

    private BlockPos bestApproach(ServerLevel level, Villager villager, HiredWorkContext context, BlockPos water) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos raw : BlockPos.betweenClosed(
                water.offset(-APPROACH_RADIUS, -2, -APPROACH_RADIUS),
                water.offset(APPROACH_RADIUS, 2, APPROACH_RADIUS))) {
            BlockPos candidate = raw.immutable();
            if (!context.isInsideWorkArea(candidate)
                    || !isDryApproachPosition(level, candidate)
                    || candidate.getCenter().distanceToSqr(water.getCenter()) > MAX_CAST_DISTANCE_SQR
                    || !hasCastLine(level, candidate, water)) {
                continue;
            }
            Path path = HiredPathMemory.createPath(level, villager, candidate, 0);
            if (path == null || !path.canReach() || !pathStaysDryAndInside(level, path, context)) {
                continue;
            }
            int vertical = Math.abs(candidate.getY() - villager.blockPosition().getY());
            double score = villager.distanceToSqr(candidate.getCenter())
                    + path.getNodeCount() * 1.5D
                    + vertical * vertical * 12.0D
                    + waterCastScore(candidate, water) * 0.5D
                    + HiredMoveToBlockFaceJob.terrainCost(level, candidate);
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private boolean hasCastLine(ServerLevel level, BlockPos approach, BlockPos water) {
        Vec3 start = new Vec3(approach.getX() + 0.5D, approach.getY() + 1.4D, approach.getZ() + 0.5D);
        Vec3 end = water.getCenter().add(0.0D, 0.25D, 0.0D);
        BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(water) || hit.getBlockPos().relative(Direction.UP).equals(water);
    }

    private boolean canCastFromPosition(ServerLevel level, Villager villager, HiredWorkContext context, BlockPos approach, BlockPos water) {
        return context.isInsideWorkArea(approach)
                && isDryApproachPosition(level, approach)
                && approach.getCenter().distanceToSqr(water.getCenter()) <= MAX_CAST_DISTANCE_SQR
                && hasCastLine(level, approach, water);
    }

    private boolean canCastFromCurrentPosition(Villager villager, HiredWorkContext context, FishingSpot spot) {
        return context.isInsideWorkArea(villager.blockPosition())
                && villager.distanceToSqr(spot.approach().getCenter()) <= 2.25D
                && villager.distanceToSqr(spot.water().getCenter()) <= MAX_CAST_DISTANCE_SQR;
    }

    private double waterCastScore(BlockPos approach, BlockPos water) {
        double dx = water.getX() + 0.5D - (approach.getX() + 0.5D);
        double dz = water.getZ() + 0.5D - (approach.getZ() + 0.5D);
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double idealPenalty = Math.abs(horizontalDistance - IDEAL_CAST_HORIZONTAL_DISTANCE);
        double closePenalty = horizontalDistance < CLOSE_CAST_HORIZONTAL_DISTANCE
                ? Math.pow(CLOSE_CAST_HORIZONTAL_DISTANCE - horizontalDistance, 2.0D) * 8.0D
                : 0.0D;
        return idealPenalty * idealPenalty + closePenalty + approach.getCenter().distanceToSqr(water.getCenter()) * 0.02D;
    }

    private boolean moveToApproach(ServerLevel level, Villager villager, HiredWorkContext context, BlockPos approach, double speed) {
        if (!context.isInsideWorkArea(approach) || !context.isInsideWorkArea(villager.blockPosition())) {
            return false;
        }
        if (villager.distanceToSqr(approach.getCenter()) <= 2.25D) {
            stopWorkNavigation(villager);
            return true;
        }
        Path path = HiredPathMemory.createPath(level, villager, approach, 0);
        if (path == null || !path.canReach() || !pathStaysDryAndInside(level, path, context)) {
            HiredPathMemory.clearNavigationProgress(villager);
            return false;
        }
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(approach));
        boolean moved = VillagerTaskNavigationUtil.moveToHiredPath(villager, path, approach, speed, 0);
        if (moved) {
            HiredPathMemory.rememberNavigationProgress(level, villager, approach, villager.distanceToSqr(approach.getCenter()));
        }
        return moved;
    }

    private boolean isDryApproachPosition(ServerLevel level, BlockPos pos) {
        return HiredMoveToBlockFaceJob.isValidApproachPosition(level, pos)
                && level.getFluidState(pos).isEmpty()
                && level.getFluidState(pos.above()).isEmpty();
    }

    private boolean pathStaysDryAndInside(ServerLevel level, Path path, HiredWorkContext context) {
        if (path == null) {
            return false;
        }
        for (int i = 0; i < path.getNodeCount(); i++) {
            BlockPos pos = path.getNode(i).asBlockPos();
            if (!context.isInsideWorkArea(pos)
                    || !context.isLoaded(level, pos)
                    || !level.getFluidState(pos).isEmpty()
                    || !level.getFluidState(pos.above()).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void rememberFishingTarget(HiredWorkContext context, FishingSpot spot) {
        context.state().putLong(ACTIVE_WATER_POS_TAG, spot.water().asLong());
        context.state().putLong(ACTIVE_APPROACH_POS_TAG, spot.approach().asLong());
    }

    private void clearHookState(HiredWorkContext context) {
        context.state().remove(ACTIVE_HOOK_ID_TAG);
    }

    private void clearFishingTarget(HiredWorkContext context) {
        clearHookState(context);
        context.state().remove(ACTIVE_WATER_POS_TAG);
        context.state().remove(ACTIVE_APPROACH_POS_TAG);
        context.state().remove(COLLECTING_ROD_TAG);
        context.state().remove(DEPOSITING_OUTPUTS_TAG);
        context.setProgressTicks(0);
    }

    private record FishingSpot(BlockPos water, BlockPos approach) {
    }
}
