package com.jvn.villagerretaliation.social;

import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.village.VillageEventMemory;
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

    private VillagerSocialGraphService() {
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof Villager villager) {
            VillagerSocialGraphSavedData.get(level).ensureProfile(level, villager);
        }
    }

    public static void onEntityTickPost(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager)
                || !(villager.level() instanceof ServerLevel level)
                || level.getGameTime() % PROFILE_REFRESH_INTERVAL_TICKS
                != Math.floorMod(villager.getUUID().getLeastSignificantBits(), PROFILE_REFRESH_INTERVAL_TICKS)) {
            return;
        }
        VillagerSocialGraphSavedData.get(level).ensureProfile(level, villager);
    }

    public static void onBabyEntitySpawn(BabyEntitySpawnEvent event) {
        Mob parentA = event.getParentA();
        Mob parentB = event.getParentB();
        if (!(parentA instanceof Villager parentVillagerA)
                || !(parentB instanceof Villager parentVillagerB)
                || !(parentA.level() instanceof ServerLevel level)) {
            return;
        }

        VillagerSocialGraphSavedData socialGraph = VillagerSocialGraphSavedData.get(level);
        VillagerSocialGraphSavedData.BreedingValidation validation = socialGraph.validateBreedingPair(level, parentVillagerA, parentVillagerB);
        if (!validation.allowed()) {
            event.setCanceled(true);
            if (event.getCausedByPlayer() instanceof ServerPlayer player) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(validation.reason()), true);
            }
            return;
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
        if (event.getEntity() instanceof Villager villager && villager.level() instanceof ServerLevel level) {
            VillagerSocialGraphSavedData.get(level).markDead(level, villager, deathCause(event.getSource()));
        }
    }

    public static void onLivingConversionPost(LivingConversionEvent.Post event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
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
        return VillagerSocialGraphSavedData.get(level).familySnapshot(level, villager);
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
