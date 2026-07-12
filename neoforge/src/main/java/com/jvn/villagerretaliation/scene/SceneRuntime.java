package com.jvn.villagerretaliation.scene;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.party.PartyRecord;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.scene.actor.SceneActorBinding;
import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration;
import com.jvn.villagerretaliation.scene.model.CompiledScene;
import com.jvn.villagerretaliation.scene.model.SceneResource;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import com.jvn.villagerretaliation.scene.runtime.SceneOwner;
import com.jvn.villagerretaliation.scene.runtime.SceneScheduler;
import com.jvn.villagerretaliation.scene.runtime.SceneStepEngine;
import com.jvn.villagerretaliation.scene.executor.BuiltinSceneStepExecutors;
import com.jvn.villagerretaliation.scene.executor.EncounterStepExecutors;
import com.jvn.villagerretaliation.scene.encounter.EncounterService;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public final class SceneRuntime {
    private static final Map<MinecraftServer, SceneScheduler> SCHEDULERS = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile SceneScheduler.Processor processor;

    private SceneRuntime() { }

    public static void initialize(MinecraftServer server) {
        BuiltinSceneStepExecutors.register();
        EncounterStepExecutors.register();
        installProcessor(SceneStepEngine::process);
        SceneLaunchService.install(SceneRuntime::launch);
        scheduler(server);
        SceneSavedData.get(server.overworld());
    }

    public static SceneLaunchService.LaunchResult launch(SceneLaunchService.LaunchRequest request) {
        if (request == null || request.server() == null) return SceneLaunchService.LaunchResult.rejected("scene launch request is missing");
        CompiledScene scene = SceneResources.scene(request.server(), request.sceneId()).orElse(null);
        if (scene == null) return SceneLaunchService.LaunchResult.rejected("unknown scene " + request.sceneId());
        OwnerAndParticipants owner = owner(scene, request);
        if (owner == null) return SceneLaunchService.LaunchResult.rejected("scene ownership requirements are not satisfied");
        Map<String, SceneActorBinding> bindings = initialBindings(request.server(), scene, request.playerId(), request.providerId());
        SceneSavedData data = SceneSavedData.get(request.server().overworld());
        SceneSavedData.StartResult result = data.start(scene, request.operationId(), owner.owner(),
                owner.owningQuestInstance(), owner.participants(), bindings,
                request.server().overworld().getGameTime(), request.questId());
        if (result.instance() == null) {
            return SceneLaunchService.LaunchResult.accepted(result.instanceId(), false);
        }
        data.changed();
        scheduler(request.server()).enqueue(result.instance());
        return SceneLaunchService.LaunchResult.accepted(result.instance().id(), result.created());
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        SceneSavedData data = SceneSavedData.get(server.overworld());
        scheduler(server).tick(server, data, server.overworld().getGameTime());
        if(server.overworld().getGameTime()%20L==0L){EncounterService.maintainCleanup(server,data);maintainSceneCleanup(server,data);}
    }
    public static void wake(MinecraftServer server, SceneInstance instance) { if(server!=null&&instance!=null)scheduler(server).enqueue(instance); }

    public static void installProcessor(SceneScheduler.Processor implementation) {
        processor = implementation;
        synchronized (SCHEDULERS) { SCHEDULERS.values().forEach(value -> value.processor(implementation)); }
    }

    public static void clearRuntimeState() {
        SCHEDULERS.clear();
        SceneLaunchService.install(null);
        SceneStepEngine.clearRuntimeState();
    }

    private static SceneScheduler scheduler(MinecraftServer server) {
        synchronized (SCHEDULERS) {
            return SCHEDULERS.computeIfAbsent(server, key -> {
                SceneScheduler value = new SceneScheduler(SceneScheduler.DEFAULT_MAX_WORK_PER_TICK, processor);
                value.rebuild(SceneSavedData.get(server.overworld()), server.overworld().getGameTime());
                return value;
            });
        }
    }

    private static void maintainSceneCleanup(MinecraftServer server, SceneSavedData data) {
        for (SceneInstance scene : data.takeCleanupBatch(16)) {
            CompiledScene definition = SceneResources.scene(server, scene.sceneId()).orElse(null);
            if (definition == null) {
                scene.cleanupStatus(SceneInstance.CleanupStatus.BLOCKED);
                data.requestCleanup(scene);
                continue;
            }
            boolean clean = true;
            if (definition.cleanupPolicy() != SceneResource.CleanupPolicy.NONE
                    && definition.cleanupPolicy() != SceneResource.CleanupPolicy.PRESERVE_WORLD) {
                for (var encounter : data.encounters()) {
                    if (!encounter.sceneId().equals(scene.id())) continue;
                    switch (encounter.state()) {
                        case CLEANED, RELEASED -> { }
                        default -> { EncounterService.cleanup(server, data, encounter, false); clean = false; }
                    }
                }
            }
            if (clean) scene.cleanupStatus(SceneInstance.CleanupStatus.COMPLETE);
            else data.requestCleanup(scene);
            data.changed();
        }
    }

    private static OwnerAndParticipants owner(CompiledScene scene, SceneLaunchService.LaunchRequest request) {
        UUID player = request.playerId();
        return switch (scene.ownership()) {
            case PLAYER -> player == null ? null : new OwnerAndParticipants(
                    new SceneOwner(SceneResource.OwnershipMode.PLAYER, player, null, null, ""), Set.of(player), request.questRunId());
            case PARTY -> {
                PartyRecord party = player == null ? null : PartyService.getPartyForPlayer(request.server().overworld(), player).orElse(null);
                yield party == null ? null : new OwnerAndParticipants(
                        new SceneOwner(SceneResource.OwnershipMode.PARTY, null, party.id(), null, ""),
                        Set.copyOf(party.playerIds()), request.questRunId());
            }
            case QUEST_INSTANCE -> {
                if (player == null) yield null;
                UUID quest = request.questRunId() != null ? request.questRunId()
                        : UUID.nameUUIDFromBytes((scene.id() + "|standalone|" + player).getBytes(StandardCharsets.UTF_8));
                yield new OwnerAndParticipants(new SceneOwner(SceneResource.OwnershipMode.QUEST_INSTANCE, player, null, quest, ""), Set.of(player), quest);
            }
            case WORLD -> new OwnerAndParticipants(new SceneOwner(SceneResource.OwnershipMode.WORLD, null, null, null,
                    request.server().overworld().dimension().location().toString()), player == null ? Set.of() : Set.of(player), null);
        };
    }

    private static Map<String, SceneActorBinding> initialBindings(MinecraftServer server, CompiledScene scene,
            UUID playerId, UUID providerId) {
        Map<String, SceneActorBinding> result = new LinkedHashMap<>();
        for (SceneActorDeclaration actor : scene.actors().values()) {
            UUID entityId = switch (actor.bindingSource()) {
                case OWNER_PLAYER -> playerId;
                case QUEST_PROVIDER -> providerId;
                case UUID, PARTY_MEMBER -> parseUuid(actor.bindingReference());
                default -> null;
            };
            Entity entity = findEntity(server, entityId);
            if (entity != null) {
                result.put(actor.alias(), SceneActorBinding.entity(actor.alias(), actor.actorType(), entity.getUUID(),
                        actor.bindingSource() == SceneActorDeclaration.BindingSource.QUEST_PROVIDER
                                ? VillagerRetaliation.id("villager") : actor.actorType(),
                        entity.level().dimension().location(), entity.blockPosition(), entity.getDisplayName().getString(), true));
            } else if (entityId != null) {
                result.put(actor.alias(), new SceneActorBinding(actor.alias(), actor.actorType(), entityId.toString(), entityId,
                        actor.actorType(), null, null, Map.of(), 1L, SceneActorBinding.BindingState.MISSING, List.of()));
            } else if (actor.bindingSource() == SceneActorDeclaration.BindingSource.MARKER) {
                SceneActorBinding marker = markerBinding(actor);
                if (marker != null) result.put(actor.alias(), marker);
            } else if (actor.bindingSource() == SceneActorDeclaration.BindingSource.UNBOUND
                    && actor.replacementPolicy() == SceneActorDeclaration.ReplacementPolicy.OPERATOR_REBINDABLE) {
                result.put(actor.alias(), new SceneActorBinding(actor.alias(), actor.actorType(), "unbound:" + actor.alias(), null,
                        actor.actorType(), null, null, actor.filters(), 1L, SceneActorBinding.BindingState.MISSING, List.of()));
            }
        }
        return Map.copyOf(result);
    }

    private static SceneActorBinding markerBinding(SceneActorDeclaration actor) {
        ResourceLocation dimension = ResourceLocation.tryParse(actor.filters().getOrDefault("dimension", ""));
        try {
            if (dimension == null || !actor.filters().containsKey("x") || !actor.filters().containsKey("y") || !actor.filters().containsKey("z")) return null;
            BlockPos position = new BlockPos(Integer.parseInt(actor.filters().get("x")), Integer.parseInt(actor.filters().get("y")),
                    Integer.parseInt(actor.filters().get("z")));
            String identity = actor.bindingReference().isBlank() ? dimension + "@" + position.toShortString() : actor.bindingReference();
            return new SceneActorBinding(actor.alias(), actor.actorType(), identity, null, VillagerRetaliation.id("marker"),
                    dimension, position, actor.filters(), 1L, SceneActorBinding.BindingState.SNAPSHOT, List.of());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static UUID parseUuid(String value) {
        try { return value == null || value.isBlank() ? null : UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static Entity findEntity(MinecraftServer server, UUID id) {
        if (id == null) return null;
        var player = server.getPlayerList().getPlayer(id); if (player != null) return player;
        for (ServerLevel level : server.getAllLevels()) { Entity entity = level.getEntity(id); if (entity != null) return entity; }
        return null;
    }

    private record OwnerAndParticipants(SceneOwner owner, Set<UUID> participants, UUID owningQuestInstance) {
        private OwnerAndParticipants(SceneOwner owner, Set<UUID> participants) { this(owner, participants, null); }
    }
}
