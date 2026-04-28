package com.jvn.commonfolk.combat;

import com.jvn.commonfolk.config.CommonfolkConfig;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class VillagerCombatRoles {
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
        if (profession == VillagerProfession.CLERIC) {
            return CommonfolkConfig.CLERICS_USE_POTIONS.get();
        }
        if (profession == VillagerProfession.FARMER) {
            return CommonfolkConfig.FARMERS_USE_BREAD.get();
        }

        return false;
    }

    public static float meleeDamage(Villager villager) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (profession == VillagerProfession.WEAPONSMITH) {
            return 5.0F;
        }
        if (profession == VillagerProfession.TOOLSMITH || profession == VillagerProfession.BUTCHER) {
            return 3.5F;
        }
        if (profession == VillagerProfession.ARMORER || profession == VillagerProfession.FLETCHER) {
            return 3.0F;
        }
        if (profession == VillagerProfession.CLERIC) {
            return 2.0F;
        }
        if (profession == VillagerProfession.FARMER) {
            return 1.5F;
        }

        return 1.0F;
    }

    public static double movementSpeed(Villager villager) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (profession == VillagerProfession.WEAPONSMITH) {
            return 1.3D;
        }
        if (profession == VillagerProfession.FLETCHER) {
            return 1.1D;
        }

        return 1.15D;
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
