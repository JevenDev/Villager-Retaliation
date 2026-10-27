package com.jvn.villagerretaliation.settlement;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

// tier 1 housing uses open boundaries or wooden doors as entrances
final class SettlementHousingSurvey {
    static final TagKey<Block> LIGHTS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("villagerretaliation", "housing_lights"));
    private final SettlementBuildingSurvey.ObservedBlocks blocks;
    private final SettlementArea area;
    private final Map<BlockPos, Boolean> walkable = new HashMap<>();
    private final Map<BlockPos, Boolean> floors = new HashMap<>();
    private final Set<BlockPos> reachable = new HashSet<>();
    private boolean unobserved;

    SettlementHousingSurvey(SettlementBuildingSurvey.ObservedBlocks blocks, SettlementArea area) {
        this.blocks = blocks;
        this.area = area;
        var entrances = new HashSet<BlockPos>();
        boolean lit = false;
        for (var mutable : BlockPos.betweenClosed(area.min(), area.max())) {
            var pos = mutable.immutable();
            if (!observed(pos)) continue;
            var state = blocks.getBlockState(pos);
            lit |= state.is(LIGHTS) && state.getLightEmission(blocks, pos) > 0;
            if (canStand(pos) && clear(pos) && clear(pos.above())) {
                for (var direction : Direction.Plane.HORIZONTAL) {
                    if (area.contains(pos.relative(direction))) continue;
                    for (int step = -1; step <= 1; step++) {
                        if (canTraverse(pos, pos.relative(direction).above(step), step)) entrances.add(pos);
                    }
                }
            }
            if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                    && door(pos)) {
                var facing = state.getValue(DoorBlock.FACING);
                // Both approaches must be usable, including an approach just outside the selection.
                if (canStand(pos.relative(facing)) && canStand(pos.relative(facing.getOpposite()))) {
                    entrances.add(pos);
                }
            }
        }
        if (!lit) return;
        var visited = new HashSet<BlockPos>();
        for (var entrance : entrances) {
            if (!visited.add(entrance)) continue;
            var queue = new ArrayDeque<BlockPos>();
            queue.add(entrance);
            while (!queue.isEmpty()) {
                var pos = queue.removeFirst();
                this.reachable.add(pos);
                for (var direction : Direction.Plane.HORIZONTAL) {
                    for (int step = -1; step <= 1; step++) {
                        var next = pos.relative(direction).above(step);
                        if (!area.contains(next) || visited.contains(next) || !canTraverse(pos, next, step)) continue;
                        visited.add(next);
                        queue.addLast(next);
                    }
                }
            }
        }
    }

    SettlementBuildingSurvey.BedState bed(BlockPos head) {
        var foot = head.relative(this.blocks.getBlockState(head).getValue(BedBlock.FACING).getOpposite());
        for (var part : new BlockPos[]{head, foot}) {
            for (var direction : Direction.Plane.HORIZONTAL) {
                if (this.reachable.contains(part.relative(direction))) return SettlementBuildingSurvey.BedState.AVAILABLE;
            }
        }
        return this.unobserved ? SettlementBuildingSurvey.BedState.UNOBSERVED : SettlementBuildingSurvey.BedState.BLOCKED;
    }

    private boolean canTraverse(BlockPos from, BlockPos to, int step) {
        return canStand(to) && (step <= 0 || clear(from.above(2))) && (step >= 0 || clear(to.above(2)));
    }

    private boolean canStand(BlockPos pos) {
        return this.walkable.computeIfAbsent(pos, at -> supportsStanding(at.below())
                && (door(at) || clear(at) && clear(at.above())));
    }

    private boolean supportsStanding(BlockPos pos) {
        return floor(pos) || observed(pos) && this.blocks.getFluidState(pos).isEmpty()
                && Block.isFaceFull(this.blocks.getBlockState(pos).getCollisionShape(this.blocks, pos), Direction.DOWN);
    }

    private boolean floor(BlockPos pos) {
        return this.floors.computeIfAbsent(pos, at -> observed(at)
                && this.blocks.getFluidState(at).isEmpty()
                && this.blocks.getBlockState(at).isFaceSturdy(this.blocks, at, Direction.UP));
    }

    private boolean clear(BlockPos pos) {
        return observed(pos) && this.blocks.getFluidState(pos).isEmpty()
                && this.blocks.getBlockState(pos).getCollisionShape(this.blocks, pos).isEmpty();
    }

    private boolean door(BlockPos pos) {
        if (!observed(pos)) return false;
        var lower = this.blocks.getBlockState(pos);
        if (!(lower.getBlock() instanceof DoorBlock) || !lower.is(BlockTags.WOODEN_DOORS)
                || lower.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER
                || !this.area.contains(pos.above()) || !observed(pos.above()) || !floor(pos.below())) return false;
        var upper = this.blocks.getBlockState(pos.above());
        return upper.is(lower.getBlock()) && upper.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
                && upper.getValue(DoorBlock.FACING) == lower.getValue(DoorBlock.FACING)
                && upper.getValue(DoorBlock.OPEN).equals(lower.getValue(DoorBlock.OPEN))
                && upper.getValue(DoorBlock.HINGE) == lower.getValue(DoorBlock.HINGE);
    }

    private boolean observed(BlockPos pos) {
        if (this.blocks.observed(pos)) return true;
        if (!this.blocks.blocked(pos)) this.unobserved = true;
        return false;
    }
}
