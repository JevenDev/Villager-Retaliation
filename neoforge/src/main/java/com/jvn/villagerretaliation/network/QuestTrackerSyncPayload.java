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
                        .distinct()
                        .limit(MAX_TRACKED_QUESTS)
                        .toList());
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
            buffer.writeBoolean(entry.questUpdate());
            buffer.writeBoolean(entry.questAvailable());
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
                    buffer.readBoolean(),
                    buffer.readBoolean()
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
            boolean questUpdate,
            boolean questAvailable) {
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
            this(questId, title, objective, description, metadata, progress, showProgress, state, status, issuer, issuerLocation, questItems, List.of(), List.of(), questUpdate, questAvailable);
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
            this(questId, title, objective, description, metadata, progress, showProgress, state, status, issuer, issuerLocation, questItems, rewardPreviews, List.of(), questUpdate, questAvailable);
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
            this(questId, title, objective, "", metadata, progress, showProgress, state, status, issuer, issuerLocation, questItems, List.of(), List.of(), questUpdate, false);
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
            this(questId, title, objective, "", metadata, progress, showProgress, state, status, issuer, issuerLocation, questItems, List.of(), List.of(), false, false);
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
            this(questId, title, objective, "", metadata, progress, showProgress, state, status, issuer, issuerLocation, questItems, List.of(), List.of(), questUpdate, questAvailable);
        }

        public Entry {
            questId = questId == null ? "" : questId;
            title = title == null ? "" : title;
            objective = objective == null ? "" : objective;
            description = description == null ? "" : description;
            metadata = metadata == null ? "" : metadata;
            progress = Math.max(0.0F, Math.min(1.0F, progress));
            state = state == null ? "" : state;
            status = status == null ? "" : status;
            issuer = issuer == null ? "" : issuer;
            issuerLocation = issuerLocation == null ? "" : issuerLocation;
            questItems = questItems == null
                    ? List.of()
                    : List.copyOf(questItems.stream().filter(Objects::nonNull).limit(MAX_QUEST_ITEMS).toList());
            rewardPreviews = rewardPreviews == null
                    ? List.of()
                    : List.copyOf(rewardPreviews.stream().filter(Objects::nonNull).limit(MAX_REWARD_PREVIEWS).toList());
            prerequisites = prerequisites == null
                    ? List.of()
                    : List.copyOf(prerequisites.stream().filter(Objects::nonNull).limit(MAX_PREREQUISITES).toList());
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
                    questUpdate,
                    this.questAvailable);
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
                    this.questUpdate,
                    this.questAvailable);
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
                    this.questUpdate,
                    questAvailable);
        }

        public boolean trackable() {
            return switch (this.state.toLowerCase(java.util.Locale.ROOT)) {
                case "active", "abandoned", "expired" -> true;
                default -> false;
            };
        }
    }

    public record RewardPreview(String kind, String label, int amount) {
        public RewardPreview {
            kind = kind == null ? "" : kind;
            label = label == null ? "" : label;
        }
    }

    public record Prerequisite(String questId, String label, boolean met) {
        public Prerequisite {
            questId = questId == null ? "" : questId;
            label = label == null ? "" : label;
        }
    }

    public record QuestItem(String itemId, String label, int count) {
        public QuestItem {
            itemId = itemId == null ? "" : itemId;
            label = label == null ? "" : label;
            count = Math.max(1, count);
        }
    }
}
