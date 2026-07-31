package com.jvn.villagerretaliation.party;

import com.jvn.villagerretaliation.mount.VillagerMountAssignmentService;
import com.jvn.villagerretaliation.util.VillagerEntityResolver;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;

public final class PartyService {
    public static final int MAX_PLAYERS = 4;
    public static final int MAX_VILLAGERS = 4;
    public static final long INVITATION_LIFETIME_TICKS = 20L * 60L;

    private PartyService() {
    }

    public static Optional<PartyRecord> getParty(ServerLevel level, UUID partyId) {
        return level == null ? Optional.empty() : partyData(level).party(partyId);
    }

    public static Optional<PartyRecord> getPartyForPlayer(ServerLevel level, UUID playerId) {
        return level == null ? Optional.empty() : partyData(level).partyForPlayer(playerId);
    }

    public static Optional<PartyRecord> getPartyForVillager(ServerLevel level, UUID villagerId) {
        return level == null ? Optional.empty() : partyData(level).partyForVillager(villagerId);
    }

    public static Optional<PartyRecord> getPartyForEntity(Entity entity) {
        if (entity == null || !(entity.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        PartySavedData data = partyData(level);
        return data.party(partyIdForEntity(data, entity));
    }

    public static boolean areInSameParty(Entity first, Entity second) {
        if (first == null
                || second == null
                || !(first.level() instanceof ServerLevel firstLevel)
                || !(second.level() instanceof ServerLevel)
                || first.level().getServer() != second.level().getServer()) {
            return false;
        }
        PartySavedData data = partyData(firstLevel);
        UUID firstPartyId = partyIdForEntity(data, first);
        return firstPartyId != null && firstPartyId.equals(partyIdForEntity(data, second));
    }

    public static boolean areInSameOrAlliedParty(Entity first, Entity second) {
        if (first == null
                || second == null
                || !(first.level() instanceof ServerLevel firstLevel)
                || !(second.level() instanceof ServerLevel)
                || first.level().getServer() != second.level().getServer()) {
            return false;
        }
        PartySavedData data = partyData(firstLevel);
        UUID firstPartyId = partyIdForEntity(data, first);
        UUID secondPartyId = partyIdForEntity(data, second);
        if (firstPartyId == null || secondPartyId == null) {
            return false;
        }
        if (firstPartyId.equals(secondPartyId)) {
            return true;
        }
        PartyRecord firstParty = data.party(firstPartyId).orElse(null);
        PartyRecord secondParty = data.party(secondPartyId).orElse(null);
        return areSameOrAllied(firstParty, secondParty);
    }

    public static boolean areSameOrAllied(PartyRecord first, PartyRecord second) {
        return first != null
                && second != null
                && (first.id().equals(second.id())
                || first.isAlliedWith(second.id()) && second.isAlliedWith(first.id()));
    }

    public static boolean arePlayerAndVillagerInSameParty(ServerLevel level, UUID playerId, UUID villagerId) {
        if (level == null || playerId == null || villagerId == null) {
            return false;
        }
        PartySavedData data = partyData(level);
        UUID playerPartyId = data.partyIdForPlayer(playerId);
        return playerPartyId != null && playerPartyId.equals(data.partyIdForVillager(villagerId));
    }

    /**
     * Returns whether recruited villagers may rally against a party member's combat target.
     * Village residents and their iron golems stay protected even when a party member starts
     * the fight, so recruitment cannot be used to turn a village against itself.
     */
    public static boolean canRecruitedVillagersAssistAgainst(LivingEntity target) {
        return target != null
                && (!(target instanceof AbstractVillager) || target instanceof Villager)
                && !(target instanceof IronGolem);
    }

    public static PartyResult setPolicies(
            ServerPlayer leader,
            PartyCombatMode combatMode,
            PartyAttackMode attackMode,
            Boolean sharedVillagerInventories) {
        if (leader == null) {
            return PartyResult.failure("villagerretaliation.party.error.not_in_party");
        }
        PartySavedData data = partyData(leader.serverLevel());
        PartyRecord party = data.partyForPlayer(leader.getUUID()).orElse(null);
        if (party == null || !party.hasAdminPrivileges(leader.getUUID())) {
            return PartyResult.failure("villagerretaliation.party.error.admin_privileges_required");
        }
        if (combatMode != null) {
            party.setCombatMode(combatMode);
            clearPartyCombatTargets(leader.getServer(), party);
        }
        if (attackMode != null) {
            party.setAttackMode(attackMode);
            clearPartyCombatTargets(leader.getServer(), party);
        }
        if (sharedVillagerInventories != null) {
            party.setSharedVillagerInventories(sharedVillagerInventories);
        }
        data.changed();
        return PartyResult.success("villagerretaliation.party.settings_updated", party.id(), null);
    }

    public static PartyResult setAdminPrivileges(ServerPlayer leader, UUID playerId, boolean enabled) {
        if (leader == null || playerId == null) {
            return PartyResult.failure("villagerretaliation.party.error.not_in_party");
        }
        PartySavedData data = partyData(leader.serverLevel());
        PartyRecord party = data.partyForPlayer(leader.getUUID()).orElse(null);
        if (party == null || !party.leaderId().equals(leader.getUUID())) {
            return PartyResult.failure("villagerretaliation.party.error.leader_only");
        }
        if (!party.playerIds().contains(playerId) || party.leaderId().equals(playerId)) {
            return PartyResult.failure("villagerretaliation.party.error.player_not_in_party");
        }
        if (party.hasAdminPrivileges(playerId) == enabled) {
            return PartyResult.success("villagerretaliation.party.settings_updated", party.id(), null);
        }
        party.setAdminPrivileges(playerId, enabled);
        data.changed();
        return PartyResult.success("villagerretaliation.party.settings_updated", party.id(), null);
    }
    public static PartyResult createParty(ServerPlayer leader) {
        if (leader == null) {
            return PartyResult.failure("villagerretaliation.party.error.not_in_party");
        }
        PartySavedData data = partyData(leader.serverLevel());
        if (data.partyForPlayer(leader.getUUID()).isPresent()) {
            return PartyResult.failure("villagerretaliation.party.error.already_in_party");
        }
        PartyRecord party = data.createParty(leader.getUUID(), serverGameTime(leader.getServer()));
        return PartyResult.success("villagerretaliation.party.created", party.id(), null);
    }

    public static PartyResult requestAlliance(ServerPlayer leader, UUID targetPlayerId) {
        PartyRelationship relationship = relationship(leader, targetPlayerId);
        if (!relationship.validLeader()) {
            return relationship.failure();
        }
        if (relationship.party().isAlliedWith(relationship.targetParty().id())) {
            return PartyResult.failure("villagerretaliation.party.error.already_allied");
        }
        if (relationship.targetParty().hasRequestedAllianceWith(relationship.party().id())) {
            return PartyResult.failure("villagerretaliation.party.error.alliance_request_pending_acceptance");
        }
        if (!relationship.party().addAllianceRequest(relationship.targetParty().id())) {
            return PartyResult.failure("villagerretaliation.party.error.alliance_request_exists");
        }
        relationship.data().changed();
        return PartyResult.success("villagerretaliation.party.alliance_requested", relationship.party().id(), null);
    }

    public static PartyResult acceptAlliance(ServerPlayer leader, UUID targetPlayerId) {
        PartyRelationship relationship = relationship(leader, targetPlayerId);
        if (!relationship.validLeader()) {
            return relationship.failure();
        }
        if (!relationship.targetParty().hasRequestedAllianceWith(relationship.party().id())) {
            return PartyResult.failure("villagerretaliation.party.error.alliance_request_missing");
        }
        relationship.party().addAlliance(relationship.targetParty().id());
        relationship.targetParty().addAlliance(relationship.party().id());
        relationship.party().removeAllianceRequest(relationship.targetParty().id());
        relationship.targetParty().removeAllianceRequest(relationship.party().id());
        relationship.data().changed();
        clearPartyCombatTargets(leader.getServer(), relationship.party());
        clearPartyCombatTargets(leader.getServer(), relationship.targetParty());
        return PartyResult.success("villagerretaliation.party.alliance_accepted", relationship.party().id(), null);
    }

    public static PartyResult cancelAllianceRequest(ServerPlayer leader, UUID targetPlayerId) {
        PartyRelationship relationship = relationship(leader, targetPlayerId);
        if (!relationship.validLeader()) {
            return relationship.failure();
        }
        if (!relationship.party().removeAllianceRequest(relationship.targetParty().id())) {
            return PartyResult.failure("villagerretaliation.party.error.alliance_request_missing");
        }
        relationship.data().changed();
        return PartyResult.success("villagerretaliation.party.alliance_request_cancelled", relationship.party().id(), null);
    }

    public static PartyResult endAlliance(ServerPlayer leader, UUID targetPlayerId) {
        PartyRelationship relationship = relationship(leader, targetPlayerId);
        if (!relationship.validLeader()) {
            return relationship.failure();
        }
        boolean removed = relationship.party().removeAlliance(relationship.targetParty().id());
        removed |= relationship.targetParty().removeAlliance(relationship.party().id());
        if (!removed) {
            return PartyResult.failure("villagerretaliation.party.error.not_allied");
        }
        relationship.party().removeAllianceRequest(relationship.targetParty().id());
        relationship.targetParty().removeAllianceRequest(relationship.party().id());
        relationship.data().changed();
        return PartyResult.success("villagerretaliation.party.alliance_ended", relationship.party().id(), null);
    }

    private static PartyRelationship relationship(ServerPlayer leader, UUID targetPlayerId) {
        if (leader == null || targetPlayerId == null) {
            return PartyRelationship.invalid("villagerretaliation.party.error.alliance_invalid");
        }
        PartySavedData data = partyData(leader.serverLevel());
        PartyRecord party = data.partyForPlayer(leader.getUUID()).orElse(null);
        if (party == null) {
            return PartyRelationship.invalid("villagerretaliation.party.error.not_in_party");
        }
        if (!party.leaderId().equals(leader.getUUID())) {
            return PartyRelationship.invalid("villagerretaliation.party.error.leader_only");
        }
        PartyRecord targetParty = data.partyForPlayer(targetPlayerId).orElse(null);
        if (targetParty == null || party.id().equals(targetParty.id())) {
            return PartyRelationship.invalid("villagerretaliation.party.error.alliance_invalid");
        }
        return new PartyRelationship(data, party, targetParty, null);
    }

    private static void clearPartyCombatTargets(MinecraftServer server, PartyRecord party) {
        for (PartyVillagerRecord record : party.villagers()) {
            Villager villager = VillagerEntityResolver.loaded(server, record.villagerId());
            if (villager != null) {
                com.jvn.villagerretaliation.combat.VillagerRetaliationHandler.clearCustomTarget(villager);
            }
        }
    }

    public static boolean isRecruitedPartyVillager(ServerLevel level, UUID villagerId) {
        return level != null && partyData(level).partyIdForVillager(villagerId) != null;
    }

    private static UUID partyIdForEntity(PartySavedData data, Entity entity) {
        if (entity instanceof ServerPlayer) {
            return data.partyIdForPlayer(entity.getUUID());
        }
        return entity instanceof Villager ? data.partyIdForVillager(entity.getUUID()) : null;
    }


    public static PartyResult sendInvitation(ServerPlayer inviter, ServerPlayer target) {
        if (inviter == null
                || target == null
                || inviter.getServer() != target.getServer()
                || inviter.getUUID().equals(target.getUUID())) {
            return PartyResult.failure("villagerretaliation.party.error.invitation_invalid");
        }
        ServerLevel level = inviter.serverLevel();
        PartySavedData data = partyData(level);
        Optional<PartyRecord> inviterParty = data.partyForPlayer(inviter.getUUID());
        if (inviterParty.isPresent() && !inviterParty.get().leaderId().equals(inviter.getUUID())) {
            return PartyResult.failure("villagerretaliation.party.error.leader_only");
        }
        if (inviterParty.isPresent() && inviterParty.get().playerIds().size() >= MAX_PLAYERS) {
            return PartyResult.failure("villagerretaliation.party.error.player_limit");
        }
        if (data.partyForPlayer(target.getUUID()).isPresent()) {
            return PartyResult.failure("villagerretaliation.party.error.player_already_in_party");
        }
        long now = serverGameTime(inviter.getServer());
        PartyInvitation invitation = new PartyInvitation(
                UUID.randomUUID(),
                inviter.getUUID(),
                target.getUUID(),
                inviterParty.map(PartyRecord::id).orElse(null),
                now,
                now + INVITATION_LIFETIME_TICKS
        );
        data.putInvitation(invitation);
        return PartyResult.success("villagerretaliation.party.invitation_sent", inviterParty.map(PartyRecord::id).orElse(null), invitation.id());
    }

    public static PartyResult acceptInvitation(ServerPlayer target, UUID invitationId) {
        if (target == null || invitationId == null) {
            return PartyResult.failure("villagerretaliation.party.error.invitation_invalid");
        }
        PartySavedData data = partyData(target.serverLevel());
        PartyInvitation invitation = invitationForTarget(data, target.getUUID(), invitationId);
        long now = serverGameTime(target.getServer());
        if (invitation == null) {
            return PartyResult.failure("villagerretaliation.party.error.invitation_invalid");
        }
        if (invitation.isExpired(now)) {
            data.removeInvitation(invitationId);
            return PartyResult.failure("villagerretaliation.party.invitation_expired");
        }
        if (data.partyForPlayer(target.getUUID()).isPresent()) {
            data.removeInvitation(invitationId);
            return PartyResult.failure("villagerretaliation.party.error.already_in_party");
        }

        PartyRecord party = data.partyForPlayer(invitation.inviterId()).orElse(null);
        if (party != null && !party.leaderId().equals(invitation.inviterId())) {
            data.removeInvitation(invitationId);
            return PartyResult.failure("villagerretaliation.party.error.invitation_invalid");
        }
        if (invitation.expectedPartyId() != null
                && (party == null || !party.id().equals(invitation.expectedPartyId()))) {
            data.removeInvitation(invitationId);
            return PartyResult.failure("villagerretaliation.party.error.invitation_invalid");
        }
        if (party != null && party.playerIds().size() >= MAX_PLAYERS) {
            data.removeInvitation(invitationId);
            return PartyResult.failure("villagerretaliation.party.error.player_limit");
        }

        boolean created = false;
        if (party == null) {
            party = data.createParty(invitation.inviterId(), now);
            created = true;
        }
        if (!data.addPlayer(party, target.getUUID())) {
            if (created) {
                data.removeParty(party.id());
            }
            data.removeInvitation(invitationId);
            return PartyResult.failure("villagerretaliation.party.error.invitation_invalid");
        }
        data.removeInvitation(invitationId);
        return PartyResult.success("villagerretaliation.party.invitation_accepted", party.id(), invitationId);
    }

    public static PartyResult declineInvitation(ServerPlayer target, UUID invitationId) {
        if (target == null || invitationId == null) {
            return PartyResult.failure("villagerretaliation.party.error.invitation_invalid");
        }
        PartySavedData data = partyData(target.serverLevel());
        PartyInvitation invitation = invitationForTarget(data, target.getUUID(), invitationId);
        if (invitation == null) {
            return PartyResult.failure("villagerretaliation.party.error.invitation_invalid");
        }
        if (invitation.isExpired(serverGameTime(target.getServer()))) {
            data.removeInvitation(invitationId);
            return PartyResult.failure("villagerretaliation.party.invitation_expired");
        }
        data.removeInvitation(invitationId);
        return PartyResult.success("villagerretaliation.party.invitation_declined", invitation.expectedPartyId(), invitationId);
    }

    private static PartyInvitation invitationForTarget(
            PartySavedData data,
            UUID targetId,
            UUID invitationId) {
        PartyInvitation invitation = data.invitation(invitationId).orElse(null);
        return invitation != null && invitation.targetId().equals(targetId) ? invitation : null;
    }

    public static PartyResult leaveParty(ServerPlayer player) {
        if (player == null) {
            return PartyResult.failure("villagerretaliation.party.error.not_in_party");
        }
        PartySavedData data = partyData(player.serverLevel());
        PartyRecord party = data.partyForPlayer(player.getUUID()).orElse(null);
        if (party == null) {
            return PartyResult.failure("villagerretaliation.party.error.not_in_party");
        }
        if (party.leaderId().equals(player.getUUID())) {
            return PartyResult.failure("villagerretaliation.party.error.leader_must_disband");
        }
        return data.removePlayer(party, player.getUUID())
                ? PartyResult.success("villagerretaliation.party.player_left", party.id(), null)
                : PartyResult.failure("villagerretaliation.party.error.not_in_party");
    }

    public static PartyResult removePlayer(ServerPlayer leader, UUID playerId) {
        if (leader == null || playerId == null) {
            return PartyResult.failure("villagerretaliation.party.error.not_in_party");
        }
        PartySavedData data = partyData(leader.serverLevel());
        PartyRecord party = data.partyForPlayer(leader.getUUID()).orElse(null);
        if (party == null || !party.leaderId().equals(leader.getUUID())) {
            return PartyResult.failure("villagerretaliation.party.error.leader_only");
        }
        if (party.leaderId().equals(playerId)) {
            return PartyResult.failure("villagerretaliation.party.error.leader_must_disband");
        }
        return data.removePlayer(party, playerId)
                ? PartyResult.success("villagerretaliation.party.player_removed", party.id(), null)
                : PartyResult.failure("villagerretaliation.party.error.player_not_in_party");
    }

    static PartyResult addVillager(
            ServerLevel level,
            UUID leaderId,
            PartyVillagerRecord villager,
            long gameTime) {
        PartySavedData data = partyData(level);
        if (data.partyForVillager(villager.villagerId()).isPresent()) {
            return PartyResult.failure("villagerretaliation.party.error.villager_already_in_party");
        }
        PartyRecord party = data.partyForPlayer(leaderId).orElse(null);
        boolean created = false;
        if (party != null && !party.leaderId().equals(leaderId)) {
            return PartyResult.failure("villagerretaliation.party.error.leader_only");
        }
        if (party != null && party.villagers().size() >= MAX_VILLAGERS) {
            return PartyResult.failure("villagerretaliation.party.error.villager_limit");
        }
        if (party == null) {
            party = data.createParty(leaderId, gameTime);
            created = true;
        }
        if (!data.addVillager(party, villager)) {
            if (created) {
                data.removeParty(party.id());
            }
            return PartyResult.failure("villagerretaliation.party.error.villager_limit");
        }
        return PartyResult.success("villagerretaliation.party.villager_recruited", party.id(), null);
    }

    static PartyVillagerRecord removeVillager(ServerLevel level, UUID villagerId) {
        PartySavedData data = partyData(level);
        PartyRecord party = data.partyForVillager(villagerId).orElse(null);
        if (party == null) {
            return null;
        }
        VillagerMountAssignmentService.clearAssignment(level, villagerId);
        return data.removeVillager(party, villagerId);
    }

    public static void markChanged(ServerLevel level) {
        if (level != null) {
            partyData(level).changed();
        }
    }

    static PartyRecord deleteParty(ServerLevel level, UUID partyId) {
        PartyRecord removed = partyData(level).removeParty(partyId);
        if (removed != null) {
            removed.villagers().forEach(villager ->
                    VillagerMountAssignmentService.clearAssignment(level, villager.villagerId()));
        }
        return removed;
    }

    public static List<PartyInvitation> pendingInvitations(ServerPlayer player) {
        return player == null
                ? List.of()
                : partyData(player.serverLevel()).invitationsFor(player.getUUID(), serverGameTime(player.getServer()));
    }

    public static int pruneExpiredInvitations(MinecraftServer server) {
        return server == null
                ? 0
                : partyData(server.overworld()).pruneExpiredInvitations(serverGameTime(server));
    }

    private static PartySavedData partyData(ServerLevel level) {
        return PartySavedData.get(level);
    }

    private static long serverGameTime(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    public record PartyResult(boolean success, String messageKey, UUID partyId, UUID invitationId) {
        static PartyResult success(String messageKey, UUID partyId, UUID invitationId) {
            return new PartyResult(true, messageKey, partyId, invitationId);
        }

        static PartyResult failure(String messageKey) {
            return new PartyResult(false, messageKey, null, null);
        }
    }

    public enum AllianceAction {
        REQUEST,
        ACCEPT,
        CANCEL_REQUEST,
        END
    }

    private record PartyRelationship(
            PartySavedData data,
            PartyRecord party,
            PartyRecord targetParty,
            String errorKey) {
        static PartyRelationship invalid(String errorKey) {
            return new PartyRelationship(null, null, null, errorKey);
        }

        boolean validLeader() {
            return this.errorKey == null;
        }

        PartyResult failure() {
            return PartyResult.failure(this.errorKey);
        }
    }
}
