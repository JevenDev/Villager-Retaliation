package com.jvn.villagerretaliation.diplomacy.route;

import com.jvn.villagerretaliation.VillagerRetaliation;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class RouteGroundService {
    private static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, VillagerRetaliation.MOD_ID);
    // keep the attachment id so saved route ground protections survive upgrades
    static final Supplier<AttachmentType<CompoundTag>> RECEIPTS = TYPES.register("natural_roads",
            () -> AttachmentType.builder(() -> new CompoundTag()).serialize(CompoundTag.CODEC).build());
    private static boolean placing;
    private RouteGroundService() { }
    public static void register(IEventBus bus) { TYPES.register(bus); }

    public static void changed(LevelChunk chunk, BlockPos position) {
        if (placing || !(chunk.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || !level.dimension().equals(Level.OVERWORLD)) return;
        if (position.getY() < chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, position.getX(), position.getZ()) - 2) return;
        var tag = chunk.getData(RECEIPTS);
        mark(tag, "CustomTouched", column(position));
        chunk.setUnsaved(true);
    }

    static boolean street(net.minecraft.world.level.levelgen.structure.StructurePiece piece) {
        if (!(piece instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece pool)
                || !(pool.getElement() instanceof net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement single)) return false;
        return ((com.jvn.villagerretaliation.mixin.RoutePoolElementAccessor) single).villagerretaliation$roadTemplate().left()
                .map(id -> id.getPath().startsWith("village/") && (id.getPath().contains("/streets/") || id.getPath().contains("/terminators/"))).orElse(false);
    }
    public static String customGroundProblem(ServerLevel level, BlockPos ground) { return customGroundProblem(level, ground, null); }
    public static String customGroundProblem(ServerLevel level, BlockPos ground, com.jvn.villagerretaliation.allegiance.VillageAllegianceId origin) {
        if (!level.hasChunkAt(ground)) return "Load this section before developing it";
        var town = com.jvn.villagerretaliation.settlement.SettlementSavedData.get(level).at(level.dimension().location(), ground).orElse(null);
        if (!level.getWorldBorder().isWithinBounds(ground) || town != null && (!town.id().equals(origin) || town.protectsRouteGround(ground)))
            return "Protected settlement ground";
        var chunk = level.getChunkAt(ground);
        if (customGroundChanged(level, ground)) return "Changed ground is protected";
        var state = level.getBlockState(ground);
        if (level.getBlockEntity(ground) != null || !state.getFluidState().isEmpty()
                || !com.jvn.villagerretaliation.settlement.SettlementPathTerrain.soil(state) && !state.is(Blocks.DIRT_PATH)
                    && !state.is(Blocks.GRAVEL)) return "Unsuitable ground";
        if (!level.getBlockState(ground.above()).isAir() || !level.getBlockState(ground.above(2)).isAir()) return "Clearance is obstructed";
        for (var structure : chunk.getAllReferences().keySet()) {
            var start = level.structureManager().getStructureWithPieceAt(ground, structure);
            if (start.isValid() && start.getPieces().stream().anyMatch(piece -> piece.getBoundingBox().isInside(ground) && !street(piece)))
                return "Structure ground is protected";
        }
        return "";
    }
    public static boolean customGroundChanged(ServerLevel level, BlockPos ground) {
        if (!level.hasChunkAt(ground)) return true;
        var chunk = level.getChunkAt(ground); var receipt = chunk.getExistingDataOrNull(RECEIPTS.get());
        return touched(chunk, ground) || receipt != null && marked(receipt, "CustomTouched", column(ground));
    }
    public static boolean wearGround(ServerLevel level, BlockPos ground, int wear) { return wearGround(level, ground, wear, null); }
    public static boolean wearGround(ServerLevel level, BlockPos ground, int wear, com.jvn.villagerretaliation.allegiance.VillageAllegianceId origin) {
        if (!customGroundProblem(level, ground, origin).isEmpty()) return false;
        var target = wear >= 8 ? Blocks.DIRT_PATH : wear >= 4 ? Blocks.ROOTED_DIRT : wear >= 2 ? Blocks.COARSE_DIRT : null;
        if (target == null || level.getBlockState(ground).is(Blocks.DIRT_PATH)) return true;
        placing = true;
        try { return level.setBlock(ground, target.defaultBlockState(), 3) || level.getBlockState(ground).is(target); }
        finally { placing = false; }
    }

    private static boolean touched(ChunkAccess chunk, BlockPos position) {
        var data = chunk.getExistingDataOrNull(RECEIPTS.get());
        return data != null && marked(data, "Touched", column(position));
    }
    private static int column(BlockPos position) { return (position.getZ() & 15) * 16 + (position.getX() & 15); }
    private static boolean marked(CompoundTag tag, String key, int column) {
        var bits = tag.getLongArray(key);
        return bits.length > (column >> 6) && (bits[column >> 6] & (1L << (column & 63))) != 0;
    }
    private static void mark(CompoundTag tag, String key, int column) {
        var bits = tag.getLongArray(key);
        if (bits.length < 4) bits = java.util.Arrays.copyOf(bits, 4);
        bits[column >> 6] |= 1L << (column & 63);
        tag.putLongArray(key, bits);
    }
}
