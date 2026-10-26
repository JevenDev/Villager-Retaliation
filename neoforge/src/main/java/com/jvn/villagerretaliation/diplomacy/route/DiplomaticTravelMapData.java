package com.jvn.villagerretaliation.diplomacy.route;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public final class DiplomaticTravelMapData extends SavedData {
    private static final int VILLAGE_LIMIT = 4096, COLUMN_LIMIT = 16384, GLOBAL_LIMIT = 262144;
    public record Cell(long sequence, ResourceLocation dimension, BlockPos position, int color) { }
    public record Page(long epoch, long next, boolean more, List<Cell> cells) { }
    private record Location(ResourceLocation dimension, int x, int z) { }
    private static final class History {
        long sequence, epoch;
        final TreeMap<Long, Cell> cells = new TreeMap<>();
        final Map<Location, Cell> positions = new LinkedHashMap<>();
    }
    private final Map<VillageAllegianceId, History> villages = new LinkedHashMap<>();
    private CompoundTag preserved;
    private int columns;
    public static DiplomaticTravelMapData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(DiplomaticTravelMapData::new,
                DiplomaticTravelMapData::load, DataFixTypes.LEVEL), "villagerretaliation_diplomatic_travel_map");
    }
    public boolean readOnly() { return this.preserved != null; }
    public void observe(ServerLevel level, VillageAllegianceId village, BlockPos position) {
        if (!level.getServer().isSameThread() || readOnly()) return;
        var history = this.villages.get(village);
        if (history == null) {
            if (this.villages.size() >= VILLAGE_LIMIT) return;
            history = new History(); this.villages.put(village, history);
        }
        var terrain = RouteSavedData.get(level); var dimension = level.dimension().location();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            int x = position.getX() + dx, z = position.getZ() + dz;
            var tile = terrain.observation(dimension, x, z);
            if (tile == null || tile.color(x, z) == 0) continue;
            var location = new Location(dimension, x, z); var previous = history.positions.get(location);
            var at = new BlockPos(x, tile.height(x, z), z);
            if (previous != null && previous.position().equals(at) && previous.color() == tile.color(x, z)) continue;
            if (history.sequence == Long.MAX_VALUE) return;
            if (previous != null) { history.cells.remove(previous.sequence()); this.columns--; }
            if (history.cells.size() >= COLUMN_LIMIT) evict(history);
            if (this.columns >= GLOBAL_LIMIT) {
                if (history.cells.isEmpty()) continue;
                evict(history);
            }
            var cell = new Cell(++history.sequence, dimension, at, tile.color(x, z));
            history.cells.put(cell.sequence(), cell); history.positions.put(location, cell); this.columns++; setDirty();
        }
    }
    public void merge(VillageAllegianceId source, VillageAllegianceId target) {
        if (readOnly() || source.equals(target)) return;
        var previous = this.villages.get(source); if (previous == null) return;
        var current = this.villages.get(target);
        long epoch = Math.max(previous.sequence, current == null ? 0 : current.sequence);
        if (epoch > Long.MAX_VALUE - COLUMN_LIMIT) return;
        var cells = new LinkedHashMap<Location, Cell>(previous.positions);
        if (current != null) cells.putAll(current.positions);
        while (cells.size() > COLUMN_LIMIT) cells.remove(cells.keySet().iterator().next());
        this.columns -= previous.cells.size() + (current == null ? 0 : current.cells.size());
        var combined = new History(); combined.epoch = epoch; combined.sequence = epoch;
        for (var entry : cells.entrySet()) {
            var cell = entry.getValue(); var value = new Cell(++combined.sequence, cell.dimension(), cell.position(), cell.color());
            combined.positions.put(entry.getKey(), value); combined.cells.put(value.sequence(), value); this.columns++;
        }
        this.villages.remove(source); this.villages.put(target, combined); setDirty();
    }
    private void evict(History history) {
        var oldest = history.cells.pollFirstEntry(); if (oldest == null) return;
        var cell = oldest.getValue(); history.positions.remove(new Location(cell.dimension(), cell.position().getX(), cell.position().getZ()));
        history.epoch = Math.max(history.epoch, oldest.getKey()); this.columns--; setDirty();
    }
    public Page page(VillageAllegianceId village, long epoch, long after, ResourceLocation dimension) {
        var history = this.villages.get(village);
        if (history == null || readOnly()) return new Page(0, 0, false, List.of());
        long start = epoch == history.epoch ? Math.clamp(after, 0, history.sequence) : 0;
        var cells = new ArrayList<Cell>(); long next = start; boolean more = false;
        int examined = 0;
        for (var cell : history.cells.tailMap(start, false).values()) {
            if (cells.size() == 64 || examined == 256) { more = true; break; }
            next = cell.sequence(); examined++;
            if (cell.dimension().equals(dimension)) cells.add(cell);
        }
        if (!more) next = history.sequence;
        return new Page(history.epoch, next, more, List.copyOf(cells));
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (this.preserved != null) return this.preserved.copy();
        tag.putInt("Version", 1); var villages = new ListTag();
        this.villages.forEach((id, history) -> {
            var entry = new CompoundTag(); entry.putUUID("Village", id.value()); entry.putLong("Sequence", history.sequence); entry.putLong("Epoch", history.epoch);
            var cells = new ListTag(); history.cells.values().forEach(cell -> {
                var column = new CompoundTag(); column.putLong("Sequence", cell.sequence()); column.putString("Dimension", cell.dimension().toString());
                column.putLong("Position", cell.position().asLong()); column.putInt("Color", cell.color()); cells.add(column);
            }); entry.put("Cells", cells); villages.add(entry);
        }); tag.put("Villages", villages); return tag;
    }
    static DiplomaticTravelMapData load(CompoundTag tag, HolderLookup.Provider provider) {
        var data = new DiplomaticTravelMapData();
        try {
            var villages = tag.getList("Villages", Tag.TAG_COMPOUND);
            if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1 || !villages.equals(tag.get("Villages"))
                    || villages.size() > VILLAGE_LIMIT) throw new IllegalArgumentException("Unsupported diplomatic travel map");
            for (var raw : villages) {
                var entry = (CompoundTag) raw; var cells = entry.getList("Cells", Tag.TAG_COMPOUND);
                if (!entry.hasUUID("Village") || !entry.contains("Sequence", Tag.TAG_LONG) || !entry.contains("Epoch", Tag.TAG_LONG)
                        || !cells.equals(entry.get("Cells")) || cells.size() > COLUMN_LIMIT) throw new IllegalArgumentException("Invalid diplomatic map village");
                var history = new History(); history.sequence = entry.getLong("Sequence"); history.epoch = entry.getLong("Epoch");
                if (history.sequence < 0 || history.epoch < 0 || history.epoch > history.sequence
                        || data.villages.put(new VillageAllegianceId(entry.getUUID("Village")), history) != null) throw new IllegalArgumentException("Invalid diplomatic map sequence");
                for (var column : cells) {
                    var cell = (CompoundTag) column;
                    if (!cell.contains("Sequence", Tag.TAG_LONG) || !cell.contains("Dimension", Tag.TAG_STRING)
                            || !cell.contains("Position", Tag.TAG_LONG) || !cell.contains("Color", Tag.TAG_INT)) throw new IllegalArgumentException("Missing diplomatic map column");
                    var value = new Cell(cell.getLong("Sequence"), ResourceLocation.parse(cell.getString("Dimension")), BlockPos.of(cell.getLong("Position")), cell.getInt("Color"));
                    if (value.sequence() <= history.epoch || value.sequence() > history.sequence || value.color() <= 0 || value.color() > 0xffffff
                            || history.cells.put(value.sequence(), value) != null || history.positions.put(new Location(value.dimension(), value.position().getX(), value.position().getZ()), value) != null
                            || ++data.columns > GLOBAL_LIMIT) throw new IllegalArgumentException("Invalid diplomatic map column");
                }
            }
        } catch (IllegalArgumentException | IllegalStateException | net.minecraft.ResourceLocationException failure) {
            LogUtils.getLogger().error("Diplomatic travel map is preserved read-only", failure); data.preserved = tag.copy(); data.villages.clear(); data.columns = 0;
        }
        return data;
    }
}
