package com.jvn.villagerretaliation.interaction.work;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

final class MiningVeinPlan {
    private MiningVeinPlan() {
    }

    static List<BlockPos> best(ServerLevel level, BlockPos origin, List<BlockPos> candidates, int maxTargets) {
        Set<Long> remaining = new LinkedHashSet<>();
        for (BlockPos candidate : candidates) {
            remaining.add(candidate.asLong());
        }

        List<BlockPos> bestVein = List.of();
        double bestScore = Double.NEGATIVE_INFINITY;
        while (!remaining.isEmpty()) {
            BlockPos seed = BlockPos.of(remaining.iterator().next());
            BlockState seedState = level.getBlockState(seed);
            List<BlockPos> vein = new ArrayList<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(seed);
            remaining.remove(seed.asLong());

            while (!queue.isEmpty() && vein.size() < maxTargets) {
                BlockPos current = queue.removeFirst();
                vein.add(current);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) {
                                continue;
                            }
                            BlockPos next = current.offset(dx, dy, dz).immutable();
                            if (!remaining.contains(next.asLong())) {
                                continue;
                            }
                            if (level.getBlockState(next).getBlock() != seedState.getBlock()) {
                                continue;
                            }
                            remaining.remove(next.asLong());
                            queue.addLast(next);
                        }
                    }
                }
            }

            List<BlockPos> ordered = HiredWorkPlan.routeOrder(origin, vein, maxTargets);
            double score = ordered.size() * 1000.0D - origin.distSqr(seed);
            if (!ordered.isEmpty() && score > bestScore) {
                bestVein = ordered;
                bestScore = score;
            }
        }
        return bestVein;
    }
}
