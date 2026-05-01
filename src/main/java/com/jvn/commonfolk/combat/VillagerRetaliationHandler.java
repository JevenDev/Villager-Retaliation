package com.jvn.commonfolk.combat;

import com.jvn.commonfolk.config.CommonfolkConfig;
import com.jvn.commonfolk.combat.CommonfolkRetaliationUtil.AngerTarget;
import com.jvn.commonfolk.combat.CommonfolkRetaliationUtil.TemporaryWeaponState;
import com.jvn.commonfolk.util.CommonfolkVillagerCombatUtil;
import com.jvn.commonfolk.villager.CommonfolkVillagerRules;
import com.jvn.commonfolk.villager.CommonfolkVillagerWeapons;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class VillagerRetaliationHandler {
    private static final String PERSISTENT_TAG_ROOT = "CommonfolkPersistentHostility";
    private static final String PERSISTENT_TARGET_UUID = "Target";
    private static final String PERSISTENT_LAST_SEEN_TICK = "LastSeenTick";
    private static final Map<UUID, AngerTarget> ANGER_TARGETS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_ATTACK_TICKS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_SPECIAL_TICKS = new HashMap<>();
    private static final Map<UUID, Double> ORIGINAL_MOVEMENT_SPEEDS = new HashMap<>();
    private static final Map<UUID, TemporaryWeaponState> TEMPORARY_WEAPONS = new HashMap<>();

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
        if (!VillagerCombatRoles.isCleric(villager) || !CommonfolkConfig.CLERICS_USE_POTIONS.get()) {
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
        if (!CommonfolkConfig.ENABLE_VILLAGER_RETALIATION.get()
                || event.getNewDamage() <= 0.0F) {
            return;
        }

        if (event.getEntity() instanceof WanderingTrader trader) {
            CommonfolkVillagerCombatUtil.resolveAttacker(event.getSource()).ifPresent(attacker ->
                    angerNearbyVillagers(trader, attacker, CommonfolkConfig.VILLAGER_KILL_AGGRO_RADIUS.get()));
            return;
        }
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }

        if (villager.isBaby()) {
            CommonfolkVillagerCombatUtil.resolveAttacker(event.getSource()).ifPresent(attacker ->
                    rallyNearbyVillagers(villager, attacker, CommonfolkConfig.VILLAGER_KILL_AGGRO_RADIUS.get()));
            return;
        }

        if (VillagerCombatRoles.isArmorer(villager) && CommonfolkConfig.ARMORERS_FIGHT_BACK.get()) {
            villager.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 0));
        }

        CommonfolkVillagerCombatUtil.resolveAttacker(event.getSource()).ifPresent(attacker -> {
            if (isNitwitAlarm(villager)) {
                rallyNearbyVillagers(villager, attacker, CommonfolkConfig.VILLAGER_KILL_AGGRO_RADIUS.get());
                return;
            }

            anger(villager, attacker);
            if (!CommonfolkConfig.ATTACK_AGGROS_ONLY_HIT_VILLAGER.get()) {
                angerNearbyVillagers(villager, attacker, CommonfolkConfig.VILLAGER_KILL_AGGRO_RADIUS.get());
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

        if (!CommonfolkConfig.ENABLE_VILLAGER_RETALIATION.get()
                || !CommonfolkConfig.KILLING_VILLAGER_AGGROS_NEARBY_VILLAGERS.get()
                || !(deceased.level() instanceof ServerLevel level)) {
            return;
        }
        if (deceased instanceof Villager villager && villager.isBaby()) {
            return;
        }

        Optional<LivingEntity> attacker = CommonfolkVillagerCombatUtil.resolveAttacker(event.getSource());
        if (deceasedIsVillager) {
            triggerNitwitWitnessedDeathFlee(deceased, attacker.orElse(null), CommonfolkConfig.VILLAGER_KILL_AGGRO_RADIUS.get());
        }

        if (attacker.isEmpty() || CommonfolkVillagerCombatUtil.shouldIgnoreAttacker(attacker.get())) {
            return;
        }

        LivingEntity resolvedAttacker = attacker.get();
        double radius = CommonfolkConfig.VILLAGER_KILL_AGGRO_RADIUS.get();
        angerNearbyVillagers(deceased, resolvedAttacker, radius);
        rallyFromNearbyNitwits(deceased, resolvedAttacker, radius);
    }

    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Villager villager) || villager.level().isClientSide) {
            return;
        }

        if (ANGER_TARGETS.containsKey(villager.getUUID()) && CommonfolkVillagerRules.shouldSuppressFleeingBehavior(villager)) {
            suppressVanillaPanic(villager);
        }
    }

    public static void onEntityTickPost(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }

        CommonfolkVillagerCombatUtil.updateSwingAnimation(villager);
        if (villager.level().isClientSide) {
            return;
        }

        if (CommonfolkConfig.ENABLE_VILLAGER_RETALIATION.get()) {
            restorePersistedAngerIfNeeded(villager);
            tryAcquireHostileTarget(villager);
        }

        AngerTarget angerTarget = ANGER_TARGETS.get(villager.getUUID());
        if (angerTarget == null) {
            VillagerRangedCombatHelper.clearState(villager);
            if (VillagerClericPotionHelper.tickDrinkingIfActive(villager)) {
                CommonfolkRetaliationUtil.restoreCombatMovement(villager, ORIGINAL_MOVEMENT_SPEEDS);
                return;
            }
            if (VillagerClericPotionHelper.tryOutOfCombatMilk(villager)) {
                CommonfolkRetaliationUtil.restoreCombatMovement(villager, ORIGINAL_MOVEMENT_SPEEDS);
                return;
            }
            VillagerClericPotionHelper.clearState(villager);
            CommonfolkRetaliationUtil.restoreCombatMovement(villager, ORIGINAL_MOVEMENT_SPEEDS);
            return;
        }

        if (!(villager.level() instanceof ServerLevel level)) {
            clearAnger(villager);
            return;
        }

        long gameTime = level.getGameTime();
        Entity entity = level.getEntity(angerTarget.targetId());
        if (!(entity instanceof LivingEntity target)) {
            if (gameTime - angerTarget.lastSeenGameTick() >= CommonfolkConfig.AGGRO_DURATION_TICKS.get()) {
                clearAnger(villager);
            }
            return;
        }
        if (!target.isAlive() || CommonfolkVillagerCombatUtil.shouldIgnoreAttacker(target)) {
            clearAnger(villager);
            return;
        }

        if (!VillagerCombatRoles.canFightBack(villager)) {
            clearAnger(villager);
            return;
        }

        suppressVanillaPanic(villager);
        villager.setAggressive(true);
        villager.setChasing(true);
        villager.setTarget(target);

        if (villager.hasLineOfSight(target)) {
            CommonfolkRetaliationUtil.refreshAngerTarget(villager, angerTarget, gameTime, ANGER_TARGETS, PERSISTENT_TAG_ROOT);
        } else if (gameTime - angerTarget.lastSeenGameTick() >= CommonfolkConfig.AGGRO_DURATION_TICKS.get()) {
            clearAnger(villager);
            return;
        }

        if (tryAcquireGroundWeapon(villager)) {
            return;
        }

        equipCombatWeapon(villager);

        double distanceSqr = villager.distanceToSqr(target);
        villager.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (VillagerClericPotionHelper.tickDrinkingIfActive(villager)) {
            return;
        }

        CommonfolkRetaliationUtil.boostCombatMovement(villager, ORIGINAL_MOVEMENT_SPEEDS);

        handleDefensiveRole(villager, gameTime);

        if (VillagerClericPotionHelper.tryCombat(villager, target, level, distanceSqr)) {
            return;
        }
        if (CommonfolkRetaliationUtil.isUsingRangedCombatMode(villager)
                && VillagerRangedCombatHelper.tryAttack(villager, target, level, distanceSqr)) {
            return;
        }

        if (CommonfolkPotionUtil.shouldSuppressCombatWhileUsingPotion(villager)) {
            villager.getNavigation().stop();
            return;
        }

        villager.getNavigation().moveTo(target, VillagerCombatRoles.movementSpeed(villager));
        if (CommonfolkRetaliationUtil.canUseMeleeCombatMode(villager)
                && CommonfolkRetaliationUtil.canMeleeHit(villager, target)
                && CommonfolkRetaliationUtil.isAttackReady(villager, NEXT_ATTACK_TICKS, gameTime)) {
            var attackHand = CommonfolkVillagerCombatUtil.selectAttackHand(villager);
            villager.swing(attackHand, true);
            syncMeleeAttackAttributes(villager);
            villager.doHurtTarget(target);
            NEXT_ATTACK_TICKS.put(villager.getUUID(), gameTime + VillagerCombatRoles.attackCooldown(villager));
        }
    }

    public static boolean blockTradingIfHostile(Villager villager, Player player) {
        if (villager.level().isClientSide || !villager.isAlive() || !player.isAlive()) {
            return false;
        }

        if (!CommonfolkRetaliationUtil.isHostileTowards(villager, player, ANGER_TARGETS, PERSISTENT_TAG_ROOT, () -> clearAnger(villager))) {
            return false;
        }

        CommonfolkRetaliationUtil.spawnMadParticles(villager);
        return true;
    }

    private static void anger(Villager villager, LivingEntity attacker) {
        if (villager.isBaby()) {
            return;
        }
        CommonfolkRetaliationUtil.tryAnger(villager, attacker, ANGER_TARGETS, PERSISTENT_TAG_ROOT);
    }

    private static void tryAcquireHostileTarget(Villager villager) {
        if (ANGER_TARGETS.containsKey(villager.getUUID())
                || !villager.isAlive()
                || !CommonfolkVillagerRules.shouldSuppressFleeingBehavior(villager)
                || !VillagerCombatRoles.canFightBack(villager)) {
            return;
        }

        villager.getBrain().getMemory(MemoryModuleType.NEAREST_HOSTILE)
                .filter(LivingEntity::isAlive)
                .filter(target -> target != villager)
                .ifPresent(target -> anger(villager, target));
    }

    private static boolean tryAcquireGroundWeapon(Villager villager) {
        if (!villager.isAlive()
                || !CommonfolkVillagerRules.shouldSuppressFleeingBehavior(villager)
                || !VillagerCombatRoles.canScavengeGroundWeapons(villager)
                || CommonfolkVillagerWeapons.hasUsableWeapon(villager)
                || !CommonfolkVillagerCombatUtil.isThreatened(villager)) {
            return false;
        }

        return CommonfolkRetaliationUtil.tryAcquireGroundWeapon(
                villager,
                VillagerCombatRoles.movementSpeed(villager),
                () -> CommonfolkRetaliationUtil.discardTemporaryWeapon(villager, TEMPORARY_WEAPONS)
        );
    }

    private static void clearAnger(Villager villager) {
        clearAnger(villager, true);
    }

    private static void clearAnger(Villager villager, boolean restoreWeapon) {
        ANGER_TARGETS.remove(villager.getUUID());
        clearPersistedAnger(villager);
        NEXT_ATTACK_TICKS.remove(villager.getUUID());
        NEXT_SPECIAL_TICKS.remove(villager.getUUID());
        VillagerRangedCombatHelper.clearState(villager);
        boolean preservePotionUse = VillagerClericPotionHelper.tickDrinkingIfActive(villager);
        if (!preservePotionUse) {
            VillagerClericPotionHelper.clearState(villager);
        }
        CommonfolkRetaliationUtil.restoreCombatMovement(villager, ORIGINAL_MOVEMENT_SPEEDS);
        if (restoreWeapon && !preservePotionUse) {
            CommonfolkRetaliationUtil.restoreTemporaryWeapon(villager, TEMPORARY_WEAPONS);
        } else {
            TEMPORARY_WEAPONS.remove(villager.getUUID());
        }
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
        CommonfolkVillagerCombatUtil.eraseMemoryIfRegistered(villager, MemoryModuleType.HURT_BY);
        CommonfolkVillagerCombatUtil.eraseMemoryIfRegistered(villager, MemoryModuleType.HURT_BY_ENTITY);
        CommonfolkVillagerCombatUtil.eraseMemoryIfRegistered(villager, MemoryModuleType.NEAREST_HOSTILE);
    }

    private static void restorePersistedAngerIfNeeded(Villager villager) {
        CommonfolkRetaliationUtil.restorePersistedAngerIfNeeded(villager, ANGER_TARGETS, PERSISTENT_TAG_ROOT);
    }

    private static void clearPersistedAnger(Villager villager) {
        CommonfolkRetaliationUtil.clearPersistentAnger(villager, PERSISTENT_TAG_ROOT);
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
        }
    }

    private static void equipCombatWeapon(Villager villager) {
        if (VillagerClericPotionHelper.isActivelyHandlingPotion(villager)) {
            return;
        }

        if (CommonfolkVillagerWeapons.maintainAcquiredWeaponAuthority(villager)) {
            CommonfolkRetaliationUtil.discardTemporaryWeapon(villager, TEMPORARY_WEAPONS);
            return;
        }

        if (CommonfolkRetaliationUtil.maintainTemporaryWeapon(villager, TEMPORARY_WEAPONS)) {
            return;
        }

        if (CommonfolkVillagerWeapons.hasUsableWeapon(villager)) {
            return;
        }

        ItemStack weapon = VillagerCombatRoles.preferredWeapon(villager);
        if (weapon.isEmpty()) {
            return;
        }

        CommonfolkRetaliationUtil.equipTemporaryWeapon(villager, TEMPORARY_WEAPONS, weapon);
    }
}
