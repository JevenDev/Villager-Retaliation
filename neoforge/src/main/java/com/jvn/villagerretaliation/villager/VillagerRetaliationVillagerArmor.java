package com.jvn.villagerretaliation.villager;

import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import java.util.Optional;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.phys.AABB;

public final class VillagerRetaliationVillagerArmor {
    private VillagerRetaliationVillagerArmor() {
    }

    public static Optional<ItemEntity> findNearestUpgrade(AbstractVillager villager) {
        if (!VillagerRetaliationVillagerWeapons.canSearchForGroundWeapon(villager)) {
            return Optional.empty();
        }

        AABB searchBox = villager.getBoundingBox().inflate(VillagerRetaliationVillagerWeapons.WEAPON_SEARCH_RADIUS);
        ItemEntity nearest = null;
        double nearestDistanceSqr = Double.MAX_VALUE;
        for (ItemEntity itemEntity : villager.level().getEntitiesOfClass(ItemEntity.class, searchBox)) {
            if (!isEligibleVisibleUpgrade(villager, itemEntity)) {
                continue;
            }
            double distanceSqr = villager.distanceToSqr(itemEntity);
            if (distanceSqr < nearestDistanceSqr) {
                nearest = itemEntity;
                nearestDistanceSqr = distanceSqr;
            }
        }
        return Optional.ofNullable(nearest);
    }

    public static boolean shouldPathfindForUpgrade(AbstractVillager villager, ItemStack candidate) {
        EquipmentSlot slot = armorSlot(candidate);
        return slot != null
                && VillagerRetaliationVillagerWeapons.canSearchForGroundWeapon(villager)
                && !hasAuthoritativeJobEquipment(villager, slot)
                && isBetterArmor(candidate, villager.getItemBySlot(slot));
    }

    public static boolean equipGroundUpgrade(AbstractVillager villager, ItemEntity itemEntity) {
        if (!itemEntity.isAlive() || itemEntity.hasPickUpDelay()) {
            return false;
        }

        ItemStack groundStack = itemEntity.getItem();
        EquipmentSlot slot = armorSlot(groundStack);
        if (slot == null
                || hasAuthoritativeJobEquipment(villager, slot)
                || !isBetterArmor(groundStack, villager.getItemBySlot(slot))) {
            return false;
        }

        VillagerRetaliationVillagerBrainUtil.stopNavigationAndClearPathing(villager);
        ItemStack previous = villager.getItemBySlot(slot).copy();
        if (!previous.isEmpty()) {
            storeOrDrop(villager, previous);
        }

        ItemStack equipped = groundStack.copyWithCount(1);
        villager.onItemPickup(itemEntity);
        villager.take(itemEntity, 1);
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(villager, slot, equipped);
        groundStack.shrink(1);
        if (groundStack.isEmpty()) {
            itemEntity.discard();
        }
        return true;
    }

    public static boolean isBetterArmor(ItemStack candidate, ItemStack current) {
        if (!(candidate.getItem() instanceof ArmorItem candidateArmor)) {
            return false;
        }
        if (current.isEmpty()) {
            return true;
        }
        if (EnchantmentHelper.has(current, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
            return false;
        }
        if (!(current.getItem() instanceof ArmorItem currentArmor)) {
            return true;
        }
        if (candidateArmor.getEquipmentSlot() != currentArmor.getEquipmentSlot()) {
            return false;
        }
        if (candidateArmor.getDefense() != currentArmor.getDefense()) {
            return candidateArmor.getDefense() > currentArmor.getDefense();
        }
        if (candidateArmor.getToughness() != currentArmor.getToughness()) {
            return candidateArmor.getToughness() > currentArmor.getToughness();
        }
        if (candidate.getDamageValue() != current.getDamageValue()) {
            return candidate.getDamageValue() < current.getDamageValue();
        }
        return candidate.isEnchanted() && !current.isEnchanted();
    }

    private static boolean isEligibleVisibleUpgrade(AbstractVillager villager, ItemEntity itemEntity) {
        return itemEntity.isAlive()
                && !itemEntity.getItem().isEmpty()
                && villager.distanceToSqr(itemEntity) <= VillagerRetaliationVillagerWeapons.WEAPON_SEARCH_RADIUS
                        * VillagerRetaliationVillagerWeapons.WEAPON_SEARCH_RADIUS
                && villager.hasLineOfSight(itemEntity)
                && villager.level().getWorldBorder().isWithinBounds(itemEntity.blockPosition())
                && shouldPathfindForUpgrade(villager, itemEntity.getItem());
    }

    private static EquipmentSlot armorSlot(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem armorItem ? armorItem.getEquipmentSlot() : null;
    }

    private static boolean hasAuthoritativeJobEquipment(AbstractVillager villager, EquipmentSlot slot) {
        return villager instanceof Villager regularVillager
                && HiredJobInventory.hasJobEquipmentForSlot(regularVillager, slot);
    }

    private static void storeOrDrop(AbstractVillager villager, ItemStack stack) {
        ItemStack remainder = villager instanceof Villager regularVillager
                ? VillagerInventoryAccess.addItem(regularVillager, stack)
                : villager.getInventory().addItem(stack);
        if (!remainder.isEmpty()) {
            villager.spawnAtLocation(remainder);
        }
    }
}
