package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.builder.BuilderConstructionCost;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

final class SettlementWallConstruction {
    private record Cell(BlockPos position, BlockState state, int stage) {}
    private final SettlementWall wall;
    private final List<Cell> cells;
    private final SettlementWallMaterials.Cost cost;
    private final Map<BlockPos, Integer> indices = new HashMap<>();
    private BuilderStructureScanner.StructurePlan plan;
    private boolean recorded;

    private SettlementWallConstruction(SettlementWall wall, List<Cell> cells, SettlementWallMaterials.Cost cost) {
        if (wall.part() != SettlementWall.Part.COMPLETE || cells.isEmpty() || cells.size() > SettlementWall.MAX_BLOCKS
                || cost.stone() < 0 || cost.logs() < 0 || cost.stone() > BuilderConstructionCost.MAX_COUNT
                || cost.logs() > BuilderConstructionCost.MAX_COUNT || cost.stone() + cost.logs() == 0)
            throw new IllegalArgumentException("Invalid wall construction");
        this.wall = wall;
        this.cells = List.copyOf(cells);
        this.cost = cost;
        int previous = -1;
        for (int index = 0; index < cells.size(); index++) {
            var cell = cells.get(index);
            if (cell.state().isAir() || cell.state().hasBlockEntity() || cell.stage() < 0 || cell.stage() < previous || cell.stage() > 127
                    || this.indices.putIfAbsent(cell.position(), index) != null)
                throw new IllegalArgumentException("Invalid wall construction cell");
            previous = cell.stage();
        }
    }

    static SettlementWallConstruction create(SettlementWall wall, SettlementWallTerrain.Preview preview) {
        var remaining = new LinkedHashMap<BlockPos, BlockState>();
        preview.cells().stream().filter(cell -> cell.kind() == SettlementWallTerrain.Kind.PLACE && !cell.target().isAir())
                .forEach(cell -> remaining.put(cell.position(), cell.target()));
        var nodes = SettlementWallGeometry.towers(wall);
        var cells = new ArrayList<Cell>();
        for (var piece : SettlementWallGeometry.pieces(wall)) {
            int node = nodes.indexOf(piece.nodes().getFirst());
            int stage = piece.part() == SettlementWall.Part.TOWER ? Math.max(0, node * 2 - 2) : node * 2 + 1;
            var geometry = SettlementWallGeometry.build(piece);
            var positions = geometry.cells().stream().map(SettlementWallTemplates.Cell::position).collect(java.util.stream.Collectors.toSet());
            var floors = new HashMap<BlockPos, Integer>();
            geometry.foundations().forEach(pos -> floors.merge(pos.atY(0), pos.getY(), Math::min));
            remaining.entrySet().removeIf(entry -> {
                var pos = entry.getKey();
                if (!positions.contains(pos) && pos.getY() >= floors.getOrDefault(pos.atY(0), Integer.MIN_VALUE)) return false;
                cells.add(new Cell(pos, entry.getValue(), stage));
                return true;
            });
        }
        if (!remaining.isEmpty()) throw new IllegalArgumentException("Wall blocks have no construction step");
        cells.sort(Comparator.comparingInt(Cell::stage).thenComparingInt(cell -> cell.position().getY())
                .thenComparingInt(cell -> cell.position().getX()).thenComparingInt(cell -> cell.position().getZ()));
        return new SettlementWallConstruction(wall, cells, SettlementWallMaterials.cost(wall, preview, null));
    }

    BlockPos origin() {
        return new BlockPos(this.cells.stream().mapToInt(cell -> cell.position().getX()).min().orElseThrow(),
                this.cells.stream().mapToInt(cell -> cell.position().getY()).min().orElseThrow(),
                this.cells.stream().mapToInt(cell -> cell.position().getZ()).min().orElseThrow());
    }

