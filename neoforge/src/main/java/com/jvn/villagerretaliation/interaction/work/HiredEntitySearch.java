package com.jvn.villagerretaliation.interaction.work;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

public final class HiredEntitySearch {
    private HiredEntitySearch() {
    }

    public static <T extends Entity> T nearest(
            ServerLevel level,
            Class<T> entityClass,
            AABB bounds,
            Predicate<? super T> filter,
            ToDoubleFunction<? super T> distance) {
        return nearest(level, entityClass, List.of(bounds), filter, distance);
    }

    public static <T extends Entity> T nearest(
            ServerLevel level,
            Class<T> entityClass,
            List<AABB> searchBounds,
            Predicate<? super T> filter,
            ToDoubleFunction<? super T> distance) {
        if (level == null || searchBounds == null || searchBounds.isEmpty()) {
            return null;
        }
        Predicate<? super T> safeFilter = filter == null ? ignored -> true : filter;
        ToDoubleFunction<? super T> safeDistance = distance == null ? ignored -> 0.0D : distance;
        IntOpenHashSet seenEntityIds = searchBounds.size() > 1 ? new IntOpenHashSet() : null;
        Predicate<? super T> queryFilter = seenEntityIds == null ? safeFilter : ignored -> true;
        T nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (AABB bounds : searchBounds) {
            if (bounds == null) {
                continue;
            }
            for (T candidate : level.getEntitiesOfClass(entityClass, bounds, queryFilter)) {
                if (seenEntityIds != null && !seenEntityIds.add(candidate.getId())) {
                    continue;
                }
                if (seenEntityIds != null && !safeFilter.test(candidate)) {
                    continue;
                }
                double candidateDistance = safeDistance.applyAsDouble(candidate);
                if (candidateDistance < nearestDistance) {
                    nearest = candidate;
                    nearestDistance = candidateDistance;
                }
            }
        }
        return nearest;
    }
}
