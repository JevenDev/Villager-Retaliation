package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.mining.MiningSafety;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

final class SettlementConstructionRoutes {
    private SettlementConstructionRoutes() {
    }

    static Route route(SettlementConstructionSite site, SettlementProject project, com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner.StructurePlan plan,
                       BlockPos start, List<BlockPos> targets,
                       net.minecraft.world.entity.npc.Villager owner, boolean clearing) {
        if (!safe(site, start) || targets.isEmpty()) return null;
        var clearingTargets = clearing ? new HashSet<>(targets) : Set.<BlockPos>of();
        var actualStance = owner.blockPosition();
        var previous = new HashMap<BlockPos, BlockPos>();
        var queue = new ArrayDeque<BlockPos>();
        previous.put(start, null);
        queue.add(start);
        while (!queue.isEmpty() && previous.size() <= 4096) {
            var current = queue.removeFirst();
            owner.setPos(current.getBottomCenter());
            boolean outside = current.getX() < project.min().getX() || current.getX() > project.max().getX()
                    || current.getZ() < project.min().getZ() || current.getZ() > project.max().getZ();
            BlockPos target = null;
            if (clearing) {
                target = clearingTarget(site, clearingTargets, current, actualStance, owner);
            } else if (outside && com.jvn.villagerretaliation.interaction.work.builder.BuilderWorker
                    .canBuildFromCurrentPosition(owner, targets.getFirst(), plan, project.origin())) {
                target = targets.getFirst();
            }
            if (target != null) {
                var path = new ArrayList<BlockPos>();
                for (var step = current; step != null; step = previous.get(step)) path.add(step);
                java.util.Collections.reverse(path);
                return new Route(target, path);
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
        return null;
    }

    private static BlockPos clearingTarget(SettlementConstructionSite site, Set<BlockPos> targets, BlockPos stance,
                                          BlockPos actualStance, net.minecraft.world.entity.npc.Villager owner) {
        for (var candidate : BlockPos.betweenClosed(stance.offset(-2, -2, -2), stance.offset(2, 2, 2))) {
            if (!targets.contains(candidate) || stance.distSqr(candidate) > 4.0D
                    || MiningSafety.isUnsafeMiningTarget(site, site::observed, candidate, stance, actualStance)) continue;
            var hit = com.jvn.villagerretaliation.interaction.work.HiredMoveToBlockFaceJob.visibleHitPosition(
                    site, site::observed, owner, owner.getEyePosition(), candidate, ignored -> false);
            if (hit != null && owner.getEyePosition().distanceToSqr(hit) <= com.jvn.villagerretaliation.interaction.work.HiredMoveToBlockFaceJob.MAX_REACH_SQR
                    && stance.getCenter().distanceToSqr(hit) <= 14.0625D) return candidate.immutable();
        }
        return null;
    }

    record Route(BlockPos target, List<BlockPos> steps) {
    }

    static boolean safe(SettlementConstructionSite site, BlockPos pos) {
        return site.area().contains(pos) && MiningSafety.isSafePathPosition(site, site::observed, pos);
    }
}
