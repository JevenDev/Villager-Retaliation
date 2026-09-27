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

final class SettlementExperienceTerrain implements LevelReader {
    private final ServerLevel level;
    private final SettlementBuildingSurvey.ObservedBlocks blocks;

    SettlementExperienceTerrain(ServerLevel level, SettlementRecord town) {
        this.level = level;
        this.blocks = new SettlementBuildingSurvey.ObservedBlocks(level, null, town);
    }

    @Override
    public BlockState getBlockState(BlockPos position) {
        if (!this.blocks.observed(position)) throw new Unobserved();
        return this.blocks.getBlockState(position);
    }

    @Override
    public FluidState getFluidState(BlockPos position) {
        return getBlockState(position).getFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos position) {
        if (!hasChunk(position.getX() >> 4, position.getZ() >> 4)) throw new Unobserved();
        return this.blocks.getBlockEntity(position);
    }

    @Override
    public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean required) {
        var chunk = this.level.getChunkSource().getChunkNow(x, z);
        if (chunk == null && required) throw new Unobserved();
        return chunk;
    }

    @Override
    public BlockGetter getChunkForCollisions(int x, int z) {
        return this;
    }

    @Override
    public boolean hasChunk(int x, int z) {
        return this.level.getChunkSource().getChunkNow(x, z) != null;
    }

    @Override
    public int getHeight(Heightmap.Types type, int x, int z) {
        var chunk = getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
        return chunk.getHeight(type, x & 15, z & 15) + 1;
    }

    @Override
    public WorldBorder getWorldBorder() {
        return this.level.getWorldBorder();
    }

    @Override
    public List<VoxelShape> getEntityCollisions(Entity entity, AABB bounds) {
        return this.level.getEntityCollisions(entity, bounds);
    }

    @Override
    public int getSkyDarken() {
        return this.level.getSkyDarken();
    }

    @Override
    public BiomeManager getBiomeManager() {
        return this.level.getBiomeManager().withDifferentSource(this);
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
        throw new Unobserved();
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    @Override
    public int getSeaLevel() {
        return this.level.getSeaLevel();
    }

    @Override
    public DimensionType dimensionType() {
        return this.level.dimensionType();
    }

    @Override
    public RegistryAccess registryAccess() {
        return this.level.registryAccess();
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return this.level.enabledFeatures();
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        return this.level.getShade(direction, shade);
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return this.level.getLightEngine();
    }

    static final class Unobserved extends RuntimeException {
        private Unobserved() {
            super(null, null, false, false);
        }
    }
}
