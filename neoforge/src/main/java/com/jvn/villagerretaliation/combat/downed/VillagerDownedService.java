package com.jvn.villagerretaliation.combat.downed;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.util.TickThrottle;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import com.jvn.villagerretaliation.scene.SceneLifecycleIntegration;
import com.jvn.villagerretaliation.compat.secondwind.VillagerSecondWindCompat;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
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
    private static final String POSE_KEY = "Pose";
    private static final int DATA_VERSION = 2;
    private static final long THREAT_SCAN_INTERVAL_TICKS = 20L;
    private static final Map<UUID, Long> NEXT_THREAT_SCAN_TICKS = new HashMap<>();
    private static final Map<UUID, Float> PENDING_ABSORPTION_RESTORE = new HashMap<>();

    private VillagerDownedService() {
    }

    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Villager villager)
                || !(villager.level() instanceof ServerLevel level)
                || canBypassDownedProtection(villager, event.getSource())) {
            return;
        }

        if (isDowned(villager)) {
            float consequenceHealth = Math.min(villager.getMaxHealth(), 2.0F);
            if (event.getNewDamage() > 0.0F && consequenceHealth > 1.0F) {
                float absorption = villager.getAbsorptionAmount();
                if (absorption > 0.0F) {
                    PENDING_ABSORPTION_RESTORE.put(villager.getUUID(), absorption);
                    villager.setAbsorptionAmount(0.0F);
                }
                villager.setHealth(consequenceHealth);
                event.setNewDamage(consequenceHealth - 1.0F);
            } else {
                event.setNewDamage(0.0F);
            }
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

        float consequenceHealth = Math.max(villager.getHealth(), Math.min(villager.getMaxHealth(), 2.0F));
        villager.setHealth(consequenceHealth);
        event.setNewDamage(Math.max(0.0F, consequenceHealth - 1.0F + absorption));
        enterDowned(level, villager, protection);
    }

    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager) || !isDowned(villager)) {
            return;
        }
        Float absorption = PENDING_ABSORPTION_RESTORE.remove(villager.getUUID());
        if (absorption != null) {
            villager.setAbsorptionAmount(Math.max(villager.getAbsorptionAmount(), absorption));
        }
        villager.setHealth(Math.max(1.0F, villager.getHealth()));
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
        VillagerDownedPose pose = VillagerSecondWindCompat.resolvePose(villager)
                .flatMap(VillagerDownedPose::fromId)
                .orElseGet(() -> VillagerDownedPose.forVillager(villager.getUUID()));
        state.putString(POSE_KEY, pose.id().toString());
        villager.getPersistentData().put(STATE_KEY, state);
        com.jvn.villagerretaliation.social.VillagerBreedingPolicy.cancelActiveAttempt(level, villager);
        NEXT_THREAT_SCAN_TICKS.remove(villager.getUUID());
        villager.setHealth(Math.max(1.0F, villager.getHealth()));
        if (villager.isPassenger()) {
            villager.stopRiding();
        }
        enforceIncapacitatedState(villager);
        refreshDownedDimensionsSafely(villager);
        VillagerConversationService.endForVillager(villager, true);
        clearNearbyTargets(level, villager);
        SceneLifecycleIntegration.onActorDowned(villager);
        VillagerReputationNetworking.syncDownedStateToTracking(villager, true);
        VillagerSecondWindCompat.notifyStateChanged(villager);
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
        if (!TickThrottle.consume(villager.getUUID(), NEXT_THREAT_SCAN_TICKS, now, THREAT_SCAN_INTERVAL_TICKS)) {
            return;
        }

        if (hasNearbyThreat(level, villager)) {
            state.putLong(QUIET_SINCE_KEY, -1L);
            clearNearbyTargets(level, villager);
            return;
        }

        if (now < state.getLong(RECOVERY_AT_KEY)) {
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
            CompoundTag state = state(villager);
            if (!state.contains(POSE_KEY)) {
                state.putString(POSE_KEY, VillagerDownedPose.forVillager(villager.getUUID()).id().toString());
                state.putInt(VERSION_KEY, DATA_VERSION);
            }
            enforceIncapacitatedState(villager);
            villager.setHealth(Math.max(1.0F, villager.getHealth()));
            refreshDownedDimensionsSafely(villager);
            VillagerSecondWindCompat.notifyStateChanged(villager);
        }
    }

    public static void onVillagerUnloaded(Villager villager) {
        NEXT_THREAT_SCAN_TICKS.remove(villager.getUUID());
        PENDING_ABSORPTION_RESTORE.remove(villager.getUUID());
    }

    public static void clearRuntimeState() {
        NEXT_THREAT_SCAN_TICKS.clear();
        PENDING_ABSORPTION_RESTORE.clear();
    }

    public static boolean isDowned(Villager villager) {
        return villager != null
                && villager.getPersistentData().contains(STATE_KEY)
                && state(villager).getBoolean(DOWNED_KEY);
    }

    public static void clearInheritedStateForNewborn(Villager child) {
        if (child == null) return;
        child.getPersistentData().remove(STATE_KEY);
        NEXT_THREAT_SCAN_TICKS.remove(child.getUUID());
        PENDING_ABSORPTION_RESTORE.remove(child.getUUID());
    }

    public static boolean canBypassDownedProtection(Villager villager, DamageSource source) {
        return source.is(DamageTypes.GENERIC_KILL)
                || source.is(DamageTypes.FELL_OUT_OF_WORLD)
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    public static VillagerDownedPose pose(Villager villager) {
        if (isDowned(villager)) {
            String value = state(villager).getString(POSE_KEY);
            if (!value.isBlank()) {
                try {
                    return VillagerDownedPose.fromId(net.minecraft.resources.ResourceLocation.parse(value))
                            .orElseGet(() -> VillagerDownedPose.forVillager(villager.getUUID()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return VillagerDownedPose.forVillager(villager.getUUID());
    }

    public static void recover(Villager villager) {
        if (!isDowned(villager)) {
            return;
        }
        CompoundTag state = state(villager);
        boolean previousNoAi = state.getBoolean(PREVIOUS_NO_AI_KEY);
        boolean previousCanPickUpLoot = state.getBoolean(PREVIOUS_PICKUP_KEY);
        villager.getPersistentData().remove(STATE_KEY);
        villager.refreshDimensions();
        NEXT_THREAT_SCAN_TICKS.remove(villager.getUUID());
        PENDING_ABSORPTION_RESTORE.remove(villager.getUUID());
        villager.setNoAi(previousNoAi);
        villager.setCanPickUpLoot(previousCanPickUpLoot);
        float percent = VillagerRetaliationConfig.DOWNED_RECOVERY_HEALTH_PERCENT.get().floatValue();
        villager.setHealth(Math.max(1.0F, villager.getMaxHealth() * percent));
        villager.setTarget(null);
        villager.setAggressive(false);
        SceneLifecycleIntegration.onActorRecovered(villager);
        VillagerReputationNetworking.syncDownedStateToTracking(villager, false);
        VillagerSecondWindCompat.notifyStateChanged(villager);
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

    private static void refreshDownedDimensionsSafely(Villager villager) {
        villager.refreshDimensions();
        if (moveResizedHitboxOutOfBlocks(villager) || pose(villager) == VillagerDownedPose.SITTING) {
            return;
        }

        state(villager).putString(POSE_KEY, VillagerDownedPose.SITTING.id().toString());
        villager.refreshDimensions();
        moveResizedHitboxOutOfBlocks(villager);
    }

    private static boolean moveResizedHitboxOutOfBlocks(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level) || level.noCollision(villager)) {
            return true;
        }

        AABB bounds = villager.getBoundingBox();
        EntityDimensions standingDimensions = villager.getDimensions(villager.getPose());
        Vec3 center = bounds.getCenter();
        double horizontalSearch = Math.max(1.0E-6D, bounds.getXsize() - standingDimensions.width() + 1.0E-6D);
        double verticalSearch = Math.max(1.0E-6D, bounds.getYsize() - standingDimensions.height() + 1.0E-6D);
        return level.findFreePosition(
                        villager,
                        Shapes.create(AABB.ofSize(center, horizontalSearch, verticalSearch, horizontalSearch)),
                        center,
                        bounds.getXsize(),
                        bounds.getYsize(),
                        bounds.getZsize())
                .map(freeCenter -> {
                    AABB freeBounds = bounds.move(
                            freeCenter.x - center.x,
                            freeCenter.y - center.y,
                            freeCenter.z - center.z);
                    if (!level.noCollision(villager, freeBounds)) {
                        return false;
                    }
                    villager.setPos(freeCenter.x, freeCenter.y - bounds.getYsize() * 0.5D, freeCenter.z);
                    return true;
                })
                .orElse(false);
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
