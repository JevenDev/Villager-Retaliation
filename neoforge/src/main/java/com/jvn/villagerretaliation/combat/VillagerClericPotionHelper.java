package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRelations;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.util.TickThrottle;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.AABB;

final class VillagerClericPotionHelper {
    private static final Map<UUID, Integer> ATTACK_DELAY = new HashMap<>();
    private static final Map<UUID, PotionUseState> DRINKING_POTIONS = new HashMap<>();
    private static final Map<UUID, Long> HEALING_REDRINK_COOLDOWN_UNTIL = new HashMap<>();
    private static final double MAX_THROW_DISTANCE_SQR = 144.0D;
    private static final int CARRIED_THROW_INTERVAL_TICKS = 60;
    private static final int GENERATED_THROW_INTERVAL_TICKS = 300;
    private static final int COMBAT_SUPPORT_SCAN_INTERVAL_TICKS = 10;
    private static final int SUPPORT_TARGET_CACHE_TICKS = 10;
    private static final int PASSIVE_SUPPORT_SCAN_INTERVAL_TICKS = 20;
    private static final int CARRIED_HEALING_REDRINK_COOLDOWN_TICKS = 40;
    private static final int GENERATED_HEALING_REDRINK_COOLDOWN_TICKS = 300;
    private static final int FIRE_RESISTANCE_TICKS = 20 * 180;
    private static final int WATER_BREATHING_TICKS = 20 * 180;
    private static final int SWIFTNESS_TICKS = 20 * 180;
    private static final float FIRE_RESISTANCE_TRIGGER_CHANCE = 0.15F;
    private static final float WATER_BREATHING_TRIGGER_CHANCE = 0.15F;
    private static final float HEALING_TRIGGER_CHANCE = 0.05F;
    private static final float SWIFTNESS_TRIGGER_CHANCE = 0.5F;
    private static final double SPLASH_RADIUS = 4.0D;
    private static final float SUPPORT_HEAL_HEALTH_RATIO = 0.6F;
    private static final float SUPPORT_HEAL_MIN_MISSING_HEALTH = 4.0F;
    private static final Map<UUID, Long> NEXT_COMBAT_SUPPORT_SCAN_TICKS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_PASSIVE_SUPPORT_SCAN_TICKS = new HashMap<>();
    private static final Map<UUID, SupportTargetState> SUPPORT_TARGET_CACHE = new HashMap<>();

    private VillagerClericPotionHelper() {
    }

    static boolean tryCombat(Villager villager, LivingEntity target, ServerLevel level, double distanceSqr) {
        if (isDrinkingPotion(villager)) {
            return tickPotionDrinking(villager);
        }

        if (canUseMilkBucket(villager) && hasMilkCurableHarmfulEffect(villager)) {
            if (startPotionDrinking(villager, ClericSelfPotion.MILK_BUCKET)) return true;
        }

        if (!canUseClericPotions(villager)) {
            return false;
        }

        ClericSelfPotion selfPotion = chooseSelfPotion(villager, distanceSqr);
        if (selfPotion != ClericSelfPotion.NONE) {
            if (startPotionDrinking(villager, selfPotion)) return true;
        }

        PotionThrowPlan throwPlan = chooseThrowPlan(villager, target, level, distanceSqr);
        int attackDelay = ATTACK_DELAY.getOrDefault(villager.getUUID(), 0);
        if (attackDelay > 0) {
            ATTACK_DELAY.put(villager.getUUID(), attackDelay - 1);
            villager.getNavigation().moveTo(
                    throwPlan != null ? throwPlan.aimTarget() : target,
                    VillagerCombatRoles.movementSpeed(villager) * 0.8D
            );
            return true;
        }

        if (throwPlan == null) {
            if (!villager.hasLineOfSight(target) || distanceSqr > MAX_THROW_DISTANCE_SQR) {
                villager.getNavigation().moveTo(target, VillagerCombatRoles.movementSpeed(villager));
                return true;
            }
            return false;
        }

        LivingEntity aimTarget = throwPlan.aimTarget();
        double aimDistanceSqr = villager.distanceToSqr(aimTarget);
        if (!villager.hasLineOfSight(aimTarget) || aimDistanceSqr > MAX_THROW_DISTANCE_SQR) {
            villager.getNavigation().moveTo(aimTarget, VillagerCombatRoles.movementSpeed(villager));
            return true;
        }

        if (!throwSplashPotionLikeWitch(villager, aimTarget, level, throwPlan.potionStack())) return false;
        ATTACK_DELAY.put(villager.getUUID(), throwCooldown(villager));
        return true;
    }

