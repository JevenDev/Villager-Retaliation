package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import imgui.ImGui;
import imgui.type.ImString;
import java.util.List;
import java.util.function.Consumer;

final class EditorQuestFlow {
    enum Route { AUTOMATIC, CHOICE, CONDITIONAL }

    private Route route = Route.AUTOMATIC;
    private String destination = "", outcome = "stage";
    private EditorBlocks.Kind condition = EditorBlocks.Kind.REPUTATION_GATE;
    private final ImString label = new ImString("Continue", 512);
    private final ImString event = new ImString("progress", 128);

    static DialogueGraph.Node stage(AuthoringDocument document, String id) {
        if (document.kind() != DefinitionKind.QUEST) throw new IllegalArgumentException("Select a quest");
        return DialogueGraph.nodes(document).stream().filter(node -> node.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Select an existing stage"));
    }

    static String add(AuthoringDocument document, String stageId, Route route, String outcome, String destination,
                      String label, String event, JsonArray conditions) {
        var node = stage(document, stageId);
        var transition = new JsonObject();
        if (outcome.equals("stage")) {
            stage(document, destination);
            transition.addProperty("stage", destination);
        } else if (List.of("complete", "fail", "abandon").contains(outcome)) transition.addProperty(outcome, true);
        else throw new IllegalArgumentException("Choose a destination or quest ending");
        if (route == Route.CHOICE && label.isBlank()) throw new IllegalArgumentException("Enter the player choice");
        if (route == Route.CONDITIONAL && conditions.isEmpty()) throw new IllegalArgumentException("Add a branch condition");
        if (route == Route.CONDITIONAL && com.jvn.villagerretaliation.quest.QuestTriggerRegistry.eventBySerializedName(event) == null)
            throw new IllegalArgumentException("Choose a registered quest event");
        var owner = JsonPointers.get(document.json(), node.pointer()).getAsJsonObject();
        String field = route == Route.CHOICE ? "responses" : "events";
        int index = DialogueGraph.array(owner, field).size();
        String path = route == Route.AUTOMATIC ? node.pointer() + "/transition" : node.pointer() + "/" + field + "/" + index;
        String id = field + "_" + (index + 1);
        while (containsId(DialogueGraph.array(owner, field), id)) id += "_new";
        var block = new JsonObject();
        block.addProperty("id", id);
        block.add("transition", transition);
        if (route == Route.CHOICE) block.addProperty("label", label);
        if (route == Route.CONDITIONAL) {
            block.addProperty("event", event);
            block.addProperty("repeatable", false);
        }
        if (!conditions.isEmpty()) block.add("conditions", conditions.deepCopy());
        document.edit(route == Route.AUTOMATIC ? "Set stage progression" : "Add quest branch", root -> {
            var target = JsonPointers.get(root, node.pointer()).getAsJsonObject();
            if (route == Route.AUTOMATIC) target.add("transition", transition);
            else {
                var values = target.has(field) ? target.getAsJsonArray(field) : new JsonArray();
                values.add(block);
                target.add(field, values);
            }
        });
        return path;
    }

    private static boolean containsId(JsonArray values, String id) {
        return values.asList().stream().anyMatch(value -> value.isJsonObject() && id.equals(DialogueGraph.string(value.getAsJsonObject(), "id")));
    }

    void render(AuthoringDocument document, String selected, Consumer<ImGuiAuthoringEditor.EditorOperation> run,
                Consumer<String> inspect, Runnable refresh) {
        if (document.kind() != DefinitionKind.QUEST || selected.isEmpty() || !EditorWidgets.section("Stage progression")) return;
        var node = stage(document, selected);
        var owner = JsonPointers.get(document.json(), node.pointer()).getAsJsonObject();
        for (String field : List.of("transition", "responses", "events")) {
            if (!owner.has(field)) continue;
            if (field.equals("transition")) {
                if (ImGui.button("Edit automatic progression")) inspect.accept(node.pointer() + "/transition");
                ImGui.textWrapped(summary(owner.getAsJsonObject(field)));
            } else {
                var values = DialogueGraph.array(owner, field);
                for (int i = 0; i < values.size(); i++) {
                    if (!values.get(i).isJsonObject()) continue;
                    var value = values.get(i).getAsJsonObject();
                    String path = node.pointer() + "/" + field + "/" + i;
                    String title = field.equals("responses") ? DialogueGraph.string(value, "id") : DialogueGraph.string(value, "event");
                    if (ImGui.button("Edit " + title + "##" + path)) inspect.accept(path);
                    ImGui.textWrapped(summary(EditorSchema.object(value.get("transition"))));
                }
            }
        }
        if (ImGui.beginCombo(EditorWidgets.field("Add progression"), routeLabel(route))) {
            for (var value : Route.values()) if (ImGui.selectable(routeLabel(value), value == route)) route = value;
            ImGui.endCombo();
        }
        if (route == Route.CHOICE) ImGui.inputText(EditorWidgets.field("Player choice"), label);
        if (route == Route.CONDITIONAL) ImGui.inputText(EditorWidgets.field("Quest event"), event);
        if (ImGui.beginCombo(EditorWidgets.field("Result"), outcome.equals("stage") ? "Go to stage" : outcome)) {
            for (String value : List.of("stage", "complete", "fail", "abandon"))
                if (ImGui.selectable(value.equals("stage") ? "Go to stage" : value, outcome.equals(value))) outcome = value;
            ImGui.endCombo();
        }
        if (outcome.equals("stage") && ImGui.beginCombo(EditorWidgets.field("Destination stage"), destination.isEmpty() ? "Choose..." : destination)) {
            for (var value : DialogueGraph.nodes(document))
                if (ImGui.selectable(value.id(), destination.equals(value.id()))) destination = value.id();
            ImGui.endCombo();
        }
        if (route != Route.AUTOMATIC && ImGui.beginCombo(EditorWidgets.field("Initial condition"),
                condition == null ? "Always available" : condition.label)) {
            if (route == Route.CHOICE && ImGui.selectable("Always available", condition == null)) condition = null;
            for (var value : EditorBlocks.Kind.values()) if (value.condition && ImGui.selectable(value.label, condition == value)) condition = value;
            ImGui.endCombo();
        }
        if (route == Route.CONDITIONAL && condition == null) condition = EditorBlocks.Kind.REPUTATION_GATE;
        ImGui.textWrapped(route == Route.AUTOMATIC ? "Runs when stage completion requirements are met."
                : route == Route.CHOICE ? "Players choose this route in quest dialogue. Edit its conditions and presentation in Details."
                : "Runs when the quest event occurs and its conditions match. Edit the condition before playtesting.");
        boolean replacing = route == Route.AUTOMATIC && owner.has("transition");
        if (replacing) ImGui.textWrapped("Adding automatic progression replaces the current automatic result.");
        ImGui.beginDisabled(outcome.equals("stage") && destination.isBlank());
        if (ImGui.button(replacing ? "Replace automatic progression" : "Add progression")) run.accept(() -> {
            var conditions = new JsonArray();
            if (route != Route.AUTOMATIC && condition != null) conditions.add(JsonParser.parseString(condition.json));
            String path = add(document, selected, route, outcome, destination, label.get(), event.get(), conditions);
            refresh.run();
            inspect.accept(path);
        });
        ImGui.endDisabled();
    }

    private static String routeLabel(Route value) {
        return switch (value) {
            case AUTOMATIC -> "Automatic";
            case CHOICE -> "Player choice";
            case CONDITIONAL -> "Conditional event";
        };
    }

    static String summary(JsonObject transition) {
        if (transition.has("stage")) return "Go to " + DialogueGraph.string(transition, "stage");
        for (String value : List.of("complete", "fail", "abandon"))
            if (transition.has(value) && transition.get(value).getAsBoolean()) return value + " quest";
        if (transition.has("scene")) return "Scene: " + DialogueGraph.string(transition, "scene");
        return "No stage change";
    }
}
