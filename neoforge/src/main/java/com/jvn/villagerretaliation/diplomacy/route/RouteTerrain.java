package com.jvn.villagerretaliation.diplomacy.route;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;

public final class RouteTerrain {
    public static final byte BLOCKED = 0, WALK = 1, WATER = 2;
    private final ChunkPos chunk;
    private final int[] heights;
    private final byte[] kinds;
    private final long observed;

    public RouteTerrain(ChunkPos chunk, int[] heights, byte[] kinds, long observed) {
        if (heights.length != 256 || kinds.length != 256 || observed < 0) throw new IllegalArgumentException("Invalid route terrain");
        for (int index = 0; index < 256; index++) {
            if (heights[index] < -2048 || heights[index] > 2048 || kinds[index] < BLOCKED || kinds[index] > WATER)
                throw new IllegalArgumentException("Invalid route column");
        }
        this.chunk = chunk;
        this.heights = heights.clone();
        this.kinds = kinds.clone();
        this.observed = observed;
    }

    public ChunkPos chunk() { return this.chunk; }
    public long observed() { return this.observed; }
    public byte kind(int x, int z) { return this.kinds[index(x, z)]; }
    public int height(int x, int z) { return this.heights[index(x, z)]; }
    public BlockPos position(int x, int z) { return new BlockPos(x, height(x, z), z); }
    public boolean passable() { for (byte kind : this.kinds) if (kind != BLOCKED) return true; return false; }
    public boolean sameSurface(RouteTerrain other) {
        return this.chunk.equals(other.chunk) && Arrays.equals(this.heights, other.heights) && Arrays.equals(this.kinds, other.kinds);
    }
    private int index(int x, int z) {
        if (x >> 4 != this.chunk.x || z >> 4 != this.chunk.z) throw new IllegalArgumentException("Column outside route tile");
        return (z & 15) * 16 + (x & 15);
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putLong("Chunk", this.chunk.toLong());
        tag.putIntArray("Heights", this.heights);
        tag.putByteArray("Kinds", this.kinds);
        tag.putLong("Observed", this.observed);
        return tag;
    }

    static RouteTerrain load(CompoundTag tag) {
        if (!tag.contains("Chunk", Tag.TAG_LONG) || !tag.contains("Heights", Tag.TAG_INT_ARRAY)
                || !tag.contains("Kinds", Tag.TAG_BYTE_ARRAY) || !tag.contains("Observed", Tag.TAG_LONG))
            throw new IllegalArgumentException("Missing route terrain fields");
        return new RouteTerrain(new ChunkPos(tag.getLong("Chunk")), tag.getIntArray("Heights"), tag.getByteArray("Kinds"), tag.getLong("Observed"));
    }
}
