package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.HiredMoveToBlockFaceJob;
import com.jvn.villagerretaliation.interaction.work.mining.MiningSafety;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

final class SettlementMiningRoutes {
    private static final int MAX_POSITIONS = 4096;

    private SettlementMiningRoutes() {
    }

    static Map<BlockPos, Integer> reachable(SettlementMine mine, BlockPos origin) {
        var distances = new LinkedHashMap<BlockPos, Integer>();
        if (!safe(mine, origin)) return distances;
        var queue = new ArrayDeque<BlockPos>();
        distances.put(origin.immutable(), 0);
        queue.add(origin.immutable());
        while (!queue.isEmpty() && distances.size() < MAX_POSITIONS) {
            var current = queue.removeFirst();
            for (var direction : Direction.Plane.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    var next = current.relative(direction).offset(0, dy, 0);
                    if (dy > 0 && !clear(mine, current.above(2)) || dy < 0 && !clear(mine, next.above(2))) continue;
                    visit(mine, distances, queue, current, next);
                }
            }
            for (int dy : new int[] {-1, 1}) {
                var next = current.offset(0, dy, 0);
                if (mine.getBlockState(current).is(Blocks.LADDER) || mine.getBlockState(next).is(Blocks.LADDER)) {
                    visit(mine, distances, queue, current, next);
                }
            }
        }
        return distances;
    }

    private static void visit(SettlementMine mine, Map<BlockPos, Integer> distances, ArrayDeque<BlockPos> queue,
                              BlockPos previous, BlockPos next) {
        if (distances.size() < MAX_POSITIONS && !distances.containsKey(next)
                && safe(mine, next)) {
            distances.put(next, distances.get(previous) + 1);
            queue.addLast(next);
        }
    }

    private static boolean safe(SettlementMine mine, BlockPos position) {
        return MiningSafety.isSafePathPosition(mine, mine::observed, position)
                && !mine.getBlockState(position.below()).hasBlockEntity();
    }

    private static boolean clear(SettlementMine mine, BlockPos position) {
        if (!mine.observed(position)) return false;
        var state = mine.getBlockState(position);
        return state.getFluidState().isEmpty() && !MiningSafety.isDamagingBlock(state)
                && (state.is(Blocks.LADDER) || state.getCollisionShape(mine, position).isEmpty());
    }

    static Target target(SettlementMine mine, Villager owner, BlockPos actualPosition,
                         BlockPos target, Map<BlockPos, Integer> distances) {
        Target best = null;
        for (BlockPos raw : BlockPos.betweenClosed(target.offset(-2, -2, -2), target.offset(2, 2, 2))) {
            Integer steps = distances.get(raw);
            if (steps == null || raw.distSqr(target) > 4.0D || best != null && steps >= best.steps()
                    || mine.getBlockState(raw.below()).hasBlockEntity()
                    || MiningSafety.isUnsafeMiningTarget(mine, mine::observed, target, raw, actualPosition)) continue;
            owner.setPos(raw.getBottomCenter());
            Vec3 eye = owner.getEyePosition();
            Vec3 hit = HiredMoveToBlockFaceJob.visibleHitPosition(mine, mine::observed, owner, eye, target, ignored -> false);
            if (hit != null && eye.distanceToSqr(hit) <= HiredMoveToBlockFaceJob.MAX_REACH_SQR) {
                best = new Target(target.immutable(), raw.immutable(), hit, steps);
            }
        }
        return best;
    }

    record Target(BlockPos position, BlockPos stance, Vec3 hit, int steps) {
    }
}
