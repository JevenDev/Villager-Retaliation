package com.jvn.villagerretaliation.util;

import com.jvn.toucanlib.util.ToucanBrainMemories;
import com.jvn.toucanlib.util.ToucanHazardAttribution;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Creeper;
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
        TargetingConditions targetingConditions = TargetingConditions.forCombat()
                .range(radius)
                .selector(target -> isNaturalHostileTarget(villager, target));
        LivingEntity closestVisible = level.getNearestEntity(
                level.getEntitiesOfClass(Mob.class, searchArea, target -> isNaturalHostileTarget(villager, target)),
                targetingConditions,
                villager,
                villager.getX(),
                villager.getEyeY(),
                villager.getZ()
        );

        return Optional.ofNullable(closestVisible);
    }

    public static Optional<LivingEntity> findNaturalHostileMemoryTarget(AbstractVillager villager) {
        return getMemoryIfRegistered(villager, MemoryModuleType.NEAREST_HOSTILE)
                .filter(LivingEntity::isAlive)
                .filter(target -> target != villager)
                .filter(target -> isNaturalHostileTarget(villager, target))
                .filter(target -> isWithinNaturalHostileTargetRange(villager, target))
                .filter(villager::hasLineOfSight);
    }

    public static boolean hasVisibleCreeperThreat(AbstractVillager villager, double radius) {
        return findNearestVisibleCreeper(villager, radius).isPresent();
    }

    public static Optional<Creeper> findNearestVisibleCreeper(AbstractVillager villager, double radius) {
        if (!(villager.level() instanceof ServerLevel level)
                || !villager.isAlive()
                || radius <= 0.0D) {
            return Optional.empty();
        }

        AABB searchArea = villager.getBoundingBox().inflate(radius);
        Creeper closestVisible = null;
        double closestVisibleDistance = Double.MAX_VALUE;
        double maxDistanceSqr = radius * radius;

        for (Creeper creeper : level.getEntitiesOfClass(Creeper.class, searchArea, LivingEntity::isAlive)) {
            double distance = villager.distanceToSqr(creeper);
            if (distance <= maxDistanceSqr && villager.hasLineOfSight(creeper) && distance < closestVisibleDistance) {
                closestVisibleDistance = distance;
                closestVisible = creeper;
            }
        }

        return Optional.ofNullable(closestVisible);
    }

    public static boolean isWithinNaturalHostileTargetRange(AbstractVillager villager, LivingEntity target) {
        double radius = VillagerRetaliationConfig.NATURAL_HOSTILE_TARGET_RADIUS.get();
        return radius > 0.0D && villager.distanceToSqr(target) <= radius * radius;
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

        Optional<LivingEntity> hazardOwner = ToucanHazardAttribution.resolveVanillaHazardOwner(victim, source)
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast);
        if (hazardOwner.isPresent()) {
            return hazardOwner;
        }

        return Optional.ofNullable(victim.getKillCredit());
    }

    public static boolean shouldIgnoreAttacker(LivingEntity attacker) {
        if (attacker instanceof AbstractVillager || attacker instanceof IronGolem || attacker instanceof NeutralMob) {
            return true;
        }

        return attacker instanceof Player player
                && (player.isSpectator()
                || VillagerRetaliationConfig.NEARBY_VILLAGERS_IGNORE_CREATIVE_PLAYERS.get() && player.isCreative());
    }

    public static boolean isVillagerGolemConflict(Entity first, Entity second) {
        return first instanceof IronGolem && second instanceof AbstractVillager
                || first instanceof AbstractVillager && second instanceof IronGolem;
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
        return ToucanBrainMemories.getIfRegistered(villager.getBrain(), memoryType);
    }

    public static void eraseMemoryIfRegistered(AbstractVillager villager, MemoryModuleType<?> memoryType) {
        ToucanBrainMemories.eraseIfRegistered(villager.getBrain(), memoryType);
    }

    private static boolean hasMemoryValueIfRegistered(AbstractVillager villager, MemoryModuleType<?> memoryType) {
        return ToucanBrainMemories.hasValueIfRegistered(villager.getBrain(), memoryType);
    }

    public static boolean isNaturalHostileTarget(AbstractVillager villager, LivingEntity target) {
        return target != villager
                && target.isAlive()
                && target instanceof Enemy
                && !(target instanceof NeutralMob)
                && !(target instanceof Creeper)
                && !target.isAlliedTo(villager)
                && !VillagerRetaliationVillagerCombatUtil.shouldIgnoreAttacker(target);
    }
}
