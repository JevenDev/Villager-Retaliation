package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.notification.ResolvedVillagerNotification;
import com.jvn.villagerretaliation.profile.VillagerProfile;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import com.jvn.toucanlib.neoforge.network.ToucanNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class VillagerReputationNetworking {
    private static final String PROTOCOL_VERSION = "21";

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
                        ToucanNetwork.withServerPlayer(context, player -> com.jvn.villagerretaliation.item.HiredStorageClipboardItem.changeHeldClipboardMode(
                            player,
                            payload.delta()
                    )))
        );
        network.playToServer(
                ClipboardPreviewTogglePayload.TYPE,
                ClipboardPreviewTogglePayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> com.jvn.villagerretaliation.debug.HiredDebugPreviewService.setEnabled(
                                player,
                                payload.enabled(),
                                com.jvn.villagerretaliation.debug.HiredDebugPreviewService.DEFAULT_RADIUS
                        )))
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
        PacketDistributor.sendToPlayer(player, new VillagerReputationSyncPayload(
                villager.getId(),
                villager.getUUID(),
                reputation,
                VillagerReputationLevel.fromReputation(reputation)
        ));
    }

    public static void sendServerConfig(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ServerConfigSyncPayload(
                VillagerRetaliationConfig.SHOW_VILLAGER_NAME_TAGS.get()
        ));
    }

    public static void sendFearedPulse(AbstractVillager villager, int ticks) {
        PacketDistributor.sendToPlayersTrackingEntity(villager, new FearedVillagerPulsePayload(villager.getId(), ticks));
    }

    public static void sendName(ServerPlayer player, AbstractVillager villager) {
        if (villager.hasCustomName() && villager.getCustomName() != null) {
            String customName = villager.getCustomName().getString().trim();
            if (!customName.isBlank()) {
                PacketDistributor.sendToPlayer(player, new VillagerNameSyncPayload(
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

        PacketDistributor.sendToPlayer(player, new VillagerNameSyncPayload(
                villager.getId(),
                villager.getUUID(),
                "",
                presetName
        ));
    }

    public static void sendTierNotice(ServerPlayer player, String text) {
        PacketDistributor.sendToPlayer(player, new VillagerReputationTierNoticePayload(text));
    }

    public static void sendNotice(ServerPlayer player, String text, VillagerReputationNoticeKind kind) {
        PacketDistributor.sendToPlayer(player, new VillagerReputationTierNoticePayload(text, kind));
    }

    public static void sendNotice(ServerPlayer player, ResolvedVillagerNotification notification) {
        PacketDistributor.sendToPlayer(player, new VillagerReputationTierNoticePayload(
                notification.text(),
                notification.noticeKind(),
                notification.textColor(),
                notification.chatColor()
        ));
    }

    public static void sendProfile(ServerPlayer player, AbstractVillager villager, VillagerProfile profile) {
        PacketDistributor.sendToPlayer(player, VillagerProfileSyncPayload.create(
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
