package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.ItemAbilities;

public final class FarmerHoeRequirement {
    private FarmerHoeRequirement() {
    }

    public static boolean hasHoe(Villager villager) {
        if (villager == null) {
            return false;
        }
        if (isHoe(villager.getItemBySlot(EquipmentSlot.MAINHAND))
                || isHoe(villager.getItemBySlot(EquipmentSlot.OFFHAND))) {
            return true;
        }
        SimpleContainer inventory = villager.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (isHoe(inventory.getItem(slot))) {
                return true;
            }
        }
        HiredJobInventory jobInventory = HiredJobInventory.getJobInventory(villager);
        return !jobInventory.findTool(FarmerHoeRequirement::isHoe).isEmpty();
    }

    public static boolean isHoe(ItemStack stack) {
        return !stack.isEmpty() && stack.canPerformAction(ItemAbilities.HOE_TILL);
    }

    public static double hoeScore(ItemStack stack) {
        int tier = 0;
        if (stack.is(Items.WOODEN_HOE)) {
            tier = 1;
        } else if (stack.is(Items.STONE_HOE)) {
            tier = 2;
        } else if (stack.is(Items.IRON_HOE)) {
            tier = 3;
        } else if (stack.is(Items.GOLDEN_HOE)) {
            tier = 4;
        } else if (stack.is(Items.DIAMOND_HOE)) {
            tier = 5;
        } else if (stack.is(Items.NETHERITE_HOE)) {
            tier = 6;
        }
        int remainingDurability = stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : 0;
        return tier * 100000.0D + remainingDurability;
    }
}
