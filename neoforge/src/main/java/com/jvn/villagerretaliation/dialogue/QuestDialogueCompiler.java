package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.compiled.QuestSourcePointer;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Resource;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public final class QuestDialogueCompiler {
    private static final String TREE_ROOT = "quest_dialogue/";

    private QuestDialogueCompiler() {
    }

    public static QuestDialogueCatalog compile(QuestV2Resource resource, QuestResourceEnvelope envelope) {
        if (resource == null || envelope == null) {
            return QuestDialogueCatalog.empty();
        }
        QuestSourcePointer source = QuestSourcePointer.from(envelope);
        ResourceLocation treeId = treeId(resource.id());
        JsonObject root = new JsonObject();
        root.addProperty("id", treeId.toString());
        addDisplay(root, resource);
        root.add("metadata", metadata(resource, "", "quest_module_v2", source));

        JsonArray entries = new JsonArray();
        JsonObject nodes = new JsonObject();
        Map<QuestDialogueCatalog.BindingKey, QuestDialogueCatalog.Binding> bindings = new LinkedHashMap<>();
        Map<String, Integer> stageIndexes = stageIndexes(resource);

        addLifecycleScenes(resource, source, treeId, entries, nodes, bindings);
        for (QuestV2Resource.Stage stage : resource.stages()) {
            QuestSourcePointer stageSource = source.child(
                    "stages",
                    Integer.toString(stageIndexes.getOrDefault(stage.id(), 0)));
            addStageDialogue(resource, stage, stageSource, treeId, entries, nodes, bindings);
        }

        if (entries.size() == 0 || nodes.size() == 0) {
            return QuestDialogueCatalog.empty();
        }
        root.add("entries", entries);
        root.add("nodes", nodes);
        DialogueTreeDefinition tree = DialogueTreeResources.readGeneratedTree(source.resource(), root, treeId);
        if (tree == null) {
            DatapackDiagnostics.warnQuestV2Validation(
                    source.resource(),
                    "",
                    "generated embedded dialogue tree failed structural validation.",
                    "Check embedded dialogue scene, response, condition, and action payloads.",
                    Set.of(resource.id().toString(), treeId.toString()));
            return QuestDialogueCatalog.empty();
        }

        return new QuestDialogueCatalog(
                Map.of(tree.id(), tree),
                bindings,
                Map.of(tree.id(), source));
    }

    public static ResourceLocation treeId(ResourceLocation questId) {
        return ResourceLocation.fromNamespaceAndPath(
                questId.getNamespace(),
                TREE_ROOT + questId.getPath());
    }

    private static void addLifecycleScenes(
            QuestV2Resource resource,
            QuestSourcePointer source,
            ResourceLocation treeId,
            JsonArray entries,
            JsonObject nodes,
            Map<QuestDialogueCatalog.BindingKey, QuestDialogueCatalog.Binding> bindings) {
        for (int index = 0; index < resource.lifecycle().dialogue().size(); index++) {
            QuestV2Resource.Scene scene = resource.lifecycle().dialogue().get(index);
            if (!shouldCompile(scene)) {
                continue;
            }
            QuestSourcePointer sceneSource = source.child("lifecycle", "dialogue", Integer.toString(index));
            String nodeId = lifecycleNodeId(scene.id());
            String entryId = lifecycleEntryId(scene.id());
            addSceneNode(resource, "", scene, sceneSource, nodeId, null, nodes);
            addEntry(resource, "", entryId, titleCase(scene.id()), nodeId, sceneSource, null, entries);
            putBinding(
                    bindings,
                    new QuestDialogueCatalog.Binding(
                            resource.id(),
                            "",
                            "lifecycle:" + scene.id(),
                            scene.id(),
                            treeId,
                            entryId,
                            sceneSource));
        }
    }

    private static void addStageDialogue(
            QuestV2Resource resource,
            QuestV2Resource.Stage stage,
            QuestSourcePointer stageSource,
            ResourceLocation treeId,
            JsonArray entries,
            JsonObject nodes,
            Map<QuestDialogueCatalog.BindingKey, QuestDialogueCatalog.Binding> bindings) {
        for (QuestV2Resource.DialogueSlot slot : sortedSlots(stage.dialogueSlots())) {
            QuestSourcePointer slotSource = stageSource.child("dialogue", slot.slot());
            QuestV2Resource.Scene scene = slot.inlineScene();
            String nodeId = stageSlotNodeId(stage.id(), slot.slot());
            String sceneId = scene == null ? slot.scene() : scene.id();
            if (scene == null && !slot.scene().isBlank()) {
                scene = stage.scenes().get(slot.scene());
                nodeId = stageSceneNodeId(stage.id(), slot.scene());
            }
            if (!shouldCompile(scene)) {
                continue;
            }
            String entryId = stageSlotEntryId(stage.id(), slot.slot());
            addSceneNode(resource, stage.id(), scene, slotSource, nodeId, stage, nodes);
            addEntry(resource, stage.id(), entryId, titleCase(slot.slot()), nodeId, slotSource, slot.data(), entries);
            putBinding(
                    bindings,
                    new QuestDialogueCatalog.Binding(
                            resource.id(),
                            stage.id(),
                            slot.slot(),
                            sceneId,
                            treeId,
                            entryId,
                            slotSource));
        }

        int sortedSceneIndex = 0;
        for (QuestV2Resource.Scene scene : sortedScenes(stage.scenes())) {
            if (shouldCompile(scene)) {
                QuestSourcePointer sceneSource = stageSource.child(
                        "scenes",
                        Integer.toString(sceneSourceIndex(stage, scene.id(), sortedSceneIndex)));
                String nodeId = stageSceneNodeId(stage.id(), scene.id());
                String entryId = stageSceneEntryId(stage.id(), scene.id());
                addSceneNode(resource, stage.id(), scene, sceneSource, nodeId, stage, nodes);
                addEntry(resource, stage.id(), entryId, titleCase(scene.id()), nodeId, sceneSource, null, entries);
                putBinding(
                        bindings,
                        new QuestDialogueCatalog.Binding(
                                resource.id(),
                                stage.id(),
                                "scene:" + scene.id(),
                                scene.id(),
                                treeId,
                                entryId,
                                sceneSource));
            }
            sortedSceneIndex++;
        }

        if (!stage.responses().isEmpty()) {
            QuestSourcePointer responsesSource = stageSource.child("responses");
            String nodeId = stageResponsesNodeId(stage.id());
            String entryId = stageResponsesEntryId(stage.id());
            JsonObject node = new JsonObject();
            node.addProperty("id", nodeId);
            JsonArray lines = stringArray(List.of(stage.ui().trackerText()));
            if (lines.size() > 0) {
                node.add("lines", lines);
            }
            node.add("responses", responseArray(
                    resource,
                    stage.id(),
                    stage.responses(),
                    stage,
                    responsesSource,
                    stageResponsesEntryId(stage.id())));
            addNode(nodes, nodeId, node);
            addEntry(resource, stage.id(), entryId, titleCase(stage.id()), nodeId, responsesSource, null, entries);
            putBinding(
                    bindings,
                    new QuestDialogueCatalog.Binding(
                            resource.id(),
                            stage.id(),
                            "responses",
                            "",
                            treeId,
                            entryId,
                            responsesSource));
        }
    }

    private static void addSceneNode(
            QuestV2Resource resource,
            String stageId,
            QuestV2Resource.Scene scene,
            QuestSourcePointer sceneSource,
            String nodeId,
            QuestV2Resource.Stage stage,
            JsonObject nodes) {
        JsonObject node = new JsonObject();
        node.addProperty("id", nodeId);
        JsonArray lines = stringArray(scene.lines());
        if (lines.size() > 0) {
            node.add("lines", lines);
        }
        copyArrayIfPresent(scene.data(), node, "actions");
        copyArrayIfPresent(scene.data(), node, "conditions");
        JsonArray responses = responseArray(
                resource,
                stageId,
                scene.responses(),
                stage,
                sceneSource.child("responses"),
                stageSceneEntryId(stageId, scene.id()));
        if (responses.size() > 0) {
            node.add("responses", responses);
        }
        addNode(nodes, nodeId, node);
    }

    private static JsonArray responseArray(
            QuestV2Resource resource,
            String stageId,
            List<QuestV2Resource.Response> responses,
            QuestV2Resource.Stage stage,
            QuestSourcePointer source,
            String scenePath) {
        JsonArray array = new JsonArray();
        for (int index = 0; index < responses.size(); index++) {
            QuestV2Resource.Response response = responses.get(index);
            JsonObject object = new JsonObject();
            object.addProperty("id", response.id());
            object.addProperty("label", responseLabel(response));
            JsonArray lines = responseLines(response);
            if (lines.size() > 0) {
                object.add("lines", lines);
            }
            String request = DatapackJsonReader.readString(response.data(), "request");
            if (!request.isBlank()) {
                object.addProperty("request", request);
            }
            object.add("metadata", metadata(
                    resource,
                    stageId,
                    "quest_module_v2_response",
                    source.child(Integer.toString(index))));
            copyArrayIfPresent(response.data(), object, "conditions");
            copyResponseActionObjects(
                    object,
                    response.actions(),
                    transitionAction(resource, stageId, scenePath, response, source.child(Integer.toString(index))));
            String next = nextSceneNodeId(stage, response.transition());
            if (!next.isBlank()) {
                object.addProperty("next", next);
            }
            array.add(object);
        }
        return array;
    }

    private static String responseLabel(QuestV2Resource.Response response) {
        String explicitLabel = DatapackJsonReader.readString(response.data(), "label");
        return firstNonBlank(
                explicitLabel,
                firstNonBlank(response.label().text(), titleCase(response.id())));
    }

    private static JsonArray responseLines(QuestV2Resource.Response response) {
        JsonArray lines = stringArray(response.label().lines());
        String responseText = DatapackJsonReader.readString(response.data(), "text");
        if (!responseText.isBlank()) {
            lines.add(responseText);
        }
        return lines;
    }

    private static String nextSceneNodeId(QuestV2Resource.Stage stage, QuestV2Resource.Transition transition) {
        if (stage == null || transition == null || transition.scene().isBlank()) {
            return "";
        }
        return stage.scenes().containsKey(transition.scene()) ? stageSceneNodeId(stage.id(), transition.scene()) : "";
    }

    private static JsonObject transitionAction(
            QuestV2Resource resource,
            String stageId,
            String scenePath,
            QuestV2Resource.Response response,
            QuestSourcePointer source) {
        QuestV2Resource.Transition transition = response.transition();
        if (transition == null
                || transition.isEmpty()
                || !transition.scene().isBlank()
                || !transition.response().isBlank()) {
            return null;
        }
        JsonObject action = new JsonObject();
        action.addProperty("type", "quest_transition");
        action.addProperty("quest", resource.id().toString());
        action.addProperty("from_stage", stageId);
        action.addProperty("scene_path", scenePath);
        action.addProperty("response_id", response.id());
        action.addProperty("source_pointer", source.jsonPointer());
        if (!transition.stage().isBlank()) {
            action.addProperty("target", "stage");
            action.addProperty("target_stage", transition.stage());
        } else if (transition.complete()) {
            action.addProperty("target", "complete");
        } else if (transition.abandon()) {
            action.addProperty("target", "abandon");
        } else if (transition.fail()) {
            action.addProperty("target", "fail");
        }
        return action;
    }

    private static void addEntry(
            QuestV2Resource resource,
            String stageId,
            String entryId,
            String label,
            String nodeId,
            QuestSourcePointer source,
            JsonObject entryData,
            JsonArray entries) {
        JsonObject entry = new JsonObject();
        entry.addProperty("id", entryId);
        entry.addProperty("label", label);
        entry.addProperty("start", nodeId);
        entry.add("metadata", metadata(resource, stageId, "quest_module_v2_entry", source));
        copyArrayIfPresent(entryData, entry, "conditions");
        entries.add(entry);
    }

    private static JsonObject metadata(
            QuestV2Resource resource,
            String stageId,
            String topic,
            QuestSourcePointer source) {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("topic", topic);
        metadata.addProperty("quest", resource.id().toString());
        putString(metadata, "questline", metadataString(resource, "questline"));
        putString(metadata, "stage", stageId);
        JsonArray tags = new JsonArray();
        tags.add("generated");
        tags.add("quest_v2");
        metadata.add("tags", tags);
        metadata.addProperty("notes", source.jsonPointer());
        return metadata;
    }

    private static void addDisplay(JsonObject root, QuestV2Resource resource) {
        JsonObject display = new JsonObject();
        putString(display, "title", metadataString(resource, "title"));
        putString(display, "description", metadataString(resource, "description"));
        if (display.size() > 0) {
            root.add("display", display);
        }
    }

    private static boolean shouldCompile(QuestV2Resource.Scene scene) {
        return scene != null
                && scene.externalScene() == null
                && (scene.hasInlineContent()
                        || hasArray(scene.data(), "actions")
                        || hasArray(scene.data(), "conditions"));
    }

    private static void addNode(JsonObject nodes, String nodeId, JsonObject node) {
        if (!nodes.has(nodeId)) {
            nodes.add(nodeId, node);
        }
    }

    private static void putBinding(
            Map<QuestDialogueCatalog.BindingKey, QuestDialogueCatalog.Binding> bindings,
            QuestDialogueCatalog.Binding binding) {
        bindings.put(binding.key(), binding);
    }

    private static Map<String, Integer> stageIndexes(QuestV2Resource resource) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (int index = 0; index < resource.stages().size(); index++) {
            indexes.put(resource.stages().get(index).id(), index);
        }
        return indexes;
    }

    private static List<QuestV2Resource.DialogueSlot> sortedSlots(Map<String, QuestV2Resource.DialogueSlot> slots) {
        List<QuestV2Resource.DialogueSlot> sorted = new ArrayList<>(slots.values());
        sorted.sort(Comparator.comparing(QuestV2Resource.DialogueSlot::slot));
        return sorted;
    }

    private static List<QuestV2Resource.Scene> sortedScenes(Map<String, QuestV2Resource.Scene> scenes) {
        List<QuestV2Resource.Scene> sorted = new ArrayList<>(scenes.values());
        sorted.sort(Comparator.comparing(QuestV2Resource.Scene::id));
        return sorted;
    }

    private static int sceneSourceIndex(QuestV2Resource.Stage stage, String sceneId, int fallback) {
        JsonElement scenes = stage.data().get("scenes");
        if (scenes == null || !scenes.isJsonArray()) {
            return fallback;
        }
        for (int index = 0; index < scenes.getAsJsonArray().size(); index++) {
            JsonElement element = scenes.getAsJsonArray().get(index);
            if (element.isJsonObject()
                    && sceneId.equals(DatapackJsonReader.readString(element.getAsJsonObject(), "id"))) {
                return index;
            }
        }
        return fallback;
    }

    private static void copyActionObjects(JsonObject target, List<JsonObject> actions) {
        if (actions == null || actions.isEmpty()) {
            return;
        }
        JsonArray array = new JsonArray();
        for (JsonObject action : actions) {
            array.add(action.deepCopy());
        }
        target.add("actions", array);
    }

    private static void copyResponseActionObjects(
            JsonObject target,
            List<JsonObject> actions,
            JsonObject transitionAction) {
        if ((actions == null || actions.isEmpty()) && transitionAction == null) {
            return;
        }
        JsonArray array = new JsonArray();
        if (actions != null) {
            for (JsonObject action : actions) {
                array.add(action.deepCopy());
            }
        }
        if (transitionAction != null) {
            array.add(transitionAction);
        }
        target.add("actions", array);
    }

    private static void copyArrayIfPresent(JsonObject source, JsonObject target, String key) {
        JsonElement element = source == null ? null : source.get(key);
        if (element != null && element.isJsonArray()) {
            target.add(key, element.deepCopy());
        }
    }

    private static boolean hasArray(JsonObject source, String key) {
        JsonElement element = source == null ? null : source.get(key);
        return element != null && element.isJsonArray() && element.getAsJsonArray().size() > 0;
    }

    private static JsonArray stringArray(Iterable<String> values) {
        JsonArray array = new JsonArray();
        if (values == null) {
            return array;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                array.add(value);
            }
        }
        return array;
    }

    private static String lifecycleNodeId(String sceneId) {
        return "lifecycle." + normalize(sceneId);
    }

    private static String lifecycleEntryId(String sceneId) {
        return "lifecycle." + normalize(sceneId);
    }

    private static String stageSlotNodeId(String stageId, String slot) {
        return "stage." + normalize(stageId) + ".slot." + normalize(slot);
    }

    private static String stageSlotEntryId(String stageId, String slot) {
        return "stage." + normalize(stageId) + "." + normalize(slot);
    }

    private static String stageSceneNodeId(String stageId, String sceneId) {
        return "stage." + normalize(stageId) + ".scene." + normalize(sceneId);
    }

    private static String stageSceneEntryId(String stageId, String sceneId) {
        return "stage." + normalize(stageId) + ".scene." + normalize(sceneId);
    }

    private static String stageResponsesNodeId(String stageId) {
        return "stage." + normalize(stageId) + ".responses";
    }

    private static String stageResponsesEntryId(String stageId) {
        return "stage." + normalize(stageId) + ".responses";
    }

    private static String titleCase(String value) {
        String normalized = value == null || value.isBlank() ? "Dialogue" : value.replace('_', ' ').replace('.', ' ');
        StringBuilder builder = new StringBuilder();
        for (String part : normalized.split("\\s+")) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.isEmpty() ? "Dialogue" : builder.toString();
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]+", "_");
        normalized = normalized.replaceAll("^[._-]+|[._-]+$", "");
        return normalized.isBlank() ? "default" : normalized;
    }

    private static void putString(JsonObject object, String key, String value) {
        if (value != null && !value.isBlank()) {
            object.addProperty(key, value);
        }
    }

    private static String metadataString(QuestV2Resource resource, String key) {
        JsonElement element = resource.metadata().get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString().trim() : "";
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}
