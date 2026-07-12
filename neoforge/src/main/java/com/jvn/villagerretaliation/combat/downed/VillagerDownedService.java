package com.jvn.villagerretaliation.combat.downed;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.util.TickThrottle;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

public final class VillagerDownedService {
    private static final String STATE_KEY = "VillagerRetaliationDownedState";
    private static final String DOWNED_KEY = "Downed";
    private static final String VERSION_KEY = "Version";
    private static final String ENTERED_AT_KEY = "EnteredAt";
    private static final String RECOVERY_AT_KEY = "RecoveryAt";
    private static final String QUIET_SINCE_KEY = "QuietSince";
    private static final String SOURCES_KEY = "ProtectionSources";
    private static final String PREVIOUS_NO_AI_KEY = "PreviousNoAi";
    private static final String PREVIOUS_PICKUP_KEY = "PreviousCanPickUpLoot";
    private static final int DATA_VERSION = 1;
    private static final long THREAT_SCAN_INTERVAL_TICKS = 20L;
    private static final Map<UUID, Long> NEXT_THREAT_SCAN_TICKS = new HashMap<>();

    private VillagerDownedService() {
    }

    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Villager villager)
                || !(villager.level() instanceof ServerLevel level)
                || canBypassDownedProtection(villager, event.getSource())) {
            return;
        }

        if (isDowned(villager)) {
            event.setNewDamage(0.0F);
            enforceIncapacitatedState(villager);
            return;
        }

        float absorption = villager.getAbsorptionAmount();
        float healthDamage = Math.max(0.0F, event.getNewDamage() - absorption);
        if (healthDamage < villager.getHealth()) {
            return;
        }

        VillagerDeathProtectionResolver.ProtectionResult protection =
                VillagerDeathProtectionResolver.resolve(level, villager);
        if (!protection.protectedFromDeath()) {
            return;
        }

        event.setNewDamage(Math.max(0.0F, villager.getHealth() - 1.0F + absorption));
        enterDowned(level, villager, protection);
    }

    public static boolean enterDowned(
            ServerLevel level,
            Villager villager,
            VillagerDeathProtectionResolver.ProtectionResult protection) {
        if (isDowned(villager)) {
            enforceIncapacitatedState(villager);
            return false;
        }

        long now = level.getGameTime();
        CompoundTag state = new CompoundTag();
        state.putBoolean(DOWNED_KEY, true);
        state.putInt(VERSION_KEY, DATA_VERSION);
        state.putLong(ENTERED_AT_KEY, now);
        state.putLong(RECOVERY_AT_KEY, now + Math.max(1, VillagerRetaliationConfig.DOWNED_MINIMUM_TICKS.get()));
        state.putLong(QUIET_SINCE_KEY, -1L);
        state.putString(SOURCES_KEY, protection.diagnosticValue());
        state.putBoolean(PREVIOUS_NO_AI_KEY, villager.isNoAi());
        state.putBoolean(PREVIOUS_PICKUP_KEY, villager.canPickUpLoot());
        villager.getPersistentData().put(STATE_KEY, state);
        villager.setHealth(Math.max(1.0F, villager.getHealth()));
        if (villager.isPassenger()) {
            villager.stopRiding();
        }
        enforceIncapacitatedState(villager);
        clearNearbyTargets(level, villager);
        return true;
    }

    public static void onVillagerTickPre(Villager villager) {
        if (!isDowned(villager) || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        enforceIncapacitatedState(villager);
        villager.setHealth(Math.max(1.0F, villager.getHealth()));

        long now = level.getGameTime();
        CompoundTag state = state(villager);
        if (now < state.getLong(RECOVERY_AT_KEY)
                || !TickThrottle.consume(villager.getUUID(), NEXT_THREAT_SCAN_TICKS, now, THREAT_SCAN_INTERVAL_TICKS)) {
            return;
        }

        if (hasNearbyThreat(level, villager)) {
            state.putLong(QUIET_SINCE_KEY, -1L);
            clearNearbyTargets(level, villager);
            return;
        }

        long quietSince = state.getLong(QUIET_SINCE_KEY);
        if (quietSince < 0L) {
            state.putLong(QUIET_SINCE_KEY, now);
            return;
        }
        if (now - quietSince >= Math.max(0, VillagerRetaliationConfig.DOWNED_QUIET_TICKS.get())) {
            recover(villager);
        }
    }

    public static void onVillagerLoaded(Villager villager) {
        if (isDowned(villager)) {
            enforceIncapacitatedState(villager);
            villager.setHealth(Math.max(1.0F, villager.getHealth()));
        }
    }

    public static void onVillagerUnloaded(Villager villager) {
        NEXT_THREAT_SCAN_TICKS.remove(villager.getUUID());
    }

    public static void clearRuntimeState() {
        NEXT_THREAT_SCAN_TICKS.clear();
    }

    public static boolean isDowned(Villager villager) {
        return villager != null
                && villager.getPersistentData().contains(STATE_KEY)
                && state(villager).getBoolean(DOWNED_KEY);
    }

    public static boolean canBypassDownedProtection(Villager villager, DamageSource source) {
        return source.is(DamageTypes.GENERIC_KILL)
                || source.is(DamageTypes.FELL_OUT_OF_WORLD)
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    public static void recover(Villager villager) {
        if (!isDowned(villager)) {
            return;
        }
        CompoundTag state = state(villager);
        boolean previousNoAi = state.getBoolean(PREVIOUS_NO_AI_KEY);
        boolean previousCanPickUpLoot = state.getBoolean(PREVIOUS_PICKUP_KEY);
        villager.getPersistentData().remove(STATE_KEY);
        NEXT_THREAT_SCAN_TICKS.remove(villager.getUUID());
        villager.setNoAi(previousNoAi);
        villager.setCanPickUpLoot(previousCanPickUpLoot);
        float percent = VillagerRetaliationConfig.DOWNED_RECOVERY_HEALTH_PERCENT.get().floatValue();
        villager.setHealth(Math.max(1.0F, villager.getMaxHealth() * percent));
        villager.setTarget(null);
        villager.setAggressive(false);
    }

    private static CompoundTag state(Villager villager) {
        return villager.getPersistentData().getCompound(STATE_KEY);
    }

    private static void enforceIncapacitatedState(Villager villager) {
        villager.getNavigation().stop();
        villager.setTarget(null);
        villager.setAggressive(false);
        villager.stopUsingItem();
        villager.setCanPickUpLoot(false);
        villager.setNoAi(true);
    }

    private static boolean hasNearbyThreat(ServerLevel level, Villager villager) {
        double radius = Math.max(1.0D, VillagerRetaliationConfig.DOWNED_THREAT_RADIUS.get());
        AABB area = villager.getBoundingBox().inflate(radius);
        for (LivingEntity candidate : level.getEntitiesOfClass(
                LivingEntity.class,
                area,
                candidate -> candidate != villager && candidate.isAlive())) {
            if (candidate instanceof Mob mob && mob.getTarget() == villager) {
                return true;
            }
            if (VillagerRetaliationVillagerCombatUtil.isNaturalHostileTarget(villager, candidate)) {
                return true;
            }
        }
        return false;
    }

    private static void clearNearbyTargets(ServerLevel level, Villager villager) {
        double radius = Math.max(1.0D, VillagerRetaliationConfig.DOWNED_THREAT_RADIUS.get());
        for (Mob mob : level.getEntitiesOfClass(Mob.class, villager.getBoundingBox().inflate(radius))) {
            if (mob.getTarget() == villager) {
                mob.setTarget(null);
            }
        }
    }
}
