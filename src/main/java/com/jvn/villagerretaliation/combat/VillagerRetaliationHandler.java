package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.combat.VillagerRetaliationRetaliationUtil.ActiveRetaliationTarget;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerRules;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class VillagerRetaliationHandler {
    private static final String PERSISTENT_TAG_ROOT = "VillagerRetaliationPersistentHostility";
    private static final VillagerRetaliationRetaliationRuntime<Villager> RETALIATION =
            new VillagerRetaliationRetaliationRuntime<>(PERSISTENT_TAG_ROOT);
    private static final Map<java.util.UUID, Long> NEXT_SPECIAL_TICKS = new java.util.HashMap<>();

    private VillagerRetaliationHandler() {
    }

    public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
        if (!event.has(EntityType.VILLAGER, Attributes.ATTACK_DAMAGE)) {
            event.add(EntityType.VILLAGER, Attributes.ATTACK_DAMAGE, VillagerCombatRoles.PLAYER_FIST_DAMAGE);
        }
        if (!event.has(EntityType.VILLAGER, Attributes.ATTACK_KNOCKBACK)) {
            event.add(EntityType.VILLAGER, Attributes.ATTACK_KNOCKBACK, 0.0D);
        }
    }

    public static void onLivingDamagePre(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }
        if (!VillagerCombatRoles.isCleric(villager) || !VillagerRetaliationConfig.CLERICS_USE_POTIONS.get()) {
            return;
        }

        float damage = event.getAmount();
        DamageSource source = event.getSource();
        if (source.getEntity() == villager) {
            event.setAmount(0.0F);
            return;
        }
        if (source.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
            event.setAmount(damage * 0.15F);
        }
    }

    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_RETALIATION.get()
                || event.getNewDamage() <= 0.0F) {
            return;
        }

        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }

        if (villager.isBaby()) {
            VillagerRetaliationVillagerCombatUtil.resolveAttacker(event.getSource()).ifPresent(attacker ->
                    rallyNearbyVillagers(villager, attacker, VillagerRetaliationConfig.VILLAGER_KILL_AGGRO_RADIUS.get()));
            return;
        }

        if (VillagerCombatRoles.isArmorer(villager) && VillagerRetaliationConfig.ARMORERS_FIGHT_BACK.get()) {
            villager.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 0));
        }

        VillagerRetaliationVillagerCombatUtil.resolveAttacker(event.getSource()).ifPresent(attacker -> {
            if (isNitwitAlarm(villager)) {
                rallyNearbyVillagers(villager, attacker, VillagerRetaliationConfig.VILLAGER_KILL_AGGRO_RADIUS.get());
                return;
            }

            anger(villager, attacker);
            if (!VillagerRetaliationConfig.ATTACK_AGGROS_ONLY_HIT_VILLAGER.get()) {
                angerNearbyVillagers(villager, attacker, VillagerRetaliationConfig.VILLAGER_KILL_AGGRO_RADIUS.get());
            }
        });
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        Entity deceased = event.getEntity();
        boolean deceasedIsVillager = deceased instanceof Villager;
        if (deceased instanceof Villager villager) {
            // Keep temporary combat weapons equipped on death so vanilla equipment drops can roll.
            clearAnger(villager, false);
        } else if (!(deceased instanceof WanderingTrader)) {
            return;
        }

        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_RETALIATION.get()
                || !VillagerRetaliationConfig.KILLING_VILLAGER_AGGROS_NEARBY_VILLAGERS.get()
                || !(deceased.level() instanceof ServerLevel level)) {
            return;
        }
        if (deceased instanceof Villager villager && villager.isBaby()) {
            return;
        }

        Optional<LivingEntity> attacker = VillagerRetaliationVillagerCombatUtil.resolveAttacker(event.getSource());
        if (deceasedIsVillager) {
            triggerNitwitWitnessedDeathFlee(deceased, attacker.orElse(null), VillagerRetaliationConfig.VILLAGER_KILL_AGGRO_RADIUS.get());
        }

        if (attacker.isEmpty() || VillagerRetaliationVillagerCombatUtil.shouldIgnoreAttacker(attacker.get())) {
            return;
        }

        LivingEntity resolvedAttacker = attacker.get();
        double radius = VillagerRetaliationConfig.VILLAGER_KILL_AGGRO_RADIUS.get();
        angerNearbyVillagers(deceased, resolvedAttacker, radius);
        WanderingTraderRetaliationHandler.angerNearbyTradersFrom(deceased, resolvedAttacker, radius);
        rallyFromNearbyNitwits(deceased, resolvedAttacker, radius);
    }

    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Villager villager) || villager.level().isClientSide) {
            return;
        }

        if (RETALIATION.hasAnger(villager) && VillagerRetaliationVillagerRules.shouldSuppressFleeingBehavior(villager)) {
            suppressVanillaPanic(villager);
        }
    }

    public static void onEntityTickPost(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }

        VillagerRetaliationVillagerCombatUtil.updateSwingAnimation(villager);
        if (villager.level().isClientSide) {
            return;
        }

        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_RETALIATION.get()) {
            clearAnger(villager);
            handlePassivePotionState(villager);
            return;
        }

        RETALIATION.restorePersistedAngerIfNeeded(villager);
        tryAcquireHostileTarget(villager);

        ActiveRetaliationTarget retaliationTarget = VillagerRetaliationRetaliationUtil.resolveActiveRetaliationTarget(
                villager,
                RETALIATION,
                VillagerCombatRoles::canFightBack,
                () -> clearAnger(villager)
        );
        if (retaliationTarget == null) {
            handlePassivePotionState(villager);
            return;
        }

        ServerLevel level = retaliationTarget.level();
        LivingEntity target = retaliationTarget.target();
        long gameTime = retaliationTarget.gameTime();
        if (!retaliationTarget.targetCurrentlyHostile()) {
            villager.setAggressive(false);
            villager.setChasing(false);
            villager.setTarget(null);
            handlePassivePotionState(villager);
            villager.getNavigation().stop();
            return;
        }

        suppressVanillaPanic(villager);
        villager.setAggressive(true);
        villager.setChasing(true);
        villager.setTarget(target);

        double distanceSqr = villager.distanceToSqr(target);
        villager.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (VillagerClericPotionHelper.tickDrinkingIfActive(villager)) {
            villager.getNavigation().stop();
            return;
        }

        if (tryAcquireGroundWeapon(villager, gameTime)) {
            return;
        }

        equipCombatWeapon(villager);
        VillagerRetaliationRetaliationUtil.boostCombatMovement(villager);

        handleDefensiveRole(villager, gameTime);

        if (VillagerClericPotionHelper.tryCombat(villager, target, level, distanceSqr)) {
            return;
        }
        if (VillagerRetaliationRetaliationUtil.isUsingRangedCombatMode(villager)
                && VillagerRangedCombatHelper.tryAttack(villager, target, level, distanceSqr)) {
            return;
        }

        if (VillagerRetaliationPotionUtil.shouldSuppressCombatWhileUsingPotion(villager)) {
            villager.getNavigation().stop();
            return;
        }

        villager.getNavigation().moveTo(target, VillagerCombatRoles.movementSpeed(villager));
        if (VillagerRetaliationRetaliationUtil.canUseMeleeCombatMode(villager)
                && VillagerRetaliationRetaliationUtil.canMeleeHit(villager, target)
                && RETALIATION.isAttackReady(villager, gameTime)) {
            var attackHand = VillagerRetaliationVillagerCombatUtil.selectAttackHand(villager);
            villager.swing(attackHand, true);
            syncMeleeAttackAttributes(villager);
            villager.doHurtTarget(target);
            RETALIATION.setNextAttackTick(villager, gameTime + VillagerCombatRoles.attackCooldown(villager));
        }
    }

    public static boolean blockTradingIfHostile(Villager villager, Player player) {
        if (villager.level().isClientSide || !villager.isAlive() || !player.isAlive()) {
            return false;
        }

        if (!RETALIATION.isHostileTowards(villager, player, () -> clearAnger(villager))) {
            return false;
        }

        VillagerRetaliationRetaliationUtil.spawnMadParticles(villager);
        return true;
    }

    public static boolean tryPacifyWithEmeralds(Villager villager, Player player, ItemStack interactionStack) {
        if (villager.level().isClientSide
                || !villager.isAlive()
                || !player.isAlive()
                || !interactionStack.is(Items.EMERALD)
                || !RETALIATION.isHostileTowards(villager, player, () -> clearAnger(villager))) {
            return false;
        }

        int requiredEmeralds = VillagerRetaliationRetaliationUtil.pacifyEmeraldCost(villager);
        if (interactionStack.getCount() < requiredEmeralds) {
            VillagerRetaliationRetaliationUtil.spawnPacifyFailureParticles(villager);
            return true;
        }

        if (!player.hasInfiniteMaterials()) {
            interactionStack.shrink(requiredEmeralds);
        }
        clearAnger(villager);
        VillagerRetaliationRetaliationUtil.spawnPacifySuccessParticles(villager);
        return true;
    }

    private static void anger(Villager villager, LivingEntity attacker) {
        if (villager.isBaby()) {
            return;
        }
        RETALIATION.anger(villager, attacker);
    }

    private static void tryAcquireHostileTarget(Villager villager) {
        if (RETALIATION.hasAnger(villager)
                || !villager.isAlive()
                || !VillagerRetaliationVillagerRules.shouldSuppressFleeingBehavior(villager)
                || !VillagerCombatRoles.canFightBack(villager)) {
            return;
        }

        VillagerRetaliationVillagerCombatUtil.getMemoryIfRegistered(villager, MemoryModuleType.NEAREST_HOSTILE)
                .filter(LivingEntity::isAlive)
                .filter(target -> target != villager)
                .ifPresent(target -> anger(villager, target));
    }

    private static boolean tryAcquireGroundWeapon(Villager villager, long gameTime) {
        if (!villager.isAlive()
                || !VillagerRetaliationVillagerRules.shouldSuppressFleeingBehavior(villager)
                || !VillagerCombatRoles.canScavengeGroundWeapons(villager)
                || VillagerRetaliationVillagerWeapons.hasTrackedPickup(villager)
                || VillagerRetaliationVillagerWeapons.hasUsableWeapon(villager)
                || !VillagerRetaliationVillagerCombatUtil.isThreatened(villager)) {
            return false;
        }

        return RETALIATION.tryAcquireGroundWeapon(
                villager,
                VillagerCombatRoles.movementSpeed(villager),
                () -> RETALIATION.discardTemporaryWeapon(villager),
                gameTime
        );
    }

    private static void clearAnger(Villager villager) {
        clearAnger(villager, true);
    }

    private static void clearAnger(Villager villager, boolean restoreWeapon) {
        RETALIATION.clearPersistentAnger(villager);
        NEXT_SPECIAL_TICKS.remove(villager.getUUID());
        VillagerRangedCombatHelper.clearState(villager);
        boolean preservePotionUse = VillagerClericPotionHelper.isDrinkingPotion(villager);
        if (preservePotionUse && RETALIATION.hasTemporaryWeapon(villager)) {
            VillagerClericPotionHelper.setPostDrinkMainHand(villager, RETALIATION.temporaryWeaponFallback(villager));
        }
        if (!preservePotionUse) {
            VillagerClericPotionHelper.clearAllState(villager);
        }
        VillagerRetaliationRetaliationUtil.restoreCombatMovement(villager);
        if (restoreWeapon && !preservePotionUse) {
            RETALIATION.restoreTemporaryWeapon(villager);
        } else {
            RETALIATION.discardTemporaryWeapon(villager);
        }
        RETALIATION.clearTransientState(villager);
        villager.setAggressive(false);
        villager.setChasing(false);
        villager.setTarget(null);
        villager.getNavigation().stop();
    }

    private static void syncMeleeAttackAttributes(Villager villager) {
        AttributeInstance attackDamage = villager.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage == null) {
            return;
        }

        double desiredBaseDamage = VillagerCombatRoles.meleeAttackDamageBase(villager);
        if (attackDamage.getBaseValue() != desiredBaseDamage) {
            attackDamage.setBaseValue(desiredBaseDamage);
        }
    }

    private static void angerNearbyVillagers(Entity sourceEntity, LivingEntity attacker, double radius) {
        if (!(sourceEntity.level() instanceof ServerLevel level)) {
            return;
        }

        AABB area = sourceEntity.getBoundingBox().inflate(radius);
        for (Villager nearby : level.getEntitiesOfClass(Villager.class, area)) {
            if (nearby != sourceEntity && !nearby.isBaby()) {
                anger(nearby, attacker);
            }
        }
    }

    private static void rallyNearbyVillagers(Villager alarmVillager, LivingEntity attacker, double radius) {
        // Keep alarm villagers in panic/flee behavior while still spreading the threat to fighters.
        long gameTime = alarmVillager.level().getGameTime();
        alarmVillager.getBrain().setActiveActivityIfPossible(Activity.PANIC);
        alarmVillager.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, gameTime);
        alarmVillager.getBrain().setMemory(MemoryModuleType.NEAREST_HOSTILE, attacker);
        angerNearbyVillagers(alarmVillager, attacker, radius);
        WanderingTraderRetaliationHandler.angerNearbyTradersFrom(alarmVillager, attacker, radius);
    }

    private static void rallyFromNearbyNitwits(Entity sourceEntity, LivingEntity attacker, double radius) {
        if (!(sourceEntity.level() instanceof ServerLevel level)) {
            return;
        }

        AABB area = sourceEntity.getBoundingBox().inflate(radius);
        for (Villager nearby : level.getEntitiesOfClass(Villager.class, area)) {
            if (isNitwitAlarm(nearby)) {
                rallyNearbyVillagers(nearby, attacker, radius);
            }
        }
    }

    private static void triggerNitwitWitnessedDeathFlee(Entity deceased, LivingEntity attacker, double radius) {
        if (!(deceased.level() instanceof ServerLevel level)) {
            return;
        }

        AABB area = deceased.getBoundingBox().inflate(radius);
        long gameTime = level.getGameTime();
        for (Villager nearby : level.getEntitiesOfClass(Villager.class, area)) {
            if (!isWitnessAlarmVillager(nearby) || !nearby.hasLineOfSight(deceased)) {
                continue;
            }

            nearby.getBrain().setActiveActivityIfPossible(Activity.PANIC);
            nearby.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, gameTime);
            if (attacker != null && attacker.isAlive()) {
                nearby.getBrain().setMemory(MemoryModuleType.NEAREST_HOSTILE, attacker);
            }
        }
    }

    private static boolean isNitwitAlarm(Villager villager) {
        return !villager.isBaby() && villager.getVillagerData().getProfession() == VillagerProfession.NITWIT;
    }

    private static boolean isWitnessAlarmVillager(Villager villager) {
        return villager.isBaby() || isNitwitAlarm(villager);
    }

    private static void suppressVanillaPanic(Villager villager) {
        VillagerRetaliationVillagerBrainUtil.clearThreatMemories(villager);
    }

    private static void handleDefensiveRole(Villager villager, long gameTime) {
        if (gameTime < NEXT_SPECIAL_TICKS.getOrDefault(villager.getUUID(), 0L)) {
            return;
        }

        if (VillagerCombatRoles.isFarmer(villager)
                && VillagerRetaliationConfig.FARMERS_USE_BREAD.get()
                && villager.getHealth() < villager.getMaxHealth() * 0.6F) {
            villager.heal(4.0F);
            NEXT_SPECIAL_TICKS.put(villager.getUUID(), gameTime + 120L);
        }
    }

    private static void equipCombatWeapon(Villager villager) {
        if (VillagerClericPotionHelper.isActivelyHandlingPotion(villager)) {
            return;
        }

        if (VillagerRetaliationVillagerWeapons.maintainAcquiredWeaponAuthority(villager)) {
            RETALIATION.discardTemporaryWeapon(villager);
            return;
        }

        if (RETALIATION.maintainTemporaryWeapon(villager)) {
            return;
        }

        if (VillagerRetaliationVillagerWeapons.hasUsableWeapon(villager)) {
            return;
        }

        ItemStack weapon = VillagerCombatRoles.preferredWeapon(villager);
        if (weapon.isEmpty()) {
            return;
        }

        RETALIATION.equipTemporaryWeapon(villager, weapon);
    }

    private static void handlePassivePotionState(Villager villager) {
        VillagerRangedCombatHelper.clearState(villager);
        if (VillagerClericPotionHelper.tickDrinkingIfActive(villager)) {
            villager.getNavigation().stop();
            VillagerRetaliationRetaliationUtil.restoreCombatMovement(villager);
            return;
        }

        VillagerRetaliationRetaliationUtil.restoreCombatMovement(villager);
        RETALIATION.restoreTemporaryWeapon(villager);
        if (VillagerClericPotionHelper.tryOutOfCombatMilk(villager)) {
            return;
        }
        if (villager.level() instanceof ServerLevel level
                && VillagerClericPotionHelper.tryOutOfCombatSupport(villager, level)) {
            return;
        }

        VillagerClericPotionHelper.clearState(villager);
    }

    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }

        VillagerRangedCombatHelper.clearState(villager);
        VillagerClericPotionHelper.restoreHeldItemAndClearState(villager);
        VillagerRetaliationRetaliationUtil.restoreCombatMovement(villager);
        if (villager.isAlive()) {
            RETALIATION.restoreTemporaryWeapon(villager);
        } else {
            RETALIATION.discardTemporaryWeapon(villager);
        }
        RETALIATION.clearTransientState(villager);
        NEXT_SPECIAL_TICKS.remove(villager.getUUID());
        if (villager.isAlive()) {
            VillagerRetaliationVillagerWeapons.clearTrackedPickupCache(villager);
        } else {
            VillagerRetaliationVillagerWeapons.clearTrackedPickup(villager);
        }
    }
}
