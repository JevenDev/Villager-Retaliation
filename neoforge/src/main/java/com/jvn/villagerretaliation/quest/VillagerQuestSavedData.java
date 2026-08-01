package com.jvn.villagerretaliation.quest;

import com.mojang.logging.LogUtils;
import com.jvn.villagerretaliation.quest.persistence.QuestSaveMigrations;
import com.jvn.villagerretaliation.quest.runtime.QuestStateMachine;
import com.jvn.villagerretaliation.quest.provider.QuestProviderBinding;
import com.jvn.villagerretaliation.quest.tracking.QuestTrackerLimits;
import com.jvn.villagerretaliation.util.NbtDataUtil;
import com.jvn.villagerretaliation.util.WorldLocation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
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
import org.slf4j.Logger;

public class VillagerQuestSavedData extends SavedData {
    public static final int CURRENT_DATA_VERSION = 3;
    private static final Logger LOGGER = LogUtils.getLogger();
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
    private static final String TAG_FAILED_TIME = "FailedGameTime";
    private static final String TAG_FAILURE_REASON = "FailureReason";
    private static final String TAG_VISITED_TARGET = "VisitedTarget";
    private static final String TAG_HAS_PROOF = "HasProof";
    private static final String TAG_PENDING_PARTY_REWARD = "PendingPartyReward";
    private static final String TAG_PARTY_REWARD_CLAIMED = "PartyRewardClaimed";
    private static final String TAG_PARTY_QUEST_INSTANCE = "PartyQuestInstance";
    private static final String TAG_QUEST_RUN_ID = "QuestRunId";
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
    private static final String TAG_PROVIDER_REBIND_HISTORY = "ProviderRebindHistory";
    private static final String TAG_PENDING_LIFECYCLE_EVENTS = "PendingLifecycleEvents";
    private static final String TAG_PREVIOUS_PROVIDER = "PreviousProvider";
    private static final String TAG_NEW_PROVIDER = "NewProvider";
    private static final String TAG_REASON = "Reason";

    private final Map<UUID, Map<ResourceLocation, QuestProgress>> entries = new HashMap<>();
    private final Map<UUID, List<ResourceLocation>> trackedQuests = new HashMap<>();

    public static VillagerQuestSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VillagerQuestSavedData::new, VillagerQuestSavedData::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static VillagerQuestSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        QuestSaveMigrations.MigrationResult migration = QuestSaveMigrations.migrate(tag, CURRENT_DATA_VERSION);
        tag = migration.data();
        if (migration.futureVersion()) {
            LOGGER.warn("Quest save DataVersion {} is newer than supported version {}; preserving readable fields",
                    migration.sourceVersion(), CURRENT_DATA_VERSION);
        }
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
            UUID playerId = entryTag.getUUID(TAG_PLAYER);
            progress.ensureRunId(playerId, questId);
            data.entries.computeIfAbsent(playerId, ignored -> new HashMap<>()).put(questId, progress);
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
        tag.putInt(QuestSaveMigrations.DATA_VERSION_TAG, CURRENT_DATA_VERSION);
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

