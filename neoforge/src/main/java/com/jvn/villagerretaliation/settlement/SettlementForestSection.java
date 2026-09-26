package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

final class SettlementForestSection {
    private final UUID area;
    private final UUID id;
    private final BlockPos min;
    private final BlockPos max;
    private final BlockState[] baseline;
    private final BlockState[] blocks;
    private final java.util.List<SettlementForestTick> ticks = new ArrayList<>();
    private final Map<BlockPos, Double> growth = new java.util.LinkedHashMap<>();
    private Map<BlockPos, SettlementForestLight.Source> lightSources = Map.of();
    private boolean environmentKnown;
    private long checkedAt;
    private long version;
    private boolean pending;
    private boolean conflict;

    private SettlementForestSection(UUID area, UUID id, BlockPos min, BlockPos max, BlockState[] baseline, BlockState[] blocks) {
        this.area = area;
        this.id = id;
        this.min = min.immutable();
        this.max = max.immutable();
        this.baseline = baseline;
        this.blocks = blocks;
    }

    static SettlementForestSection capture(UUID area, BlockPos min, BlockPos max, LevelChunk chunk) {
        validateBounds(min, max);
        if (min.getX() >> 4 != chunk.getPos().x || min.getZ() >> 4 != chunk.getPos().z) {
            throw new IllegalArgumentException("Forest section requires its observed chunk");
        }
        String key = receiptKey(area, min);
        UUID id = SettlementChunkReceipts.identity(chunk, key);
        int size = volume(min, max);
        var section = new SettlementForestSection(area, id, min, max, new BlockState[size], new BlockState[size]);
        section.version = SettlementChunkReceipts.version(chunk, key, id);
        section.refresh(chunk);
        return section;
    }

    BlockPos origin() {
        return sectionOrigin(this.min);
    }

    BlockPos min() {
        return this.min;
    }

    BlockPos max() {
        return this.max;
    }

    boolean pending() {
        return this.pending;
    }

    boolean conflict() {
        return this.conflict;
    }

    boolean contains(BlockPos position) {
        return position.getX() >= this.min.getX() && position.getX() <= this.max.getX()
                && position.getY() >= this.min.getY() && position.getY() <= this.max.getY()
                && position.getZ() >= this.min.getZ() && position.getZ() <= this.max.getZ();
    }

    BlockState state(BlockPos position) {
        if (!contains(position)) throw new IllegalArgumentException("Position is outside the observed forest section");
        return this.blocks[index(position)];
    }

    boolean canChange(BlockPos position, BlockState state) {
        return !this.conflict && contains(position) && !state.hasBlockEntity() && !state(position).hasBlockEntity();
    }

    void change(Map<BlockPos, BlockState> changes) {
        for (var entry : changes.entrySet()) {
            if (!canChange(entry.getKey(), entry.getValue())) {
                throw new IllegalArgumentException("Forest work requires observed blocks without a conflict or block entity");
            }
        }
        boolean changed = false;
        for (var entry : changes.entrySet()) {
            int index = index(entry.getKey());
            if (this.blocks[index] != entry.getValue()) {
                observeGrowth(entry.getKey(), entry.getValue(), this.blocks[index]);
                this.blocks[index] = entry.getValue();
                changed = true;
            }
        }
        if (changed) {
            this.version++;
            this.pending = true;
        }
    }

    void schedule(SettlementForestTick tick) {
        if (!contains(tick.position()) || this.conflict) throw new IllegalArgumentException("Forest tick exceeds its observed section");
        if (!this.ticks.contains(tick)) {
            this.ticks.removeIf(previous -> previous.fluid() == tick.fluid() && previous.position().equals(tick.position()));
            this.ticks.add(tick);
            this.version++;
            this.pending = true;
        }
    }

    boolean reconcile(LevelChunk chunk) {
        if (chunk.getPos().x != this.min.getX() >> 4 || chunk.getPos().z != this.min.getZ() >> 4) return false;
        long applied = SettlementChunkReceipts.version(chunk, receiptKey(this.area, this.min), this.id);
        if (applied < 0L) {
            this.conflict = this.pending;
            return false;
        }
        if (this.pending && applied < this.version) {
            for (BlockPos position : BlockPos.betweenClosed(this.min, this.max)) {
                int index = index(position);
                if (this.blocks[index] != this.baseline[index] && chunk.getBlockState(position) != this.baseline[index]) {
                    this.conflict = true;
                    return false;
                }
            }
            for (BlockPos position : BlockPos.betweenClosed(this.min, this.max)) {
                int index = index(position);
                if (this.blocks[index] != this.baseline[index]) {
                    chunk.setBlockState(position, this.blocks[index], false);
                }
            }
            this.ticks.forEach(tick -> tick.apply(chunk));
            SettlementChunkReceipts.applied(chunk, receiptKey(this.area, this.min), this.id, this.version);
        }
        if (applied > this.version) {
            this.growth.clear();
            pause(chunk.getLevel().getGameTime());
        }
        this.version = Math.max(this.version, applied);
        this.pending = false;
        this.conflict = false;
        this.ticks.clear();
        refresh(chunk);
        return true;
    }

    private void refresh(LevelChunk chunk) {
        for (BlockPos position : BlockPos.betweenClosed(this.min, this.max)) {
            int index = index(position);
            var updated = chunk.getBlockState(position);
            observeGrowth(position, updated, this.blocks[index]);
            this.blocks[index] = updated;
            this.baseline[index] = updated;
        }
        observeEnvironment(chunk);
    }

