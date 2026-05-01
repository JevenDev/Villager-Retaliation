package com.jvn.commonfolk.util;

import java.util.Optional;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;

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
