package com.jvn.villagerretaliation.villager;

import com.jvn.villagerretaliation.util.TickThrottle;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.ShowTradesToPlayer;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;

public final class VillagerRetaliationVillagerBrainUtil {
    private static final float FLEE_SPEED_MODIFIER = 0.75F;
    private static final int FLEE_HORIZONTAL_RANGE = 16;
    private static final int FLEE_VERTICAL_RANGE = 7;
    private static final long TRADE_PREVIEW_BEHAVIOR_RECHECK_TICKS = 20L * 10L;
    private static final Map<UUID, Long> NEXT_TRADE_PREVIEW_BEHAVIOR_CHECK_TICKS = new HashMap<>();

    private VillagerRetaliationVillagerBrainUtil() {
    }

    public static void clearThreatMemories(AbstractVillager villager) {
        VillagerRetaliationVillagerCombatUtil.eraseMemoryIfRegistered(villager, MemoryModuleType.HURT_BY);
        VillagerRetaliationVillagerCombatUtil.eraseMemoryIfRegistered(villager, MemoryModuleType.HURT_BY_ENTITY);
        VillagerRetaliationVillagerCombatUtil.eraseMemoryIfRegistered(villager, MemoryModuleType.NEAREST_HOSTILE);
    }

    public static boolean hasThreatMemories(Brain<?> brain) {
        return brain.hasMemoryValue(MemoryModuleType.HURT_BY)
                || brain.hasMemoryValue(MemoryModuleType.HURT_BY_ENTITY)
                || brain.hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE);
    }

    public static void removeTradePreviewBehavior(ServerLevel level, Villager villager) {
        removeTradePreviewBehaviorNow(level, villager);
        NEXT_TRADE_PREVIEW_BEHAVIOR_CHECK_TICKS.put(
                villager.getUUID(),
                level.getGameTime() + TRADE_PREVIEW_BEHAVIOR_RECHECK_TICKS + TickThrottle.stagger(villager.getUUID(), TRADE_PREVIEW_BEHAVIOR_RECHECK_TICKS)
        );
    }

    public static void removeTradePreviewBehaviorIfDue(ServerLevel level, Villager villager) {
        UUID villagerId = villager.getUUID();
        long gameTime = level.getGameTime();
        long nextCheck = NEXT_TRADE_PREVIEW_BEHAVIOR_CHECK_TICKS.getOrDefault(villagerId, Long.MIN_VALUE);
        if (nextCheck == Long.MIN_VALUE) {
            nextCheck = gameTime + TickThrottle.stagger(villagerId, TRADE_PREVIEW_BEHAVIOR_RECHECK_TICKS);
            if (nextCheck > gameTime) {
                NEXT_TRADE_PREVIEW_BEHAVIOR_CHECK_TICKS.put(villagerId, nextCheck);
                return;
            }
        } else if (gameTime < nextCheck) {
            return;
        }

        removeTradePreviewBehavior(level, villager);
    }

    public static void clearRuntimeState() {
        NEXT_TRADE_PREVIEW_BEHAVIOR_CHECK_TICKS.clear();
    }

    public static void clearRuntimeState(Villager villager) {
        NEXT_TRADE_PREVIEW_BEHAVIOR_CHECK_TICKS.remove(villager.getUUID());
    }

    private static void removeTradePreviewBehaviorNow(ServerLevel level, Villager villager) {
        Brain<Villager> brain = villager.getBrain();
        long gameTime = level.getGameTime();
        for (Map<Activity, Set<BehaviorControl<? super Villager>>> behaviorsByActivity : brain.availableBehaviorsByPriority.values()) {
            for (Set<BehaviorControl<? super Villager>> behaviors : behaviorsByActivity.values()) {
                Iterator<BehaviorControl<? super Villager>> iterator = behaviors.iterator();
                while (iterator.hasNext()) {
                    BehaviorControl<? super Villager> behavior = iterator.next();
                    if (behavior instanceof ShowTradesToPlayer) {
                        if (behavior.getStatus() == Behavior.Status.RUNNING) {
                            behavior.doStop(level, villager, gameTime);
                        }
                        iterator.remove();
                    }
                }
            }
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
}
