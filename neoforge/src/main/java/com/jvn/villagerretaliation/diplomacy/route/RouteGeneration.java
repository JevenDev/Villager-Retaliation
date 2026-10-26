package com.jvn.villagerretaliation.diplomacy.route;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;

final class RouteGeneration {
    static final TicketType<ChunkPos> TICKET = TicketType.create("villagerretaliation_diplomatic_route", Comparator.comparingLong(ChunkPos::toLong));
    static final int DISTANCE = 33 - ChunkLevel.byStatus(ChunkStatus.FEATURES);
    private final ServerLevel level;
    private final ChunkPos position;
    private final long started;
    private CompletableFuture<ChunkResult<ChunkAccess>> future;
    private final List<GenerationChunkHolder> dependencies = new ArrayList<>();
    private boolean released;
    private boolean scheduled;
    private final RouteGenerationMetrics.Observation metrics;

    RouteGeneration(ServerLevel level, ChunkPos position, long now) {
        if (DISTANCE >= 0) throw new IllegalStateException("Route terrain ticket must not load full chunks");
        this.level = level;
        this.position = position;
        this.started = now;
        this.metrics = RouteGenerationMetrics.begin(level, position);
        level.getChunkSource().addRegionTicket(TICKET, position, DISTANCE, position, false);
    }
    static List<Long> footprint(ChunkPos position) {
        int radius = ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES).accumulatedDependencies().getRadius();
        var result = new ArrayList<Long>();
        for (int x = position.x - radius; x <= position.x + radius; x++)
            for (int z = position.z - radius; z <= position.z + radius; z++) result.add(ChunkPos.asLong(x, z));
        return result;
    }
    ChunkAccess poll(long now) {
        if (this.released) return null;
        if (now - this.started > 1200) throw new IllegalStateException("route_generation_timeout");
        var map = this.level.getChunkSource().chunkMap;
        var holder = map.getVisibleChunkIfPresent(this.position.toLong());
        if (holder == null) return null;
        if (!this.scheduled) {
            var required = footprint(this.position);
            for (long key : required) if (map.getVisibleChunkIfPresent(key) == null) return null;
            for (long key : required) {
                var dependency = map.getVisibleChunkIfPresent(key);
                this.dependencies.add(dependency);
            }
            this.future = holder.scheduleChunkGenerationTask(ChunkStatus.FEATURES, map);
            this.scheduled = true;
        }
        if (!this.future.isDone()) return null;
        var result = this.future.getNow(null);
        var chunk = result == null ? null : result.orElse(null);
        if (chunk == null) throw new IllegalStateException("route_generation_unavailable");
        if (this.dependencies.stream().anyMatch(dependency -> dependency.getGenerationRefCount() != 0)) return null;
        return chunk;
    }
    void release() {
        if (this.released) return;
        this.level.getChunkSource().removeRegionTicket(TICKET, this.position, DISTANCE, this.position, false);
        this.released = true;
        RouteGenerationMetrics.finish(this.level, this.metrics);
    }
    boolean drained() { return this.dependencies.stream().allMatch(dependency -> dependency.getGenerationRefCount() == 0); }
    int generatedDependencies() { return this.metrics.generated(); }
    boolean ticketHeld() { return !this.released; }
}
