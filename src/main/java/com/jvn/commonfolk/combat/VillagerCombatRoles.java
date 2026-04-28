package com.jvn.commonfolk.combat;

import com.jvn.commonfolk.config.CommonfolkConfig;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class VillagerCombatRoles {
    public static final float PLAYER_FIST_DAMAGE = 1.0F;

    private VillagerCombatRoles() {
    }

    public static boolean canFightBack(Villager villager) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (profession == VillagerProfession.WEAPONSMITH) {
            return CommonfolkConfig.WEAPONSMITHS_FIGHT_BACK.get();
        }
        if (profession == VillagerProfession.TOOLSMITH) {
            return CommonfolkConfig.TOOLSMITHS_FIGHT_BACK.get();
        }
        if (profession == VillagerProfession.ARMORER) {
            return CommonfolkConfig.ARMORERS_FIGHT_BACK.get();
        }
        if (profession == VillagerProfession.FLETCHER) {
            return CommonfolkConfig.FLETCHERS_FIGHT_BACK.get();
        }
        if (profession == VillagerProfession.BUTCHER) {
            return CommonfolkConfig.BUTCHERS_FIGHT_BACK.get();
        }
        return true;
    }

    public static float meleeDamage(Villager villager) {
        ItemStack weapon = villager.getMainHandItem().isEmpty() ? villager.getOffhandItem() : villager.getMainHandItem();
        if (weapon.isEmpty()) {
            return PLAYER_FIST_DAMAGE;
        }

        double baseDamage = PLAYER_FIST_DAMAGE;
        double[] totalDamage = new double[]{baseDamage};
        weapon.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (!attribute.equals(Attributes.ATTACK_DAMAGE)) {
                return;
            }

            double amount = modifier.amount();
            switch (modifier.operation()) {
                case ADD_VALUE -> totalDamage[0] += amount;
                case ADD_MULTIPLIED_BASE -> totalDamage[0] += amount * baseDamage;
                case ADD_MULTIPLIED_TOTAL -> totalDamage[0] += amount * totalDamage[0];
            }
        });

        return (float) Math.max(0.0D, totalDamage[0]);
    }

    public static ItemStack preferredWeapon(Villager villager) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (profession == VillagerProfession.WEAPONSMITH) {
            return new ItemStack(Items.IRON_SWORD);
        }
        if (profession == VillagerProfession.TOOLSMITH || profession == VillagerProfession.BUTCHER) {
            return new ItemStack(Items.IRON_AXE);
        }
        if (profession == VillagerProfession.ARMORER) {
            return new ItemStack(Items.IRON_SWORD);
        }

        return ItemStack.EMPTY;
    }

    public static double movementSpeed(Villager villager) {
        return 0.5D;
    }

    public static int attackCooldown(Villager villager) {
        return villager.getVillagerData().getProfession() == VillagerProfession.WEAPONSMITH ? 16 : 20;
    }

    public static boolean isArmorer(Villager villager) {
        return villager.getVillagerData().getProfession() == VillagerProfession.ARMORER;
    }

    public static boolean isCleric(Villager villager) {
        return villager.getVillagerData().getProfession() == VillagerProfession.CLERIC;
    }

    public static boolean isFarmer(Villager villager) {
        return villager.getVillagerData().getProfession() == VillagerProfession.FARMER;
    }

    public static boolean isFletcher(Villager villager) {
        return villager.getVillagerData().getProfession() == VillagerProfession.FLETCHER;
    }
}
