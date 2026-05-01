package com.jvn.commonfolk.combat;

import com.jvn.commonfolk.config.CommonfolkConfig;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

final class VillagerClericPotionHelper {
    private static final Map<UUID, Integer> ATTACK_DELAY = new HashMap<>();
    private static final Map<UUID, PotionUseState> DRINKING_POTIONS = new HashMap<>();
    private static final Map<UUID, Long> HEALING_REDRINK_COOLDOWN_UNTIL = new HashMap<>();
    private static final double MAX_THROW_DISTANCE_SQR = 144.0D;
    private static final int THROW_INTERVAL_TICKS = 60;
    private static final int HEALING_REDRINK_COOLDOWN_TICKS = 40;
    private static final int FIRE_RESISTANCE_TICKS = 20 * 180;
    private static final int WATER_BREATHING_TICKS = 20 * 180;
    private static final int SWIFTNESS_TICKS = 20 * 180;
    private static final float FIRE_RESISTANCE_TRIGGER_CHANCE = 0.15F;
    private static final float WATER_BREATHING_TRIGGER_CHANCE = 0.15F;
    private static final float HEALING_TRIGGER_CHANCE = 0.05F;
    private static final float SWIFTNESS_TRIGGER_CHANCE = 0.5F;

    private VillagerClericPotionHelper() {
    }

    static boolean tryCombat(Villager villager, LivingEntity target, ServerLevel level, double distanceSqr) {
        if (isDrinkingPotion(villager)) {
            return tickPotionDrinking(villager);
        }

        if (canUseMilkBucket(villager) && hasMilkCurableHarmfulEffect(villager)) {
            startPotionDrinking(villager, ClericSelfPotion.MILK_BUCKET);
            return true;
        }

        if (!canUseClericPotions(villager)) {
            return false;
        }

        ClericSelfPotion selfPotion = chooseSelfPotion(villager, distanceSqr);
        if (selfPotion != ClericSelfPotion.NONE) {
            startPotionDrinking(villager, selfPotion);
            return true;
        }

        int attackDelay = ATTACK_DELAY.getOrDefault(villager.getUUID(), 0);
        if (attackDelay > 0) {
            ATTACK_DELAY.put(villager.getUUID(), attackDelay - 1);
            villager.getNavigation().moveTo(target, VillagerCombatRoles.movementSpeed(villager) * 0.8D);
            return true;
        }

        if (!villager.hasLineOfSight(target) || distanceSqr > MAX_THROW_DISTANCE_SQR) {
            villager.getNavigation().moveTo(target, VillagerCombatRoles.movementSpeed(villager));
            return true;
        }

        ItemStack splashPotion = selectSplashPotion(villager, target, distanceSqr);
        throwSplashPotionLikeWitch(villager, target, level, splashPotion);
        ATTACK_DELAY.put(villager.getUUID(), THROW_INTERVAL_TICKS);
        return true;
    }

    static void clearState(Villager villager) {
        ATTACK_DELAY.remove(villager.getUUID());
        DRINKING_POTIONS.remove(villager.getUUID());
    }

    static void clearAllState(Villager villager) {
        clearState(villager);
        HEALING_REDRINK_COOLDOWN_UNTIL.remove(villager.getUUID());
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

        startPotionDrinking(villager, ClericSelfPotion.MILK_BUCKET);
        return true;
    }

    static boolean isActivelyHandlingPotion(Villager villager) {
        if (!canUseMilkBucket(villager) && !canUseClericPotions(villager)) {
            return false;
        }
        if (isDrinkingPotion(villager)) {
            return true;
        }
        if (villager.isUsingItem() && CommonfolkPotionUtil.isDrinkableCombatConsumable(villager.getUseItem())) {
            return true;
        }

        ItemStack mainHand = villager.getMainHandItem();
        return CommonfolkPotionUtil.isPotion(mainHand) || mainHand.is(Items.MILK_BUCKET);
    }

    static boolean isDrinkingPotion(Villager villager) {
        return DRINKING_POTIONS.containsKey(villager.getUUID());
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
        HEALING_REDRINK_COOLDOWN_UNTIL.remove(villager.getUUID());
        if (state != null && villager.isAlive()) {
            villager.stopUsingItem();
            villager.setItemSlot(EquipmentSlot.MAINHAND, state.resumeMainHand().copy());
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

        villager.getNavigation().stop();
        DRINKING_POTIONS.put(villager.getUUID(), state.withTicksLeft(ticksLeft));
        return true;
    }

    private static void finishPotionDrinking(Villager villager, ClericSelfPotion potion) {
        PotionUseState state = DRINKING_POTIONS.remove(villager.getUUID());
        villager.stopUsingItem();
        applySelfPotion(villager, potion);
        if (potion == ClericSelfPotion.HEALING && villager.level() instanceof ServerLevel serverLevel) {
            HEALING_REDRINK_COOLDOWN_UNTIL.put(villager.getUUID(), serverLevel.getGameTime() + HEALING_REDRINK_COOLDOWN_TICKS);
        }
        if (state != null) {
            villager.setItemSlot(EquipmentSlot.MAINHAND, state.resumeMainHand().copy());
        }
    }

    private static void startPotionDrinking(Villager villager, ClericSelfPotion potion) {
        ItemStack drinkStack = createDrinkStack(potion);
        if (drinkStack.isEmpty()) {
            return;
        }

        ItemStack resumeMainHand = villager.getMainHandItem().copy();
        villager.getNavigation().stop();
        villager.setItemSlot(EquipmentSlot.MAINHAND, drinkStack);
        villager.startUsingItem(InteractionHand.MAIN_HAND);
        int useDuration = Math.max(2, drinkStack.getUseDuration(villager));
        int manualDrinkDuration = useDuration - 1;
        DRINKING_POTIONS.put(villager.getUUID(), new PotionUseState(potion, manualDrinkDuration, resumeMainHand));
        villager.playSound(SoundEvents.WITCH_DRINK, 1.0F, 0.8F + villager.getRandom().nextFloat() * 0.4F);
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
        if (!CommonfolkPotionUtil.isDrinkableCombatConsumable(mainHand)) {
            villager.setItemSlot(EquipmentSlot.MAINHAND, createDrinkStack(potion));
        }

        if (!villager.isUsingItem() || !CommonfolkPotionUtil.isDrinkableCombatConsumable(villager.getUseItem())) {
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
        return VillagerCombatRoles.isCleric(villager) && CommonfolkConfig.CLERICS_USE_POTIONS.get();
    }

    private static boolean canUseMilkBucket(Villager villager) {
        return canUseClericPotions(villager)
                || VillagerCombatRoles.isFarmer(villager) && CommonfolkConfig.FARMERS_USE_BREAD.get();
    }

    private static boolean canUseOutOfCombatMilk(Villager villager) {
        return canUseMilkBucket(villager);
    }

    private static ItemStack selectSplashPotion(Villager villager, LivingEntity target, double distanceSqr) {
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

    private static void throwSplashPotionLikeWitch(Villager villager, LivingEntity target, ServerLevel level, ItemStack potionStack) {
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
        villager.setItemSlot(EquipmentSlot.MAINHAND, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HARMING));
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
}
