package com.jvn.villagerretaliation.village;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent memories of raids that a player and one of their follower villagers faced together. */
public final class VillagerRaidMemorySavedData extends SavedData {
    private static final String DATA_NAME = "villagerretaliation_raid_memories";
    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_VILLAGER = "Villager";
    private static final String TAG_PLAYER = "Player";
    private static final String TAG_OUTCOME = "Outcome";
    private static final String TAG_GAME_TIME = "GameTime";
    private static final String TAG_PENDING_VICTORY = "PendingVictory";

    private final Map<MemoryKey, RaidMemory> memories = new LinkedHashMap<>();

    public static VillagerRaidMemorySavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VillagerRaidMemorySavedData::new, VillagerRaidMemorySavedData::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static VillagerRaidMemorySavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        VillagerRaidMemorySavedData data = new VillagerRaidMemorySavedData();
        for (Tag rawEntry : tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND)) {
            if (!(rawEntry instanceof CompoundTag entryTag)
                    || !entryTag.hasUUID(TAG_VILLAGER)
                    || !entryTag.hasUUID(TAG_PLAYER)) {
                continue;
            }
            try {
                UUID villagerId = entryTag.getUUID(TAG_VILLAGER);
                UUID playerId = entryTag.getUUID(TAG_PLAYER);
                RaidOutcome outcome = RaidOutcome.valueOf(entryTag.getString(TAG_OUTCOME));
                data.memories.put(
                        new MemoryKey(villagerId, playerId),
                        new RaidMemory(outcome, entryTag.getLong(TAG_GAME_TIME), entryTag.getBoolean(TAG_PENDING_VICTORY))
                );
            } catch (IllegalArgumentException ignored) {
                // Skip malformed or obsolete entries without preventing the rest of the save from loading.
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag entriesTag = new ListTag();
        for (Map.Entry<MemoryKey, RaidMemory> entry : this.memories.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID(TAG_VILLAGER, entry.getKey().villagerId());
            entryTag.putUUID(TAG_PLAYER, entry.getKey().playerId());
            entryTag.putString(TAG_OUTCOME, entry.getValue().outcome().name());
            entryTag.putLong(TAG_GAME_TIME, entry.getValue().gameTime());
            entryTag.putBoolean(TAG_PENDING_VICTORY, entry.getValue().unacknowledgedVictory());
            entriesTag.add(entryTag);
        }
        tag.put(TAG_ENTRIES, entriesTag);
        return tag;
    }

    public void remember(UUID villagerId, UUID playerId, RaidOutcome outcome, long gameTime) {
        MemoryKey key = new MemoryKey(villagerId, playerId);
        RaidMemory previous = this.memories.get(key);
        boolean pendingVictory = outcome == RaidOutcome.VICTORY
                || previous != null && previous.unacknowledgedVictory();
        this.memories.put(key, new RaidMemory(outcome, gameTime, pendingVictory));
        setDirty();
    }

    public Optional<RaidMemory> memory(UUID villagerId, UUID playerId) {
        return Optional.ofNullable(this.memories.get(new MemoryKey(villagerId, playerId)));
    }

    public boolean hasUnacknowledgedVictory(UUID villagerId, UUID playerId) {
        RaidMemory memory = this.memories.get(new MemoryKey(villagerId, playerId));
        return memory != null && memory.unacknowledgedVictory();
    }

    public boolean claimVictoryAcknowledgement(UUID villagerId, UUID playerId) {
        MemoryKey key = new MemoryKey(villagerId, playerId);
        RaidMemory memory = this.memories.get(key);
        if (memory == null || !memory.unacknowledgedVictory()) {
            return false;
        }
        this.memories.put(key, new RaidMemory(memory.outcome(), memory.gameTime(), false));
        setDirty();
        return true;
    }

    private record MemoryKey(UUID villagerId, UUID playerId) {
    }

    public record RaidMemory(RaidOutcome outcome, long gameTime, boolean unacknowledgedVictory) {
    }

    public enum RaidOutcome {
        VICTORY,
        LOSS
    }
}
