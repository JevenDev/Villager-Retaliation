package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import com.jvn.villagerretaliation.authoring.preview.DialoguePreviewSession;
import com.jvn.villagerretaliation.authoring.preview.PreviewConditions;
import com.jvn.villagerretaliation.authoring.preview.PreviewCoverage;
import com.jvn.villagerretaliation.authoring.preview.QuestPreviewSession;
import imgui.ImGui;
import imgui.type.ImString;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.BiFunction;
import net.minecraft.resources.ResourceLocation;

final class EditorScenarioTests {
    record Step(String action, String target) { }
    record Result(String name, boolean passed, boolean skipped, String message, String node) { }
    private static final List<String> EXPECTATIONS = List.of("Node or stage", "Quest outcome", "Dialogue ended", "Choice available", "Choice unavailable");
    private final ImString target = new ImString(512);
    private String selected = "", start = "", expectation = "Node or stage";
    private final List<Step> steps = new ArrayList<>();
    private AuthoringDocument owner;
    private List<Result> results = List.of();
    private JsonObject testedJson, testedMetadata;
    private String loadError = "";
    private com.google.gson.JsonElement loadedConfig;

    static JsonObject config(String start, String expectation, String target, List<Step> steps) {
        if (!EXPECTATIONS.contains(expectation)) throw new IllegalArgumentException("Choose an expected result");
        if (!expectation.equals("Dialogue ended") && target.isBlank()) throw new IllegalArgumentException("Choose the expected node, outcome or choice");
        if (steps.size() > 256) throw new IllegalArgumentException("Use at most 256 steps per scenario");
        JsonObject result = new JsonObject();
        result.addProperty("start", start); result.addProperty("expectation", expectation); result.addProperty("target", target);
        var actions = new JsonArray();
        for (var step : steps) {
            if (!List.of("advance", "choice", "objective").contains(step.action())) throw new IllegalArgumentException("Unknown test action");
            if (!step.action().equals("advance") && step.target().isBlank()) throw new IllegalArgumentException("Choose a test step target");
            var value = new JsonObject(); value.addProperty("action", step.action()); value.addProperty("target", step.target()); actions.add(value);
        }
        result.add("steps", actions);
        return result;
    }

