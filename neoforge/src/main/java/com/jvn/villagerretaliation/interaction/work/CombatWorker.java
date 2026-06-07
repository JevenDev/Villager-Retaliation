package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.interaction.HiredCombatMode;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import java.util.Optional;
import net.minecraft.core.BlockPos;
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
    private static final int MIN_PATROL_DELAY_TICKS = 60;
    private static final int RANDOM_PATROL_DELAY_TICKS = 80;
    private static final double PATROL_SPEED = 0.78D;
    private static final int PATROL_TARGET_ATTEMPTS = 10;
    private static final double TARGET_SCAN_RADIUS_PADDING = 4.0D;

    @Override
    public HiredVillagerRole role() {
        return HiredVillagerRole.COMBAT;
    }

    @Override
    public void maintain(ServerLevel level, Villager villager, HiredWorkContext context) {
        HiredCombatMode mode = HiredCombatMode.fromState(context.state());
        if (villager.getTarget() != null || villager.getLastHurtByMob() != null) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.WORKING, villager.blockPosition());
            return;
        }

        if (tryAcquireTarget(level, villager, context, mode)) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.WORKING, villager.blockPosition());
            return;
        }

        if (mode.roams()) {
            maintainPatrol(level, villager, context);
        } else {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, context.workCenter());
            if (!villager.getNavigation().isDone()
                    && villager.getNavigation().getTargetPos() != null
                    && !context.isInsideWorkArea(villager.getNavigation().getTargetPos())) {
                villager.getNavigation().stop();
            }
        }
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        context.setProgressTicks(0);
        HiredCombatMode mode = HiredCombatMode.fromState(context.state());
        if (villager.getTarget() != null && villager.getTarget().isAlive()) {
            HiredWorkerBrain.setLastTargetScanResult(context, "engaged_target");
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.WORKING, villager.getTarget().blockPosition());
            return WorkResult.idle(activeStatus(villager, mode));
        }

        if (tryAcquireTarget(level, villager, context, mode)) {
            HiredWorkerBrain.setLastTargetScanResult(context, "found_target");
            return WorkResult.progressed(activeStatus(villager, mode));
        }

        HiredWorkerBrain.setLastTargetScanResult(context, mode.roams() ? "patrolling" : "guarding");
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, context.workCenter());
        return WorkResult.idle(passiveStatus(mode));
    }

    @Override
    public void stop(ServerLevel level, Villager villager, HiredWorkContext context) {
        HiredRoleWorker.super.stop(level, villager, context);
        context.state().remove(NEXT_PATROL_GAME_TIME_TAG);
        if (villager.getTarget() == null && villager.getLastHurtByMob() == null) {
            villager.getNavigation().stop();
            villager.getBrain().setDefaultActivity(Activity.IDLE);
            villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        }
    }

    private static boolean tryAcquireTarget(ServerLevel level, Villager villager, HiredWorkContext context, HiredCombatMode mode) {
        if (mode == HiredCombatMode.GUARD || villager.getTarget() != null || villager.getLastHurtByMob() != null) {
            return false;
        }

        return findNearestTarget(level, villager, context, mode)
                .filter(target -> VillagerRetaliationHandler.engageCustomTarget(villager, target, false))
                .isPresent();
    }

    private static Optional<LivingEntity> findNearestTarget(ServerLevel level, Villager villager, HiredWorkContext context, HiredCombatMode mode) {
        double horizontalRadius = Math.max(6.0D, context.horizontalSearchRadius() + TARGET_SCAN_RADIUS_PADDING);
        double verticalRadius = Math.max(4.0D, context.verticalRadius() + 2.0D);
        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        villager.getBoundingBox().inflate(horizontalRadius, verticalRadius, horizontalRadius),
                        target -> isEligibleTarget(villager, target, context, mode))
                .stream()
                .min((first, second) -> Double.compare(villager.distanceToSqr(first), villager.distanceToSqr(second)));
    }

    private static boolean isEligibleTarget(Villager villager, LivingEntity target, HiredWorkContext context, HiredCombatMode mode) {
        if (target == villager
                || !target.isAlive()
                || !villager.canAttack(target)
                || !villager.hasLineOfSight(target)
                || !context.isInsideWorkArea(target.blockPosition())
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

        BlockPos patrolTarget = selectPatrolTarget(villager, context);
        context.state().putLong(
                NEXT_PATROL_GAME_TIME_TAG,
                gameTime + MIN_PATROL_DELAY_TICKS + villager.getRandom().nextInt(RANDOM_PATROL_DELAY_TICKS + 1));
        if (patrolTarget == null) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, context.workCenter());
            return;
        }

        Path path = villager.getNavigation().createPath(patrolTarget, 0);
        if (path != null && path.canReach() && villager.getNavigation().moveTo(path, PATROL_SPEED)) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.MOVING_TO_TARGET, patrolTarget);
        } else {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, context.workCenter());
        }
    }

    private static BlockPos selectPatrolTarget(Villager villager, HiredWorkContext context) {
        BlockPos fallback = context.workCenter();
        for (int attempt = 0; attempt < PATROL_TARGET_ATTEMPTS; attempt++) {
            int x = randomBetween(villager, context.workMin().getX(), context.workMax().getX());
            int y = randomBetween(villager, context.workMin().getY(), context.workMax().getY());
            int z = randomBetween(villager, context.workMin().getZ(), context.workMax().getZ());
            BlockPos candidate = new BlockPos(x, y, z);
            if (!context.isInsideWorkArea(candidate)) {
                continue;
            }
            Path path = villager.getNavigation().createPath(candidate, 0);
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

    private static String passiveStatus(HiredCombatMode mode) {
        return switch (mode) {
            case GUARD -> "I remain on guard and ready to answer trouble.";
            case ROAMING -> "I am roaming the work area and watching for trouble.";
            case ATTACK_ALL -> "I am sweeping the area and will engage any non-villager creature I find.";
            case HUNTING -> "I am roaming the area and hunting animals.";
        };
    }

    private static String activeStatus(Villager villager, HiredCombatMode mode) {
        LivingEntity target = villager.getTarget();
        String targetName = target == null ? "a target" : target.getName().getString();
        return switch (mode) {
            case GUARD -> "I am defending the area against " + targetName + ".";
            case ROAMING -> "I found trouble while roaming and I am engaging " + targetName + ".";
            case ATTACK_ALL -> "I am pressing the attack against " + targetName + ".";
            case HUNTING -> "I am hunting down " + targetName + ".";
        };
    }
}
