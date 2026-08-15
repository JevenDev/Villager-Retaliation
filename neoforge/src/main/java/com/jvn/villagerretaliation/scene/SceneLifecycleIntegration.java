package com.jvn.villagerretaliation.scene;

import com.jvn.villagerretaliation.scene.actor.SceneActorBinding;
import com.jvn.villagerretaliation.scene.actor.SceneActorBindingService;
import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration;
import com.jvn.villagerretaliation.scene.encounter.EncounterService;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import com.jvn.villagerretaliation.scene.runtime.SceneState;
import com.jvn.villagerretaliation.scene.runtime.SceneTransitionService;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;

public final class SceneLifecycleIntegration {
    private static final ThreadLocal<UUID> ORIGINATING_SCENE = new ThreadLocal<>();

    private SceneLifecycleIntegration() {}

    public static void onQuestTerminal(
            ServerLevel level, UUID player, ResourceLocation questId, String reason) {
        SceneSavedData data = SceneSavedData.get(level);
        UUID origin = ORIGINATING_SCENE.get();
        for (SceneInstance scene : data.active()) {
            if (scene.id().equals(origin)
                    || !questId.equals(scene.owningQuestId())
                    || !ownedBy(scene, player)) continue;
            if ("completed".equals(reason) && scene.state() == SceneState.COMPLETED) continue;
            var definition = SceneResources.scene(level.getServer(), scene.sceneId()).orElse(null);
            SceneTransitionService.cancel(
                    data,
                    scene,
                    definition,
                    "quest_" + reason,
                    "owning quest became terminal: " + reason,
                    level.getGameTime());
        }
    }

    public static <T> T withOriginatingScene(
            UUID sceneId, java.util.function.Supplier<T> operation) {
        UUID previous = ORIGINATING_SCENE.get();
        ORIGINATING_SCENE.set(sceneId);
        try {
            return operation.get();
        } finally {
            if (previous == null) ORIGINATING_SCENE.remove();
            else ORIGINATING_SCENE.set(previous);
        }
    }

    public static boolean isOriginatingScene(UUID sceneId) {
        return sceneId != null && sceneId.equals(ORIGINATING_SCENE.get());
    }

    public static void onActorDeath(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        SceneSavedData data = SceneSavedData.get(level);
        for (SceneInstance scene : data.active())
            for (var entry : scene.actorBindings().entrySet())
                if (entity.getUUID().equals(entry.getValue().entityId())) {
                    scene.replaceBinding(
                            entry.getKey(),
                            entry.getValue().withState(SceneActorBinding.BindingState.DEAD));
                    data.changed();
                    SceneRuntime.wake(level.getServer(), scene);
                }
    }

    public static void onEntityReturn(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        SceneSavedData data = SceneSavedData.get(level);
        for (SceneInstance scene : data.active())
            for (var entry : scene.actorBindings().entrySet())
                if (entity.getUUID().equals(entry.getValue().entityId())) {
                    SceneActorBinding observed =
                            entry.getValue()
                                    .withObservation(
                                            level.dimension().location(),
                                            entity.blockPosition(),
                                            true);
                    if (entity instanceof Villager villager
                            && VillagerDownedService.isDowned(villager))
                        observed = observed.withState(SceneActorBinding.BindingState.DOWNED);
                    scene.replaceBinding(entry.getKey(), observed);
                    data.changed();
                    SceneRuntime.wake(level.getServer(), scene);
                }
    }

    public static Set<ResourceLocation> protectingScenes(ServerLevel level, Villager villager) {
        Set<ResourceLocation> result = new LinkedHashSet<>();
        SceneSavedData data = SceneSavedData.get(level);
        for (SceneInstance scene : data.active()) {
            var definition = SceneResources.scene(level.getServer(), scene.sceneId()).orElse(null);
            if (definition == null) continue;
            for (var entry : scene.actorBindings().entrySet()) {
                SceneActorDeclaration declaration = definition.actors().get(entry.getKey());
                if (declaration != null
                        && declaration.lethalDamagePolicy()
                                == SceneActorDeclaration.LethalDamagePolicy.DOWNED
                        && villager.getUUID().equals(entry.getValue().entityId()))
                    result.add(scene.sceneId());
            }
        }
        return Set.copyOf(result);
    }

