package com.jvn.villagerretaliation.reputation;

import com.jvn.villagerretaliation.combat.VillagerCombatRoles;
import com.jvn.villagerretaliation.combat.WanderingTraderCombatRoles;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;

public final class VillagerAggressionPolicy {
    private VillagerAggressionPolicy() {
    }

    public static boolean shouldRetaliateDirectly(Villager villager, Player player) {
        return true;
    }

    public static boolean shouldNearbyVillagerAssist(AbstractVillager witness, Player player, ReputationEventType eventType) {
        return !witness.isBaby();
    }

    public static boolean shouldAggroFromWitnessedPlayerCrime(AbstractVillager witness, Player player) {
        return shouldAggroFromWitnessedPlayerCrime(witness, player, 0);
    }

    public static boolean shouldAggroFromWitnessedPlayerCrime(AbstractVillager witness, Player player, int pendingReputationChange) {
        if (witness.isBaby() || !(witness.level() instanceof ServerLevel level)) {
            return false;
        }
        int reputation = VillagerReputationManager.getReputation(level, witness, player.getUUID());
        return VillagerReputationLevel.fromReputation(reputation + pendingReputationChange) != VillagerReputationLevel.FEARED;
    }

    public static boolean shouldAttackOnSight(Villager villager, Player player) {
        return shouldAttackOnSight((AbstractVillager) villager, player);
    }

    public static boolean shouldAttackOnSight(AbstractVillager villager, Player player) {
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()
                || !VillagerRetaliationConfig.ENABLE_DESPISED_KILL_ON_SIGHT.get()
                || !(villager.level() instanceof ServerLevel level)
                || villager.isBaby()
                || !canFightBack(villager)) {
            return false;
        }

        if (villager instanceof Villager villageResident
                && villageResident.getVillagerData().getProfession() == VillagerProfession.NITWIT) {
            return false;
        }

        return VillagerReputationManager.isDespised(level, villager, player);
    }

    public static boolean shouldIronGolemsTargetNegativeReputationPlayer(AbstractVillager villager, Player player) {
        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()
                || !(villager.level() instanceof ServerLevel level)) {
            return false;
        }

        VillagerReputationLevel reputationLevel = VillagerReputationManager.getReputationLevel(level, villager, player.getUUID());
        return reputationLevel == VillagerReputationLevel.DESPISED || reputationLevel == VillagerReputationLevel.FEARED;
    }

    public static boolean shouldFleeFromPlayer(Villager villager, Player player) {
        if (!(villager.level() instanceof ServerLevel level)
                || !VillagerRetaliationConfig.ENABLE_VILLAGER_REPUTATION.get()) {
            return false;
        }

        VillagerReputationLevel reputationLevel = VillagerReputationManager.getReputationLevel(level, villager, player.getUUID());
        boolean lowEnoughToFlee = reputationLevel == VillagerReputationLevel.HOSTILE
                || reputationLevel == VillagerReputationLevel.DESPISED
                || reputationLevel == VillagerReputationLevel.FEARED;
        return lowEnoughToFlee
                && (villager.isBaby()
                || villager.getVillagerData().getProfession() == VillagerProfession.NITWIT
                || !VillagerCombatRoles.canFightBack(villager));
    }

    public static boolean shouldForgiveAccidentalHit(AbstractVillager villager, Player player) {
        return villager.level() instanceof ServerLevel level
                && VillagerReputationManager.isRespected(level, villager, player);
    }

    public static double getAngerDurationMultiplier(Villager villager, Player player) {
        return getAngerDurationMultiplier((AbstractVillager) villager, player);
    }

    public static double getAngerDurationMultiplier(AbstractVillager villager, Player player) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return 1.0D;
        }
        return switch (VillagerReputationManager.getReputationLevel(level, villager, player.getUUID())) {
            case REVERED -> 0.25D;
            case ROYALTY -> 0.15D;
            case RESPECTED -> 0.35D;
            case TRUSTED -> 0.65D;
            case HOSTILE -> 1.25D;
            case DESPISED -> 2.0D;
            case FEARED -> 2.0D;
            default -> 1.0D;
        };
    }

    private static boolean canFightBack(AbstractVillager villager) {
        if (villager instanceof Villager villageResident) {
            return VillagerCombatRoles.canFightBack(villageResident);
        }
        if (villager instanceof WanderingTrader trader) {
            return WanderingTraderCombatRoles.canFightBack(trader);
        }
        return false;
    }
}
