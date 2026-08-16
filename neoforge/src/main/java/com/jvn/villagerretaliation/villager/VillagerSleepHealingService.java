package com.jvn.villagerretaliation.villager;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/** Restores villagers after they complete a night's sleep. */
public final class VillagerSleepHealingService {
    private static final String SLEEP_HEALING_PENDING_TAG = "VillagerRetaliationSleepHealingPending";

    private VillagerSleepHealingService() {
    }

    public static void onVillagerTick(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level) || !villager.isAlive()) {
            return;
        }

        CompoundTag data = villager.getPersistentData();
        if (villager.isSleeping()) {
            if (VillagerRetaliationConfig.ENABLE_VILLAGER_SLEEP_HEALING.get()) {
                data.putBoolean(SLEEP_HEALING_PENDING_TAG, true);
            } else {
                data.remove(SLEEP_HEALING_PENDING_TAG);
            }
            return;
        }

        if (!data.getBoolean(SLEEP_HEALING_PENDING_TAG)) {
            return;
        }
        data.remove(SLEEP_HEALING_PENDING_TAG);

        // Natural wake-up occurs after dawn. Any wake-up during the night is an
        // interruption and consumes the pending sleep without granting recovery.
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_SLEEP_HEALING.get() || !isAfterDawn(level)) {
            return;
        }

        float targetHealth = targetHealth(
                villager.getMaxHealth(),
                VillagerRetaliationConfig.VILLAGER_SLEEP_HEALING_MAX_HEALTH_PERCENT.get());
        if (villager.getHealth() < targetHealth) {
            villager.setHealth(targetHealth);
            level.sendParticles(
                    ParticleTypes.HEART,
                    villager.getX(),
                    villager.getY() + villager.getBbHeight() + 0.25D,
                    villager.getZ(),
                    4,
                    0.3D,
                    0.25D,
                    0.3D,
                    0.02D);
        }
    }

    static float targetHealth(float maxHealth, double healthPercent) {
        double clampedPercent = Math.clamp(healthPercent, 0.0D, 1.0D);
        return (float) (Math.max(0.0F, maxHealth) * clampedPercent);
    }

    private static boolean isAfterDawn(ServerLevel level) {
        return level.dimensionType().hasSkyLight()
                && Math.floorMod(level.getDayTime(), 24_000L) < 12_000L;
    }
}
