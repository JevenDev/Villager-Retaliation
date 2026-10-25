package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class EditorSceneDraft {
    static final String FAILURE = "(failure policy)";
    record Step(String id, String pointer, String type, JsonObject value, Map<String, String> routes) { }
    record Trace(String current, List<String> visited, String status) { }

    static boolean supports(JsonObject root) { return scene(root) || DialogueGraph.string(root, "schema").equals("villagerretaliation:encounter/v1"); }
    static boolean scene(JsonObject root) { return DialogueGraph.string(root, "schema").equals("villagerretaliation:scene/v1"); }

    static List<Step> steps(JsonObject root) {
        var result = new ArrayList<Step>();
        var steps = DialogueGraph.array(root, "steps");
        for (int i = 0; i < steps.size(); i++) if (steps.get(i).isJsonObject()) {
            var value = steps.get(i).getAsJsonObject();
            var routes = new LinkedHashMap<String, String>();
            EditorSchema.object(value.get("transitions")).entrySet().stream().filter(entry -> entry.getValue().isJsonPrimitive())
                    .forEach(entry -> routes.put(entry.getKey(), entry.getValue().getAsString()));
            String next = DialogueGraph.string(value, "next"), failure = DialogueGraph.string(value, "failure_step");
            if (!next.isBlank()) routes.putIfAbsent("success", next);
            if (!failure.isBlank()) routes.put(FAILURE, failure);
            result.add(new Step(DialogueGraph.string(value, "id"), "/steps/" + i, DialogueGraph.string(value, "type"), value, Map.copyOf(routes)));
        }
        return List.copyOf(result);
    }

    static String unique(JsonArray values, String field, String prefix) {
        var used = new java.util.HashSet<String>();
        values.forEach(value -> { if (value.isJsonObject()) used.add(DialogueGraph.string(value.getAsJsonObject(), field)); });
        for (int i = 1; ; i++) if (!used.contains(prefix + i)) return prefix + i;
    }

    static String addStep(com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace workspace, AuthoringDocument document, String type) {
        if (!type.equals("villagerretaliation:dialogue")) return addStep(document, type);
        var draft = new AuthoringDocument(document.kind(), document.resourcePath(), document.json());
        String id = addStep(draft, type);
        int directory = document.resourcePath().lastIndexOf("/scenes/");
        if (directory < 0) throw new IllegalArgumentException("Save the scene inside a quest bundle scenes folder first");
        String path = document.resourcePath().substring(0, directory) + "/locales/en_us.json";
        var locale = workspace.documents().stream().filter(value -> value.resourcePath().equals(path))
                .map(AuthoringDocument::json).findFirst().orElseGet(JsonObject::new);
        var messages = locale.has("messages") ? locale.getAsJsonObject("messages") : locale;
        String key = document.id() + "." + id + ".text";
        while (messages.has(key)) key += "_new";
        messages.addProperty(key, "Hello, {player}!");
        var changed = draft.json();
        var step = steps(changed).stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
        var reference = new JsonObject(); reference.addProperty("key", key);
        step.value().getAsJsonObject("data").add("text", reference);
        workspace.editWithLocales("Add dialogue step", document, changed, Map.of(path, locale));
        return id;
    }

    static String addStep(AuthoringDocument document, String type) {
        String id = unique(DialogueGraph.array(document.json(), "steps"), "id", "step_");
        document.edit("Add scene step", root -> {
            var step = new JsonObject(); step.addProperty("id", id); step.addProperty("type", type);
            var data = new JsonObject();
            switch (type) {
                case "villagerretaliation:wait_ticks" -> data.addProperty("ticks", 20);
                case "villagerretaliation:dialogue" -> data.addProperty("text", "Hello, {player}!");
                case "villagerretaliation:move_actor", "villagerretaliation:face_position" -> {
                    data.addProperty("actor", firstActor(root)); data.addProperty("dimension", "minecraft:overworld");
                    data.addProperty("x", 0); data.addProperty("y", 64); data.addProperty("z", 0);
                    if (type.endsWith(":move_actor")) { data.addProperty("speed", 1); data.addProperty("arrival_radius", 2); }
                }
                case "villagerretaliation:face_actor" -> { data.addProperty("actor", firstActor(root)); data.addProperty("target_actor", firstActor(root)); }
                case "villagerretaliation:start_encounter" -> { data.addProperty("template", ""); data.addProperty("anchor_actor", firstActor(root)); }
                case "villagerretaliation:wait_encounter", "villagerretaliation:cleanup_encounter", "villagerretaliation:cancel_encounter" -> data.addProperty("encounter_step", steps(root).stream().filter(candidate -> candidate.type().endsWith(":start_encounter")).map(Step::id).findFirst().orElse(""));
                case "villagerretaliation:wait_condition", "villagerretaliation:scene_branch" -> data.add("conditions", new JsonArray());
                case "villagerretaliation:action_batch" -> data.add("actions", new JsonArray());
                default -> { }
            }
            if (!data.isEmpty()) step.add("data", data);
            root.getAsJsonArray("steps").add(step);
        });
        return id;
    }

    private static String firstActor(JsonObject root) {
        return DialogueGraph.array(root, "actors").asList().stream().filter(JsonElement::isJsonObject)
                .map(value -> DialogueGraph.string(value.getAsJsonObject(), "alias")).findFirst().orElse("");
    }

    static void renameStep(AuthoringDocument document, String id, String replacement) {
        identifier(replacement);
        var steps = steps(document.json());
        if (steps.stream().noneMatch(step -> step.id().equals(id))) throw new IllegalArgumentException("Scene step no longer exists");
        if (!id.equals(replacement) && steps.stream().anyMatch(step -> step.id().equals(replacement))) throw new IllegalArgumentException("Step ID already exists: " + replacement);
        document.edit("Rename scene step and links", root -> {
            var step = steps(root).stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
            step.value().addProperty("id", replacement);
            redirect(root, id, replacement);
        });
    }

    private static void redirect(JsonObject root, String id, String replacement) {
        if (DialogueGraph.string(root, "entry_step").equals(id)) root.addProperty("entry_step", replacement);
        for (var step : steps(root)) {
            for (String field : List.of("next", "failure_step")) if (DialogueGraph.string(step.value(), field).equals(id)) step.value().addProperty(field, replacement);
            var data = EditorSchema.object(step.value().get("data"));
            if (DialogueGraph.string(data, "encounter_step").equals(id)) data.addProperty("encounter_step", replacement);
            var transitions = EditorSchema.object(step.value().get("transitions"));
            for (var entry : transitions.entrySet()) if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsString().equals(id)) transitions.addProperty(entry.getKey(), replacement);
        }
    }

    static void removeStep(AuthoringDocument document, String id, String replacement) {
        var root = document.json(); var steps = steps(root);
        var selected = steps.stream().filter(step -> step.id().equals(id)).findFirst().orElseThrow();
        if (steps.size() <= 1) throw new IllegalArgumentException("Keep at least one scene step");
        boolean referenced = DialogueGraph.string(root, "entry_step").equals(id) || steps.stream().anyMatch(step -> !step.id().equals(id)
                && (step.routes().containsValue(id) || DialogueGraph.string(EditorSchema.object(step.value().get("data")), "encounter_step").equals(id)));
        if (referenced && (replacement.equals(id) || steps.stream().noneMatch(step -> step.id().equals(replacement))))
            throw new IllegalArgumentException("Choose a replacement step for incoming links and the entry step");
        document.edit("Remove scene step", value -> {
            if (referenced) redirect(value, id, replacement);
            JsonPointers.remove(value, selected.pointer());
        });
    }

    static String duplicateStep(AuthoringDocument document, String id) {
        var root = document.json(); var steps = DialogueGraph.array(root, "steps");
        var source = steps(root).stream().filter(step -> step.id().equals(id)).findFirst().orElseThrow().value().deepCopy();
        String added = unique(steps, "id", id + "_copy_"); source.addProperty("id", added);
        document.edit("Duplicate scene step", value -> value.getAsJsonArray("steps").add(source));
        return added;
    }

    static void route(AuthoringDocument document, String id, String outcome, String target) {
        if (!outcome.equals(FAILURE) && !outcome.matches("[a-zA-Z0-9_.:-]+")) throw new IllegalArgumentException("Enter an outcome name");
        var steps = steps(document.json());
        if (!target.isEmpty() && steps.stream().noneMatch(step -> step.id().equals(target))) throw new IllegalArgumentException("Choose an existing step");
        var selected = steps.stream().filter(step -> step.id().equals(id)).findFirst().orElseThrow();
        document.edit("Connect scene outcome", root -> {
            var step = JsonPointers.get(root, selected.pointer()).getAsJsonObject();
            if (outcome.equals(FAILURE)) {
                if (target.isEmpty()) step.remove("failure_step"); else step.addProperty("failure_step", target);
            } else {
                if (outcome.equals("success")) step.remove("next");
                var transitions = EditorSchema.object(step.get("transitions"));
                if (target.isEmpty()) transitions.remove(outcome); else transitions.addProperty(outcome, target);
                if (transitions.isEmpty()) step.remove("transitions"); else step.add("transitions", transitions);
            }
        });
    }

    static Trace start(JsonObject root) { return new Trace(DialogueGraph.string(root, "entry_step"), List.of(), "Ready"); }

    static Trace advance(JsonObject root, Trace trace, String outcome) {
        var current = steps(root).stream().filter(step -> step.id().equals(trace.current())).findFirst().orElse(null);
        if (current == null) return new Trace("", trace.visited(), "Missing step: " + trace.current());
        if (trace.visited().size() >= 256) return new Trace("", trace.visited(), "Stopped after 256 steps. Check for a loop.");
        var visited = new ArrayList<>(trace.visited()); visited.add(current.id());
        if (current.type().endsWith(":scene_complete")) return new Trace("", List.copyOf(visited), "Scene completes");
        if (outcome.equals(FAILURE) || current.type().endsWith(":scene_fail")) {
            String policy = DialogueGraph.string(root, "failure_policy");
            if (!policy.equals("run_failure_step")) return new Trace("", List.copyOf(visited),
                    policy.equals("block_for_repair") ? "Scene blocks for repair" : policy.equals("cancel_scene") ? "Scene cancels" : "Scene fails");
            outcome = FAILURE;
        }
        String next = current.routes().getOrDefault(outcome, "");
        return new Trace(next, List.copyOf(visited), next.isEmpty() ? "No " + outcome + " route from " + current.id() : "Following " + outcome);
    }

    static String addActor(AuthoringDocument document) {
        String alias = unique(DialogueGraph.array(document.json(), "actors"), "alias", "actor_");
        document.edit("Add scene actor", root -> {
            var actor = new JsonObject(); actor.addProperty("alias", alias); actor.addProperty("type", "villagerretaliation:villager");
            actor.addProperty("binding_source", "unbound"); actor.addProperty("replacement_policy", "operator_rebindable");
            actor.addProperty("required", true); root.getAsJsonArray("actors").add(actor);
        });
        return alias;
    }

    static void renameActor(AuthoringDocument document, String alias, String replacement) {
        identifier(replacement);
        var actors = DialogueGraph.array(document.json(), "actors");
        if (!alias.equals(replacement) && actors.asList().stream().filter(JsonElement::isJsonObject)
                .anyMatch(value -> DialogueGraph.string(value.getAsJsonObject(), "alias").equals(replacement))) throw new IllegalArgumentException("Actor alias already exists");
        document.edit("Rename scene actor and references", root -> {
            var actor = DialogueGraph.array(root, "actors").asList().stream().filter(JsonElement::isJsonObject)
                    .map(JsonElement::getAsJsonObject).filter(value -> DialogueGraph.string(value, "alias").equals(alias)).findFirst().orElseThrow();
            actor.addProperty("alias", replacement);
            for (var step : steps(root)) renameActorReferences(step.value(), alias, replacement);
        });
    }

    private static void renameActorReferences(JsonElement value, String alias, String replacement) {
        if (value.isJsonObject()) for (var entry : value.getAsJsonObject().entrySet()) {
            if (Set.of("actor", "target_actor", "speaker_actor", "anchor_actor").contains(entry.getKey()) && entry.getValue().isJsonPrimitive()
                    && entry.getValue().getAsString().equals(alias)) value.getAsJsonObject().addProperty(entry.getKey(), replacement);
            else if (entry.getKey().equals("actors") && entry.getValue().isJsonArray()) {
                var list = entry.getValue().getAsJsonArray();
                for (int i = 0; i < list.size(); i++) if (list.get(i).isJsonPrimitive() && list.get(i).getAsString().equals(alias)) list.set(i, new com.google.gson.JsonPrimitive(replacement));
            } else if (!Set.of("text", "line", "lines", "metadata").contains(entry.getKey())) renameActorReferences(entry.getValue(), alias, replacement);
        } else if (value.isJsonArray()) value.getAsJsonArray().forEach(child -> renameActorReferences(child, alias, replacement));
    }

    static void removeActor(AuthoringDocument document, String pointer) {
        var root = document.json();
        String alias = DialogueGraph.string(JsonPointers.get(root, pointer).getAsJsonObject(), "alias");
        for (var step : steps(root)) {
            var copy = step.value().deepCopy(); renameActorReferences(copy, alias, alias + "_removed");
            if (!copy.equals(step.value())) throw new IllegalArgumentException("Remove references to actor " + alias + " from step " + step.id() + " first");
        }
        document.edit("Remove scene actor", value -> JsonPointers.remove(value, pointer));
    }

    static String move(AuthoringDocument document, String pointer, int direction) {
        int split = pointer.lastIndexOf('/');
        String parent = pointer.substring(0, split);
        int index = Integer.parseInt(pointer.substring(split + 1)), target = index + direction;
        var values = JsonPointers.get(document.json(), parent).getAsJsonArray();
        if (target < 0 || target >= values.size()) throw new IllegalArgumentException("Entry is already at the end of this list");
        document.edit("Move authored entry", root -> {
            var list = JsonPointers.get(root, parent).getAsJsonArray();
            var previous = list.get(target); list.set(target, list.get(index)); list.set(index, previous);
        });
        return parent + "/" + target;
    }

    static void bindEntity(AuthoringDocument document, String pointer, String type, String uuid) {
        java.util.UUID.fromString(uuid);
        document.edit("Bind scene actor to target", root -> {
            var actor = JsonPointers.get(root, pointer).getAsJsonObject();
            actor.addProperty("type", type); actor.addProperty("binding_source", "uuid"); actor.addProperty("binding", uuid);
            actor.remove("source");
        });
    }

    static void bindPosition(AuthoringDocument document, String pointer, String dimension, int x, int y, int z) {
        document.edit("Set scene actor position", root -> {
            var actor = JsonPointers.get(root, pointer).getAsJsonObject();
            actor.addProperty("type", "villagerretaliation:position"); actor.addProperty("binding_source", "marker");
            actor.remove("source"); actor.remove("binding");
            var filters = EditorSchema.object(actor.get("filters")); filters.addProperty("dimension", dimension);
            filters.addProperty("x", Integer.toString(x)); filters.addProperty("y", Integer.toString(y)); filters.addProperty("z", Integer.toString(z));
            actor.add("filters", filters);
        });
    }

    static void makeWaves(AuthoringDocument document) {
        if (!document.json().has("members") || document.json().has("waves") || document.json().has("variants")) throw new IllegalArgumentException("Choose an encounter with a single roster");
        var source = document.json();
        if (DialogueGraph.string(source, "spawn_mode").equals("raid_waves") && source.has("wave_count") && source.get("wave_count").getAsInt() > 1)
            throw new IllegalArgumentException("This roster repeats across waves. Keep its repeated-wave settings or author a separate encounter.");
        document.edit("Convert roster to first wave", root -> {
            var wave = new JsonObject(); wave.addProperty("id", "repeat_1"); wave.add("members", root.remove("members"));
            wave.addProperty("delay_ticks", 0); wave.addProperty("trigger", root.has("wave_trigger") ? root.get("wave_trigger").getAsString() : "all_defeated");
            var waves = new JsonArray(); waves.add(wave); root.add("waves", waves); root.addProperty("spawn_mode", "raid_waves");
        });
    }

    static String addWave(AuthoringDocument document) {
        var waves = DialogueGraph.array(document.json(), "waves");
        if (waves.isEmpty() || waves.size() >= 32) throw new IllegalArgumentException("Encounters support 1 to 32 authored waves");
        String id = unique(waves, "id", "wave_");
        document.edit("Add encounter wave", root -> {
            var wave = new JsonObject(); wave.addProperty("id", id); wave.addProperty("delay_ticks", 20); wave.addProperty("trigger", "all_defeated");
            var members = new JsonArray(); members.add(member(unique(allMembers(root), "id", "member_"))); wave.add("members", members); root.getAsJsonArray("waves").add(wave);
        });
        return id;
    }

    static String addMember(AuthoringDocument document, String pointer) {
        var members = JsonPointers.get(document.json(), pointer).getAsJsonArray();
        if (members.size() >= 64) throw new IllegalArgumentException("A roster supports up to 64 member entries");
        String id = unique(allMembers(document.json()), "id", "member_");
        document.edit("Add encounter member", root -> JsonPointers.get(root, pointer).getAsJsonArray().add(member(id)));
        return id;
    }

    private static JsonArray allMembers(JsonObject root) {
        var result = DialogueGraph.array(root, "members").deepCopy();
        for (var wave : DialogueGraph.array(root, "waves")) if (wave.isJsonObject())
            DialogueGraph.array(wave.getAsJsonObject(), "members").forEach(result::add);
        return result;
    }

    private static JsonObject member(String id) {
        var member = new JsonObject(); member.addProperty("id", id); member.addProperty("entity", "minecraft:zombie"); member.addProperty("count", 1);
        return member;
    }

    static void addSpawn(AuthoringDocument document, String dimension, int x, int y, int z) {
        if (document.json().has("variants")) throw new IllegalArgumentException("Edit spawn points in the referenced encounter variant");
        var points = DialogueGraph.array(document.json(), "spawn_points");
        if (points.size() >= 64) throw new IllegalArgumentException("An encounter supports up to 64 spawn points");
        String id = unique(points, "id", "spawn_");
        document.edit("Add encounter spawn point", root -> {
            var point = new JsonObject(); point.addProperty("id", id); point.addProperty("dimension", dimension);
            point.addProperty("x", x); point.addProperty("y", y); point.addProperty("z", z);
            var values = DialogueGraph.array(root, "spawn_points"); values.add(point); root.add("spawn_points", values);
            if (DialogueGraph.string(root, "spawn_mode").equals("near_player")) root.addProperty("spawn_mode", "group");
        });
    }

    private static void identifier(String value) {
        if (!value.matches("[a-z][a-z0-9_.-]{0,63}")) throw new IllegalArgumentException("Use a lowercase identifier starting with a letter");
    }
}
