package com.jvn.villagerretaliation.villager;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/** Restores sleeping villagers to a configurable minimum health threshold. */
public final class VillagerSleepHealingService {
    private VillagerSleepHealingService() {
    }

    public static void onVillagerTick(Villager villager) {
        if (!(villager.level() instanceof ServerLevel)
                || !villager.isAlive()
                || !villager.isSleeping()
                || !VillagerRetaliationConfig.ENABLE_VILLAGER_SLEEP_HEALING.get()) {
            return;
        }

        float targetHealth = targetHealth(
                villager.getMaxHealth(),
                VillagerRetaliationConfig.VILLAGER_SLEEP_HEALING_MAX_HEALTH_PERCENT.get());
        if (villager.getHealth() < targetHealth) {
            villager.setHealth(targetHealth);
        }
    }

    static float targetHealth(float maxHealth, double healthPercent) {
        double clampedPercent = Math.clamp(healthPercent, 0.0D, 1.0D);
        return (float) (Math.max(0.0F, maxHealth) * clampedPercent);
    }
}
