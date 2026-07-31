package com.jvn.villagerretaliation.party;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.network.PartyActionRequestPayload;
import com.jvn.villagerretaliation.network.PartyInvitationSyncPayload;
import com.jvn.villagerretaliation.quest.PartyQuestService;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PartyActionHandler {
    private PartyActionHandler() {
    }

    public static void sendPendingInvitation(ServerPlayer target) {
        PartyInvitation invitation = latestPendingInvitation(target);
        if (invitation == null) {
            return;
        }
        ServerPlayer inviter = target.getServer().getPlayerList().getPlayer(invitation.inviterId());
        String inviterName = inviter == null
                ? "Player"
                : inviter.getGameProfile().getName();
        sendInvitationNotice(target, inviterName);
    }

    public static void sendInvitationCommand(ServerPlayer inviter, ServerPlayer target) {
        sendInvitation(inviter, target, false);
    }

    public static void createPartyCommand(ServerPlayer leader) {
        PartyService.PartyResult result = PartyService.createParty(leader);
        notice(leader, result.messageKey());
        if (result.success()) {
            PartySyncService.syncParty(leader.getServer(), result.partyId());
        }
    }

    public static void acceptLatestInvitationCommand(ServerPlayer target) {
        PartyInvitation invitation = latestPendingInvitation(target);
        acceptInvitation(target, invitation == null ? null : invitation.id());
    }

    public static void acceptInvitationFromCommand(ServerPlayer target, ServerPlayer inviter) {
        PartyInvitation invitation = latestPendingInvitationFrom(
                target, inviter == null ? null : inviter.getUUID());
        acceptInvitation(target, invitation == null ? null : invitation.id());
    }
    public static void declineLatestInvitationCommand(ServerPlayer target) {
        PartyInvitation invitation = latestPendingInvitation(target);
        declineInvitation(target, invitation == null ? null : invitation.id());
    }

    public static void leavePartyCommand(ServerPlayer player) {
        leaveParty(player);
    }

    public static void removePlayerCommand(ServerPlayer leader, ServerPlayer target) {
        removePlayer(leader, target == null ? null : target.getUUID());
    }

    public static void disbandCommand(ServerPlayer leader) {
        disband(leader);
    }

    public static void allianceCommand(
            ServerPlayer leader,
            ServerPlayer target,
            PartyService.AllianceAction action) {
        handleAlliance(leader, target == null ? null : target.getUUID(), action);
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
            case SET_COMBAT_MODE -> setPolicies(player, payload.combatMode(), null, null);
            case SET_ATTACK_MODE -> setPolicies(player, null, payload.attackMode(), null);
            case SET_SHARED_VILLAGER_INVENTORIES -> setPolicies(player, null, null, payload.enabled());
            case SET_ADMIN_PRIVILEGES -> setAdminPrivileges(player, payload.targetId(), payload.enabled());
            case SET_QUICK_COMMANDS_ENABLED -> PartyQuickCommandService.setParticipation(
                    player, payload.targetId(), payload.enabled());
        }
    }

    private static void handleAlliance(
            ServerPlayer leader,
            UUID targetPlayerId,
            PartyService.AllianceAction action) {
        if (leader == null || action == null) {
            return;
        }
        PartyRecord targetParty = PartyService.getPartyForPlayer(leader.serverLevel(), targetPlayerId).orElse(null);
        PartyService.PartyResult result = switch (action) {
            case REQUEST -> PartyService.requestAlliance(leader, targetPlayerId);
            case ACCEPT -> PartyService.acceptAlliance(leader, targetPlayerId);
            case CANCEL_REQUEST -> PartyService.cancelAllianceRequest(leader, targetPlayerId);
            case END -> PartyService.endAlliance(leader, targetPlayerId);
        };
        notice(leader, result.messageKey());
        if (!result.success() || targetParty == null) {
            return;
        }
        ServerPlayer targetLeader = leader.getServer().getPlayerList().getPlayer(targetParty.leaderId());
        if (targetLeader != null) {
            String targetNotice = switch (action) {
                case REQUEST -> "villagerretaliation.party.alliance_request_received";
                case ACCEPT -> "villagerretaliation.party.alliance_accepted";
                case CANCEL_REQUEST -> "villagerretaliation.party.alliance_request_cancelled_other";
                case END -> "villagerretaliation.party.alliance_ended";
            };
            notice(targetLeader, targetNotice, leader.getGameProfile().getName());
        }
        PartySyncService.syncParty(leader.getServer(), result.partyId());
        PartySyncService.syncParty(leader.getServer(), targetParty.id());
    }

    private static void setPolicies(
            ServerPlayer leader,
            PartyCombatMode combatMode,
            PartyAttackMode attackMode,
            Boolean sharedVillagerInventories) {
        PartyService.PartyResult result = PartyService.setPolicies(
                leader, combatMode, attackMode, sharedVillagerInventories);
        if (!result.success()) {
            notice(leader, result.messageKey());
            return;
        }
        PartySyncService.syncParty(leader.getServer(), result.partyId());
    }

    private static void setAdminPrivileges(ServerPlayer leader, UUID playerId, boolean enabled) {
        PartyService.PartyResult result = PartyService.setAdminPrivileges(leader, playerId, enabled);
        if (!result.success()) {
            notice(leader, result.messageKey());
            return;
        }
        PartySyncService.syncParty(leader.getServer(), result.partyId());
    }
    private static void sendInvitation(ServerPlayer inviter, UUID targetId) {
        ServerPlayer target = targetId == null ? null : inviter.getServer().getPlayerList().getPlayer(targetId);
        if (target == null || !canInteract(inviter, target)) {
            notice(inviter, "villagerretaliation.party.error.invitation_invalid");
            return;
        }
        sendInvitation(inviter, target, true);
    }

    private static void sendInvitation(ServerPlayer inviter, ServerPlayer target, boolean requireInteraction) {
        if (target == null || requireInteraction && !canInteract(inviter, target)) {
            notice(inviter, "villagerretaliation.party.error.invitation_invalid");
            return;
        }
        PartyService.PartyResult result = PartyService.sendInvitation(inviter, target);
        notice(inviter, result.messageKey());
        if (!result.success()) {
            return;
        }
        if (requireInteraction) {
            PartyInvitation invitation = pendingInvitation(target, result.invitationId());
            if (invitation != null) {
                send(target, new PartyInvitationSyncPayload(
                        invitation.id(),
                        inviter.getGameProfile().getName(),
                        invitation.expiresGameTime()));
            }
        } else {
            sendInvitationNotice(target, inviter.getGameProfile().getName());
        }
    }

    private static void acceptInvitation(ServerPlayer target, UUID invitationId) {
        PartyInvitation invitation = pendingInvitation(target, invitationId);
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
            VillagerQuestService.refreshTracker(target);
        }
    }

    private static void declineInvitation(ServerPlayer target, UUID invitationId) {
        PartyInvitation invitation = pendingInvitation(target, invitationId);
        PartyService.PartyResult result = PartyService.declineInvitation(target, invitationId);
        notice(target, result.messageKey());
        if (result.success() && invitation != null) {
            ServerPlayer inviter = target.getServer().getPlayerList().getPlayer(invitation.inviterId());
            if (inviter != null) {
                notice(inviter, "villagerretaliation.party.invitation_declined");
            }
        }
    }

    private static PartyInvitation latestPendingInvitation(ServerPlayer target) {
        return latestPendingInvitationFrom(target, null);
    }

    private static PartyInvitation latestPendingInvitationFrom(ServerPlayer target, UUID inviterId) {
        PartyInvitation latest = null;
        for (PartyInvitation invitation : PartyService.pendingInvitations(target)) {
            if ((inviterId == null || invitation.inviterId().equals(inviterId))
                    && (latest == null || invitation.createdGameTime() >= latest.createdGameTime())) {
                latest = invitation;
            }
        }
        return latest;
    }

    private static PartyInvitation pendingInvitation(ServerPlayer target, UUID invitationId) {
        if (invitationId == null) {
            return null;
        }
        for (PartyInvitation invitation : PartyService.pendingInvitations(target)) {
            if (invitation.id().equals(invitationId)) {
                return invitation;
            }
        }
        return null;
    }

    private static void leaveParty(ServerPlayer player) {
        PartyRecord party = PartyService.getPartyForPlayer(player.serverLevel(), player.getUUID()).orElse(null);
        PartyService.PartyResult result = PartyService.leaveParty(player);
        notice(player, result.messageKey());
        if (result.success() && party != null) {
            PartyQuestService.detachPlayer(player.serverLevel(), party, player.getUUID());
            VillagerQuestService.refreshTracker(player);
            refreshPartyTrackers(player, party);
            PartySyncService.clear(player.getServer(), player.getUUID());
            PartySyncService.syncParty(player.getServer(), party.id());
        }
    }

    private static void removePlayer(ServerPlayer leader, UUID targetId) {
        PartyRecord party = PartyService.getPartyForPlayer(leader.serverLevel(), leader.getUUID()).orElse(null);
        PartyService.PartyResult result = PartyService.removePlayer(leader, targetId);
        if (!result.success()) {
            notice(leader, result.messageKey());
            return;
        }
        if (party != null) {
            PartyQuestService.detachPlayer(leader.serverLevel(), party, targetId);
            PartySyncService.clear(leader.getServer(), targetId);
            ServerPlayer removed = leader.getServer().getPlayerList().getPlayer(targetId);
            String removedName = removed == null ? targetId.toString() : removed.getName().getString();
            if (removed != null) {
                styledNotice(removed, "villagerretaliation.party.player_removed.self");
                VillagerQuestService.refreshTracker(removed);
            }
            for (UUID playerId : party.playerIds()) {
                ServerPlayer member = leader.getServer().getPlayerList().getPlayer(playerId);
                if (member != null) {
                    styledNotice(member, "villagerretaliation.party.player_removed.other", removedName);
                }
            }
            refreshPartyTrackers(leader, party);
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
                VillagerQuestService.refreshTracker(affected);
            }
        }
    }

    private static void refreshPartyTrackers(ServerPlayer source, PartyRecord party) {
        if (source == null || party == null) {
            return;
        }
        for (UUID playerId : party.playerIds()) {
            ServerPlayer member = source.getServer().getPlayerList().getPlayer(playerId);
            if (member != null) {
                VillagerQuestService.refreshTracker(member);
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

    private static void notice(ServerPlayer player, String key, Object... args) {
        if (player != null && key != null && !key.isBlank()) {
            player.sendSystemMessage(Component.translatable(key, args));
        }
    }

    private static void styledNotice(ServerPlayer player, String key, Object... args) {
        if (player != null && key != null && !key.isBlank()) {
            player.sendSystemMessage(Component.translatable(key, args)
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }

    private static void sendInvitationNotice(ServerPlayer target, String inviterName) {
        if (target == null || inviterName == null || inviterName.isBlank()) return;
        String command = "/villagerretaliation party accept " + inviterName;
        Component accept = Component.translatable("villagerretaliation.party.invitation.accept_chat")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withUnderlined(true)
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("villagerretaliation.party.invitation.accept_chat.tooltip")))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
        target.sendSystemMessage(Component
                .translatable("villagerretaliation.party.invitation.prompt", inviterName)
                .append(" ")
                .append(accept));
    }
    private static void send(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        try {
            PacketDistributor.sendToPlayer(player, payload);
        } catch (UnsupportedOperationException ignored) {
            // Game tests can use mock connections without negotiated custom payloads.
        }
    }
}
