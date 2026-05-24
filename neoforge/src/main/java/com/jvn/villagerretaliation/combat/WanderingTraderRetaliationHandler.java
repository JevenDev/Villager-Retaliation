package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.combat.VillagerRetaliationRetaliationUtil.ActiveRetaliationTarget;
import com.jvn.villagerretaliation.reputation.VillagerAggressionPolicy;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
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
    private static final long NATURAL_TARGET_SCAN_INTERVAL_TICKS = 20L;
    private static final String PERSISTENT_TAG_ROOT = "VillagerRetaliationPersistentTraderHostility";
    private static final VillagerRetaliationRetaliationRuntime<WanderingTrader> RETALIATION =
            new VillagerRetaliationRetaliationRuntime<>(PERSISTENT_TAG_ROOT);
    private static final Map<UUID, Long> NEXT_NATURAL_TARGET_SCAN_TICKS = new HashMap<>();

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
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_RETALIATION.get() || event.getNewDamage() <= 0.0F) {
            return;
        }

        if (event.getEntity() instanceof WanderingTrader trader) {
            VillagerRetaliationVillagerCombatUtil.resolveAttacker(trader, event.getSource()).ifPresent(attacker -> {
                if (!shouldRetaliateAgainstAttacker(attacker)) {
                    return;
                }
                anger(trader, attacker);
                if (!VillagerRetaliationConfig.ATTACK_AGGROS_ONLY_HIT_VILLAGER.get()) {
                    angerNearbyTraders(trader, attacker, VillagerRetaliationConfig.VILLAGER_KILL_AGGRO_RADIUS.get());
                }
            });
            return;
        }

        if (!(event.getEntity() instanceof TraderLlama traderLlama)
                || !(traderLlama.getLeashHolder() instanceof WanderingTrader trader)) {
            return;
        }

        VillagerRetaliationVillagerCombatUtil.resolveAttacker(traderLlama, event.getSource()).ifPresent(attacker -> {
            if (!shouldRetaliateAgainstAttacker(attacker)) {
                return;
            }
            anger(trader, attacker);
            if (!VillagerRetaliationConfig.ATTACK_AGGROS_ONLY_HIT_VILLAGER.get()) {
                angerNearbyTraders(trader, attacker, VillagerRetaliationConfig.VILLAGER_KILL_AGGRO_RADIUS.get());
            }
        });
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof WanderingTrader trader)) {
            return;
        }

        clearAnger(trader, false);
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_RETALIATION.get()
                || !VillagerRetaliationConfig.KILLING_VILLAGER_AGGROS_NEARBY_VILLAGERS.get()) {
            return;
        }

        VillagerRetaliationVillagerCombatUtil.resolveAttacker(trader, event.getSource())
                .filter(attacker -> !VillagerRetaliationVillagerCombatUtil.shouldIgnoreAttacker(attacker))
                .filter(WanderingTraderRetaliationHandler::shouldRetaliateAgainstAttacker)
                .ifPresent(attacker -> angerNearbyTraders(trader, attacker, VillagerRetaliationConfig.VILLAGER_KILL_AGGRO_RADIUS.get()));
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

        VillagerRetaliationVillagerCombatUtil.updateSwingAnimation(trader);
        if (trader.level().isClientSide) {
            return;
        }

        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_RETALIATION.get()) {
            clearAnger(trader);
            return;
        }

        RETALIATION.restorePersistedAngerIfNeeded(trader);
        tryAcquireHostileTarget(trader);
        ActiveRetaliationTarget retaliationTarget = VillagerRetaliationRetaliationUtil.resolveActiveRetaliationTarget(
                trader,
                RETALIATION,
                WanderingTraderCombatRoles::canFightBack,
                () -> clearAnger(trader)
        );
        if (retaliationTarget == null) {
            VillagerRangedCombatHelper.clearState(trader);
            VillagerRetaliationRetaliationUtil.restoreCombatMovement(trader);
            RETALIATION.restoreTemporaryWeapon(trader);
            return;
        }

        ServerLevel level = retaliationTarget.level();
        LivingEntity target = retaliationTarget.target();
        long gameTime = retaliationTarget.gameTime();
        double distanceSqr = trader.distanceToSqr(target);
        if (!retaliationTarget.targetCurrentlyHostile()) {
            trader.setAggressive(false);
            trader.setTarget(null);
            VillagerRangedCombatHelper.clearState(trader);
            VillagerRetaliationRetaliationUtil.restoreCombatMovement(trader);
            RETALIATION.restoreTemporaryWeapon(trader);
            trader.getNavigation().stop();
            return;
        }
        if (!VillagerRetaliationRetaliationUtil.isWithinRetaliationPursuitRange(trader, target)) {
            clearAnger(trader);
            return;
        }

        suppressVanillaPanic(trader);
        trader.setAggressive(true);
        trader.setTarget(target);

        if (tryAcquireGroundWeapon(trader, gameTime)) {
            return;
        }

        equipCombatWeapon(trader);

        trader.getLookControl().setLookAt(target, 30.0F, 30.0F);
        VillagerRetaliationRetaliationUtil.boostCombatMovement(trader);

        if (VillagerRetaliationRetaliationUtil.isUsingRangedCombatMode(trader)
                && VillagerRangedCombatHelper.tryAttack(trader, target, level, distanceSqr)) {
            return;
        }

        VillagerRetaliationRetaliationUtil.moveTowardReachableRetaliationTarget(trader, target, WanderingTraderCombatRoles.movementSpeed(trader));
        if (VillagerRetaliationRetaliationUtil.canUseMeleeCombatMode(trader)
                && VillagerRetaliationRetaliationUtil.canMeleeHit(trader, target)
                && RETALIATION.isAttackReady(trader, gameTime)) {
            var attackHand = VillagerRetaliationVillagerCombatUtil.selectAttackHand(trader);
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

        if (!RETALIATION.isHostileTowards(trader, player, () -> clearAnger(trader))
                && !isDespisedBy(trader, player)) {
            return false;
        }

        VillagerRetaliationRetaliationUtil.spawnMadParticles(trader);
        return true;
    }

    public static boolean tryPacifyWithEmeralds(WanderingTrader trader, Player player, ItemStack interactionStack) {
        return tryPacifyWithPayment(trader, player, interactionStack);
    }

    public static boolean tryPacifyWithPayment(WanderingTrader trader, Player player, ItemStack interactionStack) {
        if (trader.level().isClientSide
                || !trader.isAlive()
                || !player.isAlive()
                || !RETALIATION.isHostileTowards(trader, player, () -> clearAnger(trader))) {
            return false;
        }

        Optional<PacifyPaymentOffer> payment = VillagerPacifyPaymentResources.offerFor(trader, interactionStack);
        if (payment.isEmpty()) {
            return false;
        }

        if (isPacificationBlockedByReputation(trader, player)) {
            VillagerRetaliationRetaliationUtil.spawnMadParticles(trader);
            return true;
        }

        if (interactionStack.getCount() < payment.get().count()) {
            VillagerRetaliationRetaliationUtil.spawnPacifyFailureParticles(trader);
            return true;
        }

        if (!player.hasInfiniteMaterials()) {
            interactionStack.shrink(payment.get().count());
        }
        clearAnger(trader);
        VillagerRetaliationRetaliationUtil.spawnPacifySuccessParticles(trader);
        return true;
    }

    private static boolean isDespisedBy(WanderingTrader trader, Player player) {
        return VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()
                && trader.level() instanceof ServerLevel level
                && VillagerReputationManager.isDespised(level, trader, player);
    }

    private static boolean isPacificationBlockedByReputation(WanderingTrader trader, Player player) {
        return VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()
                && trader.level() instanceof ServerLevel level
                && (VillagerReputationManager.isDespised(level, trader, player)
                || VillagerReputationManager.isFeared(level, trader, player));
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

        long gameTime = trader.level().getGameTime();
        if (gameTime < NEXT_NATURAL_TARGET_SCAN_TICKS.getOrDefault(trader.getUUID(), 0L)) {
            return;
        }

        NEXT_NATURAL_TARGET_SCAN_TICKS.put(trader.getUUID(), gameTime + NATURAL_TARGET_SCAN_INTERVAL_TICKS);
        if (tryAcquireReputationTarget(trader)) {
            return;
        }
        if (!VillagerRetaliationConfig.WANDERING_TRADERS_TARGET_HOSTILE_MOBS.get()) {
            return;
        }

        Optional<LivingEntity> memoryTarget = VillagerRetaliationVillagerCombatUtil.findNaturalHostileMemoryTarget(trader);
        if (memoryTarget.isPresent()) {
            anger(trader, memoryTarget.get());
            return;
        }

        double naturalDefenseRadius = VillagerRetaliationConfig.NATURAL_HOSTILE_TARGET_RADIUS.get();
        VillagerRetaliationVillagerCombatUtil.findNearestNaturalHostile(trader, naturalDefenseRadius)
                .ifPresent(target -> anger(trader, target));
    }

    private static boolean tryAcquireReputationTarget(WanderingTrader trader) {
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()
                || !VillagerRetaliationConfig.ENABLE_DESPISED_KILL_ON_SIGHT.get()
                || !(trader.level() instanceof ServerLevel level)) {
            return false;
        }

        double radius = VillagerRetaliationConfig.DESPISED_SIGHT_RADIUS.get();
        AABB area = trader.getBoundingBox().inflate(radius);
        for (Player player : level.getEntitiesOfClass(Player.class, area)) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
                continue;
            }
            if (!trader.hasLineOfSight(player)) {
                continue;
            }
            if (VillagerAggressionPolicy.shouldAttackOnSight(trader, player)) {
                anger(trader, player);
                return true;
            }
        }

        return false;
    }

    private static boolean tryAcquireGroundWeapon(WanderingTrader trader, long gameTime) {
        if (!trader.isAlive()
                || !WanderingTraderCombatRoles.canScavengeGroundWeapons(trader)
                || VillagerRetaliationVillagerEquipment.isPlayerManagedMainHand(trader)
                || !VillagerRetaliationVillagerCombatUtil.isThreatened(trader)) {
            return false;
        }

        return RETALIATION.tryAcquireGroundWeapon(
                trader,
                WanderingTraderCombatRoles.movementSpeed(trader),
                () -> RETALIATION.discardTemporaryWeapon(trader),
                gameTime
        );
    }

    private static boolean shouldRetaliateAgainstAttacker(LivingEntity attacker) {
        return VillagerRetaliationConfig.WANDERING_TRADERS_RETALIATE_AGAINST_HOSTILE_MOBS.get()
                || !isHostileMobAttacker(attacker);
    }

    private static boolean isHostileMobAttacker(LivingEntity attacker) {
        return attacker instanceof net.minecraft.world.entity.monster.Enemy
                && !(attacker instanceof net.minecraft.world.entity.NeutralMob);
    }

    private static void clearAnger(WanderingTrader trader) {
        clearAnger(trader, true);
    }

    private static void clearAnger(WanderingTrader trader, boolean restoreWeapon) {
        RETALIATION.clearPersistentAnger(trader);
        NEXT_NATURAL_TARGET_SCAN_TICKS.remove(trader.getUUID());
        VillagerRangedCombatHelper.clearState(trader);
        VillagerRetaliationRetaliationUtil.restoreCombatMovement(trader);
        if (restoreWeapon) {
            RETALIATION.restoreTemporaryWeapon(trader);
        } else {
            RETALIATION.discardTemporaryWeapon(trader);
        }
        VillagerRetaliationVillagerWeapons.maintainAcquiredWeaponAuthority(trader);
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
            if (nearby != sourceEntity
                    && canWitnessRetaliationEvent(nearby, sourceEntity)
                    && shouldRetaliateAgainstAttacker(attacker)) {
                anger(nearby, attacker);
            }
        }
    }

    private static boolean canWitnessRetaliationEvent(WanderingTrader witness, Entity sourceEntity) {
        return !VillagerRetaliationConfig.RETALIATION_WITNESSES_REQUIRE_LINE_OF_SIGHT.get()
                || witness.hasLineOfSight(sourceEntity);
    }

    private static void suppressVanillaPanic(WanderingTrader trader) {
        VillagerRetaliationVillagerBrainUtil.clearThreatMemories(trader);
    }

    private static void equipCombatWeapon(WanderingTrader trader) {
        if (VillagerRetaliationVillagerWeapons.maintainAcquiredWeaponAuthority(trader)) {
            RETALIATION.discardTemporaryWeapon(trader);
            return;
        }

        if (RETALIATION.maintainTemporaryWeapon(trader)) {
            return;
        }

        if (VillagerRetaliationVillagerWeapons.hasUsableWeapon(trader)
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
        VillagerRetaliationRetaliationUtil.restoreCombatMovement(trader);
        if (trader.isAlive()) {
            RETALIATION.restoreTemporaryWeapon(trader);
        } else {
            RETALIATION.discardTemporaryWeapon(trader);
        }
        RETALIATION.clearTransientState(trader);
        if (trader.isAlive()) {
            VillagerRetaliationVillagerWeapons.clearTrackedPickupCache(trader);
        } else {
            VillagerRetaliationVillagerWeapons.clearTrackedPickup(trader);
        }
        NEXT_NATURAL_TARGET_SCAN_TICKS.remove(trader.getUUID());
    }
}
