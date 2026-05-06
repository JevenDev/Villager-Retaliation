package com.jvn.villagerretaliation.util;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public final class VillagerRetaliationVillagerCombatUtil {
    private VillagerRetaliationVillagerCombatUtil() {
    }

    public static boolean isInCombat(AbstractVillager villager) {
        return villager.swinging
                || villager.isAggressive()
                || villager instanceof Villager villageResident && villageResident.isChasing()
                || villager.getTarget() != null;
    }

    public static boolean hasThreatMemory(AbstractVillager villager) {
        return hasMemoryValueIfRegistered(villager, MemoryModuleType.NEAREST_HOSTILE)
                || hasMemoryValueIfRegistered(villager, MemoryModuleType.HURT_BY)
                || hasMemoryValueIfRegistered(villager, MemoryModuleType.HURT_BY_ENTITY);
    }

    public static boolean isThreatened(AbstractVillager villager) {
        return isInCombat(villager) || hasThreatMemory(villager);
    }

    public static Optional<LivingEntity> findNearestNaturalHostile(AbstractVillager villager, double radius) {
        if (!(villager.level() instanceof ServerLevel level)
                || !villager.isAlive()
                || radius <= 0.0D) {
            return Optional.empty();
        }

        AABB searchArea = villager.getBoundingBox().inflate(radius);
        LivingEntity closestVisible = null;
        double closestVisibleDistance = Double.MAX_VALUE;

        for (LivingEntity candidate : level.getEntitiesOfClass(
                LivingEntity.class,
                searchArea,
                target -> isNaturalHostileTarget(villager, target)
        )) {
            double distance = villager.distanceToSqr(candidate);
            if (villager.hasLineOfSight(candidate) && distance < closestVisibleDistance) {
                closestVisibleDistance = distance;
                closestVisible = candidate;
            }
        }

        return Optional.ofNullable(closestVisible);
    }

    public static Optional<LivingEntity> resolveAttacker(DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity livingEntity) {
            return Optional.of(livingEntity);
        }

        Entity direct = source.getDirectEntity();
        if (direct instanceof LivingEntity livingEntity) {
            return Optional.of(livingEntity);
        }

        return Optional.empty();
    }

    public static Optional<LivingEntity> resolveAttacker(LivingEntity victim, DamageSource source) {
        Optional<LivingEntity> directAttacker = resolveAttacker(source);
        if (directAttacker.isPresent()) {
            return directAttacker;
        }

        return VillagerRetaliationHazardAttribution.resolvePlayerOwner(victim, source)
                .map(LivingEntity.class::cast);
    }

    public static boolean shouldIgnoreAttacker(LivingEntity attacker) {
        if (attacker instanceof AbstractVillager) {
            return true;
        }

        return attacker instanceof Player player
                && (player.isSpectator()
                || VillagerRetaliationConfig.NEARBY_VILLAGERS_IGNORE_CREATIVE_PLAYERS.get() && player.isCreative());
    }

    public static InteractionHand selectAttackHand(AbstractVillager villager) {
        return villager.getMainHandItem().isEmpty() && !villager.getOffhandItem().isEmpty()
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
    }

    public static void updateSwingAnimation(AbstractVillager villager) {
        int swingDuration = Math.max(1, villager.getCurrentSwingDuration());
        if (villager.swinging) {
            villager.swingTime++;
            if (villager.swingTime >= swingDuration) {
                villager.swingTime = 0;
                villager.swinging = false;
            }
        } else {
            villager.swingTime = 0;
        }

        villager.attackAnim = (float) villager.swingTime / (float) swingDuration;
    }

    public static <T> Optional<T> getMemoryIfRegistered(AbstractVillager villager, MemoryModuleType<T> memoryType) {
        try {
            return villager.getBrain().getMemory(memoryType);
        } catch (IllegalStateException ignored) {
            return Optional.empty();
        }
    }

    public static void eraseMemoryIfRegistered(AbstractVillager villager, MemoryModuleType<?> memoryType) {
        try {
            villager.getBrain().eraseMemory(memoryType);
        } catch (IllegalStateException ignored) {
        }
    }

    private static boolean hasMemoryValueIfRegistered(AbstractVillager villager, MemoryModuleType<?> memoryType) {
        try {
            return villager.getBrain().hasMemoryValue(memoryType);
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    private static boolean isNaturalHostileTarget(AbstractVillager villager, LivingEntity target) {
        return target != villager
                && target.isAlive()
                && target instanceof Enemy
                && !target.isAlliedTo(villager)
                && !VillagerRetaliationVillagerCombatUtil.shouldIgnoreAttacker(target);
    }
}
