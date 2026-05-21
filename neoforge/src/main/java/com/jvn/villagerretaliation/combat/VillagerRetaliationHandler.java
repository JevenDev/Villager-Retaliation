package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.combat.VillagerRetaliationRetaliationUtil.ActiveRetaliationTarget;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.reputation.VillagerAggressionPolicy;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerRules;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public final class VillagerRetaliationHandler {
    private static final long NATURAL_TARGET_SCAN_INTERVAL_TICKS = 20L;
    private static final double HOSTILE_HARASS_THROW_MAX_DISTANCE_SQR = 144.0D;
    private static final long ARMORER_SHIELD_AXE_BREAK_TICKS = 100L;
    private static final int ARMORER_COUNTER_SWINGS_AFTER_BLOCK = 1;
    private static final int ARMORER_COUNTER_ATTACK_DELAY_MIN_TICKS = 10;
    private static final int ARMORER_COUNTER_ATTACK_DELAY_MAX_TICKS = 30;
    private static final double ARMORER_BLOCKING_SPEED_FACTOR = 0.45D;
    private static final double ARMORER_SHIELD_TRIGGER_RANGE = 7.0D;
    private static final double ARMORER_SHIELD_TRIGGER_RANGE_SQR = ARMORER_SHIELD_TRIGGER_RANGE * ARMORER_SHIELD_TRIGGER_RANGE;
    private static final long SMITH_IRON_GOLEM_REPAIR_COOLDOWN_TICKS = 6000L;
    private static final double SMITH_IRON_GOLEM_REPAIR_SEARCH_RADIUS = 12.0D;
    private static final double SMITH_IRON_GOLEM_REPAIR_REACH_SQR = 9.0D;
    private static final float SMITH_IRON_GOLEM_REPAIR_HEAL_AMOUNT = 25.0F;
    private static final String PERSISTENT_TAG_ROOT = "VillagerRetaliationPersistentHostility";
    private static final String PERSISTENT_ARMORER_SHIELD_ROLLED_TAG = "VillagerRetaliationArmorerShieldRolled";
    private static final VillagerRetaliationRetaliationRuntime<Villager> RETALIATION =
            new VillagerRetaliationRetaliationRuntime<>(PERSISTENT_TAG_ROOT);
    private static final Map<UUID, Long> NEXT_SPECIAL_TICKS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_NATURAL_TARGET_SCAN_TICKS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_HOSTILE_HARASS_THROW_TICKS = new HashMap<>();
    private static final Map<UUID, Long> ARMORER_SHIELD_DISABLED_UNTIL_TICKS = new HashMap<>();
    private static final Map<UUID, Integer> ARMORER_PENDING_COUNTER_SWINGS = new HashMap<>();
    private static final Map<UUID, Long> ARMORER_COUNTER_ATTACK_READY_TICKS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_IRON_GOLEM_REPAIR_TICKS = new HashMap<>();

    private VillagerRetaliationHandler() {
    }

    public static void releaseTemporaryWeaponForInventory(Villager villager) {
        VillagerClericPotionHelper.restoreHeldItemAndClearState(villager);
        RETALIATION.restoreTemporaryWeapon(villager);
        VillagerInventoryAccess.returnBorrowedCombatWeapon(villager);
    }

    public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
        if (!event.has(EntityType.VILLAGER, Attributes.ATTACK_DAMAGE)) {
            event.add(EntityType.VILLAGER, Attributes.ATTACK_DAMAGE, VillagerCombatRoles.PLAYER_FIST_DAMAGE);
        }
        if (!event.has(EntityType.VILLAGER, Attributes.ATTACK_KNOCKBACK)) {
            event.add(EntityType.VILLAGER, Attributes.ATTACK_KNOCKBACK, 0.0D);
        }
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Villager villager)
                || villager.level().isClientSide
                || villager.isBaby()) {
            return;
        }

        ensureProfessionMainHand(villager);
        if (!VillagerCombatRoles.isArmorer(villager)
                || !VillagerRetaliationConfig.ARMORERS_FIGHT_BACK.get()
                || !isHardMode(villager)) {
            return;
        }

        tryRollArmorerSpawnShield(villager);
    }

    public static void onLivingDamagePre(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }
        if (tryHandleArmorerShieldBlock(villager, event)) {
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

    private static boolean tryHandleArmorerShieldBlock(Villager villager, LivingIncomingDamageEvent event) {
        if (!VillagerCombatRoles.isArmorer(villager)
                || !VillagerRetaliationConfig.ARMORERS_FIGHT_BACK.get()
                || !isArmorerActivelyBlocking(villager)) {
            return false;
        }

        DamageSource source = event.getSource();
        if (!villager.isDamageSourceBlocked(source)) {
            return false;
        }

        float incomingDamage = event.getAmount();
        event.setCanceled(true);
        event.setAmount(0.0F);
        villager.playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 0.8F + villager.getRandom().nextFloat() * 0.4F);

        boolean shieldBroke = applyShieldDurabilityDamage(villager, incomingDamage);
        boolean disabledByAxe = false;
        Optional<LivingEntity> attacker = VillagerRetaliationVillagerCombatUtil.resolveAttacker(villager, source);
        if (attacker.isPresent()) {
            LivingEntity resolvedAttacker = attacker.get();
            anger(villager, resolvedAttacker);
            if (!VillagerRetaliationConfig.ATTACK_AGGROS_ONLY_HIT_VILLAGER.get()) {
                angerNearbyVillagers(villager, resolvedAttacker, VillagerRetaliationConfig.VILLAGER_KILL_AGGRO_RADIUS.get());
            }

            if (!shieldBroke && isAxeAttacker(resolvedAttacker)) {
                disabledByAxe = true;
                breakArmorerShieldGuard(villager);
            }
        }
        if (!shieldBroke && !disabledByAxe) {
            UUID villagerId = villager.getUUID();
            ARMORER_PENDING_COUNTER_SWINGS.put(villagerId, ARMORER_COUNTER_SWINGS_AFTER_BLOCK);
            ARMORER_COUNTER_ATTACK_READY_TICKS.put(villagerId, villager.level().getGameTime() + nextCounterAttackDelayTicks(villager));
            ensureArmorerShieldBlocking(villager);
        }
        return true;
    }

    private static boolean applyShieldDurabilityDamage(Villager villager, float blockedDamage) {
        ItemStack shield = villager.getOffhandItem();
        if (!shield.is(Items.SHIELD)) {
            return false;
        }

        int durabilityLoss = blockedDamage >= 3.0F ? 1 + Mth.floor(blockedDamage) : 1;
        shield.hurtAndBreak(durabilityLoss, villager, EquipmentSlot.OFFHAND);
        return !villager.getOffhandItem().is(Items.SHIELD);
    }

    private static boolean isAxeAttacker(LivingEntity attacker) {
        return attacker.getMainHandItem().getItem() instanceof AxeItem
                || attacker.getOffhandItem().getItem() instanceof AxeItem;
    }

    private static void breakArmorerShieldGuard(Villager villager) {
        long gameTime = villager.level().getGameTime();
        UUID villagerId = villager.getUUID();
        ARMORER_SHIELD_DISABLED_UNTIL_TICKS.put(villagerId, gameTime + ARMORER_SHIELD_AXE_BREAK_TICKS);
        ARMORER_PENDING_COUNTER_SWINGS.remove(villagerId);
        ARMORER_COUNTER_ATTACK_READY_TICKS.remove(villagerId);
        stopArmorerShieldBlocking(villager);
        villager.playSound(SoundEvents.SHIELD_BREAK, 0.8F, 0.8F + villager.getRandom().nextFloat() * 0.4F);
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
            VillagerRetaliationVillagerCombatUtil.resolveAttacker(villager, event.getSource()).ifPresent(attacker -> {
                if (shouldRetaliateAgainstAttacker(villager, attacker)) {
                    rallyNearbyVillagers(villager, attacker, VillagerRetaliationConfig.VILLAGER_KILL_AGGRO_RADIUS.get());
                }
            });
            return;
        }

        if (VillagerCombatRoles.isArmorer(villager) && VillagerRetaliationConfig.ARMORERS_FIGHT_BACK.get()) {
            villager.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 0));
        }

        VillagerRetaliationVillagerCombatUtil.resolveAttacker(villager, event.getSource()).ifPresent(attacker -> {
            if (!shouldRetaliateAgainstAttacker(villager, attacker)) {
                return;
            }
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

        Optional<LivingEntity> attacker = event.getEntity() instanceof LivingEntity livingEntity
                ? VillagerRetaliationVillagerCombatUtil.resolveAttacker(livingEntity, event.getSource())
                : VillagerRetaliationVillagerCombatUtil.resolveAttacker(event.getSource());
        if (deceasedIsVillager) {
            triggerNitwitWitnessedDeathFlee(deceased, attacker.orElse(null), VillagerRetaliationConfig.VILLAGER_KILL_AGGRO_RADIUS.get());
        }

        if (attacker.isEmpty() || VillagerRetaliationVillagerCombatUtil.shouldIgnoreAttacker(attacker.get())) {
            return;
        }

        LivingEntity resolvedAttacker = attacker.get();
        if (!shouldRetaliateAgainstAttacker(deceased instanceof Villager villager ? villager : null, resolvedAttacker)) {
            return;
        }
        double radius = VillagerRetaliationConfig.VILLAGER_KILL_AGGRO_RADIUS.get();
        angerNearbyVillagers(deceased, resolvedAttacker, radius, deceasedIsVillager);
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

        ensureArmorerSpawnShieldRoll(villager);
        if (!VillagerClericPotionHelper.isActivelyHandlingPotion(villager)) {
            VillagerRetaliationVillagerEquipment.maintainPlayerManagedMainHand(villager);
        }
        ensureProfessionMainHand(villager);

        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_RETALIATION.get()) {
            clearAnger(villager);
            handlePassivePotionState(villager);
            return;
        }

        if (villager.isSleeping()) {
            handleSleepingCombatState(villager);
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
            if (tryFleeVisibleCreeper(villager)) {
                handlePassivePotionState(villager);
                return;
            }
            handlePassivePotionState(villager);
            return;
        }

        ServerLevel level = retaliationTarget.level();
        LivingEntity target = retaliationTarget.target();
        long gameTime = retaliationTarget.gameTime();
        double distanceSqr = villager.distanceToSqr(target);
        if (isNaturalHostileTarget(villager, target)
                && !VillagerRetaliationVillagerRules.canStandGroundAgainstHostileMobs(villager)) {
            clearAnger(villager, true, false);
            enterFleeState(villager, target, gameTime);
            handlePassivePotionState(villager);
            return;
        }
        if (shouldAvoidVisibleCreeper(villager, target)) {
            clearAnger(villager, true, false);
            enterCreeperAvoidanceState(villager, target, gameTime);
            handlePassivePotionState(villager);
            return;
        }
        if (!retaliationTarget.targetCurrentlyHostile()) {
            villager.setAggressive(false);
            villager.setChasing(false);
            villager.setTarget(null);
            resetArmorerShieldState(villager);
            handlePassivePotionState(villager);
            villager.getNavigation().stop();
            return;
        }
        if (!VillagerRetaliationRetaliationUtil.isWithinRetaliationPursuitRange(villager, target)) {
            clearAnger(villager);
            handlePassivePotionState(villager);
            return;
        }

        suppressVanillaPanic(villager);
        villager.setAggressive(true);
        villager.setChasing(true);
        villager.setTarget(target);

        villager.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (VillagerInventoryAccess.hasOpenInventory(villager)) {
            suspendCombatForOpenInventory(villager);
            return;
        }

        if (VillagerClericPotionHelper.tickDrinkingIfActive(villager)) {
            villager.getNavigation().stop();
            return;
        }

        tryBorrowInventoryCombatWeapon(villager);
        if (tryAcquireGroundWeapon(villager, gameTime)) {
            return;
        }

        equipCombatWeapon(villager);
        VillagerInteractionTracker.markGearReportsUsedInCombat(level, villager, hasEquippedWeaponGear(villager), hasEquippedArmorGear(villager));
        VillagerRetaliationRetaliationUtil.boostCombatMovement(villager);

        handleDefensiveRole(villager, gameTime);
        boolean meleeAttackReady = RETALIATION.isAttackReady(villager, gameTime);
        boolean allowMeleeAttack = handleArmorerShieldCombatTactics(villager, target, distanceSqr, gameTime, meleeAttackReady);

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
        if (tryHostileTierHarassThrow(villager, target, level, gameTime, distanceSqr)) {
            return;
        }

        double movementSpeed = VillagerCombatRoles.movementSpeed(villager)
                * (isArmorerActivelyBlocking(villager) ? ARMORER_BLOCKING_SPEED_FACTOR : 1.0D);
        VillagerRetaliationRetaliationUtil.moveTowardReachableRetaliationTarget(villager, target, movementSpeed);
        if (VillagerRetaliationRetaliationUtil.canUseMeleeCombatMode(villager)
                && VillagerRetaliationRetaliationUtil.canMeleeHit(villager, target)
                && allowMeleeAttack
                && meleeAttackReady) {
            var attackHand = VillagerRetaliationVillagerCombatUtil.selectAttackHand(villager);
            villager.swing(attackHand, true);
            syncMeleeAttackAttributes(villager);
            villager.doHurtTarget(target);
            RETALIATION.setNextAttackTick(villager, gameTime + VillagerCombatRoles.attackCooldown(villager));
            onArmorerMeleeAttackCommitted(villager);
        }
    }

    public static boolean blockTradingIfHostile(Villager villager, Player player) {
        if (villager.level().isClientSide || !villager.isAlive() || !player.isAlive()) {
            return false;
        }

        if (!isHostileTowards(villager, player)) {
            return false;
        }

        VillagerRetaliationRetaliationUtil.spawnMadParticles(villager);
        return true;
    }

    public static boolean isHostileTowards(Villager villager, Player player) {
        if (villager.level().isClientSide || !villager.isAlive() || !player.isAlive()) {
            return false;
        }

        return RETALIATION.isHostileTowards(villager, player, () -> clearAnger(villager))
                || isDespisedBy(villager, player);
    }

    public static boolean tryPacifyWithEmeralds(Villager villager, Player player, ItemStack interactionStack) {
        return pacifyWithEmeralds(villager, player, interactionStack).handled();
    }

    public static VillagerPacificationResult pacifyWithEmeralds(Villager villager, Player player, ItemStack interactionStack) {
        if (villager.level().isClientSide
                || !villager.isAlive()
                || !player.isAlive()
                || !interactionStack.is(Items.EMERALD)
                || !RETALIATION.isHostileTowards(villager, player, () -> clearAnger(villager))) {
            return VillagerPacificationResult.NOT_APPLICABLE;
        }

        if (isPacificationBlockedByReputation(villager, player)) {
            VillagerRetaliationRetaliationUtil.spawnMadParticles(villager);
            return VillagerPacificationResult.BLOCKED_BY_REPUTATION;
        }

        int requiredEmeralds = VillagerRetaliationRetaliationUtil.pacifyEmeraldCost(villager);
        if (interactionStack.getCount() < requiredEmeralds) {
            VillagerRetaliationRetaliationUtil.spawnPacifyFailureParticles(villager);
            return VillagerPacificationResult.NOT_ENOUGH_EMERALDS;
        }

        if (!player.hasInfiniteMaterials()) {
            interactionStack.shrink(requiredEmeralds);
        }
        clearAnger(villager);
        VillagerRetaliationRetaliationUtil.spawnPacifySuccessParticles(villager);
        return VillagerPacificationResult.SUCCESS;
    }

    private static boolean isDespisedBy(Villager villager, Player player) {
        return VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()
                && villager.level() instanceof ServerLevel level
                && VillagerReputationManager.isDespised(level, villager, player);
    }

    private static boolean isPacificationBlockedByReputation(Villager villager, Player player) {
        return VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()
                && villager.level() instanceof ServerLevel level
                && (VillagerReputationManager.isDespised(level, villager, player)
                || VillagerReputationManager.isFeared(level, villager, player));
    }

    public static void forceAnger(Villager villager, LivingEntity attacker) {
        if (villager.level().isClientSide || villager.isBaby()) {
            return;
        }
        anger(villager, attacker);
    }

    private static void anger(Villager villager, LivingEntity attacker) {
        if (villager.isBaby()) {
            return;
        }
        if (attacker instanceof Creeper creeper) {
            if (VillagerRetaliationConfig.VILLAGERS_FLEE_VISIBLE_CREEPERS.get()) {
                enterCreeperAvoidanceState(villager, creeper, villager.level().getGameTime());
            }
            return;
        }
        RETALIATION.anger(villager, attacker);
    }

    private static void tryAcquireHostileTarget(Villager villager) {
        if (RETALIATION.hasAnger(villager)
                || !villager.isAlive()
                || !VillagerRetaliationConfig.VILLAGERS_TARGET_HOSTILE_MOBS.get()
                || !VillagerRetaliationVillagerRules.shouldSuppressFleeingBehavior(villager)
                || !VillagerCombatRoles.canFightBack(villager)) {
            return;
        }

        Optional<LivingEntity> memoryTarget = VillagerRetaliationVillagerCombatUtil.getMemoryIfRegistered(villager, MemoryModuleType.NEAREST_HOSTILE)
                .filter(LivingEntity::isAlive)
                .filter(target -> target != villager)
                .filter(target -> VillagerRetaliationVillagerCombatUtil.isNaturalHostileTarget(villager, target))
                .filter(target -> VillagerRetaliationVillagerCombatUtil.isWithinNaturalHostileTargetRange(villager, target))
                .filter(villager::hasLineOfSight)
                .filter(target -> !shouldAvoidVisibleCreeper(villager, target));
        if (memoryTarget.isPresent()) {
            anger(villager, memoryTarget.get());
            return;
        }

        long gameTime = villager.level().getGameTime();
        if (gameTime < NEXT_NATURAL_TARGET_SCAN_TICKS.getOrDefault(villager.getUUID(), 0L)) {
            return;
        }

        NEXT_NATURAL_TARGET_SCAN_TICKS.put(villager.getUUID(), gameTime + NATURAL_TARGET_SCAN_INTERVAL_TICKS);
        double naturalDefenseRadius = VillagerRetaliationConfig.NATURAL_HOSTILE_TARGET_RADIUS.get();
        VillagerRetaliationVillagerCombatUtil.findNearestNaturalHostile(villager, naturalDefenseRadius)
                .filter(target -> !shouldAvoidVisibleCreeper(villager, target))
                .ifPresent(target -> anger(villager, target));
    }

    private static boolean isNaturalHostileTarget(Villager villager, LivingEntity target) {
        return VillagerRetaliationVillagerCombatUtil.isNaturalHostileTarget(villager, target);
    }

    private static boolean shouldAvoidVisibleCreeper(Villager villager, LivingEntity target) {
        return VillagerRetaliationConfig.VILLAGERS_FLEE_VISIBLE_CREEPERS.get()
                && target instanceof Creeper
                && villager.hasLineOfSight(target);
    }

    private static boolean tryFleeVisibleCreeper(Villager villager) {
        if (!VillagerRetaliationConfig.VILLAGERS_FLEE_VISIBLE_CREEPERS.get()) {
            return false;
        }

        double creeperThreatRadius = VillagerRetaliationConfig.NATURAL_HOSTILE_TARGET_RADIUS.get();
        Optional<Creeper> visibleCreeper = VillagerRetaliationVillagerCombatUtil.findNearestVisibleCreeper(villager, creeperThreatRadius);
        if (visibleCreeper.isEmpty()) {
            return false;
        }

        clearAnger(villager, true, false);
        enterCreeperAvoidanceState(villager, visibleCreeper.get(), villager.level().getGameTime());
        return true;
    }

    private static void enterCreeperAvoidanceState(Villager villager, LivingEntity creeper, long gameTime) {
        enterFleeState(villager, creeper, gameTime);
    }

    private static void enterFleeState(Villager villager, LivingEntity hostile, long gameTime) {
        VillagerRetaliationVillagerBrainUtil.enterFleeState(villager, hostile, gameTime);
        villager.setAggressive(false);
        villager.setChasing(false);
        villager.setTarget(null);
    }

    private static boolean tryAcquireGroundWeapon(Villager villager, long gameTime) {
        if (!villager.isAlive()
                || !VillagerRetaliationVillagerRules.shouldSuppressFleeingBehavior(villager)
                || !VillagerCombatRoles.canScavengeGroundWeapons(villager)
                || VillagerRetaliationVillagerEquipment.isPlayerManagedMainHand(villager)
                || VillagerInventoryAccess.hasOpenInventory(villager)
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

    private static boolean tryBorrowInventoryCombatWeapon(Villager villager) {
        if (VillagerInventoryAccess.hasOpenInventory(villager)
                || VillagerClericPotionHelper.isActivelyHandlingPotion(villager)
                || VillagerRetaliationVillagerWeapons.hasUsableWeapon(villager)) {
            return false;
        }

        boolean borrowed = VillagerInventoryAccess.tryBorrowCombatWeapon(villager);
        if (borrowed) {
            RETALIATION.discardTemporaryWeapon(villager);
            VillagerRangedCombatHelper.seedInitialAttackDelay(villager, villager.getMainHandItem());
        }
        return borrowed;
    }

    private static void clearAnger(Villager villager) {
        clearAnger(villager, true, true);
    }

    private static void clearAnger(Villager villager, boolean restoreWeapon) {
        clearAnger(villager, restoreWeapon, true);
    }

    private static void clearAnger(Villager villager, boolean restoreWeapon, boolean stopNavigation) {
        RETALIATION.clearPersistentAnger(villager);
        NEXT_SPECIAL_TICKS.remove(villager.getUUID());
        NEXT_NATURAL_TARGET_SCAN_TICKS.remove(villager.getUUID());
        NEXT_HOSTILE_HARASS_THROW_TICKS.remove(villager.getUUID());
        resetArmorerShieldState(villager);
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
            VillagerInventoryAccess.returnBorrowedCombatWeapon(villager);
        } else {
            RETALIATION.discardTemporaryWeapon(villager);
            VillagerInventoryAccess.clearBorrowedCombatWeapon(villager);
        }
        VillagerRetaliationVillagerWeapons.maintainAcquiredWeaponAuthority(villager);
        RETALIATION.clearTransientState(villager);
        villager.setAggressive(false);
        villager.setChasing(false);
        villager.setTarget(null);
        villager.setLastHurtByMob(null);
        if (stopNavigation) {
            villager.getNavigation().stop();
        }
    }

    private static boolean shouldRetaliateAgainstAttacker(Villager villager, LivingEntity attacker) {
        return VillagerRetaliationConfig.VILLAGERS_RETALIATE_AGAINST_HOSTILE_MOBS.get()
                || !isHostileMobAttacker(villager, attacker);
    }

    private static boolean isHostileMobAttacker(Villager villager, LivingEntity attacker) {
        if (attacker instanceof Creeper) {
            return true;
        }
        if (villager != null) {
            return VillagerRetaliationVillagerCombatUtil.isNaturalHostileTarget(villager, attacker);
        }
        return attacker instanceof net.minecraft.world.entity.monster.Enemy
                && !(attacker instanceof net.minecraft.world.entity.NeutralMob);
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
        angerNearbyVillagers(sourceEntity, attacker, radius, false);
    }

    private static void angerNearbyVillagers(Entity sourceEntity, LivingEntity attacker, double radius, boolean witnessedVillagerKill) {
        if (!(sourceEntity.level() instanceof ServerLevel level)) {
            return;
        }

        AABB area = sourceEntity.getBoundingBox().inflate(radius);
        for (Villager nearby : level.getEntitiesOfClass(Villager.class, area)) {
            if (nearby != sourceEntity
                    && !nearby.isBaby()
                    && canWitnessRetaliationEvent(nearby, sourceEntity)
                    && shouldAggroFromWitness(nearby, attacker, witnessedVillagerKill)) {
                anger(nearby, attacker);
            }
        }
    }

    private static boolean shouldAggroFromWitness(Villager witness, LivingEntity attacker, boolean witnessedVillagerKill) {
        if (!(attacker instanceof Player player)) {
            return true;
        }
        int pendingReputationChange = witnessedVillagerKill ? VillagerRetaliationConfig.WITNESSED_KILL_PENALTY.get() : 0;
        return VillagerAggressionPolicy.shouldAggroFromWitnessedPlayerCrime(witness, player, pendingReputationChange);
    }

    private static void rallyNearbyVillagers(Villager alarmVillager, LivingEntity attacker, double radius) {
        // Keep alarm villagers in panic/flee behavior while still spreading the threat to fighters.
        long gameTime = alarmVillager.level().getGameTime();
        VillagerRetaliationVillagerBrainUtil.enterFleeState(alarmVillager, attacker, gameTime);
        angerNearbyVillagers(alarmVillager, attacker, radius);
        WanderingTraderRetaliationHandler.angerNearbyTradersFrom(alarmVillager, attacker, radius);
    }

    private static void rallyFromNearbyNitwits(Entity sourceEntity, LivingEntity attacker, double radius) {
        if (!(sourceEntity.level() instanceof ServerLevel level)) {
            return;
        }

        AABB area = sourceEntity.getBoundingBox().inflate(radius);
        for (Villager nearby : level.getEntitiesOfClass(Villager.class, area)) {
            if (isNitwitAlarm(nearby) && canWitnessRetaliationEvent(nearby, sourceEntity)) {
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
            if (!isWitnessAlarmVillager(nearby) || !canWitnessRetaliationEvent(nearby, deceased)) {
                continue;
            }

            VillagerRetaliationVillagerBrainUtil.enterFleeState(nearby, attacker, gameTime);
        }
    }

    private static boolean isNitwitAlarm(Villager villager) {
        return !villager.isBaby() && villager.getVillagerData().getProfession() == VillagerProfession.NITWIT;
    }

    private static boolean isWitnessAlarmVillager(Villager villager) {
        return villager.isBaby() || isNitwitAlarm(villager);
    }

    private static boolean canWitnessRetaliationEvent(Villager witness, Entity sourceEntity) {
        return !VillagerRetaliationConfig.RETALIATION_WITNESSES_REQUIRE_LINE_OF_SIGHT.get()
                || witness.hasLineOfSight(sourceEntity);
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

    private static boolean tryHostileTierHarassThrow(
            Villager villager,
            LivingEntity target,
            ServerLevel level,
            long gameTime,
            double distanceSqr
    ) {
        if (!VillagerRetaliationConfig.HOSTILE_TIER_HARASS_THROW_ENABLED.get()
                || !(target instanceof Player player)
                || !player.isAlive()
                || player.isCreative()
                || player.isSpectator()
                || distanceSqr > HOSTILE_HARASS_THROW_MAX_DISTANCE_SQR
                || !villager.hasLineOfSight(player)
                || !isHostileTierAgainstPlayer(villager, player)) {
            return false;
        }

        if (gameTime < NEXT_HOSTILE_HARASS_THROW_TICKS.getOrDefault(villager.getUUID(), 0L)) {
            return false;
        }

        NEXT_HOSTILE_HARASS_THROW_TICKS.put(villager.getUUID(), gameTime + nextHarassThrowDelayTicks(villager));
        if (villager.getRandom().nextBoolean()) {
            ThrownEgg egg = new ThrownEgg(level, villager);
            egg.setItem(new ItemStack(Items.EGG));
            shootHarassProjectile(villager, egg, player, level);
        } else {
            Snowball poisonousPotato = new Snowball(level, villager);
            poisonousPotato.setItem(new ItemStack(Items.POISONOUS_POTATO));
            shootHarassProjectile(villager, poisonousPotato, player, level);
        }
        return true;
    }

    private static void shootHarassProjectile(
            Villager villager,
            ThrowableItemProjectile projectile,
            Player target,
            ServerLevel level
    ) {
        double dx = target.getX() + target.getDeltaMovement().x - villager.getX();
        double dy = target.getY(0.3333333333333333D) - projectile.getY();
        double dz = target.getZ() + target.getDeltaMovement().z - villager.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        projectile.shoot(dx, dy + horizontal * 0.2D, dz, 1.1F, (float) (16 - level.getDifficulty().getId() * 4));
        level.addFreshEntity(projectile);
        villager.swing(InteractionHand.MAIN_HAND, true);
        villager.playSound(SoundEvents.EGG_THROW, 1.0F, 0.8F + villager.getRandom().nextFloat() * 0.4F);
    }

    private static boolean isHostileTierAgainstPlayer(Villager villager, Player player) {
        if (!(villager.level() instanceof ServerLevel level) || !VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
            return false;
        }
        VillagerReputationLevel reputationLevel = VillagerReputationManager.getReputationLevel(level, villager, player.getUUID());
        return reputationLevel == VillagerReputationLevel.HOSTILE
                || reputationLevel == VillagerReputationLevel.DESPISED
                || reputationLevel == VillagerReputationLevel.FEARED;
    }

    private static int nextHarassThrowDelayTicks(Villager villager) {
        int minDelay = Math.max(1, VillagerRetaliationConfig.HOSTILE_TIER_HARASS_THROW_MIN_INTERVAL_TICKS.get());
        int maxDelay = Math.max(1, VillagerRetaliationConfig.HOSTILE_TIER_HARASS_THROW_MAX_INTERVAL_TICKS.get());
        if (maxDelay < minDelay) {
            int swap = minDelay;
            minDelay = maxDelay;
            maxDelay = swap;
        }
        return minDelay + villager.getRandom().nextInt(maxDelay - minDelay + 1);
    }

    private static void handleSleepingCombatState(Villager villager) {
        villager.setAggressive(false);
        villager.setChasing(false);
        villager.setTarget(null);
        resetArmorerShieldState(villager);
        VillagerRangedCombatHelper.clearState(villager);
        VillagerClericPotionHelper.clearState(villager);
        VillagerRetaliationRetaliationUtil.restoreCombatMovement(villager);
        RETALIATION.restoreTemporaryWeapon(villager);
        VillagerInventoryAccess.returnBorrowedCombatWeapon(villager);
        villager.getNavigation().stop();
    }

    private static void equipCombatWeapon(Villager villager) {
        if (VillagerInventoryAccess.hasOpenInventory(villager)) {
            RETALIATION.restoreTemporaryWeapon(villager);
            VillagerInventoryAccess.returnBorrowedCombatWeapon(villager);
            return;
        }

        if (VillagerClericPotionHelper.isActivelyHandlingPotion(villager)) {
            return;
        }

        if (VillagerInventoryAccess.maintainBorrowedCombatWeapon(villager)) {
            RETALIATION.discardTemporaryWeapon(villager);
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

        if (tryBorrowInventoryCombatWeapon(villager)) {
            return;
        }

        ItemStack weapon = VillagerCombatRoles.preferredWeapon(villager);
        if (weapon.isEmpty()) {
            return;
        }

        RETALIATION.equipTemporaryWeapon(villager, weapon);
    }

    private static boolean hasEquippedWeaponGear(Villager villager) {
        return VillagerRetaliationVillagerEquipment.isPlayerManagedMainHand(villager)
                || VillagerInventoryAccess.hasBorrowedCombatWeapon(villager)
                || VillagerRetaliationVillagerWeapons.isUsableWeapon(villager.getOffhandItem());
    }

    private static boolean hasEquippedArmorGear(Villager villager) {
        return !villager.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                || !villager.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                || !villager.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                || !villager.getItemBySlot(EquipmentSlot.FEET).isEmpty()
                || Equipable.get(villager.getOffhandItem()) != null;
    }

    private static void ensureProfessionMainHand(Villager villager) {
        if (VillagerInventoryAccess.hasOpenInventory(villager)
                || VillagerRetaliationVillagerEquipment.isPlayerManagedMainHand(villager)
                || RETALIATION.hasTemporaryWeapon(villager)
                || VillagerClericPotionHelper.isActivelyHandlingPotion(villager)) {
            return;
        }

        ItemStack roleWeapon = VillagerCombatRoles.persistentRoleWeapon(villager);
        if (!roleWeapon.isEmpty()) {
            roleWeapon = VillagerRetaliationCombatWeaponFactory.prepareEquippedCombatWeapon(villager, roleWeapon);
        }

        String roleKey = BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession())
                .toString();
        VillagerRetaliationVillagerEquipment.ensureRoleMainHand(villager, roleKey, roleWeapon);
    }

    private static boolean handleArmorerShieldCombatTactics(Villager villager, LivingEntity target, double distanceSqr, long gameTime, boolean meleeAttackReady) {
        if (!VillagerCombatRoles.isArmorer(villager)
                || !VillagerRetaliationConfig.ARMORERS_FIGHT_BACK.get()
                || !isHardMode(villager)
                || VillagerRetaliationRetaliationUtil.isUsingRangedCombatMode(villager)) {
            clearArmorerShieldTacticState(villager, true);
            return true;
        }

        if (!hasArmorerShield(villager)) {
            clearArmorerShieldTacticState(villager, true);
            return true;
        }

        boolean inMeleeRange = VillagerRetaliationRetaliationUtil.canUseMeleeCombatMode(villager)
                && VillagerRetaliationRetaliationUtil.canMeleeHit(villager, target);
        boolean inShieldTriggerRange = distanceSqr <= ARMORER_SHIELD_TRIGGER_RANGE_SQR;

        UUID villagerId = villager.getUUID();
        long shieldDisabledUntil = ARMORER_SHIELD_DISABLED_UNTIL_TICKS.getOrDefault(villagerId, 0L);
        if (gameTime < shieldDisabledUntil) {
            stopArmorerShieldBlocking(villager);
            return true;
        }
        if (shieldDisabledUntil != 0L) {
            ARMORER_SHIELD_DISABLED_UNTIL_TICKS.remove(villagerId);
        }

        int pendingCounterSwings = ARMORER_PENDING_COUNTER_SWINGS.getOrDefault(villagerId, 0);
        if (pendingCounterSwings > 0) {
            long counterAttackReadyTick = ARMORER_COUNTER_ATTACK_READY_TICKS.getOrDefault(villagerId, gameTime);
            if (gameTime < counterAttackReadyTick || !meleeAttackReady || !inMeleeRange) {
                if (!inShieldTriggerRange) {
                    stopArmorerShieldBlocking(villager);
                    return true;
                }
                ensureArmorerShieldBlocking(villager);
                return false;
            }
            return true;
        }

        if (!inShieldTriggerRange) {
            stopArmorerShieldBlocking(villager);
            return true;
        }

        ensureArmorerShieldBlocking(villager);
        return false;
    }

    private static void ensureArmorerShieldBlocking(Villager villager) {
        if (!isArmorerActivelyBlocking(villager) && hasArmorerShield(villager)) {
            villager.startUsingItem(InteractionHand.OFF_HAND);
        }
    }

    private static void stopArmorerShieldBlocking(Villager villager) {
        if (villager.isUsingItem() && villager.getUsedItemHand() == InteractionHand.OFF_HAND) {
            villager.stopUsingItem();
        }
    }

    private static void clearArmorerShieldTacticState(Villager villager, boolean stopBlocking) {
        UUID villagerId = villager.getUUID();
        ARMORER_SHIELD_DISABLED_UNTIL_TICKS.remove(villagerId);
        ARMORER_PENDING_COUNTER_SWINGS.remove(villagerId);
        ARMORER_COUNTER_ATTACK_READY_TICKS.remove(villagerId);
        if (stopBlocking) {
            stopArmorerShieldBlocking(villager);
        }
    }

    private static boolean isArmorerActivelyBlocking(Villager villager) {
        return villager.isUsingItem()
                && villager.getUsedItemHand() == InteractionHand.OFF_HAND
                && hasArmorerShield(villager);
    }

    private static void onArmorerMeleeAttackCommitted(Villager villager) {
        if (!VillagerCombatRoles.isArmorer(villager) || !hasArmorerShield(villager)) {
            return;
        }

        UUID villagerId = villager.getUUID();
        int pendingCounterSwings = ARMORER_PENDING_COUNTER_SWINGS.getOrDefault(villagerId, 0);
        if (pendingCounterSwings > 0) {
            ARMORER_PENDING_COUNTER_SWINGS.put(villagerId, pendingCounterSwings - 1);
            ARMORER_COUNTER_ATTACK_READY_TICKS.remove(villagerId);
        }
        stopArmorerShieldBlocking(villager);
    }

    private static int nextCounterAttackDelayTicks(Villager villager) {
        return ARMORER_COUNTER_ATTACK_DELAY_MIN_TICKS
                + villager.getRandom().nextInt(ARMORER_COUNTER_ATTACK_DELAY_MAX_TICKS - ARMORER_COUNTER_ATTACK_DELAY_MIN_TICKS + 1);
    }

    private static void handlePassivePotionState(Villager villager) {
        resetArmorerShieldState(villager);
        VillagerRangedCombatHelper.clearState(villager);
        if (VillagerInventoryAccess.hasOpenInventory(villager)) {
            suspendCombatForOpenInventory(villager);
            return;
        }

        if (VillagerClericPotionHelper.tickDrinkingIfActive(villager)) {
            villager.getNavigation().stop();
            VillagerRetaliationRetaliationUtil.restoreCombatMovement(villager);
            return;
        }

        VillagerRetaliationRetaliationUtil.restoreCombatMovement(villager);
        RETALIATION.restoreTemporaryWeapon(villager);
        VillagerInventoryAccess.returnBorrowedCombatWeapon(villager);
        if (VillagerClericPotionHelper.tryOutOfCombatMilk(villager)) {
            return;
        }
        if (villager.level() instanceof ServerLevel level
                && VillagerClericPotionHelper.tryOutOfCombatSupport(villager, level)) {
            return;
        }
        if (villager.level() instanceof ServerLevel level
                && tryRepairNearbyIronGolem(villager, level, level.getGameTime())) {
            return;
        }

        VillagerClericPotionHelper.clearState(villager);
    }

    private static void suspendCombatForOpenInventory(Villager villager) {
        villager.getNavigation().stop();
        resetArmorerShieldState(villager);
        VillagerRangedCombatHelper.clearState(villager);
        VillagerClericPotionHelper.clearState(villager);
        VillagerRetaliationRetaliationUtil.restoreCombatMovement(villager);
        RETALIATION.restoreTemporaryWeapon(villager);
        VillagerInventoryAccess.returnBorrowedCombatWeapon(villager);
    }

    private static boolean tryRepairNearbyIronGolem(Villager villager, ServerLevel level, long gameTime) {
        if (!canProfessionRepairIronGolems(villager)
                || gameTime < NEXT_IRON_GOLEM_REPAIR_TICKS.getOrDefault(villager.getUUID(), 0L)) {
            return false;
        }

        IronGolem ironGolem = findNearbyDamagedIronGolem(villager, level);
        if (ironGolem == null) {
            return false;
        }

        if (!villager.hasLineOfSight(ironGolem)
                || villager.distanceToSqr(ironGolem) > SMITH_IRON_GOLEM_REPAIR_REACH_SQR) {
            villager.getNavigation().moveTo(ironGolem, VillagerCombatRoles.movementSpeed(villager) * 0.6D);
            return true;
        }

        ironGolem.heal(SMITH_IRON_GOLEM_REPAIR_HEAL_AMOUNT);
        villager.swing(InteractionHand.MAIN_HAND, true);
        NEXT_IRON_GOLEM_REPAIR_TICKS.put(villager.getUUID(), gameTime + SMITH_IRON_GOLEM_REPAIR_COOLDOWN_TICKS);
        return true;
    }

    private static IronGolem findNearbyDamagedIronGolem(Villager villager, ServerLevel level) {
        AABB area = villager.getBoundingBox().inflate(SMITH_IRON_GOLEM_REPAIR_SEARCH_RADIUS);
        IronGolem bestTarget = null;
        float mostMissingHealth = 0.0F;
        for (IronGolem candidate : level.getEntitiesOfClass(IronGolem.class, area, ironGolem -> ironGolem.isAlive() && ironGolem.getHealth() < ironGolem.getMaxHealth())) {
            float missingHealth = candidate.getMaxHealth() - candidate.getHealth();
            if (missingHealth > mostMissingHealth
                    || (missingHealth == mostMissingHealth
                    && bestTarget != null
                    && villager.distanceToSqr(candidate) < villager.distanceToSqr(bestTarget))) {
                mostMissingHealth = missingHealth;
                bestTarget = candidate;
            }
        }
        return bestTarget;
    }

    private static boolean canProfessionRepairIronGolems(Villager villager) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        return profession == VillagerProfession.ARMORER
                || profession == VillagerProfession.WEAPONSMITH
                || profession == VillagerProfession.TOOLSMITH;
    }

    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }

        resetArmorerShieldState(villager);
        VillagerRangedCombatHelper.clearState(villager);
        VillagerClericPotionHelper.restoreHeldItemAndClearState(villager);
        VillagerRetaliationRetaliationUtil.restoreCombatMovement(villager);
        if (villager.isAlive()) {
            RETALIATION.restoreTemporaryWeapon(villager);
        } else {
            RETALIATION.discardTemporaryWeapon(villager);
            VillagerInventoryAccess.clearBorrowedCombatWeapon(villager);
        }
        RETALIATION.clearTransientState(villager);
        NEXT_SPECIAL_TICKS.remove(villager.getUUID());
        NEXT_NATURAL_TARGET_SCAN_TICKS.remove(villager.getUUID());
        NEXT_HOSTILE_HARASS_THROW_TICKS.remove(villager.getUUID());
        NEXT_IRON_GOLEM_REPAIR_TICKS.remove(villager.getUUID());
        if (villager.isAlive()) {
            VillagerRetaliationVillagerWeapons.clearTrackedPickupCache(villager);
        } else {
            VillagerRetaliationVillagerWeapons.clearTrackedPickup(villager);
        }
    }

    private static boolean isHardMode(Villager villager) {
        return villager.level().getDifficulty() == Difficulty.HARD;
    }

    private static void ensureArmorerSpawnShieldRoll(Villager villager) {
        if (villager.isBaby()
                || !VillagerCombatRoles.isArmorer(villager)
                || !VillagerRetaliationConfig.ARMORERS_FIGHT_BACK.get()
                || !isHardMode(villager)) {
            return;
        }

        tryRollArmorerSpawnShield(villager);
    }

    private static void tryRollArmorerSpawnShield(Villager villager) {
        var persistentData = villager.getPersistentData();
        if (persistentData.getBoolean(PERSISTENT_ARMORER_SHIELD_ROLLED_TAG)) {
            return;
        }
        persistentData.putBoolean(PERSISTENT_ARMORER_SHIELD_ROLLED_TAG, true);

        if (villager.getOffhandItem().isEmpty()
                && villager.getRandom().nextDouble() < VillagerRetaliationConfig.ARMORER_SHIELD_CHANCE_HARD.get()) {
            VillagerRetaliationVillagerEquipment.setRoleEquipment(villager, EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        }
    }

    private static boolean hasArmorerShield(Villager villager) {
        return villager.getOffhandItem().is(Items.SHIELD);
    }

    private static void resetArmorerShieldState(Villager villager) {
        stopArmorerShieldBlocking(villager);
        clearArmorerShieldTacticState(villager, false);
    }
}
