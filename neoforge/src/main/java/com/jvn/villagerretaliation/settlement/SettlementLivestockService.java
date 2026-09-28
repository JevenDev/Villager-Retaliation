package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

public final class SettlementLivestockService {
    static final int UNOBSERVED = -2;
    private static final Map<SettlementArea, Integer> AREA_CURSORS = new WeakHashMap<>();
    private static final Map<SettlementLivestock, Integer> WORKER_CURSORS = new WeakHashMap<>();
    private static final Map<Animal, SettlementRecord> OWNERS = new WeakHashMap<>();

    static void invalidateOwners() { OWNERS.clear(); }

    private SettlementLivestockService() {
    }

    static void observeArea(ServerLevel level, SettlementRecord town, SettlementArea area) {
        if (area.type() != SettlementAreaType.ANIMAL_PEN) return;
        var animals = level.getEntitiesOfClass(Animal.class, new AABB(area.min().getX(), area.min().getY(), area.min().getZ(),
                area.max().getX() + 1.0D, area.max().getY() + 1.0D, area.max().getZ() + 1.0D),
                Animal::isAlive);
        if (animals.isEmpty()) return;
        int cursor = Math.floorMod(AREA_CURSORS.getOrDefault(area, 0), animals.size());
        int count = Math.min(16, animals.size());
        for (int index = 0; index < count; index++) {
            Animal animal = animals.get((cursor + index) % animals.size());
            var previous = owner(level, animal);
            if (previous != null && previous != town) continue;
            var state = town.livestock().get(animal.getUUID());
            if (state == null || !state.area().equals(area.id())) {
                if (state != null) state.restore(animal);
                state = SettlementLivestock.capture(animal, area.id(), level.getGameTime());
                town.recordLivestock(state);
            } else {
                state.restore(animal);
                state.observe(animal, level.isPositionEntityTicking(animal.blockPosition()) ? level.getGameTime() : state.checkedAt());
            }
            OWNERS.put(animal, town);
            observeAccess(level, town, area, state, animal);
            town.changed();
        }
        AREA_CURSORS.put(area, (cursor + count) % animals.size());
    }

    private static void observeAccess(ServerLevel level, SettlementRecord town, SettlementArea area,
                                      SettlementLivestock state, Animal animal) {
        var workers = town.workers().values().stream().filter(worker -> worker.role() == HiredVillagerRole.ANIMAL_HANDLING
                && worker.area().equals(area.id()) && area.contains(worker.position())).toList();
        if (workers.isEmpty()) return;
        int cursor = Math.floorMod(WORKER_CURSORS.getOrDefault(state, 0), workers.size());
        int count = Math.min(4, workers.size());
        for (int index = 0; index < count; index++) {
            var worker = workers.get((cursor + index) % workers.size());
            int steps = observedSteps(level, town, area, worker, animal);
            if (steps != UNOBSERVED) state.access(worker.id(), worker.position(), steps);
        }
        WORKER_CURSORS.put(state, (cursor + count) % workers.size());
    }

    static void observeWorker(ServerLevel level, SettlementRecord town, SettlementWorkerState worker) {
        if (worker.role() != HiredVillagerRole.ANIMAL_HANDLING) return;
        var area = town.areas().get(worker.area());
        if (area == null || area.type() != SettlementAreaType.ANIMAL_PEN) return;
        observeArea(level, town, area);
        for (var state : town.livestock().values().stream().filter(animal -> animal.area().equals(area.id()))
                .sorted(java.util.Comparator.comparingDouble(animal -> animal.position().distSqr(worker.position()))).limit(16).toList()) {
            var animal = state.context(level);
            if (animal == null) continue;
            int steps = observedSteps(level, town, area, worker, animal);
            if (steps != UNOBSERVED) state.access(worker.id(), worker.position(), steps);
        }
    }

