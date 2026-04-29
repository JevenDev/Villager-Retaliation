package com.jvn.commonfolk.villager;

import com.jvn.commonfolk.combat.VillagerCombatRoles;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.Tags;

public final class CommonfolkVillagerWeapons {
    public static final double WEAPON_SEARCH_RADIUS = 12.0D;
    public static final double WEAPON_PICKUP_REACH_SQR = 2.25D;

    private CommonfolkVillagerWeapons() {
    }

    public static boolean hasUsableWeapon(Villager villager) {
        return isUsableWeapon(villager.getMainHandItem()) || isUsableWeapon(villager.getOffhandItem());
    }

    public static ItemStack getPrimaryWeapon(Villager villager) {
        ItemStack mainHand = villager.getMainHandItem();
        if (isUsableWeapon(mainHand)) {
            return mainHand;
        }

        ItemStack offHand = villager.getOffhandItem();
        if (isUsableWeapon(offHand)) {
            return offHand;
        }

        return !mainHand.isEmpty() ? mainHand : offHand;
    }

    public static InteractionHand getHoldingHand(Villager villager, Predicate<ItemStack> predicate) {
        return predicate.test(villager.getMainHandItem()) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    public static boolean isUsableWeapon(ItemStack stack) {
        return isMeleeWeapon(stack) || isRangedWeapon(stack);
    }

    public static boolean isUsableWeaponInMainHand(Villager villager) {
        return isUsableWeapon(villager.getMainHandItem());
    }

    public static Optional<ItemEntity> findNearestWeapon(Villager villager) {
        AABB searchBox = villager.getBoundingBox().inflate(WEAPON_SEARCH_RADIUS);
        return villager.level().getEntitiesOfClass(ItemEntity.class, searchBox, CommonfolkVillagerWeapons::canBePickedUp).stream()
                .filter(itemEntity -> isUsableWeapon(itemEntity.getItem()))
                .min(Comparator
                        .comparingInt((ItemEntity itemEntity) -> pickupPriority(itemEntity.getItem()))
                        .thenComparingDouble(villager::distanceToSqr));
    }

    public static void equipGroundWeapon(Villager villager, ItemEntity itemEntity) {
        ItemStack groundStack = itemEntity.getItem();
        if (groundStack.isEmpty()) {
            return;
        }

        ItemStack equippedStack = groundStack.copyWithCount(1);
        ItemStack previousMainHand = villager.getMainHandItem().copy();
        if (!previousMainHand.isEmpty()) {
            ItemStack remainder = villager.getInventory().addItem(previousMainHand);
            if (!remainder.isEmpty()) {
                villager.spawnAtLocation(remainder);
            }
        }

        villager.onItemPickup(itemEntity);
        villager.take(itemEntity, 1);
        villager.setItemSlot(EquipmentSlot.MAINHAND, equippedStack);
        villager.setGuaranteedDrop(EquipmentSlot.MAINHAND);

        groundStack.shrink(1);
        if (groundStack.isEmpty()) {
            itemEntity.discard();
        }
    }

    public static boolean isRangedWeapon(ItemStack stack) {
        return isBowWeapon(stack)
                || isCrossbowWeapon(stack)
                || isTridentWeapon(stack);
    }

    public static boolean isBowWeapon(ItemStack stack) {
        return stack.is(Tags.Items.TOOLS_BOW) || stack.getItem() instanceof BowItem;
    }

    public static boolean isCrossbowWeapon(ItemStack stack) {
        return stack.getItem() instanceof CrossbowItem;
    }

    public static boolean isTridentWeapon(ItemStack stack) {
        return stack.is(Tags.Items.TOOLS_SPEAR) || stack.is(Items.TRIDENT) || stack.getItem() instanceof TridentItem;
    }

    private static boolean isMeleeWeapon(ItemStack stack) {
        return stack.is(Tags.Items.MELEE_WEAPON_TOOLS)
                || stack.is(Tags.Items.MINING_TOOL_TOOLS)
                || stack.is(Tags.Items.TOOLS_MACE);
    }

    private static boolean canBePickedUp(ItemEntity itemEntity) {
        return itemEntity.isAlive() && !itemEntity.hasPickUpDelay() && !itemEntity.getItem().isEmpty();
    }

    private static int pickupPriority(ItemStack stack) {
        if (isRangedWeapon(stack)) {
            return 0;
        }
        if (isMeleeWeapon(stack)) {
            return 1;
        }
        return 2;
    }
}
