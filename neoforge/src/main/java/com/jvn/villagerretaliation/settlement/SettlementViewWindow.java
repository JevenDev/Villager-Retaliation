package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

public record SettlementViewWindow(BlockPos center, int radius, double x, double z, float height, List<ChunkPos> chunks) {
    public static final int BORDER = 16;
    public static final int MAX_CHUNKS = 121;
    public static final float MIN_HEIGHT = 16.0F;
    public static final float MAX_HEIGHT = 64.0F;

    public SettlementViewWindow {
        center = center.immutable();
        chunks = List.copyOf(chunks);
        if (radius < 1 || radius > SettlementStageRules.MAX_RADIUS + BORDER || chunks.size() > MAX_CHUNKS
                || !Double.isFinite(x) || !Double.isFinite(z) || !Float.isFinite(height)
                || height < MIN_HEIGHT || height > MAX_HEIGHT
                || Math.abs(x - center.getX()) > radius || Math.abs(z - center.getZ()) > radius
                || chunks.isEmpty() || new java.util.HashSet<>(chunks).size() != chunks.size()) {
            throw new IllegalArgumentException("Invalid settlement view window");
        }
        for (ChunkPos chunk : chunks) {
            if (chunk.x < Math.floorDiv(center.getX() - radius, 16) || chunk.x > Math.floorDiv(center.getX() + radius, 16)
                    || chunk.z < Math.floorDiv(center.getZ() - radius, 16) || chunk.z > Math.floorDiv(center.getZ() + radius, 16)) {
                throw new IllegalArgumentException("Settlement view chunk exceeds its boundary");
            }
        }
    }

    public static SettlementViewWindow around(SettlementRecord town, double x, double z, float height) {
        if (!Double.isFinite(x) || !Double.isFinite(z) || !Float.isFinite(height)) {
            throw new IllegalArgumentException("Settlement camera coordinates must be finite");
        }
        int radius = town.radius() + BORDER;
        BlockPos center = town.center();
        x = Math.clamp(x, center.getX() - radius, center.getX() + radius);
        z = Math.clamp(z, center.getZ() - radius, center.getZ() + radius);
        height = Math.clamp(height, MIN_HEIGHT, MAX_HEIGHT);
        int chunkX = Math.floorDiv((int) Math.floor(x), 16);
        int chunkZ = Math.floorDiv((int) Math.floor(z), 16);
        int distance = Math.clamp((int) Math.ceil(height / 16.0F) + 1, 2, 5);
        List<ChunkPos> chunks = new ArrayList<>();
        for (int cx = Math.max(chunkX - distance, Math.floorDiv(center.getX() - radius, 16));
             cx <= Math.min(chunkX + distance, Math.floorDiv(center.getX() + radius, 16)); cx++) {
            for (int cz = Math.max(chunkZ - distance, Math.floorDiv(center.getZ() - radius, 16));
                 cz <= Math.min(chunkZ + distance, Math.floorDiv(center.getZ() + radius, 16)); cz++) {
                chunks.add(new ChunkPos(cx, cz));
            }
        }
        chunks.sort(Comparator.comparingLong((ChunkPos pos) -> (long) (pos.x - chunkX) * (pos.x - chunkX)
                        + (long) (pos.z - chunkZ) * (pos.z - chunkZ)).thenComparingLong(ChunkPos::toLong));
        return new SettlementViewWindow(center, radius, x, z, height, chunks);
    }

    public boolean contains(BlockPos position) {
        return Math.abs((long) position.getX() - this.center.getX()) <= this.radius
                && Math.abs((long) position.getZ() - this.center.getZ()) <= this.radius
                && this.chunks.contains(new ChunkPos(position));
    }
}
