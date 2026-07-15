package com.jvn.villagerretaliation.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public final class PartySavedData extends SavedData {
    private static final String DATA_NAME = "villagerretaliation_parties";
    private static final String TAG_VERSION = "Version";
    private static final String TAG_PARTIES = "Parties";
    private static final String TAG_INVITATIONS = "Invitations";
    private static final int CURRENT_VERSION = 8;

    private final Map<UUID, PartyRecord> partiesById = new LinkedHashMap<>();
    private final Map<UUID, UUID> partyByPlayer = new HashMap<>();
    private final Map<UUID, UUID> partyByVillager = new HashMap<>();
    private final Map<UUID, PartyInvitation> invitationsById = new LinkedHashMap<>();

    public static PartySavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PartySavedData::new, PartySavedData::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static PartySavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        PartySavedData data = new PartySavedData();
        ListTag partiesTag = tag.getList(TAG_PARTIES, Tag.TAG_COMPOUND);
        for (Tag rawParty : partiesTag) {
            if (!(rawParty instanceof CompoundTag partyTag)) {
                continue;
            }
            PartyRecord party = PartyRecord.load(partyTag);
            if (party != null && !data.partiesById.containsKey(party.id())) {
                data.partiesById.put(party.id(), party);
            }
        }
        data.rebuildIndexesAndPruneDuplicates();

        ListTag invitationsTag = tag.getList(TAG_INVITATIONS, Tag.TAG_COMPOUND);
        for (Tag rawInvitation : invitationsTag) {
            if (!(rawInvitation instanceof CompoundTag invitationTag)) {
                continue;
            }
            PartyInvitation invitation = PartyInvitation.load(invitationTag);
            if (invitation != null) {
                data.invitationsById.putIfAbsent(invitation.id(), invitation);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt(TAG_VERSION, CURRENT_VERSION);
        ListTag partiesTag = new ListTag();
        for (PartyRecord party : this.partiesById.values()) {
            partiesTag.add(party.save());
        }
        tag.put(TAG_PARTIES, partiesTag);
        ListTag invitationsTag = new ListTag();
        for (PartyInvitation invitation : this.invitationsById.values()) {
            invitationsTag.add(invitation.save());
        }
        tag.put(TAG_INVITATIONS, invitationsTag);
        return tag;
    }

    public Optional<PartyRecord> party(UUID partyId) {
        return Optional.ofNullable(this.partiesById.get(partyId));
    }

    public Optional<PartyRecord> partyForPlayer(UUID playerId) {
        return party(this.partyByPlayer.get(playerId));
    }

    public Optional<PartyRecord> partyForVillager(UUID villagerId) {
        return party(this.partyByVillager.get(villagerId));
    }

    public Optional<PartyInvitation> invitation(UUID invitationId) {
        return Optional.ofNullable(this.invitationsById.get(invitationId));
    }

    public List<PartyRecord> parties() {
        return List.copyOf(this.partiesById.values());
    }

    public List<PartyInvitation> invitationsFor(UUID targetId, long gameTime) {
        pruneExpiredInvitations(gameTime);
        return this.invitationsById.values().stream()
                .filter(invitation -> invitation.targetId().equals(targetId))
                .toList();
    }

    PartyRecord createParty(UUID leaderId, long gameTime) {
        PartyRecord party = new PartyRecord(UUID.randomUUID(), leaderId, gameTime);
        this.partiesById.put(party.id(), party);
        this.partyByPlayer.put(leaderId, party.id());
        setDirty();
        return party;
    }

    boolean addPlayer(PartyRecord party, UUID playerId) {
        if (party == null || this.partyByPlayer.containsKey(playerId) || !party.addPlayer(playerId)) {
            return false;
        }
        this.partyByPlayer.put(playerId, party.id());
        setDirty();
        return true;
    }

    boolean removePlayer(PartyRecord party, UUID playerId) {
        if (party == null || !party.removePlayer(playerId)) {
            return false;
        }
        this.partyByPlayer.remove(playerId, party.id());
        setDirty();
        return true;
    }

    boolean addVillager(PartyRecord party, PartyVillagerRecord villager) {
        if (party == null || this.partyByVillager.containsKey(villager.villagerId()) || !party.addVillager(villager)) {
            return false;
        }
        this.partyByVillager.put(villager.villagerId(), party.id());
        setDirty();
        return true;
    }

    PartyVillagerRecord removeVillager(PartyRecord party, UUID villagerId) {
        if (party == null) {
            return null;
        }
        PartyVillagerRecord removed = party.removeVillager(villagerId);
        if (removed != null) {
            this.partyByVillager.remove(villagerId, party.id());
            setDirty();
        }
        return removed;
    }

    void changed() {
        setDirty();
    }

    void putInvitation(PartyInvitation invitation) {
        this.invitationsById.entrySet().removeIf(entry ->
                entry.getValue().inviterId().equals(invitation.inviterId())
                        && entry.getValue().targetId().equals(invitation.targetId()));
        this.invitationsById.put(invitation.id(), invitation);
        setDirty();
    }

    PartyInvitation removeInvitation(UUID invitationId) {
        PartyInvitation removed = this.invitationsById.remove(invitationId);
        if (removed != null) {
            setDirty();
        }
        return removed;
    }

    void cancelInvitationsForParty(PartyRecord party) {
        if (party == null) {
            return;
        }
        boolean removed = this.invitationsById.entrySet().removeIf(entry -> {
            PartyInvitation invitation = entry.getValue();
            return party.id().equals(invitation.expectedPartyId()) || party.leaderId().equals(invitation.inviterId());
        });
        if (removed) {
            setDirty();
        }
    }

    PartyRecord removeParty(UUID partyId) {
        PartyRecord removed = this.partiesById.remove(partyId);
        if (removed == null) {
            return null;
        }
        for (UUID playerId : removed.playerIds()) {
            this.partyByPlayer.remove(playerId, partyId);
        }
        for (PartyVillagerRecord villager : removed.villagers()) {
            this.partyByVillager.remove(villager.villagerId(), partyId);
        }
        for (PartyRecord party : this.partiesById.values()) {
            party.removeAlliance(partyId);
            party.removeAllianceRequest(partyId);
        }
        cancelInvitationsForParty(removed);
        setDirty();
        return removed;
    }

    public int partyCount() {
        return this.partiesById.size();
    }

    public int pruneExpiredInvitations(long gameTime) {
        int before = this.invitationsById.size();
        this.invitationsById.entrySet().removeIf(entry -> entry.getValue().isExpired(gameTime));
        int removed = before - this.invitationsById.size();
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    private void rebuildIndexesAndPruneDuplicates() {
        this.partyByPlayer.clear();
        this.partyByVillager.clear();
        Set<UUID> claimedPlayers = new HashSet<>();
        Set<UUID> claimedVillagers = new HashSet<>();
        List<UUID> invalidParties = new ArrayList<>();
        for (PartyRecord party : this.partiesById.values()) {
            if (!claimedPlayers.add(party.leaderId())) {
                invalidParties.add(party.id());
                continue;
            }
            party.removeDuplicatePlayers(claimedPlayers);
            party.removeDuplicateVillagers(claimedVillagers);
            for (UUID playerId : party.playerIds()) {
                this.partyByPlayer.put(playerId, party.id());
            }
            for (PartyVillagerRecord villager : party.villagers()) {
                this.partyByVillager.put(villager.villagerId(), party.id());
            }
        }
        invalidParties.forEach(this.partiesById::remove);
        Set<UUID> validPartyIds = Set.copyOf(this.partiesById.keySet());
        for (PartyRecord party : this.partiesById.values()) {
            party.retainPartyRelationships(validPartyIds);
        }
        for (PartyRecord party : this.partiesById.values()) {
            for (UUID alliedPartyId : List.copyOf(party.alliedPartyIds())) {
                PartyRecord ally = this.partiesById.get(alliedPartyId);
                if (ally != null) {
                    ally.addAlliance(party.id());
                }
            }
        }
        for (PartyRecord party : this.partiesById.values()) {
            party.retainPartyRelationships(validPartyIds);
        }
    }
}
