package com.jvn.villagerretaliation;

import com.jvn.villagerretaliation.command.VillagerRetaliationCommands;
import com.jvn.villagerretaliation.campaign.CampaignWorldModeService;
import com.jvn.villagerretaliation.campaign.CampaignWorldEventService;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceService;
import com.jvn.villagerretaliation.allegiance.VillageCombatAuthorizationService;
import com.jvn.villagerretaliation.allegiance.VillagerDisciplineService;
import com.jvn.villagerretaliation.allegiance.VillageNamingService;
import com.jvn.villagerretaliation.allegiance.VillageBoundsDebugService;
import com.jvn.villagerretaliation.api.VillagerRetaliationRegistries;
import com.jvn.villagerretaliation.block.VillagerRetaliationBlockEntityTypes;
import com.jvn.villagerretaliation.block.VillagerRetaliationBlocks;
import com.jvn.villagerretaliation.block.SellBoxCapabilities;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.compat.secondwind.VillagerSecondWindCompat;
import com.jvn.villagerretaliation.debug.VillagerRetaliationDebugItems;
import com.jvn.villagerretaliation.duel.DuelService;
import com.jvn.villagerretaliation.entity.VillagerRetaliationEntityTypes;
import com.jvn.villagerretaliation.event.VillagerRetaliationEvents;
import com.jvn.villagerretaliation.inventory.ContainerFilterResolver;
import com.jvn.villagerretaliation.inventory.VillagerRetaliationMenus;
import com.jvn.villagerretaliation.item.VillagerRetaliationCreativeTabs;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.jvn.villagerretaliation.interaction.work.HiredPathMemory;
import com.jvn.villagerretaliation.interaction.work.mining.HiredOreBlockTracker;
import com.jvn.villagerretaliation.network.VillagerReputationNetworking;
import com.jvn.villagerretaliation.mount.VillagerMountedCombatPolicy;
import com.jvn.villagerretaliation.mount.VillagerMountPassengers;
import com.jvn.villagerretaliation.profile.VillagerProfileManager;
import com.jvn.villagerretaliation.reputation.VillagerReputationEvents;
import com.jvn.villagerretaliation.raid.PlayerRaidDialogueService;
import com.jvn.villagerretaliation.raid.PlayerRaidService;
import com.jvn.villagerretaliation.recipe.VillagerRetaliationRecipes;
import com.jvn.villagerretaliation.social.VillagerSocialGraphService;
import com.jvn.villagerretaliation.social.VillagerBirthService;
import com.jvn.villagerretaliation.sell.SellBoxMarketSyncService;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueService;
import com.jvn.villagerretaliation.trade.VillagerSkillTradeEvents;
import com.jvn.villagerretaliation.villager.VillagerConversionPersistenceService;
import com.jvn.toucanlib.neoforge.event.ToucanEventBuses;
import com.jvn.toucanlib.util.ToucanIds;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(VillagerRetaliation.MOD_ID)
public class VillagerRetaliation {
    public static final String MOD_ID = "villagerretaliation";
    private static final ToucanIds IDS = ToucanIds.create(MOD_ID);

    public static ResourceLocation id(String path) {
        return IDS.id(path);
    }