    BuilderStructureScanner.StructurePlan plan(UUID id) {
        if (this.plan != null) return this.plan;
        var origin = origin();
        var blocks = this.cells.stream().map(cell -> new BuilderStructureScanner.BuildBlock(cell.position().subtract(origin),
                cell.state(), null, new ItemStack(cell.state().getBlock()), BuilderStructureScanner.BuilderToolAction.NONE)).toList();
        var max = new BlockPos(blocks.stream().mapToInt(block -> block.localPos().getX()).max().orElseThrow(),
                blocks.stream().mapToInt(block -> block.localPos().getY()).max().orElseThrow(),
                blocks.stream().mapToInt(block -> block.localPos().getZ()).max().orElseThrow());
        var ingredients = new ArrayList<BuilderConstructionCost.Ingredient>();
        if (this.cost.stone() > 0) ingredients.add(new BuilderConstructionCost.Ingredient(ResourceLocation.parse("villagerretaliation:wall_stone"), true, this.cost.stone()));
        if (this.cost.logs() > 0) ingredients.add(new BuilderConstructionCost.Ingredient(ResourceLocation.withDefaultNamespace("logs"), true, this.cost.logs()));
        var entry = new BuilderStructureCatalog.Entry(ResourceLocation.parse("villagerretaliation:wall/" + id), "Walls", this.wall.type().label(), 0,
                new BuilderConstructionCost(BuilderConstructionCost.Mode.SIMPLIFIED, ingredients), "");
        this.plan = new BuilderStructureScanner.StructurePlan(entry, Rotation.NONE, max.offset(1, 1, 1), BlockPos.ZERO, max,
                blocks, BuilderStructureScanner.materialRequirements(this.cells.stream().map(Cell::state).toList()), 0);
        return this.plan;
    }

    boolean available(int index, SettlementConstructionCrew crew) {
        if (crew.next() >= this.cells.size() || this.cells.get(index).stage() != this.cells.get(crew.next()).stage()) return false;
        var below = this.indices.get(this.cells.get(index).position().below());
        return below == null || crew.completed(below);
    }

    boolean overlaps(SettlementProject project) {
        return this.cells.stream().anyMatch(cell -> project.wallConstruction() != null
                ? project.wallConstruction().indices.containsKey(cell.position())
                : cell.position().getX() >= project.min().getX() && cell.position().getX() <= project.max().getX()
                        && cell.position().getY() >= project.min().getY() && cell.position().getY() <= project.max().getY()
                        && cell.position().getZ() >= project.min().getZ() && cell.position().getZ() <= project.max().getZ());
    }

    boolean overlaps(BlockPos min, BlockPos max) {
        return this.cells.stream().anyMatch(cell -> cell.position().getX() >= min.getX() && cell.position().getX() <= max.getX()
                && cell.position().getY() >= min.getY() && cell.position().getY() <= max.getY()
                && cell.position().getZ() >= min.getZ() && cell.position().getZ() <= max.getZ());
    }

    boolean overlaps(SettlementArea area) {
        return this.cells.stream().anyMatch(cell -> area.contains(cell.position()));
    }

    boolean protectedSite(ServerLevel level, SettlementRecord town) {
        return town.areas().values().stream().anyMatch(area -> area.type() != SettlementAreaType.CONSTRUCTION && overlaps(area))
                || this.cells.stream().anyMatch(cell -> level.hasChunkAt(cell.position()) && level.getBlockEntity(cell.position()) != null);
    }

    static String commission(ServerPlayer player, SettlementRecord town, UUID id, SettlementWall wall, SettlementWallTerrain.Preview preview) {
        if (!SettlementConstructionService.canBuild(player, town)) return "Wall construction is not permitted";
        if (town.knowsProject(id)) return "";
        if (preview.cells().stream().noneMatch(cell -> cell.kind() == SettlementWallTerrain.Kind.PLACE && !cell.target().isAir()))
            return "No wall construction is needed";
        var construction = create(wall, preview);
        var plan = construction.plan(id);
        var origin = construction.origin();
        if (town.stage().projects() == 0) return "Advance the settlement stage to unlock construction projects";
        var area = SettlementConstructionService.workplace(player.serverLevel(), town, plan, origin, id);
        if (area == null || !town.owns(area)) return "This wall exceeds the construction worksite bounds. Confirm a shorter section";
        if (construction.protectedSite(player.serverLevel(), town)) return "The wall overlaps a protected worksite or container";
        var check = com.jvn.villagerretaliation.interaction.work.builder.BuilderSitePlanner.validateSite(player.serverLevel(), player, null,
                com.jvn.villagerretaliation.interaction.HiredWorkArea.fromBounds(area.min(), area.max(), true), plan, origin);
        if (!check.valid()) return net.minecraft.network.chat.Component.translatable(check.statusKey()).getString();
        var project = SettlementProject.planned(town, area, SettlementProject.AUTOMATIC_BUILDER, plan, origin, id, player.level().getGameTime());
        project.wallConstruction(construction);
        project.queue(player.getUUID());
        if (!town.queueProject(player.getUUID(), project)) return "The construction queue is full or overlaps another project";
        SettlementConstructionService.tickQueue(player.serverLevel(), town);
        return "";
    }

