package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.network.VillagerConversationEndedPayload;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillagerConversationService {
    private VillagerConversationService() { }

    public static void clearRuntimeState() { InteractionSessions.clear(VrInteractionSessionPolicy.INSTANCE); }

    public static boolean start(ServerPlayer player, Villager villager) {
        return start(player, villager, InteractionSessions.StartMode.NORMAL);
    }

    public static boolean startForced(ServerPlayer player, Villager villager) {
        return start(player, villager, InteractionSessions.StartMode.FORCED);
    }

    /** Starts a forced scene whose caller owns disposition/hostility authorization. */
    public static boolean startForcedIgnoringDisposition(ServerPlayer player, Villager villager) {
        return start(player, villager, InteractionSessions.StartMode.AUTHORIZED_FORCED);
    }

    private static boolean start(ServerPlayer player, Villager villager, InteractionSessions.StartMode mode) {
        return InteractionSessions.start(player, villager, VrInteractionSessionPolicy.INSTANCE, mode).isPresent();
    }

    public static UUID sessionId(ServerPlayer player, Villager villager) { return owns(player) ? InteractionSessions.sessionId(player, villager) : null; }
    public static boolean validate(ServerPlayer player, Villager villager) { return owns(player) && InteractionSessions.validate(player, villager); }

    /** Records client activity for the session identified by the supplied villager entity id. */
    public static void recordActivity(ServerPlayer player, int villagerEntityId) { if (owns(player)) InteractionSessions.recordActivity(player, villagerEntityId); }

    public static boolean isForced(ServerPlayer player, Villager villager) { return owns(player) && InteractionSessions.isForced(player, villager); }
    public static void endForPlayer(ServerPlayer player, boolean notifyClient) { endForPlayer(player, notifyClient, false); }
    public static void endForPlayer(ServerPlayer player, boolean notifyClient, boolean finishExchanges) {
        if (owns(player)) InteractionSessions.endForPlayer(player, notifyClient, finishExchanges);
    }
    public static void endForVillager(Villager villager, boolean notifyClient) {
        if (InteractionSessions.ownedBy(villager, VrInteractionSessionPolicy.INSTANCE)) InteractionSessions.endForActor(villager, notifyClient);
    }
    public static boolean isConversing(Villager villager) { return InteractionSessions.ownedBy(villager, VrInteractionSessionPolicy.INSTANCE); }
    public static boolean isConversing(ServerPlayer player) { return owns(player); }
    public static void tickVillager(Villager villager) {
        if (InteractionSessions.ownedBy(villager, VrInteractionSessionPolicy.INSTANCE)) InteractionSessions.tick(villager);
    }

    public static void holdSharedForcedParticipant(Villager villager, ServerPlayer player) {
        VrInteractionSessionPolicy.holdSharedForcedParticipant(villager, player);
    }

    public static void endForEntityLeaving(Entity entity, boolean notifyClient) { InteractionSessions.endForEntityLeaving(entity, notifyClient); }

    private static boolean owns(ServerPlayer player) { return InteractionSessions.ownedBy(player, VrInteractionSessionPolicy.INSTANCE); }

    static void notifyEnded(ServerPlayer player, InteractionSession session, boolean finishExchanges) {
        try {
            PacketDistributor.sendToPlayer(player, new VillagerConversationEndedPayload(session.entityId(), "", finishExchanges));
        } catch (UnsupportedOperationException ignored) {
            // GameTest mock players and unnegotiated connections cannot accept custom client payloads.
        }
    }
}
