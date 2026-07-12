package com.jvn.villagerretaliation.scene.model;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public record CompiledScene(
        ResourceLocation id,
        int definitionVersion,
        String definitionHash,
        SceneResource.OwnershipMode ownership,
        Map<String, String> metadata,
        Map<String, SceneActorDeclaration> actors,
        String entryStep,
        Map<String, CompiledStep> steps,
        SceneResource.TransitionPolicy failurePolicy,
        SceneResource.TransitionPolicy cancellationPolicy,
        SceneResource.CleanupPolicy cleanupPolicy,
        long timeoutTicks
) {
    public CompiledScene {
        if (id == null || definitionHash == null || definitionHash.isBlank() || entryStep == null || entryStep.isBlank()) {
            throw new IllegalArgumentException("compiled scene requires id, hash, and entry step");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        actors = immutableByKey(actors);
        steps = immutableByKey(steps);
    }

    public Optional<CompiledStep> step(String id) {
        return Optional.ofNullable(this.steps.get(id));
    }

    private static <T> Map<String, T> immutableByKey(Map<String, T> values) {
        Map<String, T> ordered = new LinkedHashMap<>();
        if (values != null) values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return Map.copyOf(ordered);
    }

    public record CompiledStep(
            String id,
            ResourceLocation type,
            java.util.List<String> actors,
            JsonObject parameters,
            Map<String, String> transitions,
            String failureStep,
            boolean immediate,
            boolean terminal
    ) {
        public CompiledStep {
            actors = actors == null ? java.util.List.of() : java.util.List.copyOf(actors);
            parameters = parameters == null ? new JsonObject() : parameters.deepCopy();
            transitions = transitions == null ? Map.of() : Map.copyOf(transitions);
            failureStep = failureStep == null ? "" : failureStep;
        }
    }
}
