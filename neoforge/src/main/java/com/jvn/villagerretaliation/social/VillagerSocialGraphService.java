package com.jvn.villagerretaliation.social;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.util.TickThrottle;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class VillagerSocialGraphService {
    private static final long PROFILE_REFRESH_INTERVAL_TICKS = 200L;
    private static final Map<UUID, Long> PENDING_JOIN_PROFILE_TICKS = new HashMap<>();

    private VillagerSocialGraphService() {
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_SOCIAL_GRAPH.get()) {
            return;
        }
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof Villager villager) {
            PENDING_JOIN_PROFILE_TICKS.put(
                    villager.getUUID(),
                    level.getGameTime()
                            + TickThrottle.spreadOffset(villager.getUUID(), PROFILE_REFRESH_INTERVAL_TICKS)
            );
        }
    }

    public static void onEntityTickPost(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager)
                || !(villager.level() instanceof ServerLevel level)
                || !VillagerRetaliationConfig.ENABLE_VILLAGER_SOCIAL_GRAPH.get()) {
            return;
        }

        long gameTime = level.getGameTime();
        UUID villagerId = villager.getUUID();
        Long pendingJoinProfileTick = PENDING_JOIN_PROFILE_TICKS.get(villagerId);
        if (pendingJoinProfileTick != null) {
            if (gameTime < pendingJoinProfileTick) {
                return;
            }
            PENDING_JOIN_PROFILE_TICKS.remove(villagerId);
            VillagerSocialGraphSavedData.get(level).ensureProfile(level, villager);
            return;
        }

        if (!TickThrottle.isSpreadTick(villagerId, gameTime, PROFILE_REFRESH_INTERVAL_TICKS)) {
            return;
        }

        VillagerSocialGraphSavedData.get(level).ensureProfile(level, villager);
    }

    public static void onEntityLeaveLevel(Entity entity) {
        if (entity instanceof Villager villager) {
            PENDING_JOIN_PROFILE_TICKS.remove(villager.getUUID());
        }
    }

    public static void clearRuntimeState() {
        PENDING_JOIN_PROFILE_TICKS.clear();
    }

    public static void onBabyEntitySpawn(BabyEntitySpawnEvent event) {
        Mob parentA = event.getParentA();
        Mob parentB = event.getParentB();
        if (!(parentA instanceof Villager parentVillagerA)
                || !(parentB instanceof Villager parentVillagerB)
                || !(parentA.level() instanceof ServerLevel level)
                || !VillagerRetaliationConfig.ENABLE_VILLAGER_SOCIAL_GRAPH.get()) {
            return;
        }

        VillagerSocialGraphSavedData socialGraph = VillagerSocialGraphSavedData.get(level);
        if (VillagerRetaliationConfig.ENABLE_FAMILY_BREEDING_RULES.get()) {
            VillagerSocialGraphSavedData.BreedingValidation validation = socialGraph.validateBreedingPair(level, parentVillagerA, parentVillagerB);
            if (!validation.allowed()) {
                event.setCanceled(true);
                if (event.getCausedByPlayer() instanceof ServerPlayer player) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(validation.reason()), true);
                }
                return;
            }
        }

        AgeableMob child = event.getChild();
        if (!(child instanceof Villager childVillager)) {
            return;
        }

        socialGraph.linkParentsAndChild(level, parentVillagerA, parentVillagerB, childVillager);
        VillagerReputationManager.inheritReputationFromParents(level, childVillager, parentVillagerA, parentVillagerB);
        VillageEventMemory.remember(level, VillageEventMemory.EventTag.BABY_BORN, childVillager.blockPosition(), childVillager, event.getCausedByPlayer());
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (VillagerRetaliationConfig.ENABLE_VILLAGER_SOCIAL_GRAPH.get()
                && event.getEntity() instanceof Villager villager
                && villager.level() instanceof ServerLevel level) {
            VillagerSocialGraphSavedData.get(level).markDead(level, villager, deathCause(event.getSource()));
        }
    }

    public static void onLivingConversionPost(LivingConversionEvent.Post event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_SOCIAL_GRAPH.get()) {
            return;
        }

        LivingEntity source = event.getEntity();
        LivingEntity outcome = event.getOutcome();
        if (!(source instanceof Villager) && !(outcome instanceof Villager)) {
            return;
        }

        VillagerSocialGraphSavedData data = VillagerSocialGraphSavedData.get(level);
        data.transferIdentity(source.getUUID(), outcome.getUUID());
        if (outcome instanceof Villager villager) {
            data.ensureProfile(level, villager);
        }
    }

    public static VillagerFamilyTreeSnapshot familySnapshot(ServerLevel level, Villager villager) {
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_SOCIAL_GRAPH.get()) {
            return VillagerFamilyTreeSnapshot.EMPTY;
        }
        return VillagerSocialGraphSavedData.get(level).familySnapshot(level, villager);
    }

    public static VillagerFamilyTreeSnapshot familySnapshot(ServerLevel level, UUID villagerId) {
        if (villagerId == null || !VillagerRetaliationConfig.ENABLE_VILLAGER_SOCIAL_GRAPH.get()) {
            return VillagerFamilyTreeSnapshot.EMPTY;
        }
        return VillagerSocialGraphSavedData.get(level).familySnapshot(level, villagerId);
    }

    public static VillagerRelationshipSnapshot relationshipSnapshot(ServerLevel level, Villager villager) {
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_SOCIAL_GRAPH.get()) {
            return VillagerRelationshipSnapshot.EMPTY;
        }
        return VillagerSocialGraphSavedData.get(level).relationshipSnapshot(level, villager);
    }

    public static VillagerRelationshipSnapshot relationshipSnapshot(ServerLevel level, UUID villagerId) {
        if (villagerId == null || !VillagerRetaliationConfig.ENABLE_VILLAGER_SOCIAL_GRAPH.get()) {
            return VillagerRelationshipSnapshot.EMPTY;
        }
        return VillagerSocialGraphSavedData.get(level).relationshipSnapshot(level, villagerId);
    }

    public static Optional<Boolean> knownBaby(ServerLevel level, UUID villagerId) {
        if (villagerId == null || !VillagerRetaliationConfig.ENABLE_VILLAGER_SOCIAL_GRAPH.get()) {
            return Optional.empty();
        }
        return VillagerSocialGraphSavedData.get(level).knownBaby(villagerId);
    }

    public static Optional<String> knownVillage(ServerLevel level, UUID villagerId) {
        if (villagerId == null || !VillagerRetaliationConfig.ENABLE_VILLAGER_SOCIAL_GRAPH.get()) {
            return Optional.empty();
        }
        return VillagerSocialGraphSavedData.get(level).knownVillage(villagerId);
    }

    private static String deathCause(DamageSource source) {
        Entity attacker = source.getEntity();
        String cause = source.getMsgId();
        if (attacker != null) {
            return cause + ":" + BuiltInRegistries.ENTITY_TYPE.getKey(attacker.getType());
        }
        return cause;
    }

}
