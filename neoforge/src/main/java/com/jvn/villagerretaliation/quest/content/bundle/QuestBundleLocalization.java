package com.jvn.villagerretaliation.quest.content.bundle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Schema-aware localized-reference validation for quest-owned player-facing fields. */
public final class QuestBundleLocalization {
    private QuestBundleLocalization() {
    }

    public static Validation validateQuest(JsonObject quest, String prefix) {
        List<String> errors = new ArrayList<>();
        Set<String> references = new LinkedHashSet<>();
        if (quest == null) {
            return new Validation(Set.of(), List.of("quest.json is missing"));
        }

        JsonObject metadata = object(quest, "metadata");
        playerField(metadata, "title", "/metadata/title", prefix, references, errors);
        playerField(metadata, "description", "/metadata/description", prefix, references, errors);
        uiFields(object(quest, "ui"), "/ui", prefix, references, errors);

        JsonObject lifecycle = object(quest, "lifecycle");
        if (lifecycle != null) {
            sceneArray(array(lifecycle, "dialogue"), "/lifecycle/dialogue", prefix, references, errors);
        }

        JsonArray stages = array(quest, "stages");
        if (stages != null) {
            for (int stageIndex = 0; stageIndex < stages.size(); stageIndex++) {
                JsonObject stage = object(stages.get(stageIndex));
                if (stage == null) {
                    continue;
                }
                String stagePath = "/stages/" + stageIndex;
                uiFields(object(stage, "ui"), stagePath + "/ui", prefix, references, errors);
                JsonArray objectives = array(stage, "objectives");
                if (objectives != null) {
                    for (int objectiveIndex = 0; objectiveIndex < objectives.size(); objectiveIndex++) {
                        JsonObject objective = object(objectives.get(objectiveIndex));
                        if (objective != null) {
                            trackerFields(
                                    object(objective, "tracker"),
                                    stagePath + "/objectives/" + objectiveIndex + "/tracker",
                                    prefix,
                                    references,
                                    errors);
                        }
                    }
                }
                JsonObject scenes = object(stage, "scenes");
                if (scenes != null) {
                    for (Map.Entry<String, JsonElement> entry : scenes.entrySet()) {
                        scene(object(entry.getValue()), stagePath + "/scenes/" + entry.getKey(),
                                prefix, references, errors);
                    }
                }
                responseArray(array(stage, "responses"), stagePath + "/responses", prefix, references, errors);
            }
        }

        collectExplicitReferences(quest, prefix, references, errors, "", false);
        return new Validation(Set.copyOf(references), List.copyOf(errors));
    }

    public static Validation collectCompanion(JsonObject companion, String prefix) {
        Set<String> references = new LinkedHashSet<>();
        List<String> errors = new ArrayList<>();
        collectExplicitReferences(companion, prefix, references, errors, "", false);
        return new Validation(Set.copyOf(references), List.copyOf(errors));
    }

    private static void uiFields(
            JsonObject ui,
            String path,
            String prefix,
            Set<String> references,
            List<String> errors) {
        playerField(ui, "tracker_text", path + "/tracker_text", prefix, references, errors);
        playerField(ui, "tracker_complete_text", path + "/tracker_complete_text", prefix, references, errors);
    }

    private static void trackerFields(
            JsonObject tracker,
            String path,
            String prefix,
            Set<String> references,
            List<String> errors) {
        playerField(tracker, "text", path + "/text", prefix, references, errors);
        playerField(tracker, "complete_text", path + "/complete_text", prefix, references, errors);
    }

    private static void sceneArray(
            JsonArray scenes,
            String path,
            String prefix,
            Set<String> references,
            List<String> errors) {
        if (scenes == null) {
            return;
        }
        for (int index = 0; index < scenes.size(); index++) {
            scene(object(scenes.get(index)), path + "/" + index, prefix, references, errors);
        }
    }

    private static void scene(
            JsonObject scene,
            String path,
            String prefix,
            Set<String> references,
            List<String> errors) {
        if (scene == null) {
            return;
        }
        playerField(scene, "text", path + "/text", prefix, references, errors);
        localizedArray(array(scene, "lines"), path + "/lines", prefix, references, errors);
        JsonArray variants = array(scene, "variants");
        if (variants != null) {
            for (int index = 0; index < variants.size(); index++) {
                JsonObject variant = object(variants.get(index));
                playerField(variant, "text", path + "/variants/" + index + "/text", prefix, references, errors);
            }
        }
        responseArray(array(scene, "responses"), path + "/responses", prefix, references, errors);
    }

    private static void responseArray(
            JsonArray responses,
            String path,
            String prefix,
            Set<String> references,
            List<String> errors) {
        if (responses == null) {
            return;
        }
        for (int index = 0; index < responses.size(); index++) {
            JsonObject response = object(responses.get(index));
            playerField(response, "label", path + "/" + index + "/label", prefix, references, errors);
            playerField(response, "text", path + "/" + index + "/text", prefix, references, errors);
        }
    }

    private static void localizedArray(
            JsonArray values,
            String path,
            String prefix,
            Set<String> references,
            List<String> errors) {
        if (values == null) {
            return;
        }
        for (int index = 0; index < values.size(); index++) {
            localized(values.get(index), path + "/" + index, prefix, references, errors);
        }
    }

    private static void playerField(
            JsonObject object,
            String field,
            String path,
            String prefix,
            Set<String> references,
            List<String> errors) {
        if (object != null && object.has(field)) {
            localized(object.get(field), path, prefix, references, errors);
        }
    }

    private static void localized(
            JsonElement value,
            String path,
            String prefix,
            Set<String> references,
            List<String> errors) {
        LocalizedReference reference = LocalizedReference.read(value).orElse(null);
        if (reference == null) {
            errors.add(path + " must use a localized reference object such as {\"key\":\"#title\"}");
            return;
        }
        try {
            references.add(reference.expand(prefix));
        } catch (IllegalArgumentException exception) {
            errors.add(path + " " + exception.getMessage());
        }
    }

    private static void collectExplicitReferences(
            JsonElement element,
            String prefix,
            Set<String> references,
            List<String> errors,
            String path,
            boolean localizedContext) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        LocalizedReference reference = localizedContext
                ? LocalizedReference.read(element).orElse(null)
                : null;
        if (reference != null) {
            try {
                references.add(reference.expand(prefix));
            } catch (IllegalArgumentException exception) {
                errors.add((path.isBlank() ? "/" : path) + " " + exception.getMessage());
            }
            return;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int index = 0; index < array.size(); index++) {
                collectExplicitReferences(
                        array.get(index), prefix, references, errors,
                        path + "/" + index, localizedContext);
            }
        } else if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                boolean localized = localizedContext
                        || isPlayerFacingField(entry.getKey());
                collectExplicitReferences(
                        entry.getValue(), prefix, references, errors,
                        path + "/" + entry.getKey(), localized);
            }
        }
    }

    private static boolean isPlayerFacingField(String field) {
        return switch (field) {
            case "title", "description", "label", "text", "lines",
                    "tracker_text", "tracker_complete_text", "complete_text",
                    "custom_name", "trophy_name", "boss_bar_title" -> true;
            default -> false;
        };
    }

    private static JsonObject object(JsonObject parent, String field) {
        return parent == null ? null : object(parent.get(field));
    }

    private static JsonObject object(JsonElement value) {
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject parent, String field) {
        JsonElement value = parent == null ? null : parent.get(field);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    public record Validation(Set<String> references, List<String> errors) {
        public boolean valid() {
            return this.errors.isEmpty();
        }
    }
}
