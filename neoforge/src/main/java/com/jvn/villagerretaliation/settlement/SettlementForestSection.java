package com.jvn.villagerretaliation.settlement;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

final class SettlementForestSection {
    private final SettlementTerrainSection terrain;
    private final Map<BlockPos, Double> growth = new LinkedHashMap<>();
    private Map<BlockPos, SettlementForestLight.Source> lightSources = Map.of();
    private boolean environmentKnown;
    private long checkedAt;

    private SettlementForestSection(SettlementTerrainSection terrain) {
        this.terrain = terrain;
    }

    static SettlementForestSection capture(UUID area, BlockPos min, BlockPos max, LevelChunk chunk) {
        var section = new SettlementForestSection(SettlementTerrainSection.capture("Forest", area, min, max, chunk));
        section.refreshGrowth(Map.of());
        section.observeEnvironment(chunk);
        return section;
    }

    SettlementTerrainSection terrain() {
        return this.terrain;
    }

    BlockPos origin() {
        return this.terrain.origin();
    }

    BlockPos min() {
        return this.terrain.min();
    }

    BlockPos max() {
        return this.terrain.max();
    }

    boolean pending() {
        return this.terrain.pending();
    }

    boolean conflict() {
        return this.terrain.conflict();
    }

    boolean contains(BlockPos position) {
        return this.terrain.contains(position);
    }

    BlockState state(BlockPos position) {
        return this.terrain.state(position);
    }

    boolean canChange(BlockPos position, BlockState state) {
        return this.terrain.canChange(position, state);
    }

    void change(Map<BlockPos, BlockState> changes) {
        var previous = new LinkedHashMap<BlockPos, BlockState>();
        changes.keySet().forEach(position -> previous.put(position.immutable(), state(position)));
        this.terrain.change(changes);
        changes.forEach((position, state) -> observeGrowth(position, state, previous.get(position)));
    }

    void schedule(SettlementTerrainTick tick) {
        this.terrain.schedule(tick);
    }

    boolean reconcile(LevelChunk chunk) {
        var previous = new LinkedHashMap<BlockPos, BlockState>();
        this.growth.keySet().forEach(position -> previous.put(position, state(position)));
        long version = this.terrain.version();
        if (!this.terrain.reconcile(chunk)) return false;
        if (this.terrain.version() > version) {
            this.growth.clear();
            pause(chunk.getLevel().getGameTime());
        }
        refreshGrowth(previous);
        observeEnvironment(chunk);
        return true;
    }

    private void refreshGrowth(Map<BlockPos, BlockState> previous) {
        for (BlockPos position : BlockPos.betweenClosed(min(), max())) {
            observeGrowth(position, state(position), previous.get(position));
        }
    }

    int size() {
        return this.terrain.size();
    }

    boolean environmentKnown() {
        return this.environmentKnown;
    }

    Map<BlockPos, SettlementForestLight.Source> lightSources() {
        return this.lightSources;
    }

    java.util.Set<BlockPos> saplings() {
        return java.util.Set.copyOf(this.growth.keySet());
    }

    double growth(BlockPos position) {
        return this.growth.getOrDefault(position, 0.0D);
    }

    void growth(BlockPos position, double progress) {
        if (this.growth.containsKey(position)) this.growth.put(position.immutable(), Math.clamp(progress, 0.0D, 1.0D));
    }

    int elapsed(long now) {
        int elapsed = SettlementSimulationClock.elapsed(this.checkedAt, now);
        this.checkedAt = Math.max(now, this.checkedAt);
        return elapsed;
    }

    void pause(long now) {
        this.checkedAt = Math.max(now, this.checkedAt);
    }

    private void observeGrowth(BlockPos position, BlockState state, BlockState previous) {
        if (!SettlementTreeGrowth.growsNaturally(state)) this.growth.remove(position);
        else if (state != previous || !this.growth.containsKey(position)) this.growth.put(position.immutable(), 0.0D);
    }

    private void observeEnvironment(LevelChunk chunk) {
        if (!this.environmentKnown) this.checkedAt = chunk.getLevel().getGameTime();
        this.checkedAt = Math.max(this.checkedAt, SettlementChunkReceipts.lastTick(chunk));
        this.lightSources = SettlementForestLight.observe(chunk, min(), max());
        this.environmentKnown = true;
    }

    int index(BlockPos position) {
        return this.terrain.index(position);
    }

    static BlockPos sectionOrigin(BlockPos position) {
        return SettlementTerrainSection.sectionOrigin(position);
    }

    CompoundTag save() {
        var tag = this.terrain.save();
        tag.putLong("CheckedAt", this.checkedAt);
        if (this.environmentKnown) tag.put("LightBoundary", SettlementForestLight.save(this.lightSources));
        var growth = new ListTag();
        this.growth.forEach((position, progress) -> {
            if (progress > 0.0D) {
                var entry = new CompoundTag();
                entry.putLong("Position", position.asLong());
                entry.putDouble("Progress", progress);
                growth.add(entry);
            }
        });
        tag.put("Growth", growth);
        return tag;
    }

    static SettlementForestSection load(UUID area, CompoundTag tag) {
        var section = new SettlementForestSection(SettlementTerrainSection.load("Forest", area, tag));
        BlockPos min = section.min();
        BlockPos max = section.max();
        section.checkedAt = Math.max(0L, tag.getLong("CheckedAt"));
        section.environmentKnown = tag.contains("LightBoundary", Tag.TAG_COMPOUND);
        if (section.environmentKnown) section.lightSources = SettlementForestLight.load(tag.getCompound("LightBoundary"), min, max);
        for (BlockPos position : BlockPos.betweenClosed(min, max)) {
            if (SettlementTreeGrowth.growsNaturally(section.state(position))) section.growth.put(position.immutable(), 0.0D);
        }
        var seenGrowth = new java.util.HashSet<BlockPos>();
        for (Tag raw : tag.getList("Growth", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) raw;
            var position = BlockPos.of(entry.getLong("Position"));
            double progress = entry.getDouble("Progress");
            if (!entry.contains("Position", Tag.TAG_LONG) || !section.growth.containsKey(position)
                    || !seenGrowth.add(position) || !Double.isFinite(progress) || progress < 0.0D || progress > 1.0D) {
                throw new IllegalArgumentException("Forest growth must reference one observed sapling with bounded progress");
            }
            section.growth.put(position, progress);
        }
        return section;
    }
}
