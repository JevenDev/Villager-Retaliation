package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredMiningMode;
import com.jvn.villagerretaliation.interaction.work.HiredMoveToBlockFaceJob;
import com.jvn.villagerretaliation.interaction.work.mining.MiningSafety;
import com.jvn.villagerretaliation.interaction.work.mining.MiningWorker;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

final class SettlementMiningRoutes {
    private static final Map<SettlementWorkerState, SettlementMiningRoutes> SEARCHES = new WeakHashMap<>();
    private static final Map<SettlementWorkerState, SettlementMiningRoutes> PLACEMENT_SEARCHES = new WeakHashMap<>();
    private final SettlementMine mine;
    private final BlockPos origin;
    private final BlockPos actualPosition;
    private final HiredMiningMode mode;
    private final boolean placing;
    private final long[] permanentBarriers;
    private final Map<BlockPos, Observation> observations = new HashMap<>();
    private final Map<BlockPos, Target> completed = new HashMap<>();
    private final Set<BlockPos> unreachable = new HashSet<>();
    private Search active;

    private SettlementMiningRoutes(SettlementMine mine, BlockPos origin, BlockPos actualPosition, HiredMiningMode mode, boolean placing, long[] barriers) {
        this.mine = mine;
        this.origin = origin.immutable();
        this.actualPosition = actualPosition.immutable();
        this.mode = mode;
        this.placing = placing;
        this.permanentBarriers = barriers.clone();
        mine.sections().forEach((position, section) -> this.observations.put(position, new Observation(section, section.observationVersion())));
    }

    static SettlementMiningRoutes forWorker(SettlementWorkerState worker, SettlementMine mine, BlockPos origin) {
        return forWorker(worker, mine, origin, false);
    }

    static SettlementMiningRoutes forWorker(SettlementWorkerState worker, SettlementMine mine, BlockPos origin, boolean placing) {
        var mode = HiredMiningMode.fromState(worker.settings());
        var barriers = worker.settings().getLongArray("MiningPermanentHazardBarriers");
        var searches = placing ? PLACEMENT_SEARCHES : SEARCHES;
        var routes = searches.get(worker);
        if (routes == null || !routes.matches(mine, origin, worker.position(), mode, placing, barriers)) {
            routes = new SettlementMiningRoutes(mine, origin, worker.position(), mode, placing, barriers);
            searches.put(worker, routes);
        }
        return routes;
    }

    private boolean matches(SettlementMine mine, BlockPos origin, BlockPos actualPosition, HiredMiningMode mode, boolean placing, long[] barriers) {
        if (this.mine != mine || this.mode != mode || this.placing != placing || !Arrays.equals(this.permanentBarriers, barriers)
                || !this.origin.equals(origin) || !this.actualPosition.equals(actualPosition)
                || this.observations.size() != mine.sections().size()) return false;
        for (var entry : mine.sections().entrySet()) {
            var previous = this.observations.get(entry.getKey());
            if (previous == null || previous.section() != entry.getValue()
                    || previous.version() != entry.getValue().observationVersion()) return false;
        }
        return true;
    }

    Target target(Villager owner, BlockPos position, Budget budget) {
        var completed = this.completed.get(position);
        if (completed != null || this.unreachable.contains(position)) return completed;
        if (this.active == null || !this.active.target.equals(position)) this.active = new Search(owner, position);
        var result = this.active.advance(budget);
        if (result != null) {
            this.completed.put(position.immutable(), result);
            this.active = null;
        } else if (this.active.frontier.isEmpty()) {
            this.unreachable.add(position.immutable());
            this.active = null;
        }
        return result;
    }

    boolean pending(BlockPos position) {
        return this.active != null && this.active.target.equals(position);
    }

    static boolean safe(SettlementMine mine, BlockPos position) {
        return MiningSafety.isSafePathPosition(mine, mine::observed, position)
                && !mine.getBlockState(position.below()).hasBlockEntity();
    }

    private static boolean clear(SettlementMine mine, BlockPos position) {
        if (!mine.observed(position)) return false;
        var state = mine.getBlockState(position);
        return state.getFluidState().isEmpty() && !MiningSafety.isDamagingBlock(state)
                && (state.is(Blocks.LADDER) || state.getCollisionShape(mine, position).isEmpty());
    }

    private boolean approachAllowed(BlockPos position) {
        if (!this.placing && !this.mode.excavatesHorizontally()) return true;
        var min = this.mine.min();
        var max = this.mine.max();
        if (this.placing && this.mine.contains(position)) return true;
        return position.getY() >= (this.placing ? max.getY() + 1 : min.getY())
                && position.getY() <= (this.placing ? max.getY() + 1 : max.getY())
                && position.getX() >= min.getX() - 1 && position.getX() <= max.getX() + 1
                && position.getZ() >= min.getZ() - 1 && position.getZ() <= max.getZ() + 1;
    }

    private boolean unsafeBarrierLanding(BlockPos target, BlockPos stance) {
        return this.mode.excavatesArea() && target.above().equals(stance)
                && Arrays.stream(this.permanentBarriers).anyMatch(position -> position == target.below().asLong());
    }

    private final class Search {
        private final BlockPos target;
        private final Map<BlockPos, Target> goals = new HashMap<>();
        private final Map<BlockPos, Integer> distances = new HashMap<>();
        private final Map<BlockPos, Boolean> safePositions = new HashMap<>();
        private final PriorityQueue<Node> frontier = new PriorityQueue<>(Comparator
                .comparingInt((Node node) -> node.steps() + node.estimate())
                .thenComparingInt(Node::estimate).thenComparingLong(Node::order));
        private long order;

