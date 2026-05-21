package com.jvn.villagerretaliation.villager;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class VillagerRetaliationVillagerRules {
    private VillagerRetaliationVillagerRules() {
    }

    public static boolean shouldKeepFleeingBehavior(Villager villager) {
        return villager.isBaby()
                || villager.getVillagerData().getProfession() == VillagerProfession.NITWIT
                && !VillagerRetaliationVillagerWeapons.hasUsableWeapon(villager)
                && !VillagerRetaliationVillagerWeapons.findNearestWeapon(villager).isPresent();
    }

    public static boolean shouldSuppressFleeingBehavior(Villager villager) {
        if (shouldKeepFleeingBehavior(villager)) {
            return false;
        }
        if (!canStandGroundAgainstHostileMobs(villager)) {
            return false;
        }

        return !VillagerRetaliationVillagerCombatUtil.hasVisibleCreeperThreat(
                villager,
                VillagerRetaliationConfig.NATURAL_HOSTILE_TARGET_RADIUS.get()
        );
    }

    public static boolean canStandGroundAgainstHostileMobs(Villager villager) {
        return VillagerRetaliationVillagerWeapons.hasUsableWeapon(villager)
                || VillagerRetaliationVillagerWeapons.hasTrackedPickup(villager)
                || VillagerRetaliationVillagerWeapons.findNearestWeapon(villager).isPresent();
    }
}
