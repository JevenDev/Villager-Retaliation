package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.ScheduledTick;

final class SettlementTreeWorld implements WorldGenLevel {
    private final ServerLevel source;
    private final SettlementForest forest;
    private final RandomSource random;
    private final Map<BlockPos, BlockState> changes = new LinkedHashMap<>();
    private final List<SettlementTerrainTick> ticks = new ArrayList<>();
    private long subTick;

    SettlementTreeWorld(ServerLevel source, SettlementForest forest, RandomSource random) {
        this.source = source;
        this.forest = forest;
        this.random = random;
    }

    boolean commit() {
        return this.forest.change(this.changes, this.ticks);
    }

    @Override
    public BlockState getBlockState(BlockPos position) {
        if (!this.forest.observed(position)) throw new Unavailable("tree_needs_observation");
        return this.changes.getOrDefault(position, this.forest.getBlockState(position));
    }

    @Override
    public FluidState getFluidState(BlockPos position) {
        return getBlockState(position).getFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos position) {
        if (getBlockState(position).hasBlockEntity()) throw new Unavailable("tree_needs_native_growth");
        return null;
    }

    @Override
    public boolean setBlock(BlockPos position, BlockState state, int flags, int recursionLeft) {
        if (getBlockState(position).hasBlockEntity() || state.hasBlockEntity()) throw new Unavailable("tree_needs_native_growth");
        if (this.changes.size() >= 4096 && !this.changes.containsKey(position)) throw new Unavailable("tree_needs_native_growth");
        this.changes.put(position.immutable(), state);
        return true;
    }

    @Override
    public boolean isStateAtPosition(BlockPos position, Predicate<BlockState> predicate) {
        return predicate.test(getBlockState(position));
    }

    @Override
    public boolean isFluidAtPosition(BlockPos position, Predicate<FluidState> predicate) {
        return predicate.test(getFluidState(position));
    }

    @Override
    public boolean ensureCanWrite(BlockPos position) {
        return this.forest.observed(position);
    }

    @Override
    public LevelTickAccess<Block> getBlockTicks() {
        return tickAccess(false);
    }

    @Override
    public LevelTickAccess<Fluid> getFluidTicks() {
        return tickAccess(true);
    }

    private <T> LevelTickAccess<T> tickAccess(boolean fluid) {
        return new LevelTickAccess<>() {
            @Override
            public void schedule(ScheduledTick<T> tick) {
                if (!forest.observed(tick.pos())) throw new Unavailable("tree_needs_observation");
                var type = fluid ? BuiltInRegistries.FLUID.getKey((Fluid) tick.type()) : BuiltInRegistries.BLOCK.getKey((Block) tick.type());
                long wait = Math.max(0L, tick.triggerTick() - source.getGameTime());
                if (wait > 24_000L) throw new Unavailable("tree_needs_native_growth");
                int delay = (int) wait;
                var pending = new SettlementTerrainTick(tick.pos(), type, fluid, delay, tick.priority());
                if (!ticks.contains(pending)) ticks.add(pending);
                if (ticks.size() > 8192) throw new Unavailable("tree_needs_native_growth");
            }

            @Override
            public boolean hasScheduledTick(BlockPos position, T type) {
                var key = fluid ? BuiltInRegistries.FLUID.getKey((Fluid) type) : BuiltInRegistries.BLOCK.getKey((Block) type);
                return ticks.stream().anyMatch(tick -> tick.fluid() == fluid && tick.position().equals(position) && tick.type().equals(key));
            }

            @Override
            public boolean willTickThisTick(BlockPos position, T type) {
                return false;
            }

            @Override
            public int count() {
                return (int) ticks.stream().filter(tick -> tick.fluid() == fluid).count();
            }
        };
    }

    @Override
    public long nextSubTickCount() {
        return this.subTick++;
    }

    @Override
    public long getSeed() {
        return this.source.getSeed();
    }

    @Override
    public RandomSource getRandom() {
        return this.random;
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
    public LevelData getLevelData() {
        return this.source.getLevelData();
    }

    @Override
    public int getSeaLevel() {
        return this.source.getSeaLevel();
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    @Override
    public int getSkyDarken() {
        return this.source.getSkyDarken();
    }

    @Override
    public boolean hasChunk(int x, int z) {
        return this.forest.sections().values().stream().anyMatch(section -> section.min().getX() >> 4 == x
                && section.min().getZ() >> 4 == z && !section.conflict());
    }

    @Override
    public ServerLevel getLevel() {
        throw new Unavailable("tree_needs_native_growth");
    }

    @Override
    public ChunkSource getChunkSource() {
        throw new Unavailable("tree_needs_native_growth");
    }

    @Override
    public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean requireChunk) {
        throw new Unavailable("tree_needs_native_growth");
    }

    @Override
    public LevelLightEngine getLightEngine() {
        throw new Unavailable("tree_needs_native_growth");
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        throw new Unavailable("tree_needs_native_growth");
    }

    @Override
    public int getHeight(Heightmap.Types type, int x, int z) {
        throw new Unavailable("tree_needs_observation");
    }

    @Override
    public BiomeManager getBiomeManager() {
        throw new Unavailable("tree_needs_native_growth");
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
        throw new Unavailable("tree_needs_native_growth");
    }

    @Override
    public WorldBorder getWorldBorder() {
        throw new Unavailable("tree_needs_native_growth");
    }

    @Override
    public DifficultyInstance getCurrentDifficultyAt(BlockPos position) {
        throw new Unavailable("tree_needs_native_growth");
    }

    @Override
    public MinecraftServer getServer() {
        throw new Unavailable("tree_needs_native_growth");
    }

    @Override
    public List<Entity> getEntities(Entity entity, AABB area, Predicate<? super Entity> predicate) {
        throw new Unavailable("tree_needs_native_growth");
    }

    @Override
    public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> type, AABB area, Predicate<? super T> predicate) {
        throw new Unavailable("tree_needs_native_growth");
    }

    @Override
    public List<? extends Player> players() {
        throw new Unavailable("tree_needs_native_growth");
    }

    @Override
    public boolean removeBlock(BlockPos position, boolean moving) {
        throw new Unavailable("tree_needs_native_growth");
    }

    @Override
    public boolean destroyBlock(BlockPos position, boolean drops, Entity entity, int recursionLeft) {
        throw new Unavailable("tree_needs_native_growth");
    }

    @Override
    public boolean addFreshEntity(Entity entity) {
        throw new Unavailable("tree_needs_native_growth");
    }

    @Override
    public void playSound(Player player, BlockPos position, SoundEvent sound, SoundSource source, float volume, float pitch) {
        throw new Unavailable("tree_needs_native_growth");
    }

    @Override
    public void addParticle(ParticleOptions particle, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        throw new Unavailable("tree_needs_native_growth");
    }

    @Override
    public void levelEvent(Player player, int type, BlockPos position, int data) {
        throw new Unavailable("tree_needs_native_growth");
    }

    @Override
    public void gameEvent(Holder<GameEvent> event, Vec3 position, GameEvent.Context context) {
        throw new Unavailable("tree_needs_native_growth");
    }

    static final class Unavailable extends RuntimeException {
        Unavailable(String status) {
            super(status, null, false, false);
        }
    }
}