    void record(ServerLevel level, SettlementRecord town, SettlementProject project) {
        var records = SettlementWallsSavedData.get(level);
        if (this.recorded || project.status() != SettlementProject.Status.COMPLETED || records.readOnly()) return;
        if (records.hasGroup(project.id())) {
            this.recorded = true;
            town.changed();
            return;
        }
        var preview = new SettlementWallTerrain.Preview(this.cells.stream().map(cell ->
                new SettlementWallTerrain.Cell(cell.position(), cell.state(), null, null, SettlementWallTerrain.Kind.PLACE)).toList(), "");
        records.record(project.id(), town.id(), level, this.wall, preview);
        this.recorded = true;
        town.changed();
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.putBoolean("Recorded", this.recorded);
        tag.putString("Type", this.wall.type().name());
        tag.putBoolean("Closed", this.wall.closed());
        tag.putInt("Spacing", this.wall.towerSpacing());
        tag.putInt("Stone", this.cost.stone());
        tag.putInt("Logs", this.cost.logs());
        var nodes = new ListTag();
        for (int index = 0; index < this.wall.nodes().size(); index++) {
            var node = new CompoundTag();
            node.putLong("Pos", this.wall.nodes().get(index).asLong());
            node.putString("Tower", this.wall.nodeTowers().get(index).name());
            nodes.add(node);
        }
        tag.put("Nodes", nodes);
        var blocks = new ListTag();
        for (var cell : this.cells) {
            var block = new CompoundTag();
            block.putLong("Pos", cell.position().asLong());
            block.put("State", NbtUtils.writeBlockState(cell.state()));
            block.putInt("Stage", cell.stage());
            blocks.add(block);
        }
        tag.put("Blocks", blocks);
        return tag;
    }

    static SettlementWallConstruction load(CompoundTag tag) {
        if (tag.getInt("Version") != 1 || !(tag.get("Nodes") instanceof ListTag nodes) || nodes.size() > SettlementWall.MAX_NODES
                || !(tag.get("Blocks") instanceof ListTag blocks) || blocks.size() > SettlementWall.MAX_BLOCKS
                || nodes.getElementType() != Tag.TAG_COMPOUND || blocks.getElementType() != Tag.TAG_COMPOUND
                || !tag.contains("Stone", Tag.TAG_INT) || !tag.contains("Logs", Tag.TAG_INT))
            throw new IllegalArgumentException("Invalid wall construction record");
        var positions = new ArrayList<BlockPos>();
        var towers = new ArrayList<SettlementWall.Tower>();
        for (var raw : nodes) {
            var node = (CompoundTag) raw;
            if (!node.contains("Pos", Tag.TAG_LONG)) throw new IllegalArgumentException("Invalid wall node");
            positions.add(BlockPos.of(node.getLong("Pos")));
            towers.add(SettlementWall.Tower.valueOf(node.getString("Tower")));
        }
        var cells = new ArrayList<Cell>();
        for (var raw : blocks) {
            var block = (CompoundTag) raw;
            if (!block.contains("Pos", Tag.TAG_LONG) || !block.contains("Stage", Tag.TAG_INT)
                    || !block.contains("State", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid wall block");
            cells.add(new Cell(BlockPos.of(block.getLong("Pos")), NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), block.getCompound("State")), block.getInt("Stage")));
        }
        var construction = new SettlementWallConstruction(new SettlementWall(SettlementWall.Type.valueOf(tag.getString("Type")), positions,
                tag.getBoolean("Closed"), tag.getInt("Spacing"), towers), cells, new SettlementWallMaterials.Cost(tag.getInt("Stone"), tag.getInt("Logs")));
        construction.recorded = tag.getBoolean("Recorded");
        return construction;
    }
}
