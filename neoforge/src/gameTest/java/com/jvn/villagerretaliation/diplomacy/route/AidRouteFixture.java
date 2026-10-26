package com.jvn.villagerretaliation.diplomacy.route;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public final class AidRouteFixture {
    private AidRouteFixture() { }
    public static void waterCrossing(ServerLevel level, BlockPos from, BlockPos to) {
        for (int x = (from.getX() >> 4) - 2; x <= (to.getX() >> 4) + 2; x++) for (int z = (from.getZ() >> 4) - 2; z <= (from.getZ() >> 4) + 2; z++) {
            var heights = new int[256]; Arrays.fill(heights, from.getY()); var kinds = new byte[256];
            for (int column = 0; column < 256; column++) {
                int worldX = x * 16 + (column & 15);
                kinds[column] = worldX >= from.getX() + 8 && worldX <= to.getX() - 8 ? RouteTerrain.WATER : RouteTerrain.WALK;
            }
            RouteSavedData.get(level).observe(level.dimension().location(), new RouteTerrain(new ChunkPos(x, z), heights, kinds, level.getGameTime()));
        }
    }
    public static void flat(ServerLevel level, BlockPos from, BlockPos to) {
        for (int x = (Math.min(from.getX(), to.getX()) >> 4) - 2; x <= (Math.max(from.getX(), to.getX()) >> 4) + 2; x++) {
            for (int z = (Math.min(from.getZ(), to.getZ()) >> 4) - 2; z <= (Math.max(from.getZ(), to.getZ()) >> 4) + 2; z++) {
                var heights = new int[256]; Arrays.fill(heights, from.getY());
                var kinds = new byte[256]; Arrays.fill(kinds, RouteTerrain.WALK);
                RouteSavedData.get(level).observe(level.dimension().location(), new RouteTerrain(new ChunkPos(x, z), heights, kinds,
                        level.getServer().overworld().getGameTime()));
            }
        }
    }
}
