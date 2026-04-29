package com.jvn.commonfolk.villager;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class CommonfolkVillagerRules {
    private CommonfolkVillagerRules() {
    }

    public static boolean shouldKeepFleeingBehavior(Villager villager) {
        return villager.isBaby() || villager.getVillagerData().getProfession() == VillagerProfession.NITWIT;
    }

    public static boolean shouldSuppressFleeingBehavior(Villager villager) {
        return !shouldKeepFleeingBehavior(villager);
    }
}

