package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import imgui.ImGui;
import imgui.type.ImString;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class EditorQuestLinks {
    enum Kind { CONVERSATION, SCENE, ENCOUNTER }
    record Link(AuthoringDocument document, String source) { }
    private Kind kind = Kind.CONVERSATION;
    private final ImString name = new ImString("briefing", 128);
    private AuthoringWorkspace cachedWorkspace;
    private AuthoringDocument cachedQuest;
    private String cachedStage = "";
    private List<AuthoringDocument> cachedDocuments = List.of();
    private long[] cachedRevisions = new long[0];
    private List<Link> cachedLinks = List.of();

    static List<Link> linked(AuthoringWorkspace workspace, AuthoringDocument quest, String stage) {
        var node = EditorQuestFlow.stage(quest, stage);
        var result = new ArrayList<Link>();
        var source = JsonPointers.get(quest.json(), node.pointer());
        collect(workspace, quest, source, node.pointer(), result);
        for (var link : List.copyOf(result)) if (DialogueGraph.string(link.document().json(), "schema").equals("villagerretaliation:scene/v1"))
            collect(workspace, quest, link.document().json(), "", result);
        var links = EditorSchema.object(quest.metadata().get("quest_links"));
        for (var path : DialogueGraph.array(links, stage)) workspace.documents().stream()
                .filter(document -> document.resourcePath().equals(quest.resourcePath().substring(0, quest.resourcePath().lastIndexOf('/') + 1) + path.getAsString())).findFirst()
                .ifPresent(document -> result.add(new Link(document, "Stage content")));
        for (var document : workspace.documents()) if (document.kind() == DefinitionKind.CONVERSATION
                && quest.id().equals(DialogueGraph.string(document.json(), "quest")) && accessible(quest, document))
            for (var topic : DialogueGraph.array(document.json(), "topics"))
                if (topic.isJsonObject() && DialogueGraph.array(topic.getAsJsonObject(), "conditions").asList().stream().anyMatch(value ->
                        value.isJsonObject() && stage.equals(DialogueGraph.string(value.getAsJsonObject(), "stage"))))
                    result.add(new Link(document, "Stage conversation"));
        return result.stream().collect(java.util.stream.Collectors.toMap(link -> link.document().resourcePath(), link -> link,
                (first, second) -> first, LinkedHashMap::new)).values().stream().toList();
    }

    private static void collect(AuthoringWorkspace workspace, AuthoringDocument quest, JsonElement value, String path, List<Link> result) {
        if (value.isJsonObject()) {
            for (var entry : value.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()
                        && List.of("scene", "scene_ref", "scene_id", "conversation", "conversation_id", "template").contains(entry.getKey()))
                    for (var document : workspace.documents()) if (document.id().equals(entry.getValue().getAsString()) && accessible(quest, document)
                            && (List.of("conversation", "conversation_id").contains(entry.getKey()) ? document.kind() == DefinitionKind.CONVERSATION
                            : DialogueGraph.string(document.json(), "schema").equals(entry.getKey().equals("template")
                                ? "villagerretaliation:encounter/v1" : "villagerretaliation:scene/v1")))
                        result.add(new Link(document, path + "/" + entry.getKey()));
                if (entry.getValue().isJsonObject() || entry.getValue().isJsonArray())
                    collect(workspace, quest, entry.getValue(), path + "/" + JsonPointers.escape(entry.getKey()), result);
            }
        } else if (value.isJsonArray()) for (int i = 0; i < value.getAsJsonArray().size(); i++)
            collect(workspace, quest, value.getAsJsonArray().get(i), path + "/" + i, result);
    }

    static void renameStage(AuthoringWorkspace workspace, AuthoringDocument quest, String stage, String replacement) {
        var draft = new AuthoringDocument(quest.kind(), quest.resourcePath(), quest.json());
        draft.editMetadata("Copy stage links", metadata -> quest.metadata().entrySet().forEach(entry -> metadata.add(entry.getKey(), entry.getValue())));
        EditorContentTools.renameNode(draft, stage, replacement);
        var updates = new LinkedHashMap<AuthoringDocument, JsonObject>();
        updates.put(quest, draft.json());
        for (var document : workspace.documents()) if (document.kind() == DefinitionKind.CONVERSATION
                && quest.id().equals(DialogueGraph.string(document.json(), "quest")) && accessible(quest, document)) {
            var source = document.json();
            renameReferences(source, quest.id(), stage, replacement);
            if (!source.equals(document.json())) updates.put(document, source);
        }
        workspace.editDocuments("Rename quest stage and linked references", updates, Map.of(quest, draft.metadata()));
    }

    private static void renameReferences(JsonElement value, String quest, String stage, String replacement) {
        if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            String explicit = DialogueGraph.string(object, "quest");
            if (!explicit.isEmpty() && !explicit.equals(quest)) return;
            for (var entry : object.entrySet()) {
                if (List.of("text", "label", "title", "metadata").contains(entry.getKey())) continue;
                if (List.of("stage", "prior_stage", "next_stage", "from_stage", "to_stage").contains(entry.getKey())
                        && entry.getValue().isJsonPrimitive() && stage.equals(entry.getValue().getAsString())) object.addProperty(entry.getKey(), replacement);
                else if (entry.getKey().equals("stages") && entry.getValue().isJsonArray()) {
                    var stages = entry.getValue().getAsJsonArray();
                    for (int i = 0; i < stages.size(); i++) if (stages.get(i).isJsonPrimitive() && stage.equals(stages.get(i).getAsString())) stages.set(i, new com.google.gson.JsonPrimitive(replacement));
                } else renameReferences(entry.getValue(), quest, stage, replacement);
            }
        } else if (value.isJsonArray()) for (var child : value.getAsJsonArray()) renameReferences(child, quest, stage, replacement);
    }

    static boolean accessible(AuthoringDocument quest, AuthoringDocument content) {
        String root = DefinitionKind.contentRoot(quest.resourcePath());
        String bundle = quest.resourcePath().substring(0, quest.resourcePath().lastIndexOf('/') + 1);
        return content.resourcePath().startsWith(bundle) || content.resourcePath().startsWith(root + "quests/_shared/");
    }

    static AuthoringDocument create(AuthoringWorkspace workspace, AuthoringDocument quest, String stage, Kind kind, String name) {
        EditorQuestFlow.stage(quest, stage);
        if (!name.matches("[a-z][a-z0-9_]{0,63}")) throw new IllegalArgumentException("Use a lowercase content name with letters, numbers or underscores");
        String bundle = quest.resourcePath().substring(0, quest.resourcePath().lastIndexOf('/') + 1);
        String id = quest.id() + "_" + name;
        var additions = new LinkedHashMap<String, JsonObject>();
        AuthoringDocument primary;
        if (kind == Kind.CONVERSATION) {
            String path = bundle + "conversations/" + name + ".json";
            String prefix = DialogueGraph.string(quest.json(), "localization_prefix") + ".conversation." + name;
            var conversation = json("""
                    {"schema":"villagerretaliation:conversation/v1","revision":1,"character":"provider",
                     "topics":[{"id":"talk","start":"start","conditions":[]}],
                     "nodes":[{"id":"start","exchanges":[{"id":"greeting","speaker":{"type":"role","role":"provider"}}],"outcome":"done"}],
                     "outcomes":[{"id":"done"}]}
                    """);
            conversation.addProperty("id", id); conversation.addProperty("quest", quest.id());
            conversation.add("title", localized(prefix + ".title"));
            var topic = conversation.getAsJsonArray("topics").get(0).getAsJsonObject();
            topic.add("label", localized(prefix + ".topic"));
            var conditions = topic.getAsJsonArray("conditions");
            var active = new JsonObject(); active.addProperty("type", "quest"); active.addProperty("quest", quest.id()); active.addProperty("state", "active");
            conditions.add(active);
            var atStage = new JsonObject(); atStage.addProperty("type", "quest_fact"); atStage.addProperty("quest", quest.id()); atStage.addProperty("stage", stage);
            conditions.add(atStage);
            conversation.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonArray("exchanges").get(0).getAsJsonObject().add("text", localized(prefix + ".greeting"));
            conversation.getAsJsonArray("outcomes").get(0).getAsJsonObject().add("label", localized(prefix + ".done"));
            String localePath = bundle + "locales/en_us.json";
            var locale = workspace.documents().stream().filter(document -> document.resourcePath().equals(localePath)).findFirst()
                    .map(AuthoringDocument::json).orElseGet(JsonObject::new);
            var messages = locale.has("messages") ? locale.getAsJsonObject("messages") : locale;
            for (String suffix : List.of(".title", ".topic", ".greeting", ".done"))
                if (messages.has(prefix + suffix)) throw new IllegalArgumentException("Conversation translation already exists: " + prefix + suffix);
            messages.addProperty(prefix + ".title", name.replace('_', ' ')); messages.addProperty(prefix + ".topic", "Talk");
            messages.addProperty(prefix + ".greeting", "Let's discuss what comes next."); messages.addProperty(prefix + ".done", "Goodbye");
            additions.put(localePath, locale);
            additions.put(path, conversation);
            primary = new AuthoringDocument(DefinitionKind.CONVERSATION, path, conversation);
        } else {
            String sceneId = kind == Kind.ENCOUNTER ? id + "_scene" : id;
            String scenePath = bundle + "scenes/" + (kind == Kind.ENCOUNTER ? name + "_scene" : name) + ".json";
            var scene = scene(sceneId, kind == Kind.ENCOUNTER ? id : "");
            if (kind == Kind.SCENE) {
                String key = DialogueGraph.string(quest.json(), "localization_prefix") + ".scene." + name + ".opening";
                scene.getAsJsonArray("steps").get(0).getAsJsonObject().getAsJsonObject("data").add("text", localized(key));
                String localePath = bundle + "locales/en_us.json";
                var locale = workspace.documents().stream().filter(document -> document.resourcePath().equals(localePath)).findFirst()
                        .map(AuthoringDocument::json).orElseGet(JsonObject::new);
                var messages = locale.has("messages") ? locale.getAsJsonObject("messages") : locale;
                if (messages.has(key)) throw new IllegalArgumentException("Scene translation already exists: " + key);
                messages.addProperty(key, "Let's get started.");
                additions.put(localePath, locale);
            }
            additions.put(scenePath, scene);
            if (kind == Kind.ENCOUNTER) {
                String path = bundle + "encounters/" + name + ".json";
                var encounter = json("""
                        {"schema":"villagerretaliation:encounter/v1","controller":"villagerretaliation:controlled",
                         "members":[{"id":"guard","entity":"minecraft:zombie","count":2}],"spawn_radius":8}
                        """);
                encounter.addProperty("id", id); additions.put(path, encounter);
                primary = new AuthoringDocument(DefinitionKind.RESOURCE, path, encounter);
            } else primary = new AuthoringDocument(DefinitionKind.RESOURCE, scenePath, scene);
        }
        for (var entry : additions.entrySet()) {
            if (entry.getKey().contains("/locales/")) continue;
            if (workspace.filePaths().stream().anyMatch(path -> path.equalsIgnoreCase(entry.getKey())))
                throw new IllegalArgumentException("Resource already exists: " + entry.getKey());
            for (var existing : workspace.documents()) if (existing.id().equals(DialogueGraph.string(entry.getValue(), "id"))
                    && DialogueGraph.string(existing.json(), "schema").equals(DialogueGraph.string(entry.getValue(), "schema")))
                throw new IllegalArgumentException("Content ID already exists: " + existing.id());
        }
        var draft = new AuthoringDocument(quest.kind(), quest.resourcePath(), quest.json());
        if (kind != Kind.CONVERSATION) attachScene(draft, stage, kind == Kind.ENCOUNTER ? id + "_scene" : id, name);
        var metadata = quest.metadata();
        if (!metadata.has("quest_links")) metadata.add("quest_links", new JsonObject());
        var links = metadata.getAsJsonObject("quest_links");
        var paths = links.has(stage) ? links.getAsJsonArray(stage) : new JsonArray();
        paths.add(primary.resourcePath().substring(bundle.length()));
        if (kind == Kind.ENCOUNTER) paths.add("scenes/" + name + "_scene.json");
        links.add(stage, paths);
        workspace.addQuestContent(quest, draft.json(), metadata, additions);
        return workspace.documents().stream().filter(document -> document.resourcePath().equals(primary.resourcePath())).findFirst().orElseThrow();
    }

    static JsonObject scene(String id, String encounter) {
        var scene = json("""
                {"schema":"villagerretaliation:scene/v1","ownership":"quest_instance","entry_step":"opening",
                 "failure_policy":"block_for_repair","cleanup_policy":"all_owned",
                 "actors":[{"alias":"player","type":"villagerretaliation:player","binding_source":"owner_player","required":true}],
                 "steps":[]}
                """);
        scene.addProperty("id", id);
        JsonArray steps;
        if (encounter.isEmpty()) steps = JsonParser.parseString("""
                [{"id":"opening","type":"villagerretaliation:dialogue","data":{"text":"Let's get started."},"next":"done"},
                 {"id":"done","type":"villagerretaliation:scene_complete"}]
                """).getAsJsonArray();
        else {
            steps = JsonParser.parseString("""
                    [{"id":"opening","type":"villagerretaliation:start_encounter","data":{"anchor_actor":"player"},"next":"fight","failure_step":"failed"},
                     {"id":"fight","type":"villagerretaliation:wait_encounter","data":{"encounter_step":"opening"},"next":"cleanup","failure_step":"failed"},
                     {"id":"cleanup","type":"villagerretaliation:cleanup_encounter","data":{"encounter_step":"opening"},"next":"done"},
                     {"id":"done","type":"villagerretaliation:scene_complete"},
                     {"id":"failed","type":"villagerretaliation:scene_fail"}]
                    """).getAsJsonArray();
            steps.get(0).getAsJsonObject().getAsJsonObject("data").addProperty("template", encounter);
        }
        scene.add("steps", steps); return scene;
    }

    private static void attachScene(AuthoringDocument draft, String stage, String id, String name) {
        String tag = id + "_done";
        var objective = new JsonObject(); objective.addProperty("type", "fact"); objective.addProperty("scope", "quest"); objective.addProperty("tag", tag);
        EditorContentTools.appendObjective(draft, stage, objective);
        var node = EditorQuestFlow.stage(draft, stage);
        draft.edit("Run linked scene", root -> {
            var owner = JsonPointers.get(root, node.pointer()).getAsJsonObject();
            var actions = DialogueGraph.array(owner, "on_enter");
            var start = new JsonObject(); start.addProperty("type", "start_scene"); start.addProperty("scene", id);
            start.addProperty("operation_id", "scene_" + name); start.addProperty("wait_for_result", true);
            actions.add(start);
            var complete = new JsonObject(); complete.addProperty("type", "set_tag"); complete.addProperty("scope", "quest"); complete.addProperty("tag", tag);
            actions.add(complete); owner.add("on_enter", actions);
        });
    }

    private List<Link> cachedLinks(AuthoringWorkspace workspace, AuthoringDocument quest, String stage) {
        var documents = workspace.documents();
        boolean current = cachedWorkspace == workspace && cachedQuest == quest && cachedStage.equals(stage) && cachedDocuments.equals(documents);
        if (current) for (int i = 0; i < documents.size(); i++) if (documents.get(i).revision() != cachedRevisions[i]) { current = false; break; }
        if (!current) {
            cachedLinks = linked(workspace, quest, stage);
            cachedWorkspace = workspace; cachedQuest = quest; cachedStage = stage;
            cachedDocuments = List.copyOf(documents);
            cachedRevisions = documents.stream().mapToLong(AuthoringDocument::revision).toArray();
        }
        return cachedLinks;
    }

    void render(AuthoringWorkspace workspace, AuthoringDocument quest, String stage, Consumer<ImGuiAuthoringEditor.EditorOperation> run,
                Consumer<AuthoringDocument> open) {
        if (quest.kind() != DefinitionKind.QUEST || stage.isEmpty() || !EditorWidgets.section("Related quest content")) return;
        for (var link : cachedLinks(workspace, quest, stage)) {
            if (ImGui.button("Open " + link.document().id() + "##" + link.document().resourcePath())) open.accept(link.document());
            if (ImGui.isItemHovered()) ImGui.setTooltip(link.source());
        }
        if (ImGui.beginCombo(EditorWidgets.field("Create content"), kind.name().toLowerCase(java.util.Locale.ROOT))) {
            for (var value : Kind.values()) if (ImGui.selectable(value.name().toLowerCase(java.util.Locale.ROOT), kind == value)) kind = value;
            ImGui.endCombo();
        }
        ImGui.inputText(EditorWidgets.field("Content name"), name);
        ImGui.textWrapped(kind == Kind.CONVERSATION ? "Creates a conversation available while this quest stage is active."
                : kind == Kind.SCENE ? "Runs the scene on stage entry and waits for it before completing the stage."
                : "Creates an encounter and its controlling scene, then waits for the encounter before completing the stage.");
        if (ImGui.button("Create and open " + kind.name().toLowerCase(java.util.Locale.ROOT)))
            run.accept(() -> open.accept(create(workspace, quest, stage, kind, name.get())));
    }

    private static JsonObject localized(String key) { var value = new JsonObject(); value.addProperty("key", key); return value; }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
}
