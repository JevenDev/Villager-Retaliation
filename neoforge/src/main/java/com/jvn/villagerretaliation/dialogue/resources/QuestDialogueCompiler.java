package com.jvn.villagerretaliation.dialogue.resources;

import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueEntryMetadata;
import com.jvn.villagerretaliation.dialogue.normal.DialogueRequestType;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextVariant;
import com.jvn.villagerretaliation.dialogue.normal.DialogueUsagePolicy;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestTransition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeDefinition;
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
import java.util.LinkedHashSet;
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
        List<DialogueTreeDefinition.Entry> entries = new ArrayList<>();
        Map<String, DialogueTreeDefinition.Node> nodes = new LinkedHashMap<>();
        Map<QuestDialogueCatalog.BindingKey, QuestDialogueCatalog.Binding> bindings = new LinkedHashMap<>();
        Map<String, Integer> stageIndexes = stageIndexes(resource);

        addLifecycleScenes(resource, source, treeId, entries, nodes, bindings);
        for (QuestV2Resource.Stage stage : resource.stages()) {
            QuestSourcePointer stageSource = source.child(
                    "stages",
                    Integer.toString(stageIndexes.getOrDefault(stage.id(), 0)));
            addStageDialogue(resource, stage, stageSource, treeId, entries, nodes, bindings);
        }

        Map<ResourceLocation, DialogueTreeDefinition> trees = Map.of();
        Map<ResourceLocation, QuestSourcePointer> treeSources = Map.of();
        if (entries.size() > 0 && nodes.size() > 0) {
            DialogueTreeDefinition tree = DialogueTreeValidation.validate(source.resource(), new DialogueTreeDefinition(
                    treeId, metadataString(resource, "title"), metadataString(resource, "description"),
                    metadata(resource, "", "quest_module_v2", source), List.of(), entries, nodes));
            if (tree == null) {
                DatapackDiagnostics.warnQuestV2Validation(
                        source.resource(),
                        "",
                        "generated embedded dialogue tree failed structural validation.",
                        "Check embedded dialogue scene, response, condition, and action payloads.",
                        Set.of(resource.id().toString(), treeId.toString()));
            } else {
                trees = Map.of(tree.id(), tree);
                treeSources = Map.of(tree.id(), source);
            }
        }
        if (trees.isEmpty() && bindings.isEmpty()) {
            return QuestDialogueCatalog.empty();
        }

        return new QuestDialogueCatalog(
                trees,
                bindings,
                treeSources);
    }

    public static ResourceLocation treeId(ResourceLocation questId) {
        return ResourceLocation.fromNamespaceAndPath(
                questId.getNamespace(),
                TREE_ROOT + questId.getPath());
    }

    private static String boundedEntryId(ResourceLocation treeId, String entryId) {
        return com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition.isNetworkSafeId(
                com.jvn.villagerretaliation.dialogue.normal.DialogueTreeService.entryOptionId(treeId, entryId)) ? entryId
                : java.util.UUID.nameUUIDFromBytes(entryId.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private static void addLifecycleScenes(
            QuestV2Resource resource,
            QuestSourcePointer source,
            ResourceLocation treeId,
            List<DialogueTreeDefinition.Entry> entries,
            Map<String, DialogueTreeDefinition.Node> nodes,
            Map<QuestDialogueCatalog.BindingKey, QuestDialogueCatalog.Binding> bindings) {
        for (int index = 0; index < resource.lifecycle().dialogue().size(); index++) {
            QuestV2Resource.Scene scene = resource.lifecycle().dialogue().get(index);
            if (!shouldCompile(scene)) {
                continue;
            }
            QuestSourcePointer sceneSource = source.child("lifecycle", "dialogue", Integer.toString(index));
            if (!scene.externalScene().isEmpty()) {
                putBinding(
                        bindings,
                        new QuestDialogueCatalog.Binding(
                                resource.id(),
                                "",
                                "lifecycle:" + scene.id(),
                                scene.id(),
                                scene.externalScene().tree(),
                                scene.externalScene().entryOr(scene.id()),
                                sceneSource));
                continue;
            }
            String nodeId = lifecycleNodeId(scene.id());
            String entryId = boundedEntryId(treeId, lifecycleEntryId(scene.id()));
            addSceneNode(resource, "", scene, sceneSource, nodeId, null, nodes);
            addEntry(resource, "", entryId, titleCase(scene.id()), nodeId, sceneSource, scene.data(), entries);
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
            List<DialogueTreeDefinition.Entry> entries,
            Map<String, DialogueTreeDefinition.Node> nodes,
            Map<QuestDialogueCatalog.BindingKey, QuestDialogueCatalog.Binding> bindings) {
        for (QuestV2Resource.DialogueSlot slot : sortedSlots(stage.dialogueSlots())) {
            QuestSourcePointer slotSource = stageSource.child("dialogue", slot.slot());
            QuestV2Resource.Scene scene = slot.inlineScene();
            String nodeId = stageSlotNodeId(stage.id(), slot.slot());
            String sceneId = scene == null ? slot.scene() : scene.id();
            QuestV2Resource.ExternalScene external = slot.externalScene();
            String externalDefaultEntry = slot.slot();
            if (scene == null && !slot.scene().isBlank()) {
                scene = stage.scenes().get(slot.scene());
                nodeId = stageSceneNodeId(stage.id(), slot.scene());
                if (scene != null) {
                    sceneId = scene.id();
                    external = scene.externalScene();
                    externalDefaultEntry = scene.id();
                }
            }
            if (!external.isEmpty()) {
                putBinding(
                        bindings,
                        new QuestDialogueCatalog.Binding(
                                resource.id(),
                                stage.id(),
                                slot.slot(),
                                sceneId,
                                external.tree(),
                                external.entryOr(externalDefaultEntry),
                                slotSource));
                continue;
            }
            if (!shouldCompile(scene)) {
                continue;
            }
            String entryId = boundedEntryId(treeId, stageSlotEntryId(stage.id(), slot.slot()));
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
            QuestSourcePointer sceneSource = stageSource.child(
                    "scenes",
                    Integer.toString(sceneSourceIndex(stage, scene.id(), sortedSceneIndex)));
            if (!scene.externalScene().isEmpty()) {
                putBinding(
                        bindings,
                        new QuestDialogueCatalog.Binding(
                                resource.id(),
                                stage.id(),
                                "scene:" + scene.id(),
                                scene.id(),
                                scene.externalScene().tree(),
                                scene.externalScene().entryOr(scene.id()),
                                sceneSource));
            } else if (shouldCompile(scene)) {
                String nodeId = stageSceneNodeId(stage.id(), scene.id());
                String entryId = boundedEntryId(treeId, stageSceneEntryId(stage.id(), scene.id()));
                addSceneNode(resource, stage.id(), scene, sceneSource, nodeId, stage, nodes);
                addEntry(resource, stage.id(), entryId, titleCase(scene.id()), nodeId, sceneSource, scene.data(), entries);
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
            String entryId = boundedEntryId(treeId, stageResponsesEntryId(stage.id()));
            List<String> lines = nonBlankLines(List.of(stage.ui().trackerText()));
            nodes.putIfAbsent(nodeId, new DialogueTreeDefinition.Node(nodeId, lines,
                    textVariants(treeId + "/node/" + nodeId, lines, "", DialogueEntryMetadata.EMPTY),
                    List.of(), List.of(), compileResponses(resource, stage.id(), stage.responses(), stage,
                            responsesSource, stageResponsesEntryId(stage.id())), false,
                    com.jvn.villagerretaliation.dialogue.DialogueExchange.read(stage.data()), com.jvn.villagerretaliation.dialogue.DialogueCamera.read(stage.data()),
                    responseTimer(stage.data(), stage.id())));
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
            Map<String, DialogueTreeDefinition.Node> nodes) {
        if (nodes.containsKey(nodeId)) return;
        List<String> lines = nonBlankLines(scene.lines());
        nodes.put(nodeId, new DialogueTreeDefinition.Node(nodeId, lines,
                textVariants(treeId(resource.id()) + "/node/" + nodeId, lines,
                        scene.textKey(), DialogueEntryMetadata.EMPTY),
                VillagerActionDefinition.readList(sceneSource.resource(), sceneSource.jsonPointer(), scene.data(), resource.id()),
                DialogueCondition.readList(sceneSource.resource(), sceneSource.jsonPointer(), scene.data(), resource.id()),
                compileResponses(resource, stageId, scene.responses(), stage, sceneSource.child("responses"),
                        stageSceneEntryId(stageId, scene.id())),
                DatapackJsonReader.readBoolean(scene.data(), "end", false),
                com.jvn.villagerretaliation.dialogue.DialogueExchange.read(scene.data()), com.jvn.villagerretaliation.dialogue.DialogueCamera.read(scene.data()),
                responseTimer(scene.data(), stageId)));
    }

    private static com.jvn.villagerretaliation.dialogue.DialogueResponseTimer responseTimer(JsonObject data, String stage) {
        var timer = com.jvn.villagerretaliation.dialogue.DialogueResponseTimer.read(data.get("response_timer"));
        if (timer == null || timer.next().isBlank()) return timer;
        return new com.jvn.villagerretaliation.dialogue.DialogueResponseTimer(timer.durationTicks(), "",
                stage.isBlank() ? lifecycleNodeId(timer.next()) : stageSceneNodeId(stage, timer.next()));
    }

    private static List<DialogueTreeDefinition.Response> compileResponses(
            QuestV2Resource resource,
            String stageId,
            List<QuestV2Resource.Response> responses,
            QuestV2Resource.Stage stage,
            QuestSourcePointer source,
            String scenePath) {
        List<DialogueTreeDefinition.Response> result = new ArrayList<>();
        for (int index = 0; index < responses.size(); index++) {
            QuestV2Resource.Response response = responses.get(index);
            QuestSourcePointer responseSource = source.child(Integer.toString(index));
            String label = responseLabel(response);
            DialogueEntryMetadata metadata = metadata(resource, stageId, "quest_module_v2_response",
                    responseSource, response.data());
            List<String> lines = responseLines(response);
            result.add(new DialogueTreeDefinition.Response(response.id(), label,
                    DatapackJsonReader.readString(response.data(), "label_key"), metadata,
                    nextSceneNodeId(stage, response.transition()),
                    DatapackJsonReader.readEnum(response.data(), "request", DialogueRequestType.class).orElse(DialogueRequestType.STORY),
                    lines, textVariants(treeId(resource.id()) + "/response/" + response.id(), lines,
                            DatapackJsonReader.readString(response.data(), "text_key"), metadata),
                    responseActions(resource, stageId, scenePath, response, responseSource),
                    DialogueCondition.readList(source.resource(), responseSource.jsonPointer(), response.data(), resource.id()),
                    DatapackJsonReader.readBoolean(response.data(), "end", false),
                    DatapackJsonReader.readInt(response.data(), "order", index),
                    DatapackJsonReader.readInt(response.data(), "priority", 0),
                    com.jvn.villagerretaliation.dialogue.DialogueExchange.read(response.data()), com.jvn.villagerretaliation.dialogue.DialogueCamera.read(response.data()),
                    com.jvn.villagerretaliation.dialogue.DialogueChoice.read(response.data(), com.jvn.villagerretaliation.dialogue.DialogueChoice.HIDDEN)));
        }
        return List.copyOf(result);
    }

    private static List<DialogueTextVariant> textVariants(String owner, List<String> lines,
            String textKey, DialogueEntryMetadata metadata) {
        return DialogueTextVariant.legacy(owner,
                textKey.isBlank() || lines.isEmpty() ? lines : List.of(lines.getFirst()),
                textKey, metadata, DialogueUsagePolicy.DEFAULT);
    }

    private static String responseLabel(QuestV2Resource.Response response) {
        String explicitLabel = DatapackJsonReader.readString(response.data(), "label");
        return firstNonBlank(
                explicitLabel,
                firstNonBlank(response.label().text(), titleCase(response.id())));
    }

    private static List<String> responseLines(QuestV2Resource.Response response) {
        List<String> lines = new ArrayList<>(nonBlankLines(response.label().lines()));
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

    private static VillagerActionDefinition transitionAction(
            QuestV2Resource resource,
            String stageId,
            String scenePath,
            QuestV2Resource.Response response,
            QuestSourcePointer source) {
        QuestV2Resource.Transition transition = response.transition();
        if (transition == null || transition.isEmpty() || !transition.scene().isBlank()
                || !transition.response().isBlank()) return null;
        CompiledQuestTransition.Target target = !transition.stage().isBlank() ? CompiledQuestTransition.Target.STAGE
                : transition.complete() ? CompiledQuestTransition.Target.COMPLETE
                : transition.abandon() ? CompiledQuestTransition.Target.ABANDON
                : transition.fail() ? CompiledQuestTransition.Target.FAIL : CompiledQuestTransition.Target.NONE;
        CompiledQuestTransition compiled = new CompiledQuestTransition(resource.id(), stageId, scenePath,
                response.id(), target, transition.stage(), source.jsonPointer());
        return compiled.isEmpty() ? null : VillagerActionDefinition.transition(compiled);
    }

    private static void addEntry(
            QuestV2Resource resource,
            String stageId,
            String entryId,
            String label,
            String nodeId,
            QuestSourcePointer source,
            JsonObject entryData,
            List<DialogueTreeDefinition.Entry> entries) {
        JsonObject data = entryData == null ? new JsonObject() : entryData;
        String explicitLabel = DatapackJsonReader.readString(data, "label");
        String resolvedLabel = explicitLabel.isBlank() ? label : explicitLabel;
        entries.add(new DialogueTreeDefinition.Entry(entryId, resolvedLabel,
                DatapackJsonReader.readString(data, "label_key"),
                metadata(resource, stageId, "quest_module_v2_entry", source, data), nodeId,
                DatapackJsonReader.readEnum(data, "request", DialogueRequestType.class).orElse(DialogueRequestType.STORY),
                true, DatapackJsonReader.readBoolean(data, "show_for_babies", true), Set.of(), Set.of(),
                DialogueCondition.readList(source.resource(), source.jsonPointer(), data, resource.id()), false,
                DatapackJsonReader.readInt(data, "order", entries.size()),
                DatapackJsonReader.readInt(data, "priority", 0)));
    }

    private static DialogueEntryMetadata metadata(
            QuestV2Resource resource, String stageId, String topic, QuestSourcePointer source) {
        return metadata(resource, stageId, topic, source, null);
    }

    private static DialogueEntryMetadata metadata(
            QuestV2Resource resource, String stageId, String topic, QuestSourcePointer source, JsonObject owner) {
        DialogueEntryMetadata authored = owner == null ? DialogueEntryMetadata.EMPTY
                : DialogueEntryMetadata.read(source.resource(), "quest module v2", source.jsonPointer(), owner);
        JsonObject data = owner == null ? null : DatapackJsonReader.readObject(owner, "metadata");
        Set<String> tags = new LinkedHashSet<>(List.of("generated", "quest_v2"));
        tags.addAll(stringValues(resource.metadata().get("tags")));
        tags.addAll(authored.tags());
        return new DialogueEntryMetadata(data != null && data.has("topic") ? authored.topic() : topic,
                tags, authored.routingTags(), authored.antiRepeatGroups(),
                firstNonBlank(metadataString(resource, "questline"), authored.questline()),
                resource.id().toString(), stageId,
                data != null && data.has("notes") ? authored.notes() : source.jsonPointer());
    }

    private static List<String> stringValues(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(value -> {
                if (value.isJsonPrimitive()) {
                    values.add(value.getAsString());
                }
            });
        } else if (element.isJsonPrimitive()) {
            values.add(element.getAsString());
        }
        return List.copyOf(values);
    }

    private static boolean shouldCompile(QuestV2Resource.Scene scene) {
        return scene != null
                && scene.externalScene().isEmpty()
                && (scene.hasInlineContent()
                        || hasArray(scene.data(), "actions")
                        || hasArray(scene.data(), "conditions"));
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

    private static List<VillagerActionDefinition> responseActions(
            QuestV2Resource resource, String stageId, String scenePath,
            QuestV2Resource.Response response, QuestSourcePointer source) {
        List<VillagerActionDefinition> authored = VillagerActionDefinition.readObjects(
                source.resource(), source.jsonPointer(), response.actions(), resource.id());
        VillagerActionDefinition transition = transitionAction(resource, stageId, scenePath, response, source);
        if (transition == null) return authored;
        List<VillagerActionDefinition> actions = new ArrayList<>(authored);
        int transitionIndex = actions.size();
        for (int index = 0; index < actions.size(); index++) {
            VillagerActionDefinition action = actions.get(index);
            if (action.kind() == VillagerActionDefinition.Kind.START_SCENE && action.waitForScene()) {
                transitionIndex = index + 1;
                break;
            }
        }
        actions.add(transitionIndex, transition);
        return List.copyOf(actions);
    }

    private static boolean hasArray(JsonObject source, String key) {
        JsonElement element = source == null ? null : source.get(key);
        return element != null && element.isJsonArray() && element.getAsJsonArray().size() > 0;
    }

    private static List<String> nonBlankLines(List<String> values) {
        return values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).toList();
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

    private static String metadataString(QuestV2Resource resource, String key) {
        JsonElement element = resource.metadata().get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString().trim() : "";
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}
