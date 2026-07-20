package com.jvn.villagerretaliation.villager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public final class VillagerEquipmentMending {
    private VillagerEquipmentMending() {
    }

    public static boolean canRepair(Villager villager) {
        return getRandomDamagedMendingItem(villager).isPresent();
    }

    public static boolean repairWithXp(Villager villager, int value) {
        if (!(villager.level() instanceof ServerLevel serverLevel) || value <= 0) {
            return false;
        }

        Optional<EnchantedItemInUse> candidate = getRandomDamagedMendingItem(villager);
        if (candidate.isEmpty()) {
            return false;
        }

        ItemStack item = candidate.get().itemStack();
        int repair = EnchantmentHelper.modifyDurabilityToRepairFromXp(
                serverLevel,
                item,
                (int) (value * item.getXpRepairRatio())
        );
        int repaired = Math.min(repair, item.getDamageValue());
        if (repaired <= 0) {
            return false;
        }

        item.setDamageValue(item.getDamageValue() - repaired);
        return true;
    }

    public static boolean hasRepairWithXpEffect(ItemStack item, EquipmentSlot slot, LivingEntity holder) {
        if (item.isEmpty()) {
            return false;
        }

        boolean[] found = {false};
        EnchantmentHelper.runIterationOnItem(item, slot, holder, (enchantment, level, enchantedItem) -> {
            if (enchantment.value().effects().has(EnchantmentEffectComponents.REPAIR_WITH_XP)
                    && enchantment.value().matchingSlot(slot)) {
                found[0] = true;
            }
        });
        return found[0];
    }

    private static Optional<EnchantedItemInUse> getRandomDamagedMendingItem(Villager villager) {
        List<EnchantedItemInUse> candidates = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack item = villager.getItemBySlot(slot);
            if (!item.isDamaged()) {
                continue;
            }

            EnchantmentHelper.runIterationOnItem(item, slot, villager, (enchantment, level, enchantedItem) -> {
                if (enchantment.value().effects().has(EnchantmentEffectComponents.REPAIR_WITH_XP)
                        && enchantment.value().matchingSlot(slot)) {
                    candidates.add(enchantedItem);
                }
            });
        }
        return Util.getRandomSafe(candidates, villager.getRandom());
    }
}
