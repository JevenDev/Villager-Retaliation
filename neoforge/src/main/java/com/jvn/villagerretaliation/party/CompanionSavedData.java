package com.jvn.villagerretaliation.party;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public final class CompanionSavedData extends SavedData {
    private final Map<UUID, CompoundTag> companions = new LinkedHashMap<>();

    public static CompanionSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CompanionSavedData::new, CompanionSavedData::load, DataFixTypes.LEVEL),
                "villagerretaliation_companions");
    }

    public static CompanionSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        CompanionSavedData data = new CompanionSavedData();
        if (tag.getInt("Version") > 1) throw new IllegalArgumentException("Unsupported companion save version");
        for (Tag raw : tag.getList("Companions", Tag.TAG_COMPOUND)) {
            if (raw instanceof CompoundTag entry && entry.hasUUID("Villager")) {
                data.companions.putIfAbsent(entry.getUUID("Villager"), entry.copy());
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        var entries = new net.minecraft.nbt.ListTag();
        this.companions.values().forEach(value -> entries.add(value.copy()));
        tag.putInt("Version", 1);
        tag.put("Companions", entries);
        return tag;
    }

    public CompoundTag find(UUID villagerId) {
        return this.companions.get(villagerId);
    }

    CompoundTag getOrCreate(UUID villagerId) {
        return this.companions.computeIfAbsent(villagerId, id -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Villager", id);
            setDirty();
            return entry;
        });
    }

    public java.util.List<CompoundTag> forPlayer(UUID playerId) {
        return this.companions.values().stream()
                .filter(tag -> tag.hasUUID("Owner") && tag.getUUID("Owner").equals(playerId))
                .map(CompoundTag::copy).toList();
    }
}