        private Search(Villager owner, BlockPos target) {
            this.target = target.immutable();
            if (!safe(mine, origin)) return;
            if (placing) {
                owner.setPos(origin.getBottomCenter());
                Vec3 hit = target.getCenter();
                if (owner.getEyePosition().distanceToSqr(hit) <= 4.5D * 4.5D) {
                    this.goals.put(origin, new Target(this.target, origin, hit, List.of()));
                    visit(origin, 0, null);
                    return;
                }
            }
            double reach = placing ? 4.5D : mode.excavatesHorizontally() ? MiningWorker.HORIZONTAL_EXCAVATION_REACH : HiredMoveToBlockFaceJob.MAX_REACH;
            int radius = !placing && mode.excavatesHorizontally() ? (int) Math.ceil(reach) : 2;
            for (BlockPos raw : BlockPos.betweenClosed(target.offset(-radius, placing ? -1 : -radius, -radius), target.offset(radius, radius, radius))) {
                if ((!placing && !mode.excavatesHorizontally() && raw.distSqr(target) > 4.0D)
                        || !approachAllowed(raw) || !safe(mine, raw)) continue;
                if (!placing && (MiningSafety.isUnsafeMiningTarget(mine, mine::observed, target, raw, actualPosition)
                        || unsafeBarrierLanding(target, raw) || unsafeBarrierLanding(target, actualPosition)
                        || mode.excavatesArea() && mine.getBlockState(raw).is(Blocks.LADDER))) continue;
                owner.setPos(raw.getBottomCenter());
                Vec3 eye = owner.getEyePosition();
                Vec3 hit;
                if (placing) {
                    hit = target.getCenter();
                } else {
                    hit = HiredMoveToBlockFaceJob.visibleHitPosition(mine, mine::observed, owner, eye, target, ignored -> false);
                }
                if (hit != null && eye.distanceToSqr(hit) <= reach * reach
                        && (placing || raw.getCenter().distanceToSqr(hit) <= (reach + 0.75D) * (reach + 0.75D))) {
                    this.goals.put(raw.immutable(), new Target(this.target, raw.immutable(), hit, List.of()));
                }
            }
            if (!this.goals.isEmpty()) visit(origin, 0, null);
        }

        private Target advance(Budget budget) {
            while (!this.frontier.isEmpty() && budget.consume()) {
                var current = this.frontier.remove();
                if (current.steps() != this.distances.get(current.position())) continue;
                var goal = this.goals.get(current.position());
                if (goal != null) {
                    var path = new ArrayList<BlockPos>();
                    for (var node = current; node != null; node = node.previous()) path.add(node.position());
                    Collections.reverse(path);
                    return new Target(goal.position(), goal.stance(), goal.hit(), List.copyOf(path));
                }
                for (var direction : Direction.Plane.HORIZONTAL) {
                    for (int dy = -1; dy <= 1; dy++) {
                        var next = current.position().relative(direction).offset(0, dy, 0);
                        if (dy > 0 && !clear(mine, current.position().above(2)) || dy < 0 && !clear(mine, next.above(2))) continue;
                        visit(next, current.steps() + 1, current);
                    }
                }
                for (int dy : new int[] {-1, 1}) {
                    var next = current.position().offset(0, dy, 0);
                    if (mine.getBlockState(current.position()).is(Blocks.LADDER) || mine.getBlockState(next).is(Blocks.LADDER)) {
                        visit(next, current.steps() + 1, current);
                    }
                }
            }
            return null;
        }

        private void visit(BlockPos position, int steps, Node previous) {
            if (steps >= this.distances.getOrDefault(position, Integer.MAX_VALUE)
                    || !placing && mode.excavatesHorizontally() && !position.equals(origin) && !approachAllowed(position)
                    || !this.safePositions.computeIfAbsent(position, key -> safe(mine, key))) return;
            var immutable = position.immutable();
            this.distances.put(immutable, steps);
            int horizontal = Math.abs(position.getX() - this.target.getX()) + Math.abs(position.getZ() - this.target.getZ());
            int vertical = Math.abs(position.getY() - this.target.getY());
            int horizontalReach = placing ? 4 : mode.excavatesHorizontally() ? (int) Math.ceil(Math.sqrt(2.0D) * MiningWorker.HORIZONTAL_EXCAVATION_REACH) : 2;
            int verticalReach = !placing && mode.excavatesHorizontally() ? (int) Math.ceil(MiningWorker.HORIZONTAL_EXCAVATION_REACH) : 2;
            this.frontier.add(new Node(immutable, steps, Math.max(0, Math.max(horizontal - horizontalReach, vertical - verticalReach)), this.order++, previous));
        }
    }

    static final class Budget {
        private int remaining;

        Budget() {
            this(512);
        }

        Budget(int remaining) {
            this.remaining = Math.max(0, remaining);
        }

        private boolean consume() {
            if (this.remaining <= 0) return false;
            this.remaining--;
            return true;
        }
    }

    private record Observation(SettlementTerrainSection section, long version) {
    }

    private record Node(BlockPos position, int steps, int estimate, long order, Node previous) {
    }

    record Target(BlockPos position, BlockPos stance, Vec3 hit, List<BlockPos> path) {
        int steps() {
            return Math.max(0, this.path.size() - 1);
        }
    }
}
