package com.jvn.villagerretaliation.interaction.work.builder;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.VillagerItemText;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class BuilderStructureScanner {
    private static final Map<CacheKey, Optional<StructurePlan>> CACHE = new LinkedHashMap<>();
    private static final int CACHE_LIMIT = 128;

    private BuilderStructureScanner() {
    }

    public static void clearCache() {
        CACHE.clear();
    }

    /** Persistent identity of the complete approved output and bill, not just its dimensions.
     * Compound key order is canonicalized because NBT iteration order is not a contract.
     * Block order is intentionally retained: it is also the deterministic spending schedule. */
    public static String fingerprint(StructurePlan plan) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            fingerprintPart(digest, "builder-structure-plan-v1");
            fingerprintPart(digest, plan.entry().id().toString());
            fingerprintPart(digest, plan.rotation().name());
            fingerprintPosition(digest, plan.templateSize());
            fingerprintPosition(digest, plan.localMin());
            fingerprintPosition(digest, plan.localMax());
            fingerprintPart(digest, Integer.toString(plan.price()));
            fingerprintPart(digest, plan.entry().constructionCost().mode().name());
            var requirements = BuilderConstructionCost.requirements(plan);
            fingerprintPart(digest, Integer.toString(requirements.size()));
            for (var requirement : requirements) {
                fingerprintPart(digest, requirement.id().toString());
                fingerprintPart(digest, Boolean.toString(requirement.tag()));
                fingerprintPart(digest, Integer.toString(requirement.count()));
            }
            fingerprintPart(digest, "blocks");
            fingerprintBlocks(digest, plan.blocks());
            fingerprintPart(digest, "clearance");
            fingerprintBlocks(digest, plan.clearingBlocks());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void fingerprintBlocks(MessageDigest digest, List<BuildBlock> blocks) {
        fingerprintPart(digest, Integer.toString(blocks.size()));
        for (var block : blocks) {
            fingerprintPosition(digest, block.localPos());
            fingerprintTag(digest, NbtUtils.writeBlockState(block.state()));
            fingerprintPart(digest, block.toolAction().name());
            if (block.replacedState() != null) fingerprintTag(digest, NbtUtils.writeBlockState(block.replacedState()));
            fingerprintTag(digest, sanitizeBlockEntityTag(block.state(), block.blockEntityTag()));
        }
    }

    private static void fingerprintPosition(MessageDigest digest, Vec3i position) {
        fingerprintPart(digest, Integer.toString(position.getX()));
        fingerprintPart(digest, Integer.toString(position.getY()));
        fingerprintPart(digest, Integer.toString(position.getZ()));
    }

    private static void fingerprintTag(MessageDigest digest, Tag tag) {
        if (tag == null) {
            fingerprintPart(digest, "absent");
            return;
        }
        fingerprintPart(digest, Byte.toString(tag.getId()));
        if (tag instanceof CompoundTag compound) {
            fingerprintPart(digest, Integer.toString(compound.size()));
            for (String key : compound.getAllKeys().stream().sorted().toList()) {
                fingerprintPart(digest, key);
                fingerprintTag(digest, compound.get(key));
            }
        } else if (tag instanceof ListTag list) {
            fingerprintPart(digest, Integer.toString(list.size()));
            for (Tag value : list) fingerprintTag(digest, value);
        } else {
            fingerprintPart(digest, tag.toString());
        }
    }

    private static void fingerprintPart(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    public static Optional<StructurePlan> scan(ServerLevel level, BuilderStructureCatalog.Entry entry, Rotation rotation) {
        if (level == null || entry == null) {
            return Optional.empty();
        }
        Rotation safeRotation = rotation == null ? Rotation.NONE : rotation;
        CacheKey key = new CacheKey(level.getServer(), entry, safeRotation);
        Optional<StructurePlan> cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        Optional<StructurePlan> scanned;
        try {
            scanned = level.getStructureManager().get(entry.id())
                    .map(template -> scanTemplate(level, entry, template, safeRotation))
                    .filter(plan -> !plan.blocks().isEmpty()
                            && plan.blocks().size() <= Math.max(128, VillagerRetaliationConfig.HIRED_BUILDER_MAX_BLOCKS.get()));
        } catch (IllegalArgumentException exception) {
            com.mojang.logging.LogUtils.getLogger().warn("Rejected unsafe builder template {}: {}", entry.id(), exception.getMessage());
            scanned = Optional.empty();
        }
        if (CACHE.size() >= CACHE_LIMIT) {
            CACHE.remove(CACHE.keySet().iterator().next());
        }
        CACHE.put(key, scanned);
        return scanned;
    }

    public static boolean sameMaterial(ItemStack candidate, ItemStack required) {
        if (candidate.isEmpty() || required.isEmpty()) {
            return false;
        }
        return ItemStack.isSameItem(candidate, required)
                || isDirtOrGrassItem(candidate) && isDirtOrGrassItem(required);
    }

    public static boolean sameSchematicState(BlockState current, BlockState target) {
        if (current == null || target == null) {
            return false;
        }
        return current.equals(target)
                || isDirtOrGrassBlock(current) && isDirtOrGrassBlock(target);
    }

    public static boolean canTransformExisting(BlockState current, BlockState target) {
        if (current == null || target == null || sameSchematicState(current, target)) {
            return false;
        }
        BlockState source = toolSourceState(target);
        if (source == null) {
            return false;
        }
        if (sameSchematicState(current, source) || current.equals(source)) {
            return true;
        }
        return (target.is(Blocks.DIRT_PATH) || target.is(Blocks.FARMLAND))
                && isDirtOrGrassBlock(current);
    }

    public static BuilderToolAction toolAction(BlockState target) {
        if (target == null) {
            return BuilderToolAction.NONE;
        }
        if (sourceStrippedBlock(target.getBlock()) != null) {
            return BuilderToolAction.AXE_STRIP;
        }
        if (target.is(Blocks.DIRT_PATH)) {
            return BuilderToolAction.SHOVEL_FLATTEN;
        }
        if (target.is(Blocks.FARMLAND)) {
            return BuilderToolAction.HOE_TILL;
        }
        return BuilderToolAction.NONE;
    }

    public static boolean matchesToolAction(ItemStack stack, BuilderToolAction action) {
        if (stack.isEmpty() || action == null || action == BuilderToolAction.NONE) {
            return false;
        }
        return switch (action) {
            case AXE_STRIP -> stack.is(ItemTags.AXES);
            case SHOVEL_FLATTEN -> stack.is(ItemTags.SHOVELS);
            case HOE_TILL -> stack.is(ItemTags.HOES);
            case NONE -> false;
        };
    }

    public static String materialSummary(List<MaterialRequirement> materials, int limit) {
        return materialSummary(null, materials, limit);
    }

    public static String materialSummary(
            MinecraftServer server,
            List<MaterialRequirement> materials,
            int limit) {
        if (materials == null || materials.isEmpty()) {
            return "no carried materials";
        }
        int safeLimit = Math.max(1, limit);
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < Math.min(safeLimit, materials.size()); i++) {
            MaterialRequirement material = materials.get(i);
            ItemStack stack = material.item().copyWithCount(material.count());
            parts.add(server == null
                    ? VillagerItemText.countedName(material.count(), material.itemName())
                    : VillagerItemText.stackName(server, stack));
        }
        int hidden = materials.size() - parts.size();
        if (hidden > 0) {
            parts.add("+" + hidden + " more");
        }
        return String.join(", ", parts);
    }

    /** Rebuilds the carried-material list from a blueprint's stored block palette. */
    public static List<MaterialRequirement> materialRequirements(Iterable<BlockState> states) {
        if (states == null) {
            return List.of();
        }
        Map<Item, Integer> materials = new LinkedHashMap<>();
        for (BlockState state : states) {
            if (state == null || shouldSkip(state)) {
                continue;
            }
            for (ItemStack required : exactBlockMaterials(state)) {
                materials.merge(required.getItem(), required.getCount(), Math::addExact);
            }
        }
        return materials.entrySet().stream()
                .map(entry -> new MaterialRequirement(new ItemStack(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparing(MaterialRequirement::count).reversed().thenComparing(MaterialRequirement::itemName))
                .toList();
    }

    private static StructurePlan scanTemplate(
            ServerLevel level,
            BuilderStructureCatalog.Entry entry,
            StructureTemplate template,
            Rotation rotation) {
        CompoundTag nbt = template.save(new CompoundTag());
        BlockState[] palette = palette(nbt);
        ListTag blocksTag = nbt.getList("blocks", Tag.TAG_COMPOUND);
        if (blocksTag.size() > 1_000_000) throw new IllegalArgumentException("Template block list is too large");
        List<BuildBlock> blocks = new ArrayList<>();
        List<BuildBlock> clearingBlocks = new ArrayList<>();
        Map<Item, Integer> materials = new LinkedHashMap<>();
        Map<BlockPos, BlockState> occupied = new HashMap<>();

        for (int i = 0; i < blocksTag.size(); i++) {
            CompoundTag blockTag = blocksTag.getCompound(i);
            int stateIndex = blockTag.getInt("state");
            if (stateIndex < 0 || stateIndex >= palette.length) {
                throw new IllegalArgumentException("Invalid block palette index");
            }
            BlockState state = palette[stateIndex];
            CompoundTag blockEntityTag = blockTag.contains("nbt", Tag.TAG_COMPOUND)
                    ? blockTag.getCompound("nbt").copy()
                    : null;
            if (state.is(Blocks.JIGSAW)) {
                state = jigsawFinalState(blockEntityTag).orElseThrow(() -> new IllegalArgumentException("Invalid jigsaw final state"));
                blockEntityTag = null;
            }
            blockEntityTag = sanitizeBlockEntityTag(state, blockEntityTag);
            if (shouldSkip(state) && !state.isAir()) {
                continue;
            }

            ListTag posTag = blockTag.getList("pos", Tag.TAG_INT);
            if (posTag.size() != 3) {
                throw new IllegalArgumentException("Invalid block position");
            }
            BlockPos local = new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
            Vec3i size = template.getSize();
            if (local.getX() < 0 || local.getY() < 0 || local.getZ() < 0
                    || local.getX() >= size.getX() || local.getY() >= size.getY() || local.getZ() >= size.getZ()) {
                throw new IllegalArgumentException("Block lies outside template size");
            }
            BlockPos rotatedPos = StructureTemplate.transform(local, Mirror.NONE, rotation, BlockPos.ZERO);
            BlockState rotatedState = state.rotate(rotation);
            if (occupied.put(rotatedPos, rotatedState) != null) {
                throw new IllegalArgumentException("Duplicate template position " + rotatedPos);
            }
            if (rotatedState.isAir()) {
                clearingBlocks.add(new BuildBlock(rotatedPos.immutable(), rotatedState, null, ItemStack.EMPTY, BuilderToolAction.NONE));
                continue;
            }
            BuilderToolAction toolAction = toolAction(rotatedState);
            List<ItemStack> exactMaterials = exactBlockMaterials(rotatedState);
            ItemStack required = exactMaterials.isEmpty() ? ItemStack.EMPTY : exactMaterials.getFirst();
            for (ItemStack material : exactMaterials) {
                materials.merge(material.getItem(), material.getCount(), Math::addExact);
            }
            blocks.add(new BuildBlock(rotatedPos.immutable(), rotatedState, blockEntityTag, required, toolAction));
        }

        validateMultiblocks(occupied);
        // Template entities are deliberately never copied: mobs, item frames and dropped items
        // are not construction materials and must not appear without an explicit paid system.
        blocks.sort(placementComparator(level));
        List<MaterialRequirement> requirements = materials.entrySet().stream()
                .map(entrySet -> new MaterialRequirement(new ItemStack(entrySet.getKey()), entrySet.getValue()))
                .sorted(Comparator.comparing(MaterialRequirement::count).reversed().thenComparing(MaterialRequirement::itemName))
                .toList();
        Bounds bounds = Bounds.from(blocks);
        int price = price(entry, blocks.size());
        var clearance = clearingBlocks.stream().filter(block -> {
            var pos = block.localPos();
            return pos.getX() >= bounds.min().getX() && pos.getX() <= bounds.max().getX()
                    && pos.getY() >= bounds.min().getY() && pos.getY() <= bounds.max().getY()
                    && pos.getZ() >= bounds.min().getZ() && pos.getZ() <= bounds.max().getZ();
        }).toList();
        return new StructurePlan(entry, rotation, template.getSize(rotation), bounds.min(), bounds.max(), blocks, requirements, price, clearance);
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
            ResourceLocation blockId = ResourceLocation.tryParse(paletteTag.getCompound(i).getString("Name"));
            if (blockId == null || BuiltInRegistries.BLOCK.getOptional(blockId).isEmpty()) {
                throw new IllegalArgumentException("Unknown template block");
            }
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
        if (value.contains("{")) return Optional.empty();
        return BuiltInRegistries.BLOCK.getOptional(id).flatMap(block -> {
            BlockState state = block.defaultBlockState();
            if (value.contains("[")) {
                if (!value.endsWith("]")) return Optional.empty();
                String properties = value.substring(value.indexOf('[') + 1, value.length() - 1);
                for (String assignment : properties.split(",")) {
                    String[] pair = assignment.split("=", -1);
                    if (pair.length != 2) return Optional.empty();
                    var property = block.getStateDefinition().getProperty(pair[0].trim());
                    if (property == null) return Optional.empty();
                    state = withProperty(state, property, pair[1].trim());
                    if (state == null) return Optional.empty();
                }
            }
            return Optional.of(state);
        });
    }

    private static <T extends Comparable<T>> BlockState withProperty(BlockState state,
            net.minecraft.world.level.block.state.properties.Property<T> property, String value) {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(null);
    }

    /** Only appearance data is retained. Never import inventories, loot tables, bees,
     * spawner data, command payloads or arbitrary mod block-entity state. */
    public static CompoundTag sanitizeBlockEntityTag(BlockState state, CompoundTag source) {
        if (source == null) return null;
        if (state.is(Blocks.DECORATED_POT) && source.contains("sherds")) {
            ListTag sherds = source.getList("sherds", Tag.TAG_STRING);
            for (int index = 0; index < sherds.size(); index++) {
                if (!sherds.getString(index).equals("minecraft:brick")) {
                    throw new IllegalArgumentException("Decorated pots with sherds need an explicit supported recipe");
                }
            }
        }
        CompoundTag safe = new CompoundTag();
        if (state.getBlock() instanceof net.minecraft.world.level.block.SignBlock) {
            copyKeys(source, safe, "front_text", "back_text", "is_waxed");
        } else if (state.getBlock() instanceof net.minecraft.world.level.block.AbstractBannerBlock) {
            copyKeys(source, safe, "patterns", "Patterns", "Base");
        } else if (state.getBlock() instanceof net.minecraft.world.level.block.SkullBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.WallSkullBlock) {
            copyKeys(source, safe, "profile", "note_block_sound");
        }
        return safe.isEmpty() ? null : safe;
    }

    private static void copyKeys(CompoundTag source, CompoundTag target, String... keys) {
        for (String key : keys) if (source.contains(key)) target.put(key, source.get(key).copy());
    }

    private static void validateMultiblocks(Map<BlockPos, BlockState> states) {
        states.forEach((pos, state) -> {
            if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof DoublePlantBlock) {
                boolean lower = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER;
                BlockState other = states.get(lower ? pos.above() : pos.below());
                if (other == null || other.getBlock() != state.getBlock()
                        || other.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                    throw new IllegalArgumentException("Unpaired double-height block at " + pos);
                }
            } else if (state.getBlock() instanceof BedBlock) {
                boolean foot = state.getValue(BedBlock.PART) == BedPart.FOOT;
                BlockPos otherPos = pos.relative(foot ? state.getValue(BedBlock.FACING) : state.getValue(BedBlock.FACING).getOpposite());
                BlockState other = states.get(otherPos);
                if (other == null || other.getBlock() != state.getBlock()
                        || other.getValue(BedBlock.PART) == state.getValue(BedBlock.PART)
                        || other.getValue(BedBlock.FACING) != state.getValue(BedBlock.FACING)) {
                    throw new IllegalArgumentException("Unpaired bed at " + pos);
                }
            }
        });
    }

    /** Exact, full-item costs, including stacked states and water contained in a block. */
    public static List<ItemStack> exactBlockMaterials(BlockState state) {
        if (state == null || shouldSkip(state)) return List.of();
        if (state.hasProperty(net.minecraft.world.level.block.BeehiveBlock.HONEY_LEVEL)
                && state.getValue(net.minecraft.world.level.block.BeehiveBlock.HONEY_LEVEL) > 0) {
            throw new IllegalArgumentException("Templates must use empty beehives");
        }
        if (state.is(Blocks.COMPOSTER) && state.getValue(net.minecraft.world.level.block.ComposterBlock.LEVEL) > 0) {
            throw new IllegalArgumentException("Templates must use empty composters");
        }
        List<ItemStack> result = new ArrayList<>();
        if (consumesRequiredItem(state)) {
            if (state.getBlock() instanceof FlowerPotBlock pot && pot.getPotted() != Blocks.AIR) {
                result.add(new ItemStack(Items.FLOWER_POT));
                Item content = pot.getPotted().asItem();
                if (content == Items.AIR) throw new IllegalArgumentException("Unsupported potted plant " + state);
                result.add(new ItemStack(content));
            } else {
                ItemStack required = requiredItem(state, toolAction(state));
                if (required.isEmpty()) throw new IllegalArgumentException("No safe item cost for " + state);
                int count = 1;
                if (state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) count = 2;
                if (state.hasProperty(BlockStateProperties.CANDLES)) count = state.getValue(BlockStateProperties.CANDLES);
                if (state.hasProperty(BlockStateProperties.PICKLES)) count = state.getValue(BlockStateProperties.PICKLES);
                if (state.hasProperty(BlockStateProperties.EGGS)) count = state.getValue(BlockStateProperties.EGGS);
                if (state.is(Blocks.SNOW)) count = state.getValue(BlockStateProperties.LAYERS);
                result.add(required.copyWithCount(count));
            }
        }
        if (!state.is(Blocks.WATER) && !state.is(Blocks.LAVA) && !state.getFluidState().isEmpty()) {
            result.add(new ItemStack(Items.WATER_BUCKET));
        }
        if (state.is(Blocks.WATER_CAULDRON)) result.add(new ItemStack(Items.WATER_BUCKET));
        if (state.is(Blocks.LAVA_CAULDRON)) result.add(new ItemStack(Items.LAVA_BUCKET));
        if (state.is(Blocks.POWDER_SNOW_CAULDRON)) result.add(new ItemStack(Items.POWDER_SNOW_BUCKET));
        if (state.is(Blocks.RESPAWN_ANCHOR) && state.getValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES) > 0) {
            result.add(new ItemStack(Items.GLOWSTONE, state.getValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES)));
        }
        return List.copyOf(result);
    }

    private static boolean shouldSkip(BlockState state) {
        return state.isAir()
                || state.is(Blocks.CAVE_AIR)
                || state.is(Blocks.STRUCTURE_VOID)
                || state.is(Blocks.STRUCTURE_BLOCK)
                || state.is(Blocks.JIGSAW);
    }

    private static ItemStack requiredItem(BlockState state, BuilderToolAction toolAction) {
        if (state.is(Blocks.WATER)) {
            return new ItemStack(Items.WATER_BUCKET);
        }
        if (state.is(Blocks.LAVA)) {
            return new ItemStack(Items.LAVA_BUCKET);
        }
        if (state.is(Blocks.POWDER_SNOW)) {
            return new ItemStack(Items.POWDER_SNOW_BUCKET);
        }
        if (state.is(Blocks.WATER_CAULDRON) || state.is(Blocks.LAVA_CAULDRON) || state.is(Blocks.POWDER_SNOW_CAULDRON)) {
            return new ItemStack(Items.CAULDRON);
        }
        if (state.is(Blocks.GRASS_BLOCK)) {
            return new ItemStack(Items.DIRT);
        }
        BlockState source = toolSourceState(state);
        if (source != null && (toolAction == BuilderToolAction.AXE_STRIP
                || toolAction == BuilderToolAction.SHOVEL_FLATTEN
                || toolAction == BuilderToolAction.HOE_TILL)) {
            Item item = source.getBlock().asItem();
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        }
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) {
            if (state.is(Blocks.WHEAT)) item = Items.WHEAT_SEEDS;
            else if (state.is(Blocks.CARROTS)) item = Items.CARROT;
            else if (state.is(Blocks.POTATOES)) item = Items.POTATO;
            else if (state.is(Blocks.BEETROOTS)) item = Items.BEETROOT_SEEDS;
            else if (state.is(Blocks.SWEET_BERRY_BUSH)) item = Items.SWEET_BERRIES;
            else if (state.is(Blocks.COCOA)) item = Items.COCOA_BEANS;
            else if (state.is(Blocks.TRIPWIRE)) item = Items.STRING;
            else {
                ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                String path = blockId.getPath().replace("wall_", "");
                if (!path.equals(blockId.getPath())) {
                    item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(blockId.getNamespace(), path));
                }
            }
        }
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    public static BlockState toolSourceState(BlockState target) {
        if (target == null) {
            return null;
        }
        Block sourceStripped = sourceStrippedBlock(target.getBlock());
        if (sourceStripped != null) {
            BlockState source = sourceStripped.defaultBlockState();
            if (target.hasProperty(RotatedPillarBlock.AXIS) && source.hasProperty(RotatedPillarBlock.AXIS)) {
                source = source.setValue(RotatedPillarBlock.AXIS, target.getValue(RotatedPillarBlock.AXIS));
            }
            return source;
        }
        if (target.is(Blocks.DIRT_PATH) || target.is(Blocks.FARMLAND)) {
            return Blocks.DIRT.defaultBlockState();
        }
        return null;
    }

    private static Block sourceStrippedBlock(Block block) {
        if (block == Blocks.STRIPPED_OAK_LOG) return Blocks.OAK_LOG;
        if (block == Blocks.STRIPPED_SPRUCE_LOG) return Blocks.SPRUCE_LOG;
        if (block == Blocks.STRIPPED_BIRCH_LOG) return Blocks.BIRCH_LOG;
        if (block == Blocks.STRIPPED_JUNGLE_LOG) return Blocks.JUNGLE_LOG;
        if (block == Blocks.STRIPPED_ACACIA_LOG) return Blocks.ACACIA_LOG;
        if (block == Blocks.STRIPPED_DARK_OAK_LOG) return Blocks.DARK_OAK_LOG;
        if (block == Blocks.STRIPPED_MANGROVE_LOG) return Blocks.MANGROVE_LOG;
        if (block == Blocks.STRIPPED_CHERRY_LOG) return Blocks.CHERRY_LOG;
        if (block == Blocks.STRIPPED_CRIMSON_STEM) return Blocks.CRIMSON_STEM;
        if (block == Blocks.STRIPPED_WARPED_STEM) return Blocks.WARPED_STEM;
        if (block == Blocks.STRIPPED_OAK_WOOD) return Blocks.OAK_WOOD;
        if (block == Blocks.STRIPPED_SPRUCE_WOOD) return Blocks.SPRUCE_WOOD;
        if (block == Blocks.STRIPPED_BIRCH_WOOD) return Blocks.BIRCH_WOOD;
        if (block == Blocks.STRIPPED_JUNGLE_WOOD) return Blocks.JUNGLE_WOOD;
        if (block == Blocks.STRIPPED_ACACIA_WOOD) return Blocks.ACACIA_WOOD;
        if (block == Blocks.STRIPPED_DARK_OAK_WOOD) return Blocks.DARK_OAK_WOOD;
        if (block == Blocks.STRIPPED_MANGROVE_WOOD) return Blocks.MANGROVE_WOOD;
        if (block == Blocks.STRIPPED_CHERRY_WOOD) return Blocks.CHERRY_WOOD;
        if (block == Blocks.STRIPPED_CRIMSON_HYPHAE) return Blocks.CRIMSON_HYPHAE;
        if (block == Blocks.STRIPPED_WARPED_HYPHAE) return Blocks.WARPED_HYPHAE;
        return null;
    }

    private static boolean isDirtOrGrassBlock(BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK);
    }

    private static boolean isDirtOrGrassItem(ItemStack stack) {
        return stack.is(Items.DIRT) || stack.is(Items.GRASS_BLOCK);
    }

    private static boolean consumesRequiredItem(BlockState state) {
        if (state.getBlock() instanceof DoublePlantBlock) {
            return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER;
        }
        if (state.getBlock() instanceof DoorBlock) {
            return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
        }
        if (state.getBlock() instanceof BedBlock) {
            return state.getValue(BedBlock.PART) == BedPart.FOOT;
        }
        return true;
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
                .thenComparingInt(block -> secondaryPartPriority(block.state()))
                .thenComparingInt(block -> block.localPos().getZ())
                .thenComparingInt(block -> block.localPos().getX());
    }

    private static int placementPriority(ServerLevel level, BuildBlock block) {
        return block.state().getCollisionShape(level, BlockPos.ZERO, CollisionContext.empty()).isEmpty() ? 1 : 0;
    }

    private static int secondaryPartPriority(BlockState state) {
        if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            return 1;
        }
        if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) {
            return 1;
        }
        return 0;
    }

    private record CacheKey(MinecraftServer server, BuilderStructureCatalog.Entry entry, Rotation rotation) {
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

    public enum BuilderToolAction {
        NONE,
        AXE_STRIP,
        SHOVEL_FLATTEN,
        HOE_TILL
    }

    public record BuildBlock(
            BlockPos localPos,
            BlockState state,
            CompoundTag blockEntityTag,
            ItemStack requiredItem,
            BuilderToolAction toolAction,
            BlockState replacedState) {
        public BuildBlock(BlockPos localPos, BlockState state, CompoundTag blockEntityTag,
                          ItemStack requiredItem, BuilderToolAction toolAction) {
            this(localPos, state, blockEntityTag, requiredItem, toolAction, null);
        }

        public boolean approvedReplacement(BlockState current) {
            return this.replacedState != null && !current.hasBlockEntity()
                    && (current.equals(this.replacedState) || current.equals(this.state) || current.isAir());
        }

        public boolean requiresMaterial() {
            return !this.requiredItem.isEmpty();
        }

        public boolean materialMatches(ItemStack stack) {
            return sameMaterial(stack, this.requiredItem);
        }

        public boolean requiresToolAction() {
            return this.toolAction != BuilderToolAction.NONE;
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
            int price,
            List<BuildBlock> clearingBlocks,
            boolean wall) {
        public StructurePlan(BuilderStructureCatalog.Entry entry, Rotation rotation, Vec3i templateSize,
                             BlockPos localMin, BlockPos localMax, List<BuildBlock> blocks,
                             List<MaterialRequirement> materials, int price, List<BuildBlock> clearingBlocks) {
            this(entry, rotation, templateSize, localMin, localMax, blocks, materials, price, clearingBlocks, false);
        }

        public int blockLimit() {
            return this.wall ? com.jvn.villagerretaliation.settlement.SettlementWall.MAX_BLOCKS
                    : Math.max(128, com.jvn.villagerretaliation.config.VillagerRetaliationConfig.HIRED_BUILDER_MAX_BLOCKS.get());
        }

        public StructurePlan(BuilderStructureCatalog.Entry entry, Rotation rotation, Vec3i templateSize,
                             BlockPos localMin, BlockPos localMax, List<BuildBlock> blocks,
                             List<MaterialRequirement> materials, int price) {
            this(entry, rotation, templateSize, localMin, localMax, blocks, materials, price, List.of());
        }

        public List<BuildBlock> siteBlocks() {
            var siteBlocks = new ArrayList<>(this.blocks);
            siteBlocks.addAll(this.clearingBlocks);
            return siteBlocks;
        }

        public List<BuildBlock> siteBlocks(LevelReader level, BlockPos origin) {
            var siteBlocks = siteBlocks();
            for (var block : this.blocks) {
                if (block.toolAction() != BuilderToolAction.SHOVEL_FLATTEN && block.toolAction() != BuilderToolAction.HOE_TILL) continue;
                var pos = worldPos(origin, block);
                var above = block.localPos().above();
                if (above.getY() > this.localMax.getY() || !level.hasChunkAt(pos.above())
                        || block.state().canSurvive(level, pos)
                        || this.blocks.stream().anyMatch(part -> part.localPos().equals(above))) continue;
                siteBlocks.add(new BuildBlock(above, Blocks.AIR.defaultBlockState(), null, ItemStack.EMPTY, BuilderToolAction.NONE));
            }
            return siteBlocks;
        }

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
