package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.combat.VillagerRetaliationRetaliationUtil;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;

public final class HuntingWorker extends AbstractBlockWorker {
    private static final String NEXT_PATROL_GAME_TIME_TAG = "HuntingNextPatrolGameTime";
    private static final String NEXT_TARGET_SCAN_GAME_TIME_TAG = "HuntingNextTargetScanGameTime";
    private static final String ACTIVE_TARGET_ID_TAG = "HuntingActiveTarget";
    private static final int MIN_PATROL_DELAY_TICKS = 50;
    private static final int RANDOM_PATROL_DELAY_TICKS = 80;
    private static final int TARGET_SCAN_INTERVAL_TICKS = 20;
    private static final double PATROL_SPEED = 0.55D;
    private static final int PATROL_TARGET_ATTEMPTS = 12;
    private static final double TARGET_SCAN_RADIUS_PADDING = 4.0D;
    private static final double HUNTING_LOOT_PICKUP_REACH_SQR = 2.25D;
    private static final String HUNTING_WEAPON_LABEL = "bow, crossbow, axe, or sword";
    private static final HiredItemPickup.Messages HUNTING_LOOT_PICKUP_MESSAGES = new HiredItemPickup.Messages(
            "interaction.work.hunting.output_full_depositing",
            "interaction.work.hunting.output_full_blocked",
            "hunting_loot_unreachable",
            "interaction.work.hunting.loot_unreachable",
            "interaction.work.hunting.loot_repositioning",
            "interaction.work.hunting.moving_to_loot",
            "interaction.work.hunting.collected_loot",
            false,
            true);

    @Override
    public HiredVillagerRole role() {
        return HiredVillagerRole.HUNTING;
    }

