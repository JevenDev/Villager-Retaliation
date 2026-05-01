package com.jvn.commonfolk.combat;

import com.jvn.commonfolk.config.CommonfolkConfig;
import com.jvn.commonfolk.combat.CommonfolkRetaliationUtil.AngerTarget;
import com.jvn.commonfolk.combat.CommonfolkRetaliationUtil.TemporaryWeaponState;
import com.jvn.commonfolk.util.CommonfolkVillagerCombatUtil;
import com.jvn.commonfolk.villager.CommonfolkVillagerWeapons;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class WanderingTraderRetaliationHandler {
    private static final String PERSISTENT_TAG_ROOT = "CommonfolkPersistentTraderHostility";
    private static final String PERSISTENT_TARGET_UUID = "Target";
    private static final String PERSISTENT_LAST_SEEN_TICK = "LastSeenTick";
    private static final Map<UUID, AngerTarget> ANGER_TARGETS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_ATTACK_TICKS = new HashMap<>();
    private static final Map<UUID, Double> ORIGINAL_MOVEMENT_SPEEDS = new HashMap<>();
    private static final Map<UUID, TemporaryWeaponState> TEMPORARY_WEAPONS = new HashMap<>();

    private WanderingTraderRetaliationHandler() {
    }

    public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
        if (!event.has(EntityType.WANDERING_TRADER, Attributes.ATTACK_DAMAGE)) {
            event.add(EntityType.WANDERING_TRADER, Attributes.ATTACK_DAMAGE, VillagerCombatRoles.PLAYER_FIST_DAMAGE);
        }
        if (!event.has(EntityType.WANDERING_TRADER, Attributes.ATTACK_KNOCKBACK)) {
            event.add(EntityType.WANDERING_TRADER, Attributes.ATTACK_KNOCKBACK, 0.0D);
        }
    }

    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (!CommonfolkConfig.ENABLE_VILLAGER_RETALIATION.get() || event.getNewDamage() <= 0.0F) {
            return;
        }

        if (event.getEntity() instanceof WanderingTrader trader) {
            CommonfolkVillagerCombatUtil.resolveAttacker(event.getSource()).ifPresent(attacker -> {
                anger(trader, attacker);
                if (!CommonfolkConfig.ATTACK_AGGROS_ONLY_HIT_VILLAGER.get()) {
                    angerNearbyTraders(trader, attacker, CommonfolkConfig.VILLAGER_KILL_AGGRO_RADIUS.get());
                }
            });
            return;
        }

        if (!(event.getEntity() instanceof TraderLlama traderLlama)
                || !(traderLlama.getLeashHolder() instanceof WanderingTrader trader)) {
            return;
        }

        CommonfolkVillagerCombatUtil.resolveAttacker(event.getSource()).ifPresent(attacker -> {
            anger(trader, attacker);
            if (!CommonfolkConfig.ATTACK_AGGROS_ONLY_HIT_VILLAGER.get()) {
                angerNearbyTraders(trader, attacker, CommonfolkConfig.VILLAGER_KILL_AGGRO_RADIUS.get());
            }
        });
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof WanderingTrader trader)) {
            return;
        }

        clearAnger(trader, false);
        if (!CommonfolkConfig.ENABLE_VILLAGER_RETALIATION.get()
                || !CommonfolkConfig.KILLING_VILLAGER_AGGROS_NEARBY_VILLAGERS.get()) {
            return;
        }

        CommonfolkVillagerCombatUtil.resolveAttacker(event.getSource())
                .filter(attacker -> !CommonfolkVillagerCombatUtil.shouldIgnoreAttacker(attacker))
                .ifPresent(attacker -> angerNearbyTraders(trader, attacker, CommonfolkConfig.VILLAGER_KILL_AGGRO_RADIUS.get()));
    }

    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof WanderingTrader trader) || trader.level().isClientSide) {
            return;
        }

        if (ANGER_TARGETS.containsKey(trader.getUUID())) {
            suppressVanillaPanic(trader);
        }
    }

    public static void onEntityTickPost(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof WanderingTrader trader)) {
            return;
        }

        CommonfolkVillagerCombatUtil.updateSwingAnimation(trader);
        if (trader.level().isClientSide) {
            return;
        }

        if (!CommonfolkConfig.ENABLE_VILLAGER_RETALIATION.get()) {
            clearAnger(trader);
            return;
        }

        restorePersistedAngerIfNeeded(trader);
        tryAcquireHostileTarget(trader);

        AngerTarget angerTarget = ANGER_TARGETS.get(trader.getUUID());
        if (angerTarget == null) {
            VillagerRangedCombatHelper.clearState(trader);
            CommonfolkRetaliationUtil.restoreCombatMovement(trader, ORIGINAL_MOVEMENT_SPEEDS);
            CommonfolkRetaliationUtil.restoreTemporaryWeapon(trader, TEMPORARY_WEAPONS);
            return;
        }

        if (!(trader.level() instanceof ServerLevel level)) {
            clearAnger(trader);
            return;
        }

        long gameTime = level.getGameTime();
        Entity entity = level.getEntity(angerTarget.targetId());
        if (!(entity instanceof LivingEntity target)) {
            if (gameTime - angerTarget.lastSeenGameTick() >= CommonfolkConfig.AGGRO_DURATION_TICKS.get()) {
                clearAnger(trader);
            }
            return;
        }
        if (!target.isAlive() || CommonfolkVillagerCombatUtil.shouldIgnoreAttacker(target)) {
            clearAnger(trader);
            return;
        }

        if (!WanderingTraderCombatRoles.canFightBack(trader)) {
            clearAnger(trader);
            return;
        }

        suppressVanillaPanic(trader);
        trader.setAggressive(true);
        trader.setTarget(target);

        if (trader.hasLineOfSight(target)) {
            CommonfolkRetaliationUtil.refreshAngerTarget(trader, angerTarget, gameTime, ANGER_TARGETS, PERSISTENT_TAG_ROOT);
        } else if (gameTime - angerTarget.lastSeenGameTick() >= CommonfolkConfig.AGGRO_DURATION_TICKS.get()) {
            clearAnger(trader);
            return;
        }

        if (tryAcquireGroundWeapon(trader)) {
            return;
        }

        equipCombatWeapon(trader);

        double distanceSqr = trader.distanceToSqr(target);
        trader.getLookControl().setLookAt(target, 30.0F, 30.0F);
        CommonfolkRetaliationUtil.boostCombatMovement(trader, ORIGINAL_MOVEMENT_SPEEDS);

        if (CommonfolkRetaliationUtil.isUsingRangedCombatMode(trader)
                && VillagerRangedCombatHelper.tryAttack(trader, target, level, distanceSqr)) {
            return;
        }

        trader.getNavigation().moveTo(target, WanderingTraderCombatRoles.movementSpeed(trader));
        if (CommonfolkRetaliationUtil.canUseMeleeCombatMode(trader)
                && CommonfolkRetaliationUtil.canMeleeHit(trader, target)
                && CommonfolkRetaliationUtil.isAttackReady(trader, NEXT_ATTACK_TICKS, gameTime)) {
            var attackHand = CommonfolkVillagerCombatUtil.selectAttackHand(trader);
            trader.swing(attackHand, true);
            syncMeleeAttackAttributes(trader);
            trader.doHurtTarget(target);
            NEXT_ATTACK_TICKS.put(trader.getUUID(), gameTime + WanderingTraderCombatRoles.attackCooldown(trader));
        }
    }

    public static boolean blockTradingIfHostile(WanderingTrader trader, Player player) {
        if (trader.level().isClientSide || !trader.isAlive() || !player.isAlive()) {
            return false;
        }

        if (!CommonfolkRetaliationUtil.isHostileTowards(trader, player, ANGER_TARGETS, PERSISTENT_TAG_ROOT, () -> clearAnger(trader))) {
            return false;
        }

        CommonfolkRetaliationUtil.spawnMadParticles(trader);
        return true;
    }

    private static void anger(WanderingTrader trader, LivingEntity attacker) {
        CommonfolkRetaliationUtil.tryAnger(trader, attacker, ANGER_TARGETS, PERSISTENT_TAG_ROOT);
    }

    private static void tryAcquireHostileTarget(WanderingTrader trader) {
        if (ANGER_TARGETS.containsKey(trader.getUUID())
                || !trader.isAlive()
                || !WanderingTraderCombatRoles.canFightBack(trader)) {
            return;
        }

        CommonfolkVillagerCombatUtil.getMemoryIfRegistered(trader, MemoryModuleType.NEAREST_HOSTILE)
                .filter(LivingEntity::isAlive)
                .filter(target -> target != trader)
                .ifPresent(target -> anger(trader, target));
    }

    private static boolean tryAcquireGroundWeapon(WanderingTrader trader) {
        if (!trader.isAlive()
                || !WanderingTraderCombatRoles.canScavengeGroundWeapons(trader)
                || CommonfolkVillagerWeapons.hasUsableWeapon(trader)
                || !CommonfolkVillagerCombatUtil.isThreatened(trader)) {
            return false;
        }

        return CommonfolkRetaliationUtil.tryAcquireGroundWeapon(
                trader,
                WanderingTraderCombatRoles.movementSpeed(trader),
                () -> CommonfolkRetaliationUtil.discardTemporaryWeapon(trader, TEMPORARY_WEAPONS)
        );
    }

    private static void clearAnger(WanderingTrader trader) {
        clearAnger(trader, true);
    }

    private static void clearAnger(WanderingTrader trader, boolean restoreWeapon) {
        ANGER_TARGETS.remove(trader.getUUID());
        clearPersistedAnger(trader);
        NEXT_ATTACK_TICKS.remove(trader.getUUID());
        VillagerRangedCombatHelper.clearState(trader);
        CommonfolkRetaliationUtil.restoreCombatMovement(trader, ORIGINAL_MOVEMENT_SPEEDS);
        if (restoreWeapon) {
            CommonfolkRetaliationUtil.restoreTemporaryWeapon(trader, TEMPORARY_WEAPONS);
        } else {
            TEMPORARY_WEAPONS.remove(trader.getUUID());
        }
        trader.setAggressive(false);
        trader.setTarget(null);
        trader.getNavigation().stop();
    }

    private static void syncMeleeAttackAttributes(WanderingTrader trader) {
        AttributeInstance attackDamage = trader.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage == null) {
            return;
        }

        double desiredBaseDamage = WanderingTraderCombatRoles.meleeAttackDamageBase(trader);
        if (attackDamage.getBaseValue() != desiredBaseDamage) {
            attackDamage.setBaseValue(desiredBaseDamage);
        }
    }

    private static void angerNearbyTraders(Entity sourceEntity, LivingEntity attacker, double radius) {
        if (!(sourceEntity.level() instanceof ServerLevel level)) {
            return;
        }

        AABB area = sourceEntity.getBoundingBox().inflate(radius);
        for (WanderingTrader nearby : level.getEntitiesOfClass(WanderingTrader.class, area)) {
            if (nearby != sourceEntity) {
                anger(nearby, attacker);
            }
        }
    }

    private static void suppressVanillaPanic(WanderingTrader trader) {
        CommonfolkVillagerCombatUtil.eraseMemoryIfRegistered(trader, MemoryModuleType.HURT_BY);
        CommonfolkVillagerCombatUtil.eraseMemoryIfRegistered(trader, MemoryModuleType.HURT_BY_ENTITY);
        CommonfolkVillagerCombatUtil.eraseMemoryIfRegistered(trader, MemoryModuleType.NEAREST_HOSTILE);
    }

    private static void restorePersistedAngerIfNeeded(WanderingTrader trader) {
        CommonfolkRetaliationUtil.restorePersistedAngerIfNeeded(trader, ANGER_TARGETS, PERSISTENT_TAG_ROOT);
    }

    private static void clearPersistedAnger(WanderingTrader trader) {
        CommonfolkRetaliationUtil.clearPersistentAnger(trader, PERSISTENT_TAG_ROOT);
    }

    private static void equipCombatWeapon(WanderingTrader trader) {
        if (CommonfolkVillagerWeapons.maintainAcquiredWeaponAuthority(trader)) {
            CommonfolkRetaliationUtil.discardTemporaryWeapon(trader, TEMPORARY_WEAPONS);
            return;
        }

        if (CommonfolkRetaliationUtil.maintainTemporaryWeapon(trader, TEMPORARY_WEAPONS)) {
            return;
        }

        if (CommonfolkVillagerWeapons.hasUsableWeapon(trader)
                || !WanderingTraderCombatRoles.canUseTemporaryCombatLoadout(trader)) {
            return;
        }

        ItemStack weapon = WanderingTraderCombatRoles.preferredWeapon(trader);
        if (weapon.isEmpty()) {
            return;
        }

        CommonfolkRetaliationUtil.equipTemporaryWeapon(trader, TEMPORARY_WEAPONS, weapon);
    }
}