    static void clearState(Villager villager) {
        ATTACK_DELAY.remove(villager.getUUID());
        DRINKING_POTIONS.remove(villager.getUUID());
    }

    static void clearAllState(Villager villager) {
        clearState(villager);
        NEXT_COMBAT_SUPPORT_SCAN_TICKS.remove(villager.getUUID());
        NEXT_PASSIVE_SUPPORT_SCAN_TICKS.remove(villager.getUUID());
        HEALING_REDRINK_COOLDOWN_UNTIL.remove(villager.getUUID());
        SUPPORT_TARGET_CACHE.remove(villager.getUUID());
    }

    static void clearRuntimeState() {
        ATTACK_DELAY.clear();
        DRINKING_POTIONS.clear();
        HEALING_REDRINK_COOLDOWN_UNTIL.clear();
        NEXT_COMBAT_SUPPORT_SCAN_TICKS.clear();
        NEXT_PASSIVE_SUPPORT_SCAN_TICKS.clear();
        SUPPORT_TARGET_CACHE.clear();
    }

    static boolean tickDrinkingIfActive(Villager villager) {
        if (!isDrinkingPotion(villager)) {
            return false;
        }
        return tickPotionDrinking(villager);
    }

    static boolean tryOutOfCombatMilk(Villager villager) {
        if (!canUseOutOfCombatMilk(villager)) {
            return false;
        }
        if (isDrinkingPotion(villager)) {
            return tickPotionDrinking(villager);
        }
        if (!hasMilkCurableHarmfulEffect(villager)) {
            return false;
        }

        return startPotionDrinking(villager, ClericSelfPotion.MILK_BUCKET);
    }

