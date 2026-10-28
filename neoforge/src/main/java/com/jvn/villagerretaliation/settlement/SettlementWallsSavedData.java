package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

public final class SettlementWallsSavedData extends SavedData {
    public record Entry(UUID id, UUID group, VillageAllegianceId town, ResourceLocation dimension, SettlementWall wall,
                        Map<BlockPos, BlockState> blocks) {
        public Entry { blocks = Map.copyOf(blocks); }
    }
    private boolean readOnly;
    private final Map<UUID, Entry> walls = new LinkedHashMap<>();
    private final Map<ResourceLocation, Map<BlockPos, UUID>> ownership = new java.util.HashMap<>();

    public static SettlementWallsSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(() -> missing(level), SettlementWallsSavedData::load), "villagerretaliation_walls");
    }

    private static SettlementWallsSavedData missing(ServerLevel level) {
        var data = new SettlementWallsSavedData();
        var file = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/villagerretaliation_walls.dat");
        data.readOnly = !java.nio.file.Files.notExists(file);
        return data;
    }

    public boolean readOnly() { return this.readOnly; }
    public Entry get(UUID id) { return this.walls.get(id); }

    public Entry at(ServerLevel level, VillageAllegianceId town, BlockPos position) {
        var entry = this.walls.get(this.ownership.getOrDefault(level.dimension().location(), Map.of()).get(position));
        if (entry == null) return null;
        return entry.town().equals(town) || SettlementSavedData.get(level).get(entry.town())
                .map(record -> record.id().equals(town)).orElse(false) ? entry : null;
    }

    public Entry at(VillageAllegianceId town, ResourceLocation dimension, BlockPos position) {
        var entry = this.walls.get(this.ownership.getOrDefault(dimension, Map.of()).get(position));
        return entry != null && entry.town().equals(town) ? entry : null;
    }

    public void record(UUID id, VillageAllegianceId town, ServerLevel level, SettlementWall wall, SettlementWallTerrain.Preview preview) {
        if (this.readOnly) throw new IllegalStateException("Wall records are read-only");
        var blocks = new LinkedHashMap<BlockPos, BlockState>();
        preview.cells().stream().filter(cell -> !cell.target().isAir()).forEach(cell -> blocks.put(cell.position(), cell.target()));
        if (wall.part() == SettlementWall.Part.COMPLETE) {
            for (var piece : SettlementWallGeometry.pieces(wall)) {
                var plan = SettlementWallGeometry.build(piece);
                var positions = plan.cells().stream().map(SettlementWallTemplates.Cell::position).collect(java.util.stream.Collectors.toSet());
                var floors = new java.util.HashMap<BlockPos, Integer>();
                plan.foundations().forEach(pos -> floors.merge(pos.atY(0), pos.getY(), Math::min));
                var owned = new LinkedHashMap<BlockPos, BlockState>();
                blocks.entrySet().removeIf(entry -> {
                    var pos = entry.getKey();
                    if (!positions.contains(pos) && pos.getY() >= floors.getOrDefault(pos.atY(0), Integer.MIN_VALUE)) return false;
                    owned.put(pos, entry.getValue()); return true;
                });
                put(new Entry(UUID.randomUUID(), id, town, level.dimension().location(), piece, owned));
            }
        } else {
            var old = this.walls.get(id);
            put(new Entry(id, old == null ? id : old.group(), town, level.dimension().location(), wall, blocks));
        }
        setDirty();
    }

    public void remove(UUID id) {
        if (this.readOnly) throw new IllegalStateException("Wall records are read-only");
        var old = this.walls.remove(id);
        if (old == null) return;
        var index = this.ownership.get(old.dimension());
        old.blocks().keySet().forEach(pos -> index.remove(pos, id));
        setDirty();
    }

    public boolean ownedByOther(ResourceLocation dimension, BlockPos pos, UUID id) {
        var owner = this.ownership.getOrDefault(dimension, Map.of()).get(pos);
        return owner != null && !owner.equals(id);
    }

    private void put(Entry entry) {
        remove(entry.id());
        var index = this.ownership.computeIfAbsent(entry.dimension(), ignored -> new java.util.HashMap<>());
        var unique = new LinkedHashMap<BlockPos, BlockState>();
        entry.blocks().forEach((pos, state) -> {
            if (index.putIfAbsent(pos, entry.id()) == null) unique.put(pos, state);
        });
        if (!unique.isEmpty()) this.walls.put(entry.id(), new Entry(entry.id(), entry.group(), entry.town(), entry.dimension(), entry.wall(), unique));
    }

    public static String modification(net.minecraft.world.level.Level level, Entry entry, java.util.function.Predicate<BlockPos> allowed) {
        var authored = SettlementWallGeometry.build(entry.wall()).cells().stream().filter(cell -> !cell.state().isAir())
                .map(SettlementWallTemplates.Cell::position).collect(java.util.stream.Collectors.toSet());
        int structural = 0, missing = 0;
        for (var block : entry.blocks().entrySet()) {
            var pos = block.getKey();
            if (!allowed.test(pos) || !level.hasChunkAt(pos)) return "Load the entire wall piece inside this settlement";
            boolean shape = authored.contains(pos);
            if (shape) structural++;
            var actual = level.getBlockState(pos);
            if (actual.equals(block.getValue())) continue;
            if (!actual.isAir()) return "This piece contains replacement blocks. Upgrade another piece or use Destroy to preserve the replacements";
            if (shape) missing++;
        }
        return missing > Math.max(1, structural / 5) ? "This piece has more than 20% structural damage. Upgrade another piece or destroy this one" : "";
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider provider) {
        if (this.readOnly) throw new IllegalStateException("Wall records are read-only");
        root.putInt("Version", 1);
        var entries = new ListTag();
        for (var entry : this.walls.values()) {
            var tag = new CompoundTag();
            tag.putUUID("Id", entry.id()); tag.putUUID("Group", entry.group()); tag.putUUID("Town", entry.town().value());
            tag.putString("Dimension", entry.dimension().toString());
            tag.putString("Type", entry.wall().type().name()); tag.putString("Part", entry.wall().part().name());
            tag.putBoolean("Closed", entry.wall().closed()); tag.putInt("Spacing", entry.wall().towerSpacing());
            var nodes = new ListTag();
            for (int i = 0; i < entry.wall().nodes().size(); i++) {
                var node = new CompoundTag();
                node.putLong("Pos", entry.wall().nodes().get(i).asLong());
                node.putString("Tower", entry.wall().nodeTowers().get(i).name());
                nodes.add(node);
            }
            tag.put("Nodes", nodes);
            var blocks = new ListTag();
            entry.blocks().forEach((pos, state) -> {
                var block = new CompoundTag();
                block.putLong("Pos", pos.asLong()); block.put("State", NbtUtils.writeBlockState(state));
                blocks.add(block);
            });
            tag.put("Blocks", blocks);
            entries.add(tag);
        }
        root.put("Walls", entries);
        return root;
    }

    static SettlementWallsSavedData load(CompoundTag root, HolderLookup.Provider provider) {
        if (root.getInt("Version") != 1 || !root.contains("Walls", Tag.TAG_LIST)) throw new IllegalArgumentException("Unsupported wall data version");
        var data = new SettlementWallsSavedData();
        for (var raw : root.getList("Walls", Tag.TAG_COMPOUND)) {
            var tag = (CompoundTag) raw;
            var nodes = new ArrayList<BlockPos>();
            var towers = new ArrayList<SettlementWall.Tower>();
            for (var value : tag.getList("Nodes", Tag.TAG_COMPOUND)) {
                var node = (CompoundTag) value;
                nodes.add(BlockPos.of(node.getLong("Pos"))); towers.add(SettlementWall.Tower.valueOf(node.getString("Tower")));
            }
            var wall = new SettlementWall(SettlementWall.Type.valueOf(tag.getString("Type")), nodes,
                    tag.getBoolean("Closed"), tag.getInt("Spacing"), towers, SettlementWall.Part.valueOf(tag.getString("Part")));
            var blocks = new LinkedHashMap<BlockPos, BlockState>();
            var saved = tag.getList("Blocks", Tag.TAG_COMPOUND);
            if (saved.isEmpty() || saved.size() > SettlementWall.MAX_BLOCKS) throw new IllegalArgumentException("Invalid saved wall size");
            for (var value : saved) {
                var block = (CompoundTag) value;
                var pos = BlockPos.of(block.getLong("Pos"));
                var state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), block.getCompound("State"));
                if (state.isAir() || blocks.putIfAbsent(pos, state) != null) throw new IllegalArgumentException("Invalid saved wall block");
            }
            var id = tag.getUUID("Id");
            var entry = new Entry(id, tag.getUUID("Group"), new VillageAllegianceId(tag.getUUID("Town")), ResourceLocation.parse(tag.getString("Dimension")), wall, blocks);
            if (data.walls.containsKey(id) || blocks.keySet().stream().anyMatch(pos -> data.ownedByOther(entry.dimension(), pos, id)))
                throw new IllegalArgumentException("Duplicate saved wall ownership");
            data.put(entry);
        }
        return data;
    }
}