    @Override
    public void maintain(ServerLevel level, Villager villager, HiredWorkContext context) {
        if (!context.hasWorkArea() && !context.hasRoute()) {
            setTaskState(context, HiredWorkerTaskState.NO_WORK_AREA);
            return;
        }
        HiredWorkerBrain.Snapshot brain = HiredWorkerBrain.snapshot(context.state(), level.getGameTime());
        if (brain.taskState().keepsStorageTarget() && brain.storageTargetPos() != null) {
            return;
        }
        HiredHuntingTargets.Selection targets = HiredHuntingTargets.fromState(context.state());
        clearStaleActiveTargetIfNeeded(level, villager, context, targets);
        if (villager.getTarget() != null || villager.getLastHurtByMob() != null) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.WORKING, villager.blockPosition());
            return;
        }

        BlockPos lootPos = nearestHuntingLootPosition(level, villager, context);
        if (lootPos != null) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.COLLECTING_OUTPUT, lootPos);
            return;
        }

        if (!hasHuntingWeaponReadyForTarget(context)) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, context.workCenter());
            return;
        }

        if (tryAcquireTarget(level, villager, context, targets)) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.WORKING, villager.blockPosition());
            return;
        }

        if (context.hasRoute() && HiredRouteNavigator.maintainRoute(level, villager, context, PATROL_SPEED)) {
            return;
        }

        maintainPatrol(level, villager, context);
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        context.setProgressTicks(0);
        if (!context.hasWorkArea() && !context.hasRoute()) {
            return waitForWorkAreaAssignment(level, villager, context);
        }

        HiredHuntingTargets.Selection targets = HiredHuntingTargets.fromState(context.state());
        WorkResult weaponResult = ensureHuntingWeapon(level, villager, context);
        if (weaponResult != null) {
            return weaponResult;
        }
        if (!context.hasRoute() && !context.isInsideWorkArea(villager.blockPosition())) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, context.workCenter());
            return WorkResult.progressed("interaction.work.status.returning_bounds");
        }

        clearStaleActiveTargetIfNeeded(level, villager, context, targets);
        if (hasActiveTarget(villager) && HiredRangedAmmo.isRangedAttackBlockedByAmmo(villager)) {
            clearActiveHuntingTarget(villager, context);
        }
        if (hasActiveTarget(villager)) {
            HiredWorkerBrain.setLastTargetScanResult(context, "engaged_target");
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.WORKING, villager.getTarget().blockPosition());
            return WorkResult.idle(activeStatusKey(targets), activeStatusReplacements(villager, targets));
        }

        WorkResult lootResult = collectHuntingLoot(level, villager, context);
        if (lootResult != null) {
            return lootResult;
        }

        WorkResult ammoResult = HiredRangedAmmo.ensureReady(level, villager, context, PATROL_SPEED);
        if (ammoResult != null) {
            return ammoResult;
        }

        if (tryAcquireTarget(level, villager, context, targets)) {
            HiredWorkerBrain.setLastTargetScanResult(context, "found_target");
            return WorkResult.progressed(activeStatusKey(targets), activeStatusReplacements(villager, targets));
        }

        if (context.hasRoute() && HiredRouteNavigator.maintainRoute(level, villager, context, PATROL_SPEED)) {
            HiredWorkerBrain.setLastTargetScanResult(context, "route_patrol");
            return WorkResult.idle(passiveStatusKey(targets), passiveStatusReplacements(targets));
        }

        HiredWorkerBrain.setLastTargetScanResult(context, "no_targets");
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, context.workCenter());
        return WorkResult.idle(passiveStatusKey(targets), passiveStatusReplacements(targets));
    }

    @Override
    public void stop(ServerLevel level, Villager villager, HiredWorkContext context) {
        super.stop(level, villager, context);
        context.state().remove(NEXT_PATROL_GAME_TIME_TAG);
        context.state().remove(NEXT_TARGET_SCAN_GAME_TIME_TAG);
        if (villager.getTarget() == null && villager.getLastHurtByMob() == null) {
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            villager.getBrain().setDefaultActivity(Activity.IDLE);
            villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        }
    }

    private WorkResult ensureHuntingWeapon(ServerLevel level, Villager villager, HiredWorkContext context) {
        ToolStorageResult toolResult = equipBestToolOrCollectFromStorage(
                level,
                villager,
                context,
                HuntingWorker::isHuntingWeapon,
                HuntingWorker::huntingWeaponScore,
                PATROL_SPEED);
        if (toolResult.status() == ToolStorageStatus.READY || toolResult.status() == ToolStorageStatus.COLLECTED) {
            return null;
        }
        if (toolResult.status() == ToolStorageStatus.MOVING) {
            return WorkResult.progressed("interaction.work.status.collecting_tool");
        }
        if (toolResult.status() == ToolStorageStatus.UNREACHABLE) {
            HiredWorkerBrain.setFailure(context, "tool_storage_unreachable_hunting_weapon", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, toolResult.storagePos());
            return WorkResult.idle("interaction.work.status.tool_storage_unreachable", Map.of("tool", HUNTING_WEAPON_LABEL));
        }
        if (toolResult.status() == ToolStorageStatus.INVENTORY_FULL) {
            HiredWorkerBrain.setFailure(context, "tool_inventory_full_hunting_weapon", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, toolResult.storagePos());
            return WorkResult.idle("interaction.work.status.tool_inventory_full", Map.of("tool", HUNTING_WEAPON_LABEL));
        }
        HiredWorkerBrain.setFailure(context, "missing_hunting_weapon", 0L);
        HiredWorkerBrain.setLastTargetScanResult(context, "hunting_weapon_missing");
        setTaskState(context, HiredWorkerTaskState.PAUSED_MISSING_TOOL);
        return WorkResult.idle("interaction.work.hunting.missing_weapon");
    }

    private WorkResult collectHuntingLoot(ServerLevel level, Villager villager, HiredWorkContext context) {
        return HiredItemPickup.collectNearestOutputItem(
                level,
                villager,
                context,
                this,
                HuntingWorker::isHuntingLoot,
                HUNTING_LOOT_PICKUP_REACH_SQR,
                PATROL_SPEED,
                HUNTING_LOOT_PICKUP_MESSAGES);
    }

    private static BlockPos nearestHuntingLootPosition(ServerLevel level, Villager villager, HiredWorkContext context) {
        return HiredItemPickup.nearestOutputItemPosition(level, villager, context, HuntingWorker::isHuntingLoot);
    }

    private static boolean hasActiveTarget(Villager villager) {
        return villager.getTarget() != null && villager.getTarget().isAlive();
    }

    private static boolean tryAcquireTarget(ServerLevel level, Villager villager, HiredWorkContext context, HiredHuntingTargets.Selection targets) {
        if (villager.getTarget() != null || villager.getLastHurtByMob() != null) {
            return false;
        }

        long gameTime = level.getGameTime();
        CompoundTag state = context.state();
        if (gameTime < state.getLong(NEXT_TARGET_SCAN_GAME_TIME_TAG)) {
            return false;
        }
        state.putLong(NEXT_TARGET_SCAN_GAME_TIME_TAG, gameTime + TARGET_SCAN_INTERVAL_TICKS);
        return findNearestTarget(level, villager, context, targets)
                .filter(target -> VillagerRetaliationHandler.engageCustomTarget(villager, target, false))
                .isPresent();
    }

    private static Optional<LivingEntity> findNearestTarget(ServerLevel level, Villager villager, HiredWorkContext context, HiredHuntingTargets.Selection targets) {
        double horizontalRadius = Math.max(6.0D, context.horizontalSearchRadius() + TARGET_SCAN_RADIUS_PADDING);
        double verticalRadius = Math.max(4.0D, context.verticalRadius() + 2.0D);
        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        villager.getBoundingBox().inflate(horizontalRadius, verticalRadius, horizontalRadius),
                        target -> isEligibleTarget(villager, target, context, targets))
                .stream()
                .min((first, second) -> Double.compare(villager.distanceToSqr(first), villager.distanceToSqr(second)));
    }

    private static boolean isEligibleTarget(Villager villager, LivingEntity target, HiredWorkContext context, HiredHuntingTargets.Selection targets) {
        return isEligibleTarget(villager, target, context, targets, true);
    }

    private static boolean isEligibleTarget(
            Villager villager,
            LivingEntity target,
            HiredWorkContext context,
            HiredHuntingTargets.Selection targets,
            boolean requireLineOfSight) {
        if (target == villager
                || targets.none()
                || !target.isAlive()
                || !villager.canAttack(target)
                || requireLineOfSight && !VillagerRetaliationRetaliationUtil.hasClearLineOfSight(villager, target)
                || !context.isInsideWorkAreaOrRoute(target.blockPosition())
                || !VillagerRetaliationRetaliationUtil.isWithinRetaliationPursuitRange(villager, target)
                || target.isAlliedTo(villager)
                || target instanceof AbstractVillager
                || target instanceof IronGolem
                || target instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null
                || target instanceof TamableAnimal tamable && tamable.isTame()
                || VillagerRetaliationVillagerCombatUtil.shouldIgnoreAttacker(target)) {
            return false;
        }

        boolean animal = target instanceof Animal;
        boolean hostile = VillagerRetaliationVillagerCombatUtil.isNaturalHostileTarget(villager, target);
        boolean player = target instanceof ServerPlayer serverPlayer && isEligiblePlayerTarget(villager, serverPlayer);
        return targets.animals() && animal
                || targets.hostiles() && hostile
                || targets.players() && player;
    }

    private static void clearStaleActiveTargetIfNeeded(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredHuntingTargets.Selection targets) {
        LivingEntity target = villager.getTarget();
        if (target == null) {
            clearActiveTargetTracking(context);
            return;
        }

        if (!isEligibleTarget(villager, target, context, targets, false)) {
            clearActiveHuntingTarget(villager, context);
            return;
        }

        if (!VillagerRetaliationRetaliationUtil.hasClearLineOfSight(villager, target)) {
            clearActiveHuntingTarget(villager, context);
            return;
        }

        context.state().putUUID(ACTIVE_TARGET_ID_TAG, target.getUUID());
    }

    private static void clearActiveHuntingTarget(Villager villager, HiredWorkContext context) {
        clearActiveTargetTracking(context);
        context.state().remove(NEXT_TARGET_SCAN_GAME_TIME_TAG);
        VillagerRetaliationHandler.clearCustomTarget(villager);
    }

    private static void clearActiveTargetTracking(HiredWorkContext context) {
        context.state().remove(ACTIVE_TARGET_ID_TAG);
    }

    private static boolean isEligiblePlayerTarget(Villager villager, ServerPlayer target) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return false;
        }
        UUID hirer = HiredVillagerContractService.getHirer(level, villager).orElse(null);
        return hirer == null || !hirer.equals(target.getUUID());
    }

    private static void maintainPatrol(ServerLevel level, Villager villager, HiredWorkContext context) {
        if (!villager.getNavigation().isDone() && villager.getNavigation().getTargetPos() != null) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.MOVING_TO_TARGET, villager.getNavigation().getTargetPos());
            return;
        }

        long gameTime = level.getGameTime();
        if (gameTime < context.state().getLong(NEXT_PATROL_GAME_TIME_TAG)) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, context.workCenter());
            return;
        }

        BlockPos patrolTarget = selectPatrolTarget(level, villager, context);
        context.state().putLong(
                NEXT_PATROL_GAME_TIME_TAG,
                gameTime + MIN_PATROL_DELAY_TICKS + villager.getRandom().nextInt(RANDOM_PATROL_DELAY_TICKS + 1));
        if (patrolTarget == null) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, context.workCenter());
            return;
        }

        Path path = HiredPathMemory.createPath(level, villager, patrolTarget, 0);
        if (path != null
                && path.canReach()
                && VillagerTaskNavigationUtil.moveToHiredPath(villager, path, patrolTarget, PATROL_SPEED, 0)) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.MOVING_TO_TARGET, patrolTarget);
        } else {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, context.workCenter());
        }
    }

    private static BlockPos selectPatrolTarget(ServerLevel level, Villager villager, HiredWorkContext context) {
        BlockPos fallback = context.workCenter();
        for (int attempt = 0; attempt < PATROL_TARGET_ATTEMPTS; attempt++) {
            int x = randomBetween(villager, context.workMin().getX(), context.workMax().getX());
            int z = randomBetween(villager, context.workMin().getZ(), context.workMax().getZ());
            BlockPos candidate = patrolStandPosition(level, villager, context, x, z);
            if (candidate == null) {
                continue;
            }
            Path path = HiredPathMemory.createPath(level, villager, candidate, 0);
            if (path != null && path.canReach()) {
                return candidate;
            }
        }
        return fallback;
    }

    private static BlockPos patrolStandPosition(ServerLevel level, Villager villager, HiredWorkContext context, int x, int z) {
        BlockPos surface = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        if (isValidPatrolStandPosition(level, context, surface)) {
            return surface;
        }

        int baseY = Math.max(context.workMin().getY(), Math.min(context.workMax().getY(), villager.blockPosition().getY()));
        for (int offset = 0; offset <= 4; offset++) {
            BlockPos above = new BlockPos(x, baseY + offset, z);
            if (isValidPatrolStandPosition(level, context, above)) {
                return above;
            }
            if (offset > 0) {
                BlockPos below = new BlockPos(x, baseY - offset, z);
                if (isValidPatrolStandPosition(level, context, below)) {
                    return below;
                }
            }
        }
        return null;
    }

    private static boolean isValidPatrolStandPosition(ServerLevel level, HiredWorkContext context, BlockPos pos) {
        return context.isInsideWorkArea(pos)
                && context.isLoaded(level, pos)
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }

    private static int randomBetween(Villager villager, int min, int max) {
        if (max <= min) {
            return min;
        }
        return min + villager.getRandom().nextInt(max - min + 1);
    }

    private static boolean hasCarriedHuntingWeapon(HiredWorkContext context) {
        return isHuntingWeapon(context.inventory().getItem(HiredJobInventory.MAINHAND_SLOT));
    }

    private static boolean hasHuntingWeaponReadyForTarget(HiredWorkContext context) {
        return hasCarriedHuntingWeapon(context) && HiredRangedAmmo.hasAmmoForEquippedWeapon(context);
    }

    private static boolean isHuntingWeapon(ItemStack stack) {
        return !stack.isEmpty()
                && (VillagerRetaliationVillagerWeapons.isBowWeapon(stack)
                || VillagerRetaliationVillagerWeapons.isCrossbowWeapon(stack)
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof SwordItem);
    }

    private static boolean isHuntingLoot(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(ItemTags.MEAT)
                || stack.is(ItemTags.WOOL)
                || stack.is(Items.LEATHER)
                || stack.is(Items.RABBIT_HIDE)
                || stack.is(Items.RABBIT_FOOT)
                || stack.is(Items.FEATHER)
                || stack.is(Items.SEAGRASS)
                || stack.is(Items.TURTLE_SCUTE)
                || stack.is(Items.ARMADILLO_SCUTE)
                || stack.is(Items.BONE)
                || HiredRangedAmmo.isAmmo(stack)
                || stack.is(Items.ROTTEN_FLESH)
                || stack.is(Items.STRING)
                || stack.is(Items.SPIDER_EYE)
                || stack.is(Items.GUNPOWDER)
                || stack.is(Items.ENDER_PEARL)
                || stack.is(Items.SLIME_BALL)
                || stack.is(Items.MAGMA_CREAM)
                || stack.is(Items.BLAZE_ROD)
                || stack.is(Items.GHAST_TEAR)
                || stack.is(Items.PHANTOM_MEMBRANE)
                || stack.is(Items.NAUTILUS_SHELL)
                || stack.is(Items.INK_SAC)
                || stack.is(Items.GLOW_INK_SAC)
                || stack.is(Items.PRISMARINE_SHARD)
                || stack.is(Items.PRISMARINE_CRYSTALS)
                || stack.is(Items.COD)
                || stack.is(Items.SALMON)
                || stack.is(Items.TROPICAL_FISH)
                || stack.is(Items.PUFFERFISH));
    }

    private static double huntingWeaponScore(ItemStack stack) {
        Item item = stack.getItem();
        double score = HiredRangedAmmo.isWeaponRequiringAmmo(stack)
                ? (VillagerRetaliationVillagerWeapons.isCrossbowWeapon(stack) ? 3.25D : 3.0D)
                : item instanceof SwordItem
                        ? 2.0D
                        : item instanceof AxeItem ? 1.0D : 0.0D;
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (path.startsWith("netherite_")) {
            score += 4.0D;
        } else if (path.startsWith("diamond_")) {
            score += 3.0D;
        } else if (path.startsWith("iron_")) {
            score += 2.0D;
        } else if (path.startsWith("stone_")) {
            score += 1.0D;
        }
        if (stack.isEnchanted()) {
            score += 0.25D;
        }
        if (stack.isDamageableItem()) {
            score += Math.max(0.0D, stack.getMaxDamage() - stack.getDamageValue()) / 10000.0D;
        }
        return score;
    }

    private static String passiveStatusKey(HiredHuntingTargets.Selection targets) {
        if (targets.none()) {
            return "interaction.work.hunting.passive.none";
        }
        if (targets.animals() && !targets.hostiles() && !targets.players()) {
            return "interaction.work.hunting.passive.animals";
        }
        if (!targets.animals() && targets.hostiles() && !targets.players()) {
            return "interaction.work.hunting.passive.hostiles";
        }
        if (!targets.animals() && !targets.hostiles() && targets.players()) {
            return "interaction.work.hunting.passive.players";
        }
        if (targets.huntsAllNonPlayers() && !targets.players()) {
            return "interaction.work.hunting.passive.all";
        }
        return "interaction.work.hunting.passive.selected";
    }

    private static String activeStatusKey(HiredHuntingTargets.Selection targets) {
        if (targets.animals() && !targets.hostiles() && !targets.players()) {
            return "interaction.work.hunting.active.animals";
        }
        if (!targets.animals() && targets.hostiles() && !targets.players()) {
            return "interaction.work.hunting.active.hostiles";
        }
        if (!targets.animals() && !targets.hostiles() && targets.players()) {
            return "interaction.work.hunting.active.players";
        }
        if (targets.huntsAllNonPlayers() && !targets.players()) {
            return "interaction.work.hunting.active.all";
        }
        return "interaction.work.hunting.active.selected";
    }

    private static Map<String, String> passiveStatusReplacements(HiredHuntingTargets.Selection targets) {
        return Map.of("targets", targets.label());
    }

    private static Map<String, String> activeStatusReplacements(Villager villager, HiredHuntingTargets.Selection targets) {
        LivingEntity target = villager.getTarget();
        return Map.of(
                "target", target == null ? "a target" : target.getName().getString(),
                "targets", targets.label());
    }
}