    public static void onActorDowned(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) return;
        SceneSavedData data = SceneSavedData.get(level);
        for (SceneInstance scene : data.active()) {
            var definition = SceneResources.scene(level.getServer(), scene.sceneId()).orElse(null);
            if (definition == null) continue;
            boolean changed = false;
            for (var entry : scene.actorBindings().entrySet()) {
                SceneActorDeclaration declaration = definition.actors().get(entry.getKey());
                SceneActorBinding binding = entry.getValue();
                if (declaration == null
                        || declaration.lethalDamagePolicy()
                                != SceneActorDeclaration.LethalDamagePolicy.DOWNED
                        || !villager.getUUID().equals(binding.entityId())) continue;
                scene.replaceBinding(
                        entry.getKey(),
                        binding.withObservation(
                                        level.dimension().location(),
                                        villager.blockPosition(),
                                        true)
                                .withState(SceneActorBinding.BindingState.DOWNED));
                data.audit(
                        new com.jvn.villagerretaliation.scene.runtime.SceneAuditEntry(
                                scene.id(),
                                entry.getKey(),
                                binding.state().name(),
                                SceneActorBinding.BindingState.DOWNED.name(),
                                "actor_downed",
                                level.getGameTime(),
                                "downed_service"));
                changed = true;
            }
            if (changed) {
                data.changed();
                SceneRuntime.wake(level.getServer(), scene);
            }
        }
    }

    public static void onActorRecovered(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) return;
        SceneSavedData data = SceneSavedData.get(level);
        for (SceneInstance scene : data.active()) {
            boolean changed = false;
            for (var entry : scene.actorBindings().entrySet()) {
                SceneActorBinding binding = entry.getValue();
                if (binding.state() != SceneActorBinding.BindingState.DOWNED
                        || !villager.getUUID().equals(binding.entityId())) continue;
                scene.replaceBinding(
                        entry.getKey(),
                        binding.withObservation(
                                level.dimension().location(), villager.blockPosition(), true));
                data.audit(
                        new com.jvn.villagerretaliation.scene.runtime.SceneAuditEntry(
                                scene.id(),
                                entry.getKey(),
                                SceneActorBinding.BindingState.DOWNED.name(),
                                SceneActorBinding.BindingState.LIVE.name(),
                                "actor_recovered",
                                level.getGameTime(),
                                "downed_service"));
                changed = true;
            }
            if (changed) {
                if (scene.state() == SceneState.BLOCKED
                        && "actor_downed".equals(scene.failureCode())) scene.retry();
                data.changed();
                SceneRuntime.wake(level.getServer(), scene);
            }
        }
    }

    public static void onPlayerConnection(ServerPlayer player) {
        SceneSavedData data = SceneSavedData.get(player.serverLevel());
        for (SceneInstance scene : data.active())
            if (ownedBy(scene, player.getUUID())) SceneRuntime.wake(player.getServer(), scene);
    }

    public static void onPartyMembershipChanged(ServerLevel level, UUID partyId) {
        SceneSavedData data = SceneSavedData.get(level);
        for (SceneInstance scene : data.active())
            if (partyId.equals(scene.owner().partyId()))
                SceneRuntime.wake(level.getServer(), scene);
    }

    public static void onQuestProviderRebind(
            ServerLevel level,
            UUID player,
            ResourceLocation questId,
            UUID previousProvider,
            Entity replacement,
            String reason) {
        if (level == null || questId == null || previousProvider == null || replacement == null)
            return;
        SceneSavedData data = SceneSavedData.get(level);
        for (SceneInstance scene : data.active()) {
            if (!questId.equals(scene.owningQuestId()) || !ownedBy(scene, player)) continue;
            var definition = SceneResources.scene(level.getServer(), scene.sceneId()).orElse(null);
            if (definition == null) continue;
            boolean changed = false;
            for (var entry : scene.actorBindings().entrySet()) {
                SceneActorBinding current = entry.getValue();
                SceneActorDeclaration declaration = definition.actors().get(entry.getKey());
                if (declaration == null
                        || declaration.bindingSource()
                                != SceneActorDeclaration.BindingSource.QUEST_PROVIDER
                        || !previousProvider.equals(current.entityId())) continue;
                SceneActorBinding candidate =
                        SceneActorBinding.entity(
                                entry.getKey(),
                                declaration.actorType(),
                                replacement.getUUID(),
                                current.sourceType(),
                                replacement.level().dimension().location(),
                                replacement.blockPosition(),
                                replacement.getDisplayName().getString(),
                                true);
                var rebound =
                        SceneActorBindingService.rebind(
                                declaration,
                                current,
                                candidate,
                                SceneActorBindingService.RebindKind.COMPATIBLE,
                                level.getGameTime(),
                                reason,
                                "");
                if (!rebound.accepted() || !rebound.changed()) continue;
                scene.replaceBinding(entry.getKey(), rebound.binding());
                data.audit(
                        new com.jvn.villagerretaliation.scene.runtime.SceneAuditEntry(
                                scene.id(),
                                entry.getKey(),
                                current.targetIdentity(),
                                rebound.binding().targetIdentity(),
                                reason,
                                level.getGameTime(),
                                "quest_provider_rebind"));
                changed = true;
            }
            if (changed) {
                data.changed();
                SceneRuntime.wake(level.getServer(), scene);
            }
        }
    }

    private static boolean ownedBy(SceneInstance scene, UUID player) {
        return player != null
                && (player.equals(scene.owner().playerId())
                        || scene.participants().contains(player));
    }
}
