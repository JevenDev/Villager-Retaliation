package com.jvn.commonfolk.villager;

import com.jvn.commonfolk.util.CommonfolkVillagerCombatUtil;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.AbstractVillager;

public final class CommonfolkVillagerBrainUtil {
    private CommonfolkVillagerBrainUtil() {
    }

    public static void clearThreatMemories(AbstractVillager villager) {
        CommonfolkVillagerCombatUtil.eraseMemoryIfRegistered(villager, MemoryModuleType.HURT_BY);
        CommonfolkVillagerCombatUtil.eraseMemoryIfRegistered(villager, MemoryModuleType.HURT_BY_ENTITY);
        CommonfolkVillagerCombatUtil.eraseMemoryIfRegistered(villager, MemoryModuleType.NEAREST_HOSTILE);
    }

    public static boolean hasThreatMemories(Brain<?> brain) {
        return brain.hasMemoryValue(MemoryModuleType.HURT_BY)
                || brain.hasMemoryValue(MemoryModuleType.HURT_BY_ENTITY)
                || brain.hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE);
    }
}
