package com.jvn.villagerretaliation.villager;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class VillagerRetaliationVillagerRules {
    private VillagerRetaliationVillagerRules() {
    }

    public static boolean shouldKeepFleeingBehavior(Villager villager) {
        return villager.isBaby()
                || villager.getVillagerData().getProfession() == VillagerProfession.NITWIT
                && !VillagerRetaliationVillagerWeapons.hasUsableWeapon(villager);
    }

    public static boolean shouldSuppressFleeingBehavior(Villager villager) {
        return !shouldKeepFleeingBehavior(villager);
    }
}
