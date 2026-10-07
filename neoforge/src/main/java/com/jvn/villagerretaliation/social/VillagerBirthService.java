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
    private static final String INITIALIZED = "VillagerRetaliationBirthInitialized";
    private static final java.util.Map<Villager, PendingBirth> PENDING = new java.util.IdentityHashMap<>();

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
        if (child.isSpawnCancelled() || child.getPersistentData().getBoolean(INITIALIZED)) return;
        sanitizeNewborn(child);

        if (VillagerRetaliationConfig.ENABLE_VILLAGER_SOCIAL_GRAPH.get()) {
            VillagerSocialGraphSavedData socialGraph = VillagerSocialGraphSavedData.get(level);
            if (!socialGraph.recordAcceptedBirth(level, parentA, parentB, child)) {
                child.getPersistentData().putBoolean(INITIALIZED, true);
                return;
            }
            socialGraph.linkParentsAndChild(level, parentA, parentB, child);
            VillagerReputationManager.inheritReputationFromParents(level, child, parentA, parentB);
        }
        VillageAllegianceService.assignBirthAllegiance(level, child, parentA, parentB);
        child.getPersistentData().putBoolean(INITIALIZED, true);
        PENDING.remove(child);
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
        sanitizeNewborn(child);
        PENDING.put(child, new PendingBirth(level, parentA, parentB, event));
    }

    public static void finishAcceptedSpawns() {
        var births = java.util.List.copyOf(PENDING.entrySet());
        PENDING.clear();
        for (var entry : births) {
            Villager child = entry.getKey();
            PendingBirth birth = entry.getValue();
            if (!birth.event.isCanceled() && child.isAddedToLevel() && child.isAlive() && !child.isRemoved()) {
                initializeNewborn(birth.level, birth.first, birth.second, child, birth.event.getCausedByPlayer());
            }
        }
    }

    public static void clearRuntimeState() {
        PENDING.clear();
    }

    private record PendingBirth(ServerLevel level, Villager first, Villager second, BabyEntitySpawnEvent event) {
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
