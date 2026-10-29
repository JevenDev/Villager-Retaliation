package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderConstructionCost;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderSitePlanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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

final class SettlementConstructionOrder {
    record Cell(BlockPos position, BlockState before, BlockState after) {}
    private final String label;
    private final List<Cell> cells;
    private final BuilderConstructionCost cost;
    private final List<SettlementWallsSavedData.Entry> originals;
    private final List<SettlementWallsSavedData.Entry> replacements;
    private final SettlementArea completedArea;
    private BuilderStructureScanner.StructurePlan plan;
    private boolean recorded;

    SettlementConstructionOrder(String label, List<Cell> cells, BuilderConstructionCost cost,
                                List<SettlementWallsSavedData.Entry> originals,
                                List<SettlementWallsSavedData.Entry> replacements, SettlementArea completedArea) {
        if (label == null || label.isBlank() || label.length() > 48 || cells.isEmpty() || cells.size() > SettlementWall.MAX_BLOCKS)
            throw new IllegalArgumentException("Invalid construction order");
        var positions = new HashSet<BlockPos>();
        for (var cell : cells) {
            if (cell.before() == null || cell.after() == null || cell.before().hasBlockEntity() || cell.after().hasBlockEntity()
                    || cell.before().equals(cell.after()) || !positions.add(cell.position()))
                throw new IllegalArgumentException("Invalid construction change");
        }
        this.label = label;
        this.cells = List.copyOf(cells);
        this.cost = cost;
        this.originals = List.copyOf(originals);
        this.replacements = List.copyOf(replacements);
        this.completedArea = completedArea;
    }

    boolean wall() { return !this.originals.isEmpty(); }
    boolean campAvailable() {
        return wall() && java.util.stream.Stream.concat(this.originals.stream(), this.replacements.stream()).map(SettlementWallsSavedData.Entry::wall)
                .allMatch(wall -> wall.type() == SettlementWall.Type.PALISADE && wall.nodeTowers().stream()
                        .allMatch(tower -> tower == SettlementWall.Tower.DEFAULT || tower == SettlementWall.Tower.PALISADE || tower == SettlementWall.Tower.NONE));
    }
    boolean registers(SettlementArea area) { return area.equals(this.completedArea); }

    BlockPos origin() {
        return new BlockPos(this.cells.stream().mapToInt(c -> c.position().getX()).min().orElseThrow(),
                this.cells.stream().mapToInt(c -> c.position().getY()).min().orElseThrow(),
                this.cells.stream().mapToInt(c -> c.position().getZ()).min().orElseThrow());
    }

    BuilderStructureScanner.StructurePlan plan(UUID id) {
        if (this.plan != null) return this.plan;
        var origin = origin();
        var blocks = this.cells.stream().map(cell -> new BuilderStructureScanner.BuildBlock(cell.position().subtract(origin),
                cell.after(), null, new ItemStack(cell.after().getBlock()), BuilderStructureScanner.toolAction(cell.after()), cell.before())).toList();
        var max = new BlockPos(blocks.stream().mapToInt(c -> c.localPos().getX()).max().orElseThrow(),
                blocks.stream().mapToInt(c -> c.localPos().getY()).max().orElseThrow(),
                blocks.stream().mapToInt(c -> c.localPos().getZ()).max().orElseThrow());
        var entry = new BuilderStructureCatalog.Entry(ResourceLocation.parse("villagerretaliation:order/" + id),
                "Construction", this.label, 0, this.cost, "");
        return this.plan = new BuilderStructureScanner.StructurePlan(entry, Rotation.NONE, max.offset(1, 1, 1), BlockPos.ZERO, max,
                blocks, BuilderStructureScanner.materialRequirements(this.cells.stream().map(Cell::after).toList()), 0, List.of(), wall());
    }

    boolean available(int index, SettlementConstructionCrew crew) {
        return index == crew.next();
    }

