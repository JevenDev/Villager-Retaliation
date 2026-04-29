package com.jvn.commonfolk.combat;

import com.jvn.commonfolk.config.CommonfolkConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
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
    private static final Map<UUID, ClericSelfPotion> DRINKING_POTIONS = new HashMap<>();
    private static final double MAX_THROW_DISTANCE_SQR = 144.0D;
    private static final int THROW_INTERVAL_TICKS = 60;
    private static final int DRINK_COOLDOWN_TICKS = 60;
    private static final int FIRE_RESISTANCE_TICKS = 20 * 20;
    private static final int SWIFTNESS_TICKS = 20 * 15;

    private VillagerClericPotionHelper() {
    }

    static boolean tryCombat(Villager villager, LivingEntity target, ServerLevel level, double distanceSqr) {
        if (!VillagerCombatRoles.isCleric(villager) || !CommonfolkConfig.CLERICS_USE_POTIONS.get()) {
            return false;
        }

        if (villager.isUsingItem()) {
            return tickPotionDrinking(villager);
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

    static boolean isActivelyHandlingPotion(Villager villager) {
        if (!VillagerCombatRoles.isCleric(villager) || !CommonfolkConfig.CLERICS_USE_POTIONS.get()) {
            return false;
        }
        if (villager.isUsingItem() && villager.getUseItem().is(Items.POTION)) {
            return true;
        }

        ItemStack mainHand = villager.getMainHandItem();
        return mainHand.is(Items.POTION) || mainHand.is(Items.SPLASH_POTION) || mainHand.is(Items.LINGERING_POTION);
    }

    private static boolean tickPotionDrinking(Villager villager) {
        ClericSelfPotion potion = DRINKING_POTIONS.get(villager.getUUID());
        if (potion == null) {
            return true;
        }
        if (!villager.isUsingItem() || !villager.getUseItem().is(Items.POTION)) {
            DRINKING_POTIONS.remove(villager.getUUID());
            villager.setItemSlot(EquipmentSlot.MAINHAND, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HARMING));
            return true;
        }

        ItemStack useItem = villager.getUseItem();
        if (villager.getTicksUsingItem() < useItem.getUseDuration(villager)) {
            return true;
        }

        villager.stopUsingItem();
        DRINKING_POTIONS.remove(villager.getUUID());
        applySelfPotion(villager, potion);
        ATTACK_DELAY.put(villager.getUUID(), DRINK_COOLDOWN_TICKS);
        villager.setItemSlot(EquipmentSlot.MAINHAND, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HARMING));
        return true;
    }

    private static void startPotionDrinking(Villager villager, ClericSelfPotion potion) {
        ItemStack drinkStack = switch (potion) {
            case FIRE_RESISTANCE -> PotionContents.createItemStack(Items.POTION, Potions.FIRE_RESISTANCE);
            case SWIFTNESS -> PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS);
            case HEALING -> PotionContents.createItemStack(Items.POTION, Potions.HEALING);
            case NONE -> ItemStack.EMPTY;
        };
        if (drinkStack.isEmpty()) {
            return;
        }

        villager.getNavigation().stop();
        villager.setItemSlot(EquipmentSlot.MAINHAND, drinkStack);
        DRINKING_POTIONS.put(villager.getUUID(), potion);
        villager.startUsingItem(InteractionHand.MAIN_HAND);
        villager.playSound(SoundEvents.WITCH_DRINK, 1.0F, 0.8F + villager.getRandom().nextFloat() * 0.4F);
    }

    private static ClericSelfPotion chooseSelfPotion(Villager villager, double distanceSqr) {
        if (villager.isOnFire() && !villager.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            return ClericSelfPotion.FIRE_RESISTANCE;
        }
        if (villager.getHealth() < villager.getMaxHealth() * 0.55F) {
            return ClericSelfPotion.HEALING;
        }
        if (distanceSqr > 121.0D && !villager.hasEffect(MobEffects.MOVEMENT_SPEED) && villager.getRandom().nextFloat() < 0.5F) {
            return ClericSelfPotion.SWIFTNESS;
        }
        return ClericSelfPotion.NONE;
    }

    private static void applySelfPotion(Villager villager, ClericSelfPotion potion) {
        switch (potion) {
            case FIRE_RESISTANCE -> villager.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, FIRE_RESISTANCE_TICKS, 0));
            case SWIFTNESS -> villager.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, SWIFTNESS_TICKS, 0));
            case HEALING -> villager.heal(8.0F);
            case NONE -> {
            }
        }
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
        FIRE_RESISTANCE,
        SWIFTNESS,
        HEALING
    }
}
