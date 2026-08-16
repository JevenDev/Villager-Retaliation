package com.jvn.villagerretaliation.util;

import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;

public final class VillagerEquipmentDurability {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.FEET,
            EquipmentSlot.LEGS,
            EquipmentSlot.CHEST,
            EquipmentSlot.HEAD
    };

    private VillagerEquipmentDurability() {
    }

    public static void hurtArmor(AbstractVillager villager, DamageSource source, float damageAmount) {
        if (villager.level().isClientSide || damageAmount <= 0.0F || source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return;
        }

        boolean[] jobControlled = null;
        if (villager instanceof Villager regularVillager) {
            jobControlled = new boolean[ARMOR_SLOTS.length];
            for (int index = 0; index < ARMOR_SLOTS.length; index++) {
                jobControlled[index] = HiredJobInventory.hasJobEquipmentForSlot(
                        regularVillager,
                        ARMOR_SLOTS[index])
                        && !regularVillager.getItemBySlot(ARMOR_SLOTS[index]).isEmpty();
            }
        }
        int armorDamage = Mth.floor(Math.max(1.0F, damageAmount / 4.0F));
        CommonHooks.onArmorHurt(source, ARMOR_SLOTS, armorDamage, villager);
        if (jobControlled != null) {
            Villager regularVillager = (Villager) villager;
            for (int index = 0; index < ARMOR_SLOTS.length; index++) {
                if (jobControlled[index]) {
                    HiredJobInventory.synchronizeEquipmentDurability(regularVillager, ARMOR_SLOTS[index]);
                }
            }
        }
    }

    public static void postMeleeHit(AbstractVillager villager, LivingEntity target, InteractionHand hand) {
        if (villager.level().isClientSide) {
            return;
        }

        ItemStack weapon = villager.getItemInHand(hand);
        if (weapon.isEmpty()) {
            return;
        }

        if (weapon.getItem().hurtEnemy(weapon, target, villager)) {
            weapon.getItem().postHurtEnemy(weapon, target, villager);
        }
        if (weapon.isEmpty()) {
            villager.setItemInHand(hand, ItemStack.EMPTY);
        }
    }

    public static boolean mineBlock(ItemStack tool, Level level, BlockState state, BlockPos pos, LivingEntity villager) {
        if (tool.isEmpty() || level.isClientSide || state.getDestroySpeed(level, pos) == 0.0F) {
            return false;
        }

        ItemStack before = tool.copy();
        return tool.getItem().mineBlock(tool, level, state, pos, villager) || !sameStack(tool, before);
    }

    public static void hurtTool(ItemStack tool, LivingEntity villager, EquipmentSlot slot) {
        if (!tool.isEmpty() && tool.isDamageableItem()) {
            tool.hurtAndBreak(1, villager, slot);
        }
    }

    private static boolean sameStack(ItemStack first, ItemStack second) {
        return first.getCount() == second.getCount() && ItemStack.isSameItemSameComponents(first, second);
    }
}