    static List<Result> run(AuthoringDocument document, BiFunction<String, PreviewConditions, String> localization, Consumer<PreviewCoverage> coverage) {
        var metadata = document.metadata();
        var scenarios = EditorSchema.object(metadata.get("preview_scenarios"));
        var tests = EditorSchema.object(metadata.get("preview_scenario_tests"));
        var results = new ArrayList<Result>();
        for (var scenario : scenarios.entrySet()) {
            String name = scenario.getKey(), current = "";
            if (!tests.has(name)) { results.add(new Result(name, false, true, "Save an expected result to enable this test", "")); continue; }
            try {
                var snapshot = scenario.getValue().getAsJsonObject();
                for (String field : List.of("reputation", "tier", "profession", "variant", "check_success", "overrides", "checks", "quests", "placeholders"))
                    if (!snapshot.has(field)) throw new IllegalArgumentException("Saved conditions are missing " + field);
                var conditions = new PreviewConditions(); conditions.restore(snapshot);
                var test = tests.getAsJsonObject(name);
                var parsedSteps = readSteps(test);
                String start = text(test, "start"), expected = text(test, "expectation"), target = text(test, "target");
                config(start, expected, target, parsedSteps);
                if (!EXPECTATIONS.contains(expected)) throw new IllegalArgumentException("Unsupported expected result");
                var actions = test.getAsJsonArray("steps");
                if (actions.size() > 256) throw new IllegalArgumentException("Too many test steps");
                if (document.kind() == DefinitionKind.QUEST) {
                    var session = new QuestPreviewSession(document, conditions);
                    if (!start.isEmpty()) { session.drainCoverage(); session.startAt(start); }
                    current = session.currentStage(); coverage.accept(session.drainCoverage());
                    for (var action : actions) {
                        var step = action.getAsJsonObject();
                        String type = step.get("action").getAsString(), value = step.get("target").getAsString();
                        switch (type) {
                            case "advance" -> session.advance();
                            case "objective" -> {
                                String compiledId = session.objectives().contains(value) ? value : session.currentStage() + "." + value;
                                session.completed(compiledId, true);
                            }
                            case "choice" -> session.choose(session.transitions().stream()
                                    .filter(choice -> choice.id().equals(value) || DialogueGraph.string(choice.data(), "id").equals(value)).findFirst()
                                    .orElseThrow(() -> new IllegalArgumentException("Choice no longer exists: " + value)).id());
                            default -> throw new IllegalArgumentException("Unknown test action: " + type);
                        }
                        current = session.currentStage(); coverage.accept(session.drainCoverage());
                    }
                    boolean unavailable = session.transitions().stream().anyMatch(choice ->
                            (choice.id().equals(target) || DialogueGraph.string(choice.data(), "id").equals(target))
                                    && session.availability(choice) == com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome.UNMET);
                    boolean available = session.transitions().stream().anyMatch(choice ->
                            (choice.id().equals(target) || DialogueGraph.string(choice.data(), "id").equals(target))
                                    && session.availability(choice) == com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome.MATCHED);
                    boolean passed = switch (expected) {
                        case "Node or stage" -> current.equals(target) && session.outcome().isEmpty();
                        case "Quest outcome" -> session.outcome().equals(target);
                        case "Choice available" -> available;
                        case "Choice unavailable" -> unavailable;
                        default -> throw new IllegalArgumentException("Choose a quest expectation");
                    };
                    results.add(result(name, passed, expected, target, current, session.outcome()));
                } else {
                    try (var session = new DialoguePreviewSession(document.json(), ResourceLocation.parse(document.id()), conditions, key -> localization.apply(key, conditions))) {
                        var visited = new java.util.HashSet<String>();
                        session.start(start); current = session.nodeId();
                        var initial = session.drainCoverage(); visited.addAll(initial.nodes()); coverage.accept(initial);
                        for (var action : actions) {
                            var step = action.getAsJsonObject();
                            String type = step.get("action").getAsString(), value = step.get("target").getAsString();
                            if (session.closed()) throw new IllegalStateException("Dialogue ended before the next test step");
                            if (type.equals("advance")) {
                                if (!session.hasNextLine()) throw new IllegalStateException("Choose a response before advancing");
                                session.advance();
                            } else if (type.equals("choice")) {
                                var choices = session.choices();
                                int index = -1;
                                for (int i = 0; i < choices.size(); i++) if (choices.get(i).id().equals(value) || value.equals(choices.get(i).pointer())) { index = i; break; }
                                if (index < 0) throw new IllegalArgumentException("Choice no longer exists here: " + value);
                                session.choose(index);
                            } else throw new IllegalArgumentException("Choose a dialogue test action");
                            current = session.nodeId();
                            var batch = session.drainCoverage(); visited.addAll(batch.nodes()); coverage.accept(batch);
                        }
                        var authored = authoredChoice(document, current, target);
                        boolean unavailable = authored != null && conditions.evaluate(ResourceLocation.parse(document.id()), authored)
                                == com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome.UNMET;
                        String nodeId = current;
                        var nodePath = DialogueGraph.nodes(document).stream().filter(node -> node.id().equals(nodeId) || nodeId.equals("/") && node.pointer().isEmpty())
                                .map(DialogueGraph.Node::pointer).findFirst();
                        boolean reached = nodePath.isPresent() && visited.contains(nodePath.get());
                        boolean ended = reached && !session.hasNextLine() && session.choices().isEmpty();
                        boolean available = session.choices().stream().anyMatch(choice -> (choice.id().equals(target) || target.equals(choice.pointer())) && choice.available());
                        boolean disabled = session.choices().stream().anyMatch(choice -> (choice.id().equals(target) || target.equals(choice.pointer()))
                                && !choice.available() && choice.reason().equals("UNMET"));
                        boolean passed = switch (expected) {
                            case "Node or stage" -> reached && current.equals(target);
                            case "Dialogue ended" -> ended;
                            case "Choice available" -> available;
                            case "Choice unavailable" -> disabled || reached && unavailable;
                            default -> throw new IllegalArgumentException("Choose a dialogue expectation");
                        };
                        results.add(result(name, passed, expected, target, current, ended ? "ended" : "open"));
                    }
                }
            } catch (IllegalArgumentException | IllegalStateException | IndexOutOfBoundsException | ClassCastException | UnsupportedOperationException exception) {
                results.add(new Result(name, false, false, exception.getMessage() == null ? "Invalid saved test data" : exception.getMessage(), current));
            }
        }
        return List.copyOf(results);
    }

    private static JsonObject authoredChoice(AuthoringDocument document, String current, String target) {
        var nodes = DialogueGraph.nodes(document);
        var node = nodes.stream().filter(value -> value.id().equals(current) || current.equals("/") && value.pointer().isEmpty()).findFirst();
        if (node.isEmpty()) return null;
        var owner = JsonPointers.get(document.json(), node.get().pointer()).getAsJsonObject();
        for (String field : List.of("responses", "options")) {
            var choices = DialogueGraph.array(owner, field);
            for (int i = 0; i < choices.size(); i++) {
                var choice = choices.get(i).getAsJsonObject();
                String id = DialogueGraph.string(choice, "id");
                if (id.isBlank()) id = String.valueOf(i);
                if (id.equals(target) || (node.get().pointer() + "/" + field + "/" + i).equals(target)) return choice;
            }
        }
        return null;
    }

