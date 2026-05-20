package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import com.jvn.toucanlib.neoforge.network.ToucanNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class VillagerReputationNetworking {
    private static final String PROTOCOL_VERSION = "5";

    private VillagerReputationNetworking() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        ToucanNetwork network = ToucanNetwork.create(VillagerRetaliation.MOD_ID, PROTOCOL_VERSION, event);
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
                VillagerDialogueRequestPayload.TYPE,
                VillagerDialogueRequestPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> VillagerInteractionService.handleDialogueRequest(
                            player,
                            payload.entityId(),
                            payload.requestType()
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
                VillagerInventoryRequestPayload.TYPE,
                VillagerInventoryRequestPayload.STREAM_CODEC,
                (payload, context) -> ToucanNetwork.enqueue(context, () ->
                        ToucanNetwork.withServerPlayer(context, player -> VillagerInteractionService.handleInventoryRequest(
                            player,
                            payload.entityId()
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

    public static void sendFearedPulse(AbstractVillager villager, int ticks) {
        PacketDistributor.sendToPlayersTrackingEntity(villager, new FearedVillagerPulsePayload(villager.getId(), ticks));
    }

    public static void sendName(ServerPlayer player, AbstractVillager villager) {
        if (villager.hasCustomName()) {
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

    public static void sendWorldTextIndicator(AbstractVillager villager, String text, VillagerWorldTextIndicatorKind kind) {
        PacketDistributor.sendToPlayersTrackingEntity(
                villager,
                new VillagerWorldTextIndicatorPayload(villager.getId(), text, kind)
        );
    }
}
