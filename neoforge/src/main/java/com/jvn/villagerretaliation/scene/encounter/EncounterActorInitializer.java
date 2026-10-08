package com.jvn.villagerretaliation.scene.encounter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

final class EncounterActorInitializer {
    private static final ThreadLocal<Capture> ACTIVE = new ThreadLocal<>();

    private EncounterActorInitializer() { }

    static List<EncounterActorSpawn.Node> initialize(Entity actor, EncounterActorSpawn receipt, Function<Entity, String> initialize) throws IOException {
        var capture = new Capture(receipt, new LinkedHashSet<>());
        var previous = ACTIVE.get();
        ACTIVE.set(capture);
        try {
            String issue = initialize.apply(actor);
            if (!issue.isBlank()) throw new IOException(issue);
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
        var roots = new LinkedHashSet<Entity>();
        roots.add(actor.getRootVehicle());
        capture.joined().stream().filter(entity -> entity.level() instanceof ServerLevel level && level.getEntity(entity.getUUID()) == entity)
                .map(Entity::getRootVehicle).forEach(roots::add);
        var nodes = new ArrayList<EncounterActorSpawn.Node>();
        var visited = new java.util.HashSet<java.util.UUID>();
        for (var root : roots) {
            for (var entity : root.getSelfAndPassengers().toList()) {
                if (!visited.add(entity.getUUID())) continue;
                if (!(entity.level() instanceof ServerLevel level) || !level.dimension().location().equals(receipt.dimension()))
                    throw new IOException("Actor initialization changed dimensions before its snapshot was saved.");
                boolean owned = entity == actor || capture.joined().contains(entity) || level.getEntity(entity.getUUID()) != entity;
                if (owned) {
                    EncounterActorSpawning.stamp(entity, receipt);
                    if (entity.isPassenger()) entity.getVehicle().positionRider(entity);
                }
                var snapshot = new CompoundTag();
                if (!entity.saveAsPassenger(snapshot)) throw new IOException("An initialized encounter actor cannot be saved.");
                snapshot.remove("Passengers");
                nodes.add(new EncounterActorSpawn.Node(snapshot, entity.getVehicle() == null ? null : entity.getVehicle().getUUID(), owned));
                if (nodes.size() > 256) throw new IOException("Actor initialization exceeds the saved entity tree limit.");
            }
        }
        return List.copyOf(nodes);
    }

    static void onEntityJoin(EntityJoinLevelEvent event) {
        var capture = ACTIVE.get();
        if (capture == null || !(event.getLevel() instanceof ServerLevel) || event.loadedFromDisk()) return;
        if (event.getEntity().getPersistentData().contains(EncounterActorSpawning.OWNER)
                && !EncounterActorSpawning.owns(event.getEntity().getPersistentData(), capture.receipt())) return;
        EncounterActorSpawning.stamp(event.getEntity(), capture.receipt());
        capture.joined().add(event.getEntity());
    }

    private record Capture(EncounterActorSpawn receipt, Set<Entity> joined) { }
}
