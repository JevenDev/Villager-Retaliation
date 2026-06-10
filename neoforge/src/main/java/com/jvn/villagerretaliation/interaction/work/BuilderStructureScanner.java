package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class BuilderStructureScanner {
    private static final Map<CacheKey, Optional<StructurePlan>> CACHE = new LinkedHashMap<>();
    private static final int CACHE_LIMIT = 128;

    private BuilderStructureScanner() {
    }

    public static Optional<StructurePlan> scan(ServerLevel level, BuilderStructureCatalog.Entry entry, Rotation rotation) {
        if (level == null || entry == null) {
            return Optional.empty();
        }
        Rotation safeRotation = rotation == null ? Rotation.NONE : rotation;
        CacheKey key = new CacheKey(entry.id(), safeRotation);
        Optional<StructurePlan> cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        Optional<StructurePlan> scanned = level.getStructureManager().get(entry.id())
                .map(template -> scanTemplate(level, entry, template, safeRotation))
                .filter(plan -> !plan.blocks().isEmpty()
                        && plan.blocks().size() <= Math.max(128, VillagerRetaliationConfig.HIRED_BUILDER_MAX_BLOCKS.get()));
        if (CACHE.size() >= CACHE_LIMIT) {
            ResourceLocation firstKey = CACHE.keySet().stream().findFirst().map(CacheKey::structureId).orElse(null);
            if (firstKey != null) {
                CACHE.keySet().removeIf(cacheKey -> cacheKey.structureId().equals(firstKey));
            }
        }
        CACHE.put(key, scanned);
        return scanned;
    }

    public static boolean sameMaterial(ItemStack candidate, ItemStack required) {
        return !candidate.isEmpty()
                && !required.isEmpty()
                && ItemStack.isSameItemSameComponents(candidate, required);
    }

    public static String materialSummary(List<MaterialRequirement> materials, int limit) {
        if (materials == null || materials.isEmpty()) {
            return "no carried materials";
        }
        int safeLimit = Math.max(1, limit);
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < Math.min(safeLimit, materials.size()); i++) {
            MaterialRequirement material = materials.get(i);
            parts.add(material.count() + "x " + material.itemName());
        }
        int hidden = materials.size() - parts.size();
        if (hidden > 0) {
            parts.add(hidden + " more");
        }
        return String.join(", ", parts);
    }

    private static StructurePlan scanTemplate(
            ServerLevel level,
            BuilderStructureCatalog.Entry entry,
            StructureTemplate template,
            Rotation rotation) {
        CompoundTag nbt = template.save(new CompoundTag());
        BlockState[] palette = palette(nbt);
        ListTag blocksTag = nbt.getList("blocks", Tag.TAG_COMPOUND);
        List<BuildBlock> blocks = new ArrayList<>();
        Map<Item, Integer> materials = new LinkedHashMap<>();

        for (int i = 0; i < blocksTag.size(); i++) {
            CompoundTag blockTag = blocksTag.getCompound(i);
            int stateIndex = blockTag.getInt("state");
            if (stateIndex < 0 || stateIndex >= palette.length) {
                continue;
            }
            BlockState state = palette[stateIndex];
            CompoundTag blockEntityTag = blockTag.contains("nbt", Tag.TAG_COMPOUND)
                    ? blockTag.getCompound("nbt").copy()
                    : null;
            if (state.is(Blocks.JIGSAW)) {
                state = jigsawFinalState(blockEntityTag).orElse(Blocks.AIR.defaultBlockState());
                blockEntityTag = null;
            }
            if (shouldSkip(state)) {
                continue;
            }

            ListTag posTag = blockTag.getList("pos", Tag.TAG_INT);
            if (posTag.size() < 3) {
                continue;
            }
            BlockPos local = new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
            BlockPos rotatedPos = StructureTemplate.transform(local, Mirror.NONE, rotation, BlockPos.ZERO);
            BlockState rotatedState = state.rotate(rotation);
            ItemStack required = requiredItem(rotatedState);
            if (!required.isEmpty()) {
                materials.merge(required.getItem(), 1, Integer::sum);
            }
            blocks.add(new BuildBlock(rotatedPos.immutable(), rotatedState, blockEntityTag, required));
        }

        blocks.sort(placementComparator(level));
        List<MaterialRequirement> requirements = materials.entrySet().stream()
                .map(entrySet -> new MaterialRequirement(new ItemStack(entrySet.getKey()), entrySet.getValue()))
                .sorted(Comparator.comparing(MaterialRequirement::count).reversed().thenComparing(MaterialRequirement::itemName))
                .toList();
        Bounds bounds = Bounds.from(blocks);
        int price = price(entry, blocks.size());
        return new StructurePlan(entry, rotation, template.getSize(rotation), bounds.min(), bounds.max(), blocks, requirements, price);
    }

    private static BlockState[] palette(CompoundTag nbt) {
        ListTag paletteTag;
        if (nbt.contains("palettes", Tag.TAG_LIST)) {
            ListTag palettes = nbt.getList("palettes", Tag.TAG_LIST);
            paletteTag = palettes.isEmpty() ? new ListTag() : palettes.getList(0);
        } else {
            paletteTag = nbt.getList("palette", Tag.TAG_COMPOUND);
        }
        BlockState[] palette = new BlockState[paletteTag.size()];
        for (int i = 0; i < paletteTag.size(); i++) {
            palette[i] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), paletteTag.getCompound(i));
        }
        return palette;
    }

    private static Optional<BlockState> jigsawFinalState(CompoundTag blockEntityTag) {
        if (blockEntityTag == null || !blockEntityTag.contains("final_state", Tag.TAG_STRING)) {
            return Optional.empty();
        }
        String value = blockEntityTag.getString("final_state");
        String blockId = value.contains("[") ? value.substring(0, value.indexOf('[')) : value;
        ResourceLocation id = ResourceLocation.tryParse(blockId.trim().toLowerCase(Locale.ROOT));
        if (id == null) {
            return Optional.empty();
        }
        return BuiltInRegistries.BLOCK.getOptional(id).map(block -> block.defaultBlockState());
    }

    private static boolean shouldSkip(BlockState state) {
        return state.isAir()
                || state.is(Blocks.CAVE_AIR)
                || state.is(Blocks.STRUCTURE_VOID)
                || state.is(Blocks.STRUCTURE_BLOCK)
                || state.is(Blocks.JIGSAW);
    }

    private static ItemStack requiredItem(BlockState state) {
        if (state.is(Blocks.WATER)) {
            return new ItemStack(Items.WATER_BUCKET);
        }
        if (state.is(Blocks.LAVA)) {
            return new ItemStack(Items.LAVA_BUCKET);
        }
        Item item = state.getBlock().asItem();
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static int price(BuilderStructureCatalog.Entry entry, int blockCount) {
        int base = Math.max(0, VillagerRetaliationConfig.HIRED_BUILDER_BASE_EMERALD_COST.get()) + Math.max(0, entry.baseCost());
        int per64 = Math.max(0, VillagerRetaliationConfig.HIRED_BUILDER_EMERALDS_PER_64_BLOCKS.get());
        int blockCost = (int) Math.ceil(blockCount / 64.0D * per64);
        return Math.max(1, base + blockCost);
    }

    private static Comparator<BuildBlock> placementComparator(ServerLevel level) {
        return Comparator
                .comparingInt((BuildBlock block) -> block.localPos().getY())
                .thenComparingInt(block -> placementPriority(level, block))
                .thenComparingInt(block -> block.localPos().getZ())
                .thenComparingInt(block -> block.localPos().getX());
    }

    private static int placementPriority(ServerLevel level, BuildBlock block) {
        return block.state().getCollisionShape(level, BlockPos.ZERO, CollisionContext.empty()).isEmpty() ? 1 : 0;
    }

    private record CacheKey(ResourceLocation structureId, Rotation rotation) {
    }

    private record Bounds(BlockPos min, BlockPos max) {
        private static Bounds from(List<BuildBlock> blocks) {
            if (blocks.isEmpty()) {
                return new Bounds(BlockPos.ZERO, BlockPos.ZERO);
            }
            BlockPos min = blocks.getFirst().localPos();
            BlockPos max = min;
            for (BuildBlock block : blocks) {
                min = new BlockPos(
                        Math.min(min.getX(), block.localPos().getX()),
                        Math.min(min.getY(), block.localPos().getY()),
                        Math.min(min.getZ(), block.localPos().getZ()));
                max = new BlockPos(
                        Math.max(max.getX(), block.localPos().getX()),
                        Math.max(max.getY(), block.localPos().getY()),
                        Math.max(max.getZ(), block.localPos().getZ()));
            }
            return new Bounds(min, max);
        }
    }

    public record BuildBlock(BlockPos localPos, BlockState state, CompoundTag blockEntityTag, ItemStack requiredItem) {
        public boolean requiresMaterial() {
            return !this.requiredItem.isEmpty();
        }

        public boolean materialMatches(ItemStack stack) {
            return sameMaterial(stack, this.requiredItem);
        }
    }

    public record MaterialRequirement(ItemStack item, int count) {
        public String itemName() {
            return this.item.getHoverName().getString();
        }
    }

    public record StructurePlan(
            BuilderStructureCatalog.Entry entry,
            Rotation rotation,
            Vec3i templateSize,
            BlockPos localMin,
            BlockPos localMax,
            List<BuildBlock> blocks,
            List<MaterialRequirement> materials,
            int price) {
        public BlockPos localCenter() {
            return new BlockPos(
                    Math.floorDiv(this.localMin.getX() + this.localMax.getX(), 2),
                    Math.floorDiv(this.localMin.getY() + this.localMax.getY(), 2),
                    Math.floorDiv(this.localMin.getZ() + this.localMax.getZ(), 2));
        }

        public BlockPos worldPos(BlockPos origin, BuildBlock block) {
            return origin.offset(block.localPos());
        }

        public BlockPos worldMin(BlockPos origin) {
            return origin.offset(this.localMin);
        }

        public BlockPos worldMax(BlockPos origin) {
            return origin.offset(this.localMax);
        }

        public String materialSummary(int limit) {
            return BuilderStructureScanner.materialSummary(this.materials, limit);
        }
    }
}