    boolean protectedSite(ServerLevel level, SettlementRecord town) {
        if (this.originals.stream().anyMatch(entry -> !entry.equals(SettlementWallsSavedData.get(level).get(entry.id())))) return true;
        if (!wall() && java.util.stream.Stream.concat(town.projects().values().stream(), town.projectHistory().values().stream())
                .anyMatch(project -> project.status() == SettlementProject.Status.COMPLETED && project.order() == null
                        && this.cells.stream().anyMatch(cell -> project.wallConstruction() != null
                                ? project.wallConstruction().overlaps(cell.position(), cell.position())
                                : cell.position().getX() >= project.min().getX() && cell.position().getX() <= project.max().getX()
                                        && cell.position().getY() >= project.min().getY() && cell.position().getY() <= project.max().getY()
                                        && cell.position().getZ() >= project.min().getZ() && cell.position().getZ() <= project.max().getZ()))) return true;
        return this.cells.stream().anyMatch(cell -> level.hasChunkAt(cell.position()) && level.getBlockEntity(cell.position()) != null
                || town.areas().values().stream().anyMatch(area -> area.type() != SettlementAreaType.CONSTRUCTION && area.contains(cell.position())));
    }

    String commission(ServerPlayer player, SettlementRecord town, UUID id) {
        if (!SettlementConstructionService.canBuild(player, town)) return "Construction is not permitted";
        if (town.knowsProject(id)) return "";
        if (town.stage().projects() == 0 && !campAvailable()) return "Advance the settlement stage to unlock construction projects";
        if (town.projects().values().stream().anyMatch(project -> project.unfinished() && project.order() != null
                && project.order().originals.stream().anyMatch(old -> this.originals.stream().anyMatch(entry -> entry.id().equals(old.id())))))
            return "This wall already has an unfinished construction order";
        var plan = plan(id);
        var origin = origin();
        var area = SettlementConstructionService.workplace(player.serverLevel(), town, plan, origin, id);
        if (area == null || !town.owns(area)) return "Keep the order inside the settlement construction bounds";
        if (protectedSite(player.serverLevel(), town)) return "The order overlaps a protected worksite or changed wall";
        if (this.completedArea != null && (town.areas().size() >= SettlementRecord.MAX_AREAS
                || town.areas().values().stream().anyMatch(existing -> existing.conflictsWith(this.completedArea))))
            return "Move the pen outside existing work areas or remove an unused area first";
        var check = BuilderSitePlanner.validateSite(player.serverLevel(), player, null,
                HiredWorkArea.fromBounds(area.min(), area.max(), true), plan, origin);
        if (!check.valid()) return net.minecraft.network.chat.Component.translatable(check.statusKey()).getString();
        var project = SettlementProject.planned(town, area, SettlementProject.AUTOMATIC_BUILDER, plan, origin, id, player.level().getGameTime());
        project.order(this);
        project.queue(player.getUUID());
        if (!town.queueProject(player.getUUID(), project)) return "The construction queue is full or overlaps another project";
        SettlementConstructionService.tickQueue(player.serverLevel(), town);
        return "";
    }

    static SettlementConstructionOrder wall(String label, SettlementWallTerrain.Preview preview, SettlementWallMaterials.Cost quote,
                                            List<SettlementWallsSavedData.Entry> originals, List<SettlementWallsSavedData.Entry> replacements) {
        var ingredients = new ArrayList<BuilderConstructionCost.Ingredient>();
        if (quote.stone() > 0) ingredients.add(new BuilderConstructionCost.Ingredient(ResourceLocation.parse("villagerretaliation:wall_stone"), true, quote.stone()));
        if (quote.logs() > 0) ingredients.add(new BuilderConstructionCost.Ingredient(ResourceLocation.withDefaultNamespace("logs"), true, quote.logs()));
        var cells = preview.cells().stream().filter(cell -> cell.kind() == SettlementWallTerrain.Kind.PLACE && !cell.before().equals(cell.target()))
                .map(cell -> new Cell(cell.position(), cell.before(), cell.target()))
                .sorted(Comparator.comparingInt((Cell cell) -> cell.after().isAir() ? 0 : 1)
                        .thenComparingInt(cell -> cell.after().isAir() ? -cell.position().getY() : cell.position().getY())).toList();
        return new SettlementConstructionOrder(label, cells, ingredients.isEmpty() ? BuilderConstructionCost.LABOR
                : new BuilderConstructionCost(BuilderConstructionCost.Mode.SIMPLIFIED, ingredients), originals, replacements, null);
    }

