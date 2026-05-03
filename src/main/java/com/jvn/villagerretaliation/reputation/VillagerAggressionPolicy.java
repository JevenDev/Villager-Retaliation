package com.jvn.villagerretaliation.reputation;

import com.jvn.villagerretaliation.combat.VillagerCombatRoles;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;

public final class VillagerAggressionPolicy {
    private VillagerAggressionPolicy() {
    }

    public static boolean shouldRetaliateDirectly(Villager villager, Player player) {
        return true;
    }

    public static boolean shouldNearbyVillagerAssist(Villager witness, Player player, ReputationEventType eventType) {
        return !witness.isBaby();
    }

    public static boolean shouldAttackOnSight(Villager villager, Player player) {
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()
                || !VillagerRetaliationConfig.ENABLE_DESPISED_KILL_ON_SIGHT.get()
                || !(villager.level() instanceof ServerLevel level)
                || villager.isBaby()
                || villager.getVillagerData().getProfession() == VillagerProfession.NITWIT
                || !VillagerCombatRoles.canFightBack(villager)) {
            return false;
        }
        return VillagerReputationManager.isDespised(level, villager, player);
    }

    public static boolean shouldFleeFromPlayer(Villager villager, Player player) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return false;
        }
        return VillagerReputationManager.isDespised(level, villager, player)
                && (villager.isBaby()
                || villager.getVillagerData().getProfession() == VillagerProfession.NITWIT
                || !VillagerCombatRoles.canFightBack(villager));
    }

    public static boolean shouldForgiveAccidentalHit(Villager villager, Player player) {
        return villager.level() instanceof ServerLevel level
                && VillagerReputationManager.isRespected(level, villager, player);
    }

    public static double getAngerDurationMultiplier(Villager villager, Player player) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return 1.0D;
        }
        return switch (VillagerReputationManager.getReputationLevel(level, villager, player.getUUID())) {
            case REVERED -> 0.25D;
            case RESPECTED -> 0.35D;
            case TRUSTED -> 0.65D;
            case HOSTILE -> 1.25D;
            case DESPISED -> 2.0D;
            default -> 1.0D;
        };
    }
}
