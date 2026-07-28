package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/** Persisted position, direction, and stall recovery for a route traversal. */
public final class RouteTraversalCursor {
    private final CompoundTag state;
    private final Tags tags;

    public RouteTraversalCursor(CompoundTag state, Tags tags) {
        this.state = state;
        this.tags = tags;
    }

    public int currentIndex(List<BlockPos> nodes, int defaultIndex) {
        int lastIndex = nodes.size() - 1;
        return state.contains(tags.index(), Tag.TAG_INT)
                ? Math.clamp(state.getInt(tags.index()), 0, lastIndex)
                : Math.clamp(defaultIndex, 0, lastIndex);
    }

    public int index(List<BlockPos> nodes, int defaultIndex) {
        int index = currentIndex(nodes, defaultIndex);
        state.putInt(tags.index(), index);
        ensureDirection();
        return index;
    }

    public void reset(int index) {
        state.putInt(tags.index(), index);
        state.putInt(tags.direction(), 1);
        state.remove(tags.lastNodeReachedGameTime());
    }

    public Traversal moveToCurrentNode(ServerLevel level, Villager villager, List<BlockPos> nodes,
            int defaultIndex, double speed, double arrivalDistanceSqr, long recoveryTicks) {
        Prepared prepared = prepareCurrentNode(
                villager, nodes, defaultIndex, level.getGameTime(), arrivalDistanceSqr, recoveryTicks);
        HiredRouteNavigator.NodeMovement movement = HiredRouteNavigator.moveToRouteNode(
                level, villager, prepared.target(), speed, arrivalDistanceSqr);
        if (movement == HiredRouteNavigator.NodeMovement.ARRIVED) {
            markNodeReached(level.getGameTime());
        }
        return new Traversal(prepared.index(), prepared.target(), movement);
    }

    public Prepared prepareCurrentNode(Villager villager, List<BlockPos> nodes, int defaultIndex,
            long gameTime, double arrivalDistanceSqr, long recoveryTicks) {
        int index = index(nodes, defaultIndex);
        BlockPos target = nodes.get(index);
        boolean recovered = false;
        initializeWatchdog(gameTime);
        if (villager.blockPosition().distSqr(target) > arrivalDistanceSqr
                && recoveryDue(gameTime, recoveryTicks)) {
            index = recoverAtNearestNode(villager, nodes, gameTime);
            target = nodes.get(index);
            recovered = true;
        }
        return new Prepared(index, target, recovered);
    }

    public int advanceLinear(int currentIndex, int step, int size) {
        int nextIndex = Math.clamp(currentIndex + step, 0, size - 1);
        state.putInt(tags.index(), nextIndex);
        clearWatchdog();
        return nextIndex;
    }

    public int advancePatrol(int currentIndex, int size, boolean loop) {
        if (size <= 1) {
            reset(0);
            return 0;
        }
        int nextIndex;
        if (loop) {
            nextIndex = Math.floorMod(currentIndex + 1, size);
        } else {
            int direction = direction();
            nextIndex = currentIndex + direction;
            if (nextIndex >= size) {
                direction = -1;
                nextIndex = size - 2;
            } else if (nextIndex < 0) {
                direction = 1;
                nextIndex = 1;
            }
            state.putInt(tags.direction(), direction);
        }
        state.putInt(tags.index(), nextIndex);
        return nextIndex;
    }

    public void markNodeReached(long gameTime) {
        state.putLong(tags.lastNodeReachedGameTime(), gameTime);
    }

    public void clearWatchdog() {
        state.remove(tags.lastNodeReachedGameTime());
    }

    public void clear() {
        state.remove(tags.index());
        state.remove(tags.direction());
        clearWatchdog();
    }

    private void ensureDirection() {
        if (!state.contains(tags.direction(), Tag.TAG_INT) || state.getInt(tags.direction()) == 0) {
            state.putInt(tags.direction(), 1);
        }
    }

    private int direction() {
        ensureDirection();
        return state.getInt(tags.direction()) < 0 ? -1 : 1;
    }

    private void initializeWatchdog(long gameTime) {
        if (!state.contains(tags.lastNodeReachedGameTime(), Tag.TAG_LONG)
                || state.getLong(tags.lastNodeReachedGameTime()) > gameTime) {
            markNodeReached(gameTime);
        }
    }

    private boolean recoveryDue(long gameTime, long recoveryTicks) {
        return gameTime - state.getLong(tags.lastNodeReachedGameTime()) >= recoveryTicks;
    }

    private int recoverAtNearestNode(Villager villager, List<BlockPos> nodes, long gameTime) {
        int nearestIndex = 0;
        double nearestDistanceSqr = Double.MAX_VALUE;
        for (int index = 0; index < nodes.size(); index++) {
            double distanceSqr = villager.blockPosition().distSqr(nodes.get(index));
            if (distanceSqr < nearestDistanceSqr) {
                nearestDistanceSqr = distanceSqr;
                nearestIndex = index;
            }
        }
        BlockPos nearestNode = nodes.get(nearestIndex);
        VillagerTaskNavigationUtil.stopHiredNavigation(villager);
        HiredPathMemory.clearNavigationProgress(villager);
        HiredPathMemory.clearAvoided(villager, nearestNode);
        state.putInt(tags.index(), nearestIndex);
        markNodeReached(gameTime);
        return nearestIndex;
    }

    public record Tags(String index, String direction, String lastNodeReachedGameTime) {
    }

    public record Prepared(int index, BlockPos target, boolean recovered) {
    }

    public record Traversal(int index, BlockPos target, HiredRouteNavigator.NodeMovement movement) {
    }
}