    void record(ServerLevel level, SettlementRecord town, SettlementProject project) {
        if (this.recorded || project.unfinished() || project.site() != null && project.site().pending()) return;
        if (wall()) {
            var records = SettlementWallsSavedData.get(level);
            if (records.readOnly()) return;
            if (project.status() == SettlementProject.Status.COMPLETED) {
                this.originals.forEach(entry -> records.remove(entry.id()));
                records.replace(this.replacements);
            } else {
                if (this.cells.stream().anyMatch(cell -> !level.hasChunkAt(cell.position()))) return;
                var retained = new ArrayList<SettlementWallsSavedData.Entry>();
                for (var old : this.originals) {
                    var replacement = this.replacements.stream().filter(entry -> entry.id().equals(old.id())).findFirst().orElse(old);
                    var blocks = new LinkedHashMap<>(old.blocks());
                    for (var cell : this.cells) if (blocks.containsKey(cell.position()) || replacement.blocks().containsKey(cell.position())) {
                        var actual = level.getBlockState(cell.position());
                        if (cell.after().isAir() && !cell.before().isAir() && actual.isAir()) blocks.remove(cell.position());
                        else if (!cell.after().isAir() && actual.equals(cell.after())) blocks.put(cell.position(), actual);
                    }
                    if (!blocks.isEmpty()) retained.add(new SettlementWallsSavedData.Entry(old.id(), old.group(), old.town(), old.dimension(), replacement.wall(), blocks));
                }
                this.originals.forEach(entry -> records.remove(entry.id()));
                records.replace(retained);
            }
        }
        if (this.completedArea != null && project.status() == SettlementProject.Status.COMPLETED
                && !town.putArea(project.authority(), this.completedArea)) {
            project.waitFor("worksite_registration");
            return;
        }
        this.recorded = true;
        town.changed();
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.putString("Label", this.label);
        tag.putBoolean("Recorded", this.recorded);
        tag.putString("CostMode", this.cost.mode().name());
        var ingredients = new ListTag();
        for (var ingredient : this.cost.ingredients()) {
            var row = new CompoundTag();
            row.putString("Id", ingredient.id().toString());
            row.putBoolean("Tag", ingredient.tag());
            row.putInt("Count", ingredient.count());
            ingredients.add(row);
        }
        tag.put("Cost", ingredients);
        var cells = new ListTag();
        for (var cell : this.cells) {
            var row = new CompoundTag();
            row.putLong("Pos", cell.position().asLong());
            row.put("Before", NbtUtils.writeBlockState(cell.before()));
            row.put("After", NbtUtils.writeBlockState(cell.after()));
            cells.add(row);
        }
        tag.put("Cells", cells);
        tag.put("Originals", SettlementWallsSavedData.encode(this.originals));
        tag.put("Replacements", SettlementWallsSavedData.encode(this.replacements));
        if (this.completedArea != null) tag.put("CompletedArea", this.completedArea.save());
        return tag;
    }

    static SettlementConstructionOrder load(CompoundTag tag) {
        if (tag.getInt("Version") != 1 || !(tag.get("Cells") instanceof ListTag saved) || saved.size() > SettlementWall.MAX_BLOCKS)
            throw new IllegalArgumentException("Invalid construction order data");
        var cells = new ArrayList<Cell>();
        for (var raw : saved) {
            if (!(raw instanceof CompoundTag row) || !row.contains("Pos", Tag.TAG_LONG)
                    || !row.contains("Before", Tag.TAG_COMPOUND) || !row.contains("After", Tag.TAG_COMPOUND))
                throw new IllegalArgumentException("Invalid construction order cell");
            cells.add(new Cell(BlockPos.of(row.getLong("Pos")), NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), row.getCompound("Before")),
                    NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), row.getCompound("After"))));
        }
        var ingredients = new ArrayList<BuilderConstructionCost.Ingredient>();
        for (var raw : tag.getList("Cost", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) raw;
            ingredients.add(new BuilderConstructionCost.Ingredient(ResourceLocation.parse(row.getString("Id")), row.getBoolean("Tag"), row.getInt("Count")));
        }
        var order = new SettlementConstructionOrder(tag.getString("Label"), cells,
                new BuilderConstructionCost(BuilderConstructionCost.Mode.valueOf(tag.getString("CostMode")), ingredients),
                SettlementWallsSavedData.decode(tag.getCompound("Originals")), SettlementWallsSavedData.decode(tag.getCompound("Replacements")),
                tag.contains("CompletedArea", Tag.TAG_COMPOUND) ? SettlementArea.load(tag.getCompound("CompletedArea")) : null);
        order.recorded = tag.getBoolean("Recorded");
        return order;
    }
}