    static int observedSteps(ServerLevel level, SettlementRecord town, SettlementArea area,
                              SettlementWorkerState worker, Entity animal) {
        var proxy = worker.context(level, town);
        if (proxy == null || !area.contains(worker.position()) || !area.contains(animal.blockPosition())) return -1;
        if (proxy.distanceToSqr(animal) <= (animal instanceof net.minecraft.world.entity.item.ItemEntity ? 2.25D : 9.0D)) return 0;
        int radius = (int) Math.ceil(proxy.getAttributeValue(Attributes.FOLLOW_RANGE)) + 9;
        if (radius < 9 || radius > 256) return -1;
        BlockPos start = proxy.blockPosition();
        for (int x = (start.getX() - radius) >> 4; x <= (start.getX() + radius) >> 4; x++) {
            for (int z = (start.getZ() - radius) >> 4; z <= (start.getZ() + radius) >> 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) return UNOBSERVED;
            }
        }
        var path = proxy.getNavigation().createPath(animal.blockPosition(), 1);
        if (path == null || !path.canReach() || path.getNodeCount() > 4096 || path.getEndNode() == null
                || path.getEndNode().asBlockPos().distSqr(animal.blockPosition()) > 9.0D) return -1;
        for (int index = 0; index < path.getNodeCount(); index++) {
            if (!area.contains(path.getNodePos(index))) return -1;
        }
        return Math.max(0, path.getNodeCount() - 1);
    }

    static void update(ServerLevel level, SettlementRecord town, long now) {
        SettlementAnimalBirthService.update(level, town);
        SettlementItemDropService.update(level, town, now);
        var frames = new java.util.ArrayList<SettlementLivestockBreeding.Frame>();
        for (var state : List.copyOf(town.livestock().values())) {
            var entity = level.getEntity(state.id());
            if (entity != null && (!(entity instanceof Animal) || !entity.isAlive()
                    || !state.type().equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())))) {
                town.removeLivestock(state.id());
                continue;
            }
            var area = town.areas().get(state.area());
            if (entity instanceof Animal animal) {
                state.restore(animal);
                boolean active = level.isPositionEntityTicking(animal.blockPosition());
                state.observe(animal, active ? now : state.checkedAt());
                if (!active) {
                    var frame = SettlementLivestockBreeding.step(level, state, now);
                    frames.add(frame);
                    SettlementLivestockProducts.advance(level, town, frame, now);
                    if (frame.animal() == null || !frame.animal().isInLove()) SettlementPasture.graze(level, town, state, frame.elapsed());
                    state.restore(animal);
                } else {
                    state.resetGrazing();
                    state.resetMating();
                }
                if (area == null || area.type() != SettlementAreaType.ANIMAL_PEN || !area.contains(animal.blockPosition())) {
                    town.removeLivestock(state.id());
                    OWNERS.remove(animal);
                }
            } else if (level.areEntitiesLoaded(ChunkPos.asLong(state.position())) && !SettlementAnimalBirthService.pending(level, town, state.id())) {
                town.removeLivestock(state.id());
            } else if (area != null && area.type() == SettlementAreaType.ANIMAL_PEN && area.contains(state.position())) {
                var frame = SettlementLivestockBreeding.step(level, state, now);
                frames.add(frame);
                SettlementLivestockProducts.advance(level, town, frame, now);
                if (frame.animal() == null || !frame.animal().isInLove()) SettlementPasture.graze(level, town, state, frame.elapsed());
            } else if (!state.pending()) {
                town.removeLivestock(state.id());
            }
            town.changed();
        }
        SettlementLivestockBreeding.advance(level, town, frames, now);
        SettlementAnimalBirthService.update(level, town);
        SettlementItemDropService.update(level, town, now);
    }

    public static void onActive(Animal animal) {
        if (!(animal.level() instanceof ServerLevel level)) return;
        var town = owner(level, animal);
        if (town == null) return;
        var state = town.livestock().get(animal.getUUID());
        if (state != null) {
            boolean restored = state.restore(animal);
            boolean grazing = state.resetGrazing();
            if (state.resetMating() || grazing || restored) town.changed();
        }
    }

    public static void onUnloaded(Animal animal) {
        if (!(animal.level() instanceof ServerLevel level)) return;
        var town = owner(level, animal);
        OWNERS.remove(animal);
        if (town == null) return;
        var reason = animal.getRemovalReason();
        if (reason != null && reason != Entity.RemovalReason.UNLOADED_TO_CHUNK) {
            town.removeLivestock(animal.getUUID());
            return;
        }
        var state = town.livestock().get(animal.getUUID());
        state.restore(animal);
        state.observe(animal, level.getGameTime());
        var area = town.areas().get(state.area());
        if (area != null) observeAccess(level, town, area, state, animal);
        town.changed();
    }

    static void applyLoaded(ServerLevel level, SettlementLivestock state) {
        if (level.getEntity(state.id()) instanceof Animal animal) state.restore(animal);
    }

    private static SettlementRecord owner(ServerLevel level, Animal animal) {
        if (OWNERS.containsKey(animal)) {
            var cached = OWNERS.get(animal);
            if (cached == null || cached.livestock().containsKey(animal.getUUID())) return cached;
        }
        for (var town : SettlementSavedData.get(level).settlements()) {
            if (town.dimension().equals(level.dimension().location()) && town.livestock().containsKey(animal.getUUID())) {
                OWNERS.put(animal, town);
                return town;
            }
        }
        OWNERS.put(animal, null);
        return null;
    }
}
