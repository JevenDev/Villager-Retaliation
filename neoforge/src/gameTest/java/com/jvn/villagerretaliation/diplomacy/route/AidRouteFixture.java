package com.jvn.villagerretaliation.diplomacy.route;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public final class AidRouteFixture {
    private AidRouteFixture() { }
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
