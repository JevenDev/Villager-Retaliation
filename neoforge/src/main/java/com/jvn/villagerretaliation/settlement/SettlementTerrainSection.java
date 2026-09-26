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

final class SettlementTerrainSection {
    private final String receiptKey;
    private final UUID id;
    private final BlockPos min;
    private final BlockPos max;
    private final BlockState[] baseline;
    private final BlockState[] blocks;
    private final java.util.List<SettlementTerrainTick> ticks = new ArrayList<>();
    private long version;
    private long observationVersion;
    private boolean pending;
    private boolean conflict;

    private SettlementTerrainSection(String namespace, UUID area, UUID id, BlockPos min, BlockPos max, BlockState[] baseline, BlockState[] blocks) {
        this.receiptKey = receiptKey(namespace, area, min);
        this.id = id;
        this.min = min.immutable();
        this.max = max.immutable();
        this.baseline = baseline;
        this.blocks = blocks;
    }

    static SettlementTerrainSection capture(String namespace, UUID area, BlockPos min, BlockPos max, LevelChunk chunk) {
        validateBounds(min, max);
        if (min.getX() >> 4 != chunk.getPos().x || min.getZ() >> 4 != chunk.getPos().z) {
            throw new IllegalArgumentException("Terrain section requires its observed chunk");
        }
        String key = receiptKey(namespace, area, min);
        UUID id = SettlementChunkReceipts.identity(chunk, key);
        int size = volume(min, max);
        var section = new SettlementTerrainSection(namespace, area, id, min, max, new BlockState[size], new BlockState[size]);
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
        if (!contains(position)) throw new IllegalArgumentException("Position is outside the observed terrain section");
        return this.blocks[index(position)];
    }

    boolean canChange(BlockPos position, BlockState state) {
        return !this.conflict && contains(position) && !state.hasBlockEntity() && !state(position).hasBlockEntity();
    }

    void change(Map<BlockPos, BlockState> changes) {
        for (var entry : changes.entrySet()) {
            if (!canChange(entry.getKey(), entry.getValue())) {
                throw new IllegalArgumentException("Terrain work requires observed blocks without a conflict or block entity");
            }
        }
        boolean changed = false;
        for (var entry : changes.entrySet()) {
            int index = index(entry.getKey());
            if (this.blocks[index] != entry.getValue()) {
                this.blocks[index] = entry.getValue();
                changed = true;
            }
        }
        if (changed) {
            this.version++;
            this.observationVersion++;
            this.pending = true;
        }
    }

    void schedule(SettlementTerrainTick tick) {
        if (!contains(tick.position()) || this.conflict) throw new IllegalArgumentException("Terrain tick exceeds its observed section");
        if (!this.ticks.contains(tick)) {
            this.ticks.removeIf(previous -> previous.fluid() == tick.fluid() && previous.position().equals(tick.position()));
            this.ticks.add(tick);
            this.version++;
            this.pending = true;
        }
    }

    boolean reconcile(LevelChunk chunk) {
        if (chunk.getPos().x != this.min.getX() >> 4 || chunk.getPos().z != this.min.getZ() >> 4) return false;
        long applied = SettlementChunkReceipts.version(chunk, this.receiptKey, this.id);
        if (applied < 0L) {
            conflict(this.pending);
            return false;
        }
        if (this.pending && applied < this.version) {
            for (BlockPos position : BlockPos.betweenClosed(this.min, this.max)) {
                int index = index(position);
                if (this.blocks[index] != this.baseline[index] && chunk.getBlockState(position) != this.baseline[index]) {
                    conflict(true);
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
            SettlementChunkReceipts.applied(chunk, this.receiptKey, this.id, this.version);
        }
        this.version = Math.max(this.version, applied);
        this.pending = false;
        conflict(false);
        this.ticks.clear();
        refresh(chunk);
        return true;
    }

    private void refresh(LevelChunk chunk) {
        boolean changed = false;
        for (BlockPos position : BlockPos.betweenClosed(this.min, this.max)) {
            int index = index(position);
            var updated = chunk.getBlockState(position);
            changed |= this.blocks[index] != updated;
            this.blocks[index] = updated;
            this.baseline[index] = updated;
        }
        if (changed) this.observationVersion++;
    }

    private void conflict(boolean conflict) {
        if (this.conflict != conflict) this.observationVersion++;
        this.conflict = conflict;
    }

    long observationVersion() {
        return this.observationVersion;
    }

    int size() {
        return this.blocks.length;
    }

    long version() {
        return this.version;
    }

    int index(BlockPos position) {
        return (position.getY() - this.min.getY()) * (this.max.getZ() - this.min.getZ() + 1) * (this.max.getX() - this.min.getX() + 1)
                + (position.getZ() - this.min.getZ()) * (this.max.getX() - this.min.getX() + 1) + position.getX() - this.min.getX();
    }

    static BlockPos sectionOrigin(BlockPos position) {
        return new BlockPos(position.getX() & ~15, position.getY() & ~15, position.getZ() & ~15);
    }

    private static String receiptKey(String namespace, UUID area, BlockPos min) {
        return namespace + ":" + area + ":" + min.asLong();
    }

    private static int volume(BlockPos min, BlockPos max) {
        return (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
    }

    private static void validateBounds(BlockPos min, BlockPos max) {
        if (min.getX() > max.getX() || min.getY() > max.getY() || min.getZ() > max.getZ()
                || !sectionOrigin(min).equals(sectionOrigin(max))) {
            throw new IllegalArgumentException("Terrain observations must fit within one chunk section");
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

    static SettlementTerrainSection load(String namespace, UUID area, CompoundTag tag) {
        if (!tag.hasUUID("Id") || !tag.contains("Min", Tag.TAG_LONG) || !tag.contains("Max", Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Terrain section requires its identity and bounds");
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
            throw new IllegalArgumentException("Terrain section palette does not match its observed bounds");
        }
        var section = new SettlementTerrainSection(namespace, area, tag.getUUID("Id"), min, max, new BlockState[size], new BlockState[size]);
        for (int index = 0; index < size; index++) {
            if (baseline[index] < 0 || baseline[index] >= palette.size() || blocks[index] < 0 || blocks[index] >= palette.size()) {
                throw new IllegalArgumentException("Terrain section contains an unknown palette entry");
            }
            section.baseline[index] = palette.get(baseline[index]);
            section.blocks[index] = palette.get(blocks[index]);
        }
        section.version = Math.max(0L, tag.getLong("Version"));
        section.pending = tag.getBoolean("Pending");
        section.conflict = tag.getBoolean("Conflict");
        for (Tag raw : tag.getList("Ticks", Tag.TAG_COMPOUND)) {
            var tick = SettlementTerrainTick.load((CompoundTag) raw);
            if (!section.contains(tick.position()) || section.ticks.stream().anyMatch(previous -> previous.fluid() == tick.fluid()
                    && previous.position().equals(tick.position())) || section.ticks.size() >= size * 2) {
                throw new IllegalArgumentException("Terrain ticks must be unique and fit their observed section");
            }
            section.ticks.add(tick);
        }
        if (!section.pending && (!section.ticks.isEmpty() || !Arrays.equals(section.baseline, section.blocks))) {
            throw new IllegalArgumentException("Changed terrain blocks require a pending revision");
        }
        return section;
    }
}
