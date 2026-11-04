package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderConstructionCost;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner.BuildBlock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class SettlementBuildingPreparation {
    static final int MAX_CELLS = 4096;
    record Cell(BlockPos local, BlockState before, BlockState after) {}
    record Result(SettlementBuildingPreparation preparation, String failure, BlockPos problem) {}
    private final SettlementTerrainOptions options;
    private final List<Cell> cells;
    private int precedingBlocks;
    private BuilderStructureScanner.StructurePlan checkedSource;
    private BuilderStructureScanner.StructurePlan preparedPlan;

    boolean available(int index, SettlementConstructionCrew crew) {
        if (index < this.precedingBlocks) return true;
        for (int step = 0; step < this.precedingBlocks; step++) if (!crew.completed(step)) return false;
        return true;
    }

    private SettlementBuildingPreparation(SettlementTerrainOptions options, List<Cell> cells) {
        if (cells.size() > MAX_CELLS || cells.stream().map(Cell::local).distinct().count() != cells.size()
                || cells.stream().anyMatch(cell -> cell.before().hasBlockEntity() || cell.before().equals(cell.after()) || !cell.before().isAir() && !natural(cell.before())))
            throw new IllegalArgumentException("Invalid building preparation");
        this.options = options;
        this.cells = List.copyOf(cells);
    }

    SettlementTerrainOptions options() { return this.options; }
    List<Cell> cells() { return this.cells; }

    static Result preview(ServerPlayer player, SettlementRecord town, BuilderStructureScanner.StructurePlan plan,
                          BlockPos origin, SettlementTerrainOptions options) {
        if (!options.enabled()) return new Result(new SettlementBuildingPreparation(options, List.of()), "", null);
        var level = player.serverLevel();
        var min = plan.localMin();
        var max = plan.localMax();
        if ((long) (max.getX() - min.getX() + 1) * (max.getZ() - min.getZ() + 1) > MAX_CELLS)
            return new Result(null, "too_large", origin);
        var planned = new LinkedHashMap<BlockPos, BuildBlock>();
        plan.blocks().forEach(block -> planned.put(block.localPos(), block));
        var cells = new ArrayList<Cell>();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                if (options.level()) {
                    for (int y = max.getY(); y >= min.getY(); y--) {
                        var local = new BlockPos(x, y, z);
                        var pos = origin.offset(local);
                        String failure = access(level, player, town, pos);
                        if (!failure.isEmpty()) return new Result(null, failure, pos);
                        var current = level.getBlockState(pos);
                        var target = planned.containsKey(local) ? planned.get(local).state() : Blocks.AIR.defaultBlockState();
                        if (current.equals(target) || current.isAir() || !natural(current)) continue;
                        if (y - min.getY() >= options.depth()) return new Result(null, "terrain_depth", pos);
                        cells.add(new Cell(local, current, target));
                    }
                }
                if (options.fill()) {
                    var column = new ArrayList<Cell>();
                    boolean supported = false;
                    for (int depth = 1; depth <= options.depth() + 1; depth++) {
                        var local = new BlockPos(x, min.getY() - depth, z);
                        var pos = origin.offset(local);
                        String failure = access(level, player, town, pos);
                        if (!failure.isEmpty()) return new Result(null, failure, pos);
                        var current = level.getBlockState(pos);
                        if (current.liquid() || current.hasBlockEntity()) return new Result(null, "terrain_unsafe", pos);
                        boolean solid = current.isFaceSturdy(level, pos, net.minecraft.core.Direction.UP);
                        if (solid && (!options.foundation() || depth > 1 || current.is(Blocks.COBBLESTONE))) {
                            supported = true;
                            break;
                        }
                        if (depth > options.depth()) break;
                        if (!current.isAir() && !natural(current)) return new Result(null, "terrain_unsafe", pos);
                        var target = options.foundation() ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.DIRT.defaultBlockState();
                        column.add(new Cell(local, current, target));
                        if (solid) { supported = true; break; }
                    }
                    if (!supported) return new Result(null, "terrain_depth", origin.offset(x, min.getY() - options.depth(), z));
                    java.util.Collections.reverse(column);
                    cells.addAll(column);
                }
                if (cells.size() > MAX_CELLS) return new Result(null, "too_large", origin);
            }
        }
        var result = new SettlementBuildingPreparation(options, cells);
        var prepared = result.apply(plan);
        if (prepared == null || BuilderConstructionCost.requirements(prepared).size() > BuilderConstructionCost.MAX_INGREDIENTS)
            return new Result(null, "material_quote_too_large", origin);
        if (prepared.blocks().size() > plan.blockLimit()) return new Result(null, "too_large", origin);
        return new Result(result, "", null);
    }

    private static String access(ServerLevel level, ServerPlayer player, SettlementRecord town, BlockPos pos) {
        if (!town.contains(pos, pos) || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) return "bounds";
        if (!level.hasChunkAt(pos)) return "site_unloaded";
        if (!level.mayInteract(player, pos) || level.getBlockEntity(pos) != null) return "terrain_unsafe";
        return "";
    }

    private static boolean natural(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.SNOW) || state.is(Blocks.CLAY) || state.is(Blocks.GRAVEL);
    }

    BuilderStructureScanner.StructurePlan apply(BuilderStructureScanner.StructurePlan source) {
        if (this.cells.isEmpty()) return source;
        if (this.checkedSource == source) return this.preparedPlan;
        var changes = new LinkedHashMap<BlockPos, Cell>();
        this.cells.forEach(cell -> changes.put(cell.local(), cell));
        var original = source.blocks().stream().map(BuildBlock::localPos).collect(java.util.stream.Collectors.toSet());
        var blocks = new ArrayList<BuildBlock>();
        for (var cell : this.cells) {
            if (!original.contains(cell.local())) blocks.add(new BuildBlock(cell.local(), cell.after(), null,
                    new ItemStack(cell.after().getBlock()), BuilderStructureScanner.BuilderToolAction.NONE, cell.before()));
        }
        this.precedingBlocks = blocks.size();
        for (var block : source.blocks()) {
            var cell = changes.get(block.localPos());
            blocks.add(cell == null ? block : new BuildBlock(block.localPos(), block.state(), block.blockEntityTag(),
                    block.requiredItem(), block.toolAction(), cell.before()));
        }
        var materials = BuilderStructureScanner.materialRequirements(blocks.stream().map(BuildBlock::state).toList());
        var entry = source.entry();
        if (entry.constructionCost().mode() == BuilderConstructionCost.Mode.SIMPLIFIED) {
            var ingredients = new ArrayList<>(entry.constructionCost().ingredients());
            var extra = this.cells.stream().filter(cell -> !original.contains(cell.local()) && !cell.after().isAir()).map(Cell::after).toList();
            for (var material : BuilderStructureScanner.materialRequirements(extra)) {
                var id = BuiltInRegistries.ITEM.getKey(material.item().getItem());
                int match = -1;
                for (int index = 0; index < ingredients.size(); index++) {
                    if (!ingredients.get(index).tag() && ingredients.get(index).id().equals(id)) { match = index; break; }
                }
                long count = material.count() + (match < 0 ? 0L : ingredients.get(match).count());
                if (count > BuilderConstructionCost.MAX_COUNT) return null;
                var ingredient = new BuilderConstructionCost.Ingredient(id, false, (int) count);
                if (match < 0) ingredients.add(ingredient);
                else ingredients.set(match, ingredient);
            }
            if (ingredients.size() > BuilderConstructionCost.MAX_INGREDIENTS) return null;
            entry = new BuilderStructureCatalog.Entry(entry.id(), entry.category(), entry.label(), entry.baseCost(),
                    new BuilderConstructionCost(BuilderConstructionCost.Mode.SIMPLIFIED, ingredients), entry.houseSize());
        }
        var min = source.localMin();
        var max = source.localMax();
        for (var cell : this.cells) { min = new BlockPos(Math.min(min.getX(), cell.local().getX()), Math.min(min.getY(), cell.local().getY()), Math.min(min.getZ(), cell.local().getZ())); }
        int per64 = Math.max(0, VillagerRetaliationConfig.HIRED_BUILDER_EMERALDS_PER_64_BLOCKS.get());
        int price = source.price() + (int) Math.ceil(blocks.size() / 64.0 * per64) - (int) Math.ceil(source.blocks().size() / 64.0 * per64);
        var clearing = source.clearingBlocks().stream().filter(block -> !changes.containsKey(block.localPos())).toList();
        this.checkedSource = source;
        return this.preparedPlan = new BuilderStructureScanner.StructurePlan(entry, source.rotation(), source.templateSize(), min, max,
                List.copyOf(blocks), materials, price, clearing, source.wall(), true);
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("Mode", this.options.mode().name());
        tag.putInt("Depth", this.options.depth());
        var cells = new ListTag();
        for (var cell : this.cells) {
            var value = new CompoundTag();
            value.putLong("Local", cell.local().asLong());
            value.put("Before", NbtUtils.writeBlockState(cell.before()));
            value.put("After", NbtUtils.writeBlockState(cell.after()));
            cells.add(value);
        }
        tag.put("Cells", cells);
        return tag;
    }

    static SettlementBuildingPreparation load(CompoundTag tag) {
        var options = new SettlementTerrainOptions(SettlementTerrainOptions.Mode.valueOf(tag.getString("Mode")), tag.getInt("Depth"));
        if (!(tag.get("Cells") instanceof ListTag raw) || raw.size() > MAX_CELLS || !raw.isEmpty() && raw.getElementType() != Tag.TAG_COMPOUND)
            throw new IllegalArgumentException("Invalid saved building preparation");
        var cells = new ArrayList<Cell>();
        for (var value : raw) {
            var cell = (CompoundTag) value;
            cells.add(new Cell(BlockPos.of(cell.getLong("Local")), NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), cell.getCompound("Before")),
                    NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), cell.getCompound("After"))));
        }
        return new SettlementBuildingPreparation(options, cells);
    }
}
