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
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public final class SceneRuntime {
    private static final Map<MinecraftServer, SceneScheduler> SCHEDULERS = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile SceneScheduler.Processor processor;

    private SceneRuntime() { }

    public static void initialize(MinecraftServer server) {
        BuiltinSceneStepExecutors.register();
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
                owner.owner().questInstanceId(), owner.participants(), bindings, request.server().overworld().getGameTime());
        scheduler(request.server()).enqueue(result.instance());
        return SceneLaunchService.LaunchResult.accepted(result.instance().id(), result.created());
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        SceneSavedData data = SceneSavedData.get(server.overworld());
        scheduler(server).tick(server, data, server.overworld().getGameTime());
    }

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

    private static OwnerAndParticipants owner(CompiledScene scene, SceneLaunchService.LaunchRequest request) {
        UUID player = request.playerId();
        return switch (scene.ownership()) {
            case PLAYER -> player == null ? null : new OwnerAndParticipants(
                    new SceneOwner(SceneResource.OwnershipMode.PLAYER, player, null, null, ""), Set.of(player));
            case PARTY -> {
                PartyRecord party = player == null ? null : PartyService.getPartyForPlayer(request.server().overworld(), player).orElse(null);
                yield party == null ? null : new OwnerAndParticipants(
                        new SceneOwner(SceneResource.OwnershipMode.PARTY, null, party.id(), null, ""),
                        Set.copyOf(party.playerIds()));
            }
            case QUEST_INSTANCE -> {
                if (player == null) yield null;
                UUID quest = UUID.nameUUIDFromBytes((scene.id() + "|" + player).getBytes(StandardCharsets.UTF_8));
                yield new OwnerAndParticipants(new SceneOwner(SceneResource.OwnershipMode.QUEST_INSTANCE, player, null, quest, ""), Set.of(player));
            }
            case WORLD -> new OwnerAndParticipants(new SceneOwner(SceneResource.OwnershipMode.WORLD, null, null, null,
                    request.server().overworld().dimension().location().toString()), player == null ? Set.of() : Set.of(player));
        };
    }

    private static Map<String, SceneActorBinding> initialBindings(MinecraftServer server, CompiledScene scene,
            UUID playerId, UUID providerId) {
        Map<String, SceneActorBinding> result = new LinkedHashMap<>();
        for (SceneActorDeclaration actor : scene.actors().values()) {
            UUID entityId = switch (actor.bindingSource()) {
                case OWNER_PLAYER -> playerId;
                case QUEST_PROVIDER -> providerId;
                default -> null;
            };
            Entity entity = findEntity(server, entityId);
            if (entity != null) {
                result.put(actor.alias(), SceneActorBinding.entity(actor.alias(), actor.actorType(), entity.getUUID(),
                        actor.bindingSource() == SceneActorDeclaration.BindingSource.QUEST_PROVIDER
                                ? VillagerRetaliation.id("villager") : actor.actorType(),
                        entity.level().dimension().location(), entity.blockPosition(), entity.getDisplayName().getString(), true));
            }
        }
        return Map.copyOf(result);
    }

    private static Entity findEntity(MinecraftServer server, UUID id) {
        if (id == null) return null;
        var player = server.getPlayerList().getPlayer(id); if (player != null) return player;
        for (ServerLevel level : server.getAllLevels()) { Entity entity = level.getEntity(id); if (entity != null) return entity; }
        return null;
    }

    private record OwnerAndParticipants(SceneOwner owner, Set<UUID> participants) { }
}
