package com.jvn.villagerretaliation.villager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public final class VillagerArmorMending {
    private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    );

    private VillagerArmorMending() {
    }

    public static boolean canRepair(Villager villager) {
        return getRandomDamagedMendingArmor(villager).isPresent();
    }

    public static boolean repairWithXp(Villager villager, int value) {
        if (!(villager.level() instanceof ServerLevel serverLevel) || value <= 0) {
            return false;
        }

        Optional<EnchantedItemInUse> candidate = getRandomDamagedMendingArmor(villager);
        if (candidate.isEmpty()) {
            return false;
        }

        ItemStack armor = candidate.get().itemStack();
        int repair = EnchantmentHelper.modifyDurabilityToRepairFromXp(
                serverLevel,
                armor,
                (int) (value * armor.getXpRepairRatio())
        );
        int repaired = Math.min(repair, armor.getDamageValue());
        if (repaired <= 0) {
            return false;
        }

        armor.setDamageValue(armor.getDamageValue() - repaired);
        return true;
    }

    private static Optional<EnchantedItemInUse> getRandomDamagedMendingArmor(Villager villager) {
        List<EnchantedItemInUse> candidates = new ArrayList<>();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack armor = villager.getItemBySlot(slot);
            if (!armor.isDamaged()) {
                continue;
            }

            EnchantmentHelper.runIterationOnItem(armor, slot, villager, (enchantment, level, item) -> {
                if (enchantment.value().effects().has(EnchantmentEffectComponents.REPAIR_WITH_XP)
                        && enchantment.value().matchingSlot(slot)) {
                    candidates.add(item);
                }
            });
        }
        return Util.getRandomSafe(candidates, villager.getRandom());
    }
}
