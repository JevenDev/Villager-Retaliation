package com.jvn.villagerretaliation.villager;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;

public final class VillagerRetaliationVillagerBrainUtil {
    private static final float FLEE_SPEED_MODIFIER = 0.75F;
    private static final int FLEE_HORIZONTAL_RANGE = 16;
    private static final int FLEE_VERTICAL_RANGE = 7;

    private VillagerRetaliationVillagerBrainUtil() {
    }

    public static void clearThreatMemories(AbstractVillager villager) {
        VillagerRetaliationVillagerCombatUtil.eraseMemoryIfRegistered(villager, MemoryModuleType.HURT_BY);
        VillagerRetaliationVillagerCombatUtil.eraseMemoryIfRegistered(villager, MemoryModuleType.HURT_BY_ENTITY);
        VillagerRetaliationVillagerCombatUtil.eraseMemoryIfRegistered(villager, MemoryModuleType.NEAREST_HOSTILE);
        villager.setLastHurtByMob(null);
    }

    public static void clearPathingMemories(AbstractVillager villager) {
        VillagerRetaliationVillagerCombatUtil.eraseMemoryIfRegistered(villager, MemoryModuleType.WALK_TARGET);
        VillagerRetaliationVillagerCombatUtil.eraseMemoryIfRegistered(villager, MemoryModuleType.PATH);
    }

    public static void clearMovementMemories(AbstractVillager villager) {
        clearPathingMemories(villager);
        VillagerRetaliationVillagerCombatUtil.eraseMemoryIfRegistered(villager, MemoryModuleType.LOOK_TARGET);
    }

    public static boolean shouldSuppressVanillaBrainTickForCombat(Villager villager) {
        LivingEntity target = villager.getTarget();
        return target != null
                && target != villager
                && target.isAlive()
                && VillagerRetaliationConfig.ENABLE_VILLAGER_RETALIATION.get();
    }

    public static void stopNavigationAndClearPathing(AbstractVillager villager) {
        if (!villager.getNavigation().isDone() || villager.getNavigation().getTargetPos() != null) {
            villager.getNavigation().stop();
        }
        clearPathingMemories(villager);
    }

    public static void stopNavigationAndClearMovement(AbstractVillager villager) {
        if (!villager.getNavigation().isDone() || villager.getNavigation().getTargetPos() != null) {
            villager.getNavigation().stop();
        }
        clearMovementMemories(villager);
    }

    public static boolean hasThreatMemories(Brain<?> brain) {
        return brain.hasMemoryValue(MemoryModuleType.HURT_BY)
                || brain.hasMemoryValue(MemoryModuleType.HURT_BY_ENTITY)
                || brain.hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE);
    }

    public static void clearFleeMemories(Villager villager) {
        clearThreatMemories(villager);
        VillagerRetaliationVillagerCombatUtil.eraseMemoryIfRegistered(villager, MemoryModuleType.HEARD_BELL_TIME);
        VillagerRetaliationVillagerCombatUtil.eraseMemoryIfRegistered(villager, MemoryModuleType.HIDING_PLACE);
    }

    public static boolean hasVanillaFleeState(@Nullable ServerLevel level, Brain<Villager> brain) {
        return hasThreatMemories(brain)
                || brain.hasMemoryValue(MemoryModuleType.HEARD_BELL_TIME)
                || brain.hasMemoryValue(MemoryModuleType.HIDING_PLACE)
                || brain.isActive(Activity.PANIC)
                || brain.isActive(Activity.HIDE)
                || level != null && brain.isActive(Activity.PRE_RAID)
                || level != null && brain.isActive(Activity.RAID);
    }

    public static void suppressVanillaFleeState(ServerLevel level, Villager villager) {
        Brain<Villager> brain = villager.getBrain();
        boolean shouldResetActivity = hasVanillaFleeState(level, brain);
        clearFleeMemories(villager);
        if (!shouldResetActivity) {
            return;
        }

        stopNavigationAndClearMovement(villager);
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(scheduledActivity(level, brain));
        if (isActiveFleeActivity(brain)) {
            brain.setActiveActivityIfPossible(Activity.IDLE);
        }
    }

    public static void enterFleeState(Villager villager, @Nullable LivingEntity hostile, long gameTime) {
        Brain<Villager> brain = villager.getBrain();
        if (hostile != null && hostile.isAlive()) {
            brain.setMemory(MemoryModuleType.NEAREST_HOSTILE, hostile);
            seedWalkTargetAwayFrom(villager, hostile);
        }
        brain.setMemory(MemoryModuleType.HEARD_BELL_TIME, gameTime);
        brain.setActiveActivityIfPossible(Activity.PANIC);
    }

    private static void seedWalkTargetAwayFrom(Villager villager, LivingEntity hostile) {
        if (hasAwayFromHostileWalkTarget(villager, hostile)) {
            return;
        }

        Vec3 fleeTarget = LandRandomPos.getPosAway(
                villager,
                FLEE_HORIZONTAL_RANGE,
                FLEE_VERTICAL_RANGE,
                hostile.position()
        );
        if (fleeTarget != null) {
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(fleeTarget, FLEE_SPEED_MODIFIER, 0));
        }
    }

    private static boolean hasAwayFromHostileWalkTarget(Villager villager, LivingEntity hostile) {
        return villager.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .map(walkTarget -> isTargetAwayFromHostile(villager, hostile, walkTarget))
                .orElse(false);
    }

    private static boolean isTargetAwayFromHostile(Villager villager, LivingEntity hostile, WalkTarget walkTarget) {
        Vec3 villagerPosition = villager.position();
        Vec3 walkDirection = walkTarget.getTarget().currentPosition().subtract(villagerPosition);
        Vec3 hostileDirection = hostile.position().subtract(villagerPosition);
        return walkDirection.dot(hostileDirection) < 0.0D;
    }

    private static Activity scheduledActivity(ServerLevel level, Brain<Villager> brain) {
        Activity activity = brain.getSchedule().getActivityAt((int) (level.getDayTime() % 24000L));
        return isFleeActivity(activity) ? Activity.IDLE : activity;
    }

    private static boolean isFleeActivity(Activity activity) {
        return activity == Activity.PANIC
                || activity == Activity.PRE_RAID
                || activity == Activity.RAID
                || activity == Activity.HIDE;
    }

    private static boolean isActiveFleeActivity(Brain<Villager> brain) {
        return brain.isActive(Activity.PANIC)
                || brain.isActive(Activity.PRE_RAID)
                || brain.isActive(Activity.RAID)
                || brain.isActive(Activity.HIDE);
    }
}