    static boolean tryOutOfCombatSupport(Villager villager, ServerLevel level) {
        if (!canUseClericPotions(villager)) {
            return false;
        }
        if (isDrinkingPotion(villager)) {
            return tickPotionDrinking(villager);
        }

        ClericSelfPotion selfPotion = chooseSelfPotion(villager, 0.0D);
        if (selfPotion != ClericSelfPotion.NONE) {
            if (startPotionDrinking(villager, selfPotion)) return true;
        }

        int attackDelay = ATTACK_DELAY.getOrDefault(villager.getUUID(), 0);
        if (attackDelay > 0) {
            ATTACK_DELAY.put(villager.getUUID(), attackDelay - 1);
            return true;
        }

        if (!consumePassiveSupportScanSlot(villager, level.getGameTime())) {
            return false;
        }

        double passiveRange = VillagerRetaliationConfig.PASSIVE_CLERIC_ALLY_HEAL_RANGE.get();
        LivingEntity supportTarget = findSupportTarget(
                villager,
                level,
                passiveRange * passiveRange,
                VillagerRetaliationConfig.PASSIVE_CLERIC_ALLY_HEAL_HEALTH_THRESHOLD.get().floatValue(),
                VillagerRetaliationConfig.PASSIVE_CLERIC_ALLY_HEAL_REQUIRES_LINE_OF_SIGHT.get()
        );
        if (supportTarget == null) {
            return false;
        }

        double distanceSqr = villager.distanceToSqr(supportTarget);
        if (!villager.hasLineOfSight(supportTarget) || distanceSqr > MAX_THROW_DISTANCE_SQR) {
            villager.getNavigation().moveTo(supportTarget, VillagerCombatRoles.movementSpeed(villager) * 0.6D);
            return true;
        }

        if (!throwSplashPotionLikeWitch(
                villager,
                supportTarget,
                level,
                PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING)
        )) return false;
        ATTACK_DELAY.put(villager.getUUID(), throwCooldown(villager));
        return true;
    }

    static boolean isActivelyHandlingPotion(Villager villager) {
        if (!canUseMilkBucket(villager) && !canUseClericPotions(villager)) {
            return false;
        }
        if (isDrinkingPotion(villager)) {
            return true;
        }
        if (villager.isUsingItem() && VillagerRetaliationPotionUtil.isDrinkableCombatConsumable(villager.getUseItem())) {
            return true;
        }

        ItemStack mainHand = villager.getMainHandItem();
        return VillagerRetaliationPotionUtil.isPotion(mainHand) || mainHand.is(Items.MILK_BUCKET);
    }

    static boolean isDrinkingPotion(Villager villager) {
        return DRINKING_POTIONS.containsKey(villager.getUUID());
    }

    private static boolean consumePassiveSupportScanSlot(Villager villager, long gameTime) {
        return consumeSupportScanSlot(villager, gameTime, NEXT_PASSIVE_SUPPORT_SCAN_TICKS, PASSIVE_SUPPORT_SCAN_INTERVAL_TICKS);
    }

    private static boolean consumeCombatSupportScanSlot(Villager villager, long gameTime) {
        return consumeSupportScanSlot(villager, gameTime, NEXT_COMBAT_SUPPORT_SCAN_TICKS, COMBAT_SUPPORT_SCAN_INTERVAL_TICKS);
    }

    private static boolean consumeSupportScanSlot(
            Villager villager,
            long gameTime,
            Map<UUID, Long> nextScanTicks,
            long intervalTicks
    ) {
        return TickThrottle.consume(villager.getUUID(), nextScanTicks, gameTime, intervalTicks);
    }

    static void setPostDrinkMainHand(Villager villager, ItemStack stack) {
        PotionUseState state = DRINKING_POTIONS.get(villager.getUUID());
        if (state != null) {
            DRINKING_POTIONS.put(villager.getUUID(), state.withResumeMainHand(stack));
        }
    }

    static void restoreHeldItemAndClearState(Villager villager) {
        PotionUseState state = DRINKING_POTIONS.remove(villager.getUUID());
        ATTACK_DELAY.remove(villager.getUUID());
        NEXT_COMBAT_SUPPORT_SCAN_TICKS.remove(villager.getUUID());
        NEXT_PASSIVE_SUPPORT_SCAN_TICKS.remove(villager.getUUID());
        HEALING_REDRINK_COOLDOWN_UNTIL.remove(villager.getUUID());
        SUPPORT_TARGET_CACHE.remove(villager.getUUID());
        if (state != null && villager.isAlive()) {
            villager.stopUsingItem();
            VillagerRetaliationVillagerEquipment.restoreVisualMainHand(villager, state.resumeMainHand());
        }
    }

    private static boolean tickPotionDrinking(Villager villager) {
        PotionUseState state = DRINKING_POTIONS.get(villager.getUUID());
        if (state == null || !villager.isAlive()) {
            clearAllState(villager);
            return false;
        }

        ensureDrinkVisualState(villager, state.potion());
        int ticksLeft = state.ticksLeft() - 1;
        if (ticksLeft <= 0) {
            finishPotionDrinking(villager, state.potion());
            return true;
        }

        VillagerRetaliationVillagerBrainUtil.stopNavigationAndClearPathing(villager);
        DRINKING_POTIONS.put(villager.getUUID(), state.withTicksLeft(ticksLeft));
        return true;
    }

    private static void finishPotionDrinking(Villager villager, ClericSelfPotion potion) {
        PotionUseState state = DRINKING_POTIONS.remove(villager.getUUID());
        villager.stopUsingItem();
        applySelfPotion(villager, potion);
        if (potion == ClericSelfPotion.HEALING && villager.level() instanceof ServerLevel serverLevel) {
            HEALING_REDRINK_COOLDOWN_UNTIL.put(
                    villager.getUUID(), serverLevel.getGameTime() + healingRedrinkCooldown(villager));
        }
        if (state != null) {
            VillagerRetaliationVillagerEquipment.restoreVisualMainHand(villager, state.resumeMainHand());
        }
    }

    private static boolean startPotionDrinking(Villager villager, ClericSelfPotion potion) {
        ItemStack drinkStack = createDrinkStack(potion);
        if (drinkStack.isEmpty()) {
            return false;
        }
        drinkStack = carriedOrGenerated(villager, drinkStack);
        if (drinkStack.isEmpty()) return false;

        ItemStack resumeMainHand = villager.getMainHandItem().copy();
        VillagerRetaliationVillagerBrainUtil.stopNavigationAndClearPathing(villager);
        VillagerRetaliationVillagerEquipment.setVisualMainHand(villager, drinkStack);
        villager.startUsingItem(InteractionHand.MAIN_HAND);
        int useDuration = Math.max(2, drinkStack.getUseDuration(villager));
        int manualDrinkDuration = useDuration - 1;
        DRINKING_POTIONS.put(villager.getUUID(), new PotionUseState(potion, manualDrinkDuration, resumeMainHand));
        villager.playSound(SoundEvents.WITCH_DRINK, 1.0F, 0.8F + villager.getRandom().nextFloat() * 0.4F);
        return true;
    }

    private static ClericSelfPotion chooseSelfPotion(Villager villager, double distanceSqr) {
        if (villager.getRandom().nextFloat() < WATER_BREATHING_TRIGGER_CHANCE
                && villager.isEyeInFluid(FluidTags.WATER)
                && !villager.hasEffect(MobEffects.WATER_BREATHING)) {
            return ClericSelfPotion.WATER_BREATHING;
        }
        if (villager.getRandom().nextFloat() < FIRE_RESISTANCE_TRIGGER_CHANCE
                && (villager.isOnFire()
                || villager.getLastDamageSource() != null && villager.getLastDamageSource().is(DamageTypeTags.IS_FIRE))
                && !villager.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            return ClericSelfPotion.FIRE_RESISTANCE;
        }
        if (villager.getRandom().nextFloat() < HEALING_TRIGGER_CHANCE
                && villager.level() instanceof ServerLevel serverLevel
                && serverLevel.getGameTime() >= HEALING_REDRINK_COOLDOWN_UNTIL.getOrDefault(villager.getUUID(), 0L)
                && villager.getHealth() < villager.getMaxHealth()) {
            return ClericSelfPotion.HEALING;
        }
        if (villager.getRandom().nextFloat() < SWIFTNESS_TRIGGER_CHANCE
                && villager.getTarget() != null
                && distanceSqr > 121.0D
                && !villager.hasEffect(MobEffects.MOVEMENT_SPEED)
        ) {
            return ClericSelfPotion.SWIFTNESS;
        }
        return ClericSelfPotion.NONE;
    }

    private static void applySelfPotion(Villager villager, ClericSelfPotion potion) {
        switch (potion) {
            case MILK_BUCKET -> villager.removeAllEffects();
            case FIRE_RESISTANCE -> villager.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, FIRE_RESISTANCE_TICKS, 0));
            case WATER_BREATHING -> villager.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, WATER_BREATHING_TICKS, 0));
            case SWIFTNESS -> villager.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, SWIFTNESS_TICKS, 0));
            case HEALING -> villager.heal(8.0F);
            case NONE -> {
            }
        }
    }

    private static ItemStack createDrinkStack(ClericSelfPotion potion) {
        return switch (potion) {
            case MILK_BUCKET -> new ItemStack(Items.MILK_BUCKET);
            case FIRE_RESISTANCE -> PotionContents.createItemStack(Items.POTION, Potions.FIRE_RESISTANCE);
            case WATER_BREATHING -> PotionContents.createItemStack(Items.POTION, Potions.WATER_BREATHING);
            case SWIFTNESS -> PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS);
            case HEALING -> PotionContents.createItemStack(Items.POTION, Potions.HEALING);
            case NONE -> ItemStack.EMPTY;
        };
    }

    private static void ensureDrinkVisualState(Villager villager, ClericSelfPotion potion) {
        ItemStack mainHand = villager.getMainHandItem();
        if (!VillagerRetaliationPotionUtil.isDrinkableCombatConsumable(mainHand)) {
            VillagerRetaliationVillagerEquipment.setVisualMainHand(villager, createDrinkStack(potion));
        }

        if (!villager.isUsingItem() || !VillagerRetaliationPotionUtil.isDrinkableCombatConsumable(villager.getUseItem())) {
            villager.startUsingItem(InteractionHand.MAIN_HAND);
        }
    }

    private static boolean hasMilkCurableHarmfulEffect(Villager villager) {
        for (MobEffectInstance effectInstance : villager.getActiveEffects()) {
            if (effectInstance.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static boolean canUseClericPotions(Villager villager) {
        return VillagerCombatRoles.isCleric(villager) && VillagerRetaliationConfig.CLERICS_USE_POTIONS.get();
    }

    private static boolean canUseMilkBucket(Villager villager) {
        return canUseClericPotions(villager);
    }

    private static boolean canUseOutOfCombatMilk(Villager villager) {
        return canUseMilkBucket(villager);
    }

    private static PotionThrowPlan chooseThrowPlan(Villager villager, LivingEntity target, ServerLevel level, double distanceSqr) {
        LivingEntity supportTarget = findCachedCombatSupportTarget(
                villager,
                level,
                MAX_THROW_DISTANCE_SQR,
                SUPPORT_HEAL_HEALTH_RATIO,
                false
        );
        if (supportTarget != null && isSafeSupportThrow(villager, supportTarget, target)) {
            return new PotionThrowPlan(
                    supportTarget,
                    PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING)
            );
        }

        ItemStack splashPotion = selectSplashPotion(villager, target, distanceSqr);
        if (splashPotion.isEmpty() || !isSafeOffensiveThrow(villager, target, splashPotion)) {
            return null;
        }

        return new PotionThrowPlan(target, splashPotion);
    }

    private static LivingEntity findCachedCombatSupportTarget(
            Villager villager,
            ServerLevel level,
            double maxDistanceSqr,
            float healthThreshold,
            boolean requireLineOfSight
    ) {
        UUID villagerId = villager.getUUID();
        SupportTargetState cachedTarget = SUPPORT_TARGET_CACHE.get(villagerId);
        if (cachedTarget != null && level.getGameTime() <= cachedTarget.expiresGameTime()) {
            Entity entity = level.getEntity(cachedTarget.targetId());
            if (entity instanceof LivingEntity livingEntity
                    && villager.distanceToSqr(livingEntity) <= maxDistanceSqr
                    && isSupportTarget(villager, livingEntity, healthThreshold, requireLineOfSight)) {
                return livingEntity;
            }
            SUPPORT_TARGET_CACHE.remove(villagerId);
        }

        if (!consumeCombatSupportScanSlot(villager, level.getGameTime())) {
            return null;
        }

        LivingEntity supportTarget = findSupportTarget(villager, level, maxDistanceSqr, healthThreshold, requireLineOfSight);
        if (supportTarget != null) {
            SUPPORT_TARGET_CACHE.put(
                    villagerId,
                    new SupportTargetState(supportTarget.getUUID(), level.getGameTime() + SUPPORT_TARGET_CACHE_TICKS)
            );
        }
        return supportTarget;
    }

    private static LivingEntity findSupportTarget(
            Villager villager,
            ServerLevel level,
            double maxDistanceSqr,
            float healthThreshold,
            boolean requireLineOfSight
    ) {
        AABB searchArea = villager.getBoundingBox().inflate(Math.sqrt(maxDistanceSqr));
        SupportCandidate bestTarget = null;
        bestTarget = bestSupportCandidate(
                villager,
                level.getEntitiesOfClass(Villager.class, searchArea),
                maxDistanceSqr,
                healthThreshold,
                requireLineOfSight,
                bestTarget
        );
        bestTarget = bestSupportCandidate(
                villager,
                level.getEntitiesOfClass(IronGolem.class, searchArea),
                maxDistanceSqr,
                healthThreshold,
                requireLineOfSight,
                bestTarget
        );
        bestTarget = bestSupportCandidate(
                villager,
                level.getEntitiesOfClass(Player.class, searchArea),
                maxDistanceSqr,
                healthThreshold,
                requireLineOfSight,
                bestTarget
        );
        return bestTarget == null ? null : bestTarget.target();
    }

    private static SupportCandidate bestSupportCandidate(
            Villager villager,
            Iterable<? extends LivingEntity> candidates,
            double maxDistanceSqr,
            float healthThreshold,
            boolean requireLineOfSight,
            SupportCandidate bestTarget
    ) {
        for (LivingEntity candidate : candidates) {
            if (!isSupportTarget(villager, candidate, healthThreshold, requireLineOfSight)) {
                continue;
            }

            double distanceSqr = villager.distanceToSqr(candidate);
            if (distanceSqr > maxDistanceSqr) {
                continue;
            }

            float missingHealth = candidate.getMaxHealth() - candidate.getHealth();
            float healthRatio = candidate.getHealth() / candidate.getMaxHealth();
            float score = missingHealth + (1.0F - healthRatio) * 8.0F;
            if (bestTarget == null
                    || score > bestTarget.score()
                    || score == bestTarget.score() && distanceSqr < bestTarget.distanceSqr()) {
                bestTarget = new SupportCandidate(candidate, score, distanceSqr);
            }
        }
        return bestTarget;
    }

    private static boolean isSupportTarget(Villager villager, LivingEntity entity, float healthThreshold, boolean requireLineOfSight) {
        if (entity == villager || !entity.isAlive() || entity.isInvertedHealAndHarm()) {
            return false;
        }
        if (!(entity instanceof Villager)
                && !(entity instanceof IronGolem)
                && !(entity instanceof Player)) {
            return false;
        }
        if ((entity instanceof Villager || entity instanceof IronGolem)
                && villager.level() instanceof ServerLevel level
                && !VillageAllegianceRelations.sameCanonical(level, villager, entity)) {
            return false;
        }
        if (entity instanceof Player player && !canSupportPlayer(villager, player)) {
            return false;
        }
        if (requireLineOfSight && !villager.hasLineOfSight(entity)) {
            return false;
        }

        float missingHealth = entity.getMaxHealth() - entity.getHealth();
        return entity.getHealth() <= entity.getMaxHealth() * healthThreshold
                || missingHealth >= SUPPORT_HEAL_MIN_MISSING_HEALTH;
    }

    private static boolean canSupportPlayer(Villager villager, Player player) {
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }
        if (!(villager.level() instanceof ServerLevel level) || !VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
            return false;
        }

        VillagerReputationLevel reputationLevel = VillagerReputationManager.getReputationLevel(level, villager, player.getUUID());
        return reputationLevel == VillagerReputationLevel.TRUSTED
                || reputationLevel == VillagerReputationLevel.RESPECTED
                || reputationLevel == VillagerReputationLevel.REVERED
                || reputationLevel == VillagerReputationLevel.ROYALTY;
    }

    private static ItemStack selectSplashPotion(Villager villager, LivingEntity target, double distanceSqr) {
        if (target.isInvertedHealAndHarm()) {
            if (distanceSqr >= 64.0D && !target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                return PotionContents.createItemStack(Items.SPLASH_POTION, Potions.SLOWNESS);
            }
            if (distanceSqr <= 9.0D && !target.hasEffect(MobEffects.WEAKNESS) && villager.getRandom().nextFloat() < 0.25F) {
                return PotionContents.createItemStack(Items.SPLASH_POTION, Potions.WEAKNESS);
            }
            return PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING);
        }
        if (distanceSqr >= 64.0D && !target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
            return PotionContents.createItemStack(Items.SPLASH_POTION, Potions.SLOWNESS);
        }
        if (target.getHealth() >= 8.0F && !target.hasEffect(MobEffects.POISON)) {
            return PotionContents.createItemStack(Items.SPLASH_POTION, Potions.POISON);
        }
        if (distanceSqr <= 9.0D && !target.hasEffect(MobEffects.WEAKNESS) && villager.getRandom().nextFloat() < 0.25F) {
            return PotionContents.createItemStack(Items.SPLASH_POTION, Potions.WEAKNESS);
        }
        return PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HARMING);
    }

    private static boolean isSafeOffensiveThrow(Villager villager, LivingEntity target, ItemStack potionStack) {
        if (isFriendlySafePotion(potionStack)) {
            return true;
        }

        AABB splashArea = target.getBoundingBox().inflate(SPLASH_RADIUS);
        for (LivingEntity nearby : villager.level().getEntitiesOfClass(LivingEntity.class, splashArea, entity -> isFriendlyCivilian(villager, entity))) {
            return false;
        }
        return true;
    }

    private static boolean isSafeSupportThrow(Villager villager, LivingEntity supportTarget, LivingEntity hostileTarget) {
        if (!hostileTarget.isAlive() || hostileTarget.isInvertedHealAndHarm()) {
            return true;
        }

        double maxDistance = SPLASH_RADIUS + hostileTarget.getBbWidth();
        return supportTarget.distanceToSqr(hostileTarget) > maxDistance * maxDistance;
    }

    private static boolean isFriendlyCivilian(Villager villager, LivingEntity entity) {
        return entity != villager
                && entity.isAlive()
                && (entity instanceof Villager || entity instanceof IronGolem);
    }

    private static boolean isFriendlySafePotion(ItemStack potionStack) {
        return VillagerRetaliationPotionUtil.isHealingPotion(potionStack);
    }

    private static boolean throwSplashPotionLikeWitch(Villager villager, LivingEntity target, ServerLevel level, ItemStack potionStack) {
        potionStack = carriedOrGenerated(villager, potionStack);
        if (potionStack.isEmpty()) return false;
        ThrownPotion thrownPotion = new ThrownPotion(level, villager);
        thrownPotion.setItem(potionStack);
        double dx = target.getX() + target.getDeltaMovement().x - villager.getX();
        double dy = target.getY(0.3333333333333333D) - thrownPotion.getY();
        double dz = target.getZ() + target.getDeltaMovement().z - villager.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        thrownPotion.shoot(dx, dy + horizontal * 0.2D, dz, 0.75F, 8.0F);
        level.addFreshEntity(thrownPotion);
        villager.swing(InteractionHand.MAIN_HAND, true);
        villager.playSound(SoundEvents.WITCH_THROW, 1.0F, 0.8F + villager.getRandom().nextFloat() * 0.4F);
        VillagerRetaliationVillagerEquipment.setVisualMainHand(
                villager,
                potionStack
        );
        return true;
    }

    private static ItemStack carriedOrGenerated(Villager villager, ItemStack planned) {
        if (!(villager.level() instanceof ServerLevel level)
                || !PartyService.isRecruitedPartyVillager(level, villager.getUUID())) {
            return planned;
        }
        return VillagerInventoryAccess.takeCarriedItem(
                villager,
                stack -> ItemStack.isSameItemSameComponents(stack, planned));
    }

    private static int throwCooldown(Villager villager) {
        return villager.level() instanceof ServerLevel level
                && PartyService.isRecruitedPartyVillager(level, villager.getUUID())
                ? CARRIED_THROW_INTERVAL_TICKS
                : GENERATED_THROW_INTERVAL_TICKS;
    }

    private static int healingRedrinkCooldown(Villager villager) {
        return villager.level() instanceof ServerLevel level
                && PartyService.isRecruitedPartyVillager(level, villager.getUUID())
                ? CARRIED_HEALING_REDRINK_COOLDOWN_TICKS
                : GENERATED_HEALING_REDRINK_COOLDOWN_TICKS;
    }

    private enum ClericSelfPotion {
        NONE,
        MILK_BUCKET,
        FIRE_RESISTANCE,
        WATER_BREATHING,
        SWIFTNESS,
        HEALING
    }

    private record PotionUseState(ClericSelfPotion potion, int ticksLeft, ItemStack resumeMainHand) {
        private PotionUseState withTicksLeft(int ticksLeft) {
            return new PotionUseState(this.potion, ticksLeft, this.resumeMainHand.copy());
        }

        private PotionUseState withResumeMainHand(ItemStack resumeMainHand) {
            return new PotionUseState(this.potion, this.ticksLeft, resumeMainHand.copy());
        }
    }

    private record PotionThrowPlan(LivingEntity aimTarget, ItemStack potionStack) {
    }

    private record SupportTargetState(UUID targetId, long expiresGameTime) {
    }

    private record SupportCandidate(LivingEntity target, float score, double distanceSqr) {
    }
}
