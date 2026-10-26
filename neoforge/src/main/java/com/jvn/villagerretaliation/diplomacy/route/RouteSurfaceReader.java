package com.jvn.villagerretaliation.diplomacy.route;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

final class RouteSurfaceReader implements BlockGetter {
    private final ChunkPos position;
    private final BlockGetter source;
    private final int[] heights = new int[256];
    private final byte[] kinds = new byte[256];
    private final long observed;
    private int cursor;

    RouteSurfaceReader(ChunkPos position, BlockGetter source, long observed) {
        this.position = position;
        this.source = source;
        this.observed = observed;
    }

    boolean step() {
        int x = this.position.getMinBlockX() + (this.cursor & 15);
        int z = this.position.getMinBlockZ() + (this.cursor >> 4);
        int top = this.source instanceof ChunkAccess chunk && chunk.hasPrimedHeightmap(Heightmap.Types.WORLD_SURFACE)
                ? chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15) : getMaxBuildHeight() - 1;
        var at = new BlockPos.MutableBlockPos(x, top, z);
        while (at.getY() > getMinBuildHeight() && clear(at)) at.move(Direction.DOWN);
        var state = getBlockState(at);
        int y = at.getY() + 1;
        this.heights[this.cursor] = y;
        if (y + 1 < getMaxBuildHeight() && clear(at.above()) && clear(at.above(2)) && !hazard(state)) {
            if (state.getFluidState().is(FluidTags.WATER) && state.getFluidState().isSource()
                    && state.getCollisionShape(this, at).isEmpty()) this.kinds[this.cursor] = RouteTerrain.WATER;
            else if (state.getFluidState().isEmpty() && state.isFaceSturdy(this, at, Direction.UP)
                    && !state.is(BlockTags.LEAVES)) this.kinds[this.cursor] = RouteTerrain.WALK;
        }
        return ++this.cursor == 256;
    }

    RouteTerrain result() {
        if (this.cursor != 256) throw new IllegalStateException("Incomplete terrain observation");
        return new RouteTerrain(this.position, this.heights, this.kinds, this.observed);
    }
    private boolean clear(BlockPos position) {
        var state = getBlockState(position);
        return !hazard(state) && state.getFluidState().isEmpty() && state.getCollisionShape(this, position).isEmpty();
    }
    private static boolean hazard(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(Blocks.LAVA) || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.COBWEB)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.MOVING_PISTON) || state.is(Blocks.POINTED_DRIPSTONE);
    }
    @Override public BlockState getBlockState(BlockPos position) {
        if (position.getX() >> 4 != this.position.x || position.getZ() >> 4 != this.position.z)
            return Blocks.BEDROCK.defaultBlockState();
        return this.source.getBlockState(position);
    }
    @Override public FluidState getFluidState(BlockPos position) { return getBlockState(position).getFluidState(); }
    @Override public BlockEntity getBlockEntity(BlockPos position) { return null; }
    @Override public int getHeight() { return this.source.getHeight(); }
    @Override public int getMinBuildHeight() { return this.source.getMinBuildHeight(); }

    static BlockGetter decode(CompoundTag tag, ChunkPos position, int minimum, int height) {
        if (tag.getInt("DataVersion") != SharedConstants.getCurrentVersion().getDataVersion().getVersion())
            throw new IllegalArgumentException("Unsupported terrain data version");
        if (!tag.contains("xPos", Tag.TAG_INT) || !tag.contains("zPos", Tag.TAG_INT)
                || tag.getInt("xPos") != position.x || tag.getInt("zPos") != position.z
                || !tag.contains("sections", Tag.TAG_LIST)) throw new IllegalArgumentException("Malformed terrain chunk");
        var raw = tag.getList("sections", Tag.TAG_COMPOUND);
        if (!raw.equals(tag.get("sections")) || raw.size() > height / 16 + 2) throw new IllegalArgumentException("Invalid terrain sections");
        var codec = PalettedContainer.codecRW(Block.BLOCK_STATE_REGISTRY, BlockState.CODEC,
                PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState());
        var sections = new HashMap<Integer, PalettedContainer<BlockState>>();
        for (var value : raw) {
            var section = (CompoundTag) value;
            if (!section.contains("Y", Tag.TAG_BYTE)) throw new IllegalArgumentException("Missing terrain section height");
            if (!section.contains("block_states", Tag.TAG_COMPOUND)) continue;
            int y = section.getByte("Y");
            if (y * 16 < minimum || y * 16 >= minimum + height) throw new IllegalArgumentException("Terrain section outside dimension");
            var palette = codec.parse(NbtOps.INSTANCE, section.getCompound("block_states")).getOrThrow(IllegalArgumentException::new);
            if (sections.put(y, palette) != null) throw new IllegalArgumentException("Duplicate terrain section");
        }
        return new StoredSurface(Map.copyOf(sections), minimum, height);
    }

    private record StoredSurface(Map<Integer, PalettedContainer<BlockState>> sections, int minimum, int height) implements BlockGetter {
        @Override public BlockState getBlockState(BlockPos position) {
            var section = this.sections.get(position.getY() >> 4);
            return section == null ? Blocks.AIR.defaultBlockState() : section.get(position.getX() & 15, position.getY() & 15, position.getZ() & 15);
        }
        @Override public FluidState getFluidState(BlockPos position) { return getBlockState(position).getFluidState(); }
        @Override public BlockEntity getBlockEntity(BlockPos position) { return null; }
        @Override public int getHeight() { return this.height; }
        @Override public int getMinBuildHeight() { return this.minimum; }
    }
}
