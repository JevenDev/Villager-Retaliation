package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.notification.ResolvedVillagerNotification;
import com.jvn.villagerretaliation.profile.VillagerProfile;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import com.jvn.toucanlib.neoforge.network.ToucanNetwork;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class VillagerReputationNetworking {
    private static final String PROTOCOL_VERSION = "32";

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
                VillagerNameSyncPayload.TYPE,
                VillagerNameSyncPayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.villager.VillagerNameClientCache",
                "accept"
        );
        network.safePlayToClientThreaded(
                OpenVillagerInteractionPayload.TYPE,
                OpenVillagerInteractionPayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.interaction.VillagerInteractionClientHandler",
                "open"
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
                HiredDebugPreviewSyncPayload.TYPE,
                HiredDebugPreviewSyncPayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.inventory.ClipboardStorageOutlineRenderer",
                "acceptDebugPreview"
        );
        network.safePlayToClientThreaded(
                ClipboardWorkforceSyncPayload.TYPE,
                ClipboardWorkforceSyncPayload.STREAM_CODEC,
                "com.jvn.villagerretaliation.client.inventory.ClipboardWorkforceClient",
                "accept"
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
                VillagerRecruitRequestPayload.TYPE,
                VillagerRecruitRequestPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> VillagerInteractionService.handleRecruitRequest(
                            player,
                            payload.entityId(),
                            payload.action()
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
                                com.jvn.villagerretaliation.debug.HiredDebugPreviewService.setClipboardPreviewEnabled(
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
                VillagerRetaliationConfig.SHOW_VILLAGER_NAME_TAGS.get()
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

    public static void sendName(ServerPlayer player, Entity villager) {
        if (!VillagerPresetNameRegistry.isVillagerForm(villager)) {
            return;
        }
        if (villager.hasCustomName() && villager.getCustomName() != null) {
            String customName = villager.getCustomName().getString().trim();
            if (!customName.isBlank()) {
                trySendToPlayer(player, new VillagerNameSyncPayload(
                        villager.getId(),
                        villager.getUUID(),
                        "",
                        customName
                ));
            }
            return;
        }

        String presetName = VillagerPresetNameRegistry.resolvePresetName(villager);
        if (presetName.isBlank()) {
            return;
        }

        trySendToPlayer(player, new VillagerNameSyncPayload(
                villager.getId(),
                villager.getUUID(),
                "",
                presetName
        ));
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
                profile.tradeLevelSkillAdjustedXpProgress()
        ));
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
