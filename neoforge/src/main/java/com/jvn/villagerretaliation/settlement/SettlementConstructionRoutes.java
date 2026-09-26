package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.mining.MiningSafety;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

final class SettlementConstructionRoutes {
    private SettlementConstructionRoutes() {
    }

    static List<BlockPos> route(SettlementConstructionSite site, SettlementProject project, com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner.StructurePlan plan,
                               BlockPos start, BlockPos target,
                               net.minecraft.world.entity.npc.Villager owner, boolean clearing) {
        if (!safe(site, start)) return List.of();
        var previous = new HashMap<BlockPos, BlockPos>();
        var queue = new ArrayDeque<BlockPos>();
        previous.put(start, null);
        queue.add(start);
        while (!queue.isEmpty() && previous.size() <= 4096) {
            var current = queue.removeFirst();
            owner.setPos(current.getBottomCenter());
            boolean outside = current.getX() < project.min().getX() || current.getX() > project.max().getX()
                    || current.getZ() < project.min().getZ() || current.getZ() > project.max().getZ();
            boolean reachable = outside && com.jvn.villagerretaliation.interaction.work.builder.BuilderWorker
                    .canBuildFromCurrentPosition(owner, target, plan, project.origin());
            if (clearing) {
                reachable = false;
                if (current.distSqr(target) <= 4.0D) {
                    var hit = com.jvn.villagerretaliation.interaction.work.HiredMoveToBlockFaceJob.visibleHitPosition(
                            site, site::observed, owner, owner.getEyePosition(), target, ignored -> false);
                    reachable = hit != null && owner.getEyePosition().distanceToSqr(hit) <= com.jvn.villagerretaliation.interaction.work.HiredMoveToBlockFaceJob.MAX_REACH_SQR
                            && current.getCenter().distanceToSqr(hit) <= 14.0625D;
                }
            }
            if (reachable) {
                var path = new ArrayList<BlockPos>();
                for (var step = current; step != null; step = previous.get(step)) path.add(step);
                java.util.Collections.reverse(path);
                return path;
            }
            for (var direction : Direction.Plane.HORIZONTAL) {
                for (int dy : new int[]{0, 1, -1}) {
                    var next = current.relative(direction).offset(0, dy, 0);
                    if (previous.containsKey(next) || !site.area().contains(next) || !safe(site, next)) continue;
                    if (dy > 0 && (!site.observed(current.above(2)) || !site.getBlockState(current.above(2)).getCollisionShape(site, current.above(2)).isEmpty())) continue;
                    if (dy < 0 && (!site.observed(next.above(2)) || !site.getBlockState(next.above(2)).getCollisionShape(site, next.above(2)).isEmpty())) continue;
                    previous.put(next, current);
                    queue.add(next);
                    break;
                }
            }
        }
        return List.of();
    }

    static boolean safe(SettlementConstructionSite site, BlockPos pos) {
        return site.area().contains(pos) && MiningSafety.isSafePathPosition(site, site::observed, pos);
    }
}
