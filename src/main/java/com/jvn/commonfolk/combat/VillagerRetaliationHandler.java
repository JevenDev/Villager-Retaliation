package com.jvn.commonfolk.combat;

import com.jvn.commonfolk.config.CommonfolkConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class VillagerRetaliationHandler {
    private static final Map<UUID, AngerTarget> ANGER_TARGETS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_ATTACK_TICKS = new HashMap<>();

    private VillagerRetaliationHandler() {
    }

    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (!CommonfolkConfig.ENABLE_VILLAGER_RETALIATION.get()
                || event.getNewDamage() <= 0.0F
                || !(event.getEntity() instanceof Villager villager)
                || villager.isBaby()) {
            return;
        }

        resolveAttacker(event.getSource()).ifPresent(attacker -> anger(villager, attacker));
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!CommonfolkConfig.ENABLE_VILLAGER_RETALIATION.get()
                || !CommonfolkConfig.KILLING_VILLAGER_AGGROS_NEARBY_VILLAGERS.get()
                || !(event.getEntity() instanceof Villager villager)
                || villager.isBaby()
                || !(villager.level() instanceof ServerLevel level)) {
            return;
        }

        Optional<LivingEntity> attacker = resolveAttacker(event.getSource());
        if (attacker.isEmpty() || shouldIgnoreAttacker(attacker.get())) {
            return;
        }

        double radius = CommonfolkConfig.VILLAGER_KILL_AGGRO_RADIUS.get();
        AABB area = villager.getBoundingBox().inflate(radius);
        for (Villager nearby : level.getEntitiesOfClass(Villager.class, area)) {
            if (nearby != villager && !nearby.isBaby()) {
                anger(nearby, attacker.get());
            }
        }
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager) || villager.level().isClientSide) {
            return;
        }

        AngerTarget angerTarget = ANGER_TARGETS.get(villager.getUUID());
        if (angerTarget == null) {
            return;
        }

        if (!(villager.level() instanceof ServerLevel level) || level.getGameTime() >= angerTarget.expiresAt()) {
            clearAnger(villager);
            return;
        }

        Entity entity = level.getEntity(angerTarget.targetId());
        if (!(entity instanceof LivingEntity target) || !target.isAlive() || shouldIgnoreAttacker(target)) {
            clearAnger(villager);
            return;
        }

        if (!VillagerCombatRoles.canFightBack(villager)) {
            return;
        }

        villager.getLookControl().setLookAt(target, 30.0F, 30.0F);
        villager.getNavigation().moveTo(target, 1.15D);
        if (villager.distanceToSqr(target) <= 4.0D && attackReady(villager, level.getGameTime())) {
            target.hurt(villager.damageSources().mobAttack(villager), VillagerCombatRoles.meleeDamage(villager));
            NEXT_ATTACK_TICKS.put(villager.getUUID(), level.getGameTime() + 20L);
        }
    }

    private static void anger(Villager villager, LivingEntity attacker) {
        if (shouldIgnoreAttacker(attacker) || !villager.isAlive() || villager.isBaby() || attacker == villager) {
            return;
        }

        long expiresAt = villager.level().getGameTime() + CommonfolkConfig.AGGRO_DURATION_TICKS.get();
        ANGER_TARGETS.put(villager.getUUID(), new AngerTarget(attacker.getUUID(), expiresAt));
    }

    private static void clearAnger(Villager villager) {
        ANGER_TARGETS.remove(villager.getUUID());
        NEXT_ATTACK_TICKS.remove(villager.getUUID());
        villager.getNavigation().stop();
    }

    private static Optional<LivingEntity> resolveAttacker(DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity livingEntity) {
            return Optional.of(livingEntity);
        }

        Entity direct = source.getDirectEntity();
        if (direct instanceof LivingEntity livingEntity) {
            return Optional.of(livingEntity);
        }

        return Optional.empty();
    }

    private static boolean shouldIgnoreAttacker(LivingEntity attacker) {
        return attacker instanceof Player player
                && (player.isSpectator()
                || CommonfolkConfig.NEARBY_VILLAGERS_IGNORE_CREATIVE_PLAYERS.get() && player.isCreative());
    }

    private static boolean attackReady(Villager villager, long gameTime) {
        return gameTime >= NEXT_ATTACK_TICKS.getOrDefault(villager.getUUID(), 0L);
    }

    private record AngerTarget(UUID targetId, long expiresAt) {
    }
}
