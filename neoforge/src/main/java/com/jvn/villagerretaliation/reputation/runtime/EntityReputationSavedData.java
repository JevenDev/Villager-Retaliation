package com.jvn.villagerretaliation.reputation.runtime;

import com.jvn.villagerretaliation.util.NbtDataUtil;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class EntityReputationSavedData extends SavedData implements ReputationStore<ReputationState> {
    private final Map<Key, ReputationState> entries = new LinkedHashMap<>();

    public static EntityReputationSavedData get(ServerLevel level, String dataName) {
        if (dataName == null || !dataName.matches("[a-z0-9_.-]{1,128}")) {
            throw new IllegalArgumentException("reputation data name must be a safe saved-data identifier");
        }
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(EntityReputationSavedData::new, EntityReputationSavedData::load), dataName);
    }

    public static EntityReputationSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new EntityReputationSavedData();
        for (Tag raw : tag.getList("Entries", Tag.TAG_COMPOUND)) {
            if (!(raw instanceof CompoundTag entry) || !entry.hasUUID("Entity") || !entry.hasUUID("Player")) continue;
            var state = data.getOrCreate(entry.getUUID("Entity"), entry.getUUID("Player"));
            state.setReputation(entry.getInt("Reputation"));
            state.setLastInteractionGameTime(entry.getLong("LastInteractionGameTime"));
            state.setPosition(NbtDataUtil.readBlockPos(entry, "Position").orElse(null));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var entries = new ListTag();
        for (var entry : this.entries.entrySet()) {
            var value = new CompoundTag();
            value.putUUID("Entity", entry.getKey().entity());
            value.putUUID("Player", entry.getKey().player());
            value.putInt("Reputation", entry.getValue().reputation());
            value.putLong("LastInteractionGameTime", entry.getValue().lastInteractionGameTime());
            NbtDataUtil.putBlockPos(value, "Position", entry.getValue().position());
            entries.add(value);
        }
        tag.put("Entries", entries);
        return tag;
    }

    @Override
    public ReputationState get(UUID entityId, UUID playerId) {
        return this.entries.get(new Key(entityId, playerId));
    }

    @Override
    public ReputationState getOrCreate(UUID entityId, UUID playerId) {
        return this.entries.computeIfAbsent(new Key(entityId, playerId), key -> new ReputationState());
    }

    @Override
    public void changed() { setDirty(); }

    private record Key(UUID entity, UUID player) { }
}
