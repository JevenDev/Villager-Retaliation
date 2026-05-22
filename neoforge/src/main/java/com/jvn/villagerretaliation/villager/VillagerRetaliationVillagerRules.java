package com.jvn.villagerretaliation.villager;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class VillagerRetaliationVillagerRules {
    private VillagerRetaliationVillagerRules() {
    }

    public static boolean shouldKeepFleeingBehavior(Villager villager) {
        if (villager.isBaby()) {
            return true;
        }
        if (villager.getVillagerData().getProfession() != VillagerProfession.NITWIT
                || VillagerRetaliationVillagerWeapons.hasUsableWeapon(villager)
                || VillagerInventoryAccess.hasBorrowedCombatWeapon(villager)
                || VillagerInventoryAccess.hasUsableWeapon(villager)) {
            return false;
        }

        return !canPickUpGroundWeapons() || !VillagerRetaliationVillagerWeapons.findNearestWeapon(villager).isPresent();
    }

    public static boolean shouldSuppressFleeingBehavior(Villager villager) {
        if (shouldKeepFleeingBehavior(villager)) {
            return false;
        }
        if (!canStandGroundAgainstHostileMobs(villager)) {
            return false;
        }
        if (!VillagerRetaliationConfig.VILLAGERS_FLEE_VISIBLE_CREEPERS.get()) {
            return true;
        }

        return !VillagerRetaliationVillagerCombatUtil.hasVisibleCreeperThreat(
                villager,
                VillagerRetaliationConfig.NATURAL_HOSTILE_TARGET_RADIUS.get()
        );
    }

    public static boolean canStandGroundAgainstHostileMobs(Villager villager) {
        if (!VillagerRetaliationConfig.VILLAGERS_STAND_GROUND_AGAINST_HOSTILE_MOBS.get()) {
            return false;
        }

        return VillagerRetaliationVillagerWeapons.hasUsableWeapon(villager)
                || VillagerRetaliationVillagerWeapons.hasTrackedPickup(villager)
                || VillagerInventoryAccess.hasBorrowedCombatWeapon(villager)
                || VillagerInventoryAccess.hasUsableWeapon(villager)
                || canPickUpGroundWeapons() && VillagerRetaliationVillagerWeapons.findNearestWeapon(villager).isPresent();
    }

    private static boolean canPickUpGroundWeapons() {
        return VillagerRetaliationConfig.VILLAGERS_PICK_UP_GROUND_WEAPONS.get();
    }
}