    int size() {
        return this.blocks.length;
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
        int elapsed = (int) Math.clamp(now - this.checkedAt, 0L, 200L);
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
        this.lightSources = SettlementForestLight.observe(chunk, this.min, this.max);
        this.environmentKnown = true;
    }

    int index(BlockPos position) {
        return (position.getY() - this.min.getY()) * (this.max.getZ() - this.min.getZ() + 1) * (this.max.getX() - this.min.getX() + 1)
                + (position.getZ() - this.min.getZ()) * (this.max.getX() - this.min.getX() + 1) + position.getX() - this.min.getX();
    }

    static BlockPos sectionOrigin(BlockPos position) {
        return new BlockPos(position.getX() & ~15, position.getY() & ~15, position.getZ() & ~15);
    }

    private static String receiptKey(UUID area, BlockPos min) {
        return "Forest:" + area + ":" + min.asLong();
    }

    private static int volume(BlockPos min, BlockPos max) {
        return (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
    }

    private static void validateBounds(BlockPos min, BlockPos max) {
        if (min.getX() > max.getX() || min.getY() > max.getY() || min.getZ() > max.getZ()
                || !sectionOrigin(min).equals(sectionOrigin(max))) {
            throw new IllegalArgumentException("Forest observations must fit within one chunk section");
        }
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Id", this.id);
        tag.putLong("Min", this.min.asLong());
        tag.putLong("Max", this.max.asLong());
        tag.putLong("Version", this.version);
        tag.putBoolean("Pending", this.pending);
        tag.putBoolean("Conflict", this.conflict);
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
        if (!this.ticks.isEmpty()) {
            var ticks = new ListTag();
            this.ticks.forEach(tick -> ticks.add(tick.save()));
            tag.put("Ticks", ticks);
        }
        var palette = new ArrayList<BlockState>();
        var indexes = new HashMap<BlockState, Integer>();
        int[] baseline = new int[this.blocks.length];
        int[] blocks = new int[this.blocks.length];
        for (int index = 0; index < this.blocks.length; index++) {
            baseline[index] = paletteIndex(this.baseline[index], palette, indexes);
            blocks[index] = paletteIndex(this.blocks[index], palette, indexes);
        }
        var states = new ListTag();
        palette.forEach(state -> states.add(NbtUtils.writeBlockState(state)));
        tag.put("Palette", states);
        tag.putIntArray("Baseline", baseline);
        if (!Arrays.equals(baseline, blocks)) tag.putIntArray("Blocks", blocks);
        return tag;
    }

    private static int paletteIndex(BlockState state, ArrayList<BlockState> palette, Map<BlockState, Integer> indexes) {
        return indexes.computeIfAbsent(state, value -> {
            palette.add(value);
            return palette.size() - 1;
        });
    }

    static SettlementForestSection load(UUID area, CompoundTag tag) {
        if (!tag.hasUUID("Id") || !tag.contains("Min", Tag.TAG_LONG) || !tag.contains("Max", Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Forest section requires its identity and bounds");
        }
        BlockPos min = BlockPos.of(tag.getLong("Min"));
        BlockPos max = BlockPos.of(tag.getLong("Max"));
        validateBounds(min, max);
        int[] baseline = tag.getIntArray("Baseline");
        int[] blocks = tag.contains("Blocks", Tag.TAG_INT_ARRAY) ? tag.getIntArray("Blocks") : baseline;
        var palette = new ArrayList<BlockState>();
        for (Tag raw : tag.getList("Palette", Tag.TAG_COMPOUND)) {
            palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), (CompoundTag) raw));
        }
        int size = volume(min, max);
        if (baseline.length != size || blocks.length != size || palette.isEmpty() || palette.size() > size * 2) {
            throw new IllegalArgumentException("Forest section palette does not match its observed bounds");
        }
        var section = new SettlementForestSection(area, tag.getUUID("Id"), min, max, new BlockState[size], new BlockState[size]);
        for (int index = 0; index < size; index++) {
            if (baseline[index] < 0 || baseline[index] >= palette.size() || blocks[index] < 0 || blocks[index] >= palette.size()) {
                throw new IllegalArgumentException("Forest section contains an unknown palette entry");
            }
            section.baseline[index] = palette.get(baseline[index]);
            section.blocks[index] = palette.get(blocks[index]);
        }
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
        section.version = Math.max(0L, tag.getLong("Version"));
        section.pending = tag.getBoolean("Pending");
        section.conflict = tag.getBoolean("Conflict");
        for (Tag raw : tag.getList("Ticks", Tag.TAG_COMPOUND)) {
            var tick = SettlementForestTick.load((CompoundTag) raw);
            if (!section.contains(tick.position()) || section.ticks.stream().anyMatch(previous -> previous.fluid() == tick.fluid()
                    && previous.position().equals(tick.position())) || section.ticks.size() >= size * 2) {
                throw new IllegalArgumentException("Forest ticks must be unique and fit their observed section");
            }
            section.ticks.add(tick);
        }
        if (!section.pending && (!section.ticks.isEmpty() || !Arrays.equals(section.baseline, section.blocks))) {
            throw new IllegalArgumentException("Changed forest blocks require a pending revision");
        }
        return section;
    }
}
