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
    private static final String TAG_PLAYER = "Player";
    private static final String TAG_QUEST = "Quest";
    private static final String TAG_STATE = "State";
    private static final String TAG_STARTED_VILLAGER = "StartedVillager";
    private static final String TAG_STARTED_TIME = "StartedGameTime";
    private static final String TAG_COMPLETED_TIME = "CompletedGameTime";
    private static final String TAG_ABANDONED_TIME = "AbandonedGameTime";
    private static final String TAG_VISITED_TARGET = "VisitedTarget";
    private static final String TAG_HAS_PROOF = "HasProof";
    private static final String TAG_TARGET_DIMENSION = "TargetDimension";
    private static final String TAG_TARGET_POS = "TargetPos";
    private static final String TAG_START_COUNT = "StartCount";
    private static final String TAG_COMPLETION_COUNT = "CompletionCount";
    private static final String TAG_ABANDON_COUNT = "AbandonCount";
    private static final String TAG_CONSUMED_REASON = "ConsumedReason";
    private static final String TAG_TRIGGER_TIMES = "TriggerTimes";

    private final Map<UUID, Map<ResourceLocation, QuestProgress>> entries = new HashMap<>();

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

    public enum QuestState {
        NOT_STARTED,
        ACTIVE,
        COMPLETED,
        ABANDONED,
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

    public static class QuestProgress {
        private QuestState state = QuestState.NOT_STARTED;
        private UUID startedVillagerId;
        private long startedGameTime;
        private long completedGameTime;
        private long abandonedGameTime;
        private boolean visitedTarget;
        private boolean hasProof;
        private ResourceKey<Level> targetDimension;
        private BlockPos targetPos;
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
            progress.visitedTarget = tag.getBoolean(TAG_VISITED_TARGET);
            progress.hasProof = tag.getBoolean(TAG_HAS_PROOF);
            progress.startCount = tag.getInt(TAG_START_COUNT);
            progress.completionCount = tag.getInt(TAG_COMPLETION_COUNT);
            progress.abandonCount = tag.getInt(TAG_ABANDON_COUNT);
            progress.consumedReason = tag.getString(TAG_CONSUMED_REASON);
            if (tag.contains(TAG_TRIGGER_TIMES, Tag.TAG_COMPOUND)) {
                CompoundTag triggerTimesTag = tag.getCompound(TAG_TRIGGER_TIMES);
                for (String key : triggerTimesTag.getAllKeys()) {
                    progress.triggerTimes.put(key, triggerTimesTag.getLong(key));
                }
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
            tag.putBoolean(TAG_VISITED_TARGET, this.visitedTarget);
            tag.putBoolean(TAG_HAS_PROOF, this.hasProof);
            tag.putInt(TAG_START_COUNT, this.startCount);
            tag.putInt(TAG_COMPLETION_COUNT, this.completionCount);
            tag.putInt(TAG_ABANDON_COUNT, this.abandonCount);
            tag.putString(TAG_CONSUMED_REASON, this.consumedReason);
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

        public boolean visitedTarget() {
            return this.visitedTarget;
        }

        public boolean hasProof() {
            return this.hasProof;
        }

        public ResourceKey<Level> targetDimension() {
            return this.targetDimension;
        }

        public BlockPos targetPos() {
            return this.targetPos;
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
            this.startedGameTime = gameTime;
            this.completedGameTime = 0L;
            this.abandonedGameTime = 0L;
            this.visitedTarget = false;
            this.hasProof = false;
            this.consumedReason = "";
            this.triggerTimes.clear();
            this.startCount++;
        }

        public void complete(long gameTime, boolean consume) {
            this.state = consume ? QuestState.CONSUMED : QuestState.COMPLETED;
            this.completedGameTime = gameTime;
            this.completionCount++;
            this.consumedReason = consume ? "completion" : "";
        }

        public void abandon(long gameTime, boolean consume) {
            this.state = consume ? QuestState.CONSUMED : QuestState.ABANDONED;
            this.abandonedGameTime = gameTime;
            this.abandonCount++;
            this.visitedTarget = false;
            this.hasProof = false;
            this.targetDimension = null;
            this.targetPos = null;
            this.consumedReason = consume ? "abandonment" : "";
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
