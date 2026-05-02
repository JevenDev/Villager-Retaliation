package com.jvn.villagerretaliation.villager;

import com.jvn.villagerretaliation.util.VillagerRetaliationLootUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

public final class VillagerRetaliationVillagerWeapons {
    public static final double WEAPON_SEARCH_RADIUS = 12.0D;
    public static final double WEAPON_PICKUP_REACH_SQR = 2.25D;
    private static final String PERSISTENT_PICKED_UP_MAINHAND_TAG = "VillagerRetaliationPickedUpMainhand";
    private static final Map<UUID, ItemStack> PICKED_UP_MAINHAND_ITEMS = new HashMap<>();

    private VillagerRetaliationVillagerWeapons() {
    }

    public static boolean hasUsableWeapon(AbstractVillager villager) {
        return isUsableWeapon(villager.getMainHandItem()) || isUsableWeapon(villager.getOffhandItem());
    }

    public static boolean hasTrackedPickup(AbstractVillager villager) {
        return !getTrackedPickup(villager).isEmpty();
    }

    public static ItemStack getPrimaryWeapon(AbstractVillager villager) {
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

    public static InteractionHand getHoldingHand(LivingEntity entity, Predicate<ItemStack> predicate) {
        return predicate.test(entity.getMainHandItem()) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    public static boolean isUsableWeapon(ItemStack stack) {
        return isMeleeWeapon(stack) || isRangedWeapon(stack);
    }

    public static Optional<ItemEntity> findNearestWeapon(AbstractVillager villager) {
        AABB searchBox = villager.getBoundingBox().inflate(WEAPON_SEARCH_RADIUS);
        ItemEntity bestWeapon = null;
        int bestPriority = Integer.MAX_VALUE;
        double bestDistanceSqr = Double.MAX_VALUE;
        for (ItemEntity itemEntity : villager.level().getEntitiesOfClass(ItemEntity.class, searchBox, VillagerRetaliationVillagerWeapons::canBePickedUp)) {
            ItemStack itemStack = itemEntity.getItem();
            if (!isUsableWeapon(itemStack)) {
                continue;
            }

            int priority = pickupPriority(itemStack);
            double distanceSqr = villager.distanceToSqr(itemEntity);
            if (bestWeapon != null && (priority > bestPriority || priority == bestPriority && distanceSqr >= bestDistanceSqr)) {
                continue;
            }

            bestWeapon = itemEntity;
            bestPriority = priority;
            bestDistanceSqr = distanceSqr;
        }

        return Optional.ofNullable(bestWeapon);
    }

    public static void equipGroundWeapon(AbstractVillager villager, ItemEntity itemEntity) {
        if (hasTrackedPickup(villager)) {
            return;
        }

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
        setTrackedPickup(villager, equippedStack.copy());

        groundStack.shrink(1);
        if (groundStack.isEmpty()) {
            itemEntity.discard();
        }
    }

    public static void ensurePickedMainHandDrop(AbstractVillager villager, LivingDropsEvent event) {
        ItemStack trackedPickup = getTrackedPickup(villager);
        if (trackedPickup.isEmpty()) {
            return;
        }
        clearTrackedPickup(villager);

        ItemStack mainHand = villager.getMainHandItem();
        if (!mainHand.isEmpty() && ItemStack.isSameItem(mainHand, trackedPickup)) {
            VillagerRetaliationLootUtil.addDropIfNoMatchingItem(event, mainHand.copy());
            return;
        }

        VillagerRetaliationLootUtil.addDropIfNoMatchingItem(event, trackedPickup.copy());
    }

    public static boolean maintainAcquiredWeaponAuthority(AbstractVillager villager) {
        ItemStack trackedPickup = getTrackedPickup(villager);
        if (trackedPickup.isEmpty()) {
            return false;
        }

        ItemStack mainHand = villager.getMainHandItem();
        if (ItemStack.isSameItem(mainHand, trackedPickup)) {
            setTrackedPickup(villager, mainHand.copy());
            villager.setGuaranteedDrop(EquipmentSlot.MAINHAND);
            return true;
        }

        if (!mainHand.isEmpty()) {
            ItemStack displacedMainHand = mainHand.copy();
            ItemStack remainder = villager.getInventory().addItem(displacedMainHand);
            if (!remainder.isEmpty()) {
                villager.spawnAtLocation(remainder);
            }
        }

        villager.setItemSlot(EquipmentSlot.MAINHAND, trackedPickup.copy());
        villager.setGuaranteedDrop(EquipmentSlot.MAINHAND);
        return true;
    }

    public static void clearTrackedPickup(AbstractVillager villager) {
        PICKED_UP_MAINHAND_ITEMS.remove(villager.getUUID());
        villager.getPersistentData().remove(PERSISTENT_PICKED_UP_MAINHAND_TAG);
    }

    public static void clearTrackedPickupCache(AbstractVillager villager) {
        PICKED_UP_MAINHAND_ITEMS.remove(villager.getUUID());
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

    private static ItemStack getTrackedPickup(AbstractVillager villager) {
        ItemStack trackedPickup = PICKED_UP_MAINHAND_ITEMS.get(villager.getUUID());
        if (trackedPickup != null && !trackedPickup.isEmpty()) {
            return trackedPickup;
        }

        CompoundTag trackedTag = villager.getPersistentData().getCompound(PERSISTENT_PICKED_UP_MAINHAND_TAG);
        if (trackedTag.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack restored = ItemStack.parseOptional(villager.level().registryAccess(), trackedTag);
        if (!restored.isEmpty()) {
            PICKED_UP_MAINHAND_ITEMS.put(villager.getUUID(), restored.copy());
        }
        return restored;
    }

    private static void setTrackedPickup(AbstractVillager villager, ItemStack trackedPickup) {
        PICKED_UP_MAINHAND_ITEMS.put(villager.getUUID(), trackedPickup.copy());
        villager.getPersistentData().put(PERSISTENT_PICKED_UP_MAINHAND_TAG, (CompoundTag) trackedPickup.saveOptional(villager.level().registryAccess()));
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
