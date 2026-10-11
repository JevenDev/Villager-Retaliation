package com.jvn.villagerretaliation.repair;

import com.jvn.villagerretaliation.VillagerRetaliation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class SmithRepairService {
    public static final TagKey<Item> TOOLS = tag("toolsmith_repairs");
    public static final TagKey<Item> WEAPONS = tag("weaponsmith_repairs");
    public static final TagKey<Item> ARMOR = tag("armorer_repairs");

    private SmithRepairService() {
    }

    private static TagKey<Item> tag(String name) {
        return TagKey.create(Registries.ITEM, VillagerRetaliation.id(name));
    }

    public static boolean isSmith(Villager villager) {
        var profession = villager.getVillagerData().getProfession();
        return !villager.isBaby() && (profession == VillagerProfession.TOOLSMITH
                || profession == VillagerProfession.WEAPONSMITH || profession == VillagerProfession.ARMORER);
    }

    public static boolean specialized(VillagerProfession profession, ItemStack stack) {
        return profession == VillagerProfession.TOOLSMITH && stack.is(TOOLS)
                || profession == VillagerProfession.WEAPONSMITH && stack.is(WEAPONS)
                || profession == VillagerProfession.ARMORER && stack.is(ARMOR);
    }

    public static boolean repairable(ItemStack stack) {
        return !stack.isEmpty() && stack.isDamageableItem() && stack.isRepairable()
                && BuiltInRegistries.ITEM.stream().anyMatch(item -> validMaterial(stack, item.getDefaultInstance()));
    }

    public static boolean validMaterial(ItemStack item, ItemStack material) {
        return !item.isEmpty() && !material.isEmpty() && item.getItem().isValidRepairItem(item, material);
    }

    public static int amount(ItemStack stack, boolean specialized) {
        return Math.max(1, (int) ((long) stack.getMaxDamage() * (specialized ? 35 : 25) / 100));
    }

    public static int cost(int base, boolean specialized, boolean suppliedMaterial) {
        return base * (specialized ? 1 : 2) * (suppliedMaterial ? 1 : 2);
    }

    public static ItemStack preview(ItemStack stack, boolean specialized) {
        ItemStack result = stack.copyWithCount(1);
        result.setDamageValue(Math.max(0, stack.getDamageValue() - amount(stack, specialized)));
        return result;
    }
}
