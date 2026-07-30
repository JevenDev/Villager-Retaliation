package com.jvn.villagerretaliation.profile;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
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
    private static final String TAG_RETIRED_PROFILES = "RetiredProfiles";
    private static final String TAG_VILLAGER = "Villager";
    private static final String TAG_RETIRED_AT_GAME_TIME = "RetiredAtGameTime";
    private static final String TAG_RETIREMENT_REASON = "RetirementReason";

    private final Map<UUID, VillagerProfile> profiles = new HashMap<>();
    private final Map<UUID, RetiredProfile> retiredProfiles = new HashMap<>();

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

        ListTag retiredProfilesTag = tag.getList(TAG_RETIRED_PROFILES, Tag.TAG_COMPOUND);
        for (Tag rawEntry : retiredProfilesTag) {
            if (!(rawEntry instanceof CompoundTag entryTag) || !entryTag.hasUUID(TAG_VILLAGER)) {
                continue;
            }
            UUID villagerId = entryTag.getUUID(TAG_VILLAGER);
            RetirementReason reason = RetirementReason.fromSerializedName(entryTag.getString(TAG_RETIREMENT_REASON));
            if (!data.profiles.containsKey(villagerId) || reason == null) {
                continue;
            }
            data.retiredProfiles.put(
                    villagerId,
                    new RetiredProfile(Math.max(0L, entryTag.getLong(TAG_RETIRED_AT_GAME_TIME)), reason)
            );
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

        ListTag retiredProfilesTag = new ListTag();
        for (Map.Entry<UUID, RetiredProfile> entry : this.retiredProfiles.entrySet()) {
            CompoundTag retiredTag = new CompoundTag();
            retiredTag.putUUID(TAG_VILLAGER, entry.getKey());
            retiredTag.putLong(TAG_RETIRED_AT_GAME_TIME, entry.getValue().retiredAtGameTime());
            retiredTag.putString(TAG_RETIREMENT_REASON, entry.getValue().reason().serializedName());
            retiredProfilesTag.add(retiredTag);
        }
        tag.put(TAG_RETIRED_PROFILES, retiredProfilesTag);
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
        this.retiredProfiles.remove(profile.villagerUuid());
        setDirty();
        return previous;
    }

    public boolean transferVillagerProfile(UUID sourceVillagerId, UUID targetVillagerId) {
        if (sourceVillagerId == null || targetVillagerId == null || sourceVillagerId.equals(targetVillagerId)) {
            return false;
        }

        VillagerProfile source = this.profiles.remove(sourceVillagerId);
        boolean changed = this.retiredProfiles.remove(sourceVillagerId) != null;
        if (source == null) {
            changed |= this.retiredProfiles.remove(targetVillagerId) != null;
            if (changed) {
                setDirty();
            }
            return changed;
        }
        this.profiles.put(targetVillagerId, source.copyFor(targetVillagerId));
        this.retiredProfiles.remove(targetVillagerId);
        setDirty();
        return true;
    }

    public boolean reactivate(UUID villagerUuid) {
        if (this.retiredProfiles.remove(villagerUuid) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    public boolean retire(UUID villagerUuid, long gameTime, RetirementReason reason) {
        if (!this.profiles.containsKey(villagerUuid)
                || reason == null
                || this.retiredProfiles.containsKey(villagerUuid)) {
            return false;
        }
        this.retiredProfiles.put(villagerUuid, new RetiredProfile(Math.max(0L, gameTime), reason));
        setDirty();
        return true;
    }

    public int pruneRetiredProfiles(long retiredOnOrBeforeGameTime) {
        int removed = 0;
        Iterator<Map.Entry<UUID, RetiredProfile>> iterator = this.retiredProfiles.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, RetiredProfile> entry = iterator.next();
            if (entry.getValue().retiredAtGameTime() > retiredOnOrBeforeGameTime) {
                continue;
            }
            this.profiles.remove(entry.getKey());
            iterator.remove();
            removed++;
        }
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    public Collection<VillagerProfile> profiles() {
        return this.profiles.values();
    }

    public enum RetirementReason {
        DEATH("death"),
        NATURAL_DESPAWN("natural_despawn");

        private final String serializedName;

        RetirementReason(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return this.serializedName;
        }

        private static RetirementReason fromSerializedName(String value) {
            for (RetirementReason reason : values()) {
                if (reason.serializedName.equals(value)) {
                    return reason;
                }
            }
            return null;
        }
    }

    private record RetiredProfile(long retiredAtGameTime, RetirementReason reason) {
    }
}
