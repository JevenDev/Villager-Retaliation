package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.builder.BuilderConstructionCost;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.network.SettlementBuildingRequestPayload.Action;
import java.util.ArrayList;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

final class SettlementBuildingWork {
    final UUID source;
    final Action action;
    private final BlockPos origin;
    private final List<BuilderStructureScanner.BuildBlock> blocks;
    private final int price;
    private BuilderStructureScanner.StructurePlan plan;

    SettlementBuildingWork(UUID source, Action action, BlockPos origin, List<BuilderStructureScanner.BuildBlock> blocks, int price) {
        if (source == null || action == null || action == Action.INSPECT || origin == null || blocks.isEmpty()
                || blocks.size() > com.jvn.villagerretaliation.network.SettlementBuildingPreviewPayload.MAX_BLOCKS || price < 0
                || blocks.stream().map(BuilderStructureScanner.BuildBlock::localPos).distinct().count() != blocks.size()
                || blocks.stream().anyMatch(block -> block.replacedState() == null || block.replacedState().hasBlockEntity()))
            throw new IllegalArgumentException("Invalid building work");
        this.source = source; this.action = action; this.origin = origin.immutable(); this.blocks = List.copyOf(blocks); this.price = price;
    }

    BlockPos origin() { return this.origin; }

    BuilderStructureScanner.StructurePlan plan(UUID id) {
        if (this.plan != null) return this.plan;
        var min = new BlockPos(this.blocks.stream().mapToInt(b -> b.localPos().getX()).min().orElseThrow(),
                this.blocks.stream().mapToInt(b -> b.localPos().getY()).min().orElseThrow(), this.blocks.stream().mapToInt(b -> b.localPos().getZ()).min().orElseThrow());
        var max = new BlockPos(this.blocks.stream().mapToInt(b -> b.localPos().getX()).max().orElseThrow(),
                this.blocks.stream().mapToInt(b -> b.localPos().getY()).max().orElseThrow(), this.blocks.stream().mapToInt(b -> b.localPos().getZ()).max().orElseThrow());
        var materials = new LinkedHashMap<net.minecraft.world.item.Item, Integer>();
        for (var block : this.blocks) if (block.requiresMaterial()) materials.merge(block.requiredItem().getItem(), block.requiredItem().getCount(), Integer::sum);
        var entry = new BuilderStructureCatalog.Entry(ResourceLocation.parse("villagerretaliation:building_work/" + id),
                "Maintenance", this.action.name().toLowerCase(java.util.Locale.ROOT), 0, BuilderConstructionCost.EXACT, "");
        return this.plan = new BuilderStructureScanner.StructurePlan(entry, Rotation.NONE, max.subtract(min).offset(1, 1, 1), min, max,
                this.blocks, materials.entrySet().stream().map(row -> new BuilderStructureScanner.MaterialRequirement(new ItemStack(row.getKey()), row.getValue())).toList(), this.price);
    }

    boolean safe(ServerLevel level, SettlementRecord town, SettlementProject project) {
        var original = town.project(this.source);
        if (original == null || !original.completedBuilding() || !town.owns(original)) return false;
        for (var block : this.blocks) {
            var pos = this.origin.offset(block.localPos());
            if (!town.contains(pos) || !level.hasChunkAt(pos)) return false;
            var current = level.getBlockState(pos);
            if (!BuilderStructureScanner.sameSchematicState(current, block.state()) && !block.approvedReplacement(current)) return false;
        }
        return true;
    }

    void record(SettlementRecord town, SettlementProject project) {
        if (this.action == Action.DISMANTLE && project.status() == SettlementProject.Status.COMPLETED) {
            var original = town.project(this.source);
            if (original != null && !original.retired()) { original.retire(); town.changed(); }
        }
    }

    CompoundTag save() {
        var tag = new CompoundTag(); tag.putUUID("Source", this.source); tag.putString("Action", this.action.name());
        tag.putLong("Origin", this.origin.asLong()); tag.putInt("Price", this.price);
        var rows = new ListTag();
        for (var block : this.blocks) {
            var row = new CompoundTag(); row.putLong("Position", block.localPos().asLong());
            row.put("Before", NbtUtils.writeBlockState(block.replacedState())); row.put("After", NbtUtils.writeBlockState(block.state()));
            row.putString("Tool", block.toolAction().name());
            if (block.requiresMaterial()) { row.putString("Item", BuiltInRegistries.ITEM.getKey(block.requiredItem().getItem()).toString()); row.putInt("Count", block.requiredItem().getCount()); }
            var data = BuilderStructureScanner.sanitizeBlockEntityTag(block.state(), block.blockEntityTag());
            if (data != null) row.put("BlockEntity", data);
            rows.add(row);
        }
        tag.put("Blocks", rows); return tag;
    }

    static SettlementBuildingWork load(CompoundTag tag) {
        if (!tag.hasUUID("Source") || !tag.contains("Origin", Tag.TAG_LONG) || !tag.contains("Blocks", Tag.TAG_LIST))
            throw new IllegalArgumentException("Invalid saved building work");
        var rows = tag.getList("Blocks", Tag.TAG_COMPOUND);
        if (rows.size() > com.jvn.villagerretaliation.network.SettlementBuildingPreviewPayload.MAX_BLOCKS) throw new IllegalArgumentException("Building work is too large");
        var blocks = new ArrayList<BuilderStructureScanner.BuildBlock>();
        for (var raw : rows) {
            var row = (CompoundTag) raw;
            if (!row.contains("Position", Tag.TAG_LONG) || !row.contains("Before", Tag.TAG_COMPOUND) || !row.contains("After", Tag.TAG_COMPOUND))
                throw new IllegalArgumentException("Invalid saved building change");
            var item = ItemStack.EMPTY;
            if (row.contains("Item")) {
                var id = ResourceLocation.parse(row.getString("Item"));
                if (!BuiltInRegistries.ITEM.containsKey(id) || row.getInt("Count") < 1 || row.getInt("Count") > 64)
                    throw new IllegalArgumentException("Invalid building material");
                item = new ItemStack(BuiltInRegistries.ITEM.get(id), row.getInt("Count"));
            }
            var after = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), row.getCompound("After"));
            blocks.add(new BuilderStructureScanner.BuildBlock(BlockPos.of(row.getLong("Position")), after,
                    BuilderStructureScanner.sanitizeBlockEntityTag(after, row.contains("BlockEntity") ? row.getCompound("BlockEntity") : null), item,
                    BuilderStructureScanner.BuilderToolAction.valueOf(row.getString("Tool")), NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), row.getCompound("Before"))));
        }
        return new SettlementBuildingWork(tag.getUUID("Source"), Action.valueOf(tag.getString("Action")), BlockPos.of(tag.getLong("Origin")), blocks, tag.getInt("Price"));
    }

}
