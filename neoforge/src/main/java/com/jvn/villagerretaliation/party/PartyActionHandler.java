package com.jvn.villagerretaliation.party;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.network.OpenPlayerPartyMenuPayload;
import com.jvn.villagerretaliation.network.PartyActionRequestPayload;
import com.jvn.villagerretaliation.network.PartyInvitationSyncPayload;
import com.jvn.villagerretaliation.quest.PartyQuestService;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PartyActionHandler {
    private PartyActionHandler() {
    }

    public static void openPlayerMenu(ServerPlayer player, ServerPlayer target) {
        if (!canInteract(player, target)) {
            return;
        }
        PartyRecord playerParty = PartyService.getPartyForPlayer(player.serverLevel(), player.getUUID()).orElse(null);
        PartyRecord targetParty = PartyService.getPartyForPlayer(player.serverLevel(), target.getUUID()).orElse(null);
        boolean leader = playerParty == null || playerParty.leaderId().equals(player.getUUID());
        boolean canInvite = leader
                && targetParty == null
                && (playerParty == null || playerParty.playerIds().size() < PartyService.MAX_PLAYERS);
        boolean canRemove = playerParty != null
                && playerParty.leaderId().equals(player.getUUID())
                && targetParty != null
                && targetParty.id().equals(playerParty.id())
                && !target.getUUID().equals(playerParty.leaderId());
        send(player, new OpenPlayerPartyMenuPayload(
                target.getUUID(),
                target.getGameProfile().getName(),
                canInvite,
                canRemove));
    }

    public static void sendPendingInvitation(ServerPlayer target) {
        PartyInvitation invitation = PartyService.pendingInvitations(target).stream()
                .reduce((first, second) -> second)
                .orElse(null);
        if (invitation == null) {
            return;
        }
        ServerPlayer inviter = target.getServer().getPlayerList().getPlayer(invitation.inviterId());
        String inviterName = inviter == null
                ? "Player"
                : inviter.getGameProfile().getName();
        send(target, new PartyInvitationSyncPayload(
                invitation.id(),
                inviterName,
                invitation.expiresGameTime()));
    }

    public static void handle(ServerPlayer player, PartyActionRequestPayload payload) {
        if (player == null || payload == null || payload.action() == null) {
            return;
        }
        switch (payload.action()) {
            case SEND_INVITATION -> sendInvitation(player, payload.targetId());
            case ACCEPT_INVITATION -> acceptInvitation(player, payload.invitationId());
            case DECLINE_INVITATION -> declineInvitation(player, payload.invitationId());
            case LEAVE_PARTY -> leaveParty(player);
            case REMOVE_PLAYER -> removePlayer(player, payload.targetId());
            case DISBAND_PARTY -> disband(player);
        }
    }

    private static void sendInvitation(ServerPlayer inviter, UUID targetId) {
        ServerPlayer target = targetId == null ? null : inviter.getServer().getPlayerList().getPlayer(targetId);
        if (target == null || !canInteract(inviter, target)) {
            notice(inviter, "villagerretaliation.party.error.invitation_invalid");
            return;
        }
        PartyService.PartyResult result = PartyService.sendInvitation(inviter, target);
        notice(inviter, result.messageKey());
        if (!result.success()) {
            return;
        }
        PartyInvitation invitation = PartyService.pendingInvitations(target).stream()
                .filter(candidate -> candidate.id().equals(result.invitationId()))
                .findFirst()
                .orElse(null);
        if (invitation != null) {
            send(target, new PartyInvitationSyncPayload(
                    invitation.id(),
                    inviter.getGameProfile().getName(),
                    invitation.expiresGameTime()));
        }
    }

    private static void acceptInvitation(ServerPlayer target, UUID invitationId) {
        PartyInvitation invitation = PartyService.pendingInvitations(target).stream()
                .filter(candidate -> candidate.id().equals(invitationId))
                .findFirst()
                .orElse(null);
        boolean createsParty = invitation != null
                && PartyService.getPartyForPlayer(target.serverLevel(), invitation.inviterId()).isEmpty();
        PartyService.PartyResult result = PartyService.acceptInvitation(target, invitationId);
        notice(target, result.messageKey());
        if (result.success()) {
            if (invitation != null) {
                ServerPlayer inviter = target.getServer().getPlayerList().getPlayer(invitation.inviterId());
                if (inviter != null) {
                    if (createsParty) {
                        notice(inviter, "villagerretaliation.party.created");
                    }
                    notice(inviter, "villagerretaliation.party.invitation_accepted");
                }
            }
            PartySyncService.syncParty(target.getServer(), result.partyId());
        }
    }

    private static void declineInvitation(ServerPlayer target, UUID invitationId) {
        PartyInvitation invitation = PartyService.pendingInvitations(target).stream()
                .filter(candidate -> candidate.id().equals(invitationId))
                .findFirst()
                .orElse(null);
        PartyService.PartyResult result = PartyService.declineInvitation(target, invitationId);
        notice(target, result.messageKey());
        if (result.success() && invitation != null) {
            ServerPlayer inviter = target.getServer().getPlayerList().getPlayer(invitation.inviterId());
            if (inviter != null) {
                notice(inviter, "villagerretaliation.party.invitation_declined");
            }
        }
    }

    private static void leaveParty(ServerPlayer player) {
        PartyRecord party = PartyService.getPartyForPlayer(player.serverLevel(), player.getUUID()).orElse(null);
        PartyService.PartyResult result = PartyService.leaveParty(player);
        notice(player, result.messageKey());
        if (result.success() && party != null) {
            PartyQuestService.detachPlayer(player.serverLevel(), party, player.getUUID());
            PartySyncService.clear(player.getServer(), player.getUUID());
            PartySyncService.syncParty(player.getServer(), party.id());
        }
    }

    private static void removePlayer(ServerPlayer leader, UUID targetId) {
        PartyRecord party = PartyService.getPartyForPlayer(leader.serverLevel(), leader.getUUID()).orElse(null);
        PartyService.PartyResult result = PartyService.removePlayer(leader, targetId);
        notice(leader, result.messageKey());
        if (result.success() && party != null) {
            PartyQuestService.detachPlayer(leader.serverLevel(), party, targetId);
            PartySyncService.clear(leader.getServer(), targetId);
            ServerPlayer removed = leader.getServer().getPlayerList().getPlayer(targetId);
            if (removed != null) {
                notice(removed, "villagerretaliation.party.player_removed");
            }
            PartySyncService.syncParty(leader.getServer(), party.id());
        }
    }

    private static void disband(ServerPlayer leader) {
        PartyRecord party = PartyService.getPartyForPlayer(leader.serverLevel(), leader.getUUID()).orElse(null);
        if (party == null || !party.leaderId().equals(leader.getUUID())) {
            notice(leader, "villagerretaliation.party.error.leader_only");
            return;
        }
        List<UUID> affectedPlayers = List.copyOf(party.playerIds());
        PartyQuestService.detachAll(leader.serverLevel(), party);
        PartyVillagerContractService.disband(leader);
        PartySyncService.clear(leader.getServer(), affectedPlayers);
        for (UUID playerId : affectedPlayers) {
            ServerPlayer affected = leader.getServer().getPlayerList().getPlayer(playerId);
            if (affected != null) {
                notice(affected, "villagerretaliation.party.disbanded");
            }
        }
    }

    private static boolean canInteract(ServerPlayer player, ServerPlayer target) {
        if (player == null || target == null || player.serverLevel() != target.serverLevel()
                || !player.isAlive() || !target.isAlive()) {
            return false;
        }
        double distance = VillagerRetaliationConfig.MAX_DIALOGUE_DISTANCE.get();
        return player.distanceToSqr(target) <= distance * distance;
    }

    private static void notice(ServerPlayer player, String key) {
        if (player != null && key != null && !key.isBlank()) {
            player.sendSystemMessage(Component.translatable(key));
        }
    }

    private static void send(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        try {
            PacketDistributor.sendToPlayer(player, payload);
        } catch (UnsupportedOperationException ignored) {
            // Game tests can use mock connections without negotiated custom payloads.
        }
    }
}
