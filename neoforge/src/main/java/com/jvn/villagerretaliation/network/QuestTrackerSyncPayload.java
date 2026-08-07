package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.quest.tracking.QuestTrackerLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record QuestTrackerSyncPayload(List<Entry> entries, List<String> trackedQuestIds, boolean flash) implements CustomPacketPayload {
    public static final int MAX_TRACKER_ENTRIES = 3;
    public static final int MAX_TRACKED_QUESTS = QuestTrackerLimits.MAX_TRACKED_QUESTS;
    public static final int MAX_SYNC_ENTRIES = 32;
    public static final int MAX_QUEST_ITEMS = 16;
    public static final int MAX_REWARD_PREVIEWS = 8;
    public static final int MAX_PREREQUISITES = 8;
    public static final int MAX_OBJECTIVE_STEPS = 24;
    public static final int MAX_JOURNAL_TAGS = 16;
    private static final int MAX_QUEST_ID_LENGTH = 128;
    private static final int MAX_TITLE_LENGTH = 128;
    private static final int MAX_TEXT_LENGTH = 256;
    private static final int MAX_STATE_LENGTH = 32;
    private static final int MAX_STATUS_LENGTH = 96;
    private static final int MAX_ISSUER_LENGTH = 160;
    private static final int MAX_LOCATION_LENGTH = 192;
    private static final int MAX_ITEM_ID_LENGTH = 128;
    private static final int MAX_ITEM_LABEL_LENGTH = 128;
    private static final int MAX_REWARD_KIND_LENGTH = 32;
    private static final int MAX_REWARD_LABEL_LENGTH = 160;
    private static final int MAX_PREREQUISITE_LABEL_LENGTH = 160;
    private static final int MAX_JOURNAL_VALUE_LENGTH = 128;
    public static final Type<QuestTrackerSyncPayload> TYPE = VillagerPayloads.type("quest_tracker_sync");
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestTrackerSyncPayload> STREAM_CODEC =
            VillagerPayloads.codec(QuestTrackerSyncPayload::encode, QuestTrackerSyncPayload::decode);

    public QuestTrackerSyncPayload(List<Entry> entries, String trackedQuestId, boolean flash) {
        this(entries, trackedQuestId == null || trackedQuestId.isBlank() ? List.of() : List.of(trackedQuestId), flash);
    }

    public QuestTrackerSyncPayload {
        entries = entries == null
                ? List.of()
                : List.copyOf(entries.stream().filter(Objects::nonNull).limit(MAX_SYNC_ENTRIES).toList());
        trackedQuestIds = trackedQuestIds == null
                ? List.of()
                : List.copyOf(trackedQuestIds.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(questId -> !questId.isBlank())
                        .map(questId -> boundedUtf(questId, MAX_QUEST_ID_LENGTH))
                        .distinct()
                        .limit(MAX_TRACKED_QUESTS)
                        .toList());
    }

    private static String boundedUtf(String value, int maxLength) {
        if (value == null || maxLength <= 0) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        int end = maxLength;
        if (Character.isHighSurrogate(value.charAt(end - 1))
                && end < value.length()
                && Character.isLowSurrogate(value.charAt(end))) {
            end--;
        }
        return value.substring(0, end);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, QuestTrackerSyncPayload payload) {
        buffer.writeVarInt(Math.min(MAX_SYNC_ENTRIES, payload.entries().size()));
        for (Entry entry : payload.entries()) {
            buffer.writeUtf(entry.questId(), 128);
            buffer.writeUtf(entry.title(), 128);
            buffer.writeUtf(entry.objective(), 256);
            buffer.writeUtf(entry.description(), 256);
            buffer.writeUtf(entry.metadata(), 256);
            buffer.writeFloat(entry.progress());
            buffer.writeBoolean(entry.showProgress());
            buffer.writeUtf(entry.state(), 32);
            buffer.writeUtf(entry.status(), 96);
            buffer.writeUtf(entry.issuer(), 160);
            buffer.writeUtf(entry.issuerLocation(), 192);
            buffer.writeVarInt(Math.min(MAX_QUEST_ITEMS, entry.questItems().size()));
            for (QuestItem item : entry.questItems()) {
                buffer.writeUtf(item.itemId(), 128);
                buffer.writeUtf(item.label(), 128);
                buffer.writeVarInt(item.count());
                buffer.writeVarInt(item.currentCount());
            }
            buffer.writeVarInt(Math.min(MAX_REWARD_PREVIEWS, entry.rewardPreviews().size()));
            for (RewardPreview reward : entry.rewardPreviews()) {
                buffer.writeUtf(reward.kind(), 32);
                buffer.writeUtf(reward.label(), 160);
                buffer.writeVarInt(reward.amount());
            }
            buffer.writeVarInt(Math.min(MAX_PREREQUISITES, entry.prerequisites().size()));
            for (Prerequisite prerequisite : entry.prerequisites()) {
                buffer.writeUtf(prerequisite.questId(), 128);
                buffer.writeUtf(prerequisite.label(), 160);
                buffer.writeBoolean(prerequisite.met());
            }
            buffer.writeVarInt(Math.min(MAX_OBJECTIVE_STEPS, entry.objectiveSteps().size()));
            for (ObjectiveStep objectiveStep : entry.objectiveSteps()) {
                buffer.writeUtf(objectiveStep.label(), 256);
                buffer.writeBoolean(objectiveStep.completed());
            }
            buffer.writeBoolean(entry.questUpdate());
            buffer.writeBoolean(entry.questAvailable());
            writeJournal(buffer, entry.journal());
        }
        buffer.writeVarInt(Math.min(MAX_TRACKED_QUESTS, payload.trackedQuestIds().size()));
        for (String questId : payload.trackedQuestIds()) {
            buffer.writeUtf(questId, 128);
        }
        buffer.writeBoolean(payload.flash());
    }

    private static QuestTrackerSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        int size = VillagerPayloads.readCollectionSize(buffer, MAX_SYNC_ENTRIES, "quest tracker entries");
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Entry entry = new Entry(
                    buffer.readUtf(128),
                    buffer.readUtf(128),
                    buffer.readUtf(256),
                    buffer.readUtf(256),
                    buffer.readUtf(256),
                    buffer.readFloat(),
                    buffer.readBoolean(),
                    buffer.readUtf(32),
                    buffer.readUtf(96),
                    buffer.readUtf(160),
                    buffer.readUtf(192),
                    readQuestItems(buffer),
                    readRewardPreviews(buffer),
                    readPrerequisites(buffer),
                    readObjectiveSteps(buffer),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    readJournal(buffer)
            );
            entries.add(entry);
        }
        int trackedSize = VillagerPayloads.readCollectionSize(buffer, MAX_TRACKED_QUESTS, "tracked quests");
        List<String> trackedQuestIds = new ArrayList<>(trackedSize);
        for (int i = 0; i < trackedSize; i++) {
            trackedQuestIds.add(buffer.readUtf(128));
        }
        return new QuestTrackerSyncPayload(entries, trackedQuestIds, buffer.readBoolean());
    }

    private static List<QuestItem> readQuestItems(RegistryFriendlyByteBuf buffer) {
        int size = VillagerPayloads.readCollectionSize(buffer, MAX_QUEST_ITEMS, "quest tracker items");
        List<QuestItem> items = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            QuestItem item = new QuestItem(
                    buffer.readUtf(128),
                    buffer.readUtf(128),
                    buffer.readVarInt(),
                    buffer.readVarInt()
            );
            items.add(item);
        }
        return items;
    }

    private static List<RewardPreview> readRewardPreviews(RegistryFriendlyByteBuf buffer) {
        int size = VillagerPayloads.readCollectionSize(buffer, MAX_REWARD_PREVIEWS, "quest reward previews");
        List<RewardPreview> rewards = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            RewardPreview reward = new RewardPreview(
                    buffer.readUtf(32),
                    buffer.readUtf(160),
                    buffer.readVarInt()
            );
            rewards.add(reward);
        }
        return rewards;
    }

    private static List<Prerequisite> readPrerequisites(RegistryFriendlyByteBuf buffer) {
        int size = VillagerPayloads.readCollectionSize(buffer, MAX_PREREQUISITES, "quest prerequisites");
        List<Prerequisite> prerequisites = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Prerequisite prerequisite = new Prerequisite(
                    buffer.readUtf(128),
                    buffer.readUtf(160),
                    buffer.readBoolean()
            );
            prerequisites.add(prerequisite);
        }
        return prerequisites;
    }

    private static List<ObjectiveStep> readObjectiveSteps(RegistryFriendlyByteBuf buffer) {
        int size = VillagerPayloads.readCollectionSize(buffer, MAX_OBJECTIVE_STEPS, "quest objective steps");
        List<ObjectiveStep> objectiveSteps = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ObjectiveStep objectiveStep = new ObjectiveStep(
                    buffer.readUtf(256),
                    buffer.readBoolean()
            );
            objectiveSteps.add(objectiveStep);
        }
        return objectiveSteps;
    }

    private static void writeJournal(RegistryFriendlyByteBuf buffer, Journal journal) {
        buffer.writeUtf(journal.questline(), MAX_JOURNAL_VALUE_LENGTH);
        buffer.writeVarInt(Math.min(MAX_JOURNAL_TAGS, journal.tags().size()));
        for (String tag : journal.tags()) {
            buffer.writeUtf(tag, MAX_JOURNAL_VALUE_LENGTH);
        }
        buffer.writeUtf(journal.icon(), MAX_JOURNAL_VALUE_LENGTH);
        buffer.writeUtf(journal.color(), MAX_JOURNAL_VALUE_LENGTH);
        buffer.writeVarInt(journal.priority());
        buffer.writeBoolean(journal.hidden());
        buffer.writeLong(journal.expiresAtGameTime());
        buffer.writeLong(journal.completedGameTime());
        buffer.writeBoolean(journal.waypoint().present());
        if (journal.waypoint().present()) {
            buffer.writeUtf(journal.waypoint().dimension(), MAX_JOURNAL_VALUE_LENGTH);
            buffer.writeInt(journal.waypoint().x());
            buffer.writeInt(journal.waypoint().y());
            buffer.writeInt(journal.waypoint().z());
        }
    }

    private static Journal readJournal(RegistryFriendlyByteBuf buffer) {
        String questline = buffer.readUtf(MAX_JOURNAL_VALUE_LENGTH);
        int tagCount = VillagerPayloads.readCollectionSize(buffer, MAX_JOURNAL_TAGS, "quest journal tags");
        List<String> tags = new ArrayList<>(tagCount);
        for (int i = 0; i < tagCount; i++) {
            tags.add(buffer.readUtf(MAX_JOURNAL_VALUE_LENGTH));
        }
        String icon = buffer.readUtf(MAX_JOURNAL_VALUE_LENGTH);
        String color = buffer.readUtf(MAX_JOURNAL_VALUE_LENGTH);
        int priority = buffer.readVarInt();
        boolean hidden = buffer.readBoolean();
        long expiresAt = buffer.readLong();
        long completedAt = buffer.readLong();
        Waypoint waypoint = buffer.readBoolean()
                ? new Waypoint(buffer.readUtf(MAX_JOURNAL_VALUE_LENGTH), buffer.readInt(), buffer.readInt(), buffer.readInt())
                : Waypoint.NONE;
        return new Journal(questline, tags, icon, color, priority, hidden, expiresAt, completedAt, waypoint);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public String trackedQuestId() {
        return this.trackedQuestIds.isEmpty() ? "" : this.trackedQuestIds.getFirst();
    }

    public record Entry(
            String questId,
            String title,
            String objective,
            String description,
            String metadata,
            float progress,
            boolean showProgress,
            String state,
            String status,
            String issuer,
            String issuerLocation,
            List<QuestItem> questItems,
            List<RewardPreview> rewardPreviews,
            List<Prerequisite> prerequisites,
            List<ObjectiveStep> objectiveSteps,
            boolean questUpdate,
            boolean questAvailable,
            Journal journal) {
        public Entry(
                String questId,
                String title,
                String objective,
                String description,
                String metadata,
                float progress,
                boolean showProgress,
                String state,
                String status,
                String issuer,
                String issuerLocation,
                List<QuestItem> questItems,
                List<RewardPreview> rewardPreviews,
                List<Prerequisite> prerequisites,
                List<ObjectiveStep> objectiveSteps,
                boolean questUpdate,
                boolean questAvailable) {
            this(questId, title, objective, description, metadata, progress, showProgress, state, status, issuer, issuerLocation,
                    questItems, rewardPreviews, prerequisites, objectiveSteps, questUpdate, questAvailable, Journal.EMPTY);
        }
        public Entry(
                String questId,
                String title,
                String objective,
                String description,
                String metadata,
                float progress,
                boolean showProgress,
                String state,
                String status,
                String issuer,
                String issuerLocation,
                List<QuestItem> questItems,
                boolean questUpdate,
                boolean questAvailable) {
            this(questId, title, objective, description, metadata, progress, showProgress, state, status, issuer, issuerLocation, questItems, List.of(), List.of(), List.of(), questUpdate, questAvailable);
        }

        public Entry(
                String questId,
                String title,
                String objective,
                String description,
                String metadata,
                float progress,
                boolean showProgress,
                String state,
                String status,
                String issuer,
                String issuerLocation,
                List<QuestItem> questItems,
                List<RewardPreview> rewardPreviews,
                boolean questUpdate,
                boolean questAvailable) {
            this(questId, title, objective, description, metadata, progress, showProgress, state, status, issuer, issuerLocation, questItems, rewardPreviews, List.of(), List.of(), questUpdate, questAvailable);
        }

        public Entry(
                String questId,
                String title,
                String objective,
                String description,
                String metadata,
                float progress,
                boolean showProgress,
                String state,
                String status,
                String issuer,
                String issuerLocation,
                List<QuestItem> questItems,
                List<RewardPreview> rewardPreviews,
                List<Prerequisite> prerequisites,
                boolean questUpdate,
                boolean questAvailable) {
            this(questId, title, objective, description, metadata, progress, showProgress, state, status, issuer, issuerLocation, questItems, rewardPreviews, prerequisites, List.of(), questUpdate, questAvailable);
        }

        public Entry(
                String questId,
                String title,
                String objective,
                String metadata,
                float progress,
                boolean showProgress,
                String state,
                String status,
                String issuer,
                String issuerLocation,
                List<QuestItem> questItems,
                boolean questUpdate) {
            this(questId, title, objective, "", metadata, progress, showProgress, state, status, issuer, issuerLocation, questItems, List.of(), List.of(), List.of(), questUpdate, false);
        }

        public Entry(
                String questId,
                String title,
                String objective,
                String metadata,
                float progress,
                boolean showProgress,
                String state,
                String status,
                String issuer,
                String issuerLocation,
                List<QuestItem> questItems) {
            this(questId, title, objective, "", metadata, progress, showProgress, state, status, issuer, issuerLocation, questItems, List.of(), List.of(), List.of(), false, false);
        }

        public Entry(
                String questId,
                String title,
                String objective,
                String metadata,
                float progress,
                boolean showProgress,
                String state,
                String status,
                String issuer,
                String issuerLocation,
                List<QuestItem> questItems,
                boolean questUpdate,
                boolean questAvailable) {
            this(questId, title, objective, "", metadata, progress, showProgress, state, status, issuer, issuerLocation, questItems, List.of(), List.of(), List.of(), questUpdate, questAvailable);
        }

        public Entry {
            questId = boundedUtf(questId, MAX_QUEST_ID_LENGTH);
            title = boundedUtf(title, MAX_TITLE_LENGTH);
            objective = boundedUtf(objective, MAX_TEXT_LENGTH);
            description = boundedUtf(description, MAX_TEXT_LENGTH);
            metadata = boundedUtf(metadata, MAX_TEXT_LENGTH);
            progress = Float.isFinite(progress) ? Math.max(0.0F, Math.min(1.0F, progress)) : 0.0F;
            state = boundedUtf(state, MAX_STATE_LENGTH);
            status = boundedUtf(status, MAX_STATUS_LENGTH);
            issuer = boundedUtf(issuer, MAX_ISSUER_LENGTH);
            issuerLocation = boundedUtf(issuerLocation, MAX_LOCATION_LENGTH);
            questItems = questItems == null
                    ? List.of()
                    : List.copyOf(questItems.stream().filter(Objects::nonNull).limit(MAX_QUEST_ITEMS).toList());
            rewardPreviews = rewardPreviews == null
                    ? List.of()
                    : List.copyOf(rewardPreviews.stream().filter(Objects::nonNull).limit(MAX_REWARD_PREVIEWS).toList());
            prerequisites = prerequisites == null
                    ? List.of()
                    : List.copyOf(prerequisites.stream().filter(Objects::nonNull).limit(MAX_PREREQUISITES).toList());
            objectiveSteps = objectiveSteps == null
                    ? List.of()
                    : List.copyOf(objectiveSteps.stream().filter(Objects::nonNull).limit(MAX_OBJECTIVE_STEPS).toList());
            journal = journal == null ? Journal.EMPTY : journal;
        }

        public Entry withQuestUpdate(boolean questUpdate) {
            return new Entry(
                    this.questId,
                    this.title,
                    this.objective,
                    this.description,
                    this.metadata,
                    this.progress,
                    this.showProgress,
                    this.state,
                    this.status,
                    this.issuer,
                    this.issuerLocation,
                    this.questItems,
                    this.rewardPreviews,
                    this.prerequisites,
                    this.objectiveSteps,
                    questUpdate,
                    this.questAvailable,
                    this.journal);
        }

        public Entry withQuestId(String questId) {
            return new Entry(
                    questId,
                    this.title,
                    this.objective,
                    this.description,
                    this.metadata,
                    this.progress,
                    this.showProgress,
                    this.state,
                    this.status,
                    this.issuer,
                    this.issuerLocation,
                    this.questItems,
                    this.rewardPreviews,
                    this.prerequisites,
                    this.objectiveSteps,
                    this.questUpdate,
                    this.questAvailable,
                    this.journal);
        }

        public Entry withQuestAvailable(boolean questAvailable) {
            return new Entry(
                    this.questId,
                    this.title,
                    this.objective,
                    this.description,
                    this.metadata,
                    this.progress,
                    this.showProgress,
                    this.state,
                    this.status,
                    this.issuer,
                    this.issuerLocation,
                    this.questItems,
                    this.rewardPreviews,
                    this.prerequisites,
                    this.objectiveSteps,
                    this.questUpdate,
                    questAvailable,
                    this.journal);
        }

        public Entry withJournal(Journal journal) {
            return new Entry(
                    this.questId, this.title, this.objective, this.description, this.metadata, this.progress,
                    this.showProgress, this.state, this.status, this.issuer, this.issuerLocation, this.questItems,
                    this.rewardPreviews, this.prerequisites, this.objectiveSteps, this.questUpdate, this.questAvailable, journal);
        }

        public boolean trackable() {
            return switch (this.state.toLowerCase(java.util.Locale.ROOT)) {
                case "active", "abandoned", "expired" -> true;
                default -> false;
            };
        }
    }

    public record Journal(
            String questline,
            List<String> tags,
            String icon,
            String color,
            int priority,
            boolean hidden,
            long expiresAtGameTime,
            long completedGameTime,
            Waypoint waypoint) {
        public static final Journal EMPTY = new Journal("", List.of(), "", "", 0, false, -1L, -1L, Waypoint.NONE);

        public Journal {
            questline = boundedUtf(questline, MAX_JOURNAL_VALUE_LENGTH);
            tags = tags == null ? List.of() : List.copyOf(tags.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(tag -> !tag.isBlank())
                    .map(tag -> boundedUtf(tag, MAX_JOURNAL_VALUE_LENGTH))
                    .distinct()
                    .limit(MAX_JOURNAL_TAGS)
                    .toList());
            icon = boundedUtf(icon, MAX_JOURNAL_VALUE_LENGTH);
            color = boundedUtf(color, MAX_JOURNAL_VALUE_LENGTH);
            waypoint = waypoint == null ? Waypoint.NONE : waypoint;
        }

        public Journal withRuntime(long expiresAtGameTime, long completedGameTime, Waypoint waypoint) {
            return new Journal(this.questline, this.tags, this.icon, this.color, this.priority, this.hidden,
                    expiresAtGameTime, completedGameTime, waypoint);
        }
    }

    public record Waypoint(String dimension, int x, int y, int z) {
        public static final Waypoint NONE = new Waypoint("", 0, 0, 0);

        public Waypoint {
            dimension = boundedUtf(dimension, MAX_JOURNAL_VALUE_LENGTH);
        }

        public boolean present() {
            return !this.dimension.isBlank();
        }
    }

    public record RewardPreview(String kind, String label, int amount) {
        public RewardPreview {
            kind = boundedUtf(kind, MAX_REWARD_KIND_LENGTH);
            label = boundedUtf(label, MAX_REWARD_LABEL_LENGTH);
        }
    }

    public record Prerequisite(String questId, String label, boolean met) {
        public Prerequisite {
            questId = boundedUtf(questId, MAX_QUEST_ID_LENGTH);
            label = boundedUtf(label, MAX_PREREQUISITE_LABEL_LENGTH);
        }
    }

    public record ObjectiveStep(String label, boolean completed) {
        public ObjectiveStep {
            label = boundedUtf(label, MAX_TEXT_LENGTH);
        }
    }

    public record QuestItem(String itemId, String label, int count, int currentCount) {
        public QuestItem(String itemId, String label, int count) {
            this(itemId, label, count, 0);
        }

        public QuestItem {
            itemId = boundedUtf(itemId, MAX_ITEM_ID_LENGTH);
            label = boundedUtf(label, MAX_ITEM_LABEL_LENGTH);
            count = Math.max(1, count);
            currentCount = Math.max(0, currentCount);
        }
    }
}
