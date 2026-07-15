package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.allegiance.AllegianceCombatContext;
import com.jvn.villagerretaliation.allegiance.AllegianceCombatDecision;
import com.jvn.villagerretaliation.allegiance.AllegianceEntityClassifier;
import com.jvn.villagerretaliation.allegiance.UnlawfulOrderService;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceCombatPolicy;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRelations;
import com.jvn.villagerretaliation.allegiance.VillageCombatAuthorizationService;
import com.jvn.villagerretaliation.allegiance.VillagerDisciplineService;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.combat.VillagerRetaliationRetaliationUtil.ActiveRetaliationTarget;
import com.jvn.villagerretaliation.combat.VillagerRetaliationRetaliationUtil.AngerTarget;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueService;
import com.jvn.villagerretaliation.dialogue.VillagerInteractionTracker;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.mood.VillagerMoodService;
import com.jvn.villagerretaliation.party.PartyAttackMode;
import com.jvn.villagerretaliation.party.PartyCombatMode;
import com.jvn.villagerretaliation.party.PartyRecord;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.party.PartyVillagerRecord;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.profile.VillagerSocialAttributeBehavior;
import com.jvn.villagerretaliation.reputation.VillagerAggressionPolicy;
import com.jvn.villagerretaliation.reputation.VillagerAmbientIndicatorService;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.util.TickThrottle;
import com.jvn.villagerretaliation.util.VillagerEquipmentDurability;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerRules;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
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
    private static final long PARTY_KOS_TARGET_SCAN_INTERVAL_TICKS = 20L;
    private static final double PARTY_KOS_TARGET_RADIUS = 16.0D;
    private static final long CREEPER_AVOIDANCE_SCAN_INTERVAL_TICKS = 10L;
    private static final long ROLE_MAINHAND_MAINTENANCE_INTERVAL_TICKS = 40L;
    private static final long ROYALTY_AGGRO_BYPASS_NOTICE_COOLDOWN_TICKS = 20L * 5L;
    private static final int VERY_LOW_GUTS_RALLY_THRESHOLD = 20;
    private static final int LOW_GUTS_FLEE_THRESHOLD = 34;
    private static final int WAVERING_GUTS_COUNTER_THRESHOLD = 49;
    private static final long WAVERING_UNARMED_COUNTER_GIVE_UP_TICKS = 80L;
    private static final String ROYALTY_AGGRO_BYPASS_MESSAGE_KEY = "retaliation.royalty_aggro_bypass";
    private static final String PERSISTENT_TAG_ROOT = "VillagerRetaliationPersistentHostility";
    private static final VillagerRetaliationRetaliationRuntime<Villager> RETALIATION =
            new VillagerRetaliationRetaliationRuntime<>(PERSISTENT_TAG_ROOT);
    private static final RetaliationActorPolicy<Villager> ACTOR_POLICY = VillagerCombatRoles.policy();
    private static final Map<UUID, Long> NEXT_SPECIAL_TICKS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_NATURAL_TARGET_SCAN_TICKS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_PARTY_KOS_TARGET_SCAN_TICKS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_CREEPER_AVOIDANCE_SCAN_TICKS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_ROLE_MAINHAND_MAINTENANCE_TICKS = new HashMap<>();
    private static final Map<PlayerVillagerKey, Long> NEXT_ROYALTY_AGGRO_BYPASS_NOTICE_TICKS = new HashMap<>();
    private static final Map<PlayerVillagerKey, Long> WAVERING_UNARMED_COUNTERS = new HashMap<>();
    private static final Map<PlayerVillagerKey, Long> LOW_GUTS_RALLY_USED_UNTIL_TICKS = new HashMap<>();

    private VillagerRetaliationHandler() {
    }

    public static void releaseTemporaryWeaponForInventory(Villager villager) {
        VillagerClericPotionHelper.restoreHeldItemAndClearState(villager);
        RETALIATION.restoreTemporaryWeapon(villager);
        VillagerInventoryAccess.returnBorrowedCombatWeapon(villager);
    }

    public static void suspendCombatForInteraction(Villager villager) {
        clearAnger(villager);
    }

    public static void clearCustomTarget(Villager villager) {
        clearAnger(villager, false);
    }

    public static void suppressCombatForPartyOrder(Villager villager) {
        clearAnger(villager, false, false);
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
        VillagerArmorerCombatTactics.ensureSpawnShieldRoll(villager);
    }

    public static void onLivingDamagePre(LivingIncomingDamageEvent event) {
        LivingEntity damaged = event.getEntity();
        Optional<LivingEntity> resolvedAttacker =
                VillagerRetaliationVillagerCombatUtil.resolveDamageAttacker(damaged, event.getSource());
        boolean disciplinary = resolvedAttacker
                .map(attacker -> VillagerDisciplineService.isCommitting(attacker, damaged))
                .orElse(false);
        if (!disciplinary && resolvedAttacker.filter(Villager.class::isInstance)
                .filter(attacker -> PartyService.areInSameOrAlliedParty(attacker, damaged))
                .isPresent()) {
            event.setAmount(0.0F);
            return;
        }
        if (!disciplinary && damaged.level() instanceof ServerLevel level && resolvedAttacker.isPresent()) {
            LivingEntity attacker = resolvedAttacker.get();
            boolean authorized = VillageCombatAuthorizationService.isAuthorized(attacker, damaged);
            AllegianceCombatContext damageContext = attacker instanceof Player
                    ? AllegianceCombatContext.DIRECT_PLAYER_DAMAGE
                    : AllegianceCombatContext.DAMAGE;
            if (VillageAllegianceCombatPolicy.evaluate(
                    level, attacker, damaged, damageContext, authorized).denied()) {
                event.setAmount(0.0F);
                return;
            }
        }
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }
        if (VillagerArmorerCombatTactics.tryHandleShieldBlock(
                villager,
                event,
                VillagerRetaliationHandler::angerFromArmorerShield,
                VillagerRetaliationHandler::angerNearbyFromArmorerShield)) {
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

    private static void angerFromArmorerShield(Villager villager, LivingEntity attacker) {
        anger(villager, attacker);
    }

    private static void angerNearbyFromArmorerShield(Villager villager, LivingEntity attacker, double radius) {
        angerNearbyVillagers(villager, attacker, radius);
    }

    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_RETALIATION.get()
                || event.getNewDamage() <= 0.0F) {
            return;
        }

        VillagerRetaliationVillagerCombatUtil.resolveDamageAttacker(event.getEntity(), event.getSource())
                .ifPresent(attacker -> {
                    rallyPartyVillagers(event.getEntity(), attacker, false);
                    rallyPartyVillagers(attacker, event.getEntity(), true);
                });

        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }

        if (villager.isBaby()) {
            VillagerRetaliationVillagerCombatUtil.resolveDamageAttacker(villager, event.getSource()).ifPresent(attacker -> {
                if (shouldRetaliateAgainstAttacker(villager, attacker)) {
                    rallyNearbyVillagers(villager, attacker, VillagerRetaliationConfig.VILLAGER_KILL_AGGRO_RADIUS.get(), false);
                }
            });
            return;
        }

        if (VillagerCombatRoles.isArmorer(villager) && VillagerRetaliationConfig.ARMORERS_FIGHT_BACK.get()) {
            villager.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 0));
        }

        VillagerRetaliationVillagerCombatUtil.resolveDamageAttacker(villager, event.getSource()).ifPresent(attacker -> {
            if (!shouldRetaliateAgainstAttacker(villager, attacker)) {
                return;
            }
            if (isNitwitAlarm(villager)) {
                rallyNearbyVillagers(villager, attacker, VillagerRetaliationConfig.VILLAGER_KILL_AGGRO_RADIUS.get());
                return;
            }
            if (tryHandleUnarmedLowGutsPlayerHit(villager, attacker)) {
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
        boolean deceasedIsBabyVillager = deceased instanceof Villager villager && villager.isBaby();
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
        Optional<LivingEntity> attacker = event.getEntity() instanceof LivingEntity livingEntity
                ? VillagerRetaliationVillagerCombatUtil.resolveDeathAttacker(livingEntity, event.getSource())
                : VillagerRetaliationVillagerCombatUtil.resolveAttacker(event.getSource());
        double radius = VillagerRetaliationConfig.VILLAGER_KILL_AGGRO_RADIUS.get();
        List<Villager> witnessVillagers = witnessVillagersNear(deceased, radius);
        if (deceasedIsVillager) {
            triggerNitwitWitnessedDeathFlee(witnessVillagers, attacker.orElse(null));
        }
        if (deceasedIsBabyVillager) {
            return;
        }

        if (attacker.isEmpty() || VillagerRetaliationVillagerCombatUtil.shouldIgnoreAttacker(attacker.get())) {
            return;
        }

        LivingEntity resolvedAttacker = attacker.get();
        if (!shouldRetaliateAgainstAttacker(deceased instanceof Villager villager ? villager : null, resolvedAttacker)) {
            return;
        }
        angerWitnessVillagers(witnessVillagers, resolvedAttacker, deceasedIsVillager);
        WanderingTraderRetaliationHandler.angerNearbyTradersFrom(deceased, resolvedAttacker, radius);
        rallyFromNitwitWitnesses(witnessVillagers, resolvedAttacker, deceasedIsVillager);
    }

    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Villager villager) || villager.level().isClientSide) {
            return;
        }

        if (shouldSuppressFleeingForRetaliation(villager)) {
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
        ServerLevel serverLevel = (ServerLevel) villager.level();
        if (villager.getTarget() instanceof LivingEntity target
                && VillagerRetaliationVillagerCombatUtil.isConcealedFromVillagers(target)) {
            clearAnger(villager);
        }

        if (!VillagerCombatLoadoutService.hasPersistentEquippedPreference(villager)) {
            HiredJobInventory.maintainEquipmentSlots(villager);
        }
        VillagerArmorerCombatTactics.ensureSpawnShieldRoll(villager);
        if (!VillagerClericPotionHelper.isActivelyHandlingPotion(villager)
                && VillagerRetaliationVillagerEquipment.isPlayerManagedMainHand(villager)) {
            VillagerRetaliationVillagerEquipment.maintainPlayerManagedMainHand(villager);
        }
        boolean maintainingSelectedLoadout = !VillagerInventoryAccess.hasOpenInventory(villager)
                && !VillagerClericPotionHelper.isActivelyHandlingPotion(villager)
                && VillagerCombatLoadoutService.maintainEquippedPreference(villager);
        if (!maintainingSelectedLoadout && shouldMaintainProfessionMainHand(villager, serverLevel.getGameTime())) {
            ensureProfessionMainHand(villager);
        }

        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_RETALIATION.get()) {
            clearAnger(villager);
            handlePassivePotionState(villager);
            return;
        }

        if (villager.isSleeping()) {
            handleSleepingCombatState(villager);
            return;
        }

        if (VillagerDisciplineService.tickVillager(villager)) {
            return;
        }

        if (com.jvn.villagerretaliation.party.PartyQuickCommandService.overridesCombatTargeting(villager)) {
            suppressCombatForPartyOrder(villager);
            handlePassivePotionState(villager);
            return;
        }

        RETALIATION.restorePersistedAngerIfNeeded(villager);
        com.jvn.villagerretaliation.party.PartyQuickCommandService.maintainManualAttackAuthorization(villager);
        tryAcquirePartyKillOnSightTarget(villager);
        tryAcquireHostileTarget(villager);

        ActiveRetaliationTarget retaliationTarget = VillagerRetaliationRetaliationUtil.resolveActiveRetaliationTarget(
                villager,
                RETALIATION,
                ACTOR_POLICY::canFightBack,
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
        boolean authorizedTarget = VillageCombatAuthorizationService.isAuthorized(villager, target);
        if (VillageAllegianceCombatPolicy.evaluate(
                level, villager, target, AllegianceCombatContext.TARGET_CONTINUATION, authorizedTarget).denied()) {
            clearAnger(villager);
            return;
        }
        long gameTime = retaliationTarget.gameTime();
        double distanceSqr = villager.distanceToSqr(target);
        if (isNaturalHostileTarget(villager, target)
                && !VillagerRetaliationVillagerRules.canStandGroundAgainstHostileMobs(villager)
                && !canBravelyStandGroundAgainst(level, villager, target)) {
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
            VillagerArmorerCombatTactics.resetState(villager);
            handlePassivePotionState(villager);
            VillagerRetaliationVillagerBrainUtil.stopNavigationAndClearPathing(villager);
            return;
        }
        wakeSleepingVillagerTargetForPartyAttacker(level, villager, target);
        if (!VillagerRetaliationRetaliationUtil.isWithinRetaliationPursuitRange(villager, target)) {
            clearAnger(villager);
            handlePassivePotionState(villager);
            return;
        }
        if (isHiredHunter(level, villager)
                && !VillagerRetaliationRetaliationUtil.hasClearLineOfSight(villager, target)) {
            clearAnger(villager, false);
            handlePassivePotionState(villager);
            return;
        }
        boolean waveringUnarmedCounter = isWaveringUnarmedCounter(villager, target, gameTime);
        if (shouldGiveUpWaveringUnarmedCounter(villager, target, gameTime)) {
            clearWaveringUnarmedCounter(villager, target);
            clearAnger(villager, false);
            enterFleeState(villager, target, gameTime);
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
            VillagerRetaliationVillagerBrainUtil.stopNavigationAndClearPathing(villager);
            return;
        }

        if (!waveringUnarmedCounter) {
            tryBorrowInventoryCombatWeapon(villager);
            if (tryAcquireGroundWeapon(villager, gameTime)) {
                return;
            }

            equipCombatWeapon(villager);
        }
        VillagerInteractionTracker.markGearReportsUsedInCombat(level, villager, hasEquippedWeaponGear(villager), hasEquippedArmorGear(villager));
        VillagerRetaliationRetaliationUtil.boostCombatMovement(villager);

        handleDefensiveRole(villager, gameTime);
        boolean meleeAttackReady = RETALIATION.isAttackReady(villager, gameTime);
        boolean allowMeleeAttack = VillagerArmorerCombatTactics.handleCombatTactics(villager, target, distanceSqr, gameTime, meleeAttackReady);

        if (VillagerClericPotionHelper.tryCombat(villager, target, level, distanceSqr)) {
            return;
        }
        if (VillagerRetaliationRetaliationUtil.isUsingRangedCombatMode(villager)
                && VillagerRangedCombatHelper.tryAttack(villager, target, level, distanceSqr)) {
            return;
        }

        if (VillagerRetaliationPotionUtil.shouldSuppressCombatWhileUsingPotion(villager)) {
            VillagerRetaliationVillagerBrainUtil.stopNavigationAndClearPathing(villager);
            return;
        }
        if (VillagerHostileTierHarass.tryThrow(villager, target, level, gameTime, distanceSqr)) {
            return;
        }

        double movementSpeed = ACTOR_POLICY.movementSpeed(villager)
                * VillagerArmorerCombatTactics.movementSpeedFactor(villager);
        boolean canUseMeleeCombat = VillagerRetaliationRetaliationUtil.canUseMeleeCombatMode(villager);
        boolean canMeleeHit = canUseMeleeCombat && VillagerRetaliationRetaliationUtil.canMeleeHit(villager, target);
        if (canMeleeHit) {
            VillagerRetaliationVillagerBrainUtil.stopNavigationAndClearPathing(villager);
            VillagerRetaliationRetaliationUtil.clearPathingState(villager);
        } else {
            VillagerRetaliationRetaliationUtil.moveTowardMeleeRetaliationTarget(villager, target, movementSpeed);
        }
        if (canMeleeHit && allowMeleeAttack && meleeAttackReady) {
            var attackHand = VillagerRetaliationVillagerCombatUtil.selectAttackHand(villager);
            villager.swing(attackHand, true);
            if (syncMeleeAttackAttributes(villager) && villager.doHurtTarget(target)) {
                VillagerEquipmentDurability.postMeleeHit(villager, target, attackHand);
            }
            RETALIATION.setNextAttackTick(
                    villager,
                    gameTime + VillagerSocialAttributeBehavior.adjustCombatCooldownTicks(
                            level,
                            villager,
                            ACTOR_POLICY.attackCooldown(villager)
                    )
            );
            VillagerArmorerCombatTactics.onMeleeAttackCommitted(villager);
            if (waveringUnarmedCounter) {
                clearWaveringUnarmedCounter(villager, target);
                clearAnger(villager, false, false);
                enterFleeState(villager, target, gameTime);
                if (target instanceof ServerPlayer player) {
                    tryTellNearbyVillagersAboutPlayerHit(villager, player, gameTime);
                }
            }
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

        if (PartyService.areInSameOrAlliedParty(villager, player)) {
            clearAnger(villager);
            return false;
        }

        if (VillagerRetaliationVillagerCombatUtil.isConcealedFromVillagers(player)) {
            RETALIATION.isHostileTowards(villager, player, () -> clearAnger(villager));
            return false;
        }

        if (player.isInvisible()) {
            return villager.getTarget() == player
                    || RETALIATION.isHostileTowards(villager, player, () -> clearAnger(villager));
        }

        if (isRoyaltyFor(villager, player)) {
            clearAnger(villager);
            return false;
        }

        return RETALIATION.isHostileTowards(villager, player, () -> clearAnger(villager))
                || isDespisedBy(villager, player);
    }

    public static boolean tryPacifyWithEmeralds(Villager villager, Player player, ItemStack interactionStack) {
        return tryPacifyWithPayment(villager, player, interactionStack);
    }

    public static VillagerPacificationResult pacifyWithEmeralds(Villager villager, Player player, ItemStack interactionStack) {
        return pacifyWithPayment(villager, player, interactionStack).result();
    }

    public static boolean tryPacifyWithPayment(Villager villager, Player player, ItemStack interactionStack) {
        return pacifyWithPayment(villager, player, interactionStack).handled();
    }

    public static VillagerPacificationAttempt pacifyWithPayment(Villager villager, Player player, ItemStack interactionStack) {
        if (villager.level().isClientSide
                || !villager.isAlive()
                || !player.isAlive()
                || !RETALIATION.isHostileTowards(villager, player, () -> clearAnger(villager))) {
            return VillagerPacificationAttempt.notApplicable();
        }

        Optional<PacifyPaymentOffer> payment = VillagerPacifyPaymentResources.offerFor(villager, interactionStack);
        if (payment.isEmpty()) {
            return VillagerPacificationAttempt.notApplicable();
        }

        if (isPacificationBlockedByReputation(villager, player)) {
            VillagerRetaliationRetaliationUtil.spawnMadParticles(villager);
            return VillagerPacificationAttempt.of(VillagerPacificationResult.BLOCKED_BY_REPUTATION, payment.get());
        }

        if (interactionStack.getCount() < payment.get().count()) {
            VillagerRetaliationRetaliationUtil.spawnPacifyFailureParticles(villager);
            return VillagerPacificationAttempt.of(VillagerPacificationResult.NOT_ENOUGH_EMERALDS, payment.get());
        }

        if (!player.hasInfiniteMaterials()) {
            interactionStack.shrink(payment.get().count());
        }
        clearAnger(villager);
        VillagerRetaliationRetaliationUtil.spawnPacifySuccessParticles(villager);
        return VillagerPacificationAttempt.of(VillagerPacificationResult.SUCCESS, payment.get());
    }

    private static boolean isDespisedBy(Villager villager, Player player) {
        return VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()
                && villager.level() instanceof ServerLevel level
                && VillagerReputationManager.isDespised(level, villager, player);
    }

    private static boolean isRoyaltyFor(Villager villager, Player player) {
        return VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()
                && villager.level() instanceof ServerLevel level
                && VillagerReputationManager.isRoyalty(level, villager, player);
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
        anger(villager, attacker, false, true);
    }

    public static void forceAngerSilently(Villager villager, LivingEntity attacker) {
        if (villager.level().isClientSide || villager.isBaby()) {
            return;
        }
        anger(villager, attacker, false, false);
    }

    public static boolean hasRetaliationTarget(Villager villager, LivingEntity target) {
        if (villager == null || target == null) {
            return false;
        }
        AngerTarget angerTarget = RETALIATION.angerTarget(villager);
        return angerTarget != null && angerTarget.targetId().equals(target.getUUID());
    }

    public static boolean hasActiveRetaliationTarget(Villager villager) {
        return villager != null && RETALIATION.hasAnger(villager);
    }

    private static void anger(Villager villager, LivingEntity attacker) {
        anger(villager, attacker, true, true);
    }

    public static boolean engageCustomTarget(Villager villager, LivingEntity target, boolean announceRetaliation) {
        if (villager == null
                || target == null
                || !villager.isAlive()
                || !target.isAlive()
                || VillagerRetaliationVillagerCombatUtil.isConcealedFromVillagers(target)
                || villager == target
                || PartyService.areInSameOrAlliedParty(villager, target)
                || !villager.canAttack(target)) {
            return false;
        }
        if (villager.level() instanceof ServerLevel level
                && VillageAllegianceCombatPolicy.evaluate(
                        level,
                        villager,
                        target,
                        AllegianceCombatContext.CUSTOM_TARGET,
                        VillageCombatAuthorizationService.isAuthorized(villager, target)).denied()) {
            return false;
        }
        anger(villager, target, false, announceRetaliation);
        return true;
    }

    private static void anger(Villager villager, LivingEntity attacker, boolean allowForcedDialogue, boolean announceRetaliation) {
        if (villager.isBaby()) {
            return;
        }
        if (PartyService.areInSameOrAlliedParty(villager, attacker)) {
            clearAnger(villager);
            return;
        }
        if (attacker instanceof Creeper creeper) {
            if (VillagerRetaliationConfig.VILLAGERS_FLEE_VISIBLE_CREEPERS.get()) {
                enterCreeperAvoidanceState(villager, creeper, villager.level().getGameTime());
            }
            return;
        }
        if (attacker instanceof ServerPlayer player
                && VillagerAggressionPolicy.shouldBypassAggroForRoyalty(villager, player)) {
            clearAnger(villager);
            sendRoyaltyAggroBypassNotice(villager, player);
            return;
        }
        if (allowForcedDialogue
                && attacker instanceof net.minecraft.server.level.ServerPlayer player
                && villager.level() instanceof ServerLevel level
                && ForcedDialogueService.triggerRetaliationStarted(level, villager, player)) {
            return;
        }
        AngerTarget previousTarget = RETALIATION.angerTarget(villager);
        if (RETALIATION.anger(villager, attacker) && villager.level() instanceof ServerLevel level) {
            VillagerMoodService.recordRetaliationStarted(level, villager, attacker);
            if ((previousTarget == null || !previousTarget.targetId().equals(attacker.getUUID()))
                    && announceRetaliation) {
                VillagerAmbientIndicatorService.onRetaliationStarted(level, villager, attacker);
            }
        }
    }

    private static void sendRoyaltyAggroBypassNotice(Villager villager, ServerPlayer player) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }
        long gameTime = level.getGameTime();
        PlayerVillagerKey key = new PlayerVillagerKey(player.getUUID(), villager.getUUID());
        if (gameTime < NEXT_ROYALTY_AGGRO_BYPASS_NOTICE_TICKS.getOrDefault(key, 0L)) {
            return;
        }
        NEXT_ROYALTY_AGGRO_BYPASS_NOTICE_TICKS.put(key, gameTime + ROYALTY_AGGRO_BYPASS_NOTICE_COOLDOWN_TICKS);
        VillagerInteractionService.sendVillagerNotice(player, villager, ROYALTY_AGGRO_BYPASS_MESSAGE_KEY);
    }

    private static void tryAcquireHostileTarget(Villager villager) {
        if (RETALIATION.hasAnger(villager)
                || !villager.isAlive()
                || (villager.level() instanceof ServerLevel level
                        && PartyService.isRecruitedPartyVillager(level, villager.getUUID()))
                || !VillagerRetaliationConfig.VILLAGERS_TARGET_HOSTILE_MOBS.get()) {
            return;
        }

        long gameTime = villager.level().getGameTime();
        if (!TickThrottle.consume(villager.getUUID(), NEXT_NATURAL_TARGET_SCAN_TICKS, gameTime, NATURAL_TARGET_SCAN_INTERVAL_TICKS)) {
            return;
        }
        if (!VillagerRetaliationVillagerRules.shouldSuppressFleeingBehavior(villager)
                || !ACTOR_POLICY.canFightBack(villager)) {
            return;
        }

        Optional<LivingEntity> memoryTarget = VillagerRetaliationVillagerCombatUtil.findNaturalHostileMemoryTarget(villager)
                .filter(target -> !shouldAvoidVisibleCreeper(villager, target));
        if (memoryTarget.isPresent()) {
            anger(villager, memoryTarget.get());
            return;
        }

        double naturalDefenseRadius = VillagerRetaliationConfig.NATURAL_HOSTILE_TARGET_RADIUS.get();
        VillagerRetaliationVillagerCombatUtil.findNearestNaturalHostile(villager, naturalDefenseRadius)
                .filter(target -> !shouldAvoidVisibleCreeper(villager, target))
                .ifPresent(target -> anger(villager, target));
    }

    private static void tryAcquirePartyKillOnSightTarget(Villager villager) {
        if (!villager.isAlive()
                || villager.isBaby()
                || com.jvn.villagerretaliation.party.PartyQuickCommandService.suppressesPartyTargetAcquisition(villager)) {
            return;
        }
        ServerLevel level = (ServerLevel) villager.level();
        PartyRecord party = PartyService.getPartyForVillager(level, villager.getUUID()).orElse(null);
        PartyVillagerRecord record = party == null ? null : party.villager(villager.getUUID());
        if (record == null
                || record.combatMode() != PartyCombatMode.KILL_ON_SIGHT
                || !ACTOR_POLICY.canFightBack(villager)) {
            return;
        }
        if (RETALIATION.hasAnger(villager)) {
            maintainPartyKillOnSightAuthorization(level, villager, party, record);
            return;
        }
        long gameTime = level.getGameTime();
        if (!TickThrottle.consume(
                villager.getUUID(),
                NEXT_PARTY_KOS_TARGET_SCAN_TICKS,
                gameTime,
                PARTY_KOS_TARGET_SCAN_INTERVAL_TICKS)) {
            return;
        }

        LivingEntity nearest = level.getEntitiesOfClass(
                        LivingEntity.class,
                        villager.getBoundingBox().inflate(PARTY_KOS_TARGET_RADIUS),
                        target -> isEligiblePartyKillOnSightTarget(level, villager, party, record, target))
                .stream()
                .min(java.util.Comparator.comparingDouble(villager::distanceToSqr))
                .orElse(null);
        if (nearest == null) {
            return;
        }
        if (!authorizePartyKillOnSightTarget(level, villager, nearest)) {
            return;
        }
        anger(villager, nearest, false, true);
    }

    private static void maintainPartyKillOnSightAuthorization(
            ServerLevel level,
            Villager villager,
            PartyRecord party,
            PartyVillagerRecord record) {
        AngerTarget angerTarget = RETALIATION.angerTarget(villager);
        if (angerTarget == null
                || !(level.getEntity(angerTarget.targetId()) instanceof LivingEntity target)
                || !isEligiblePartyKillOnSightTarget(level, villager, party, record, target)
                || VillageCombatAuthorizationService.isAuthorized(villager, target)) {
            return;
        }
        authorizePartyKillOnSightTarget(level, villager, target);
    }

    private static boolean authorizePartyKillOnSightTarget(
            ServerLevel level,
            Villager villager,
            LivingEntity target) {
        AllegianceCombatDecision decision = VillageAllegianceCombatPolicy.evaluate(
                level, villager, target, AllegianceCombatContext.PARTY_ATTACK, false);
        if (decision.denied()) {
            return false;
        }
        return decision.action() != AllegianceCombatDecision.Action.ALLOW
                || VillageCombatAuthorizationService.isAuthorized(villager, target)
                || VillageCombatAuthorizationService.authorize(level, villager, target);
    }

    private static boolean isEligiblePartyKillOnSightTarget(
            ServerLevel level,
            Villager villager,
            PartyRecord party,
            PartyVillagerRecord record,
            LivingEntity target) {
        if (target == villager
                || !target.isAlive()
                || VillagerRetaliationVillagerCombatUtil.isConcealedFromVillagers(target)
                || !villager.canAttack(target)
                || target.isAlliedTo(villager)
                || PartyService.areInSameOrAlliedParty(villager, target)
                || (target instanceof Villager targetVillager
                        && PartyVillagerContractService.hasExpiredContractWithParty(targetVillager, party.id()))
                || (target instanceof AbstractVillager && !(target instanceof Villager))
                || (target instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null)
                || (target instanceof TamableAnimal tamable && tamable.isTame())
                || (!(target instanceof Villager)
                        && !(target instanceof IronGolem)
                        && VillagerRetaliationVillagerCombatUtil.shouldIgnoreAttacker(target))
                || !VillagerRetaliationRetaliationUtil.hasClearLineOfSight(villager, target)
                || !attackModeAllows(record.attackMode(), villager, target)) {
            return false;
        }
        return !VillageAllegianceCombatPolicy.evaluate(
                level, villager, target, AllegianceCombatContext.PARTY_ATTACK, false).denied();
    }

    private static boolean isNaturalHostileTarget(Villager villager, LivingEntity target) {
        return VillagerRetaliationVillagerCombatUtil.isNaturalHostileTarget(villager, target);
    }

    private static boolean shouldAvoidVisibleCreeper(Villager villager, LivingEntity target) {
        return VillagerRetaliationConfig.VILLAGERS_FLEE_VISIBLE_CREEPERS.get()
                && target instanceof Creeper
                && villager.hasLineOfSight(target);
    }

    private static boolean canBravelyStandGroundAgainst(ServerLevel level, Villager villager, LivingEntity target) {
        return !(target instanceof Creeper)
                && ACTOR_POLICY.canFightBack(villager)
                && VillagerSocialAttributeBehavior.canBravelyStandGround(level, villager);
    }

    private static boolean tryFleeVisibleCreeper(Villager villager) {
        if (!VillagerRetaliationConfig.VILLAGERS_FLEE_VISIBLE_CREEPERS.get()) {
            return false;
        }

        long gameTime = villager.level().getGameTime();
        if (!TickThrottle.consume(villager.getUUID(), NEXT_CREEPER_AVOIDANCE_SCAN_TICKS, gameTime, CREEPER_AVOIDANCE_SCAN_INTERVAL_TICKS)) {
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
        VillagerRetaliationVillagerRules.clearCachedChecks(villager);
        enterFleeState(villager, creeper, gameTime);
    }

    private static void enterFleeState(Villager villager, LivingEntity hostile, long gameTime) {
        if (villager.level() instanceof ServerLevel level) {
            VillagerMoodService.recordFleeStarted(level, villager, hostile);
        }
        VillagerRetaliationVillagerBrainUtil.enterFleeState(villager, hostile, gameTime);
        villager.setAggressive(false);
        villager.setChasing(false);
        villager.setTarget(null);
    }

    private static boolean tryAcquireGroundWeapon(Villager villager, long gameTime) {
        if (!villager.isAlive()
                || !ACTOR_POLICY.canScavengeGroundWeapons(villager)
                || VillagerInventoryAccess.hasOpenInventory(villager)
                || !VillagerRetaliationVillagerCombatUtil.isThreatened(villager)) {
            return false;
        }

        return RETALIATION.tryAcquireGroundWeapon(
                villager,
                ACTOR_POLICY.movementSpeed(villager),
                () -> RETALIATION.discardTemporaryWeapon(villager),
                gameTime
        );
    }

    private static boolean tryBorrowInventoryCombatWeapon(Villager villager) {
        if (VillagerInventoryAccess.hasOpenInventory(villager)
                || VillagerClericPotionHelper.isActivelyHandlingPotion(villager)) {
            return false;
        }

        ItemStack weaponBeforePreference = VillagerRetaliationVillagerWeapons.getPrimaryWeapon(villager).copy();
        if (VillagerCombatLoadoutService.ensurePreferredWeapon(villager)) {
            RETALIATION.discardTemporaryWeapon(villager);
            ItemStack equippedWeapon = VillagerRetaliationVillagerWeapons.getPrimaryWeapon(villager);
            if (!ItemStack.isSameItemSameComponents(weaponBeforePreference, equippedWeapon)) {
                VillagerRangedCombatHelper.seedInitialAttackDelay(villager, equippedWeapon);
            }
            return true;
        }
        if (VillagerRetaliationVillagerWeapons.hasUsableWeapon(villager)) {
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
        NEXT_PARTY_KOS_TARGET_SCAN_TICKS.remove(villager.getUUID());
        VillagerHostileTierHarass.clearState(villager);
        VillagerArmorerCombatTactics.resetState(villager);
        VillagerRangedCombatHelper.clearState(villager);
        boolean preservePotionUse = VillagerClericPotionHelper.isDrinkingPotion(villager);
        if (preservePotionUse && RETALIATION.hasTemporaryWeapon(villager)) {
            VillagerClericPotionHelper.setPostDrinkMainHand(villager, RETALIATION.temporaryWeaponFallback(villager));
        }
        if (!preservePotionUse) {
            VillagerClericPotionHelper.clearAllState(villager);
        }
        VillagerRetaliationRetaliationUtil.restoreCombatMovement(villager);
        boolean keepSelectedLoadout = VillagerCombatLoadoutService.hasPersistentEquippedPreference(villager)
                && !VillagerInventoryAccess.hasOpenInventory(villager);
        if (keepSelectedLoadout && !preservePotionUse) {
            RETALIATION.restoreTemporaryWeapon(villager);
            VillagerCombatLoadoutService.maintainEquippedPreference(villager);
        } else if (restoreWeapon && !preservePotionUse) {
            RETALIATION.restoreTemporaryWeapon(villager);
            VillagerInventoryAccess.returnBorrowedCombatWeapon(villager);
        } else {
            RETALIATION.discardTemporaryWeapon(villager);
            VillagerInventoryAccess.clearBorrowedCombatWeapon(villager);
        }
        if (keepSelectedLoadout) {
            VillagerCombatLoadoutService.maintainEquippedPreference(villager);
        } else {
            VillagerRetaliationVillagerWeapons.maintainAcquiredWeaponAuthority(villager);
        }
        RETALIATION.clearTransientState(villager);
        villager.setAggressive(false);
        villager.setChasing(false);
        villager.setTarget(null);
        villager.setLastHurtByMob(null);
        if (stopNavigation) {
            VillagerRetaliationVillagerBrainUtil.stopNavigationAndClearPathing(villager);
        }
    }

    private static boolean shouldRetaliateAgainstAttacker(Villager villager, LivingEntity attacker) {
        if (VillagerRetaliationVillagerCombatUtil.isConcealedFromVillagers(attacker)
                || villager != null && PartyService.areInSameOrAlliedParty(villager, attacker)) {
            return false;
        }
        return VillagerRetaliationConfig.VILLAGERS_RETALIATE_AGAINST_HOSTILE_MOBS.get()
                || !isHostileMobAttacker(villager, attacker);
    }

    private static void rallyPartyVillagers(Entity partyMember, LivingEntity target, boolean attackingWithParty) {
        if (!(partyMember.level() instanceof ServerLevel level)
                || partyMember == target
                || PartyService.areInSameOrAlliedParty(partyMember, target)) {
            return;
        }
        PartyRecord party = PartyService.getPartyForEntity(partyMember).orElse(null);
        if (party == null) {
            return;
        }
        for (PartyVillagerRecord member : party.villagers()) {
            if (member.combatMode() != PartyCombatMode.ATTACK_WITH_PARTY) {
                continue;
            }
            Entity entity = level.getEntity(member.villagerId());
            if (!(entity instanceof Villager villager)
                    || villager == partyMember
                    || villager.isBaby()
                    || !villager.isAlive()
                    || com.jvn.villagerretaliation.party.PartyQuickCommandService.suppressesPartyTargetAcquisition(villager)
                    || !canWitnessRetaliationEvent(villager, partyMember)
                    || (attackingWithParty && !attackModeAllows(member.attackMode(), villager, target))
                    || !shouldRetaliateAgainstAttacker(villager, target)) {
                continue;
            }
            AllegianceCombatContext context = attackingWithParty
                    ? AllegianceCombatContext.PARTY_ATTACK
                    : AllegianceCombatContext.PARTY_DEFEND;
            var decision = VillageAllegianceCombatPolicy.evaluate(
                    level, villager, target, context, false);
            if (decision.denied()) {
                if (AllegianceEntityClassifier.protectedCivilian(target)
                        && decision.reason() == AllegianceCombatDecision.Reason.SAME_CANONICAL_ALLEGIANCE) {
                    UUID responsiblePlayer = partyMember instanceof Player
                            ? partyMember.getUUID()
                            : party.leaderId();
                    UnlawfulOrderService.record(level, villager, responsiblePlayer, target.getUUID());
                }
                continue;
            }
            if (decision.action() == AllegianceCombatDecision.Action.ALLOW
                    && !VillageCombatAuthorizationService.authorize(level, villager, target)) {
                continue;
            }
            anger(villager, target);
        }
    }

    private static boolean attackModeAllows(PartyAttackMode mode, Villager villager, LivingEntity target) {
        PartyAttackMode resolved = mode == null ? PartyAttackMode.ALL : mode;
        return resolved.allows(
                target instanceof Animal,
                VillagerRetaliationVillagerCombatUtil.isNaturalHostileTarget(villager, target),
                target instanceof Player,
                target instanceof Villager,
                target instanceof IronGolem,
                PartyService.getPartyForEntity(target).isPresent());
    }

    private static boolean isHiredHunter(ServerLevel level, Villager villager) {
        return HiredVillagerContractService.activeRole(level, villager) == HiredVillagerRole.HUNTING;
    }

    private static boolean tryHandleUnarmedLowGutsPlayerHit(Villager villager, LivingEntity attacker) {
        if (!(attacker instanceof ServerPlayer player)
                || !(villager.level() instanceof ServerLevel level)
                || !VillagerSocialAttributeBehavior.enabled(VillagerRetaliationConfig.ENABLE_SOCIAL_ATTRIBUTE_RETALIATION_EFFECTS)
                || !isUnarmedForGutsResponse(villager)) {
            return false;
        }

        int guts = VillagerSocialAttributeBehavior.value(level, villager, VillagerSocialAttribute.GUTS);
        if (guts <= VERY_LOW_GUTS_RALLY_THRESHOLD) {
            long gameTime = level.getGameTime();
            tryTellNearbyVillagersAboutPlayerHit(villager, player, gameTime);
            clearAnger(villager, false);
            enterFleeState(villager, player, level.getGameTime());
            return true;
        }

        if (guts <= LOW_GUTS_FLEE_THRESHOLD) {
            clearAnger(villager, false);
            enterFleeState(villager, player, level.getGameTime());
            return true;
        }

        if (guts <= WAVERING_GUTS_COUNTER_THRESHOLD) {
            WAVERING_UNARMED_COUNTERS.put(
                    new PlayerVillagerKey(player.getUUID(), villager.getUUID()),
                    level.getGameTime() + WAVERING_UNARMED_COUNTER_GIVE_UP_TICKS);
            anger(villager, player, false, true);
            return true;
        }

        return false;
    }

    private static boolean tryTellNearbyVillagersAboutPlayerHit(Villager villager, ServerPlayer player, long gameTime) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return false;
        }

        PlayerVillagerKey key = new PlayerVillagerKey(player.getUUID(), villager.getUUID());
        if (gameTime < LOW_GUTS_RALLY_USED_UNTIL_TICKS.getOrDefault(key, 0L)) {
            return false;
        }

        boolean confronted = ForcedDialogueService.triggerLowGutsRetaliationRally(level, villager, player);
        boolean rallied = confronted;
        if (!confronted && !VillagerRetaliationConfig.ATTACK_AGGROS_ONLY_HIT_VILLAGER.get()) {
            rallied = angerNearbyVillagersIfAny(villager, player, VillagerRetaliationConfig.VILLAGER_KILL_AGGRO_RADIUS.get());
        }
        if (rallied) {
            LOW_GUTS_RALLY_USED_UNTIL_TICKS.put(
                    key,
                    gameTime + Math.max(20L, VillagerRetaliationConfig.AGGRO_DURATION_TICKS.get()));
        }
        pruneLowGutsRallyState(gameTime);
        return rallied;
    }

    private static boolean isUnarmedForGutsResponse(Villager villager) {
        return !VillagerRetaliationVillagerWeapons.hasUsableWeapon(villager)
                && !VillagerInventoryAccess.hasBorrowedCombatWeapon(villager)
                && !VillagerInventoryAccess.hasUsableWeapon(villager);
    }

    private static boolean isWaveringUnarmedCounter(Villager villager, LivingEntity target, long gameTime) {
        if (!(target instanceof Player player)) {
            return false;
        }
        Long giveUpTick = WAVERING_UNARMED_COUNTERS.get(new PlayerVillagerKey(player.getUUID(), villager.getUUID()));
        return giveUpTick != null && gameTime <= giveUpTick;
    }

    private static boolean shouldGiveUpWaveringUnarmedCounter(Villager villager, LivingEntity target, long gameTime) {
        if (!(target instanceof Player player)) {
            return false;
        }
        Long giveUpTick = WAVERING_UNARMED_COUNTERS.get(new PlayerVillagerKey(player.getUUID(), villager.getUUID()));
        return giveUpTick != null && gameTime > giveUpTick;
    }

    private static void clearWaveringUnarmedCounter(Villager villager, LivingEntity target) {
        if (target instanceof Player player) {
            WAVERING_UNARMED_COUNTERS.remove(new PlayerVillagerKey(player.getUUID(), villager.getUUID()));
        }
    }

    private static void pruneLowGutsRallyState(long gameTime) {
        LOW_GUTS_RALLY_USED_UNTIL_TICKS.entrySet().removeIf(entry -> entry.getValue() < gameTime);
    }

    private static boolean isHostileMobAttacker(Villager villager, LivingEntity attacker) {
        if (attacker instanceof Creeper) {
            return true;
        }
        if (villager != null) {
            return VillagerRetaliationVillagerCombatUtil.isNaturalHostileTarget(villager, attacker);
        }
        return !VillagerRetaliationVillagerCombatUtil.shouldIgnoreAttacker(attacker)
                && VillagerRetaliationVillagerCombatUtil.isHostileMobType(attacker);
    }

    private static boolean shouldSuppressFleeingForRetaliation(Villager villager) {
        if (!RETALIATION.hasAnger(villager) || !ACTOR_POLICY.canFightBack(villager)) {
            return false;
        }
        if (!(villager.level() instanceof ServerLevel level)) {
            return true;
        }

        AngerTarget angerTarget = RETALIATION.angerTarget(villager);
        if (angerTarget == null) {
            return false;
        }
        Entity targetEntity = level.getEntity(angerTarget.targetId());
        if (!(targetEntity instanceof LivingEntity target)) {
            return true;
        }
        if (target instanceof Creeper) {
            return false;
        }
        return !isNaturalHostileTarget(villager, target)
                || VillagerRetaliationVillagerRules.canStandGroundAgainstHostileMobs(villager);
    }

    private static boolean syncMeleeAttackAttributes(Villager villager) {
        AttributeInstance attackDamage = villager.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage == null || villager.getAttribute(Attributes.ATTACK_KNOCKBACK) == null) {
            return false;
        }

        double desiredBaseDamage = ACTOR_POLICY.meleeAttackDamageBase(villager);
        if (attackDamage.getBaseValue() != desiredBaseDamage) {
            attackDamage.setBaseValue(desiredBaseDamage);
        }
        return true;
    }

    private static void angerNearbyVillagers(Entity sourceEntity, LivingEntity attacker, double radius) {
        angerNearbyVillagers(sourceEntity, attacker, radius, false, true);
    }

    private static boolean angerNearbyVillagersIfAny(Entity sourceEntity, LivingEntity attacker, double radius) {
        if (!(sourceEntity.level() instanceof ServerLevel level)) {
            return false;
        }

        boolean angeredAny = false;
        AABB area = sourceEntity.getBoundingBox().inflate(radius);
        for (Villager nearby : level.getEntitiesOfClass(Villager.class, area)) {
            if (nearby != sourceEntity
                    && !nearby.isBaby()
                    && belongsToHarmedCommunity(level, nearby, sourceEntity)
                    && canWitnessRetaliationEvent(nearby, sourceEntity)
                    && shouldAggroFromWitness(nearby, attacker, false)) {
                anger(nearby, attacker);
                angeredAny = true;
            }
        }
        return angeredAny;
    }

    private static void angerNearbyVillagers(Entity sourceEntity, LivingEntity attacker, double radius, boolean witnessedVillagerKill) {
        angerNearbyVillagers(sourceEntity, attacker, radius, witnessedVillagerKill, true);
    }

    private static void angerNearbyVillagers(
            Entity sourceEntity,
            LivingEntity attacker,
            double radius,
            boolean witnessedVillagerKill,
            boolean announceRetaliation) {
        if (!(sourceEntity.level() instanceof ServerLevel level)) {
            return;
        }

        AABB area = sourceEntity.getBoundingBox().inflate(radius);
        for (Villager nearby : level.getEntitiesOfClass(Villager.class, area)) {
            if (nearby != sourceEntity
                    && !nearby.isBaby()
                    && belongsToHarmedCommunity(level, nearby, sourceEntity)
                    && canWitnessRetaliationEvent(nearby, sourceEntity)
                    && shouldAggroFromWitness(nearby, attacker, witnessedVillagerKill)) {
                anger(nearby, attacker, announceRetaliation, announceRetaliation);
            }
        }
    }

    private static List<Villager> witnessVillagersNear(Entity sourceEntity, double radius) {
        if (!(sourceEntity.level() instanceof ServerLevel level)) {
            return List.of();
        }

        List<Villager> witnesses = new ArrayList<>();
        AABB area = sourceEntity.getBoundingBox().inflate(radius);
        for (Villager nearby : level.getEntitiesOfClass(Villager.class, area)) {
            if (nearby != sourceEntity
                    && belongsToHarmedCommunity(level, nearby, sourceEntity)
                    && canWitnessRetaliationEvent(nearby, sourceEntity)) {
                witnesses.add(nearby);
            }
        }
        return witnesses;
    }

    private static void angerWitnessVillagers(
            List<Villager> witnesses,
            LivingEntity attacker,
            boolean witnessedVillagerKill) {
        for (Villager witness : witnesses) {
            if (!witness.isBaby() && shouldAggroFromWitness(witness, attacker, witnessedVillagerKill)) {
                anger(witness, attacker);
            }
        }
    }

    private static void rallyFromNitwitWitnesses(
            List<Villager> witnesses,
            LivingEntity attacker,
            boolean witnessedVillagerKill) {
        long gameTime = attacker.level().getGameTime();
        boolean rallied = false;
        for (Villager witness : witnesses) {
            if (!isNitwitAlarm(witness)) {
                continue;
            }
            VillagerRetaliationVillagerBrainUtil.enterFleeState(witness, attacker, gameTime);
            rallied = true;
        }
        if (rallied) {
            angerWitnessVillagers(witnesses, attacker, witnessedVillagerKill);
        }
    }

    private static boolean shouldAggroFromWitness(Villager witness, LivingEntity attacker, boolean witnessedVillagerKill) {
        if (PartyService.areInSameOrAlliedParty(witness, attacker)) {
            return false;
        }
        if (!(attacker instanceof Player player)) {
            return true;
        }
        int pendingReputationChange = witnessedVillagerKill ? VillagerRetaliationConfig.WITNESSED_KILL_PENALTY.get() : 0;
        return VillagerAggressionPolicy.shouldAggroFromWitnessedPlayerCrime(witness, player, pendingReputationChange);
    }

    private static boolean belongsToHarmedCommunity(ServerLevel level, Villager witness, Entity sourceEntity) {
        if (!AllegianceEntityClassifier.bearsAllegiance(sourceEntity)) {
            return true;
        }
        return VillageAllegianceRelations.sharesCommunity(level, sourceEntity, witness);
    }

    private static void rallyNearbyVillagers(Villager alarmVillager, LivingEntity attacker, double radius) {
        rallyNearbyVillagers(alarmVillager, attacker, radius, true);
    }

    private static void rallyNearbyVillagers(
            Villager alarmVillager,
            LivingEntity attacker,
            double radius,
            boolean announceRetaliation) {
        // Keep alarm villagers in panic/flee behavior while still spreading the threat to fighters.
        long gameTime = alarmVillager.level().getGameTime();
        VillagerRetaliationVillagerBrainUtil.enterFleeState(alarmVillager, attacker, gameTime);
        angerNearbyVillagers(alarmVillager, attacker, radius, false, announceRetaliation);
        WanderingTraderRetaliationHandler.angerNearbyTradersFrom(alarmVillager, attacker, radius);
    }

    private static void rallyFromNearbyNitwits(Entity sourceEntity, LivingEntity attacker, double radius) {
        if (!(sourceEntity.level() instanceof ServerLevel level)) {
            return;
        }

        AABB area = sourceEntity.getBoundingBox().inflate(radius);
        for (Villager nearby : level.getEntitiesOfClass(Villager.class, area)) {
            if (isNitwitAlarm(nearby)
                    && belongsToHarmedCommunity(level, nearby, sourceEntity)
                    && canWitnessRetaliationEvent(nearby, sourceEntity)) {
                rallyNearbyVillagers(nearby, attacker, radius);
            }
        }
    }

    private static void triggerNitwitWitnessedDeathFlee(List<Villager> witnesses, LivingEntity attacker) {
        long gameTime = attacker == null ? 0L : attacker.level().getGameTime();
        for (Villager nearby : witnesses) {
            if (!isWitnessAlarmVillager(nearby)) {
                continue;
            }

            if (attacker != null) {
                VillagerRetaliationVillagerBrainUtil.enterFleeState(nearby, attacker, gameTime);
            } else {
                VillagerRetaliationVillagerBrainUtil.stopNavigationAndClearPathing(nearby);
            }
        }
    }

    private static boolean isNitwitAlarm(Villager villager) {
        return !villager.isBaby() && villager.getVillagerData().getProfession() == VillagerProfession.NITWIT;
    }

    private static boolean isWitnessAlarmVillager(Villager villager) {
        return isNitwitAlarm(villager)
                || villager.isBaby() && VillagerRetaliationConfig.BABY_VILLAGERS_FLEE_WITNESSED_DEATHS.get();
    }

    private static boolean canWitnessRetaliationEvent(Villager witness, Entity sourceEntity) {
        return !VillagerRetaliationConfig.RETALIATION_WITNESSES_REQUIRE_LINE_OF_SIGHT.get()
                || witness.hasLineOfSight(sourceEntity);
    }

    private static void suppressVanillaPanic(Villager villager) {
        if (villager.level() instanceof ServerLevel level) {
            VillagerRetaliationVillagerBrainUtil.suppressVanillaFleeState(level, villager);
        } else {
            VillagerRetaliationVillagerBrainUtil.clearFleeMemories(villager);
            VillagerRetaliationVillagerBrainUtil.stopNavigationAndClearMovement(villager);
        }
    }

    private static void handleDefensiveRole(Villager villager, long gameTime) {
        if (gameTime < NEXT_SPECIAL_TICKS.getOrDefault(villager.getUUID(), 0L)) {
            return;
        }

        // Recovery consumables are handled by VillagerRecoveryService for every profession.
    }

    private static void handleSleepingCombatState(Villager villager) {
        villager.setAggressive(false);
        villager.setChasing(false);
        villager.setTarget(null);
        VillagerArmorerCombatTactics.resetState(villager);
        VillagerRangedCombatHelper.clearState(villager);
        VillagerClericPotionHelper.clearState(villager);
        VillagerRetaliationRetaliationUtil.restoreCombatMovement(villager);
        RETALIATION.restoreTemporaryWeapon(villager);
        VillagerInventoryAccess.returnBorrowedCombatWeapon(villager);
        VillagerRetaliationVillagerBrainUtil.stopNavigationAndClearPathing(villager);
    }

    private static void wakeSleepingVillagerTargetForPartyAttacker(
            ServerLevel level,
            Villager attacker,
            LivingEntity target) {
        if (target instanceof Villager targetVillager
                && targetVillager.isSleeping()
                && PartyService.isRecruitedPartyVillager(level, attacker.getUUID())) {
            targetVillager.stopSleeping();
        }
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

        if (VillagerCombatLoadoutService.ensurePreferredWeapon(villager)) {
            RETALIATION.discardTemporaryWeapon(villager);
            return;
        }
        if (VillagerCombatLoadoutService.preference(villager)
                != com.jvn.villagerretaliation.party.PartyWeaponPreference.AUTO) {
            return;
        }

        if (VillagerInventoryAccess.maintainBorrowedCombatWeapon(villager)) {
            RETALIATION.discardTemporaryWeapon(villager);
            return;
        }

        if (RETALIATION.maintainTemporaryWeapon(villager)) {
            return;
        }

        if (VillagerRetaliationVillagerWeapons.maintainAcquiredWeaponAuthority(villager)) {
            RETALIATION.discardTemporaryWeapon(villager);
            return;
        }

        if (VillagerRetaliationVillagerWeapons.hasUsableWeapon(villager)) {
            return;
        }

        if (tryBorrowInventoryCombatWeapon(villager)) {
            return;
        }
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
                || VillagerCombatLoadoutService.hasPersistentEquippedPreference(villager)
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

    private static void handlePassivePotionState(Villager villager) {
        VillagerArmorerCombatTactics.resetStateIfActive(villager);
        clearRangedStateIfActive(villager);
        if (VillagerInventoryAccess.hasOpenInventory(villager)) {
            suspendCombatForOpenInventory(villager);
            return;
        }

        if (VillagerClericPotionHelper.tickDrinkingIfActive(villager)) {
            VillagerRetaliationVillagerBrainUtil.stopNavigationAndClearPathing(villager);
            VillagerRetaliationRetaliationUtil.restoreCombatMovement(villager);
            return;
        }

        VillagerRetaliationRetaliationUtil.restoreCombatMovement(villager);
        restoreTemporaryWeaponIfActive(villager);
        returnBorrowedCombatWeaponIfActive(villager);
        if (VillagerClericPotionHelper.tryOutOfCombatMilk(villager)) {
            return;
        }
        if (villager.level() instanceof ServerLevel level
                && VillagerClericPotionHelper.tryOutOfCombatSupport(villager, level)) {
            return;
        }
        if (villager.level() instanceof ServerLevel level
                && VillagerSmithGolemRepairSupport.tryRepairNearbyIronGolem(
                villager,
                level,
                level.getGameTime(),
                ACTOR_POLICY.movementSpeed(villager))) {
            return;
        }

        VillagerClericPotionHelper.clearState(villager);
    }

    private static void suspendCombatForOpenInventory(Villager villager) {
        VillagerRetaliationVillagerBrainUtil.stopNavigationAndClearPathing(villager);
        VillagerArmorerCombatTactics.resetState(villager);
        VillagerRangedCombatHelper.clearState(villager);
        VillagerClericPotionHelper.clearState(villager);
        VillagerRetaliationRetaliationUtil.restoreCombatMovement(villager);
        RETALIATION.restoreTemporaryWeapon(villager);
        VillagerInventoryAccess.returnBorrowedCombatWeapon(villager);
    }

    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }

        clearRuntimeState(villager);
    }

    private static void clearRuntimeState(Villager villager) {
        VillagerArmorerCombatTactics.resetState(villager);
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
        NEXT_PARTY_KOS_TARGET_SCAN_TICKS.remove(villager.getUUID());
        NEXT_CREEPER_AVOIDANCE_SCAN_TICKS.remove(villager.getUUID());
        NEXT_ROLE_MAINHAND_MAINTENANCE_TICKS.remove(villager.getUUID());
        VillagerHostileTierHarass.clearState(villager);
        VillagerSmithGolemRepairSupport.clearState(villager);
        NEXT_ROYALTY_AGGRO_BYPASS_NOTICE_TICKS.keySet().removeIf(key -> key.villagerId().equals(villager.getUUID()));
        WAVERING_UNARMED_COUNTERS.keySet().removeIf(key -> key.villagerId().equals(villager.getUUID()));
        LOW_GUTS_RALLY_USED_UNTIL_TICKS.keySet().removeIf(key -> key.villagerId().equals(villager.getUUID()));
        if (villager.isAlive()) {
            VillagerRetaliationVillagerWeapons.clearTrackedPickupCache(villager);
        } else {
            VillagerRetaliationVillagerWeapons.clearTrackedPickup(villager);
        }
    }

    public static void clearRuntimeState(net.minecraft.server.MinecraftServer server) {
        if (server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof Villager villager) {
                        clearRuntimeState(villager);
                    }
                }
            }
        }
        RETALIATION.clearRuntimeState();
        NEXT_SPECIAL_TICKS.clear();
        NEXT_NATURAL_TARGET_SCAN_TICKS.clear();
        NEXT_PARTY_KOS_TARGET_SCAN_TICKS.clear();
        NEXT_CREEPER_AVOIDANCE_SCAN_TICKS.clear();
        NEXT_ROLE_MAINHAND_MAINTENANCE_TICKS.clear();
        NEXT_ROYALTY_AGGRO_BYPASS_NOTICE_TICKS.clear();
        WAVERING_UNARMED_COUNTERS.clear();
        LOW_GUTS_RALLY_USED_UNTIL_TICKS.clear();
        VillagerArmorerCombatTactics.clearRuntimeState();
        VillagerRangedCombatHelper.clearRuntimeState();
        VillagerClericPotionHelper.clearRuntimeState();
        VillagerHostileTierHarass.clearRuntimeState();
        VillagerSmithGolemRepairSupport.clearRuntimeState();
        VillagerRetaliationRetaliationUtil.clearRuntimeState();
        VillagerRetaliationVillagerWeapons.clearCache();
    }

    private static void clearRangedStateIfActive(Villager villager) {
        if (VillagerRangedCombatHelper.hasState(villager) || villager.isUsingItem()) {
            VillagerRangedCombatHelper.clearState(villager);
        }
    }

    private static void restoreTemporaryWeaponIfActive(Villager villager) {
        if (RETALIATION.hasTemporaryWeapon(villager)) {
            RETALIATION.restoreTemporaryWeapon(villager);
        }
    }

    private static void returnBorrowedCombatWeaponIfActive(Villager villager) {
        if (VillagerInventoryAccess.hasBorrowedCombatWeapon(villager)
                && !VillagerCombatLoadoutService.hasPersistentEquippedPreference(villager)) {
            VillagerInventoryAccess.returnBorrowedCombatWeapon(villager);
        }
    }

    private static boolean shouldMaintainProfessionMainHand(Villager villager, long gameTime) {
        return TickThrottle.consume(
                villager.getUUID(),
                NEXT_ROLE_MAINHAND_MAINTENANCE_TICKS,
                gameTime,
                ROLE_MAINHAND_MAINTENANCE_INTERVAL_TICKS);
    }

    private record PlayerVillagerKey(UUID playerId, UUID villagerId) {
    }
}
