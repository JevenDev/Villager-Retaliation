package com.jvn.villagerretaliation.party;

import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;

public final class PartyTargetPolicy {
    private PartyTargetPolicy() {
    }

    public static boolean allows(PartyAttackMode mode, Villager villager, LivingEntity target) {
        PartyAttackMode resolved = mode == null ? PartyAttackMode.ALL : mode;
        return resolved.allows(
                target instanceof Animal,
                VillagerRetaliationVillagerCombatUtil.isNaturalHostileTarget(villager, target),
                target instanceof Player,
                target instanceof Villager,
                target instanceof IronGolem,
                PartyService.getPartyForEntity(target).isPresent());
    }
}
