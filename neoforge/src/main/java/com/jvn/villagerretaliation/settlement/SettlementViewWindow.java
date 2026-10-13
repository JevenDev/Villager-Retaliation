package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

public record SettlementViewWindow(BlockPos center, int radius, double x, double z, float height, List<ChunkPos> chunks) {
    public static final int BORDER = SettlementExpeditions.RANGE;
    public static final int MAX_CHUNKS = 121;
    public static final float MIN_HEIGHT = 8.0F;
    public static final float MAX_HEIGHT = 64.0F;

    public SettlementViewWindow {
        center = center.immutable();
        chunks = List.copyOf(chunks);
        if (radius < 1 || radius > 60_000_000 + BORDER || chunks.size() > MAX_CHUNKS
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
        return around(town, x, z, height, chunk -> true, -29_999_984, 29_999_984, -29_999_984, 29_999_984);
    }

    static SettlementViewWindow around(net.minecraft.server.level.ServerLevel level, SettlementRecord town,
                                       double x, double z, float height) {
        var border = level.getWorldBorder();
        var data = SettlementSavedData.get(level);
        return around(town, x, z, height, chunk -> intersects(border, chunk)
                        && data.owner(town.dimension(), chunk.x, chunk.z).map(town.id()::equals).orElse(true),
                border.getMinX(), border.getMaxX(), border.getMinZ(), border.getMaxZ());
    }

    private static SettlementViewWindow around(SettlementRecord town, double x, double z, float height,
                                               java.util.function.Predicate<ChunkPos> allowed,
                                               double minX, double maxX, double minZ, double maxZ) {
        if (!Double.isFinite(x) || !Double.isFinite(z) || !Float.isFinite(height)) {
            throw new IllegalArgumentException("Settlement camera coordinates must be finite");
        }
        int radius = town.territory().envelopeRadius(town.center()) + BORDER;
        BlockPos center = town.center();
        x = Math.clamp(x, Math.max(minX, center.getX() - radius), Math.min(maxX - 0.001D, center.getX() + radius));
        z = Math.clamp(z, Math.max(minZ, center.getZ() - radius), Math.min(maxZ - 0.001D, center.getZ() + radius));
        height = Math.clamp(height, MIN_HEIGHT, MAX_HEIGHT);
        var target = new ChunkPos(BlockPos.containing(x, center.getY(), z));
        if (!nearTerritory(town, target) || !allowed.test(target)) {
            double nearest = Double.POSITIVE_INFINITY;
            double selectedX = x;
            double selectedZ = z;
            int borderChunks = BORDER / 16;
            for (long plot : town.territory().plots()) {
                var owned = new ChunkPos(plot);
                for (int dx = -borderChunks; dx <= borderChunks; dx++) {
                    for (int dz = -borderChunks; dz <= borderChunks; dz++) {
                        var candidate = new ChunkPos(owned.x + dx, owned.z + dz);
                        if (!SettlementTerritory.validChunk(candidate.x, candidate.z) || !allowed.test(candidate)) continue;
                        double cx = Math.clamp(x, Math.max(minX, candidate.getMinBlockX()),
                                Math.min(maxX - 0.001D, candidate.getMaxBlockX() + 0.999D));
                        double cz = Math.clamp(z, Math.max(minZ, candidate.getMinBlockZ()),
                                Math.min(maxZ - 0.001D, candidate.getMaxBlockZ() + 0.999D));
                        double distance = (cx - x) * (cx - x) + (cz - z) * (cz - z);
                        if (distance < nearest) {
                            nearest = distance;
                            selectedX = cx;
                            selectedZ = cz;
                        }
                    }
                }
            }
            if (!Double.isFinite(nearest)) throw new IllegalArgumentException("Settlement has no viewable territory");
            x = selectedX;
            z = selectedZ;
        }
        int chunkX = Math.floorDiv((int) Math.floor(x), 16);
        int chunkZ = Math.floorDiv((int) Math.floor(z), 16);
        int distance = Math.clamp((int) Math.ceil(height / 16.0F) + 1, 2, 5);
        List<ChunkPos> chunks = new ArrayList<>();
        for (int cx = chunkX - distance; cx <= chunkX + distance; cx++) {
            for (int cz = chunkZ - distance; cz <= chunkZ + distance; cz++) {
                var chunk = new ChunkPos(cx, cz);
                if (nearTerritory(town, chunk) && allowed.test(chunk)) chunks.add(chunk);
            }
        }
        chunks.sort(Comparator.comparingLong((ChunkPos pos) -> (long) (pos.x - chunkX) * (pos.x - chunkX)
                        + (long) (pos.z - chunkZ) * (pos.z - chunkZ)).thenComparingLong(ChunkPos::toLong));
        return new SettlementViewWindow(center, radius, x, z, height, chunks);
    }

    static boolean intersects(net.minecraft.world.level.border.WorldBorder border, ChunkPos chunk) {
        return chunk.getMaxBlockX() + 1.0D > border.getMinX() && chunk.getMinBlockX() < border.getMaxX()
                && chunk.getMaxBlockZ() + 1.0D > border.getMinZ() && chunk.getMinBlockZ() < border.getMaxZ();
    }

    static boolean nearTerritory(SettlementRecord town, ChunkPos chunk) {
        if (!SettlementTerritory.validChunk(chunk.x, chunk.z)) return false;
        return SettlementExpeditions.near(town, chunk.x, chunk.z);
    }

    public net.minecraft.world.phys.Vec3 focus(double x, double z) {
        double best = Double.POSITIVE_INFINITY;
        double chosenX = this.x;
        double chosenZ = this.z;
        for (var chunk : this.chunks) {
            double cx = Math.clamp(x, Math.max(chunk.getMinBlockX(), this.center.getX() - this.radius),
                    Math.min(chunk.getMaxBlockX() + 0.999D, this.center.getX() + this.radius));
            double cz = Math.clamp(z, Math.max(chunk.getMinBlockZ(), this.center.getZ() - this.radius),
                    Math.min(chunk.getMaxBlockZ() + 0.999D, this.center.getZ() + this.radius));
            double distance = (cx - x) * (cx - x) + (cz - z) * (cz - z);
            if (distance < best) {
                best = distance;
                chosenX = cx;
                chosenZ = cz;
            }
        }
        return new net.minecraft.world.phys.Vec3(chosenX, 0, chosenZ);
    }

    public boolean contains(BlockPos position) {
        return Math.abs((long) position.getX() - this.center.getX()) <= this.radius
                && Math.abs((long) position.getZ() - this.center.getZ()) <= this.radius
                && this.chunks.contains(new ChunkPos(position));
    }
}
