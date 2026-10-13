package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.mining.MiningSafety;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.FluidState;

final class SettlementFamilyRoutes implements BlockGetter {
    private final SettlementRecord town;
    private final SettlementBuildingSurvey.ObservedBlocks blocks;

    SettlementFamilyRoutes(ServerLevel level, SettlementRecord town) {
        this.town = town;
        this.blocks = new SettlementBuildingSurvey.ObservedBlocks(level, null, town);
    }

    BlockPos standing(BlockPos position, boolean sleeping) {
        if (safe(position)) return position;
        if (!sleeping) return null;
        for (var offset : BlockPos.betweenClosed(-1, 0, -1, 1, 1, 1)) {
            var candidate = position.offset(offset);
            if (safe(candidate)) return candidate;
        }
        return null;
    }

    List<BlockPos> route(BlockPos start, BlockPos target) {
        return route(start, target, position -> true);
    }

    List<BlockPos> route(BlockPos start, BlockPos target, java.util.function.Predicate<BlockPos> allowed) {
        if (start == null || target == null || !allowed.test(start) || !allowed.test(target) || !safe(start)) return List.of();
        var key = new Key(start.immutable(), target.immutable());
        var cached = this.town.familyRoutes.get(key);
        if (cached != null) {
            if (valid(cached, allowed)) return cached;
            this.town.familyRoutes.remove(key);
        }
        var costs = new HashMap<BlockPos, Integer>();
        var previous = new HashMap<BlockPos, BlockPos>();
        var queue = new PriorityQueue<Step>(Comparator.comparingInt(Step::estimate).thenComparing(step -> step.position));
        costs.put(start, 0);
        queue.add(new Step(start, 0, distance(start, target)));
        int visited = 0;
        while (!queue.isEmpty() && visited++ < 4096) {
            var step = queue.remove();
            var current = step.position;
            if (costs.get(current) != step.cost) continue;
            if (current.distSqr(target) <= 1.0D) {
                var path = new ArrayList<BlockPos>();
                for (var position = current; position != null; position = previous.get(position)) path.add(position);
                java.util.Collections.reverse(path);
                var result = List.copyOf(path);
                this.town.familyRoutes.put(key, result);
                if (this.town.familyRoutes.size() > 256) this.town.familyRoutes.remove(this.town.familyRoutes.keySet().iterator().next());
                return result;
            }
            if (step.cost >= 256) continue;
            for (var direction : Direction.Plane.HORIZONTAL) {
                for (int dy : new int[]{0, 1, -1}) {
                    var next = current.relative(direction).offset(0, dy, 0);
                    if (Math.abs(next.getX() - this.town.center().getX()) > this.town.radius() + 16
                            || Math.abs(next.getZ() - this.town.center().getZ()) > this.town.radius() + 16
                            || !allowed.test(next) || !safe(next)) continue;
                    if (dy > 0 && !clear(current.above(2)) || dy < 0 && !clear(next.above(2))) continue;
                    int cost = step.cost + 1;
                    if (cost < costs.getOrDefault(next, Integer.MAX_VALUE)) {
                        previous.put(next, current);
                        costs.put(next, cost);
                        queue.add(new Step(next, cost, cost + distance(next, target)));
                    }
                    break;
                }
            }
        }
        return List.of();
    }

    private boolean valid(List<BlockPos> route, java.util.function.Predicate<BlockPos> allowed) {
        for (int index = 0; index < route.size(); index++) {
            var next = route.get(index);
            if (Math.abs(next.getX() - this.town.center().getX()) > this.town.radius() + 16
                    || Math.abs(next.getZ() - this.town.center().getZ()) > this.town.radius() + 16
                    || !allowed.test(next) || !safe(next)) return false;
            if (index == 0) continue;
            var previous = route.get(index - 1);
            if (next.getY() > previous.getY() && !clear(previous.above(2))
                    || next.getY() < previous.getY() && !clear(next.above(2))) return false;
        }
        return true;
    }

    record Key(BlockPos start, BlockPos target) { }

    private static int distance(BlockPos first, BlockPos second) {
        return Math.max(Math.abs(first.getX() - second.getX()) + Math.abs(first.getZ() - second.getZ()),
                Math.abs(first.getY() - second.getY()));
    }

    private boolean clear(BlockPos position) {
        return this.blocks.observed(position) && getBlockState(position).getCollisionShape(this, position).isEmpty()
                && getFluidState(position).isEmpty();
    }

    private boolean safe(BlockPos position) {
        return MiningSafety.isSafePathPosition(this, this.blocks::observed, position);
    }

    @Override
    public BlockState getBlockState(BlockPos position) {
        var state = this.blocks.getBlockState(position);
        if (state.getBlock() instanceof DoorBlock && (state.is(BlockTags.WOODEN_DOORS) || state.getValue(DoorBlock.OPEN))) {
            var otherPosition = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? position.above() : position.below();
            var other = this.blocks.getBlockState(otherPosition);
            if (this.blocks.observed(otherPosition) && other.is(state.getBlock())
                    && other.getValue(DoorBlock.HALF) != state.getValue(DoorBlock.HALF)
                    && other.getValue(DoorBlock.FACING) == state.getValue(DoorBlock.FACING)) return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Override
    public FluidState getFluidState(BlockPos position) {
        return getBlockState(position).getFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos position) {
        return this.blocks.getBlockEntity(position);
    }

    @Override
    public int getHeight() {
        return this.blocks.getHeight();
    }

    @Override
    public int getMinBuildHeight() {
        return this.blocks.getMinBuildHeight();
    }

    private record Step(BlockPos position, int cost, int estimate) {
    }
}