    private static Result result(String name, boolean passed, String expected, String target, String node, String outcome) {
        return new Result(name, passed, false, (passed ? "Matched " : "Expected ") + expected.toLowerCase(java.util.Locale.ROOT)
                + (target.isEmpty() ? "" : ": " + target) + (passed ? "" : ". Actual: " + node + (outcome.isEmpty() ? "" : " (" + outcome + ")")), node);
    }

    private void load(AuthoringDocument document, String name) {
        selected = name; steps.clear(); start = ""; expectation = "Node or stage"; target.set(""); loadError = "";
        var tests = EditorSchema.object(document.metadata().get("preview_scenario_tests"));
        loadedConfig = tests.has(name) ? tests.get(name).deepCopy() : null;
        if (!tests.has(name)) return;
        try {
            var test = tests.getAsJsonObject(name);
            var parsed = readSteps(test);
            config(text(test, "start"), text(test, "expectation"), text(test, "target"), parsed);
            start = text(test, "start"); expectation = text(test, "expectation"); target.set(text(test, "target")); steps.addAll(parsed);
            loadError = "";
        } catch (IllegalArgumentException | IllegalStateException | ClassCastException exception) {
            loadError = "Repair this saved test by saving new steps and an expected result: " + exception.getMessage();
        }
    }

