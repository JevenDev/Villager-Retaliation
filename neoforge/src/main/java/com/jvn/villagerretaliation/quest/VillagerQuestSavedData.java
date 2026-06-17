package com.jvn.villagerretaliation.quest;

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
    private static final String TAG_ABANDONED_TIME = "AbandonedGameTime";
    private static final String TAG_EXPIRED_TIME = "ExpiredGameTime";
    private static final String TAG_VISITED_TARGET = "VisitedTarget";
    private static final String TAG_HAS_PROOF = "HasProof";
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

    private final Map<UUID, Map<ResourceLocation, QuestProgress>> entries = new HashMap<>();
    private final Map<UUID, ResourceLocation> trackedQuests = new HashMap<>();

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
                data.trackedQuests.put(entryTag.getUUID(TAG_PLAYER), questId);
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
        for (Map.Entry<UUID, ResourceLocation> entry : this.trackedQuests.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID(TAG_PLAYER, entry.getKey());
            entryTag.putString(TAG_QUEST, entry.getValue().toString());
            trackedTag.add(entryTag);
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
        return this.trackedQuests.get(playerId);
    }

    public void setTrackedQuest(UUID playerId, ResourceLocation questId) {
        if (playerId == null || questId == null) {
            return;
        }
        this.trackedQuests.put(playerId, questId);
        setDirty();
    }

    public void clearTrackedQuest(UUID playerId) {
        if (playerId != null && this.trackedQuests.remove(playerId) != null) {
            setDirty();
        }
    }

    public boolean toggleTrackedQuest(UUID playerId, ResourceLocation questId) {
        if (playerId == null || questId == null) {
            return false;
        }
        if (questId.equals(this.trackedQuests.get(playerId))) {
            this.trackedQuests.remove(playerId);
            setDirty();
            return false;
        }
        this.trackedQuests.put(playerId, questId);
        setDirty();
        return true;
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

    public static class QuestProgress {
        private QuestState state = QuestState.NOT_STARTED;
        private UUID startedVillagerId;
        private long startedGameTime;
        private long completedGameTime;
        private long abandonedGameTime;
        private long expiredGameTime;
        private boolean visitedTarget;
        private boolean hasProof;
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
            if (tag.contains(TAG_COMPLETED_OBJECTIVES, Tag.TAG_LIST)) {
                ListTag objectivesTag = tag.getList(TAG_COMPLETED_OBJECTIVES, Tag.TAG_STRING);
                for (Tag rawObjective : objectivesTag) {
                    String objectiveId = rawObjective.getAsString();
                    if (!objectiveId.isBlank()) {
                        progress.completedObjectives.add(objectiveId);
                    }
                }
            }
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
            ResourceLocation issuerDimensionId = ResourceLocation.tryParse(tag.getString(TAG_ISSUER_DIMENSION));
            if (issuerDimensionId != null) {
                progress.issuerDimension = ResourceKey.create(Registries.DIMENSION, issuerDimensionId);
            }
            if (tag.contains(TAG_ISSUER_POS, Tag.TAG_COMPOUND)) {
                CompoundTag posTag = tag.getCompound(TAG_ISSUER_POS);
                progress.issuerPos = new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z"));
            }
            ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString(TAG_TARGET_DIMENSION));
            if (dimensionId != null) {
                progress.targetDimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
            }
            if (tag.contains(TAG_TARGET_POS, Tag.TAG_COMPOUND)) {
                CompoundTag posTag = tag.getCompound(TAG_TARGET_POS);
                progress.targetPos = new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z"));
            }
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
                tag.putString(TAG_ISSUER_DIMENSION, this.issuerDimension.location().toString());
            }
            if (this.issuerPos != null) {
                CompoundTag posTag = new CompoundTag();
                posTag.putInt("X", this.issuerPos.getX());
                posTag.putInt("Y", this.issuerPos.getY());
                posTag.putInt("Z", this.issuerPos.getZ());
                tag.put(TAG_ISSUER_POS, posTag);
            }
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
                ListTag objectivesTag = new ListTag();
                for (String objectiveId : this.completedObjectives) {
                    objectivesTag.add(net.minecraft.nbt.StringTag.valueOf(objectiveId));
                }
                tag.put(TAG_COMPLETED_OBJECTIVES, objectivesTag);
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
            if (this.targetDimension != null) {
                tag.putString(TAG_TARGET_DIMENSION, this.targetDimension.location().toString());
            }
            if (this.targetPos != null) {
                CompoundTag posTag = new CompoundTag();
                posTag.putInt("X", this.targetPos.getX());
                posTag.putInt("Y", this.targetPos.getY());
                posTag.putInt("Z", this.targetPos.getZ());
                tag.put(TAG_TARGET_POS, posTag);
            }
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
            this.consumedReason = "";
            this.currentStage = "started";
            this.issuerVillageKey = "";
            this.triggerTimes.clear();
            this.completedObjectives.clear();
            this.objectiveCounters.clear();
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
    }
}
