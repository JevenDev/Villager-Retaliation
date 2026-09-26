package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.entity.VillagerFishingHook;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class SettlementFishingSpot implements BlockGetter {
    private final UUID area;
    private final BlockPos water;
    private final BlockPos approach;
    private final BlockPos min;
    private final BlockPos max;
    private final List<BlockState> terrain = new ArrayList<>();
    private boolean forbiddenFloor;
    private boolean skyVisible;
    private boolean rainExposed;

    private SettlementFishingSpot(UUID area, BlockPos water, BlockPos approach) {
        if (water.distSqr(approach) > 64.0D) {
            throw new IllegalArgumentException("Fishing approach is outside casting range");
        }
        this.area = area;
        this.water = water.immutable();
        this.approach = approach.immutable();
        this.min = new BlockPos(Math.min(water.getX() - 3, approach.getX() - 1),
                Math.min(water.getY() - 2, approach.getY() - 1), Math.min(water.getZ() - 3, approach.getZ() - 1));
        this.max = new BlockPos(Math.max(water.getX() + 3, approach.getX() + 1),
                Math.max(water.getY() + 3, approach.getY() + 2), Math.max(water.getZ() + 3, approach.getZ() + 1));
    }

    public UUID area() {
        return this.area;
    }

    public BlockPos water() {
        return this.water;
    }

    public BlockPos approach() {
        return this.approach;
    }

    boolean skyVisible() {
        return this.skyVisible;
    }

    boolean rainExposed() {
        return this.rainExposed;
    }

    boolean openWater() {
        return VillagerFishingHook.isOpenWater(this, this.water);
    }

    boolean within(SettlementArea area) {
        return area != null && area.id().equals(this.area) && area.type() == SettlementAreaType.FISHERY
                && area.contains(this.approach)
                && this.water.getX() >= area.min().getX() - 8 && this.water.getX() <= area.max().getX() + 8
                && this.water.getY() >= area.min().getY() - 8 && this.water.getY() <= area.max().getY() + 8
                && this.water.getZ() >= area.min().getZ() - 8 && this.water.getZ() <= area.max().getZ() + 8;
    }

    static SettlementFishingSpot capture(ServerLevel level, SettlementArea area, BlockPos water, BlockPos approach, LevelChunk supplied) {
        var spot = new SettlementFishingSpot(area.id(), water, approach);
        for (BlockPos position : BlockPos.betweenClosed(spot.min, spot.max)) {
            var chunk = chunk(level, supplied, position);
            if (chunk == null) {
                return null;
            }
            spot.terrain.add(chunk.getBlockState(position));
        }
        spot.observe(level, supplied);
        return spot.within(area) && spot.valid() ? spot : null;
    }

    boolean observe(ServerLevel level, LevelChunk supplied) {
        boolean changed = false;
        int index = 0;
        for (BlockPos position : BlockPos.betweenClosed(this.min, this.max)) {
            var chunk = chunk(level, supplied, position);
            if (chunk != null) {
                var state = chunk.getBlockState(position);
                if (this.terrain.get(index) != state) {
                    this.terrain.set(index, state);
                    changed = true;
                }
            }
            index++;
        }
        var floorChunk = chunk(level, supplied, this.approach.below());
        if (floorChunk != null) {
            boolean forbidden = floorChunk.getBlockEntity(this.approach.below(), LevelChunk.EntityCreationType.CHECK) instanceof Container;
            changed |= this.forbiddenFloor != forbidden;
            this.forbiddenFloor = forbidden;
        }
        var waterChunk = chunk(level, supplied, this.water);
        if (waterChunk != null) {
            boolean sky = level.getBrightness(LightLayer.SKY, this.water.above()) == 15;
            boolean rain = sky && waterChunk.getHeight(Heightmap.Types.MOTION_BLOCKING, this.water.getX(), this.water.getZ()) < this.water.above().getY()
                    && waterChunk.getNoiseBiome(this.water.getX() >> 2, this.water.above().getY() >> 2, this.water.getZ() >> 2)
                            .value().getPrecipitationAt(this.water.above()) == net.minecraft.world.level.biome.Biome.Precipitation.RAIN;
            changed |= this.skyVisible != sky || this.rainExposed != rain;
            this.skyVisible = sky;
            this.rainExposed = rain;
        }
        return changed;
    }

    boolean valid() {
        return getFluidState(this.water).is(Fluids.WATER) && !getFluidState(this.water.above()).is(FluidTags.WATER)
                && getBlockState(this.water).getCollisionShape(this, this.water).isEmpty()
                && dryApproach(this, this.approach) && !this.forbiddenFloor && clearCast(this, this.approach, this.water);
    }

    static boolean dryApproach(BlockGetter blocks, BlockPos position) {
        var feet = blocks.getBlockState(position);
        var head = blocks.getBlockState(position.above());
        return feet.getFluidState().isEmpty() && head.getFluidState().isEmpty()
                && !(feet.getBlock() instanceof BaseFireBlock) && !(head.getBlock() instanceof BaseFireBlock)
                && feet.getCollisionShape(blocks, position).isEmpty() && head.getCollisionShape(blocks, position.above()).isEmpty()
                && blocks.getBlockState(position.below()).isSolid();
    }

    static boolean clearCast(BlockGetter blocks, BlockPos approach, BlockPos water) {
        Vec3 start = new Vec3(approach.getX() + 0.5D, approach.getY() + 1.4D, approach.getZ() + 0.5D);
        Vec3 target = water.getCenter().add(0.0D, 0.25D, 0.0D);
        return blocks.clip(new ClipContext(start, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()))
                .getType() == HitResult.Type.MISS;
    }

    static LevelChunk chunk(ServerLevel level, LevelChunk supplied, BlockPos position) {
        int x = Math.floorDiv(position.getX(), 16);
        int z = Math.floorDiv(position.getZ(), 16);
        return supplied != null && supplied.getPos().x == x && supplied.getPos().z == z
                ? supplied : level.getChunkSource().getChunkNow(x, z);
    }

    @Override
    public BlockState getBlockState(BlockPos position) {
        if (position.getX() < this.min.getX() || position.getX() > this.max.getX()
                || position.getY() < this.min.getY() || position.getY() > this.max.getY()
                || position.getZ() < this.min.getZ() || position.getZ() > this.max.getZ()) {
            return Blocks.AIR.defaultBlockState();
        }
        int width = this.max.getX() - this.min.getX() + 1;
        int height = this.max.getY() - this.min.getY() + 1;
        return this.terrain.get(position.getX() - this.min.getX()
                + width * (position.getY() - this.min.getY() + height * (position.getZ() - this.min.getZ())));
    }

    @Override
    public FluidState getFluidState(BlockPos position) {
        return getBlockState(position).getFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos position) {
        return null;
    }

    @Override
    public int getHeight() {
        return this.max.getY() - this.min.getY() + 1;
    }

    @Override
    public int getMinBuildHeight() {
        return this.min.getY();
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Area", this.area);
        tag.putLong("Water", this.water.asLong());
        tag.putLong("Approach", this.approach.asLong());
        var palette = new ArrayList<BlockState>();
        int[] blocks = new int[this.terrain.size()];
        for (int index = 0; index < blocks.length; index++) {
            BlockState state = this.terrain.get(index);
            int id = palette.indexOf(state);
            if (id < 0) {
                id = palette.size();
                palette.add(state);
            }
            blocks[index] = id;
        }
        ListTag states = new ListTag();
        palette.forEach(state -> states.add(NbtUtils.writeBlockState(state)));
        tag.put("Palette", states);
        tag.putIntArray("Blocks", blocks);
        tag.putBoolean("ForbiddenFloor", this.forbiddenFloor);
        tag.putBoolean("SkyVisible", this.skyVisible);
        tag.putBoolean("RainExposed", this.rainExposed);
        return tag;
    }

    static SettlementFishingSpot load(CompoundTag tag) {
        if (!tag.hasUUID("Area") || !tag.contains("Water", Tag.TAG_LONG) || !tag.contains("Approach", Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Fishing site identity or positions are missing");
        }
        var spot = new SettlementFishingSpot(tag.getUUID("Area"), BlockPos.of(tag.getLong("Water")), BlockPos.of(tag.getLong("Approach")));
        var states = tag.getList("Palette", Tag.TAG_COMPOUND);
        var palette = new ArrayList<BlockState>();
        states.forEach(raw -> palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), (CompoundTag) raw)));
        int[] blocks = tag.getIntArray("Blocks");
        int expected = (spot.max.getX() - spot.min.getX() + 1) * (spot.max.getY() - spot.min.getY() + 1)
                * (spot.max.getZ() - spot.min.getZ() + 1);
        if (blocks.length != expected || palette.isEmpty() || palette.size() > expected) {
            throw new IllegalArgumentException("Fishing terrain snapshot is incomplete");
        }
        for (int id : blocks) {
            if (id < 0 || id >= palette.size()) {
                throw new IllegalArgumentException("Fishing terrain palette reference is invalid");
            }
            spot.terrain.add(palette.get(id));
        }
        spot.forbiddenFloor = tag.getBoolean("ForbiddenFloor");
        spot.skyVisible = tag.getBoolean("SkyVisible");
        spot.rainExposed = tag.getBoolean("RainExposed");
        return spot;
    }
}