    public VillagerRetaliation(IEventBus modEventBus, ModContainer modContainer) {
        com.jvn.villagerretaliation.command.VillagerTargetArgument.register(modEventBus);
        VillagerMountPassengers.init();
        VillagerRetaliationRegistries.registerBuiltIns();
        VillagerSecondWindCompat.init();
        VillagerRetaliationConfig.init();
        com.jvn.villagerretaliation.campaign.selection.WorldCampaignConfig.register(modContainer);
        com.jvn.villagerretaliation.settlement.SettlementChunkReceipts.register(modEventBus);
        com.jvn.villagerretaliation.interaction.work.mining.OreNodes.register(modEventBus);
        com.jvn.villagerretaliation.campaign.CampaignBlockOwnership.register(modEventBus);
        com.jvn.villagerretaliation.campaign.CampaignGeneratedBlocks.register(modEventBus);
        com.jvn.villagerretaliation.scene.encounter.EncounterActorBirths.register(modEventBus);
        com.jvn.villagerretaliation.storage.StorageRegistry.register(modEventBus);
        VillagerRetaliationBlocks.register(modEventBus);
        VillagerRetaliationBlockEntityTypes.register(modEventBus);
        VillagerRetaliationEntityTypes.register(modEventBus);
        VillagerRetaliationMenus.register(modEventBus);
        VillagerRetaliationItems.register(modEventBus);
        VillagerRetaliationRecipes.register(modEventBus);
        VillagerRetaliationCreativeTabs.register(modEventBus);
        VillagerRetaliationDebugItems.register(modEventBus);
        ToucanEventBuses.on(modEventBus)
                .listener(VillagerRetaliationEvents::onEntityAttributeModification)
                .listener(SellBoxCapabilities::register)
                .listener(VillagerReputationNetworking::registerPayloads);
        ToucanEventBuses.game()
                .listener(com.jvn.villagerretaliation.interaction.InteractionService::onServerTickPost)
                .listener(com.jvn.villagerretaliation.interaction.InteractionService::onLogout)
                .listener(com.jvn.villagerretaliation.interaction.InteractionService::onServerStopped)
                .listener(com.jvn.villagerretaliation.dialogue.normal.EntityDialogueRuntime::onServerTickPost)
                .listener(com.jvn.villagerretaliation.dialogue.conversation.ConversationService::onServerTickPost)
                .listener(com.jvn.villagerretaliation.dialogue.conversation.ConversationService::onLogout)
                .listener(com.jvn.villagerretaliation.dialogue.conversation.ConversationPresentationService::onServerTickPost)
                .listener(com.jvn.villagerretaliation.dialogue.conversation.ConversationPresentationService::onLogout)
                .listener(com.jvn.villagerretaliation.dialogue.conversation.ConversationPresentationService::onServerStopped)
                .listener(com.jvn.villagerretaliation.campaign.CampaignOwnedSpawns::onEntityJoin)
                .listener(com.jvn.villagerretaliation.scene.encounter.EncounterActorSpawning::onEntityJoin)
                .listener(com.jvn.villagerretaliation.campaign.CampaignCharacterLossService::onServerTickPost)
                .listener(com.jvn.villagerretaliation.campaign.CampaignCharacterLossService::onServerStopped)
                .listener(com.jvn.villagerretaliation.campaign.CampaignCharacterActivation::onServerTickPost)
                .listener(com.jvn.villagerretaliation.campaign.CampaignCharacterService::onConversion)
                .listener(com.jvn.villagerretaliation.campaign.selection.WorldCampaignSelection::onServerAboutToStart)
                .listener(com.jvn.villagerretaliation.campaign.selection.WorldCampaignSelection::onServerStopped)
                .listener(CampaignWorldModeService::onLevelLoad)
                .listener(CampaignWorldModeService::onServerStarted)
                .listener(CampaignWorldEventService::onServerTickPost)
                .listener(CampaignWorldEventService::onServerStopped)
                .listener(com.jvn.villagerretaliation.campaign.CampaignRewardService::onServerTickPost)
                .listener(com.jvn.villagerretaliation.campaign.CampaignRewardService::onServerStopped)
                .listener(VillagerRetaliationEvents::onServerStarted)
                .listener(VillagerRetaliationEvents::onServerStopping)
                .listener(VillagerRetaliationEvents::onAddReloadListeners)
                .listener(com.jvn.villagerretaliation.dialogue.resources.DialogueStyleResources::sync)
                .listener(com.jvn.villagerretaliation.interaction.work.mining.OreNodes::syncCatalog)
                .listener(com.jvn.villagerretaliation.interaction.work.mining.OreNodeDefinitions::onTagsUpdated)
                .listener(com.jvn.villagerretaliation.interaction.work.HiredAnimalBreedingTargets::onTagsUpdated)
                .listener(VillagerSkillTradeEvents::onVillagerTrades)
                .listener(VillagerSkillTradeEvents::onWandererTrades)
                .listener(VillagerRetaliationCommands::onRegisterCommands)
                .listener(DuelService::onAttackEntity)
                .listener(VillagerRetaliationEvents::onLivingDamagePre)
                .listener(VillagerMountedCombatPolicy::onProjectileImpact)
                .listener(VillagerRetaliationEvents::onLivingDamageFinalPre)
                .listener(VillagerRetaliationEvents::onLivingDamage)
                .listener(VillagerDisciplineService::onLivingDamage)
                .listener(VillagerReputationEvents::onLivingDamage)
                .listener(VillagerRetaliationEvents::onLivingDrops)
                .listener(VillagerRetaliationEvents::onEntityTickPre)
                .listener(VillagerRetaliationEvents::onEntityTickPost)
                .listener(VillageBoundsDebugService::onEntityTickPost)
                .listener(VillagerRetaliationEvents::onEntitySize)
                .listener(VillagerRetaliationEvents::onPlayerLoggedIn)
                .listener(VillagerRetaliationEvents::onServerTickPost)
                .listener(SellBoxMarketSyncService::onServerTickPost)
                .listener(VillageAllegianceService::onServerTickPost)
                .listener(VillageCombatAuthorizationService::onServerTickPost)
                .listener(VillagerReputationEvents::onServerTickPost)
                .listener(VillagerProfileManager::onServerTickPost)
                .listener(PlayerRaidService::onServerTickPost)
                .listener(PlayerRaidService::onUseItemStart)
                .listener(VillagerSocialGraphService::onLivingConversionPost)
                .listener(VillagerReputationEvents::onLivingConversionPost)
                .listener(VillagerConversionPersistenceService::onLivingConversionPost)
                .listener(VillageAllegianceService::onLivingConversionPost)
                .listener(VillagerDisciplineService::onLivingConversionPost)
                .listener(PlayerRaidService::onLivingConversionPost)
                .listener(VillagerRetaliationEvents::onEntityJoinLevel)
                .listener(VillagerSocialGraphService::onEntityJoinLevel)
                .listener(VillageAllegianceService::onEntityJoinLevel)
                .listener(VillagerRetaliationEvents::onPlayerStartTracking)
                .listener(VillagerRetaliationEvents::onPlayerLoggedOut)
                .listener(VillagerRetaliationEvents::onPlayerChangedDimension)
                .listener(VillagerRetaliationEvents::onItemCrafted)
                .listener(VillagerRetaliationEvents::onItemSmelted)
                .listener(VillageBoundsDebugService::onPlayerLoggedOut)
                .listener(PlayerRaidDialogueService::onPlayerLoggedOut)
                .listener(VillagerRetaliationEvents::onPlayerClone)
                .listener(VillagerRetaliationEvents::onEntityInteract)
                .listener(VillagerRetaliationEvents::onEntityInteractSpecific)
                .listener(VillageNamingService::onRightClickBlock)
                .listener(com.jvn.villagerretaliation.allegiance.VillageOverviewService::onRightClickBlock)
                .listener(VillagerRetaliationEvents::onRightClickBlock)
                .listener(VillagerRetaliationEvents::onQuestItemUse)
                .listener(VillagerRetaliationEvents::onLeftClickBlock)
                .listener(VillagerRetaliationEvents::onBlockBreak)
                .listener(VillagerRetaliationEvents::onBlockPlace)
                .listener(HiredOreBlockTracker::onBlockPlace)
                .listener(HiredOreBlockTracker::onFluidPlaceBlock)
                .listener(com.jvn.villagerretaliation.settlement.SettlementForestService::onChunkLoad)
                .listener(com.jvn.villagerretaliation.settlement.SettlementForestService::onChunkUnload)
                .listener(com.jvn.villagerretaliation.settlement.SettlementMineService::onChunkLoad)
                .listener(com.jvn.villagerretaliation.settlement.SettlementMineService::onChunkUnload)
                .listener(com.jvn.villagerretaliation.settlement.SettlementFieldService::onChunkLoad)
                .listener(com.jvn.villagerretaliation.settlement.SettlementFieldService::onChunkUnload)
                .listener(com.jvn.villagerretaliation.settlement.SettlementBuildingService::onChunkLoad)
                .listener(com.jvn.villagerretaliation.settlement.SettlementBuildingService::onChunkUnload)
                .listener(com.jvn.villagerretaliation.settlement.SettlementNavigationService::onChunkLoad)
                .listener(com.jvn.villagerretaliation.settlement.SettlementNavigationService::onChunkUnload)
                .listener(com.jvn.villagerretaliation.settlement.SettlementProjectileReceipts::onChunkLoad)
                .listener(com.jvn.villagerretaliation.settlement.SettlementProjectileReceipts::onChunkUnload)
                .listener(com.jvn.villagerretaliation.settlement.SettlementFurnaceService::onChunkLoad)
                .listener(com.jvn.villagerretaliation.settlement.SettlementFurnaceService::onChunkUnload)
                .listener(com.jvn.villagerretaliation.settlement.SettlementBrewingStandService::onChunkLoad)
                .listener(com.jvn.villagerretaliation.settlement.SettlementBrewingStandService::onChunkUnload)
                .listener(ContainerFilterResolver::onChunkUnload)
                .listener(HiredPathMemory::onFluidPlaceBlock)
                .listener(HiredOreBlockTracker::onChunkUnload)
                .listener(VillagerReputationEvents::onTradeWithVillager)
                .listener(com.jvn.villagerretaliation.duel.DuelService::onContainerOpen)
                .listener(VillagerRetaliationEvents::onQuestContainerOpen)
                .listener(ForcedDialogueService::onContainerOpen)
                .listener(VillagerReputationEvents::onContainerOpen)
                .listener(ForcedDialogueService::onItemToss)
                .listener(com.jvn.villagerretaliation.duel.DuelService::onItemToss)
                .listener(com.jvn.villagerretaliation.duel.DuelService::onItemPickup)
                .listener(ForcedDialogueService::onContainerClose)
                .listener(VillagerRetaliationEvents::onEntityLeaveLevel)
                .listener(VillageAllegianceService::onEntityLeaveLevel);

        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, com.jvn.villagerretaliation.campaign.CampaignEvidenceService::onInteract);

        // Death is cancellable (for example, Second Wind converts it into a downed state).
        // Run irreversible death bookkeeping only after other mods have had a chance to cancel it.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, VillagerRetaliationEvents::stabilizeGameTestOrigin);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,
                com.jvn.villagerretaliation.inventory.VillagerDefensiveLoadoutService::onLivingUseTotem);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, VillagerBirthService::onBabyEntitySpawn);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, VillagerRetaliationEvents::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, VillageAllegianceService::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, VillagerSocialGraphService::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, VillagerReputationEvents::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PlayerRaidService::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, com.jvn.villagerretaliation.progress.PlayerHistoryService::died);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.progress.PlayerHistoryService::advancementEarned);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.progress.PlayerHistoryService::dimensionChanged);
        NeoForge.EVENT_BUS.addListener(com.jvn.villagerretaliation.progress.ExplorationHistoryService::pickedUp);
    }
}
