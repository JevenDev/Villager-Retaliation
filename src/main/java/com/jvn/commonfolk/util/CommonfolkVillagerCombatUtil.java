package com.jvn.commonfolk.util;

import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;

public final class CommonfolkVillagerCombatUtil {
    private CommonfolkVillagerCombatUtil() {
    }

    public static boolean isInCombat(Villager villager) {
        return villager.swinging
                || villager.isAggressive()
                || villager.isChasing()
                || villager.getTarget() != null;
    }

    public static boolean hasThreatMemory(Villager villager) {
        return villager.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE)
                || villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY)
                || villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY_ENTITY);
    }

    public static boolean isThreatened(Villager villager) {
        return isInCombat(villager) || hasThreatMemory(villager);
    }
}
