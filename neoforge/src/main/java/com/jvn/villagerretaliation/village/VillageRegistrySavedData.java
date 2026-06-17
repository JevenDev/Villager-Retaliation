package com.jvn.villagerretaliation.village;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public class VillageRegistrySavedData extends SavedData {
    private static final String DATA_NAME = "villagerretaliation_village_registry";
    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_KEY = "Key";
    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_X = "X";
    private static final String TAG_Y = "Y";
    private static final String TAG_Z = "Z";
    private static final String TAG_LAST_SEEN_GAME_TIME = "LastSeenGameTime";
    private static final int MATCH_RADIUS_BLOCKS = 48;
    private static final long MATCH_RADIUS_SQUARED = (long) MATCH_RADIUS_BLOCKS * MATCH_RADIUS_BLOCKS;

    private final Map<String, Entry> entriesByKey = new LinkedHashMap<>();

    public static VillageRegistrySavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VillageRegistrySavedData::new, VillageRegistrySavedData::load, DataFixTypes.LEVEL),
                DATA_NAME
        );
    }

    public static VillageRegistrySavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        VillageRegistrySavedData data = new VillageRegistrySavedData();
        ListTag entriesTag = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (Tag rawEntry : entriesTag) {
            if (!(rawEntry instanceof CompoundTag entryTag)) {
                continue;
            }
            Entry entry = Entry.load(entryTag);
            if (entry != null) {
                data.entriesByKey.put(entry.key(), entry);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag entriesTag = new ListTag();
        for (Entry entry : this.entriesByKey.values()) {
            entriesTag.add(entry.save());
        }
        tag.put(TAG_ENTRIES, entriesTag);
        return tag;
    }

    public String keyFor(ServerLevel level, BlockPos center) {
        if (level == null || center == null) {
            return "";
        }
        ResourceLocation dimension = level.dimension().location();
        Entry existing = nearest(dimension, center);
        if (existing != null) {
            if (existing.needsCenterUpdate(center)) {
                this.entriesByKey.put(existing.key(), existing.with(center, level.getGameTime()));
                setDirty();
            }
            return existing.key();
        }

        String key = VillageScopeKeys.forPosition(dimension, center);
        Entry created = new Entry(key, dimension, center.immutable(), level.getGameTime());
        this.entriesByKey.put(key, created);
        setDirty();
        return key;
    }

    public List<EntrySnapshot> entries() {
        return this.entriesByKey.values()
                .stream()
                .map(EntrySnapshot::from)
                .toList();
    }

    public int size() {
        return this.entriesByKey.size();
    }

    public int pruneNotSeenSince(long minimumLastSeenGameTime) {
        int before = this.entriesByKey.size();
        this.entriesByKey.entrySet().removeIf(entry -> entry.getValue().lastSeenGameTime() < minimumLastSeenGameTime);
        int removed = before - this.entriesByKey.size();
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    public MergeResult mergeKey(String sourceKey, String targetKey) {
        sourceKey = sourceKey == null ? "" : sourceKey.trim();
        targetKey = targetKey == null ? "" : targetKey.trim();
        if (sourceKey.isBlank() || targetKey.isBlank() || sourceKey.equals(targetKey)) {
            return new MergeResult(false, false, this.entriesByKey.containsKey(targetKey), false, false);
        }

        Entry source = this.entriesByKey.remove(sourceKey);
        Entry target = this.entriesByKey.get(targetKey);
        if (source == null) {
            return new MergeResult(false, false, target != null, false, false);
        }

        boolean createdTarget = false;
        boolean updatedTargetLastSeen = false;
        if (target == null) {
            this.entriesByKey.put(targetKey, source.withKey(targetKey));
            createdTarget = true;
        } else if (source.lastSeenGameTime() > target.lastSeenGameTime()) {
            this.entriesByKey.put(targetKey, target.withLastSeenGameTime(source.lastSeenGameTime()));
            updatedTargetLastSeen = true;
        }
        setDirty();
        return new MergeResult(true, true, target != null, createdTarget, updatedTargetLastSeen);
    }

    private Entry nearest(ResourceLocation dimension, BlockPos center) {
        Entry nearest = null;
        long nearestDistance = Long.MAX_VALUE;
        for (Entry entry : this.entriesByKey.values()) {
            if (!entry.dimension().equals(dimension)) {
                continue;
            }
            long distance = entry.distanceSquared(center);
            if (distance <= MATCH_RADIUS_SQUARED && distance < nearestDistance) {
                nearest = entry;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private record Entry(String key, ResourceLocation dimension, BlockPos center, long lastSeenGameTime) {
        private static Entry load(CompoundTag tag) {
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString(TAG_DIMENSION));
            if (dimension == null || !hasCenter(tag)) {
                return null;
            }
            BlockPos center = new BlockPos(tag.getInt(TAG_X), tag.getInt(TAG_Y), tag.getInt(TAG_Z));
            String key = tag.getString(TAG_KEY);
            if (key.isBlank()) {
                key = VillageScopeKeys.forPosition(dimension, center);
            }
            return new Entry(key, dimension, center, tag.getLong(TAG_LAST_SEEN_GAME_TIME));
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString(TAG_KEY, this.key);
            tag.putString(TAG_DIMENSION, this.dimension.toString());
            tag.putInt(TAG_X, this.center.getX());
            tag.putInt(TAG_Y, this.center.getY());
            tag.putInt(TAG_Z, this.center.getZ());
            tag.putLong(TAG_LAST_SEEN_GAME_TIME, this.lastSeenGameTime);
            return tag;
        }

        private boolean needsCenterUpdate(BlockPos center) {
            return !this.center.equals(center);
        }

        private Entry with(BlockPos center, long gameTime) {
            return new Entry(this.key, this.dimension, center.immutable(), gameTime);
        }

        private Entry withKey(String key) {
            String safeKey = key == null ? "" : key;
            ResourceLocation dimension = VillageScopeKeys.dimension(safeKey)
                    .map(ResourceKey::location)
                    .orElse(this.dimension);
            BlockPos center = VillageScopeKeys.pos(safeKey)
                    .orElse(this.center)
                    .immutable();
            return new Entry(safeKey, dimension, center, this.lastSeenGameTime);
        }

        private Entry withLastSeenGameTime(long gameTime) {
            return new Entry(this.key, this.dimension, this.center, gameTime);
        }

        private long distanceSquared(BlockPos pos) {
            long dx = this.center.getX() - pos.getX();
            long dy = this.center.getY() - pos.getY();
            long dz = this.center.getZ() - pos.getZ();
            return dx * dx + dy * dy + dz * dz;
        }

        private static boolean hasCenter(CompoundTag tag) {
            return tag.contains(TAG_X, Tag.TAG_INT)
                    && tag.contains(TAG_Y, Tag.TAG_INT)
                    && tag.contains(TAG_Z, Tag.TAG_INT);
        }
    }

    public record EntrySnapshot(String key, ResourceLocation dimension, BlockPos center, long lastSeenGameTime) {
        private static EntrySnapshot from(Entry entry) {
            return new EntrySnapshot(entry.key(), entry.dimension(), entry.center(), entry.lastSeenGameTime());
        }

        public long ageTicks(long gameTime) {
            return Math.max(0L, gameTime - this.lastSeenGameTime);
        }
    }

    public record MergeResult(
            boolean changed,
            boolean sourceFound,
            boolean targetFound,
            boolean targetCreated,
            boolean targetLastSeenUpdated) {
    }
}
