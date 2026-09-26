package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.ScheduledTick;

final class SettlementMinePhysics extends SettlementMiningView implements LevelAccessor {
    private final ServerLevel source;
    private final SettlementMine mine;
    private final Map<BlockPos, BlockState> changes = new LinkedHashMap<>();
    private final List<SettlementTerrainTick> ticks = new ArrayList<>();
    private final List<SettlementTerrainDrop> drops = new ArrayList<>();
    private long subTick;
    private int reads;

    private SettlementMinePhysics(ServerLevel source, SettlementMine mine) {
        super(source, mine);
        this.source = source;
        this.mine = mine;
    }

    static void advance(ServerLevel level, SettlementMine mine) {
        var deferred = new java.util.HashSet<BlockPos>();
        for (int work = 0; work < 256 && !mine.reactions().isEmpty(); work++) {
            var iterator = mine.reactions().iterator();
            var position = iterator.next();
            iterator.remove();
            if (level.shouldTickBlocksAt(net.minecraft.world.level.ChunkPos.asLong(position))) continue;
            if (!deferred.add(position)) {
                mine.reactions().add(position);
                break;
            }
            var world = new SettlementMinePhysics(level, mine);
            try {
                world.update(position);
                if (!mine.react(world.changes, world.ticks, world.drops)) mine.reactions().add(position);
                else deferred.remove(position);
            } catch (Unavailable unavailable) {
                mine.reactions().add(position);
            }
        }
    }

    private void update(BlockPos position) {
        var state = getBlockState(position);
        if (state.isAir()) return;
        if (state.getBlock().getClass() == ColoredFallingBlock.class && fall(position, state)) return;
        for (var direction : Direction.values()) {
            state = getBlockState(position);
            if (state.isAir()) return;
            var neighbor = position.relative(direction);
            if (!this.mine.observed(neighbor)) throw new Unavailable();
            var updated = state.updateShape(direction, getBlockState(neighbor), this, position, neighbor);
            Block.updateOrDestroy(state, updated, this, position, Block.UPDATE_ALL, 512);
        }
    }

    private boolean fall(BlockPos position, BlockState state) {
        if (!FallingBlock.isFree(getBlockState(position.below()))) return false;
        BlockPos landing = position.below();
        while (true) {
            var support = getBlockState(landing);
            var shape = support.getCollisionShape(this, landing);
            if (!shape.isEmpty()) {
                if (shape.max(Direction.Axis.Y) >= 1.0D) landing = landing.above();
                break;
            }
            landing = landing.below();
        }
        if (landing.equals(position)) return false;
        var replaced = getBlockState(landing);
        setBlock(position, state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL, 512);
        if (replaced.canBeReplaced() && state.canSurvive(this, landing)
                && !FallingBlock.isFree(getBlockState(landing.below()))) {
            setBlock(landing, state, Block.UPDATE_ALL, 512);
        } else if (this.source.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            this.drops.add(SettlementTerrainDrop.create(this.source, landing, new ItemStack(state.getBlock())));
        }
        return true;
    }

    @Override
    public BlockState getBlockState(BlockPos position) {
        if (++this.reads > 4096) throw new Unavailable();
        return this.changes.getOrDefault(position, super.getBlockState(position));
    }

    @Override
    public boolean setBlock(BlockPos position, BlockState state, int flags, int recursionLeft) {
        var before = getBlockState(position);
        if (before.hasBlockEntity() || state.hasBlockEntity() || this.changes.size() >= 256) throw new Unavailable();
        if (before == state) return false;
        this.changes.put(position.immutable(), state);
        return true;
    }

    @Override
    public boolean destroyBlock(BlockPos position, boolean dropItems, Entity entity, int recursionLeft) {
        var state = getBlockState(position);
        if (state.isAir()) return false;
        if (state.hasBlockEntity()) throw new Unavailable();
        if (dropItems && this.source.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
            for (var item : Block.getDrops(state, this.source, position, null, entity, ItemStack.EMPTY)) {
                if (!item.isEmpty()) this.drops.add(SettlementTerrainDrop.create(this.source, position, item));
            }
        }
        return setBlock(position, state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL, recursionLeft);
    }

    @Override
    public boolean removeBlock(BlockPos position, boolean moving) {
        return setBlock(position, getFluidState(position).createLegacyBlock(), Block.UPDATE_ALL, 512);
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
                if (!mine.observed(tick.pos()) || ticks.size() >= 512) throw new Unavailable();
                long delay = Math.max(0L, tick.triggerTick() - source.getGameTime());
                if (delay > 24_000L) throw new Unavailable();
                var type = fluid ? BuiltInRegistries.FLUID.getKey((Fluid) tick.type()) : BuiltInRegistries.BLOCK.getKey((Block) tick.type());
                var pending = new SettlementTerrainTick(tick.pos(), type, fluid, (int) delay, tick.priority());
                if (!ticks.contains(pending)) ticks.add(pending);
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
    public RandomSource getRandom() {
        return this.source.getRandom();
    }

    @Override
    public LevelData getLevelData() {
        return this.source.getLevelData();
    }

    @Override
    public ChunkSource getChunkSource() {
        throw new Unavailable();
    }

    @Override
    public DifficultyInstance getCurrentDifficultyAt(BlockPos position) {
        throw new Unavailable();
    }

    @Override
    public MinecraftServer getServer() {
        throw new Unavailable();
    }

    @Override
    public List<Entity> getEntities(Entity entity, AABB area, Predicate<? super Entity> predicate) {
        throw new Unavailable();
    }

    @Override
    public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> type, AABB area, Predicate<? super T> predicate) {
        throw new Unavailable();
    }

    @Override
    public List<? extends Player> players() {
        throw new Unavailable();
    }

    @Override
    public boolean addFreshEntity(Entity entity) {
        throw new Unavailable();
    }

    @Override
    public void playSound(Player player, BlockPos position, SoundEvent sound, SoundSource source, float volume, float pitch) {
        throw new Unavailable();
    }

    @Override
    public void addParticle(ParticleOptions particle, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        throw new Unavailable();
    }

    @Override
    public void levelEvent(Player player, int type, BlockPos position, int data) {
        throw new Unavailable();
    }

    @Override
    public void gameEvent(Holder<GameEvent> event, Vec3 position, GameEvent.Context context) {
        throw new Unavailable();
    }
}
