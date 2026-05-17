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
                && !VillagerRetaliationVillagerWeapons.hasUsableWeapon(villager);
    }

    public static boolean shouldSuppressFleeingBehavior(Villager villager) {
        if (shouldKeepFleeingBehavior(villager)) {
            return false;
        }

        return !VillagerRetaliationVillagerCombatUtil.hasVisibleCreeperThreat(
                villager,
                VillagerRetaliationConfig.VILLAGER_KILL_AGGRO_RADIUS.get()
        );
    }
}
