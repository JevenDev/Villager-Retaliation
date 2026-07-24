package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.notification.ResolvedVillagerNotification;
import com.jvn.villagerretaliation.profile.VillagerProfile;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.skill.VillagerProfessionSkills;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerTradeLevelingService;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import com.jvn.toucanlib.neoforge.network.ToucanNetwork;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class VillagerReputationNetworking {
    private static final String PROTOCOL_VERSION = "60";

    private VillagerReputationNetworking() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        ToucanNetwork network = ToucanNetwork.create(VillagerRetaliation.MOD_ID, PROTOCOL_VERSION, event);
        network.safePlayToClientThreaded(
                ServerConfigSyncPayload.TYPE,
                ServerConfigSyncPayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.config.VillagerRetaliationServerConfigClient",
                "accept"
        );
        network.safePlayToClientThreaded(
                BuilderStructureCatalogSyncPayload.TYPE,
                BuilderStructureCatalogSyncPayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.interaction.BuilderStructureCatalogClient",
                "accept"
        );
        network.safePlayToClientThreaded(
                VillagerReputationSyncPayload.TYPE,
                VillagerReputationSyncPayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.reputation.VillagerReputationClientCache",
                "accept"
        );
        network.safePlayToClientThreaded(
                VillagerReputationTierNoticePayload.TYPE,
                VillagerReputationTierNoticePayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.reputation.VillagerReputationNotificationOverlay",
                "accept"
        );
        network.safePlayToClientThreaded(
                FearedVillagerPulsePayload.TYPE,
                FearedVillagerPulsePayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.reputation.FearedVillagerAnimationClientCache",
                "accept"
        );
        network.safePlayToClientThreaded(
                VillagerDownedStatePayload.TYPE,
                VillagerDownedStatePayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.villager.VillagerDownedClientCache",
                "accept"
        );
        network.safePlayToClientThreaded(
                VillagerWorkAnimationPayload.TYPE,
                VillagerWorkAnimationPayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.villager.VillagerWorkAnimationClientCache",
                "accept"
        );
        network.safePlayToClientThreaded(
                VillagerNameSyncPayload.TYPE,
                VillagerNameSyncPayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.villager.VillagerNameClientCache",
                "accept"
        );
        network.safePlayToClientThreaded(
                VillageBoundsSyncPayload.TYPE,
                VillageBoundsSyncPayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.allegiance.VillageBoundsDebugRenderer",
                "accept"
        );
        network.safePlayToClientThreaded(
                VillagerHungerSyncPayload.TYPE,
                VillagerHungerSyncPayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.villager.VillagerHungerClientCache",
                "accept"
        );
        network.safePlayToClientThreaded(
                OpenVillageNamingPayload.TYPE,
                OpenVillageNamingPayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.allegiance.VillageNamingClient",
                "open"
        );
        network.safePlayToClientThreaded(
                OpenVillagerInteractionPayload.TYPE,
                OpenVillagerInteractionPayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.interaction.VillagerInteractionClientHandler",
                "open"
        );
        network.safePlayToClientThreaded(
                OpenVillagerDuelPayload.TYPE,
                OpenVillagerDuelPayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.interaction.VillagerInteractionClientHandler",
                "acceptDuelStatus"
        );
        network.safePlayToClientThreaded(
                DuelInventoryStatePayload.TYPE,
                DuelInventoryStatePayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.duel.DuelInventoryClientState",
                "accept"
        );
        network.safePlayToClientThreaded(
                VillagerDialogueResponsePayload.TYPE,
                VillagerDialogueResponsePayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.interaction.VillagerInteractionClientHandler",
                "acceptDialogue"
        );
        network.safePlayToClientThreaded(
                VillagerInteractionNoticePayload.TYPE,
                VillagerInteractionNoticePayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.interaction.VillagerInteractionClientHandler",
                "acceptNotice"
        );
        network.safePlayToClientThreaded(
                VillagerConversationEndedPayload.TYPE,
                VillagerConversationEndedPayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.interaction.VillagerInteractionClientHandler",
                "acceptConversationEnded"
        );
        network.safePlayToClientThreaded(
                RecruitmentResultPayload.TYPE,
                RecruitmentResultPayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.interaction.VillagerInteractionClientHandler",
                "acceptRecruitmentResult"
        );
        network.safePlayToClientThreaded(
                VillagerWorldTextIndicatorPayload.TYPE,
                VillagerWorldTextIndicatorPayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.reputation.VillagerWorldTextIndicatorClient",
                "accept"
        );
        network.safePlayToClientThreaded(
                GeneratedContainerTooltipPayload.TYPE,
                GeneratedContainerTooltipPayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.inventory.GeneratedContainerTooltipClient",
                "accept"
        );
        network.safePlayToClientThreaded(
                VillagerProfileSyncPayload.TYPE,
                VillagerProfileSyncPayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.profile.VillagerProfileClientCache",
                "accept"
        );
        network.safePlayToClientThreaded(
                VillagerTradeRefreshStatePayload.TYPE,
                VillagerTradeRefreshStatePayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.trade.VillagerTradeRefreshButtons",
                "acceptState"
        );
        network.safePlayToClientThreaded(
                QuestTrackerSyncPayload.TYPE,
                QuestTrackerSyncPayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.quest.VillagerQuestTrackerOverlay",
                "accept"
        );
        network.safePlayToClientThreaded(
                ClipboardAssignedStorageSyncPayload.TYPE,
                ClipboardAssignedStorageSyncPayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.inventory.ClipboardStorageOutlineRenderer",
                "accept"
        );
        network.safePlayToClientThreaded(
                ClipboardWorkAreaSyncPayload.TYPE,
                ClipboardWorkAreaSyncPayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.inventory.ClipboardStorageOutlineRenderer",
                "accept"
        );
        network.safePlayToClientThreaded(
                ClipboardRouteSyncPayload.TYPE,
                ClipboardRouteSyncPayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.inventory.ClipboardStorageOutlineRenderer",
                "accept"
        );
        network.safePlayToClientThreaded(
                HiredDebugPreviewSyncPayload.TYPE,
                HiredDebugPreviewSyncPayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.inventory.ClipboardStorageOutlineRenderer",
                "acceptDebugPreview"
        );
        network.safePlayToClientThreaded(
                ClipboardPreviewMarkerSyncPayload.TYPE,
                ClipboardPreviewMarkerSyncPayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.inventory.ClipboardStorageOutlineRenderer",
                "acceptClipboardPreviewMarkers"
        );
        network.safePlayToClientThreaded(
                ClipboardWorkforceSyncPayload.TYPE,
                ClipboardWorkforceSyncPayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.inventory.ClipboardWorkforceClient",
                "accept"
        );
        network.safePlayToClientThreaded(
                PartyRosterSyncPayload.TYPE,
                PartyRosterSyncPayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.party.PartyRosterClient",
                "accept"
        );
        network.safePlayToClientThreaded(
                PartyInvitationSyncPayload.TYPE,
                PartyInvitationSyncPayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.party.PartyInvitationClient",
                "accept"
        );
        network.safePlayToClientThreaded(
                VillagerMountTargetModePayload.TYPE,
                VillagerMountTargetModePayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.mount.VillagerMountTargetClient",
                "accept"
        );
        network.playToServer(
                VillagerMountTargetCancelPayload.TYPE,
                VillagerMountTargetCancelPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player ->
                                com.jvn.villagerretaliation.mount.VillagerMountAssignmentService.cancelTargeting(player)))
        );
        network.playToServer(
                PartyActionRequestPayload.TYPE,
                PartyActionRequestPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player ->
                                com.jvn.villagerretaliation.party.PartyActionHandler.handle(player, payload)))
        );
        network.playToServer(
                PartyQuickCommandRequestPayload.TYPE,
                PartyQuickCommandRequestPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player ->
                                com.jvn.villagerretaliation.party.PartyQuickCommandService.handle(player, payload)))
        );
        network.playToServer(
                QuestTrackerRequestPayload.TYPE,
                QuestTrackerRequestPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> com.jvn.villagerretaliation.quest.VillagerQuestService.handleTrackerRequest(
                                player,
                                payload.questId(),
                                payload.action()
                        )))
        );
        network.playToServer(
                VillagerReputationRequestPayload.TYPE,
                VillagerReputationRequestPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> VillagerInteractionService.handleReputationRequest(
                            player,
                            payload.entityId()
                    )))
        );
        network.playToServer(
                VillageBoundsSubscriptionPayload.TYPE,
                VillageBoundsSubscriptionPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player ->
                                com.jvn.villagerretaliation.allegiance.VillageBoundsDebugService.setSubscribed(
                                        player, payload.enabled())))
        );
        network.playToServer(
                VillageRenameRequestPayload.TYPE,
                VillageRenameRequestPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player ->
                                com.jvn.villagerretaliation.allegiance.VillageNamingService.handleRename(player, payload)))
        );
        network.playToServer(
                VillagerAllegianceActionPayload.TYPE,
                VillagerAllegianceActionPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> VillagerInteractionService.handleAllegianceAction(
                                player,
                                payload.entityId(),
                                payload.action()
                        )))
        );
        network.playToServer(
                VillagerProfileRequestPayload.TYPE,
                VillagerProfileRequestPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> VillagerInteractionService.handleProfileRequest(
                            player,
                            payload.entityId()
                    )))
        );
        network.playToServer(
                VillagerDialogueRequestPayload.TYPE,
                VillagerDialogueRequestPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> VillagerInteractionService.handleDialogueRequest(
                            player,
                            payload.entityId(),
                            payload.optionId()
                    )))
        );
        network.playToServer(
                VillagerMouseEasterEggPayload.TYPE,
                VillagerMouseEasterEggPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> VillagerInteractionService.handleMouseEasterEggRequest(
                            player,
                            payload.entityId(),
                            payload.kind()
                    )))
        );
        network.playToServer(
                VillagerTradeRequestPayload.TYPE,
                VillagerTradeRequestPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> VillagerInteractionService.handleTradeRequest(
                            player,
                            payload.entityId()
                    )))
        );
        network.playToServer(
                VillagerRoutineChatTogglePayload.TYPE,
                VillagerRoutineChatTogglePayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> VillagerInteractionService.handleRoutineChatToggle(
                            player,
                            payload.entityId(),
                            payload.muted()
                    )))
        );
        network.playToServer(
                VillagerTradeRefreshRequestPayload.TYPE,
                VillagerTradeRefreshRequestPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> VillagerInteractionService.handleTradeRefreshRequest(
                            player,
                            payload.entityId(),
                            payload.offerIndex()
                    )))
        );
        network.playToServer(
                VillagerInventoryRequestPayload.TYPE,
                VillagerInventoryRequestPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> VillagerInteractionService.handleInventoryRequest(
                            player,
                            payload.entityId()
                    )))
        );
        network.playToServer(
                VillagerJobInventoryRequestPayload.TYPE,
                VillagerJobInventoryRequestPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> VillagerInteractionService.handleJobInventoryRequest(
                            player,
                            payload.entityId(),
                            payload.jobInventory()
                    )))
        );
        network.playToServer(
                VillagerGiftRequestPayload.TYPE,
                VillagerGiftRequestPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> VillagerInteractionService.handleGiftRequest(
                            player,
                            payload.entityId(),
                            payload.inventorySlot()
                    )))
        );
        network.playToServer(
                VillagerDuelRequestPayload.TYPE,
                VillagerDuelRequestPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player ->
                                com.jvn.villagerretaliation.duel.DuelRequestHandler.handle(player, payload)))
        );
        network.playToServer(
                VillagerRecruitRequestPayload.TYPE,
                VillagerRecruitRequestPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> VillagerInteractionService.handleRecruitRequest(
                            player,
                            payload.entityId(),
                            payload.action(),
                            payload.selectedRole(),
                            payload.expectedRevision()
                    )))
        );
        network.playToServer(
                HiredBrewingOrderPayload.TYPE,
                HiredBrewingOrderPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> VillagerInteractionService.handleBrewingOrderRequest(
                            player,
                            payload.entityId(),
                            payload.itemId(),
                            payload.potionId(),
                            payload.amount(),
                            payload.continuous()
                    )))
        );
        network.playToServer(
                HiredBuilderOrderPayload.TYPE,
                HiredBuilderOrderPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> VillagerInteractionService.handleBuilderOrderRequest(
                            player,
                            payload.entityId(),
                            payload.action(),
                            payload.structureId()
                    )))
        );
        network.playToServer(
                ConstructionBlueprintPlacementPayload.TYPE,
                ConstructionBlueprintPlacementPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> VillagerInteractionService.handleConstructionBlueprintPlacement(
                            player,
                            payload.jobId(),
                            payload.action(),
                            payload.steps(),
                            payload.targetPos()
                    )))
        );
        network.playToServer(
                ClipboardWorkAreaDraftPayload.TYPE,
                ClipboardWorkAreaDraftPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> com.jvn.villagerretaliation.item.HiredStorageClipboardItem.handleWorkAreaDraftAction(
                            player,
                            payload.action(),
                            payload.steps()
                    )))
        );
        network.playToServer(
                HiredLoggingFilterPayload.TYPE,
                HiredLoggingFilterPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> VillagerInteractionService.handleLoggingFilterRequest(
                            player,
                            payload.entityId(),
                            payload.filterId()
                    )))
        );
        network.playToServer(
                HiredLoggingOptionPayload.TYPE,
                HiredLoggingOptionPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> VillagerInteractionService.handleLoggingOptionRequest(
                            player,
                            payload.entityId(),
                            payload.optionId()
                    )))
        );
        network.playToServer(
                HiredFarmingOptionPayload.TYPE,
                HiredFarmingOptionPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> VillagerInteractionService.handleFarmingOptionRequest(
                            player,
                            payload.entityId(),
                            payload.optionId()
                    )))
        );
        network.playToServer(
                HiredHuntingTargetPayload.TYPE,
                HiredHuntingTargetPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> VillagerInteractionService.handleHuntingTargetRequest(
                            player,
                            payload.entityId(),
                            payload.targetId()
                    )))
        );
        network.playToServer(
                HiredAnimalBreedingTargetPayload.TYPE,
                HiredAnimalBreedingTargetPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> VillagerInteractionService.handleAnimalBreedingTargetRequest(
                            player,
                            payload.entityId(),
                            payload.targetId()
                    )))
        );
        network.playToServer(
                HiredAnimalCullCapPayload.TYPE,
                HiredAnimalCullCapPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> VillagerInteractionService.handleAnimalCullCapRequest(
                            player,
                            payload.entityId(),
                            payload.cap()
                    )))
        );
        network.playToServer(
                ClipboardStorageActionPayload.TYPE,
                ClipboardStorageActionPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> VillagerInteractionService.handleClipboardStorageAction(
                            player,
                            payload.entityId(),
                            payload.action()
                    )))
        );
        network.playToServer(
                ClipboardWorkAreaActionPayload.TYPE,
                ClipboardWorkAreaActionPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> VillagerInteractionService.handleClipboardWorkAreaAction(
                            player,
                            payload.villagerId(),
                            payload.action(),
                            payload.steps()
                    )))
        );
        network.playToServer(
                ClipboardModeChangePayload.TYPE,
                ClipboardModeChangePayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> com.jvn.villagerretaliation.item.HiredStorageClipboardItem.changeClipboardMode(
                            player,
                            payload.delta(),
                            payload.menuSlotIndex(),
                            payload.storageVariantOnly()
                    )))
        );
        network.playToServer(
                ClipboardPreviewTogglePayload.TYPE,
                ClipboardPreviewTogglePayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player ->
                                com.jvn.villagerretaliation.debug.HiredDebugPreviewService.configureClipboardPreview(
                                        player,
                                        payload.enabled(),
                                        payload.lens(),
                                        payload.trackedJobs())))
        );
        network.playToServer(
                ItemFilterModeChangePayload.TYPE,
                ItemFilterModeChangePayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player ->
                                com.jvn.villagerretaliation.item.VillagerItemFilterItem.handleModeChange(
                                        player,
                                        payload.menuSlotIndex(),
                                        payload.modeId())))
        );
        network.playToServer(
                HiredHitboxDebugPreviewPayload.TYPE,
                HiredHitboxDebugPreviewPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player ->
                                com.jvn.villagerretaliation.debug.HiredDebugPreviewService.setHitboxDebugPreviewEnabled(
                                        player,
                                        payload.enabled())))
        );
        network.playToServer(
                VillagerConversationEndRequestPayload.TYPE,
                VillagerConversationEndRequestPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> VillagerInteractionService.handleConversationEndRequest(
                            player,
                            payload.entityId()
                    )))
        );
    }

    public static void sendReputation(ServerPlayer player, AbstractVillager villager, int reputation) {
        trySendToPlayer(player, new VillagerReputationSyncPayload(
                villager.getId(),
                villager.getUUID(),
                reputation,
                VillagerReputationLevel.fromReputation(reputation)
        ));
    }

    public static void sendServerConfig(ServerPlayer player) {
        trySendToPlayer(player, new ServerConfigSyncPayload(
                VillagerRetaliationConfig.SHOW_VILLAGER_NAME_TAGS.get(),
                VillagerRetaliationConfig.VILLAGER_STAT_DISPLAY_MODE.get()
        ));
        sendBuilderStructureCatalog(player);
    }

    public static void sendBuilderStructureCatalog(ServerPlayer player) {
        if (player == null || player.server == null) {
            return;
        }
        trySendToPlayer(player, new BuilderStructureCatalogSyncPayload(
                BuilderStructureCatalog.entries(player.server)
        ));
    }

    private static void trySendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        try {
            PacketDistributor.sendToPlayer(player, payload);
        } catch (UnsupportedOperationException ignored) {
            // Some server-side harnesses use mock connections that cannot negotiate mod payloads.
        }
    }

    public static void sendFearedPulse(AbstractVillager villager, int ticks) {
        PacketDistributor.sendToPlayersTrackingEntity(villager, new FearedVillagerPulsePayload(villager.getId(), ticks));
    }

    public static void sendDownedState(ServerPlayer player, net.minecraft.world.entity.npc.Villager villager, boolean downed) {
        trySendToPlayer(player, new VillagerDownedStatePayload(villager.getId(), downed,
                com.jvn.villagerretaliation.combat.downed.VillagerDownedService.pose(villager).id()));
    }

    public static void syncDownedStateToTracking(net.minecraft.world.entity.npc.Villager villager, boolean downed) {
        PacketDistributor.sendToPlayersTrackingEntity(
                villager,
                new VillagerDownedStatePayload(villager.getId(), downed,
                        com.jvn.villagerretaliation.combat.downed.VillagerDownedService.pose(villager).id()));
    }

    public static void sendName(ServerPlayer player, Entity villager) {
        VillagerNameSyncPayload payload = namePayload(villager);
        if (payload != null) {
            trySendToPlayer(player, payload);
        }
    }

    public static void syncNameToTracking(Entity villager) {
        VillagerNameSyncPayload payload = namePayload(villager);
        if (payload != null) {
            PacketDistributor.sendToPlayersTrackingEntity(villager, payload);
        }
    }

    private static VillagerNameSyncPayload namePayload(Entity villager) {
        if (!VillagerPresetNameRegistry.isVillagerForm(villager)) {
            return null;
        }
        String name = villager.hasCustomName() && villager.getCustomName() != null
                ? villager.getCustomName().getString().trim()
                : VillagerPresetNameRegistry.resolvePresetName(villager);
        if (name.isBlank()) {
            return null;
        }
        return new VillagerNameSyncPayload(
                villager.getId(), villager.getUUID(), "", name, isHiredVillager(villager));
    }

    private static boolean isHiredVillager(Entity entity) {
        return entity instanceof net.minecraft.world.entity.npc.Villager villager
                && villager.level() instanceof ServerLevel level
                && com.jvn.villagerretaliation.interaction.HiredVillagerContractService.isHired(level, villager);
    }

    public static void sendTierNotice(ServerPlayer player, String text) {
        trySendToPlayer(player, new VillagerReputationTierNoticePayload(text));
    }

    public static void sendNotice(ServerPlayer player, String text, VillagerReputationNoticeKind kind) {
        trySendToPlayer(player, new VillagerReputationTierNoticePayload(text, kind));
    }

    public static void sendNotice(ServerPlayer player, ResolvedVillagerNotification notification) {
        trySendToPlayer(player, new VillagerReputationTierNoticePayload(
                notification.text(),
                notification.noticeKind(),
                notification.textColor(),
                notification.chatColor()
        ));
    }

    public static void sendProfile(ServerPlayer player, AbstractVillager villager, VillagerProfile profile) {
        trySendToPlayer(player, VillagerProfileSyncPayload.create(
                villager.getId(),
                profile.villagerUuid(),
                profile.lastKnownProfession(),
                profile.generatedVersion(),
                profile.socialAttributes(),
                profile.skillGeneratedVersion(),
                profile.skills(),
                isSkillBasedTradeLevelingEnabled(villager),
                profile.tradeLevelSkillAdjustedXpProgress(),
                tradeLevelXpMultiplier(villager, profile)
        ));
    }

    public static void sendHunger(ServerPlayer player, Villager villager, int hunger) {
        trySendToPlayer(player, new VillagerHungerSyncPayload(villager.getId(), hunger));
    }

    public static void syncHungerToTracking(Villager villager, int hunger) {
        try {
            PacketDistributor.sendToPlayersTrackingEntity(
                    villager,
                    new VillagerHungerSyncPayload(villager.getId(), hunger));
        } catch (UnsupportedOperationException ignored) {
            // Some server-side harnesses track entities through mock connections without payload support.
        }
    }

    private static double tradeLevelXpMultiplier(AbstractVillager villager, VillagerProfile profile) {
        if (!isSkillBasedTradeLevelingEnabled(villager)) {
            return 1.0D;
        }
        Villager villageResident = (Villager) villager;
        VillagerSkill primarySkill = VillagerProfessionSkills.primarySkill(villageResident);
        return VillagerTradeLevelingService.tradeLevelXpMultiplier(profile.skills().get(primarySkill));
    }

    private static boolean isSkillBasedTradeLevelingEnabled(AbstractVillager villager) {
        return VillagerRetaliationConfig.ENABLE_SKILL_BASED_TRADE_LEVELING.get()
                && villager instanceof Villager villageResident
                && !villageResident.isBaby();
    }

    public static void sendWorldTextIndicator(AbstractVillager villager, String text, VillagerWorldTextIndicatorKind kind) {
        sendWorldTextIndicator(villager, text, kind, ResolvedVillagerNotification.DEFAULT_COLOR);
    }

    public static void sendWorldTextIndicator(
            AbstractVillager villager,
            String text,
            VillagerWorldTextIndicatorKind kind,
            int textColor) {
        PacketDistributor.sendToPlayersTrackingEntity(
                villager,
                new VillagerWorldTextIndicatorPayload(villager.getId(), text, kind, textColor)
        );
    }
}
