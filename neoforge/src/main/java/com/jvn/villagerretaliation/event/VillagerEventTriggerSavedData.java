package com.jvn.villagerretaliation.event;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public class VillagerEventTriggerSavedData extends SavedData {
    private static final String DATA_NAME = "villagerretaliation_event_triggers";
    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_KEY = "Key";
    private static final String TAG_LAST_RUN_TIME = "LastRunGameTime";

    private final Map<String, Long> lastRunTimes = new HashMap<>();

    public static VillagerEventTriggerSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VillagerEventTriggerSavedData::new, VillagerEventTriggerSavedData::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static VillagerEventTriggerSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        VillagerEventTriggerSavedData data = new VillagerEventTriggerSavedData();
        ListTag entriesTag = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (Tag rawEntry : entriesTag) {
            if (!(rawEntry instanceof CompoundTag entryTag) || !entryTag.contains(TAG_KEY, Tag.TAG_STRING)) {
                continue;
            }
            String key = entryTag.getString(TAG_KEY);
            if (!key.isBlank()) {
                data.lastRunTimes.put(key, entryTag.getLong(TAG_LAST_RUN_TIME));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag entriesTag = new ListTag();
        for (Map.Entry<String, Long> entry : this.lastRunTimes.entrySet()) {
            if (entry.getKey().isBlank() || entry.getValue() <= 0L) {
                continue;
            }
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString(TAG_KEY, entry.getKey());
            entryTag.putLong(TAG_LAST_RUN_TIME, entry.getValue());
            entriesTag.add(entryTag);
        }
        tag.put(TAG_ENTRIES, entriesTag);
        return tag;
    }

    public long lastRunGameTime(String key) {
        if (key == null || key.isBlank()) {
            return 0L;
        }
        return this.lastRunTimes.getOrDefault(key, 0L);
    }

    public void markRun(String key, long gameTime) {
        if (key == null || key.isBlank() || gameTime <= 0L) {
            return;
        }
        this.lastRunTimes.put(key, gameTime);
        setDirty();
    }

    public CooldownMergeResult mergeScopeKey(String sourceScopeKey, String targetScopeKey) {
        sourceScopeKey = sourceScopeKey == null ? "" : sourceScopeKey.trim();
        targetScopeKey = targetScopeKey == null ? "" : targetScopeKey.trim();
        if (sourceScopeKey.isBlank() || targetScopeKey.isBlank() || sourceScopeKey.equals(targetScopeKey)) {
            return CooldownMergeResult.empty();
        }

        String sourceSuffix = "|" + sourceScopeKey;
        int moved = 0;
        int merged = 0;
        int updatedTargetTimes = 0;
        Map<String, Long> matching = new HashMap<>();
        for (Map.Entry<String, Long> entry : this.lastRunTimes.entrySet()) {
            if (entry.getKey().endsWith(sourceSuffix)) {
                matching.put(entry.getKey(), entry.getValue());
            }
        }

        for (Map.Entry<String, Long> entry : matching.entrySet()) {
            String sourceKey = entry.getKey();
            long sourceTime = entry.getValue();
            this.lastRunTimes.remove(sourceKey);
            String targetKey = sourceKey.substring(0, sourceKey.length() - sourceScopeKey.length()) + targetScopeKey;
            Long existing = this.lastRunTimes.get(targetKey);
            if (existing == null) {
                this.lastRunTimes.put(targetKey, sourceTime);
                moved++;
            } else {
                this.lastRunTimes.put(targetKey, Math.max(existing, sourceTime));
                merged++;
                if (sourceTime > existing) {
                    updatedTargetTimes++;
                }
            }
        }

        if (!matching.isEmpty()) {
            setDirty();
        }
        return new CooldownMergeResult(!matching.isEmpty(), moved, merged, updatedTargetTimes);
    }

    public record CooldownMergeResult(
            boolean changed,
            int moved,
            int merged,
            int updatedTargetTimes) {
        private static CooldownMergeResult empty() {
            return new CooldownMergeResult(false, 0, 0, 0);
        }
    }
}
