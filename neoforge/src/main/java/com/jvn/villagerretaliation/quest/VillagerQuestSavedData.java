package com.jvn.villagerretaliation.quest;

import com.mojang.logging.LogUtils;
import com.jvn.villagerretaliation.quest.persistence.QuestSaveMigrations;
import com.jvn.villagerretaliation.quest.runtime.QuestActionCheckpoint;
import com.jvn.villagerretaliation.quest.runtime.QuestRewardDelivery;
import com.jvn.villagerretaliation.quest.runtime.QuestStateMachine;
import com.jvn.villagerretaliation.quest.tracking.QuestTrackerLimits;
import com.jvn.villagerretaliation.util.NbtDataUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.slf4j.Logger;

public class VillagerQuestSavedData extends SavedData implements com.jvn.villagerretaliation.quest.runtime.QuestProgressStore<VillagerQuestSavedData.QuestProgress> {
    public static final int CURRENT_DATA_VERSION = 11;
    public static final int MAX_QUEST_RECORDS_PER_PLAYER = 1024;
    public static final int MAX_CHOICE_HISTORY = com.jvn.villagerretaliation.quest.runtime.QuestProgressTypes.MAX_CHOICE_HISTORY;
    public static final int MAX_COMPLETION_HISTORY = com.jvn.villagerretaliation.quest.runtime.QuestProgressTypes.MAX_COMPLETION_HISTORY;
    public static final int MAX_PROVIDER_REBIND_HISTORY = com.jvn.villagerretaliation.quest.runtime.QuestProgressTypes.MAX_PROVIDER_REBIND_HISTORY;
    public static final int MAX_PENDING_TRIGGER_EVENTS = com.jvn.villagerretaliation.quest.runtime.QuestProgressTypes.MAX_PENDING_TRIGGER_EVENTS;
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
    private static final String TAG_RESOLVED_OBJECTIVE_ITEMS = "ResolvedObjectiveItems";
    private static final String TAG_CLAIMED_BONUSES = "ClaimedBonuses";
    private static final String TAG_DEFINITION_REVISION = "DefinitionRevision";
    private static final String TAG_LAST_REVISION_POLICY = "LastRevisionPolicy";
    private static final String TAG_LAST_REVISION_TIME = "LastRevisionGameTime";
    private static final String TAG_OBJECTIVE = "Objective";
    private static final String TAG_ITEM = "Item";
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
    private static final String TAG_PENDING_TRIGGER_EVENTS = "PendingTriggerEvents";
    private static final String TAG_PREVIOUS_PROVIDER = "PreviousProvider";
    private static final String TAG_NEW_PROVIDER = "NewProvider";
    private static final String TAG_REASON = "Reason";

