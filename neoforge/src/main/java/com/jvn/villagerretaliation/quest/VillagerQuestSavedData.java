package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.quest.tracking.QuestTrackerLimits;
import com.jvn.villagerretaliation.util.NbtDataUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public class VillagerQuestSavedData extends SavedData {
    private static final String DATA_NAME = "villagerretaliation_quests";
    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_TRACKED_QUESTS = "TrackedQuests";
    private static final String TAG_PLAYER = "Player";
    private static final String TAG_QUEST = "Quest";
    private static final String TAG_STATE = "State";
    private static final String TAG_STARTED_VILLAGER = "StartedVillager";
    private static final String TAG_STARTED_TIME = "StartedGameTime";
    private static final String TAG_COMPLETED_TIME = "CompletedGameTime";
    private static final String TAG_COMPLETION_HISTORY = "CompletionHistory";
    private static final String TAG_COMPLETION_INDEX = "CompletionIndex";
    private static final String TAG_ABANDONED_TIME = "AbandonedGameTime";
    private static final String TAG_EXPIRED_TIME = "ExpiredGameTime";
    private static final String TAG_VISITED_TARGET = "VisitedTarget";
    private static final String TAG_HAS_PROOF = "HasProof";
    private static final String TAG_PENDING_PARTY_REWARD = "PendingPartyReward";
    private static final String TAG_PARTY_REWARD_CLAIMED = "PartyRewardClaimed";
    private static final String TAG_PARTY_QUEST_INSTANCE = "PartyQuestInstance";
    private static final String TAG_ISSUER_NAME = "IssuerName";
    private static final String TAG_ISSUER_PROFESSION = "IssuerProfession";
    private static final String TAG_ISSUER_LEVEL = "IssuerLevel";
    private static final String TAG_ISSUER_DIMENSION = "IssuerDimension";
    private static final String TAG_ISSUER_POS = "IssuerPos";
    private static final String TAG_ISSUER_VILLAGE_KEY = "IssuerVillageKey";
    private static final String TAG_TARGET_DIMENSION = "TargetDimension";
    private static final String TAG_TARGET_POS = "TargetPos";
    private static final String TAG_TARGET_OBJECTIVE = "TargetObjective";
    private static final String TAG_CURRENT_STAGE = "CurrentStage";
    private static final String TAG_COMPLETED_OBJECTIVES = "CompletedObjectives";
    private static final String TAG_OBJECTIVE_COUNTERS = "ObjectiveCounters";
    private static final String TAG_OBJECTIVE = "Objective";
    private static final String TAG_COUNT = "Count";
    private static final String TAG_START_COUNT = "StartCount";
    private static final String TAG_COMPLETION_COUNT = "CompletionCount";
    private static final String TAG_ABANDON_COUNT = "AbandonCount";
    private static final String TAG_CONSUMED_REASON = "ConsumedReason";
    private static final String TAG_TRIGGER_TIMES = "TriggerTimes";
    private static final String TAG_CHOICE_HISTORY = "ChoiceHistory";
    private static final String TAG_SCENE_PATH = "ScenePath";
    private static final String TAG_RESPONSE = "Response";
    private static final String TAG_PRIOR_STAGE = "PriorStage";
    private static final String TAG_NEXT_STAGE = "NextStage";
    private static final String TAG_GAME_TIME = "GameTime";

    private final Map<UUID, Map<ResourceLocation, QuestProgress>> entries = new HashMap<>();
    private final Map<UUID, List<ResourceLocation>> trackedQuests = new HashMap<>();

    public static VillagerQuestSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VillagerQuestSavedData::new, VillagerQuestSavedData::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static VillagerQuestSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        VillagerQuestSavedData data = new VillagerQuestSavedData();
        ListTag entriesTag = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (Tag rawEntry : entriesTag) {
            if (!(rawEntry instanceof CompoundTag entryTag)
                    || !entryTag.hasUUID(TAG_PLAYER)
                    || !entryTag.contains(TAG_QUEST, Tag.TAG_STRING)) {
                continue;
            }

            ResourceLocation questId = ResourceLocation.tryParse(entryTag.getString(TAG_QUEST));
            if (questId == null) {
                continue;
            }
            QuestProgress progress = QuestProgress.load(entryTag);
            data.entries.computeIfAbsent(entryTag.getUUID(TAG_PLAYER), ignored -> new HashMap<>()).put(questId, progress);
        }
        ListTag trackedTag = tag.getList(TAG_TRACKED_QUESTS, Tag.TAG_COMPOUND);
        for (Tag rawEntry : trackedTag) {
            if (!(rawEntry instanceof CompoundTag entryTag)
                    || !entryTag.hasUUID(TAG_PLAYER)
                    || !entryTag.contains(TAG_QUEST, Tag.TAG_STRING)) {
                continue;
            }

            ResourceLocation questId = ResourceLocation.tryParse(entryTag.getString(TAG_QUEST));
            if (questId != null) {
                data.trackQuest(entryTag.getUUID(TAG_PLAYER), questId, false, false);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag entriesTag = new ListTag();
        for (Map.Entry<UUID, Map<ResourceLocation, QuestProgress>> playerEntry : this.entries.entrySet()) {
            for (Map.Entry<ResourceLocation, QuestProgress> questEntry : playerEntry.getValue().entrySet()) {
                CompoundTag entryTag = questEntry.getValue().save();
                entryTag.putUUID(TAG_PLAYER, playerEntry.getKey());
                entryTag.putString(TAG_QUEST, questEntry.getKey().toString());
                entriesTag.add(entryTag);
            }
        }
        tag.put(TAG_ENTRIES, entriesTag);
        ListTag trackedTag = new ListTag();
        for (Map.Entry<UUID, List<ResourceLocation>> entry : this.trackedQuests.entrySet()) {
            for (ResourceLocation questId : entry.getValue()) {
                CompoundTag entryTag = new CompoundTag();
                entryTag.putUUID(TAG_PLAYER, entry.getKey());
                entryTag.putString(TAG_QUEST, questId.toString());
                trackedTag.add(entryTag);
            }
        }
        tag.put(TAG_TRACKED_QUESTS, trackedTag);
        return tag;
    }

    public QuestProgress get(UUID playerId, ResourceLocation questId) {
        Map<ResourceLocation, QuestProgress> playerEntries = this.entries.get(playerId);
        return playerEntries == null ? null : playerEntries.get(questId);
    }

    public QuestProgress getOrCreate(UUID playerId, ResourceLocation questId) {
        return this.entries.computeIfAbsent(playerId, ignored -> new HashMap<>())
                .computeIfAbsent(questId, ignored -> new QuestProgress());
    }

    public QuestProgress remove(UUID playerId, ResourceLocation questId) {
        Map<ResourceLocation, QuestProgress> playerEntries = this.entries.get(playerId);
        if (playerEntries == null) {
            return null;
        }
        QuestProgress removed = playerEntries.remove(questId);
        if (playerEntries.isEmpty()) {
            this.entries.remove(playerId);
        }
        if (removed != null) {
            setDirty();
        }
        return removed;
    }

    public List<Map.Entry<ResourceLocation, QuestProgress>> activeProgress(UUID playerId) {
        Map<ResourceLocation, QuestProgress> playerEntries = this.entries.get(playerId);
        if (playerEntries == null || playerEntries.isEmpty()) {
            return List.of();
        }
        List<Map.Entry<ResourceLocation, QuestProgress>> active = new ArrayList<>();
        for (Map.Entry<ResourceLocation, QuestProgress> entry : playerEntries.entrySet()) {
            if (entry.getValue().state() == QuestState.ACTIVE) {
                active.add(entry);
            }
        }
        return List.copyOf(active);
    }

    public List<QuestEntry> activeProgressStartedBy(UUID villagerId) {
        if (villagerId == null || this.entries.isEmpty()) {
            return List.of();
        }
        List<QuestEntry> active = new ArrayList<>();
        for (Map.Entry<UUID, Map<ResourceLocation, QuestProgress>> playerEntry : this.entries.entrySet()) {
            for (Map.Entry<ResourceLocation, QuestProgress> questEntry : playerEntry.getValue().entrySet()) {
                QuestProgress progress = questEntry.getValue();
                if (progress.state() == QuestState.ACTIVE && villagerId.equals(progress.startedVillagerId())) {
                    active.add(new QuestEntry(playerEntry.getKey(), questEntry.getKey(), progress));
                }
            }
        }
        return List.copyOf(active);
    }

    public List<Map.Entry<ResourceLocation, QuestProgress>> progress(UUID playerId) {
        Map<ResourceLocation, QuestProgress> playerEntries = this.entries.get(playerId);
        if (playerEntries == null || playerEntries.isEmpty()) {
            return List.of();
        }
        return List.copyOf(playerEntries.entrySet());
    }

    public ResourceLocation getTrackedQuest(UUID playerId) {
        List<ResourceLocation> questIds = this.trackedQuests.get(playerId);
        return questIds == null || questIds.isEmpty() ? null : questIds.getFirst();
    }

    public List<ResourceLocation> getTrackedQuests(UUID playerId) {
        List<ResourceLocation> questIds = this.trackedQuests.get(playerId);
        return questIds == null || questIds.isEmpty() ? List.of() : List.copyOf(questIds);
    }

    public void setTrackedQuest(UUID playerId, ResourceLocation questId) {
        trackQuest(playerId, questId, true, true);
    }

    public void clearTrackedQuest(UUID playerId) {
        if (playerId != null && this.trackedQuests.remove(playerId) != null) {
            setDirty();
        }
    }

    public boolean removeTrackedQuest(UUID playerId, ResourceLocation questId) {
        if (playerId == null || questId == null) {
            return false;
        }
        List<ResourceLocation> questIds = this.trackedQuests.get(playerId);
        if (questIds == null || !questIds.remove(questId)) {
            return false;
        }
        if (questIds.isEmpty()) {
            this.trackedQuests.remove(playerId);
        }
        setDirty();
        return true;
    }

    public boolean toggleTrackedQuest(UUID playerId, ResourceLocation questId) {
        if (playerId == null || questId == null) {
            return false;
        }
        List<ResourceLocation> questIds = this.trackedQuests.get(playerId);
        if (questIds != null && questIds.contains(questId)) {
            removeTrackedQuest(playerId, questId);
            return false;
        }
        return trackQuest(playerId, questId, true, true);
    }

    private boolean trackQuest(UUID playerId, ResourceLocation questId, boolean primary, boolean markDirty) {
        if (playerId == null || questId == null) {
            return false;
        }
        List<ResourceLocation> questIds = this.trackedQuests.computeIfAbsent(playerId, ignored -> new ArrayList<>());
        boolean changed = questIds.remove(questId);
        if (primary) {
            questIds.addFirst(questId);
            changed = true;
        } else if (!changed) {
            questIds.add(questId);
            changed = true;
        } else {
            questIds.add(questId);
        }
        while (questIds.size() > QuestTrackerLimits.MAX_TRACKED_QUESTS) {
            questIds.removeLast();
            changed = true;
        }
        if (markDirty && changed) {
            setDirty();
        }
        return changed;
    }

    public int replaceIssuerVillageKey(String sourceKey, String targetKey) {
        sourceKey = sourceKey == null ? "" : sourceKey.trim();
        targetKey = targetKey == null ? "" : targetKey.trim();
        if (sourceKey.isBlank() || targetKey.isBlank() || sourceKey.equals(targetKey)) {
            return 0;
        }

        int changed = 0;
        for (Map<ResourceLocation, QuestProgress> playerEntries : this.entries.values()) {
            for (QuestProgress progress : playerEntries.values()) {
                if (progress.replaceIssuerVillageKey(sourceKey, targetKey)) {
                    changed++;
                }
            }
        }
        if (changed > 0) {
            setDirty();
        }
        return changed;
    }

    public int transferVillagerIdentity(UUID sourceVillagerId, UUID targetVillagerId) {
        if (sourceVillagerId == null || targetVillagerId == null || sourceVillagerId.equals(targetVillagerId)) {
            return 0;
        }

        int changed = 0;
        for (Map<ResourceLocation, QuestProgress> playerEntries : this.entries.values()) {
            for (QuestProgress progress : playerEntries.values()) {
                if (progress.replaceVillagerIdentity(sourceVillagerId, targetVillagerId)) {
                    changed++;
                }
            }
        }
        if (changed > 0) {
            setDirty();
        }
        return changed;
    }

    public enum QuestState {
        NOT_STARTED,
        ACTIVE,
        COMPLETED,
        ABANDONED,
        EXPIRED,
        CONSUMED;

        public static QuestState byName(String value) {
            if (value == null || value.isBlank()) {
                return NOT_STARTED;
            }
            try {
                return QuestState.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return NOT_STARTED;
            }
        }
    }

    public record QuestEntry(UUID playerId, ResourceLocation questId, QuestProgress progress) {
    }

    public record CompletionHistoryEntry(
            int completionIndex,
            UUID issuerId,
            long startedGameTime,
            long completedGameTime,
            String issuerName,
            String issuerProfession,
            int issuerLevel,
            ResourceKey<Level> issuerDimension,
            BlockPos issuerPos,
            String issuerVillageKey
    ) {
        public CompletionHistoryEntry {
            completionIndex = Math.max(1, completionIndex);
            issuerName = issuerName == null ? "" : issuerName;
            issuerProfession = issuerProfession == null ? "" : issuerProfession;
            issuerLevel = Math.max(0, issuerLevel);
            issuerPos = issuerPos == null ? null : issuerPos.immutable();
            issuerVillageKey = issuerVillageKey == null ? "" : issuerVillageKey;
        }

        private static CompletionHistoryEntry load(CompoundTag tag) {
            UUID issuerId = tag.hasUUID(TAG_STARTED_VILLAGER) ? tag.getUUID(TAG_STARTED_VILLAGER) : null;
            ResourceKey<Level> issuerDimension = NbtDataUtil.readResourceLocation(tag, TAG_ISSUER_DIMENSION)
                    .map(id -> ResourceKey.create(Registries.DIMENSION, id))
                    .orElse(null);
            return new CompletionHistoryEntry(
                    tag.getInt(TAG_COMPLETION_INDEX),
                    issuerId,
                    tag.getLong(TAG_STARTED_TIME),
                    tag.getLong(TAG_COMPLETED_TIME),
                    tag.getString(TAG_ISSUER_NAME),
                    tag.getString(TAG_ISSUER_PROFESSION),
                    tag.getInt(TAG_ISSUER_LEVEL),
                    issuerDimension,
                    NbtDataUtil.readBlockPos(tag, TAG_ISSUER_POS).orElse(null),
                    tag.getString(TAG_ISSUER_VILLAGE_KEY));
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt(TAG_COMPLETION_INDEX, this.completionIndex);
            if (this.issuerId != null) {
                tag.putUUID(TAG_STARTED_VILLAGER, this.issuerId);
            }
            tag.putLong(TAG_STARTED_TIME, this.startedGameTime);
            tag.putLong(TAG_COMPLETED_TIME, this.completedGameTime);
            if (!this.issuerName.isBlank()) {
                tag.putString(TAG_ISSUER_NAME, this.issuerName);
            }
            if (!this.issuerProfession.isBlank()) {
                tag.putString(TAG_ISSUER_PROFESSION, this.issuerProfession);
            }
            if (this.issuerLevel > 0) {
                tag.putInt(TAG_ISSUER_LEVEL, this.issuerLevel);
            }
            if (this.issuerDimension != null) {
                NbtDataUtil.putResourceLocation(tag, TAG_ISSUER_DIMENSION, this.issuerDimension.location());
            }
            NbtDataUtil.putBlockPos(tag, TAG_ISSUER_POS, this.issuerPos);
            if (!this.issuerVillageKey.isBlank()) {
                tag.putString(TAG_ISSUER_VILLAGE_KEY, this.issuerVillageKey);
            }
            return tag;
        }

        private CompletionHistoryEntry replacingVillagerIdentity(UUID sourceVillagerId, UUID targetVillagerId) {
            if (this.issuerId == null || !this.issuerId.equals(sourceVillagerId)) {
                return this;
            }
            return new CompletionHistoryEntry(
                    this.completionIndex,
                    targetVillagerId,
                    this.startedGameTime,
                    this.completedGameTime,
                    this.issuerName,
                    this.issuerProfession,
                    this.issuerLevel,
                    this.issuerDimension,
                    this.issuerPos,
                    this.issuerVillageKey
            );
        }
    }

    public static class QuestProgress {
        private QuestState state = QuestState.NOT_STARTED;
        private UUID startedVillagerId;
        private long startedGameTime;
        private long completedGameTime;
        private long abandonedGameTime;
        private long expiredGameTime;
        private boolean visitedTarget;
        private boolean hasProof;
        private boolean pendingPartyReward;
        private boolean partyRewardClaimed;
        private UUID partyQuestInstanceId;
        private String issuerName = "";
        private String issuerProfession = "";
        private int issuerLevel;
        private ResourceKey<Level> issuerDimension;
        private BlockPos issuerPos;
        private String issuerVillageKey = "";
        private ResourceKey<Level> targetDimension;
        private BlockPos targetPos;
        private String targetObjectiveId = "";
        private String currentStage = "";
        private final java.util.Set<String> completedObjectives = new java.util.HashSet<>();
        private final Map<String, Integer> objectiveCounters = new HashMap<>();
        private int startCount;
        private int completionCount;
        private int abandonCount;
        private String consumedReason = "";
        private final Map<String, Long> triggerTimes = new HashMap<>();
        private final List<ChoiceHistoryEntry> choiceHistory = new ArrayList<>();
        private final List<CompletionHistoryEntry> completionHistory = new ArrayList<>();

        private static QuestProgress load(CompoundTag tag) {
            QuestProgress progress = new QuestProgress();
            progress.state = QuestState.byName(tag.getString(TAG_STATE));
            if (tag.hasUUID(TAG_STARTED_VILLAGER)) {
                progress.startedVillagerId = tag.getUUID(TAG_STARTED_VILLAGER);
            }
            progress.startedGameTime = tag.getLong(TAG_STARTED_TIME);
            progress.completedGameTime = tag.getLong(TAG_COMPLETED_TIME);
            progress.abandonedGameTime = tag.getLong(TAG_ABANDONED_TIME);
            progress.expiredGameTime = tag.getLong(TAG_EXPIRED_TIME);
            progress.visitedTarget = tag.getBoolean(TAG_VISITED_TARGET);
            progress.hasProof = tag.getBoolean(TAG_HAS_PROOF);
            progress.pendingPartyReward = tag.getBoolean(TAG_PENDING_PARTY_REWARD);
            progress.partyRewardClaimed = tag.getBoolean(TAG_PARTY_REWARD_CLAIMED);
            if (tag.hasUUID(TAG_PARTY_QUEST_INSTANCE)) {
                progress.partyQuestInstanceId = tag.getUUID(TAG_PARTY_QUEST_INSTANCE);
            }
            progress.issuerName = tag.getString(TAG_ISSUER_NAME);
            progress.issuerProfession = tag.getString(TAG_ISSUER_PROFESSION);
            progress.issuerLevel = tag.getInt(TAG_ISSUER_LEVEL);
            progress.issuerVillageKey = tag.getString(TAG_ISSUER_VILLAGE_KEY);
            progress.startCount = tag.getInt(TAG_START_COUNT);
            progress.completionCount = tag.getInt(TAG_COMPLETION_COUNT);
            progress.abandonCount = tag.getInt(TAG_ABANDON_COUNT);
            progress.consumedReason = tag.getString(TAG_CONSUMED_REASON);
            progress.targetObjectiveId = tag.getString(TAG_TARGET_OBJECTIVE);
            progress.currentStage = tag.getString(TAG_CURRENT_STAGE);
            progress.completedObjectives.addAll(NbtDataUtil.readStringSet(tag, TAG_COMPLETED_OBJECTIVES));
            if (tag.contains(TAG_OBJECTIVE_COUNTERS, Tag.TAG_LIST)) {
                ListTag countersTag = tag.getList(TAG_OBJECTIVE_COUNTERS, Tag.TAG_COMPOUND);
                for (Tag rawCounter : countersTag) {
                    if (!(rawCounter instanceof CompoundTag counterTag)
                            || !counterTag.contains(TAG_OBJECTIVE, Tag.TAG_STRING)
                            || !counterTag.contains(TAG_COUNT, Tag.TAG_INT)) {
                        continue;
                    }
                    String objectiveId = counterTag.getString(TAG_OBJECTIVE);
                    if (!objectiveId.isBlank()) {
                        progress.objectiveCounters.put(objectiveId, counterTag.getInt(TAG_COUNT));
                    }
                }
            }
            if (tag.contains(TAG_TRIGGER_TIMES, Tag.TAG_COMPOUND)) {
                CompoundTag triggerTimesTag = tag.getCompound(TAG_TRIGGER_TIMES);
                for (String key : triggerTimesTag.getAllKeys()) {
                    progress.triggerTimes.put(key, triggerTimesTag.getLong(key));
                }
            }
            if (tag.contains(TAG_CHOICE_HISTORY, Tag.TAG_LIST)) {
                ListTag historyTag = tag.getList(TAG_CHOICE_HISTORY, Tag.TAG_COMPOUND);
                for (Tag rawChoice : historyTag) {
                    if (rawChoice instanceof CompoundTag choiceTag) {
                        progress.choiceHistory.add(ChoiceHistoryEntry.load(choiceTag));
                    }
                }
            }
            if (tag.contains(TAG_COMPLETION_HISTORY, Tag.TAG_LIST)) {
                ListTag historyTag = tag.getList(TAG_COMPLETION_HISTORY, Tag.TAG_COMPOUND);
                for (Tag rawCompletion : historyTag) {
                    if (rawCompletion instanceof CompoundTag completionTag) {
                        progress.completionHistory.add(CompletionHistoryEntry.load(completionTag));
                    }
                }
            }
            NbtDataUtil.readResourceLocation(tag, TAG_ISSUER_DIMENSION)
                    .ifPresent(id -> progress.issuerDimension = ResourceKey.create(Registries.DIMENSION, id));
            progress.issuerPos = NbtDataUtil.readBlockPos(tag, TAG_ISSUER_POS).orElse(null);
            NbtDataUtil.readResourceLocation(tag, TAG_TARGET_DIMENSION)
                    .ifPresent(id -> progress.targetDimension = ResourceKey.create(Registries.DIMENSION, id));
            progress.targetPos = NbtDataUtil.readBlockPos(tag, TAG_TARGET_POS).orElse(null);
            return progress;
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString(TAG_STATE, this.state.name());
            if (this.startedVillagerId != null) {
                tag.putUUID(TAG_STARTED_VILLAGER, this.startedVillagerId);
            }
            tag.putLong(TAG_STARTED_TIME, this.startedGameTime);
            tag.putLong(TAG_COMPLETED_TIME, this.completedGameTime);
            tag.putLong(TAG_ABANDONED_TIME, this.abandonedGameTime);
            tag.putLong(TAG_EXPIRED_TIME, this.expiredGameTime);
            tag.putBoolean(TAG_VISITED_TARGET, this.visitedTarget);
            tag.putBoolean(TAG_HAS_PROOF, this.hasProof);
            tag.putBoolean(TAG_PENDING_PARTY_REWARD, this.pendingPartyReward);
            tag.putBoolean(TAG_PARTY_REWARD_CLAIMED, this.partyRewardClaimed);
            if (this.partyQuestInstanceId != null) {
                tag.putUUID(TAG_PARTY_QUEST_INSTANCE, this.partyQuestInstanceId);
            }
            if (!this.issuerName.isBlank()) {
                tag.putString(TAG_ISSUER_NAME, this.issuerName);
            }
            if (!this.issuerProfession.isBlank()) {
                tag.putString(TAG_ISSUER_PROFESSION, this.issuerProfession);
            }
            if (this.issuerLevel > 0) {
                tag.putInt(TAG_ISSUER_LEVEL, this.issuerLevel);
            }
            if (this.issuerDimension != null) {
                NbtDataUtil.putResourceLocation(tag, TAG_ISSUER_DIMENSION, this.issuerDimension.location());
            }
            NbtDataUtil.putBlockPos(tag, TAG_ISSUER_POS, this.issuerPos);
            if (!this.issuerVillageKey.isBlank()) {
                tag.putString(TAG_ISSUER_VILLAGE_KEY, this.issuerVillageKey);
            }
            tag.putInt(TAG_START_COUNT, this.startCount);
            tag.putInt(TAG_COMPLETION_COUNT, this.completionCount);
            tag.putInt(TAG_ABANDON_COUNT, this.abandonCount);
            tag.putString(TAG_CONSUMED_REASON, this.consumedReason);
            if (!this.targetObjectiveId.isBlank()) {
                tag.putString(TAG_TARGET_OBJECTIVE, this.targetObjectiveId);
            }
            if (!this.currentStage.isBlank()) {
                tag.putString(TAG_CURRENT_STAGE, this.currentStage);
            }
            if (!this.completedObjectives.isEmpty()) {
                tag.put(TAG_COMPLETED_OBJECTIVES, NbtDataUtil.stringList(this.completedObjectives));
            }
            if (!this.objectiveCounters.isEmpty()) {
                ListTag countersTag = new ListTag();
                for (Map.Entry<String, Integer> entry : this.objectiveCounters.entrySet()) {
                    if (entry.getKey().isBlank() || entry.getValue() <= 0) {
                        continue;
                    }
                    CompoundTag counterTag = new CompoundTag();
                    counterTag.putString(TAG_OBJECTIVE, entry.getKey());
                    counterTag.putInt(TAG_COUNT, entry.getValue());
                    countersTag.add(counterTag);
                }
                tag.put(TAG_OBJECTIVE_COUNTERS, countersTag);
            }
            if (!this.triggerTimes.isEmpty()) {
                CompoundTag triggerTimesTag = new CompoundTag();
                for (Map.Entry<String, Long> entry : this.triggerTimes.entrySet()) {
                    triggerTimesTag.putLong(entry.getKey(), entry.getValue());
                }
                tag.put(TAG_TRIGGER_TIMES, triggerTimesTag);
            }
            if (!this.choiceHistory.isEmpty()) {
                ListTag historyTag = new ListTag();
                for (ChoiceHistoryEntry entry : this.choiceHistory) {
                    historyTag.add(entry.save());
                }
                tag.put(TAG_CHOICE_HISTORY, historyTag);
            }
            if (!this.completionHistory.isEmpty()) {
                ListTag historyTag = new ListTag();
                for (CompletionHistoryEntry entry : this.completionHistory) {
                    historyTag.add(entry.save());
                }
                tag.put(TAG_COMPLETION_HISTORY, historyTag);
            }
            if (this.targetDimension != null) {
                NbtDataUtil.putResourceLocation(tag, TAG_TARGET_DIMENSION, this.targetDimension.location());
            }
            NbtDataUtil.putBlockPos(tag, TAG_TARGET_POS, this.targetPos);
            return tag;
        }

        public QuestState state() {
            return this.state;
        }

        public UUID startedVillagerId() {
            return this.startedVillagerId;
        }

        public long startedGameTime() {
            return this.startedGameTime;
        }

        public long completedGameTime() {
            return this.completedGameTime;
        }

        public long abandonedGameTime() {
            return this.abandonedGameTime;
        }

        public long expiredGameTime() {
            return this.expiredGameTime;
        }

        public boolean visitedTarget() {
            return this.visitedTarget;
        }

        public boolean hasProof() {
            return this.hasProof;
        }

        public boolean pendingPartyReward() {
            return this.pendingPartyReward && !this.partyRewardClaimed;
        }

        public boolean partyRewardClaimed() {
            return this.partyRewardClaimed;
        }

        public void markPendingPartyReward() {
            if (!this.partyRewardClaimed) {
                this.pendingPartyReward = true;
            }
        }

        public void markPartyRewardClaimed() {
            this.pendingPartyReward = false;
            this.partyRewardClaimed = true;
        }

        public UUID partyQuestInstanceId() {
            return this.partyQuestInstanceId;
        }

        public void linkPartyQuest(UUID instanceId) {
            this.partyQuestInstanceId = instanceId;
        }

        public String issuerName() {
            return this.issuerName;
        }

        public String issuerProfession() {
            return this.issuerProfession;
        }

        public int issuerLevel() {
            return this.issuerLevel;
        }

        public ResourceKey<Level> issuerDimension() {
            return this.issuerDimension;
        }

        public BlockPos issuerPos() {
            return this.issuerPos;
        }

        public String issuerVillageKey() {
            return this.issuerVillageKey;
        }

        public ResourceKey<Level> targetDimension() {
            return this.targetDimension;
        }

        public BlockPos targetPos() {
            return this.targetPos;
        }

        public String targetObjectiveId() {
            return this.targetObjectiveId;
        }

        public String currentStage() {
            if (!this.currentStage.isBlank()) {
                return this.currentStage;
            }
            return switch (this.state) {
                case ACTIVE -> "started";
                case COMPLETED -> "completed";
                case ABANDONED -> "abandoned";
                case EXPIRED -> "expired";
                case CONSUMED -> "branch_lock".equals(this.consumedReason) ? "branch_locked" : "consumed";
                case NOT_STARTED -> "";
            };
        }

        public int startCount() {
            return this.startCount;
        }

        public int completionCount() {
            return this.completionCount;
        }

        public List<CompletionHistoryEntry> completionHistory() {
            return List.copyOf(this.completionHistory);
        }

        public int abandonCount() {
            return this.abandonCount;
        }

        public String consumedReason() {
            return this.consumedReason;
        }

        public void start(UUID villagerId, ResourceKey<Level> dimension, BlockPos pos, long gameTime) {
            this.state = QuestState.ACTIVE;
            this.startedVillagerId = villagerId;
            this.targetDimension = dimension;
            this.targetPos = pos == null ? null : pos.immutable();
            this.targetObjectiveId = "";
            this.startedGameTime = gameTime;
            this.completedGameTime = 0L;
            this.abandonedGameTime = 0L;
            this.expiredGameTime = 0L;
            this.visitedTarget = false;
            this.hasProof = false;
            this.pendingPartyReward = false;
            this.partyRewardClaimed = false;
            this.partyQuestInstanceId = null;
            this.consumedReason = "";
            this.currentStage = "started";
            this.issuerVillageKey = "";
            this.triggerTimes.clear();
            this.completedObjectives.clear();
            this.objectiveCounters.clear();
            this.choiceHistory.clear();
            this.startCount++;
        }

        public void setIssuer(
                UUID villagerId,
                String displayName,
                String professionId,
                int villagerLevel,
                ResourceKey<Level> dimension,
                BlockPos pos,
                String villageKey) {
            if (villagerId != null) {
                this.startedVillagerId = villagerId;
            }
            this.issuerName = displayName == null ? "" : displayName;
            this.issuerProfession = professionId == null ? "" : professionId;
            this.issuerLevel = Math.max(0, villagerLevel);
            this.issuerDimension = dimension;
            this.issuerPos = pos == null ? null : pos.immutable();
            this.issuerVillageKey = villageKey == null ? "" : villageKey;
        }

        private boolean replaceIssuerVillageKey(String sourceKey, String targetKey) {
            if (!this.issuerVillageKey.equals(sourceKey)) {
                return false;
            }
            this.issuerVillageKey = targetKey;
            return true;
        }

        private boolean replaceVillagerIdentity(UUID sourceVillagerId, UUID targetVillagerId) {
            boolean changed = false;
            if (this.startedVillagerId != null && this.startedVillagerId.equals(sourceVillagerId)) {
                this.startedVillagerId = targetVillagerId;
                changed = true;
            }
            for (int index = 0; index < this.completionHistory.size(); index++) {
                CompletionHistoryEntry current = this.completionHistory.get(index);
                CompletionHistoryEntry updated = current.replacingVillagerIdentity(sourceVillagerId, targetVillagerId);
                if (updated != current) {
                    this.completionHistory.set(index, updated);
                    changed = true;
                }
            }
            return changed;
        }

        public void setTarget(UUID villagerId, ResourceKey<Level> dimension, BlockPos pos, String objectiveId) {
            this.startedVillagerId = villagerId;
            this.targetDimension = dimension;
            this.targetPos = pos == null ? null : pos.immutable();
            this.targetObjectiveId = objectiveId == null ? "" : objectiveId;
        }

        public void complete(long gameTime, boolean consume) {
            this.state = consume ? QuestState.CONSUMED : QuestState.COMPLETED;
            this.completedGameTime = gameTime;
            this.completionCount++;
            this.completionHistory.add(new CompletionHistoryEntry(
                    this.completionCount,
                    this.startedVillagerId,
                    this.startedGameTime,
                    gameTime,
                    this.issuerName,
                    this.issuerProfession,
                    this.issuerLevel,
                    this.issuerDimension,
                    this.issuerPos,
                    this.issuerVillageKey));
            this.consumedReason = consume ? "completion" : "";
            this.currentStage = "completed";
        }

        public void abandon(long gameTime, boolean consume) {
            this.state = consume ? QuestState.CONSUMED : QuestState.ABANDONED;
            this.abandonedGameTime = gameTime;
            this.abandonCount++;
            this.visitedTarget = false;
            this.hasProof = false;
            this.targetDimension = null;
            this.targetPos = null;
            this.targetObjectiveId = "";
            this.completedObjectives.clear();
            this.objectiveCounters.clear();
            this.consumedReason = consume ? "abandonment" : "";
            this.currentStage = "abandoned";
        }

        public void expire(long gameTime, boolean consume) {
            this.state = consume ? QuestState.CONSUMED : QuestState.EXPIRED;
            this.expiredGameTime = gameTime;
            this.visitedTarget = false;
            this.hasProof = false;
            this.targetDimension = null;
            this.targetPos = null;
            this.targetObjectiveId = "";
            this.completedObjectives.clear();
            this.objectiveCounters.clear();
            this.consumedReason = consume ? "expiration" : "";
            this.currentStage = "expired";
        }

        public void consume(String reason) {
            this.state = QuestState.CONSUMED;
            this.visitedTarget = false;
            this.hasProof = false;
            this.targetDimension = null;
            this.targetPos = null;
            this.targetObjectiveId = "";
            this.completedObjectives.clear();
            this.objectiveCounters.clear();
            this.consumedReason = reason == null ? "" : reason;
            this.currentStage = "branch_lock".equals(this.consumedReason) ? "branch_locked" : "consumed";
        }

        public boolean setCurrentStage(String stage) {
            String normalized = stage == null ? "" : stage.trim();
            if (this.currentStage.equals(normalized)) {
                return false;
            }
            this.currentStage = normalized;
            return true;
        }

        public boolean markVisitedTarget() {
            if (this.visitedTarget) {
                return false;
            }
            this.visitedTarget = true;
            return true;
        }

        public boolean markHasProof() {
            if (this.hasProof) {
                return false;
            }
            this.hasProof = true;
            return true;
        }

        public boolean markObjectiveComplete(String objectiveId) {
            if (objectiveId == null || objectiveId.isBlank()) {
                return false;
            }
            return this.completedObjectives.add(objectiveId);
        }

        public boolean objectiveComplete(String objectiveId) {
            return objectiveId != null && this.completedObjectives.contains(objectiveId);
        }

        public int objectiveCounter(String objectiveId) {
            if (objectiveId == null || objectiveId.isBlank()) {
                return 0;
            }
            return this.objectiveCounters.getOrDefault(objectiveId, 0);
        }

        public int addObjectiveCounter(String objectiveId, int amount) {
            if (objectiveId == null || objectiveId.isBlank() || amount == 0) {
                return objectiveCounter(objectiveId);
            }
            int next = Math.max(0, this.objectiveCounters.getOrDefault(objectiveId, 0) + amount);
            this.objectiveCounters.put(objectiveId, next);
            return next;
        }

        public long lastTriggerGameTime(String triggerId) {
            if (triggerId == null || triggerId.isBlank()) {
                return 0L;
            }
            return this.triggerTimes.getOrDefault(triggerId, 0L);
        }

        public void markTriggerUsed(String triggerId, long gameTime) {
            if (triggerId == null || triggerId.isBlank()) {
                return;
            }
            this.triggerTimes.put(triggerId, gameTime);
        }

        public List<ChoiceHistoryEntry> choiceHistory() {
            return List.copyOf(this.choiceHistory);
        }

        public ChoiceHistoryEntry lastChoice() {
            return this.choiceHistory.isEmpty() ? null : this.choiceHistory.getLast();
        }

        public boolean hasChoice(String scenePath, String responseId, String priorStage) {
            String normalizedScene = normalizeChoiceValue(scenePath);
            String normalizedResponse = normalizeChoiceValue(responseId);
            String normalizedStage = normalizeChoiceValue(priorStage);
            return this.choiceHistory.stream().anyMatch(entry ->
                    entry.scenePath().equals(normalizedScene)
                            && entry.responseId().equals(normalizedResponse)
                            && entry.priorStage().equals(normalizedStage));
        }

        public ChoiceHistoryEntry recordChoice(
                String scenePath,
                String responseId,
                String priorStage,
                String nextStage,
                long gameTime) {
            ChoiceHistoryEntry entry = new ChoiceHistoryEntry(
                    scenePath,
                    responseId,
                    priorStage,
                    nextStage,
                    gameTime);
            this.choiceHistory.add(entry);
            return entry;
        }
    }

    public record ChoiceHistoryEntry(
            String scenePath,
            String responseId,
            String priorStage,
            String nextStage,
            long gameTime
    ) {
        public ChoiceHistoryEntry {
            scenePath = normalizeChoiceValue(scenePath);
            responseId = normalizeChoiceValue(responseId);
            priorStage = normalizeChoiceValue(priorStage);
            nextStage = normalizeChoiceValue(nextStage);
        }

        private static ChoiceHistoryEntry load(CompoundTag tag) {
            return new ChoiceHistoryEntry(
                    tag.getString(TAG_SCENE_PATH),
                    tag.getString(TAG_RESPONSE),
                    tag.getString(TAG_PRIOR_STAGE),
                    tag.getString(TAG_NEXT_STAGE),
                    tag.getLong(TAG_GAME_TIME));
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            if (!this.scenePath.isBlank()) {
                tag.putString(TAG_SCENE_PATH, this.scenePath);
            }
            if (!this.responseId.isBlank()) {
                tag.putString(TAG_RESPONSE, this.responseId);
            }
            if (!this.priorStage.isBlank()) {
                tag.putString(TAG_PRIOR_STAGE, this.priorStage);
            }
            if (!this.nextStage.isBlank()) {
                tag.putString(TAG_NEXT_STAGE, this.nextStage);
            }
            tag.putLong(TAG_GAME_TIME, this.gameTime);
            return tag;
        }
    }

    private static String normalizeChoiceValue(String value) {
        return value == null ? "" : value.trim();
    }
}