    private static String text(JsonObject value, String field) {
        if (value == null) throw new IllegalArgumentException("Missing saved test");
        var entry = value.get(field);
        if (entry == null || !entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Missing text field " + field);
        return entry.getAsString();
    }

    private static List<Step> readSteps(JsonObject value) {
        if (value == null || !value.has("steps") || !value.get("steps").isJsonArray()) throw new IllegalArgumentException("Missing test steps");
        return value.getAsJsonArray("steps").asList().stream().map(entry -> {
            if (!entry.isJsonObject()) throw new IllegalArgumentException("Invalid test step");
            return new Step(text(entry.getAsJsonObject(), "action"), text(entry.getAsJsonObject(), "target"));
        }).toList();
    }

    void render(AuthoringDocument document, Consumer<ImGuiAuthoringEditor.EditorOperation> run, BiFunction<String, PreviewConditions, String> localization,
                Consumer<PreviewCoverage> coverage, Consumer<String> navigate) {
        if (owner != document) { owner = document; selected = ""; results = List.of(); }
        var scenarios = EditorSchema.object(document.metadata().get("preview_scenarios"));
        if (scenarios.isEmpty()) return;
        if (!scenarios.has(selected)) load(document, scenarios.keySet().iterator().next());
        var saved = EditorSchema.object(document.metadata().get("preview_scenario_tests")).get(selected);
        if (!java.util.Objects.equals(saved, loadedConfig)) load(document, selected);
        if (!loadError.isEmpty()) ImGui.textWrapped(loadError);
        if (ImGui.beginCombo("Scenario to test", selected)) {
            for (String name : scenarios.keySet()) if (ImGui.selectable(name, name.equals(selected))) load(document, name);
            ImGui.endCombo();
        }
        if (ImGui.beginCombo("Start at", start.isEmpty() ? document.kind() == DefinitionKind.QUEST ? "Entry stage" : "Entry choices" : start)) {
            if (ImGui.selectable(document.kind() == DefinitionKind.QUEST ? "Entry stage" : "Entry choices", start.isEmpty())) start = "";
            for (var node : DialogueGraph.nodes(document)) {
                String id = node.id().isEmpty() ? "/" : node.id();
                if (ImGui.selectable(id, id.equals(start))) start = id;
            }
            ImGui.endCombo();
        }
        if (ImGui.beginCombo("Add test step", "Choose an action...")) {
            if (ImGui.selectable(document.kind() == DefinitionKind.QUEST ? "Advance stage" : "Next exchange")) steps.add(new Step("advance", ""));
            for (String field : List.of("entries", "topics")) {
                int index = 0;
                for (var child : DialogueGraph.array(document.json(), field)) {
                    var value = child.getAsJsonObject();
                    String id = document.kind() == DefinitionKind.FORCED ? "/" + field + "/" + index : DialogueGraph.string(value, "id");
                    if (ImGui.selectable("Choose entry: " + id)) steps.add(new Step("choice", id));
                    index++;
                }
            }
            for (var node : DialogueGraph.nodes(document)) {
                var value = JsonPointers.get(document.json(), node.pointer()).getAsJsonObject();
                for (String field : List.of("responses", "options", "events", "objectives")) {
                    int index = 0;
                    for (var child : DialogueGraph.array(value, field)) {
                        String id = DialogueGraph.string(child.getAsJsonObject(), "id");
                        if (id.isBlank()) id = document.kind() == DefinitionKind.QUEST ? field + "/" + index : String.valueOf(index);
                        if (ImGui.selectable(node.id() + ": " + (field.equals("objectives") ? "Complete " : "Choose ") + id))
                            steps.add(new Step(field.equals("objectives") ? "objective" : "choice", id));
                        index++;
                    }
                }
                if (value.has("next") && ImGui.selectable(node.id() + ": Continue")) steps.add(new Step("choice", "continue"));
                if (value.has("leave_option") && ImGui.selectable(node.id() + ": Leave")) steps.add(new Step("choice", "leave"));
            }
            ImGui.endCombo();
        }
        for (int i = 0; i < steps.size(); i++) {
            ImGui.pushID(i); var step = steps.get(i);
            ImGui.textWrapped((i + 1) + ". " + (step.action().equals("advance") ? "Advance" : step.action().equals("objective") ? "Complete objective: " + step.target() : "Choose: " + step.target()));
            ImGui.beginDisabled(i == 0);
            if (ImGui.smallButton("Up")) java.util.Collections.swap(steps, i, i - 1);
            ImGui.endDisabled(); ImGui.sameLine(); ImGui.beginDisabled(i == steps.size() - 1);
            if (ImGui.smallButton("Down")) java.util.Collections.swap(steps, i, i + 1);
            ImGui.endDisabled(); ImGui.sameLine();
            boolean remove = ImGui.smallButton("Remove step"); ImGui.popID();
            if (remove) { steps.remove(i); break; }
        }
        if (ImGui.beginCombo("Expected result", expectation)) {
            for (String value : EXPECTATIONS) if (document.kind() == DefinitionKind.QUEST ? !value.equals("Dialogue ended") : !value.equals("Quest outcome"))
                if (ImGui.selectable(value, value.equals(expectation))) { expectation = value; target.set(""); }
            ImGui.endCombo();
        }
        if (expectation.equals("Node or stage")) {
            if (ImGui.beginCombo("Expected node / stage", target.get().isEmpty() ? "Choose..." : target.get())) {
                for (var node : DialogueGraph.nodes(document)) {
                    String id = node.id().isEmpty() ? "/" : node.id();
                    if (ImGui.selectable(id, id.equals(target.get()))) target.set(id);
                }
                ImGui.endCombo();
            }
        } else if (expectation.equals("Quest outcome")) {
            if (ImGui.beginCombo("Expected outcome", target.get().isEmpty() ? "Choose..." : target.get())) {
                for (String value : List.of("complete", "fail", "abandon")) if (ImGui.selectable(value, value.equals(target.get()))) target.set(value);
                ImGui.endCombo();
            }
        } else if (!expectation.equals("Dialogue ended")) ImGui.inputText("Expected choice ID", target);
        if (ImGui.button("Save test steps and expectation")) run.accept(() -> {
            var config = config(start, expectation, target.get(), List.copyOf(steps));
            String name = selected;
            loadError = "";
            document.editMetadata("Save scenario test", metadata -> {
                if (!metadata.has("preview_scenario_tests")) metadata.add("preview_scenario_tests", new JsonObject());
                metadata.getAsJsonObject("preview_scenario_tests").add(name, config);
            });
        });
        ImGui.textWrapped("Save test edits before running. Runs use isolated simulations. Objective completion is simulated. Actions and rewards are logged.");
        if (ImGui.button("Run all saved scenarios")) run.accept(() -> {
            results = run(document, localization, coverage); testedJson = document.json(); testedMetadata = document.metadata();
        });
        if (results.isEmpty()) return;
        if (!document.json().equals(testedJson) || !document.metadata().equals(testedMetadata)) ImGui.textWrapped("Tests or content changed. Run again for current results.");
        long passed = results.stream().filter(Result::passed).count(), skipped = results.stream().filter(Result::skipped).count();
        ImGui.textWrapped(passed + " passed, " + (results.size() - passed - skipped) + " failed, " + skipped + " without expectations");
        for (var result : results) {
            ImGui.textWrapped((result.skipped() ? "SKIP " : result.passed() ? "PASS " : "FAIL ") + result.name() + ": " + result.message());
            if (ImGui.smallButton((result.node().isEmpty() ? "Open file settings" : "Open " + result.node()) + "##" + result.name())) navigate.accept(result.node());
        }
    }
}