    private final Map<UUID, Map<ResourceLocation, QuestProgress>> entries = new HashMap<>();
    private final Map<UUID, List<ResourceLocation>> trackedQuests = new HashMap<>();
    private CompoundTag futureVersionData;
    private boolean campaignPersistencePending;

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
            LOGGER.warn("Quest save DataVersion {} is newer than supported version {}; preserving the raw save unchanged, so runtime mutations will not be persisted",
                    migration.sourceVersion(), CURRENT_DATA_VERSION);
        }
        VillagerQuestSavedData data = new VillagerQuestSavedData();
        if (migration.futureVersion()) {
            data.futureVersionData = tag.copy();
        }
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
        int pruned = 0;
        for (Map<ResourceLocation, QuestProgress> playerEntries : data.entries.values()) {
            pruned += pruneTerminalQuestRecords(playerEntries, MAX_QUEST_RECORDS_PER_PLAYER);
        }
        if (pruned > 0 && data.futureVersionData == null) {
            data.setDirty();
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (this.futureVersionData != null) {
            return this.futureVersionData.copy();
        }
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

    @Override
    public void changed() { setDirty(); }

    public boolean campaignPersistencePending() { return this.campaignPersistencePending; }

    public boolean futureVersion() { return this.futureVersionData != null; }

    public void requireCampaignPersistence() {
        this.campaignPersistencePending = true;
        setDirty();
    }

    public void persistCampaignProgress(ServerLevel level) throws IOException {
        persistCampaignProgress(level.getServer().getWorldPath(LevelResource.ROOT).resolve("data"), level.registryAccess());
    }

    public void persistCampaignProgress(Path directory, HolderLookup.Provider provider) throws IOException {
        if (this.futureVersionData != null) throw new IOException("The quest save has an unsupported future version.");
        CompoundTag wrapper = new CompoundTag();
        wrapper.put("data", save(new CompoundTag(), provider));
        NbtUtils.addCurrentDataVersion(wrapper);
        IOUtilities.waitUntilIOWorkerComplete();
        Files.createDirectories(directory);
        com.jvn.villagerretaliation.util.AtomicDataFiles.writeCompressed(wrapper, directory.resolve(DATA_NAME + ".dat"));
        this.campaignPersistencePending = false;
        setDirty(false);
    }

    public CompoundTag debugSnapshotQuest(UUID playerId, ResourceLocation questId) {
        if (futureVersion()) throw new IllegalStateException("Quest data uses a newer save format");
        var result = new CompoundTag();
        var progress = get(playerId, questId);
        if (progress != null) result.put("Progress", progress.save());
        result.putInt("TrackedIndex", getTrackedQuests(playerId).indexOf(questId));
        return result;
    }

    public void debugRestoreQuest(UUID playerId, ResourceLocation questId, CompoundTag snapshot) {
        if (futureVersion()) throw new IllegalStateException("Quest data uses a newer save format");
        QuestProgress progress = snapshot.contains("Progress", Tag.TAG_COMPOUND) ? QuestProgress.load(snapshot.getCompound("Progress").copy()) : null;
        if (progress == null) remove(playerId, questId);
        else entries.computeIfAbsent(playerId, ignored -> new HashMap<>()).put(questId, progress);
        var tracked = new ArrayList<>(getTrackedQuests(playerId));
        tracked.remove(questId);
        int index = snapshot.getInt("TrackedIndex");
        if (index >= 0) tracked.add(Math.min(index, tracked.size()), questId);
        if (tracked.isEmpty()) trackedQuests.remove(playerId);
        else trackedQuests.put(playerId, tracked);
        setDirty();
    }

    public QuestProgress get(UUID playerId, ResourceLocation questId) {
        Map<ResourceLocation, QuestProgress> playerEntries = this.entries.get(playerId);
        return playerEntries == null ? null : playerEntries.get(questId);
    }

    public QuestProgress getOrCreate(UUID playerId, ResourceLocation questId) {
        Map<ResourceLocation, QuestProgress> playerEntries =
                this.entries.computeIfAbsent(playerId, ignored -> new HashMap<>());
        if (!playerEntries.containsKey(questId) && playerEntries.size() >= MAX_QUEST_RECORDS_PER_PLAYER) {
            if (pruneTerminalQuestRecords(playerEntries, MAX_QUEST_RECORDS_PER_PLAYER - 1) > 0) {
                setDirty();
            }
        }
        return playerEntries.computeIfAbsent(questId, ignored -> new QuestProgress());
    }

    private static int pruneTerminalQuestRecords(
            Map<ResourceLocation, QuestProgress> playerEntries,
            int targetSize) {
        if (playerEntries == null || playerEntries.size() <= targetSize) {
            return 0;
        }
        List<Map.Entry<ResourceLocation, QuestProgress>> terminalEntries = playerEntries.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().terminal()
                        && !entry.getValue().pendingPartyReward())
                .sorted(Comparator
                        .comparingLong((Map.Entry<ResourceLocation, QuestProgress> entry) ->
                                entry.getValue().lastActivityGameTime())
                        .thenComparing(entry -> entry.getKey().toString()))
                .toList();
        int requested = playerEntries.size() - Math.max(0, targetSize);
        int removed = 0;
        for (Map.Entry<ResourceLocation, QuestProgress> entry : terminalEntries) {
            if (removed >= requested) {
                break;
            }
            if (playerEntries.remove(entry.getKey(), entry.getValue())) {
                removed++;
            }
        }
        return removed;
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

    public boolean hasActiveRun(ResourceLocation quest, UUID run) {
        if (run == null) return false;
        for (var player : this.entries.values()) {
            var progress = player.get(quest);
            if (progress != null && progress.state() == QuestState.ACTIVE && run.equals(progress.questRunId())) return true;
        }
        return false;
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

    public List<QuestEntry> activeEntries(Set<ResourceLocation> questIds) {
        List<QuestEntry> result = new ArrayList<>();
        entries.forEach((player, quests) -> questIds.forEach(quest -> {
            var progress = quests.get(quest);
            if (progress != null && progress.state() == QuestState.ACTIVE) result.add(new QuestEntry(player, quest, progress));
        }));
        return List.copyOf(result);
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

        public CompletionHistoryEntry replacingVillagerIdentity(UUID sourceVillagerId, UUID targetVillagerId) {
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

    public static class QuestProgress extends com.jvn.villagerretaliation.quest.runtime.QuestProgressState {
        private static QuestProgress load(CompoundTag tag) {
            QuestProgress progress = new QuestProgress();
            progress.state = com.jvn.villagerretaliation.quest.runtime.QuestProgressTypes.QuestState.valueOf(QuestState.byName(tag.getString(TAG_STATE)).name());
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
            progress.completionPending = tag.getBoolean("CompletionPending");
            if (tag.contains("Performance")) {
                try {
                    if (!tag.contains("Performance", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid saved performance state");
                    progress.performance = com.jvn.villagerretaliation.quest.runtime.QuestPerformanceState.load(tag.getCompound("Performance"));
                } catch (IllegalArgumentException exception) {
                    progress.preservedPerformance = tag.get("Performance").copy();
                    progress.rewardDeliveryIssue = exception.getMessage();
                }
            }
            if (tag.contains("RewardDelivery")) {
                try {
                    if (!tag.contains("RewardDelivery", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid saved reward plan");
                    progress.rewardDelivery = QuestRewardDelivery.load(tag.getCompound("RewardDelivery"));
                } catch (IllegalArgumentException exception) {
                    progress.preservedRewardDelivery = tag.get("RewardDelivery").copy();
                    progress.rewardDeliveryIssue = exception.getMessage();
                }
            }
            if (tag.hasUUID(TAG_PARTY_QUEST_INSTANCE)) {
                progress.partyQuestInstanceId = tag.getUUID(TAG_PARTY_QUEST_INSTANCE);
            }
            if (tag.hasUUID(TAG_QUEST_RUN_ID)) {
                progress.questRunId = tag.getUUID(TAG_QUEST_RUN_ID);
            }
            progress.issuerProviderType = tag.getString("IssuerProviderType");
            progress.issuerName = tag.getString(TAG_ISSUER_NAME);
            progress.issuerProfession = tag.getString(TAG_ISSUER_PROFESSION);
            progress.issuerLevel = tag.getInt(TAG_ISSUER_LEVEL);
            progress.issuerVillageKey = tag.getString(TAG_ISSUER_VILLAGE_KEY);
            progress.startCount = Math.max(0, tag.getInt(TAG_START_COUNT));
            progress.completionCount = Math.max(0, tag.getInt(TAG_COMPLETION_COUNT));
            progress.abandonCount = Math.max(0, tag.getInt(TAG_ABANDON_COUNT));
            progress.consumedReason = tag.getString(TAG_CONSUMED_REASON);
            progress.targetObjectiveId = tag.getString(TAG_TARGET_OBJECTIVE);
            progress.currentStage = tag.getString(TAG_CURRENT_STAGE);
            var paths = tag.getCompound("ParallelPaths");
            for (String key : paths.getAllKeys())
                progress.parallelPaths.put(key, com.jvn.villagerretaliation.quest.runtime.QuestParallelPath.load(paths.getCompound(key)));
            var timers = tag.getCompound("Timers");
            for (String key : timers.getAllKeys())
                progress.timers.put(key, com.jvn.villagerretaliation.quest.runtime.QuestTimerState.load(timers.getCompound(key)));
            progress.pendingStageTransition = tag.getString("PendingStageTransition");
            progress.pendingStageEntry = tag.getString("PendingStageEntry");
            CompoundTag stageActions = tag.getCompound("StageActions");
            for (String key : stageActions.getAllKeys()) {
                progress.stageActions.put(key, QuestActionCheckpoint.load(stageActions.getCompound(key)));
            }
            progress.completedObjectives.addAll(NbtDataUtil.readStringSet(tag, TAG_COMPLETED_OBJECTIVES));
            progress.claimedBonuses.addAll(NbtDataUtil.readStringSet(tag, TAG_CLAIMED_BONUSES));
            progress.definitionRevision = Math.max(0, tag.getInt(TAG_DEFINITION_REVISION));
            progress.campaignId = tag.getString("Campaign");
            progress.campaignEntryStage = tag.getString("CampaignEntryStage");
            progress.campaignWaitingQuest = tag.getString("CampaignWaitingQuest");
            progress.campaignStageRevision = tag.getLong("CampaignStageRevision");
            progress.campaignAcceptance = com.jvn.villagerretaliation.campaign.CampaignQuestAcceptance.load(tag.getCompound("CampaignAcceptance"));
            progress.campaignLegacyEntry = tag.getBoolean("CampaignLegacyEntry");
            for (Tag raw : tag.getList("CampaignCatchUpOperations", Tag.TAG_COMPOUND)) {
                if (raw instanceof CompoundTag receipt && receipt.hasUUID("Operation")) {
                    progress.campaignCatchUpOperations.add(receipt.getUUID("Operation"));
                }
            }
            progress.lastRevisionPolicy = tag.getString(TAG_LAST_REVISION_POLICY);
            progress.lastRevisionGameTime = tag.contains(TAG_LAST_REVISION_TIME, Tag.TAG_LONG)
                    ? tag.getLong(TAG_LAST_REVISION_TIME) : -1L;
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
                        progress.objectiveCounters.put(objectiveId, Math.max(0, counterTag.getInt(TAG_COUNT)));
                    }
                }
            }
            if (tag.contains(TAG_RESOLVED_OBJECTIVE_ITEMS, Tag.TAG_LIST)) {
                for (Tag rawSelection : tag.getList(TAG_RESOLVED_OBJECTIVE_ITEMS, Tag.TAG_COMPOUND)) {
                    if (!(rawSelection instanceof CompoundTag selectionTag)
                            || !selectionTag.contains(TAG_OBJECTIVE, Tag.TAG_STRING)
                            || !selectionTag.contains(TAG_ITEM, Tag.TAG_STRING)) {
                        continue;
                    }
                    String objectiveId = selectionTag.getString(TAG_OBJECTIVE);
                    ResourceLocation itemId = ResourceLocation.tryParse(selectionTag.getString(TAG_ITEM));
                    if (!objectiveId.isBlank() && itemId != null) {
                        progress.resolvedObjectiveItems.put(objectiveId, itemId);
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
                        addBounded(progress.choiceHistory, sharedChoice(ChoiceHistoryEntry.load(choiceTag)),
                                MAX_CHOICE_HISTORY);
                    }
                }
            }
            if (tag.contains(TAG_COMPLETION_HISTORY, Tag.TAG_LIST)) {
                ListTag historyTag = tag.getList(TAG_COMPLETION_HISTORY, Tag.TAG_COMPOUND);
                for (Tag rawCompletion : historyTag) {
                    if (rawCompletion instanceof CompoundTag completionTag) {
                        addBounded(progress.completionHistory, sharedCompletion(CompletionHistoryEntry.load(completionTag)),
                                MAX_COMPLETION_HISTORY);
                    }
                }
            }
            if (tag.contains(TAG_PROVIDER_REBIND_HISTORY, Tag.TAG_LIST)) {
                for (Tag rawRebind : tag.getList(TAG_PROVIDER_REBIND_HISTORY, Tag.TAG_COMPOUND)) {
                    if (rawRebind instanceof CompoundTag rebindTag) {
                        addBounded(progress.providerRebindHistory, sharedRebind(ProviderRebindHistoryEntry.load(rebindTag)),
                                MAX_PROVIDER_REBIND_HISTORY);
                    }
                }
            }
            for (String eventId : NbtDataUtil.readStringSet(tag, TAG_PENDING_LIFECYCLE_EVENTS)) {
                QuestDefinition.TriggerEvent event = QuestDefinition.TriggerEvent.bySerializedName(eventId);
                if (isDeferredLifecycleEvent(event)) {
                    progress.pendingLifecycleEvents.add(event);
                }
            }
            for (Tag rawPending : tag.getList(TAG_PENDING_TRIGGER_EVENTS, Tag.TAG_COMPOUND)) {
                if (rawPending instanceof CompoundTag pendingTag) {
                    addBounded(progress.pendingTriggerEvents, QuestTriggerContext.load(pendingTag),
                            MAX_PENDING_TRIGGER_EVENTS);
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
            if (!this.issuerProviderType.isBlank()) tag.putString("IssuerProviderType", this.issuerProviderType);
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
            tag.putBoolean("CompletionPending", this.completionPending);
            tag.put("Performance", this.preservedPerformance == null ? this.performance.save() : this.preservedPerformance.copy());
            if (this.preservedRewardDelivery != null) tag.put("RewardDelivery", this.preservedRewardDelivery.copy());
            else if (this.rewardDelivery != null) tag.put("RewardDelivery", this.rewardDelivery.save());
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
            if (!this.parallelPaths.isEmpty()) {
                var paths = new CompoundTag();
                this.parallelPaths.forEach((key, value) -> paths.put(key, value.save()));
                tag.put("ParallelPaths", paths);
            }
            if (!this.timers.isEmpty()) {
                var timers = new CompoundTag();
                this.timers.forEach((key, value) -> timers.put(key, value.save()));
                tag.put("Timers", timers);
            }
            }
            if (!this.stageActions.isEmpty()) {
                CompoundTag actions = new CompoundTag();
                this.stageActions.forEach((key, value) -> actions.put(key, value.save()));
                tag.put("StageActions", actions);
            }
            if (!this.pendingStageTransition.isBlank()) tag.putString("PendingStageTransition", this.pendingStageTransition);
            if (!this.pendingStageEntry.isBlank()) tag.putString("PendingStageEntry", this.pendingStageEntry);
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
            if (!this.resolvedObjectiveItems.isEmpty()) {
                ListTag selectionsTag = new ListTag();
                this.resolvedObjectiveItems.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> {
                            CompoundTag selectionTag = new CompoundTag();
                            selectionTag.putString(TAG_OBJECTIVE, entry.getKey());
                            selectionTag.putString(TAG_ITEM, entry.getValue().toString());
                            selectionsTag.add(selectionTag);
                        });
                tag.put(TAG_RESOLVED_OBJECTIVE_ITEMS, selectionsTag);
            }
            if (!this.claimedBonuses.isEmpty()) {
                tag.put(TAG_CLAIMED_BONUSES, NbtDataUtil.stringList(this.claimedBonuses));
            }
            if (!this.campaignId.isBlank()) tag.putString("Campaign", this.campaignId);
            if (!this.campaignEntryStage.isBlank()) tag.putString("CampaignEntryStage", this.campaignEntryStage);
            if (!this.campaignWaitingQuest.isBlank()) tag.putString("CampaignWaitingQuest", this.campaignWaitingQuest);
            if (!this.campaignId.isBlank()) tag.putLong("CampaignStageRevision", this.campaignStageRevision);
            if (this.campaignAcceptance != null) tag.put("CampaignAcceptance", this.campaignAcceptance.save());
            if (this.campaignLegacyEntry) tag.putBoolean("CampaignLegacyEntry", true);
            if (!this.campaignCatchUpOperations.isEmpty()) {
                var receipts = new ListTag();
                for (UUID operation : this.campaignCatchUpOperations) {
                    var receipt = new CompoundTag();
                    receipt.putUUID("Operation", operation);
                    receipts.add(receipt);
                }
                tag.put("CampaignCatchUpOperations", receipts);
            }
            if (this.definitionRevision > 0) {
                tag.putInt(TAG_DEFINITION_REVISION, this.definitionRevision);
            }
            if (!this.lastRevisionPolicy.isBlank()) {
                tag.putString(TAG_LAST_REVISION_POLICY, this.lastRevisionPolicy);
            }
            if (this.lastRevisionGameTime >= 0L) {
                tag.putLong(TAG_LAST_REVISION_TIME, this.lastRevisionGameTime);
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
                for (ChoiceHistoryEntry entry : choiceHistory()) {
                    historyTag.add(entry.save());
                }
                tag.put(TAG_CHOICE_HISTORY, historyTag);
            }
            if (!this.completionHistory.isEmpty()) {
                ListTag historyTag = new ListTag();
                for (CompletionHistoryEntry entry : completionHistory()) {
                    historyTag.add(entry.save());
                }
                tag.put(TAG_COMPLETION_HISTORY, historyTag);
            }
            if (!this.providerRebindHistory.isEmpty()) {
                ListTag historyTag = new ListTag();
                providerRebindHistory().stream().map(ProviderRebindHistoryEntry::save).forEach(historyTag::add);
                tag.put(TAG_PROVIDER_REBIND_HISTORY, historyTag);
            }
            if (!this.pendingLifecycleEvents.isEmpty()) {
                tag.put(TAG_PENDING_LIFECYCLE_EVENTS, NbtDataUtil.stringList(
                        this.pendingLifecycleEvents.stream().map(QuestTriggerRegistry::canonicalEventId).toList()));
            }
            if (!this.pendingTriggerEvents.isEmpty()) {
                ListTag pendingTag = new ListTag();
                this.pendingTriggerEvents.stream().map(QuestTriggerContext::save).forEach(pendingTag::add);
                tag.put(TAG_PENDING_TRIGGER_EVENTS, pendingTag);
            }
            if (this.targetDimension != null) {
                NbtDataUtil.putResourceLocation(tag, TAG_TARGET_DIMENSION, this.targetDimension.location());
            }
            NbtDataUtil.putBlockPos(tag, TAG_TARGET_POS, this.targetPos);
            return tag;
        }

        public CompoundTag campaignRequestSnapshot() { return save(); }
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
