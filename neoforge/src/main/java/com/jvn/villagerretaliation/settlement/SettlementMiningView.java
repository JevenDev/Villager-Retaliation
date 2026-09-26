package com.jvn.villagerretaliation.settlement;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

class SettlementMiningView implements LevelReader {
    private final ServerLevel source;
    private final SettlementMine mine;

    SettlementMiningView(ServerLevel source, SettlementMine mine) {
        this.source = source;
        this.mine = mine;
    }

    @Override
    public BlockState getBlockState(BlockPos position) {
        if (!this.mine.observed(position)) throw new Unavailable();
        return this.mine.getBlockState(position);
    }

    @Override
    public FluidState getFluidState(BlockPos position) {
        return getBlockState(position).getFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos position) {
        if (getBlockState(position).hasBlockEntity()) throw new Unavailable();
        return null;
    }

    @Override
    public boolean hasChunk(int x, int z) {
        return this.mine.sections().values().stream().anyMatch(section -> section.min().getX() >> 4 == x
                && section.min().getZ() >> 4 == z && !section.conflict());
    }

    @Override
    public boolean hasChunkAt(BlockPos position) {
        return this.mine.observed(position);
    }

    @Override
    public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean requireChunk) {
        throw new Unavailable();
    }

    @Override
    public BlockGetter getChunkForCollisions(int x, int z) {
        throw new Unavailable();
    }

    @Override
    public int getHeight(Heightmap.Types type, int x, int z) {
        throw new Unavailable();
    }

    @Override
    public LevelLightEngine getLightEngine() {
        throw new Unavailable();
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        throw new Unavailable();
    }

    @Override
    public BiomeManager getBiomeManager() {
        throw new Unavailable();
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
        throw new Unavailable();
    }

    @Override
    public List<VoxelShape> getEntityCollisions(Entity entity, AABB area) {
        throw new Unavailable();
    }

    @Override
    public WorldBorder getWorldBorder() {
        return this.source.getWorldBorder();
    }

    @Override
    public int getSkyDarken() {
        return this.source.getSkyDarken();
    }

    @Override
    public int getSeaLevel() {
        return this.source.getSeaLevel();
    }

    @Override
    public DimensionType dimensionType() {
        return this.source.dimensionType();
    }

    @Override
    public RegistryAccess registryAccess() {
        return this.source.registryAccess();
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return this.source.enabledFeatures();
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    static final class Unavailable extends RuntimeException {
        Unavailable() {
            super("Mine observations do not contain the requested world state", null, false, false);
        }
    }
}
