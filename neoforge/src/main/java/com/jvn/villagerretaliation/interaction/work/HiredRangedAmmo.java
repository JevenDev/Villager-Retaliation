package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.mixin.AbstractArrowAccessor;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public final class HiredRangedAmmo {
    private static final String CONSUMED_CROSSBOW_PROJECTILE_TAG =
            "VillagerRetaliationConsumedCrossbowProjectile";
    public static final String FAILURE_STORAGE_PATH = "ranged_ammo_storage_path_failed";
    public static final String FAILURE_INVENTORY_FULL = "ranged_ammo_inventory_full";
    public static final String FAILURE_MISSING = "missing_ranged_ammo";
    public static final String STATUS_COLLECTING = "interaction.work.status.collecting_arrows";
    public static final String STATUS_GATHERED = "interaction.work.status.gathered_arrows";
    public static final String STATUS_MISSING = "interaction.work.status.missing_arrows";
    public static final String STATUS_STORAGE_UNREACHABLE = "interaction.work.status.arrow_storage_unreachable";
    public static final String STATUS_INVENTORY_FULL = "interaction.work.status.arrow_inventory_full";
    private static final int RESTOCK_COUNT = 64;
    private static final double ARROW_PICKUP_HORIZONTAL_REACH_SQR = 0.64D;
    private static final double ARROW_PICKUP_VERTICAL_REACH = 1.25D;
    private static final int ARROW_PATH_CLOSE_ENOUGH = 0;

    private HiredRangedAmmo() {
    }

    public static boolean isAmmo(ItemStack stack) {
        return stack.is(Items.ARROW)
                || stack.is(Items.SPECTRAL_ARROW)
                || stack.is(Items.TIPPED_ARROW);
    }

    public static boolean isWeaponRequiringAmmo(ItemStack weapon) {
        return VillagerRetaliationVillagerWeapons.isBowWeapon(weapon)
                || VillagerRetaliationVillagerWeapons.isCrossbowWeapon(weapon);
    }

    public static boolean hasAmmo(HiredWorkContext context) {
        return !context.inventory().findSupply(HiredRangedAmmo::isAmmo).isEmpty();
    }

    public static boolean hasAmmo(Villager villager) {
        return VillagerInventoryAccess.hasCarriedItem(villager, HiredRangedAmmo::isAmmo);
    }

    public static boolean hasAmmoForEquippedWeapon(HiredWorkContext context) {
        return hasAmmoForEquippedWeapon(context, null);
    }

    public static boolean hasAmmoForEquippedWeapon(HiredWorkContext context, Villager villager) {
        ItemStack weapon = ammoCheckedWeapon(context, villager);
        return !isWeaponRequiringAmmo(weapon) || hasAmmo(context);
    }

    public static boolean requiresAmmo(AbstractVillager villager, ItemStack weapon) {
        return isWeaponRequiringAmmo(weapon)
                && villager instanceof Villager;
    }

    public static boolean canUseRangedAttack(AbstractVillager villager, ItemStack weapon) {
        if (!requiresAmmo(villager, weapon)) {
            return true;
        }
        if (VillagerRetaliationVillagerWeapons.isCrossbowWeapon(weapon) && CrossbowItem.isCharged(weapon)) {
            return true;
        }
        return villager instanceof Villager regular && hasAmmo(regular);
    }

    public static boolean isRangedAttackBlockedByAmmo(AbstractVillager villager) {
        ItemStack weapon = VillagerRetaliationVillagerWeapons.getPrimaryWeapon(villager);
        return VillagerRetaliationVillagerWeapons.isRangedWeapon(weapon)
                && !canUseRangedAttack(villager, weapon);
    }

    public static ItemStack consumeAmmo(Villager villager) {
        return VillagerInventoryAccess.takeCarriedItem(villager, HiredRangedAmmo::isAmmo);
    }

    public static ItemStack consumeAmmo(AbstractVillager villager) {
        return villager instanceof Villager regular ? consumeAmmo(regular) : ItemStack.EMPTY;
    }

    public static void markConsumedCrossbowProjectile(ItemStack stack) {
        if (!stack.isEmpty()) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack,
                    tag -> tag.putBoolean(CONSUMED_CROSSBOW_PROJECTILE_TAG, true));
        }
    }

    public static boolean clearConsumedCrossbowProjectileMarker(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (!customData.copyTag().getBoolean(CONSUMED_CROSSBOW_PROJECTILE_TAG)) {
            return false;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> tag.remove(CONSUMED_CROSSBOW_PROJECTILE_TAG));
        return true;
    }

    public static WorkResult ensureReady(ServerLevel level, Villager villager, HiredWorkContext context, double speed) {
        ItemStack weapon = ammoCheckedWeapon(context, villager);
        if (!isWeaponRequiringAmmo(weapon)) {
            return null;
        }
        if (hasAmmo(context)) {
            HiredWorkerBrain.clearFailure(context);
            return null;
        }
        if (context.inventory().promoteOutputToSupply(HiredRangedAmmo::isAmmo, RESTOCK_COUNT) > 0
                && hasAmmo(context)) {
            HiredWorkerBrain.clearFailure(context);
            return null;
        }
        WorkResult recoveredArrow = recoverNearbyArrow(level, villager, context, speed);
        if (recoveredArrow != null) {
            return recoveredArrow;
        }
        if (!context.useAssignedStorageForSupplies()) {
            return missingAmmo(context);
        }

        BlockPos storage = AssignedStorageService.nearestAssignedNonPaymentStoragePosContaining(
                level,
                villager,
                HiredRangedAmmo::isAmmo);
        if (storage == null) {
            return missingAmmo(context);
        }

        HiredWorkerBrain.setStorageTarget(context, storage);
        HiredStorageNavigationGoal.Result result = HiredStorageNavigationGoal.moveToStorageTarget(
                level,
                context,
                villager,
                storage,
                speed);
        if (result == HiredStorageNavigationGoal.Result.MOVING) {
            HiredWorkerBrain.clearFailure(context);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.MOVING_TO_STORAGE, storage);
            return WorkResult.progressed(STATUS_COLLECTING);
        }
        if (result == HiredStorageNavigationGoal.Result.FAILED) {
            HiredStorageNavigationGoal.clearStorageTarget(context);
            HiredWorkerBrain.setFailure(context, FAILURE_STORAGE_PATH, level.getGameTime() + 100L);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.FAILED_COOLDOWN, storage);
            return WorkResult.idle(STATUS_STORAGE_UNREACHABLE);
        }

        int moved = AssignedStorageService.transferItemsAtAssignedNonPaymentStorage(
                villager,
                storage,
                HiredRangedAmmo::isAmmo,
                RESTOCK_COUNT,
                context.inventory()::insertSupplyFromStorage);
        HiredStorageNavigationGoal.clearStorageTarget(context);
        if (moved > 0) {
            HiredWorkerBrain.clearFailure(context);
            HiredWorkerBrain.setLastTargetScanResult(context, "ranged_ammo_restocked");
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, context.workCenter());
            return WorkResult.progressed(STATUS_GATHERED);
        }

        HiredWorkerBrain.setFailure(context, FAILURE_INVENTORY_FULL, level.getGameTime() + 100L);
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, storage);
        return WorkResult.idle(STATUS_INVENTORY_FULL);
    }

    private static WorkResult recoverNearbyArrow(ServerLevel level, Villager villager, HiredWorkContext context, double speed) {
        AbstractArrow arrow = findNearestRecoverableArrow(level, villager, context);
        if (arrow == null) {
            return null;
        }

        BlockPos arrowPos = arrow.blockPosition();
        if (!canRecoverFromCurrentPosition(villager, context, arrow)) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.MOVING_TO_TARGET, arrowPos);
            if (!moveToArrow(level, villager, context, arrow, speed)) {
                if (HiredPathMemory.recordFailure(level, villager, arrowPos)) {
                    HiredWorkerBrain.setFailure(context, FAILURE_STORAGE_PATH, level.getGameTime() + 100L);
                    HiredWorkerBrain.setState(context, HiredWorkerTaskState.FAILED_COOLDOWN, arrowPos);
                    return WorkResult.idle(STATUS_STORAGE_UNREACHABLE);
                }
                return WorkResult.progressed(STATUS_COLLECTING);
            }
            HiredWorkerBrain.clearFailure(context);
            return WorkResult.progressed(STATUS_COLLECTING);
        }

        ItemStack pickupStack = arrow.getPickupItemStackOrigin();
        if (pickupStack.isEmpty() || !isAmmo(pickupStack)) {
            return null;
        }
        ItemStack recovered = pickupStack.copyWithCount(1);
        ItemStack remainder = context.inventory().insertSupply(recovered);
        if (!remainder.isEmpty()) {
            HiredWorkerBrain.setFailure(context, FAILURE_INVENTORY_FULL, level.getGameTime() + 100L);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, arrowPos);
            return WorkResult.idle(STATUS_INVENTORY_FULL);
        }

        villager.getNavigation().stop();
        villager.getLookControl().setLookAt(Vec3.atCenterOf(arrowPos));
        villager.take(arrow, 1);
        arrow.discard();
        HiredPathMemory.clearFailure(villager, arrowPos);
        HiredPathMemory.clearNavigationProgress(villager);
        HiredWorkerBrain.clearFailure(context);
        HiredWorkerBrain.setLastTargetScanResult(context, "ranged_arrow_recovered");
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, context.workCenter());
        return WorkResult.progressed(STATUS_GATHERED);
    }

    private static AbstractArrow findNearestRecoverableArrow(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context) {
        return HiredEntitySearch.nearest(
                level,
                AbstractArrow.class,
                context.assignment().entitySearchBounds(),
                arrow -> isRecoverableArrow(level, context, villager, arrow),
                villager::distanceToSqr);
    }

    private static boolean isRecoverableArrow(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            AbstractArrow arrow) {
        BlockPos pos = arrow.blockPosition();
        return arrow.isAlive()
                && (((AbstractArrowAccessor) arrow).villagerretaliation$isInGround() || arrow.isNoPhysics())
                && arrow.shakeTime <= 0
                && isAmmo(arrow.getPickupItemStackOrigin())
                && context.isInsideWorkAreaOrRoute(pos)
                && context.isLoaded(level, pos)
                && !HiredPathMemory.isAvoided(level, villager, pos);
    }

    private static boolean canRecoverFromCurrentPosition(
            Villager villager,
            HiredWorkContext context,
            AbstractArrow arrow) {
        return context.isInsideWorkAreaOrRoute(villager.blockPosition())
                && context.isInsideWorkAreaOrRoute(arrow.blockPosition())
                && isCloseEnoughToRecover(villager, arrow);
    }

    private static boolean moveToArrow(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            AbstractArrow arrow,
            double speed) {
        if (!context.isInsideWorkAreaOrRoute(villager.blockPosition())
                || !context.isInsideWorkAreaOrRoute(arrow.blockPosition())) {
            villager.getNavigation().stop();
            return false;
        }
        if (isCloseEnoughToRecover(villager, arrow)) {
            villager.getNavigation().stop();
            villager.getLookControl().setLookAt(arrow.position());
            return true;
        }

        BlockPos targetPos = arrow.blockPosition();
        Path currentPath = villager.getNavigation().getPath();
        if (currentPath != null && !HiredMoveToBlockFaceJob.pathStaysInsideFilter(currentPath, context::isInsideWorkAreaOrRoute)) {
            villager.getNavigation().stop();
            return false;
        }
        Path path = HiredPathMemory.createPath(level, villager, targetPos, ARROW_PATH_CLOSE_ENOUGH);
        if (path != null && path.canReach() && HiredMoveToBlockFaceJob.pathStaysInsideFilter(path, context::isInsideWorkAreaOrRoute)) {
            villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(targetPos));
            boolean moved = VillagerTaskNavigationUtil.moveToHiredPath(villager, path, targetPos, speed, ARROW_PATH_CLOSE_ENOUGH);
            if (moved) {
                HiredPathMemory.rememberNavigationProgress(level, villager, targetPos, villager.distanceToSqr(targetPos.getCenter()));
            }
            return moved;
        }
        HiredPathMemory.clearNavigationProgress(villager);
        return false;
    }

    private static boolean isCloseEnoughToRecover(Villager villager, AbstractArrow arrow) {
        double dx = villager.getX() - arrow.getX();
        double dz = villager.getZ() - arrow.getZ();
        return dx * dx + dz * dz <= ARROW_PICKUP_HORIZONTAL_REACH_SQR
                && Math.abs(villager.getY() - arrow.getY()) <= ARROW_PICKUP_VERTICAL_REACH;
    }

    private static ItemStack ammoCheckedWeapon(HiredWorkContext context, Villager villager) {
        ItemStack jobWeapon = context.inventory().getItem(HiredJobInventory.MAINHAND_SLOT);
        if (isWeaponRequiringAmmo(jobWeapon) || villager == null) {
            return jobWeapon;
        }
        ItemStack carriedWeapon = VillagerRetaliationVillagerWeapons.getPrimaryWeapon(villager);
        return isWeaponRequiringAmmo(carriedWeapon) ? carriedWeapon : jobWeapon;
    }

    private static WorkResult missingAmmo(HiredWorkContext context) {
        HiredWorkerBrain.setFailure(context, FAILURE_MISSING, 0L);
        HiredWorkerBrain.setLastTargetScanResult(context, "ranged_ammo_missing");
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.PAUSED_MISSING_TOOL);
        return WorkResult.idle(STATUS_MISSING);
    }
}
