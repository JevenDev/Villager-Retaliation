package com.jvn.villagerretaliation.mood;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public class VillagerMoodSavedData extends SavedData {
    public static final String DATA_NAME = "villagerretaliation_villager_moods";

    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_VILLAGER = "Villager";
    private static final String TAG_STATE = "State";

    private final Map<UUID, VillagerMoodState> moods = new HashMap<>();

    public static VillagerMoodSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VillagerMoodSavedData::new, VillagerMoodSavedData::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static VillagerMoodSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        VillagerMoodSavedData data = new VillagerMoodSavedData();
        ListTag entriesTag = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (Tag rawEntry : entriesTag) {
            if (!(rawEntry instanceof CompoundTag entryTag) || !entryTag.hasUUID(TAG_VILLAGER)) {
                continue;
            }
            VillagerMoodState state = entryTag.contains(TAG_STATE, Tag.TAG_COMPOUND)
                    ? VillagerMoodState.load(entryTag.getCompound(TAG_STATE))
                    : VillagerMoodState.DEFAULT;
            if (!state.isNeutral()) {
                data.moods.put(entryTag.getUUID(TAG_VILLAGER), state);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag entriesTag = new ListTag();
        for (Map.Entry<UUID, VillagerMoodState> entry : this.moods.entrySet()) {
            if (entry.getValue().isNeutral()) {
                continue;
            }
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID(TAG_VILLAGER, entry.getKey());
            entryTag.put(TAG_STATE, entry.getValue().save());
            entriesTag.add(entryTag);
        }
        tag.put(TAG_ENTRIES, entriesTag);
        return tag;
    }

    public VillagerMoodState get(UUID villagerUuid) {
        return this.moods.getOrDefault(villagerUuid, VillagerMoodState.DEFAULT);
    }

    public void put(UUID villagerUuid, VillagerMoodState state) {
        VillagerMoodState safeState = state == null ? VillagerMoodState.DEFAULT : state;
        VillagerMoodState previous;
        boolean changed;
        if (safeState.isNeutral()) {
            previous = this.moods.remove(villagerUuid);
            changed = previous != null;
        } else {
            previous = this.moods.put(villagerUuid, safeState);
            changed = previous == null || !previous.equals(safeState);
        }
        if (changed) {
            setDirty();
        }
    }

    public boolean transferVillagerMood(UUID sourceVillagerId, UUID targetVillagerId) {
        if (sourceVillagerId == null || targetVillagerId == null || sourceVillagerId.equals(targetVillagerId)) {
            return false;
        }

        VillagerMoodState source = this.moods.remove(sourceVillagerId);
        if (source == null) {
            return false;
        }
        if (!source.isNeutral()) {
            this.moods.put(targetVillagerId, source);
        }
        setDirty();
        return true;
    }
}
