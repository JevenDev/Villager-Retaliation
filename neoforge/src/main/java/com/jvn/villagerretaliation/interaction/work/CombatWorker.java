package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.interaction.HiredCombatMode;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.pathfinder.Path;

public final class CombatWorker implements HiredRoleWorker {
    private static final String NEXT_PATROL_GAME_TIME_TAG = "CombatNextPatrolGameTime";
    private static final String NEXT_TARGET_SCAN_GAME_TIME_TAG = "CombatNextTargetScanGameTime";
    private static final int MIN_PATROL_DELAY_TICKS = 60;
    private static final int RANDOM_PATROL_DELAY_TICKS = 80;
    private static final int TARGET_SCAN_INTERVAL_TICKS = 20;
    private static final double PATROL_SPEED = 0.55D;
    private static final int PATROL_TARGET_ATTEMPTS = 10;
    private static final double TARGET_SCAN_RADIUS_PADDING = 4.0D;

    @Override
    public HiredVillagerRole role() {
        return HiredVillagerRole.COMBAT;
    }

    @Override
    public void maintain(ServerLevel level, Villager villager, HiredWorkContext context) {
        HiredWorkerBrain.Snapshot brain = HiredWorkerBrain.snapshot(context.state(), level.getGameTime());
        if (brain.taskState().keepsStorageTarget() && brain.storageTargetPos() != null) {
            return;
        }
        HiredCombatMode mode = HiredCombatMode.fromState(context.state());
        if (villager.getTarget() != null || villager.getLastHurtByMob() != null) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.WORKING, villager.blockPosition());
            return;
        }

        if (!HiredRangedAmmo.hasAmmoForEquippedWeapon(context, villager)) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, context.workCenter());
            return;
        }

        if (tryAcquireTarget(level, villager, context, mode)) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.WORKING, villager.blockPosition());
            return;
        }

        if (context.hasRoute() && HiredRouteNavigator.maintainRoute(level, villager, context, PATROL_SPEED)) {
            return;
        }

        if (mode.roams()) {
            maintainPatrol(level, villager, context);
        } else {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, context.workCenter());
            if (!villager.getNavigation().isDone()
                    && villager.getNavigation().getTargetPos() != null
                    && !context.isInsideWorkArea(villager.getNavigation().getTargetPos())) {
                VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            }
        }
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        context.setProgressTicks(0);
        HiredCombatMode mode = HiredCombatMode.fromState(context.state());
        if (hasActiveTarget(villager) && HiredRangedAmmo.isRangedAttackBlockedByAmmo(villager)) {
            VillagerRetaliationHandler.clearCustomTarget(villager);
        }
        if (!hasActiveTarget(villager)) {
            WorkResult ammoResult = HiredRangedAmmo.ensureReady(level, villager, context, PATROL_SPEED);
            if (ammoResult != null) {
                return ammoResult;
            }
        }
        if (hasActiveTarget(villager)) {
            HiredWorkerBrain.setLastTargetScanResult(context, "engaged_target");
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.WORKING, villager.getTarget().blockPosition());
            return WorkResult.idle(activeStatusKey(mode), activeStatusReplacements(villager));
        }

        if (tryAcquireTarget(level, villager, context, mode)) {
            HiredWorkerBrain.setLastTargetScanResult(context, "found_target");
            return WorkResult.progressed(activeStatusKey(mode), activeStatusReplacements(villager));
        }

        if (context.hasRoute() && HiredRouteNavigator.maintainRoute(level, villager, context, PATROL_SPEED)) {
            HiredWorkerBrain.setLastTargetScanResult(context, "route_patrol");
            return WorkResult.idle(passiveStatusKey(mode));
        }

        HiredWorkerBrain.setLastTargetScanResult(context, mode.roams() ? "patrolling" : "guarding");
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, context.workCenter());
        return WorkResult.idle(passiveStatusKey(mode));
    }

    @Override
    public void stop(ServerLevel level, Villager villager, HiredWorkContext context) {
        HiredRoleWorker.super.stop(level, villager, context);
        context.state().remove(NEXT_PATROL_GAME_TIME_TAG);
        context.state().remove(NEXT_TARGET_SCAN_GAME_TIME_TAG);
        if (villager.getTarget() == null && villager.getLastHurtByMob() == null) {
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            villager.getBrain().setDefaultActivity(Activity.IDLE);
            villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        }
    }

    private static boolean tryAcquireTarget(ServerLevel level, Villager villager, HiredWorkContext context, HiredCombatMode mode) {
        if (mode == HiredCombatMode.GUARD || villager.getTarget() != null || villager.getLastHurtByMob() != null) {
            return false;
        }

        long gameTime = level.getGameTime();
        CompoundTag state = context.state();
        if (gameTime < state.getLong(NEXT_TARGET_SCAN_GAME_TIME_TAG)) {
            return false;
        }
        state.putLong(NEXT_TARGET_SCAN_GAME_TIME_TAG, gameTime + TARGET_SCAN_INTERVAL_TICKS);
        return findNearestTarget(level, villager, context, mode)
                .filter(target -> VillagerRetaliationHandler.engageCustomTarget(villager, target, false))
                .isPresent();
    }

    private static boolean hasActiveTarget(Villager villager) {
        return villager.getTarget() != null && villager.getTarget().isAlive();
    }

    private static Optional<LivingEntity> findNearestTarget(ServerLevel level, Villager villager, HiredWorkContext context, HiredCombatMode mode) {
        double horizontalRadius = Math.max(6.0D, context.horizontalSearchRadius() + TARGET_SCAN_RADIUS_PADDING);
        double verticalRadius = Math.max(4.0D, context.verticalRadius() + 2.0D);
        LivingEntity target = HiredEntitySearch.nearest(
                level,
                LivingEntity.class,
                villager.getBoundingBox().inflate(horizontalRadius, verticalRadius, horizontalRadius),
                candidate -> isEligibleTarget(villager, candidate, context, mode),
                villager::distanceToSqr);
        return Optional.ofNullable(target);
    }

    private static boolean isEligibleTarget(Villager villager, LivingEntity target, HiredWorkContext context, HiredCombatMode mode) {
        if (target == villager
                || !target.isAlive()
                || !villager.canAttack(target)
                || !villager.hasLineOfSight(target)
                || !context.isInsideWorkAreaOrRoute(target.blockPosition())
                || target.isAlliedTo(villager)
                || target instanceof AbstractVillager
                || target instanceof IronGolem
                || target instanceof Player
                || target instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null
                || target instanceof TamableAnimal tamable && tamable.isTame()) {
            return false;
        }

        return switch (mode) {
            case ROAMING -> VillagerRetaliationVillagerCombatUtil.isNaturalHostileTarget(villager, target);
            case ATTACK_ALL -> target instanceof Mob || target instanceof Animal;
            case HUNTING -> target instanceof Animal;
            default -> false;
        };
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
            int y = randomBetween(villager, context.workMin().getY(), context.workMax().getY());
            int z = randomBetween(villager, context.workMin().getZ(), context.workMax().getZ());
            BlockPos candidate = new BlockPos(x, y, z);
            if (!context.isInsideWorkArea(candidate)) {
                continue;
            }
            Path path = HiredPathMemory.createPath(level, villager, candidate, 0);
            if (path != null && path.canReach()) {
                return candidate;
            }
        }
        return fallback;
    }

    private static int randomBetween(Villager villager, int min, int max) {
        if (max <= min) {
            return min;
        }
        return min + villager.getRandom().nextInt(max - min + 1);
    }

    private static String passiveStatusKey(HiredCombatMode mode) {
        return switch (mode) {
            case GUARD -> "interaction.work.combat.passive.guard";
            case ROAMING -> "interaction.work.combat.passive.roaming";
            case ATTACK_ALL -> "interaction.work.combat.passive.attack_all";
            case HUNTING -> "interaction.work.combat.passive.hunting";
        };
    }

    private static String activeStatusKey(HiredCombatMode mode) {
        return switch (mode) {
            case GUARD -> "interaction.work.combat.active.guard";
            case ROAMING -> "interaction.work.combat.active.roaming";
            case ATTACK_ALL -> "interaction.work.combat.active.attack_all";
            case HUNTING -> "interaction.work.combat.active.hunting";
        };
    }

    private static Map<String, String> activeStatusReplacements(Villager villager) {
        LivingEntity target = villager.getTarget();
        return Map.of("target", target == null ? "a target" : target.getName().getString());
    }
}
