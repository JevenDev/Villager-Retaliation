package com.jvn.villagerretaliation.party;

import com.jvn.villagerretaliation.util.NbtDataUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public final class PartySharedQuestRecord {
    private static final String TAG_INSTANCE = "Instance";
    private static final String TAG_QUEST = "Quest";
    private static final String TAG_SOURCE_VILLAGER = "SourceVillager";
    private static final String TAG_CREATED_GAME_TIME = "CreatedGameTime";
    private static final String TAG_ENROLLMENTS = "Enrollments";
    private static final String TAG_PLAYER = "Player";
    private static final String TAG_PENDING_START = "PendingStart";
    private static final String TAG_PENDING_REWARD = "PendingReward";
    private static final String TAG_REWARD_CLAIMED = "RewardClaimed";
    private static final String TAG_COUNTERS = "Counters";
    private static final String TAG_OBJECTIVE = "Objective";
    private static final String TAG_COUNT = "Count";
    private static final String TAG_COMPLETED_OBJECTIVES = "CompletedObjectives";
    private static final String TAG_PROCESSED_DEATHS = "ProcessedDeaths";
    private static final String TAG_COMPLETED = "Completed";
    private static final int MAX_PROCESSED_DEATHS = 512;

    private final UUID instanceId;
    private final ResourceLocation questId;
    private final UUID sourceVillagerId;
    private final long createdGameTime;
    private final Map<UUID, Enrollment> enrollments;
    private final Map<String, Integer> objectiveCounters;
    private final Set<String> completedObjectives;
    private final LinkedHashSet<String> processedDeaths;
    private boolean completed;

    public PartySharedQuestRecord(ResourceLocation questId, UUID sourceVillagerId, long createdGameTime) {
        this(UUID.randomUUID(), questId, sourceVillagerId, createdGameTime,
                new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashSet<>(), new LinkedHashSet<>(), false);
    }

    private PartySharedQuestRecord(
            UUID instanceId,
            ResourceLocation questId,
            UUID sourceVillagerId,
            long createdGameTime,
            Map<UUID, Enrollment> enrollments,
            Map<String, Integer> objectiveCounters,
            Set<String> completedObjectives,
            LinkedHashSet<String> processedDeaths,
            boolean completed) {
        this.instanceId = instanceId;
        this.questId = questId;
        this.sourceVillagerId = sourceVillagerId;
        this.createdGameTime = Math.max(0L, createdGameTime);
        this.enrollments = enrollments;
        this.objectiveCounters = objectiveCounters;
        this.completedObjectives = completedObjectives;
        this.processedDeaths = processedDeaths;
        this.completed = completed;
    }

    public UUID instanceId() {
        return this.instanceId;
    }

    public ResourceLocation questId() {
        return this.questId;
    }

    public UUID sourceVillagerId() {
        return this.sourceVillagerId;
    }

    public long createdGameTime() {
        return this.createdGameTime;
    }

    public Map<UUID, Enrollment> enrollments() {
        return Collections.unmodifiableMap(this.enrollments);
    }

    public boolean linked(UUID playerId) {
        Enrollment enrollment = this.enrollments.get(playerId);
        return enrollment != null && !enrollment.pendingStart();
    }

    public Enrollment enrollment(UUID playerId) {
        return this.enrollments.get(playerId);
    }

    public void enroll(UUID playerId, boolean pendingStart) {
        if (playerId == null) {
            return;
        }
        Enrollment existing = this.enrollments.get(playerId);
        if (existing == null) {
            this.enrollments.put(playerId, new Enrollment(playerId, pendingStart, false, false));
        } else if (!pendingStart) {
            existing.setPendingStart(false);
        }
    }

    public void removeEnrollment(UUID playerId) {
        this.enrollments.remove(playerId);
    }

    void retainEnrollments(Set<UUID> playerIds) {
        this.enrollments.keySet().removeIf(playerId -> !playerIds.contains(playerId));
    }

    public int objectiveCounter(String objectiveId) {
        return this.objectiveCounters.getOrDefault(objectiveId, 0);
    }

    public int incrementObjective(String objectiveId) {
        int next = objectiveCounter(objectiveId) + 1;
        this.objectiveCounters.put(objectiveId, next);
        return next;
    }

    public void mergeObjectiveCounter(String objectiveId, int count) {
        if (objectiveId != null && !objectiveId.isBlank() && count > objectiveCounter(objectiveId)) {
            this.objectiveCounters.put(objectiveId, count);
        }
    }

    public boolean markObjectiveComplete(String objectiveId) {
        return objectiveId != null && !objectiveId.isBlank() && this.completedObjectives.add(objectiveId);
    }

    public boolean objectiveComplete(String objectiveId) {
        return objectiveId != null && this.completedObjectives.contains(objectiveId);
    }

    public boolean markDeathProcessed(String objectiveId, UUID killedEntityId) {
        if (objectiveId == null || killedEntityId == null) {
            return false;
        }
        String key = objectiveId + "|" + killedEntityId;
        if (!this.processedDeaths.add(key)) {
            return false;
        }
        while (this.processedDeaths.size() > MAX_PROCESSED_DEATHS) {
            this.processedDeaths.remove(this.processedDeaths.iterator().next());
        }
        return true;
    }

    public boolean completed() {
        return this.completed;
    }

    public void markCompleted() {
        this.completed = true;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_INSTANCE, this.instanceId);
        tag.putString(TAG_QUEST, this.questId.toString());
        if (this.sourceVillagerId != null) {
            tag.putUUID(TAG_SOURCE_VILLAGER, this.sourceVillagerId);
        }
        tag.putLong(TAG_CREATED_GAME_TIME, this.createdGameTime);
        tag.putBoolean(TAG_COMPLETED, this.completed);
        ListTag enrollmentsTag = new ListTag();
        for (Enrollment enrollment : this.enrollments.values()) {
            enrollmentsTag.add(enrollment.save());
        }
        tag.put(TAG_ENROLLMENTS, enrollmentsTag);
        ListTag countersTag = new ListTag();
        for (Map.Entry<String, Integer> entry : this.objectiveCounters.entrySet()) {
            CompoundTag counterTag = new CompoundTag();
            counterTag.putString(TAG_OBJECTIVE, entry.getKey());
            counterTag.putInt(TAG_COUNT, entry.getValue());
            countersTag.add(counterTag);
        }
        tag.put(TAG_COUNTERS, countersTag);
        tag.put(TAG_COMPLETED_OBJECTIVES, NbtDataUtil.stringList(this.completedObjectives));
        tag.put(TAG_PROCESSED_DEATHS, NbtDataUtil.stringList(this.processedDeaths));
        return tag;
    }

    static PartySharedQuestRecord load(CompoundTag tag) {
        ResourceLocation questId = ResourceLocation.tryParse(tag.getString(TAG_QUEST));
        if (questId == null) {
            return null;
        }
        UUID instanceId = tag.hasUUID(TAG_INSTANCE) ? tag.getUUID(TAG_INSTANCE) : UUID.randomUUID();
        Map<UUID, Enrollment> enrollments = new LinkedHashMap<>();
        for (Tag rawEnrollment : tag.getList(TAG_ENROLLMENTS, Tag.TAG_COMPOUND)) {
            if (rawEnrollment instanceof CompoundTag enrollmentTag) {
                Enrollment enrollment = Enrollment.load(enrollmentTag);
                if (enrollment != null) {
                    enrollments.putIfAbsent(enrollment.playerId(), enrollment);
                }
            }
        }
        Map<String, Integer> counters = new LinkedHashMap<>();
        for (Tag rawCounter : tag.getList(TAG_COUNTERS, Tag.TAG_COMPOUND)) {
            if (rawCounter instanceof CompoundTag counterTag) {
                String objective = counterTag.getString(TAG_OBJECTIVE);
                int count = Math.max(0, counterTag.getInt(TAG_COUNT));
                if (!objective.isBlank() && count > 0) {
                    counters.put(objective, count);
                }
            }
        }
        Set<String> completedObjectives = new LinkedHashSet<>(
                readStringList(tag, TAG_COMPLETED_OBJECTIVES));
        LinkedHashSet<String> processedDeaths = new LinkedHashSet<>(
                readStringList(tag, TAG_PROCESSED_DEATHS));
        return new PartySharedQuestRecord(
                instanceId,
                questId,
                tag.hasUUID(TAG_SOURCE_VILLAGER) ? tag.getUUID(TAG_SOURCE_VILLAGER) : null,
                tag.getLong(TAG_CREATED_GAME_TIME),
                enrollments,
                counters,
                completedObjectives,
                processedDeaths,
                tag.getBoolean(TAG_COMPLETED));
    }

    private static List<String> readStringList(CompoundTag tag, String key) {
        List<String> values = new ArrayList<>();
        for (Tag rawValue : tag.getList(key, Tag.TAG_STRING)) {
            String value = rawValue.getAsString();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    public static final class Enrollment {
        private final UUID playerId;
        private boolean pendingStart;
        private boolean pendingReward;
        private boolean rewardClaimed;

        private Enrollment(UUID playerId, boolean pendingStart, boolean pendingReward, boolean rewardClaimed) {
            this.playerId = playerId;
            this.pendingStart = pendingStart;
            this.pendingReward = pendingReward;
            this.rewardClaimed = rewardClaimed;
        }

        public UUID playerId() {
            return this.playerId;
        }

        public boolean pendingStart() {
            return this.pendingStart;
        }

        public boolean pendingReward() {
            return this.pendingReward;
        }

        public boolean rewardClaimed() {
            return this.rewardClaimed;
        }

        public void setPendingStart(boolean pendingStart) {
            this.pendingStart = pendingStart;
        }

        public void markPendingReward() {
            if (!this.rewardClaimed) {
                this.pendingReward = true;
            }
        }

        public void markRewardClaimed() {
            this.pendingReward = false;
            this.rewardClaimed = true;
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID(TAG_PLAYER, this.playerId);
            tag.putBoolean(TAG_PENDING_START, this.pendingStart);
            tag.putBoolean(TAG_PENDING_REWARD, this.pendingReward);
            tag.putBoolean(TAG_REWARD_CLAIMED, this.rewardClaimed);
            return tag;
        }

        private static Enrollment load(CompoundTag tag) {
            if (!tag.hasUUID(TAG_PLAYER)) {
                return null;
            }
            return new Enrollment(
                    tag.getUUID(TAG_PLAYER),
                    tag.getBoolean(TAG_PENDING_START),
                    tag.getBoolean(TAG_PENDING_REWARD),
                    tag.getBoolean(TAG_REWARD_CLAIMED));
        }
    }
}
