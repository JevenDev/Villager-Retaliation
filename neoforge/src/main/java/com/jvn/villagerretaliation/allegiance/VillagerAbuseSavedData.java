package com.jvn.villagerretaliation.allegiance;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public final class VillagerAbuseSavedData extends SavedData {
    private static final String DATA_NAME = "villagerretaliation_villager_abuse";
    private static final int FORMAT_VERSION = 1;
    private final Map<AbuseKey, AbuseRecord> records = new LinkedHashMap<>();

    public static VillagerAbuseSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VillagerAbuseSavedData::new, VillagerAbuseSavedData::load, DataFixTypes.LEVEL),
                DATA_NAME);
    }

    public static VillagerAbuseSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        VillagerAbuseSavedData data = new VillagerAbuseSavedData();
        if (tag.getInt("FormatVersion") > FORMAT_VERSION) {
            return data;
        }
        for (Tag raw : tag.getList("Records", Tag.TAG_COMPOUND)) {
            if (raw instanceof CompoundTag recordTag
                    && recordTag.hasUUID("Villager")
                    && recordTag.hasUUID("Player")) {
                AbuseKey key = new AbuseKey(recordTag.getUUID("Villager"), recordTag.getUUID("Player"));
                data.records.put(key, new AbuseRecord(
                        Math.max(0, recordTag.getInt("Hits")),
                        recordTag.getLong("LastIncidentGameTime")));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("FormatVersion", FORMAT_VERSION);
        ListTag recordsTag = new ListTag();
        for (Map.Entry<AbuseKey, AbuseRecord> entry : this.records.entrySet()) {
            CompoundTag recordTag = new CompoundTag();
            recordTag.putUUID("Villager", entry.getKey().villagerId());
            recordTag.putUUID("Player", entry.getKey().playerId());
            recordTag.putInt("Hits", entry.getValue().hits());
            recordTag.putLong("LastIncidentGameTime", entry.getValue().lastIncidentGameTime());
            recordsTag.add(recordTag);
        }
        tag.put("Records", recordsTag);
        return tag;
    }

    public AbuseRecord recordHit(UUID villagerId, UUID playerId, long gameTime) {
        AbuseKey key = new AbuseKey(villagerId, playerId);
        AbuseRecord updated = this.records.getOrDefault(key, new AbuseRecord(0, 0L)).next(gameTime);
        this.records.put(key, updated);
        setDirty();
        return updated;
    }

    public AbuseRecord record(UUID villagerId, UUID playerId) {
        return this.records.getOrDefault(new AbuseKey(villagerId, playerId), new AbuseRecord(0, 0L));
    }

    public boolean reset(UUID villagerId, UUID playerId) {
        boolean removed = this.records.remove(new AbuseKey(villagerId, playerId)) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public void transferVillager(UUID sourceId, UUID targetId) {
        if (sourceId == null || targetId == null || sourceId.equals(targetId)) {
            return;
        }
        Map<AbuseKey, AbuseRecord> moved = new LinkedHashMap<>();
        this.records.entrySet().removeIf(entry -> {
            if (!entry.getKey().villagerId().equals(sourceId)) {
                return false;
            }
            moved.put(new AbuseKey(targetId, entry.getKey().playerId()), entry.getValue());
            return true;
        });
        if (!moved.isEmpty()) {
            moved.forEach((key, value) -> this.records.merge(key, value, AbuseRecord::max));
            setDirty();
        }
    }

    private record AbuseKey(UUID villagerId, UUID playerId) {
    }

    public record AbuseRecord(int hits, long lastIncidentGameTime) {
        private AbuseRecord next(long gameTime) {
            return new AbuseRecord(this.hits + 1, gameTime);
        }

        private static AbuseRecord max(AbuseRecord first, AbuseRecord second) {
            return first.hits >= second.hits ? first : second;
        }
    }
}
