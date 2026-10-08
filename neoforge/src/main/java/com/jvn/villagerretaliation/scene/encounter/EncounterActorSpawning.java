package com.jvn.villagerretaliation.scene.encounter;

import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

public final class EncounterActorSpawning {
    static final String OWNER = "VillagerRetaliationActorSpawn";

    private EncounterActorSpawning() { }

    static Result resume(MinecraftServer server, SceneSavedData data, EncounterInstance encounter, String key) {
        if (!encounter.actorSpawns().diagnostic().isBlank()) return waiting(encounter.actorSpawns().diagnostic());
        if (encounter.actorSpawns().legacy()) return waiting("This encounter predates actor birth receipts. Inspect its existing actors before migrating its unfinished spawns.");
        var receipt = encounter.actorSpawns().get(key);
        if (receipt == null) return null;
        if (receipt.state() == EncounterActorSpawn.State.INITIALIZING)
            return waiting("Actor initialization has no completion snapshot. Inspect its effects and restore compatible actor data before continuing.");
        if (receipt.state().ordinal() > EncounterActorSpawn.State.COMMITTED.ordinal())
            return waiting("This actor birth was retired by encounter cleanup and cannot be replayed.");
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, receipt.dimension()));
        if (level == null) return waiting("The actor's original dimension is unavailable.");
        try {
            if (receipt.state() == EncounterActorSpawn.State.READY) {
                EncounterActorBirths.reserve(level, receipt);
                receipt = receipt.reserved();
                encounter.actorSpawns().record(receipt);
                data.changed();
                String issue = EncounterPersistence.checkpoint(server, data, encounter);
                if (!issue.isBlank()) return waiting(issue);
            }
            return new Result(EncounterActorMaterializer.materialize(level, receipt), "");
        } catch (IOException | IllegalArgumentException exception) {
            return waiting("Encounter actor recovery is waiting: " + exception.getMessage());
        }
    }

    static Result initialize(ServerLevel level, SceneSavedData data, EncounterInstance encounter, String key,
            Entity actor, Function<Entity, String> initialize) {
        if (!EncounterActorMaterializer.safe(level, actor)) return waiting("Load the actor's chunks and clear its spawn position before initialization.");
        UUID operation = EncounterActorSpawn.operation(encounter.id(), key);
        actor.setUUID(UUID.nameUUIDFromBytes((operation + "/entity").getBytes(StandardCharsets.UTF_8)));
        var receipt = EncounterActorSpawn.prepare(key, encounter.id(), actor.getUUID(), level.dimension().location(), actor.blockPosition());
        encounter.actorSpawns().record(receipt);
        data.changed();
        String issue = EncounterPersistence.checkpoint(level.getServer(), data, encounter);
        if (!issue.isBlank()) {
            encounter.actorSpawns().removeUnattempted(receipt);
            data.changed();
            return waiting(issue);
        }
        stamp(actor, receipt);
        try {
            receipt = receipt.ready(EncounterActorInitializer.initialize(actor, receipt, initialize));
            encounter.actorSpawns().record(receipt);
            data.changed();
            issue = EncounterPersistence.checkpoint(level.getServer(), data, encounter);
            return issue.isBlank() ? resume(level.getServer(), data, encounter, key) : waiting(issue);
        } catch (IOException | IllegalArgumentException exception) {
            return waiting("Encounter actor initialization is waiting: " + exception.getMessage());
        }
    }

    static String complete(MinecraftServer server, SceneSavedData data, EncounterInstance encounter, Entity actor) {
        if (!EncounterPersistence.required(data, encounter)) return "";
        var receipt = encounter.actorSpawns().forEntity(actor.getUUID());
        if (receipt != null) encounter.actorSpawns().record(receipt.committed());
        data.changed();
        return EncounterPersistence.checkpoint(server, data, encounter);
    }

    static String retireAll(MinecraftServer server, SceneSavedData data, EncounterInstance encounter, boolean forceRemove) {
        if (!EncounterPersistence.required(data, encounter)) return "";
        if (!encounter.actorSpawns().diagnostic().isBlank()) return encounter.actorSpawns().diagnostic();
        for (var receipt : java.util.List.copyOf(encounter.actorSpawns().records().values())) {
            var ally = encounter.ally(receipt.entity());
            boolean remove = forceRemove || receipt.state().ordinal() < EncounterActorSpawn.State.COMMITTED.ordinal()
                    || (ally == null ? encounter.cleanupPolicy() == EncounterTemplate.CleanupPolicy.REMOVE_SURVIVORS
                            : ally.cleanupPolicy() == EncounterTemplate.AllyCleanupPolicy.REMOVE);
            String issue = retire(server, data, encounter, receipt.entity(), remove);
            if (!issue.isBlank()) return issue;
        }
        return "";
    }

    static String retire(MinecraftServer server, SceneSavedData data, EncounterInstance encounter, UUID actor, boolean remove) {
        if (!EncounterPersistence.required(data, encounter)) return "";
        var receipt = encounter.actorSpawns().forEntity(actor);
        if (receipt == null) return "";
        if (receipt.state() == EncounterActorSpawn.State.REMOVED) remove = true;
        var target = remove ? EncounterActorSpawn.State.REMOVED : EncounterActorSpawn.State.RELEASED;
        if (receipt.state() != target) {
            encounter.actorSpawns().record(receipt.retired(remove));
            data.changed();
            String issue = EncounterPersistence.checkpoint(server, data, encounter);
            if (!issue.isBlank()) return issue;
        }
        for (var node : receipt.nodes()) {
            if (!node.owned()) continue;
            var entity = EncounterActorMaterializer.find(server, node.id());
            if (entity == null || !owns(entity.getPersistentData(), receipt)) continue;
            if (remove) entity.discard();
            else if (node.id().equals(receipt.entity())) EncounterService.releaseActor(entity, encounter);
            else entity.getPersistentData().remove(OWNER);
        }
        return "";
    }

    static String resumeRetirements(MinecraftServer server, SceneSavedData data, EncounterInstance encounter) {
        for (var receipt : java.util.List.copyOf(encounter.actorSpawns().records().values())) {
            if (receipt.state().ordinal() <= EncounterActorSpawn.State.COMMITTED.ordinal()) continue;
            String issue = retire(server, data, encounter, receipt.entity(), receipt.state() == EncounterActorSpawn.State.REMOVED);
            if (!issue.isBlank()) return issue;
        }
        return "";
    }

    static String retireRetry(MinecraftServer server, SceneSavedData data, EncounterInstance encounter,
            java.util.Set<UUID> removed, int generation, int start) {
        if (!EncounterPersistence.required(data, encounter)) return "";
        for (var receipt : java.util.List.copyOf(encounter.actorSpawns().records().values())) {
            boolean unfinished = receipt.state().ordinal() < EncounterActorSpawn.State.COMMITTED.ordinal()
                    && receipt.key().startsWith("member/" + generation + "/")
                    && Integer.parseInt(receipt.key().substring(receipt.key().lastIndexOf('/') + 1)) >= start;
            if (removed.contains(receipt.entity()) || unfinished) encounter.actorSpawns().record(receipt.retired(true));
        }
        data.changed();
        String issue = EncounterPersistence.checkpoint(server, data, encounter);
        return issue.isBlank() ? resumeRetirements(server, data, encounter) : issue;
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        EncounterActorInitializer.onEntityJoin(event);
        if (retired(event.getEntity())) event.setCanceled(true);
    }

    public static boolean beforeTick(Entity entity) {
        return !retired(entity) && !blocked(entity);
    }

    public static boolean blocked(Entity entity) {
        if (!(entity.level() instanceof ServerLevel)) return false;
        return entity.getSelfAndPassengers().anyMatch(EncounterActorSpawning::pending);
    }

    private static boolean pending(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !entity.getPersistentData().contains(OWNER)) return false;
        var data = SceneSavedData.get(level);
        var owner = entity.getPersistentData().getCompound(OWNER);
        var encounter = owner.hasUUID("Encounter") ? data.encounter(owner.getUUID("Encounter")).orElse(null) : null;
        var receipt = encounter == null || !owner.hasUUID("Operation") ? null : encounter.actorSpawns().forOperation(owner.getUUID("Operation"));
        return receipt == null || receipt.state().ordinal() < EncounterActorSpawn.State.COMMITTED.ordinal() || data.campaignPersistencePending();
    }

    private static boolean retired(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return false;
        var owner = entity.getPersistentData().getCompound(OWNER);
        if (!owner.hasUUID("Encounter") || !owner.hasUUID("Operation")) return false;
        var data = SceneSavedData.get(level);
        if (data.campaignPersistencePending()) return false;
        var encounter = data.encounter(owner.getUUID("Encounter")).orElse(null);
        var receipt = encounter == null ? null : encounter.actorSpawns().forOperation(owner.getUUID("Operation"));
        if (receipt == null) return false;
        if (receipt.state() == EncounterActorSpawn.State.REMOVED) { entity.discard(); return true; }
        if (receipt.state() == EncounterActorSpawn.State.RELEASED) {
            if (entity.getUUID().equals(receipt.entity())) EncounterService.releaseActor(entity, encounter);
            else entity.getPersistentData().remove(OWNER);
        }
        return false;
    }

    static void stamp(Entity entity, EncounterActorSpawn receipt) {
        var owner = new CompoundTag();
        owner.putUUID("Encounter", receipt.encounter());
        owner.putUUID("Operation", receipt.operation());
        entity.getPersistentData().put(OWNER, owner);
    }

    static boolean owns(CompoundTag persistent, EncounterActorSpawn receipt) {
        var owner = persistent.getCompound(OWNER);
        return owner.getAllKeys().equals(java.util.Set.of("Encounter", "Operation")) && owner.hasUUID("Encounter") && owner.hasUUID("Operation")
                && owner.getUUID("Encounter").equals(receipt.encounter()) && owner.getUUID("Operation").equals(receipt.operation());
    }

    private static Result waiting(String issue) { return new Result(null, issue); }
    record Result(Entity entity, String diagnostic) { }
}
