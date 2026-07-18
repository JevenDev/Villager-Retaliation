package com.jvn.villagerretaliation.social;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceService;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerWorkService;
import com.jvn.villagerretaliation.interaction.VillagerRecruitmentService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;

/** Validates villager births and performs all newborn initialization in one deliberate order. */
public final class VillagerBirthService {
    private VillagerBirthService() {
    }

    public static boolean validateBirth(ServerLevel level, Villager parentA, Villager parentB) {
        return VillagerBreedingPolicy.evaluatePair(level, parentA, parentB).allowed();
    }

    public static void initializeNewborn(
            ServerLevel level,
            Villager parentA,
            Villager parentB,
            Villager child,
            @Nullable Player causedByPlayer) {
        sanitizeNewborn(child);

        if (VillagerRetaliationConfig.ENABLE_VILLAGER_SOCIAL_GRAPH.get()) {
            VillagerSocialGraphSavedData socialGraph = VillagerSocialGraphSavedData.get(level);
            socialGraph.linkParentsAndChild(level, parentA, parentB, child);
            VillagerReputationManager.inheritReputationFromParents(level, child, parentA, parentB);
        }
        VillageAllegianceService.assignBirthAllegiance(level, child, parentA, parentB);
        VillageEventMemory.remember(
                level,
                VillageEventMemory.EventTag.BABY_BORN,
                child.blockPosition(),
                child,
                causedByPlayer);
    }

    public static void onBabyEntitySpawn(BabyEntitySpawnEvent event) {
        if (event.isCanceled()
                || !(event.getParentA() instanceof Villager parentA)
                || !(event.getParentB() instanceof Villager parentB)
                || !(event.getChild() instanceof Villager child)
                || !(parentA.level() instanceof ServerLevel level)) {
            return;
        }

        BreedingDecision decision = VillagerBreedingPolicy.evaluatePair(level, parentA, parentB);
        if (!decision.allowed()) {
            event.setCanceled(true);
            if (event.getCausedByPlayer() instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable(decision.messageKey()), true);
            }
            return;
        }
        initializeNewborn(level, parentA, parentB, child, event.getCausedByPlayer());
    }

    private static void sanitizeNewborn(Villager child) {
        HiredVillagerContractService.clearInheritedStateForNewborn(child);
        PartyVillagerContractService.clearInheritedStateForNewborn(child);
        VillagerRecruitmentService.clearInheritedStateForNewborn(child);
        HiredVillagerWorkService.clearInheritedStateForNewborn(child);
        HiredJobInventory.clearInheritedStateForNewborn(child);
        VillagerDownedService.clearInheritedStateForNewborn(child);
    }
}
