package com.jvn.villagerretaliation.diplomacy.route;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public final class RouteGenerationMetrics {
    private static final Map<ServerLevel, Observation> ACTIVE = new ConcurrentHashMap<>();
    private RouteGenerationMetrics() { }
    static final class Observation {
        final ChunkPos center;
        final int radius;
        final Set<Long> generated = ConcurrentHashMap.newKeySet();
        Observation(ChunkPos center) {
            this.center = center;
            this.radius = ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES).accumulatedDependencies().getRadius();
        }
        int generated() { return this.generated.size(); }
    }
    static Observation begin(ServerLevel level, ChunkPos center) {
        var observation = new Observation(center);
        if (ACTIVE.putIfAbsent(level, observation) != null) throw new IllegalStateException("Overlapping diplomatic generation requests");
        return observation;
    }
    public static void observe(ServerLevel level, ChunkAccess chunk, ChunkStatus target) {
        var observation = ACTIVE.get(level);
        if (observation == null || !chunk.getPersistedStatus().isBefore(target)) return;
        var position = chunk.getPos();
        if (Math.abs(position.x - observation.center.x) <= observation.radius && Math.abs(position.z - observation.center.z) <= observation.radius)
            observation.generated.add(position.toLong());
    }
    static void finish(ServerLevel level, Observation observation) { ACTIVE.remove(level, observation); }
}
