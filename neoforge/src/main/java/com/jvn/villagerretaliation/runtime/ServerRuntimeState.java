package com.jvn.villagerretaliation.runtime;

import com.jvn.villagerretaliation.allegiance.UnlawfulOrderService;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceService;
import com.jvn.villagerretaliation.allegiance.VillageBoundsDebugService;
import com.jvn.villagerretaliation.allegiance.VillageCombatAuthorizationService;
import com.jvn.villagerretaliation.allegiance.VillagerDisciplineService;
import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.combat.WanderingTraderRetaliationHandler;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.debug.HiredDebugPreviewService;
import com.jvn.villagerretaliation.dialogue.VillagerStoryHintService;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueService;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService;
import com.jvn.villagerretaliation.interaction.HiredVillagerIndex;
import com.jvn.villagerretaliation.interaction.HiredVillagerWorkService;
import com.jvn.villagerretaliation.interaction.VillagerCombatSurvivalService;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerRecruitmentService;
import com.jvn.villagerretaliation.interaction.work.HiredPathMemory;
import com.jvn.villagerretaliation.interaction.work.HiredRoleWorkerRegistry;
import com.jvn.villagerretaliation.interaction.work.mining.HiredOreBlockTracker;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.party.PartyQuickCommandService;
import com.jvn.villagerretaliation.party.PartyVillagerDropCollection;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.reputation.VillagerAmbientIndicatorService;
import com.jvn.villagerretaliation.reputation.VillagerGossipHooks;
import com.jvn.villagerretaliation.reputation.VillagerReputationAdvancements;
import com.jvn.villagerretaliation.reputation.VillagerReputationEvents;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.scene.encounter.EncounterService;
import com.jvn.villagerretaliation.social.VillagerSocialGraphService;
import com.jvn.villagerretaliation.trade.VillagerTradeMemory;
import com.jvn.villagerretaliation.trade.VillagerTradeUseTracker;
import com.jvn.villagerretaliation.util.VillagerDataWarmup;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerRules;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import net.minecraft.server.MinecraftServer;

/** Owns process-local state that must not survive a server lifecycle. */
public final class ServerRuntimeState {
    private ServerRuntimeState() {
    }

    public static void clear(MinecraftServer server) {
        // Restore live combat equipment before discarding its in-memory ownership state.
        VillagerRetaliationHandler.clearRuntimeState(server);
        WanderingTraderRetaliationHandler.clearRuntimeState(server);
        VillageCombatAuthorizationService.clearRuntimeState();
        VillagerDisciplineService.clearRuntimeState();

        VillagerDataWarmup.clearCaches();
        VillagerTaskNavigationUtil.clearRuntimeState();
        VillagerRetaliationVillagerRules.clearCachedChecks();

        VillagerGossipHooks.clear();
        VillageAllegianceService.clearRuntimeState(server);
        VillageBoundsDebugService.clearRuntimeState();
        VillagerReputationManager.clearRuntimeState();
        VillagerReputationEvents.clearRuntimeState();
        VillagerReputationAdvancements.clearRuntimeState();
        VillagerAmbientIndicatorService.clearRuntimeState();

        VillagerCombatSurvivalService.clearRuntimeState();
        VillagerDownedService.clearRuntimeState();
        VillagerConversationService.clearRuntimeState();
        VillagerRecruitmentService.clearRuntimeState();
        PartyVillagerContractService.clearRuntimeState();
        PartyQuickCommandService.clearRuntimeState();
        PartyVillagerDropCollection.clearRuntimeState();

        HiredVillagerWorkService.clearRuntimeState();
        HiredVillagerIndex.clearRuntimeState();
        HiredJobInventory.clearRuntimeState();
        HiredOreBlockTracker.clearRuntimeState();
        HiredPathMemory.clear();
        HiredRoleWorkerRegistry.clearRuntimeState();
        AssignedStorageService.clearRuntimeState();
        VillagerInventoryAccess.clearRuntimeState();

        VillagerTradeMemory.clearRuntimeState();
        VillagerTradeUseTracker.clearRuntimeState();
        VillagerSocialGraphService.clearRuntimeState();
        VillagerStoryHintService.clearRuntimeState();
        ForcedDialogueService.clearRuntimeState();
        DialogueTreeService.clearRuntimeState();
        VillagerQuestService.clearRuntimeState();
        HiredDebugPreviewService.clearRuntimeState();
        UnlawfulOrderService.clearRuntimeState();

        EncounterService.clearRuntimeState();
    }
}