    public List<QuestEntry> pendingPartyRewardsStartedBy(UUID villagerId) {
        if (villagerId == null || this.entries.isEmpty()) {
            return List.of();
        }
        List<QuestEntry> pending = new ArrayList<>();
        for (Map.Entry<UUID, Map<ResourceLocation, QuestProgress>> playerEntry : this.entries.entrySet()) {
            for (Map.Entry<ResourceLocation, QuestProgress> questEntry : playerEntry.getValue().entrySet()) {
                QuestProgress progress = questEntry.getValue();
                if (progress.pendingPartyReward() && villagerId.equals(progress.startedVillagerId())) {
                    pending.add(new QuestEntry(playerEntry.getKey(), questEntry.getKey(), progress));
                }
            }
        }
        return List.copyOf(pending);
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
        FAILED,
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

    public record ProviderRebindHistoryEntry(
            UUID previousProviderId,
            UUID newProviderId,
            String previousName,
            String previousProfession,
            int previousLevel,
            ResourceKey<Level> previousDimension,
            BlockPos previousPos,
            String previousVillageKey,
            long gameTime,
            String reason) {
        public ProviderRebindHistoryEntry {
            previousName = previousName == null ? "" : previousName;
            previousProfession = previousProfession == null ? "" : previousProfession;
            previousLevel = Math.max(0, previousLevel);
            previousPos = previousPos == null ? null : previousPos.immutable();
            previousVillageKey = previousVillageKey == null ? "" : previousVillageKey;
            reason = QuestStateMachine.normalizeCode(reason, "operator_rebind");
        }

        private static ProviderRebindHistoryEntry load(CompoundTag tag) {
            ResourceKey<Level> dimension = NbtDataUtil.readResourceLocation(tag, TAG_ISSUER_DIMENSION)
                    .map(id -> ResourceKey.create(Registries.DIMENSION, id)).orElse(null);
            return new ProviderRebindHistoryEntry(
                    tag.hasUUID(TAG_PREVIOUS_PROVIDER) ? tag.getUUID(TAG_PREVIOUS_PROVIDER) : null,
                    tag.hasUUID(TAG_NEW_PROVIDER) ? tag.getUUID(TAG_NEW_PROVIDER) : null,
                    tag.getString(TAG_ISSUER_NAME),
                    tag.getString(TAG_ISSUER_PROFESSION),
                    tag.getInt(TAG_ISSUER_LEVEL),
                    dimension,
                    NbtDataUtil.readBlockPos(tag, TAG_ISSUER_POS).orElse(null),
                    tag.getString(TAG_ISSUER_VILLAGE_KEY),
                    tag.getLong(TAG_GAME_TIME),
                    tag.getString(TAG_REASON));
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            if (this.previousProviderId != null) tag.putUUID(TAG_PREVIOUS_PROVIDER, this.previousProviderId);
            if (this.newProviderId != null) tag.putUUID(TAG_NEW_PROVIDER, this.newProviderId);
            if (!this.previousName.isBlank()) tag.putString(TAG_ISSUER_NAME, this.previousName);
            if (!this.previousProfession.isBlank()) tag.putString(TAG_ISSUER_PROFESSION, this.previousProfession);
            tag.putInt(TAG_ISSUER_LEVEL, this.previousLevel);
            if (this.previousDimension != null) {
                NbtDataUtil.putResourceLocation(tag, TAG_ISSUER_DIMENSION, this.previousDimension.location());
            }
            NbtDataUtil.putBlockPos(tag, TAG_ISSUER_POS, this.previousPos);
            if (!this.previousVillageKey.isBlank()) tag.putString(TAG_ISSUER_VILLAGE_KEY, this.previousVillageKey);
            tag.putLong(TAG_GAME_TIME, this.gameTime);
            tag.putString(TAG_REASON, this.reason);
            return tag;
        }
    }

    public static class QuestProgress {
        private QuestState state = QuestState.NOT_STARTED;
        private UUID startedVillagerId;
        private long startedGameTime = -1L;
        private long completedGameTime = -1L;
        private long abandonedGameTime = -1L;
        private long expiredGameTime = -1L;
        private long failedGameTime = -1L;
        private String failureReason = "";
        private boolean visitedTarget;
        private boolean hasProof;
        private boolean pendingPartyReward;
        private boolean partyRewardClaimed;
        private UUID partyQuestInstanceId;
        private UUID questRunId;
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
        private final List<ProviderRebindHistoryEntry> providerRebindHistory = new ArrayList<>();
        private final Set<QuestDefinition.TriggerEvent> pendingLifecycleEvents = new LinkedHashSet<>();

        private static QuestProgress load(CompoundTag tag) {
            QuestProgress progress = new QuestProgress();
            progress.state = QuestState.byName(tag.getString(TAG_STATE));
            if (tag.hasUUID(TAG_STARTED_VILLAGER)) {
                progress.startedVillagerId = tag.getUUID(TAG_STARTED_VILLAGER);
            }
            progress.startedGameTime = tag.contains(TAG_STARTED_TIME, Tag.TAG_LONG)
                    ? tag.getLong(TAG_STARTED_TIME) : -1L;
            progress.completedGameTime = tag.contains(TAG_COMPLETED_TIME, Tag.TAG_LONG)
                    ? tag.getLong(TAG_COMPLETED_TIME) : -1L;
            progress.abandonedGameTime = tag.contains(TAG_ABANDONED_TIME, Tag.TAG_LONG)
                    ? tag.getLong(TAG_ABANDONED_TIME) : -1L;
            progress.expiredGameTime = tag.contains(TAG_EXPIRED_TIME, Tag.TAG_LONG)
                    ? tag.getLong(TAG_EXPIRED_TIME) : -1L;
            progress.failedGameTime = tag.contains(TAG_FAILED_TIME, Tag.TAG_LONG)
                    ? tag.getLong(TAG_FAILED_TIME) : -1L;
            progress.failureReason = QuestStateMachine.normalizeCode(tag.getString(TAG_FAILURE_REASON), "");
            progress.visitedTarget = tag.getBoolean(TAG_VISITED_TARGET);
            progress.hasProof = tag.getBoolean(TAG_HAS_PROOF);
            progress.pendingPartyReward = tag.getBoolean(TAG_PENDING_PARTY_REWARD);
            progress.partyRewardClaimed = tag.getBoolean(TAG_PARTY_REWARD_CLAIMED);
            if (tag.hasUUID(TAG_PARTY_QUEST_INSTANCE)) {
                progress.partyQuestInstanceId = tag.getUUID(TAG_PARTY_QUEST_INSTANCE);
            }
            if (tag.hasUUID(TAG_QUEST_RUN_ID)) {
                progress.questRunId = tag.getUUID(TAG_QUEST_RUN_ID);
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
            if (tag.contains(TAG_PROVIDER_REBIND_HISTORY, Tag.TAG_LIST)) {
                for (Tag rawRebind : tag.getList(TAG_PROVIDER_REBIND_HISTORY, Tag.TAG_COMPOUND)) {
                    if (rawRebind instanceof CompoundTag rebindTag) {
                        progress.providerRebindHistory.add(ProviderRebindHistoryEntry.load(rebindTag));
                    }
                }
            }
            for (String eventId : NbtDataUtil.readStringSet(tag, TAG_PENDING_LIFECYCLE_EVENTS)) {
                QuestDefinition.TriggerEvent event = QuestDefinition.TriggerEvent.bySerializedName(eventId);
                if (isDeferredLifecycleEvent(event)) {
                    progress.pendingLifecycleEvents.add(event);
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
            tag.putLong(TAG_FAILED_TIME, this.failedGameTime);
            if (!this.failureReason.isBlank()) {
                tag.putString(TAG_FAILURE_REASON, this.failureReason);
            }
            tag.putBoolean(TAG_VISITED_TARGET, this.visitedTarget);
            tag.putBoolean(TAG_HAS_PROOF, this.hasProof);
            tag.putBoolean(TAG_PENDING_PARTY_REWARD, this.pendingPartyReward);
            tag.putBoolean(TAG_PARTY_REWARD_CLAIMED, this.partyRewardClaimed);
            if (this.partyQuestInstanceId != null) {
                tag.putUUID(TAG_PARTY_QUEST_INSTANCE, this.partyQuestInstanceId);
            }
            if (this.questRunId != null) {
                tag.putUUID(TAG_QUEST_RUN_ID, this.questRunId);
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
            if (!this.providerRebindHistory.isEmpty()) {
                ListTag historyTag = new ListTag();
                this.providerRebindHistory.stream().map(ProviderRebindHistoryEntry::save).forEach(historyTag::add);
                tag.put(TAG_PROVIDER_REBIND_HISTORY, historyTag);
            }
            if (!this.pendingLifecycleEvents.isEmpty()) {
                tag.put(TAG_PENDING_LIFECYCLE_EVENTS, NbtDataUtil.stringList(
                        this.pendingLifecycleEvents.stream().map(QuestTriggerRegistry::canonicalEventId).toList()));
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

        public long failedGameTime() {
            return this.failedGameTime;
        }

        public String failureReason() {
            return this.failureReason;
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

        public UUID questRunId() {
            return this.questRunId;
        }

        public UUID beginRun(UUID playerId, ResourceLocation questId, UUID sharedRunId) {
            if (playerId == null || questId == null) {
                throw new IllegalArgumentException("quest run identity requires a player and quest id");
            }
            UUID definitiveRunId = sharedRunId == null
                    ? deterministicRunId(playerId, questId, Math.max(1, this.startCount))
                    : sharedRunId;
            if (this.questRunId != null && !this.questRunId.equals(definitiveRunId)) {
                throw new IllegalStateException("quest run identity cannot change during an active run");
            }
            this.questRunId = definitiveRunId;
            return this.questRunId;
        }

        private void ensureRunId(UUID playerId, ResourceLocation questId) {
            if (this.questRunId == null && this.state == QuestState.ACTIVE) {
                this.questRunId = this.partyQuestInstanceId != null
                        ? this.partyQuestInstanceId
                        : deterministicRunId(playerId, questId, Math.max(1, this.startCount));
            }
        }

        public static UUID deterministicRunId(UUID playerId, ResourceLocation questId, int runNumber) {
            if (playerId == null || questId == null) {
                throw new IllegalArgumentException("quest run identity requires a player and quest id");
            }
            return UUID.nameUUIDFromBytes((playerId + "|quest|" + questId + "|run|" + Math.max(1, runNumber))
                    .getBytes(StandardCharsets.UTF_8));
        }

        public boolean linkPartyQuest(UUID instanceId) {
            if (instanceId != null && this.questRunId != null && !instanceId.equals(this.questRunId)) {
                return false;
            }
            this.partyQuestInstanceId = instanceId;
            if (instanceId != null && this.questRunId == null) this.questRunId = instanceId;
            return true;
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

        public WorldLocation issuerLocation() {
            return WorldLocation.of(this.issuerDimension, this.issuerPos);
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

        public WorldLocation targetLocation() {
            return WorldLocation.of(this.targetDimension, this.targetPos);
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
                case FAILED -> "failed";
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

        public List<ProviderRebindHistoryEntry> providerRebindHistory() {
            return List.copyOf(this.providerRebindHistory);
        }

        public Set<QuestDefinition.TriggerEvent> pendingLifecycleEvents() {
            return Set.copyOf(this.pendingLifecycleEvents);
        }

        public boolean hasPendingLifecycleEvents() {
            return !this.pendingLifecycleEvents.isEmpty();
        }

        public boolean deferLifecycleEvent(QuestDefinition.TriggerEvent event) {
            return isDeferredLifecycleEvent(event) && this.pendingLifecycleEvents.add(event);
        }

        public boolean resolveLifecycleEvent(QuestDefinition.TriggerEvent event) {
            return event != null && this.pendingLifecycleEvents.remove(event);
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
            this.completedGameTime = -1L;
            this.abandonedGameTime = -1L;
            this.expiredGameTime = -1L;
            this.failedGameTime = -1L;
            this.failureReason = "";
            this.visitedTarget = false;
            this.hasProof = false;
            this.pendingPartyReward = false;
            this.partyRewardClaimed = false;
            this.partyQuestInstanceId = null;
            this.questRunId = null;
            this.consumedReason = "";
            this.currentStage = "started";
            this.issuerVillageKey = "";
            this.triggerTimes.clear();
            this.completedObjectives.clear();
            this.objectiveCounters.clear();
            this.choiceHistory.clear();
            this.pendingLifecycleEvents.clear();
            this.startCount++;
        }

        private static boolean isDeferredLifecycleEvent(QuestDefinition.TriggerEvent event) {
            return event == QuestDefinition.TriggerEvent.COMPLETED
                    || event == QuestDefinition.TriggerEvent.FAILED
                    || event == QuestDefinition.TriggerEvent.ABANDONED
                    || event == QuestDefinition.TriggerEvent.EXPIRED;
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

        public void rebindProvider(QuestProviderBinding binding, long gameTime, String reason) {
            if (binding == null || binding.providerId() == null) {
                throw new IllegalArgumentException("provider binding must have an id");
            }
            this.providerRebindHistory.add(new ProviderRebindHistoryEntry(
                    this.startedVillagerId,
                    binding.providerId(),
                    this.issuerName,
                    this.issuerProfession,
                    this.issuerLevel,
                    this.issuerDimension,
                    this.issuerPos,
                    this.issuerVillageKey,
                    gameTime,
                    reason));
            setIssuer(
                    binding.providerId(),
                    binding.displayName(),
                    binding.professionId() == null ? "" : binding.professionId().toString(),
                    binding.level(),
                    binding.dimension(),
                    binding.pos(),
                    binding.villageKey());
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

        public void fail(long gameTime, String reason) {
            this.state = QuestState.FAILED;
            this.failedGameTime = gameTime;
            this.failureReason = QuestStateMachine.normalizeCode(reason, "unspecified_failure");
            this.visitedTarget = false;
            this.hasProof = false;
            this.targetDimension = null;
            this.targetPos = null;
            this.targetObjectiveId = "";
            this.completedObjectives.clear();
            this.objectiveCounters.clear();
            this.consumedReason = "";
            this.currentStage = "failed";
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
                return -1L;
            }
            return this.triggerTimes.getOrDefault(triggerId, -1L);
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
