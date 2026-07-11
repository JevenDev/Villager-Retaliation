package com.jvn.villagerretaliation.party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class PartyRecord {
    private static final String TAG_ID = "Id";
    private static final String TAG_LEADER = "Leader";
    private static final String TAG_CREATED_GAME_TIME = "CreatedGameTime";
    private static final String TAG_PLAYERS = "Players";
    private static final String TAG_PLAYER = "Player";
    private static final String TAG_VILLAGERS = "Villagers";
    private static final String TAG_SHARED_QUESTS = "SharedQuests";
    private static final String TAG_ATTACK_WITH_PARTY = "AttackWithParty";
    private static final String TAG_DEFEND_PARTY = "DefendParty";
    private static final String TAG_SHARED_VILLAGER_INVENTORIES = "SharedVillagerInventories";

    private final UUID id;
    private final UUID leaderId;
    private final long createdGameTime;
    private final List<UUID> playerIds;
    private final List<PartyVillagerRecord> villagers;
    private final List<PartySharedQuestRecord> sharedQuests;
    private boolean attackWithParty;
    private boolean defendParty;
    private boolean sharedVillagerInventories;

    PartyRecord(UUID id, UUID leaderId, long createdGameTime) {
        this(id, leaderId, createdGameTime, new ArrayList<>(List.of(leaderId)), new ArrayList<>(), new ArrayList<>(), true, true, true);
    }

    private PartyRecord(
            UUID id,
            UUID leaderId,
            long createdGameTime,
            List<UUID> playerIds,
            List<PartyVillagerRecord> villagers,
            List<PartySharedQuestRecord> sharedQuests,
            boolean attackWithParty,
            boolean defendParty,
            boolean sharedVillagerInventories) {
        this.id = id;
        this.leaderId = leaderId;
        this.createdGameTime = Math.max(0L, createdGameTime);
        this.playerIds = playerIds;
        this.villagers = villagers;
        this.sharedQuests = sharedQuests;
        this.attackWithParty = attackWithParty;
        this.defendParty = defendParty;
        this.sharedVillagerInventories = sharedVillagerInventories;
        normalizePlayers();
    }

    public UUID id() {
        return this.id;
    }

    public UUID leaderId() {
        return this.leaderId;
    }

    public long createdGameTime() {
        return this.createdGameTime;
    }

    public List<UUID> playerIds() {
        return Collections.unmodifiableList(this.playerIds);
    }

    public List<PartyVillagerRecord> villagers() {
        return Collections.unmodifiableList(this.villagers);
    }

    public List<PartySharedQuestRecord> sharedQuests() {
        return Collections.unmodifiableList(this.sharedQuests);
    }

    public boolean attackWithParty() {
        return this.attackWithParty;
    }

    public boolean defendParty() {
        return this.defendParty;
    }

    public boolean sharedVillagerInventories() {
        return this.sharedVillagerInventories;
    }

    void setAttackWithParty(boolean enabled) {
        this.attackWithParty = enabled;
    }

    void setDefendParty(boolean enabled) {
        this.defendParty = enabled;
    }

    void setSharedVillagerInventories(boolean enabled) {
        this.sharedVillagerInventories = enabled;
    }

    public void addSharedQuest(PartySharedQuestRecord sharedQuest) {
        if (sharedQuest != null && this.sharedQuests.stream().noneMatch(existing -> existing.instanceId().equals(sharedQuest.instanceId()))) {
            this.sharedQuests.add(sharedQuest);
        }
    }

    public boolean removeSharedQuest(UUID instanceId) {
        return this.sharedQuests.removeIf(sharedQuest -> sharedQuest.instanceId().equals(instanceId));
    }

    public int totalMembers() {
        return this.playerIds.size() + this.villagers.size();
    }

    boolean addPlayer(UUID playerId) {
        if (playerId == null || this.playerIds.size() >= PartyService.MAX_PLAYERS || this.playerIds.contains(playerId)) {
            return false;
        }
        return this.playerIds.add(playerId);
    }

    boolean removePlayer(UUID playerId) {
        return playerId != null && !this.leaderId.equals(playerId) && this.playerIds.remove(playerId);
    }

    boolean addVillager(PartyVillagerRecord villager) {
        if (villager == null || this.villagers.size() >= PartyService.MAX_VILLAGERS) {
            return false;
        }
        for (PartyVillagerRecord existing : this.villagers) {
            if (existing.villagerId().equals(villager.villagerId())) {
                return false;
            }
        }
        this.villagers.add(villager);
        this.villagers.sort(java.util.Comparator.comparingInt(PartyVillagerRecord::recruitmentOrder));
        return true;
    }

    PartyVillagerRecord removeVillager(UUID villagerId) {
        for (int i = 0; i < this.villagers.size(); i++) {
            if (this.villagers.get(i).villagerId().equals(villagerId)) {
                return this.villagers.remove(i);
            }
        }
        return null;
    }

    public PartyVillagerRecord villager(UUID villagerId) {
        for (PartyVillagerRecord record : this.villagers) {
            if (record.villagerId().equals(villagerId)) {
                return record;
            }
        }
        return null;
    }

    int nextRecruitmentOrder() {
        return this.villagers.stream().mapToInt(PartyVillagerRecord::recruitmentOrder).max().orElse(-1) + 1;
    }

    void removeDuplicatePlayers(Set<UUID> claimedPlayers) {
        this.playerIds.removeIf(playerId -> !playerId.equals(this.leaderId) && !claimedPlayers.add(playerId));
        pruneSharedQuests();
    }

    void removeDuplicateVillagers(Set<UUID> claimedVillagers) {
        this.villagers.removeIf(villager -> !claimedVillagers.add(villager.villagerId()));
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_ID, this.id);
        tag.putUUID(TAG_LEADER, this.leaderId);
        tag.putLong(TAG_CREATED_GAME_TIME, this.createdGameTime);
        ListTag playersTag = new ListTag();
        for (UUID playerId : this.playerIds) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID(TAG_PLAYER, playerId);
            playersTag.add(playerTag);
        }
        tag.put(TAG_PLAYERS, playersTag);
        ListTag villagersTag = new ListTag();
        for (PartyVillagerRecord villager : this.villagers) {
            villagersTag.add(villager.save());
        }
        tag.put(TAG_VILLAGERS, villagersTag);
        ListTag sharedQuestsTag = new ListTag();
        for (PartySharedQuestRecord sharedQuest : this.sharedQuests) {
            sharedQuestsTag.add(sharedQuest.save());
        }
        tag.put(TAG_SHARED_QUESTS, sharedQuestsTag);
        tag.putBoolean(TAG_ATTACK_WITH_PARTY, this.attackWithParty);
        tag.putBoolean(TAG_DEFEND_PARTY, this.defendParty);
        tag.putBoolean(TAG_SHARED_VILLAGER_INVENTORIES, this.sharedVillagerInventories);
        return tag;
    }

    static PartyRecord load(CompoundTag tag) {
        if (!tag.hasUUID(TAG_ID) || !tag.hasUUID(TAG_LEADER)) {
            return null;
        }
        UUID leaderId = tag.getUUID(TAG_LEADER);
        List<UUID> players = new ArrayList<>();
        ListTag playersTag = tag.getList(TAG_PLAYERS, Tag.TAG_COMPOUND);
        for (Tag rawPlayer : playersTag) {
            if (rawPlayer instanceof CompoundTag playerTag && playerTag.hasUUID(TAG_PLAYER)) {
                players.add(playerTag.getUUID(TAG_PLAYER));
            }
        }
        List<PartyVillagerRecord> villagers = new ArrayList<>();
        ListTag villagersTag = tag.getList(TAG_VILLAGERS, Tag.TAG_COMPOUND);
        for (Tag rawVillager : villagersTag) {
            if (rawVillager instanceof CompoundTag villagerTag) {
                PartyVillagerRecord villager = PartyVillagerRecord.load(villagerTag);
                if (villager != null) {
                    villagers.add(villager);
                }
            }
        }
        List<PartySharedQuestRecord> sharedQuests = new ArrayList<>();
        for (Tag rawSharedQuest : tag.getList(TAG_SHARED_QUESTS, Tag.TAG_COMPOUND)) {
            if (rawSharedQuest instanceof CompoundTag sharedQuestTag) {
                PartySharedQuestRecord sharedQuest = PartySharedQuestRecord.load(sharedQuestTag);
                if (sharedQuest != null) {
                    sharedQuests.add(sharedQuest);
                }
            }
        }
        return new PartyRecord(
                tag.getUUID(TAG_ID),
                leaderId,
                tag.getLong(TAG_CREATED_GAME_TIME),
                players,
                villagers,
                sharedQuests,
                !tag.contains(TAG_ATTACK_WITH_PARTY) || tag.getBoolean(TAG_ATTACK_WITH_PARTY),
                !tag.contains(TAG_DEFEND_PARTY) || tag.getBoolean(TAG_DEFEND_PARTY),
                !tag.contains(TAG_SHARED_VILLAGER_INVENTORIES) || tag.getBoolean(TAG_SHARED_VILLAGER_INVENTORIES));
    }

    private void normalizePlayers() {
        LinkedHashSet<UUID> ordered = new LinkedHashSet<>();
        ordered.add(this.leaderId);
        ordered.addAll(this.playerIds);
        this.playerIds.clear();
        this.playerIds.addAll(ordered.stream().limit(PartyService.MAX_PLAYERS).toList());
        Set<UUID> uniqueVillagers = new LinkedHashSet<>();
        this.villagers.removeIf(villager -> !uniqueVillagers.add(villager.villagerId()));
        this.villagers.sort(java.util.Comparator.comparingInt(PartyVillagerRecord::recruitmentOrder));
        if (this.villagers.size() > PartyService.MAX_VILLAGERS) {
            this.villagers.subList(PartyService.MAX_VILLAGERS, this.villagers.size()).clear();
        }
        pruneSharedQuests();
    }

    private void pruneSharedQuests() {
        Set<UUID> currentPlayers = Set.copyOf(this.playerIds);
        Set<UUID> questInstances = new LinkedHashSet<>();
        for (PartySharedQuestRecord sharedQuest : this.sharedQuests) {
            sharedQuest.retainEnrollments(currentPlayers);
        }
        this.sharedQuests.removeIf(sharedQuest -> sharedQuest.enrollments().isEmpty()
                || sharedQuest.settled()
                || !questInstances.add(sharedQuest.instanceId()));
    }
}
