package com.jvn.commonfolk.combat;

import com.jvn.commonfolk.config.CommonfolkConfig;
import com.jvn.commonfolk.combat.CommonfolkRetaliationUtil.ActiveRetaliationTarget;
import com.jvn.commonfolk.util.CommonfolkVillagerCombatUtil;
import com.jvn.commonfolk.villager.CommonfolkVillagerBrainUtil;
import com.jvn.commonfolk.villager.CommonfolkVillagerWeapons;
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
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class WanderingTraderRetaliationHandler {
    private static final String PERSISTENT_TAG_ROOT = "CommonfolkPersistentTraderHostility";
    private static final CommonfolkRetaliationRuntime<WanderingTrader> RETALIATION =
            new CommonfolkRetaliationRuntime<>(PERSISTENT_TAG_ROOT);

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

        if (RETALIATION.hasAnger(trader)) {
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

        RETALIATION.restorePersistedAngerIfNeeded(trader);
        tryAcquireHostileTarget(trader);
        ActiveRetaliationTarget retaliationTarget = CommonfolkRetaliationUtil.resolveActiveRetaliationTarget(
                trader,
                RETALIATION,
                WanderingTraderCombatRoles::canFightBack,
                () -> clearAnger(trader)
        );
        if (retaliationTarget == null) {
            VillagerRangedCombatHelper.clearState(trader);
            CommonfolkRetaliationUtil.restoreCombatMovement(trader);
            RETALIATION.restoreTemporaryWeapon(trader);
            return;
        }

        ServerLevel level = retaliationTarget.level();
        LivingEntity target = retaliationTarget.target();
        long gameTime = retaliationTarget.gameTime();
        if (!retaliationTarget.targetCurrentlyHostile()) {
            trader.setAggressive(false);
            trader.setTarget(null);
            VillagerRangedCombatHelper.clearState(trader);
            CommonfolkRetaliationUtil.restoreCombatMovement(trader);
            RETALIATION.restoreTemporaryWeapon(trader);
            trader.getNavigation().stop();
            return;
        }

        suppressVanillaPanic(trader);
        trader.setAggressive(true);
        trader.setTarget(target);

        if (tryAcquireGroundWeapon(trader, gameTime)) {
            return;
        }

        equipCombatWeapon(trader);

        double distanceSqr = trader.distanceToSqr(target);
        trader.getLookControl().setLookAt(target, 30.0F, 30.0F);
        CommonfolkRetaliationUtil.boostCombatMovement(trader);

        if (CommonfolkRetaliationUtil.isUsingRangedCombatMode(trader)
                && VillagerRangedCombatHelper.tryAttack(trader, target, level, distanceSqr)) {
            return;
        }

        trader.getNavigation().moveTo(target, WanderingTraderCombatRoles.movementSpeed(trader));
        if (CommonfolkRetaliationUtil.canUseMeleeCombatMode(trader)
                && CommonfolkRetaliationUtil.canMeleeHit(trader, target)
                && RETALIATION.isAttackReady(trader, gameTime)) {
            var attackHand = CommonfolkVillagerCombatUtil.selectAttackHand(trader);
            trader.swing(attackHand, true);
            syncMeleeAttackAttributes(trader);
            trader.doHurtTarget(target);
            RETALIATION.setNextAttackTick(trader, gameTime + WanderingTraderCombatRoles.attackCooldown(trader));
        }
    }

    public static boolean blockTradingIfHostile(WanderingTrader trader, Player player) {
        if (trader.level().isClientSide || !trader.isAlive() || !player.isAlive()) {
            return false;
        }

        if (!RETALIATION.isHostileTowards(trader, player, () -> clearAnger(trader))) {
            return false;
        }

        CommonfolkRetaliationUtil.spawnMadParticles(trader);
        return true;
    }

    public static void angerNearbyTradersFrom(Entity sourceEntity, LivingEntity attacker, double radius) {
        angerNearbyTraders(sourceEntity, attacker, radius);
    }

    private static void anger(WanderingTrader trader, LivingEntity attacker) {
        RETALIATION.anger(trader, attacker);
    }

    private static void tryAcquireHostileTarget(WanderingTrader trader) {
        if (RETALIATION.hasAnger(trader)
                || !trader.isAlive()
                || !WanderingTraderCombatRoles.canFightBack(trader)) {
            return;
        }

        CommonfolkVillagerCombatUtil.getMemoryIfRegistered(trader, MemoryModuleType.NEAREST_HOSTILE)
                .filter(LivingEntity::isAlive)
                .filter(target -> target != trader)
                .ifPresent(target -> anger(trader, target));
    }

    private static boolean tryAcquireGroundWeapon(WanderingTrader trader, long gameTime) {
        if (!trader.isAlive()
                || !WanderingTraderCombatRoles.canScavengeGroundWeapons(trader)
                || CommonfolkVillagerWeapons.hasTrackedPickup(trader)
                || CommonfolkVillagerWeapons.hasUsableWeapon(trader)
                || !CommonfolkVillagerCombatUtil.isThreatened(trader)) {
            return false;
        }

        return RETALIATION.tryAcquireGroundWeapon(
                trader,
                WanderingTraderCombatRoles.movementSpeed(trader),
                () -> RETALIATION.discardTemporaryWeapon(trader),
                gameTime
        );
    }

    private static void clearAnger(WanderingTrader trader) {
        clearAnger(trader, true);
    }

    private static void clearAnger(WanderingTrader trader, boolean restoreWeapon) {
        RETALIATION.clearPersistentAnger(trader);
        VillagerRangedCombatHelper.clearState(trader);
        CommonfolkRetaliationUtil.restoreCombatMovement(trader);
        if (restoreWeapon) {
            RETALIATION.restoreTemporaryWeapon(trader);
        } else {
            RETALIATION.discardTemporaryWeapon(trader);
        }
        RETALIATION.clearTransientState(trader);
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
        CommonfolkVillagerBrainUtil.clearThreatMemories(trader);
    }

    private static void equipCombatWeapon(WanderingTrader trader) {
        if (CommonfolkVillagerWeapons.maintainAcquiredWeaponAuthority(trader)) {
            RETALIATION.discardTemporaryWeapon(trader);
            return;
        }

        if (RETALIATION.maintainTemporaryWeapon(trader)) {
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

        RETALIATION.equipTemporaryWeapon(trader, weapon);
    }

    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof WanderingTrader trader)) {
            return;
        }

        VillagerRangedCombatHelper.clearState(trader);
        CommonfolkRetaliationUtil.restoreCombatMovement(trader);
        if (trader.isAlive()) {
            RETALIATION.restoreTemporaryWeapon(trader);
        } else {
            RETALIATION.discardTemporaryWeapon(trader);
        }
        RETALIATION.clearTransientState(trader);
        if (trader.isAlive()) {
            CommonfolkVillagerWeapons.clearTrackedPickupCache(trader);
        } else {
            CommonfolkVillagerWeapons.clearTrackedPickup(trader);
        }
    }
}
