package com.jvn.villagerretaliation.duel;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Durable per-opponent duel records and bounded village-facing history. */
public final class DuelSavedData extends SavedData {
    private static final String DATA_NAME = "villagerretaliation_duels";
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_HISTORY = 64;
    private final Map<Key, DuelRecord> records = new HashMap<>();
    private final Map<StoryKey, Set<UUID>> acknowledgedStories = new HashMap<>();
    private final ArrayDeque<DuelMemory> history = new ArrayDeque<>();

    public static DuelSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DuelSavedData::new, DuelSavedData::load, DataFixTypes.LEVEL), DATA_NAME);
    }

    public static DuelSavedData load(CompoundTag root, HolderLookup.Provider provider) {
        DuelSavedData data = new DuelSavedData();
        for (Tag raw : root.getList("Records", Tag.TAG_COMPOUND)) {
            if (!(raw instanceof CompoundTag tag) || !tag.hasUUID("Villager") || !tag.hasUUID("Player")) continue;
            DuelRecord record = new DuelRecord(
                    tag.getInt("Wins"), tag.getInt("Losses"), tag.getInt("LossStreak"),
                    tag.getBoolean("Refuses"), tag.getLong("LastStart"),
                    tag.getInt("PendingGloats"), tag.getInt("PendingSulks"));
            data.records.put(new Key(tag.getUUID("Villager"), tag.getUUID("Player")), record);
        }
        for (Tag raw : root.getList("History", Tag.TAG_COMPOUND)) {
            if (!(raw instanceof CompoundTag tag) || !tag.hasUUID("Id") || !tag.hasUUID("Villager") || !tag.hasUUID("Player")) continue;
            DuelResult result;
            try { result = DuelResult.valueOf(tag.getString("Result")); }
            catch (IllegalArgumentException ignored) { continue; }
            data.history.addLast(new DuelMemory(
                    tag.getUUID("Id"), tag.getUUID("Villager"), tag.getUUID("Player"),
                    tag.getString("VillagerName"), tag.getString("PlayerName"), result,
                    tag.getInt("Wager"), tag.getLong("GameTime"), tag.getLong("Position"),
                    tag.hasUUID("Village") ? tag.getUUID("Village") : null,
                    readUuidSet(tag, "Witnesses"),
                    tag.getInt("VillagerWins"), tag.getInt("VillagerLosses")));
        }
        for (Tag raw : root.getList("StoryAcknowledgements", Tag.TAG_COMPOUND)) {
            if (!(raw instanceof CompoundTag tag) || !tag.hasUUID("Speaker") || !tag.hasUUID("Player")) continue;
            Set<UUID> ids = new HashSet<>();
            for (Tag idRaw : tag.getList("Events", Tag.TAG_INT_ARRAY)) {
                if (idRaw instanceof net.minecraft.nbt.IntArrayTag array && array.getAsIntArray().length == 4)
                    ids.add(net.minecraft.core.UUIDUtil.uuidFromIntArray(array.getAsIntArray()));
            }
            data.acknowledgedStories.put(new StoryKey(tag.getUUID("Speaker"), tag.getUUID("Player")), ids);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider provider) {
        root.putInt("FormatVersion", FORMAT_VERSION);
        ListTag recordTags = new ListTag();
        this.records.forEach((key, record) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Villager", key.villagerId());
            tag.putUUID("Player", key.playerId());
            tag.putInt("Wins", record.villagerWins());
            tag.putInt("Losses", record.villagerLosses());
            tag.putInt("LossStreak", record.consecutiveLosses());
            tag.putBoolean("Refuses", record.refuses());
            tag.putLong("LastStart", record.lastStartGameTime());
            tag.putInt("PendingGloats", record.pendingGloats());
            tag.putInt("PendingSulks", record.pendingSulks());
            recordTags.add(tag);
        });
        root.put("Records", recordTags);
        ListTag historyTags = new ListTag();
        for (DuelMemory memory : this.history) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", memory.id());
            tag.putUUID("Villager", memory.villagerId());
            tag.putUUID("Player", memory.playerId());
            tag.putString("VillagerName", memory.villagerName());
            tag.putString("PlayerName", memory.playerName());
            tag.putString("Result", memory.result().name());
            tag.putInt("Wager", memory.wager());
            tag.putLong("GameTime", memory.gameTime());
            tag.putLong("Position", memory.packedPosition());
            if (memory.villageId() != null) tag.putUUID("Village", memory.villageId());
            ListTag witnesses = new ListTag();
            for (UUID witnessId : memory.witnessIds()) {
                witnesses.add(new net.minecraft.nbt.IntArrayTag(net.minecraft.core.UUIDUtil.uuidToIntArray(witnessId)));
            }
            tag.put("Witnesses", witnesses);
            tag.putInt("VillagerWins", memory.villagerWins());
            tag.putInt("VillagerLosses", memory.villagerLosses());
            historyTags.add(tag);
        }
        root.put("History", historyTags);
        ListTag acknowledgementTags = new ListTag();
        this.acknowledgedStories.forEach((key, ids) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Speaker", key.speakerId());
            tag.putUUID("Player", key.playerId());
            ListTag events = new ListTag();
            for (UUID id : ids) events.add(new net.minecraft.nbt.IntArrayTag(net.minecraft.core.UUIDUtil.uuidToIntArray(id)));
            tag.put("Events", events);
            acknowledgementTags.add(tag);
        });
        root.put("StoryAcknowledgements", acknowledgementTags);
        return root;
    }

    private static Set<UUID> readUuidSet(CompoundTag tag, String key) {
        Set<UUID> ids = new HashSet<>();
        for (Tag raw : tag.getList(key, Tag.TAG_INT_ARRAY)) {
            if (raw instanceof net.minecraft.nbt.IntArrayTag array && array.getAsIntArray().length == 4) {
                ids.add(net.minecraft.core.UUIDUtil.uuidFromIntArray(array.getAsIntArray()));
            }
        }
        return ids;
    }

    public DuelRecord record(UUID villagerId, UUID playerId) {
        DuelRecord record = this.records.get(new Key(villagerId, playerId));
        return record == null ? DuelRecord.EMPTY : record;
    }

    public void markStarted(UUID villagerId, UUID playerId, long gameTime) {
        Key key = new Key(villagerId, playerId);
        this.records.put(key, record(villagerId, playerId).withLastStart(gameTime));
        setDirty();
    }

    public DuelRecord complete(UUID villagerId, UUID playerId, DuelResult result) {
        Key key = new Key(villagerId, playerId);
        DuelRecord current = record(villagerId, playerId);
        DuelRecord next = switch (result) {
            case PLAYER_WIN -> {
                int streak = current.consecutiveLosses() + 1;
                yield new DuelRecord(current.villagerWins(), current.villagerLosses() + 1, streak,
                        current.refuses() || streak >= VillagerRetaliationConfig.DUEL_REFUSAL_LOSSES.get(),
                        current.lastStartGameTime(), current.pendingGloats(), current.pendingSulks() + 1);
            }
            case VILLAGER_WIN -> new DuelRecord(current.villagerWins() + 1, current.villagerLosses(), 0,
                    current.refuses(), current.lastStartGameTime(), current.pendingGloats() + 1, current.pendingSulks());
            case DRAW, CANCELLED -> current;
        };
        this.records.put(key, next);
        setDirty();
        return next;
    }

    public void remember(DuelMemory memory) {
        if (memory == null || memory.result() == DuelResult.CANCELLED) return;
        this.history.removeIf(existing -> existing.id().equals(memory.id()));
        this.history.addLast(memory);
        while (this.history.size() > MAX_HISTORY) this.history.removeFirst();
        setDirty();
    }

    public boolean storyAcknowledged(UUID speakerId, UUID playerId, UUID eventId) {
        return this.acknowledgedStories.getOrDefault(new StoryKey(speakerId, playerId), Set.of()).contains(eventId);
    }

    public void acknowledgeStory(UUID speakerId, UUID playerId, UUID eventId) {
        StoryKey key = new StoryKey(speakerId, playerId);
        Set<UUID> ids = this.acknowledgedStories.computeIfAbsent(key, ignored -> new HashSet<>());
        ids.add(eventId);
        if (ids.size() > MAX_HISTORY) {
            Set<UUID> retained = new HashSet<>(this.history.stream().map(DuelMemory::id).toList());
            ids.retainAll(retained);
        }
        setDirty();
    }

    private record StoryKey(UUID speakerId, UUID playerId) {}

    public List<DuelMemory> history() {
        return List.copyOf(this.history);
    }

    public Reaction consumeReaction(UUID villagerId, UUID playerId, Reaction requested) {
        if (requested == null || requested == Reaction.NONE) return Reaction.NONE;
        Key key = new Key(villagerId, playerId);
        DuelRecord current = record(villagerId, playerId);
        if (requested == Reaction.GLOAT && current.pendingGloats() > 0) {
            this.records.put(key, new DuelRecord(current.villagerWins(), current.villagerLosses(), current.consecutiveLosses(),
                    current.refuses(), current.lastStartGameTime(), current.pendingGloats() - 1, current.pendingSulks()));
            setDirty();
            return Reaction.GLOAT;
        }
        if (requested == Reaction.SULK && current.pendingSulks() > 0) {
            this.records.put(key, new DuelRecord(current.villagerWins(), current.villagerLosses(), current.consecutiveLosses(),
                    current.refuses(), current.lastStartGameTime(), current.pendingGloats(), current.pendingSulks() - 1));
            setDirty();
            return Reaction.SULK;
        }
        return Reaction.NONE;
    }

    public Reaction consumeReaction(UUID villagerId, UUID playerId) {
        Key key = new Key(villagerId, playerId);
        DuelRecord current = record(villagerId, playerId);
        if (current.pendingGloats() > 0) {
            this.records.put(key, new DuelRecord(current.villagerWins(), current.villagerLosses(), current.consecutiveLosses(),
                    current.refuses(), current.lastStartGameTime(), current.pendingGloats() - 1, current.pendingSulks()));
            setDirty();
            return Reaction.GLOAT;
        }
        if (current.pendingSulks() > 0) {
            this.records.put(key, new DuelRecord(current.villagerWins(), current.villagerLosses(), current.consecutiveLosses(),
                    current.refuses(), current.lastStartGameTime(), current.pendingGloats(), current.pendingSulks() - 1));
            setDirty();
            return Reaction.SULK;
        }
        return Reaction.NONE;
    }

    public enum Reaction { NONE, GLOAT, SULK }

    private record Key(UUID villagerId, UUID playerId) {}

    public record DuelRecord(int villagerWins, int villagerLosses, int consecutiveLosses, boolean refuses,
                             long lastStartGameTime, int pendingGloats, int pendingSulks) {
        public static final DuelRecord EMPTY = new DuelRecord(0, 0, 0, false, Long.MIN_VALUE, 0, 0);
        DuelRecord withLastStart(long value) {
            return new DuelRecord(this.villagerWins, this.villagerLosses, this.consecutiveLosses, this.refuses,
                    value, this.pendingGloats, this.pendingSulks);
        }
    }

    public record DuelMemory(UUID id, UUID villagerId, UUID playerId, String villagerName, String playerName,
                             DuelResult result, int wager, long gameTime, long packedPosition, UUID villageId,
                             Set<UUID> witnessIds, int villagerWins, int villagerLosses) {
        public DuelMemory {
            witnessIds = witnessIds == null ? Set.of() : Set.copyOf(witnessIds);
        }
    }
}
