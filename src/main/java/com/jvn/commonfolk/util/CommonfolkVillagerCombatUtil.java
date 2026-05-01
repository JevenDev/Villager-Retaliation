package com.jvn.commonfolk.util;

import com.jvn.commonfolk.config.CommonfolkConfig;
import java.util.Optional;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;

public final class CommonfolkVillagerCombatUtil {
    private CommonfolkVillagerCombatUtil() {
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

    public static boolean shouldIgnoreAttacker(LivingEntity attacker) {
        return attacker instanceof Player player
                && (player.isSpectator()
                || CommonfolkConfig.NEARBY_VILLAGERS_IGNORE_CREATIVE_PLAYERS.get() && player.isCreative());
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
}
