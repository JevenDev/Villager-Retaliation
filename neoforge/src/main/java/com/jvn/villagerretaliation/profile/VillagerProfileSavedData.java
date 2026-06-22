package com.jvn.villagerretaliation.profile;

import java.util.Collection;
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

public class VillagerProfileSavedData extends SavedData {
    public static final String DATA_NAME = "villagerretaliation_villager_profiles";

    private static final String TAG_ENTRIES = "Entries";

    private final Map<UUID, VillagerProfile> profiles = new HashMap<>();

    public static VillagerProfileSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VillagerProfileSavedData::new, VillagerProfileSavedData::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static VillagerProfileSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        VillagerProfileSavedData data = new VillagerProfileSavedData();
        ListTag entriesTag = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (Tag rawEntry : entriesTag) {
            if (!(rawEntry instanceof CompoundTag entryTag)) {
                continue;
            }
            VillagerProfile profile = VillagerProfile.load(entryTag);
            if (profile != null) {
                data.profiles.put(profile.villagerUuid(), profile);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag entriesTag = new ListTag();
        for (VillagerProfile profile : this.profiles.values()) {
            entriesTag.add(profile.save());
        }
        tag.put(TAG_ENTRIES, entriesTag);
        return tag;
    }

    public VillagerProfile get(UUID villagerUuid) {
        return this.profiles.get(villagerUuid);
    }

    public boolean hasProfile(UUID villagerUuid) {
        return this.profiles.containsKey(villagerUuid);
    }

    public VillagerProfile put(VillagerProfile profile) {
        VillagerProfile previous = this.profiles.put(profile.villagerUuid(), profile);
        setDirty();
        return previous;
    }

    public boolean transferVillagerProfile(UUID sourceVillagerId, UUID targetVillagerId) {
        if (sourceVillagerId == null || targetVillagerId == null || sourceVillagerId.equals(targetVillagerId)) {
            return false;
        }

        VillagerProfile source = this.profiles.remove(sourceVillagerId);
        if (source == null) {
            return false;
        }
        this.profiles.put(targetVillagerId, source.copyFor(targetVillagerId));
        setDirty();
        return true;
    }

    public Collection<VillagerProfile> profiles() {
        return this.profiles.values();
    }
}
