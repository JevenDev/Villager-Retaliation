package com.jvn.villagerretaliation.diplomacy.route;

import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public final class RouteSavedData extends SavedData {
    static final int TILE_LIMIT = 16384, JOB_LIMIT = 256;
    record Key(ResourceLocation dimension, long chunk) { }
    final Map<Key, RouteTerrain> terrain = new LinkedHashMap<>();
    final Map<UUID, RoutePreparation> jobs = new LinkedHashMap<>();
    private CompoundTag preserved;
    long revision;

    public static RouteSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RouteSavedData::new, RouteSavedData::load, DataFixTypes.LEVEL), "villagerretaliation_diplomatic_routes");
    }
    public boolean readOnly() { return this.preserved != null || this.revision == Long.MAX_VALUE; }
    public RoutePreparation get(UUID id) { return this.jobs.get(id); }
    public List<RoutePreparation> preparations() { return List.copyOf(this.jobs.values()); }
    void changed() { this.revision++; setDirty(); }
    void observe(ResourceLocation dimension, RouteTerrain tile) {
        if (readOnly()) return;
        var key = new Key(dimension, tile.chunk().toLong());
        if (!this.terrain.containsKey(key) && this.terrain.size() >= TILE_LIMIT) {
            var oldest = this.terrain.keySet().iterator().next();
            this.terrain.remove(oldest);
        }
        this.terrain.put(key, tile);
        changed();
    }
    Map<Long, RouteTerrain> snapshot(ResourceLocation dimension, RouteSearch.Bounds bounds, long now) {
        var result = new LinkedHashMap<Long, RouteTerrain>();
        this.terrain.forEach((key, tile) -> {
            if (key.dimension.equals(dimension) && bounds.contains(tile.chunk().x, tile.chunk().z)
                    && tile.observed() <= now && now - tile.observed() <= 24000) result.put(key.chunk, tile);
        });
        return Map.copyOf(result);
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (this.preserved != null) return this.preserved.copy();
        tag.putInt("Version", 1);
        tag.putLong("Revision", this.revision);
        var tiles = new ListTag();
        this.terrain.forEach((key, tile) -> {
            var entry = tile.save();
            entry.putString("Dimension", key.dimension.toString());
            tiles.add(entry);
        });
        tag.put("Terrain", tiles);
        var jobs = new ListTag();
        this.jobs.values().forEach(job -> jobs.add(job.save()));
        tag.put("Preparations", jobs);
        return tag;
    }
    public static RouteSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        var result = new RouteSavedData();
        try {
            if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1
                    || !tag.contains("Revision", Tag.TAG_LONG) || tag.getLong("Revision") < 0)
                throw new IllegalArgumentException("Unsupported route save");
            result.revision = tag.getLong("Revision");
            var tiles = tag.getList("Terrain", Tag.TAG_COMPOUND);
            var jobs = tag.getList("Preparations", Tag.TAG_COMPOUND);
            if (!tiles.equals(tag.get("Terrain")) || !jobs.equals(tag.get("Preparations"))
                    || tiles.size() > TILE_LIMIT || jobs.size() > JOB_LIMIT) throw new IllegalArgumentException("Invalid route ledger bounds");
            for (var raw : tiles) {
                var entry = (CompoundTag) raw;
                var tile = RouteTerrain.load(entry);
                var key = new Key(ResourceLocation.parse(entry.getString("Dimension")), tile.chunk().toLong());
                if (result.terrain.put(key, tile) != null) throw new IllegalArgumentException("Duplicate terrain tile");
            }
            for (var raw : jobs) {
                var job = RoutePreparation.load((CompoundTag) raw);
                if (result.jobs.put(job.id(), job) != null) throw new IllegalArgumentException("Duplicate route preparation");
            }
        } catch (IllegalArgumentException | net.minecraft.ResourceLocationException failure) {
            result.preserved = tag.copy();
            result.terrain.clear();
            result.jobs.clear();
            LogUtils.getLogger().error("Diplomatic route data preserved read-only: {}", failure.getMessage());
        }
        return result;
    }
}
