package com.jvn.villagerretaliation.villager;

import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.AbstractVillager;

public final class VillagerRetaliationVillagerBrainUtil {
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
}
