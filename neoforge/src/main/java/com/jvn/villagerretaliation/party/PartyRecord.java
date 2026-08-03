package com.jvn.villagerretaliation.party;

import com.jvn.villagerretaliation.quest.tracking.QuestTrackerLimits;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public final class PartyRecord {
    private static final String TAG_ID = "Id";
    private static final String TAG_LEADER = "Leader";
    private static final String TAG_CREATED_GAME_TIME = "CreatedGameTime";
    private static final String TAG_PLAYERS = "Players";
    private static final String TAG_ADMIN_PLAYERS = "AdminPlayers";
    private static final String TAG_PLAYER = "Player";
    private static final String TAG_VILLAGERS = "Villagers";
    private static final String TAG_SHARED_QUESTS = "SharedQuests";
    private static final String TAG_TRACKED_QUESTS = "TrackedQuests";
    private static final String TAG_QUEST = "Quest";
    private static final String TAG_ATTACK_WITH_PARTY = "AttackWithParty";
    private static final String TAG_COMBAT_MODE = "PartyCombatMode";
    private static final String TAG_ATTACK_MODE = "AttackMode";
    private static final String TAG_KILL_ON_SIGHT = "KillOnSight";
    private static final String TAG_SHARED_VILLAGER_INVENTORIES = "SharedVillagerInventories";
    private static final String TAG_FRIENDLY_FIRE_ALLOWED = "FriendlyFireAllowed";
    private static final String TAG_MOUNT_MODE = "MountMode";
    private static final String TAG_ALLIED_PARTIES = "AlliedParties";
    private static final String TAG_ALLIANCE_REQUESTS = "AllianceRequests";
    private static final String TAG_PARTY = "Party";

    private final UUID id;
    private final UUID leaderId;
    private final long createdGameTime;
    private final List<UUID> playerIds;
    private final Set<UUID> adminPlayerIds;
    private final List<PartyVillagerRecord> villagers;
    private final List<PartySharedQuestRecord> sharedQuests;
    private final List<ResourceLocation> trackedQuests;
    private final Set<UUID> alliedPartyIds;
    private final Set<UUID> allianceRequestPartyIds;
    private final List<UUID> playerIdsView;
    private final List<PartyVillagerRecord> villagersView;
    private final List<PartySharedQuestRecord> sharedQuestsView;
    private final List<ResourceLocation> trackedQuestsView;
    private final Set<UUID> alliedPartyIdsView;
    private PartyCombatMode combatMode;
    private PartyAttackMode attackMode;
    private boolean sharedVillagerInventories;
    private boolean friendlyFireAllowed;
    private boolean mountMode;

    PartyRecord(UUID id, UUID leaderId, long createdGameTime) {
        this(id, leaderId, createdGameTime, new ArrayList<>(List.of(leaderId)), new LinkedHashSet<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new LinkedHashSet<>(), new LinkedHashSet<>(),
                PartyCombatMode.ATTACK_WITH_PARTY, PartyAttackMode.ALL,
                true, false, false);
    }

    private PartyRecord(
            UUID id,
            UUID leaderId,
            long createdGameTime,
            List<UUID> playerIds,
            Set<UUID> adminPlayerIds,
            List<PartyVillagerRecord> villagers,
            List<PartySharedQuestRecord> sharedQuests,
            List<ResourceLocation> trackedQuests,
            Set<UUID> alliedPartyIds,
            Set<UUID> allianceRequestPartyIds,
            PartyCombatMode combatMode,
            PartyAttackMode attackMode,
            boolean sharedVillagerInventories,
            boolean friendlyFireAllowed,
            boolean mountMode) {
        this.id = id;
        this.leaderId = leaderId;
        this.createdGameTime = Math.max(0L, createdGameTime);
        this.playerIds = playerIds;
        this.adminPlayerIds = adminPlayerIds;
        this.villagers = villagers;
        this.sharedQuests = sharedQuests;
        this.trackedQuests = trackedQuests;
        this.alliedPartyIds = alliedPartyIds;
        this.allianceRequestPartyIds = allianceRequestPartyIds;
        this.playerIdsView = Collections.unmodifiableList(this.playerIds);
        this.villagersView = Collections.unmodifiableList(this.villagers);
        this.sharedQuestsView = Collections.unmodifiableList(this.sharedQuests);
        this.trackedQuestsView = Collections.unmodifiableList(this.trackedQuests);
        this.alliedPartyIdsView = Collections.unmodifiableSet(this.alliedPartyIds);
        this.combatMode = combatMode == null ? PartyCombatMode.ATTACK_WITH_PARTY : combatMode;
        this.attackMode = attackMode == null ? PartyAttackMode.ALL : attackMode;
        this.villagers.forEach(villager -> villager.bindPartyPolicies(this.combatMode, this.attackMode));
        this.sharedVillagerInventories = sharedVillagerInventories;
        this.friendlyFireAllowed = friendlyFireAllowed;
        this.mountMode = mountMode;
        normalizePlayers();
    }

    public UUID id() {
        return this.id;
    }

    public UUID leaderId() {
        return this.leaderId;
    }

    public List<UUID> playerIds() {
        return this.playerIdsView;
    }

    public boolean hasAdminPrivileges(UUID playerId) {
        return playerId != null
                && (this.leaderId.equals(playerId) || this.adminPlayerIds.contains(playerId));
    }

    boolean setAdminPrivileges(UUID playerId, boolean enabled) {
        if (playerId == null || this.leaderId.equals(playerId) || !this.playerIds.contains(playerId)) {
            return false;
        }
        return enabled ? this.adminPlayerIds.add(playerId) : this.adminPlayerIds.remove(playerId);
    }

    public List<PartyVillagerRecord> villagers() {
        return this.villagersView;
    }

    public List<PartySharedQuestRecord> sharedQuests() {
        return this.sharedQuestsView;
    }

    public List<ResourceLocation> trackedQuests() {
        return this.trackedQuestsView;
    }

    public boolean setTrackedQuest(ResourceLocation questId) {
        return trackQuest(questId);
    }

    public boolean removeTrackedQuest(ResourceLocation questId) {
        return questId != null && this.trackedQuests.remove(questId);
    }

    public boolean toggleTrackedQuest(ResourceLocation questId) {
        if (questId == null) {
            return false;
        }
        if (this.trackedQuests.remove(questId)) {
            return true;
        }
        return trackQuest(questId);
    }

    private boolean trackQuest(ResourceLocation questId) {
        if (questId == null) {
            return false;
        }
        this.trackedQuests.remove(questId);
        this.trackedQuests.addFirst(questId);
        while (this.trackedQuests.size() > QuestTrackerLimits.MAX_TRACKED_QUESTS) {
            this.trackedQuests.removeLast();
        }
        return true;
    }

    Set<UUID> alliedPartyIds() {
        return this.alliedPartyIdsView;
    }

    public boolean isAlliedWith(UUID partyId) {
        return partyId != null && this.alliedPartyIds.contains(partyId);
    }

    public boolean hasRequestedAllianceWith(UUID partyId) {
        return partyId != null && this.allianceRequestPartyIds.contains(partyId);
    }

    boolean addAlliance(UUID partyId) {
        return partyId != null && !this.id.equals(partyId) && this.alliedPartyIds.add(partyId);
    }

    boolean removeAlliance(UUID partyId) {
        return partyId != null && this.alliedPartyIds.remove(partyId);
    }

    boolean addAllianceRequest(UUID partyId) {
        return partyId != null
                && !this.id.equals(partyId)
                && !isAlliedWith(partyId)
                && this.allianceRequestPartyIds.add(partyId);
    }

    boolean removeAllianceRequest(UUID partyId) {
        return partyId != null && this.allianceRequestPartyIds.remove(partyId);
    }

    void retainPartyRelationships(Set<UUID> validPartyIds) {
        this.alliedPartyIds.removeIf(partyId -> this.id.equals(partyId) || !validPartyIds.contains(partyId));
        this.allianceRequestPartyIds.removeIf(partyId -> this.id.equals(partyId)
                || !validPartyIds.contains(partyId)
                || this.alliedPartyIds.contains(partyId));
    }

    void retainAlliances(Set<UUID> mutualPartyIds) {
        this.alliedPartyIds.retainAll(mutualPartyIds);
    }

    public PartyCombatMode combatMode() {
        return this.combatMode;
    }

    public PartyAttackMode attackMode() {
        return this.attackMode;
    }

    public boolean sharedVillagerInventories() {
        return this.sharedVillagerInventories;
    }

    public boolean friendlyFireAllowed() {
        return this.friendlyFireAllowed;
    }

    public boolean mountMode() {
        return this.mountMode;
    }

    void setCombatMode(PartyCombatMode mode) {
        this.combatMode = mode == null ? PartyCombatMode.ATTACK_WITH_PARTY : mode;
        this.villagers.forEach(villager -> villager.bindPartyPolicies(this.combatMode, this.attackMode));
    }

    void setAttackMode(PartyAttackMode mode) {
        this.attackMode = mode == null ? PartyAttackMode.ALL : mode;
        this.villagers.forEach(villager -> villager.bindPartyPolicies(this.combatMode, this.attackMode));
    }

    void setSharedVillagerInventories(boolean enabled) {
        this.sharedVillagerInventories = enabled;
    }

    void setFriendlyFireAllowed(boolean enabled) {
        this.friendlyFireAllowed = enabled;
    }

    void setMountMode(boolean enabled) {
        this.mountMode = enabled;
    }

    public void addSharedQuest(PartySharedQuestRecord sharedQuest) {
        if (sharedQuest != null && this.sharedQuests.stream().noneMatch(existing -> existing.instanceId().equals(sharedQuest.instanceId()))) {
            this.sharedQuests.add(sharedQuest);
        }
    }

    public boolean removeSharedQuest(UUID instanceId) {
        PartySharedQuestRecord removed = this.sharedQuests.stream()
                .filter(sharedQuest -> sharedQuest.instanceId().equals(instanceId))
                .findFirst()
                .orElse(null);
        if (removed == null || !this.sharedQuests.remove(removed)) {
            return false;
        }
        if (this.sharedQuests.stream().noneMatch(sharedQuest -> !sharedQuest.completed()
                && sharedQuest.questId().equals(removed.questId()))) {
            this.trackedQuests.remove(removed.questId());
        }
        return true;
    }

    boolean addPlayer(UUID playerId) {
        if (playerId == null || this.playerIds.size() >= PartyService.MAX_PLAYERS || this.playerIds.contains(playerId)) {
            return false;
        }
        return this.playerIds.add(playerId);
    }

    boolean removePlayer(UUID playerId) {
        if (playerId == null || this.leaderId.equals(playerId) || !this.playerIds.remove(playerId)) {
            return false;
        }
        this.adminPlayerIds.remove(playerId);
        return true;
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
        villager.bindPartyPolicies(this.combatMode, this.attackMode);
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
        tag.put(TAG_ADMIN_PLAYERS, savePlayerIds(this.adminPlayerIds));
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
        ListTag trackedQuestsTag = new ListTag();
        for (ResourceLocation questId : this.trackedQuests) {
            CompoundTag questTag = new CompoundTag();
            questTag.putString(TAG_QUEST, questId.toString());
            trackedQuestsTag.add(questTag);
        }
        tag.put(TAG_TRACKED_QUESTS, trackedQuestsTag);
        tag.putString(TAG_COMBAT_MODE, this.combatMode.name());
        tag.putString(TAG_ATTACK_MODE, this.attackMode.name());
        tag.putBoolean(TAG_SHARED_VILLAGER_INVENTORIES, this.sharedVillagerInventories);
        tag.putBoolean(TAG_FRIENDLY_FIRE_ALLOWED, this.friendlyFireAllowed);
        tag.putBoolean(TAG_MOUNT_MODE, this.mountMode);
        tag.put(TAG_ALLIED_PARTIES, savePartyIds(this.alliedPartyIds));
        tag.put(TAG_ALLIANCE_REQUESTS, savePartyIds(this.allianceRequestPartyIds));
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
        List<ResourceLocation> trackedQuests = new ArrayList<>();
        for (Tag rawTrackedQuest : tag.getList(TAG_TRACKED_QUESTS, Tag.TAG_COMPOUND)) {
            if (rawTrackedQuest instanceof CompoundTag trackedQuestTag) {
                ResourceLocation questId = ResourceLocation.tryParse(trackedQuestTag.getString(TAG_QUEST));
                if (questId != null && !trackedQuests.contains(questId)) {
                    trackedQuests.add(questId);
                }
            }
        }
        return new PartyRecord(
                tag.getUUID(TAG_ID),
                leaderId,
                tag.getLong(TAG_CREATED_GAME_TIME),
                players,
                loadPlayerIds(tag, TAG_ADMIN_PLAYERS),
                villagers,
                sharedQuests,
                trackedQuests,
                loadPartyIds(tag, TAG_ALLIED_PARTIES),
                loadPartyIds(tag, TAG_ALLIANCE_REQUESTS),
                loadCombatMode(tag),
                PartyAttackMode.byName(tag.getString(TAG_ATTACK_MODE)),
                !tag.contains(TAG_SHARED_VILLAGER_INVENTORIES) || tag.getBoolean(TAG_SHARED_VILLAGER_INVENTORIES),
                tag.getBoolean(TAG_FRIENDLY_FIRE_ALLOWED),
                tag.getBoolean(TAG_MOUNT_MODE));
    }

    private void normalizePlayers() {
        LinkedHashSet<UUID> ordered = new LinkedHashSet<>();
        ordered.add(this.leaderId);
        ordered.addAll(this.playerIds);
        this.playerIds.clear();
        this.playerIds.addAll(ordered.stream().limit(PartyService.MAX_PLAYERS).toList());
        this.adminPlayerIds.retainAll(this.playerIds);
        this.adminPlayerIds.remove(this.leaderId);
        Set<UUID> uniqueVillagers = new LinkedHashSet<>();
        this.villagers.removeIf(villager -> !uniqueVillagers.add(villager.villagerId()));
        this.villagers.sort(java.util.Comparator.comparingInt(PartyVillagerRecord::recruitmentOrder));
        if (this.villagers.size() > PartyService.MAX_VILLAGERS) {
            this.villagers.subList(PartyService.MAX_VILLAGERS, this.villagers.size()).clear();
        }
        pruneSharedQuests();
        LinkedHashSet<ResourceLocation> uniqueTrackedQuests = new LinkedHashSet<>(this.trackedQuests);
        this.trackedQuests.clear();
        this.trackedQuests.addAll(uniqueTrackedQuests.stream()
                .filter(questId -> this.sharedQuests.stream().anyMatch(sharedQuest -> !sharedQuest.completed()
                        && sharedQuest.questId().equals(questId)))
                .limit(QuestTrackerLimits.MAX_TRACKED_QUESTS)
                .toList());
    }

    private static PartyCombatMode loadCombatMode(CompoundTag tag) {
        if (tag.contains(TAG_COMBAT_MODE, Tag.TAG_STRING)) {
            return PartyCombatMode.byName(tag.getString(TAG_COMBAT_MODE));
        }
        if (tag.getBoolean(TAG_KILL_ON_SIGHT)) {
            return PartyCombatMode.KILL_ON_SIGHT;
        }
        return !tag.contains(TAG_ATTACK_WITH_PARTY) || tag.getBoolean(TAG_ATTACK_WITH_PARTY)
                ? PartyCombatMode.ATTACK_WITH_PARTY
                : PartyCombatMode.SELF_DEFENSE;
    }

    private static ListTag savePlayerIds(Set<UUID> playerIds) {
        ListTag ids = new ListTag();
        for (UUID playerId : playerIds) {
            CompoundTag idTag = new CompoundTag();
            idTag.putUUID(TAG_PLAYER, playerId);
            ids.add(idTag);
        }
        return ids;
    }

    private static Set<UUID> loadPlayerIds(CompoundTag tag, String key) {
        Set<UUID> playerIds = new LinkedHashSet<>();
        for (Tag rawPlayer : tag.getList(key, Tag.TAG_COMPOUND)) {
            if (rawPlayer instanceof CompoundTag playerTag && playerTag.hasUUID(TAG_PLAYER)) {
                playerIds.add(playerTag.getUUID(TAG_PLAYER));
            }
        }
        return playerIds;
    }

    private static ListTag savePartyIds(Set<UUID> partyIds) {
        ListTag ids = new ListTag();
        for (UUID partyId : partyIds) {
            CompoundTag idTag = new CompoundTag();
            idTag.putUUID(TAG_PARTY, partyId);
            ids.add(idTag);
        }
        return ids;
    }

    private static Set<UUID> loadPartyIds(CompoundTag tag, String key) {
        Set<UUID> partyIds = new LinkedHashSet<>();
        for (Tag rawParty : tag.getList(key, Tag.TAG_COMPOUND)) {
            if (rawParty instanceof CompoundTag partyTag && partyTag.hasUUID(TAG_PARTY)) {
                partyIds.add(partyTag.getUUID(TAG_PARTY));
            }
        }
        return partyIds;
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
        this.trackedQuests.removeIf(questId -> this.sharedQuests.stream().noneMatch(sharedQuest ->
                !sharedQuest.completed() && sharedQuest.questId().equals(questId)));
    }
}
