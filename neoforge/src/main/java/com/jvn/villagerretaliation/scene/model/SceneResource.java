package com.jvn.villagerretaliation.scene.model;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public record SceneResource(
        ResourceLocation id,
        ResourceLocation schema,
        Map<String, String> metadata,
        OwnershipMode ownership,
        List<SceneActorDeclaration> actors,
        String entryStep,
        List<StepResource> steps,
        TransitionPolicy failurePolicy,
        TransitionPolicy cancellationPolicy,
        CleanupPolicy cleanupPolicy,
        long timeoutTicks,
        int definitionVersion,
        JsonObject canonicalSource
) {
    public SceneResource {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        ownership = ownership == null ? OwnershipMode.PLAYER : ownership;
        actors = actors == null ? List.of() : List.copyOf(actors);
        entryStep = entryStep == null ? "" : entryStep;
        steps = steps == null ? List.of() : List.copyOf(steps);
        failurePolicy = failurePolicy == null ? TransitionPolicy.FAIL_SCENE : failurePolicy;
        cancellationPolicy = cancellationPolicy == null ? TransitionPolicy.CANCEL_SCENE : cancellationPolicy;
        cleanupPolicy = cleanupPolicy == null ? CleanupPolicy.OWNED_ENTITIES : cleanupPolicy;
        timeoutTicks = Math.max(0L, timeoutTicks);
        definitionVersion = Math.max(1, definitionVersion);
        canonicalSource = canonicalSource == null ? new JsonObject() : canonicalSource.deepCopy();
    }

    public enum OwnershipMode { PLAYER, PARTY, QUEST_INSTANCE, WORLD }
    public enum TransitionPolicy { FAIL_SCENE, CANCEL_SCENE, BLOCK_FOR_REPAIR, RUN_FAILURE_STEP }
    public enum CleanupPolicy { NONE, OWNED_ENTITIES, ENCOUNTERS, ALL_OWNED, PRESERVE_WORLD }

    public record StepResource(
            String id,
            ResourceLocation type,
            List<String> actors,
            JsonObject data,
            Map<String, String> transitions,
            String failureStep
    ) {
        public StepResource {
            id = id == null ? "" : id;
            actors = actors == null ? List.of() : List.copyOf(actors);
            data = data == null ? new JsonObject() : data.deepCopy();
            transitions = transitions == null ? Map.of() : Map.copyOf(transitions);
            failureStep = failureStep == null ? "" : failureStep;
        }
    }
}
