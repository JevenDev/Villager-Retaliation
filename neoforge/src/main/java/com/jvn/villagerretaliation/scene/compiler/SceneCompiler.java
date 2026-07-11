package com.jvn.villagerretaliation.scene.compiler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.api.VillagerRetaliationRegistries;
import com.jvn.villagerretaliation.api.registry.RuntimeTypeDescriptor;
import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration;
import com.jvn.villagerretaliation.scene.model.CompiledScene;
import com.jvn.villagerretaliation.scene.model.SceneResource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public final class SceneCompiler {
    private static final Set<String> TERMINAL_TYPES = Set.of("scene_complete", "scene_fail");
    private static final Set<String> SUSPENDING_TYPES = Set.of("wait_ticks", "wait_condition", "move_actor", "dialogue", "wait_encounter");

    private SceneCompiler() {
    }

    public static CompileResult compile(SceneResource resource) {
        VillagerRetaliationRegistries.freezeForDatapackCompilation();
        List<SceneDiagnostic> diagnostics = new ArrayList<>();
        if (resource == null) return new CompileResult(null, List.of(error("scene.missing", "", "scene resource is missing")));

        Map<String, SceneActorDeclaration> actors = new LinkedHashMap<>();
        for (SceneActorDeclaration actor : resource.actors()) {
            if (actors.putIfAbsent(actor.alias(), actor) != null) {
                diagnostics.add(error("scene.actor.duplicate", "actors." + actor.alias(), "duplicate actor alias " + actor.alias()));
                continue;
            }
            RuntimeTypeDescriptor descriptor = VillagerRetaliationRegistries.ACTOR_TYPES.get(actor.actorType()).orElse(null);
            if (descriptor == null) {
                diagnostics.add(error("scene.actor.unknown_type", "actors." + actor.alias(), "unknown actor type " + actor.actorType()));
            } else {
                Set<ResourceLocation> available = new LinkedHashSet<>(descriptor.liveCapabilities());
                available.addAll(descriptor.snapshotCapabilities());
                if (!available.containsAll(actor.requiredCapabilities())) {
                    Set<ResourceLocation> missing = new LinkedHashSet<>(actor.requiredCapabilities());
                    missing.removeAll(available);
                    diagnostics.add(error("scene.actor.capability", "actors." + actor.alias(), "actor type "
                            + actor.actorType() + " lacks capabilities " + missing));
                }
            }
        }

        Map<String, CompiledScene.CompiledStep> steps = new LinkedHashMap<>();
        for (SceneResource.StepResource step : resource.steps()) {
            if (steps.containsKey(step.id())) {
                diagnostics.add(error("scene.step.duplicate", "steps." + step.id(), "duplicate stable step id " + step.id()));
                continue;
            }
            RuntimeTypeDescriptor descriptor = VillagerRetaliationRegistries.SCENE_STEPS.get(step.type()).orElse(null);
            if (descriptor == null) {
                diagnostics.add(error("scene.step.unknown_type", "steps." + step.id(), "unknown scene step type " + step.type()));
                continue;
            }
            for (String alias : step.actors()) if (!actors.containsKey(alias)) {
                diagnostics.add(error("scene.step.actor", "steps." + step.id() + ".actors", "unknown actor alias " + alias));
            }
            validateEncounterReference(step, diagnostics);
            try {
                Object parsed = descriptor.parser().parse(step.data());
                for (String message : descriptor.validator().validate(parsed)) {
                    diagnostics.add(error("scene.step.extension", "steps." + step.id(), message));
                }
            } catch (IllegalArgumentException exception) {
                diagnostics.add(error("scene.step.parse", "steps." + step.id(), exception.getMessage()));
            }
            String localType = step.type().getPath();
            steps.put(step.id(), new CompiledScene.CompiledStep(step.id(), step.type(), step.actors(), step.data(),
                    step.transitions(), step.failureStep(), !SUSPENDING_TYPES.contains(localType), TERMINAL_TYPES.contains(localType)));
        }

        if (!steps.containsKey(resource.entryStep())) {
            diagnostics.add(error("scene.entry.unknown", "entry_step", "entry step does not exist: " + resource.entryStep()));
        }
        for (CompiledScene.CompiledStep step : steps.values()) {
            for (Map.Entry<String, String> transition : step.transitions().entrySet()) {
                if (!steps.containsKey(transition.getValue())) diagnostics.add(error("scene.transition.unknown",
                        "steps." + step.id() + ".transitions." + transition.getKey(),
                        "transition references unknown step " + transition.getValue()));
            }
            if (!step.failureStep().isBlank() && !steps.containsKey(step.failureStep())) {
                diagnostics.add(error("scene.failure.unknown", "steps." + step.id() + ".failure_step",
                        "failure transition references unknown step " + step.failureStep()));
            }
            if (!step.terminal() && step.transitions().isEmpty() && step.failureStep().isBlank()) {
                diagnostics.add(error("scene.path.dead_end", "steps." + step.id(), "non-terminal step has no transition"));
            }
        }

        Set<String> reachable = reachable(resource.entryStep(), steps);
        for (String id : steps.keySet()) if (!reachable.contains(id)) {
            diagnostics.add(warning("scene.step.unreachable", "steps." + id, "step is unreachable from entry_step"));
        }
        if (reachable.stream().map(steps::get).noneMatch(step -> step != null && step.terminal())) {
            diagnostics.add(error("scene.path.no_terminal", "entry_step", "no terminal scene_complete or scene_fail step is reachable"));
        }
        detectImmediateCycles(reachable, steps, diagnostics);

        if (diagnostics.stream().anyMatch(value -> value.severity() == SceneDiagnostic.Severity.ERROR)) {
            return new CompileResult(null, List.copyOf(diagnostics));
        }
        CompiledScene compiled = new CompiledScene(resource.id(), resource.definitionVersion(), hash(resource.canonicalSource()),
                resource.ownership(), resource.metadata(), actors, resource.entryStep(), steps, resource.failurePolicy(),
                resource.cancellationPolicy(), resource.cleanupPolicy(), resource.timeoutTicks());
        return new CompileResult(compiled, List.copyOf(diagnostics));
    }

    private static void validateEncounterReference(SceneResource.StepResource step, List<SceneDiagnostic> diagnostics) {
        if (!Set.of("start_encounter", "wait_encounter", "cancel_encounter", "cleanup_encounter")
                .contains(step.type().getPath())) return;
        String value = string(step.data(), "template", "encounter_template");
        if (step.type().getPath().equals("start_encounter")) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id == null) diagnostics.add(error("scene.encounter.missing", "steps." + step.id() + ".data.template",
                    "start_encounter requires a namespaced encounter template"));
            else if (VillagerRetaliationRegistries.ENCOUNTER_TEMPLATES.get(id).isEmpty()) {
                diagnostics.add(error("scene.encounter.unknown", "steps." + step.id() + ".data.template",
                        "unknown encounter template " + id));
            }
        }
    }

    private static String string(JsonObject object, String... keys) {
        for (String key : keys) if (object.has(key) && object.get(key).isJsonPrimitive()) return object.get(key).getAsString();
        return "";
    }

    private static Set<String> reachable(String entry, Map<String, CompiledScene.CompiledStep> steps) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        if (steps.containsKey(entry)) pending.add(entry);
        while (!pending.isEmpty()) {
            String id = pending.removeFirst();
            if (!visited.add(id)) continue;
            CompiledScene.CompiledStep step = steps.get(id);
            if (step == null) continue;
            step.transitions().values().stream().sorted().forEach(pending::addLast);
            if (!step.failureStep().isBlank()) pending.addLast(step.failureStep());
        }
        return Set.copyOf(visited);
    }

    private static void detectImmediateCycles(Set<String> reachable, Map<String, CompiledScene.CompiledStep> steps,
            List<SceneDiagnostic> diagnostics) {
        Set<String> visited = new HashSet<>();
        Set<String> active = new LinkedHashSet<>();
        for (String id : reachable) detectImmediateCycle(id, steps, visited, active, diagnostics);
    }

    private static void detectImmediateCycle(String id, Map<String, CompiledScene.CompiledStep> steps, Set<String> visited,
            Set<String> active, List<SceneDiagnostic> diagnostics) {
        CompiledScene.CompiledStep step = steps.get(id);
        if (step == null || !step.immediate() || step.terminal() || visited.contains(id)) return;
        if (!active.add(id)) {
            diagnostics.add(error("scene.cycle.immediate", "steps." + id,
                    "unbounded immediate cycle detected at step " + id));
            return;
        }
        for (String target : step.transitions().values()) {
            if (active.contains(target)) diagnostics.add(error("scene.cycle.immediate", "steps." + id,
                    "unbounded immediate cycle " + active + " -> " + target));
            else detectImmediateCycle(target, steps, visited, active, diagnostics);
        }
        active.remove(id);
        visited.add(id);
    }

    public static String hash(JsonObject source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = canonical(source).toString().getBytes(StandardCharsets.UTF_8);
            return java.util.HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static JsonElement canonical(JsonElement element) {
        if (element == null || element.isJsonNull() || element.isJsonPrimitive()) return element;
        if (element.isJsonArray()) {
            JsonArray array = new JsonArray();
            element.getAsJsonArray().forEach(value -> array.add(canonical(value)));
            return array;
        }
        JsonObject object = new JsonObject();
        element.getAsJsonObject().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> object.add(entry.getKey(), canonical(entry.getValue())));
        return object;
    }

    private static SceneDiagnostic error(String code, String path, String message) {
        return new SceneDiagnostic(SceneDiagnostic.Severity.ERROR, code, path, message);
    }

    private static SceneDiagnostic warning(String code, String path, String message) {
        return new SceneDiagnostic(SceneDiagnostic.Severity.WARNING, code, path, message);
    }

    public record CompileResult(CompiledScene scene, List<SceneDiagnostic> diagnostics) {
        public boolean valid() { return scene != null; }
    }
}
