package com.jvn.commonfolk.combat;

import com.jvn.commonfolk.config.CommonfolkConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class VillagerRetaliationHandler {
    private static final Map<UUID, AngerTarget> ANGER_TARGETS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_ATTACK_TICKS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_SPECIAL_TICKS = new HashMap<>();

    private VillagerRetaliationHandler() {
    }

    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (!CommonfolkConfig.ENABLE_VILLAGER_RETALIATION.get()
                || event.getNewDamage() <= 0.0F
                || !(event.getEntity() instanceof Villager villager)
                || villager.isBaby()) {
            return;
        }

        if (VillagerCombatRoles.isArmorer(villager) && CommonfolkConfig.ARMORERS_FIGHT_BACK.get()) {
            villager.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 0));
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
        clearAnger(villager);

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

    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Villager villager) || villager.level().isClientSide) {
            return;
        }

        if (ANGER_TARGETS.containsKey(villager.getUUID())) {
            suppressVanillaPanic(villager);
        }
    }

    public static void onEntityTickPost(EntityTickEvent.Post event) {
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

        suppressVanillaPanic(villager);
        villager.setAggressive(true);
        villager.setChasing(true);
        villager.setTarget(target);
        if (villager.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            villager.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.75D);
        }

        handleDefensiveRole(villager, level.getGameTime());

        double distanceSqr = villager.distanceToSqr(target);
        villager.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (tryFletcherShot(villager, target, level, distanceSqr)) {
            return;
        }

        villager.getNavigation().moveTo(target, VillagerCombatRoles.movementSpeed(villager));
        if (canMeleeHit(villager, target) && attackReady(villager, level.getGameTime())) {
            villager.swing(selectAttackHand(villager));
            target.hurt(villager.damageSources().mobAttack(villager), VillagerCombatRoles.meleeDamage(villager));
            NEXT_ATTACK_TICKS.put(villager.getUUID(), level.getGameTime() + VillagerCombatRoles.attackCooldown(villager));
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
        NEXT_SPECIAL_TICKS.remove(villager.getUUID());
        villager.setAggressive(false);
        villager.setChasing(false);
        villager.setTarget(null);
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

    private static boolean canMeleeHit(Villager villager, LivingEntity target) {
        // Use hitbox-based reach so contact is consistent regardless of center-point offsets.
        return villager.getBoundingBox().inflate(1.0D).intersects(target.getBoundingBox());
    }

    private static void suppressVanillaPanic(Villager villager) {
        villager.getBrain().eraseMemory(MemoryModuleType.HURT_BY);
        villager.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
        villager.getBrain().eraseMemory(MemoryModuleType.NEAREST_HOSTILE);
    }

    private static void handleDefensiveRole(Villager villager, long gameTime) {
        if (gameTime < NEXT_SPECIAL_TICKS.getOrDefault(villager.getUUID(), 0L)) {
            return;
        }

        if (VillagerCombatRoles.isFarmer(villager)
                && CommonfolkConfig.FARMERS_USE_BREAD.get()
                && villager.getHealth() < villager.getMaxHealth() * 0.6F) {
            villager.heal(4.0F);
            NEXT_SPECIAL_TICKS.put(villager.getUUID(), gameTime + 120L);
        } else if (VillagerCombatRoles.isCleric(villager)
                && CommonfolkConfig.CLERICS_USE_POTIONS.get()
                && villager.getHealth() < villager.getMaxHealth() * 0.7F) {
            villager.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
            NEXT_SPECIAL_TICKS.put(villager.getUUID(), gameTime + 180L);
        }
    }

    private static boolean tryFletcherShot(Villager villager, LivingEntity target, ServerLevel level, double distanceSqr) {
        if (!VillagerCombatRoles.isFletcher(villager)
                || !CommonfolkConfig.FLETCHERS_FIGHT_BACK.get()
                || distanceSqr < 16.0D
                || distanceSqr > 225.0D
                || !villager.hasLineOfSight(target)
                || level.getGameTime() < NEXT_SPECIAL_TICKS.getOrDefault(villager.getUUID(), 0L)) {
            return false;
        }

        villager.getNavigation().stop();
        Arrow arrow = new Arrow(level, villager, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
        arrow.setBaseDamage(2.0D);
        double dx = target.getX() - villager.getX();
        double dy = target.getEyeY() - arrow.getY();
        double dz = target.getZ() - villager.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizontal * 0.2D, dz, 1.6F, 8.0F);
        level.addFreshEntity(arrow);
        villager.swing(selectAttackHand(villager));
        NEXT_SPECIAL_TICKS.put(villager.getUUID(), level.getGameTime() + 35L);
        return true;
    }

    private static InteractionHand selectAttackHand(Villager villager) {
        return villager.getMainHandItem().isEmpty() && !villager.getOffhandItem().isEmpty()
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
    }

    private record AngerTarget(UUID targetId, long expiresAt) {
    }
}
