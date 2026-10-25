package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.preview.QuestLiveTest;
import imgui.ImGui;
import imgui.type.ImString;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class EditorQuestLiveTests {
    private AuthoringDocument owner;
    private String selected = "", baseline = "", action = "start", check = "state";
    private final ImString name = new ImString("Quest completion", 128), target = new ImString(512), value = new ImString(512);
    private final ImString checkTarget = new ImString(512), expected = new ImString("completed", 512);
    private final List<QuestLiveTest.Step> steps = new ArrayList<>();
    private final List<QuestLiveTest.Expectation> checks = new ArrayList<>();
    private boolean manual = true;
    private com.google.gson.JsonElement loaded;
    private JsonObject testedContent, testedTests;
    private String error = "";

    private void load(AuthoringDocument document, String selected) {
        this.selected = selected; name.set(selected.isEmpty() ? "Quest completion" : selected);
        steps.clear(); checks.clear(); baseline = ""; manual = true; error = ""; loaded = null;
        var saved = EditorSchema.object(document.metadata().get("live_scenario_tests")).get(selected);
        if (saved == null) return;
        loaded = saved.deepCopy();
        try {
            if (!saved.isJsonObject()) throw new IllegalArgumentException("Saved live test must be an object");
            var config = QuestLiveTest.read(loaded.getAsJsonObject());
            baseline = config.baseline(); manual = config.manual(); steps.addAll(config.steps()); checks.addAll(config.expectations());
        } catch (IllegalArgumentException exception) { error = "Repair this saved live test before running: " + exception.getMessage(); }
    }

    void render(AuthoringWorkspace workspace, AuthoringDocument document, Consumer<ImGuiAuthoringEditor.EditorOperation> run) {
        if (owner != document) { owner = document; load(document, ""); testedContent = null; testedTests = null; }
        var saved = EditorSchema.object(document.metadata().get("live_scenario_tests"));
        var view = EditorLiveQuestTest.scenarioView();
        ImGui.textWrapped("Run saved steps against the real quest runtime. Choose a baseline saved in Test tools. Baselines are saved with this world and remain available after reopening it.");
        ImGui.textWrapped("Quest progress, inventory, experience, reputation, position and environment restore after the suite. Use a world checkpoint in Tests to also reset terrain, entities and other world changes.");
        if (!error.isEmpty()) ImGui.textWrapped(error);
        ImGui.beginDisabled(view.running());
        if (ImGui.beginCombo(EditorWidgets.field("Saved live test"), selected.isEmpty() ? "New test" : selected)) {
            for (String entry : saved.keySet()) if (ImGui.selectable(entry, selected.equals(entry))) load(document, entry);
            ImGui.endCombo();
        }
        if (ImGui.button("New live test")) load(document, "");
        if (!selected.isEmpty() && !java.util.Objects.equals(saved.get(selected), loaded)) load(document, saved.has(selected) ? selected : "");
        ImGui.inputText(EditorWidgets.field("Test name"), name);
        if (ImGui.beginCombo(EditorWidgets.field("Restore baseline"), baseline.isEmpty() ? "Choose..." : baseline)) {
            for (String entry : EditorLiveQuestTest.baselineNames()) if (ImGui.selectable(entry, baseline.equals(entry))) baseline = entry;
            ImGui.endCombo();
        }
        if (ImGui.checkbox("Step stages manually", manual)) manual = !manual;
        if (ImGui.beginCombo(EditorWidgets.field("Test action"), label(action))) {
            for (String entry : QuestLiveTest.ACTIONS) if (ImGui.selectable(label(entry), action.equals(entry))) {
                action = entry; target.set(""); value.set(entry.equals("wait") ? "20" : "");
            }
            ImGui.endCombo();
        }
        if (List.of("prepare", "travel", "criterion", "choice", "discovery_item", "discovery_travel").contains(action)) {
            ImGui.inputText(EditorWidgets.field(action.equals("choice") ? "Choice ID" : action.startsWith("discovery") ? "Discovery route ID" : "Objective ID"), target);
            var snapshot = EditorLiveQuestTest.snapshot();
            if (action.equals("choice") && ImGui.beginCombo(EditorWidgets.field("Current choices"), "Use a live choice...")) {
                for (var choice : snapshot.transitions()) if (ImGui.selectable(choice.label() + "##" + choice.id())) target.set(choice.id());
                ImGui.endCombo();
            } else if (!action.startsWith("discovery") && !action.equals("choice") && ImGui.beginCombo(EditorWidgets.field("Current objectives"), "Use a live objective...")) {
                for (var objective : snapshot.objectives()) if (ImGui.selectable(objective.id())) target.set(objective.id());
                ImGui.endCombo();
            }
        }
        ImGui.inputText(EditorWidgets.field(action.equals("wait") ? "Wait ticks" : action.equals("prepare") ? "Setup value (optional)" : "Expected action result (optional)"), value);
        if (ImGui.button("Add live step")) steps.add(new QuestLiveTest.Step(action, target.get(), value.get()));
        for (int i = 0; i < steps.size(); i++) {
            ImGui.pushID("step" + i);
            var step = steps.get(i);
            ImGui.textWrapped((i + 1) + ". " + label(step.action()) + (step.target().isEmpty() ? "" : ": " + step.target())
                    + (step.value().isEmpty() ? "" : " (" + step.value() + ")"));
            if (ImGui.treeNode("Edit step")) {
                if (ImGui.beginCombo(EditorWidgets.field("Action"), label(step.action()))) {
                    for (String option : QuestLiveTest.ACTIONS) if (ImGui.selectable(label(option), option.equals(step.action())))
                        steps.set(i, new QuestLiveTest.Step(option, step.target(), step.value()));
                    ImGui.endCombo();
                }
                var editedTarget = new ImString(steps.get(i).target(), 512);
                var editedValue = new ImString(steps.get(i).value(), 512);
                if (ImGui.inputText(EditorWidgets.field("Target"), editedTarget) | ImGui.inputText(EditorWidgets.field("Value"), editedValue))
                    steps.set(i, new QuestLiveTest.Step(steps.get(i).action(), editedTarget.get(), editedValue.get()));
                ImGui.treePop();
            }
            ImGui.beginDisabled(i == 0);
            if (ImGui.smallButton("Up")) java.util.Collections.swap(steps, i, i - 1);
            ImGui.endDisabled(); EditorWidgets.sameLineFor("Down"); ImGui.beginDisabled(i == steps.size() - 1);
            if (ImGui.smallButton("Down")) java.util.Collections.swap(steps, i, i + 1);
            ImGui.endDisabled(); EditorWidgets.sameLineFor("Remove");
            boolean remove = ImGui.smallButton("Remove");
            ImGui.popID();
            if (remove) { steps.remove(i); break; }
        }
        ImGui.separatorText("Expected results");
        if (ImGui.beginCombo(EditorWidgets.field("Expectation"), label(check))) {
            for (String entry : QuestLiveTest.CHECKS) if (ImGui.selectable(label(entry), check.equals(entry))) { check = entry; checkTarget.set(""); expected.set(""); }
            ImGui.endCombo();
        }
        if (List.of("item_count", "objective_complete").contains(check)) ImGui.inputText(EditorWidgets.field(check.equals("item_count") ? "Item ID" : "Objective ID"), checkTarget);
        ImGui.inputText(EditorWidgets.field("Expected value"), expected);
        if (ImGui.button("Add expectation")) checks.add(new QuestLiveTest.Expectation(check, checkTarget.get(), expected.get()));
        for (int i = 0; i < checks.size(); i++) {
            var entry = checks.get(i);
            ImGui.textWrapped(label(entry.kind()) + (entry.target().isEmpty() ? "" : ": " + entry.target()) + " = " + entry.expected());
            ImGui.pushID("expectation" + i);
            if (ImGui.treeNode("Edit expectation")) {
                if (ImGui.beginCombo(EditorWidgets.field("Check"), label(entry.kind()))) {
                    for (String option : QuestLiveTest.CHECKS) if (ImGui.selectable(label(option), option.equals(entry.kind())))
                        checks.set(i, new QuestLiveTest.Expectation(option, entry.target(), entry.expected()));
                    ImGui.endCombo();
                }
                var editedTarget = new ImString(checks.get(i).target(), 512);
                var editedExpected = new ImString(checks.get(i).expected(), 512);
                if (ImGui.inputText(EditorWidgets.field("Target"), editedTarget) | ImGui.inputText(EditorWidgets.field("Expected"), editedExpected))
                    checks.set(i, new QuestLiveTest.Expectation(checks.get(i).kind(), editedTarget.get(), editedExpected.get()));
                ImGui.treePop();
            }
            ImGui.popID();
            if (ImGui.smallButton("Remove expectation##" + i)) { checks.remove(i); break; }
        }
        if (ImGui.button("Save live test")) run.accept(() -> {
            if (name.get().isBlank() || name.get().length() > 128) throw new IllegalArgumentException("Enter a test name of 1 to 128 characters");
            var config = new QuestLiveTest(baseline, manual, steps, checks).json();
            String testName = name.get();
            document.editMetadata("Save live quest test", metadata -> {
                if (!metadata.has("live_scenario_tests")) metadata.add("live_scenario_tests", new JsonObject());
                metadata.getAsJsonObject("live_scenario_tests").add(testName, config);
            });
            load(document, testName);
        });
        if (!selected.isEmpty() && ImGui.button("Delete saved live test")) run.accept(() -> {
            String testName = selected;
            document.editMetadata("Delete live quest test", metadata -> metadata.getAsJsonObject("live_scenario_tests").remove(testName));
            load(document, "");
        });
        ImGui.textWrapped("Save changes before running. Experience and completion expectations are deltas from each test's restored baseline. Use base for the default reward tier.");
        ImGui.beginDisabled(saved.isEmpty());
        if (ImGui.button("Run all saved live tests")) run.accept(() -> {
            EditorLiveQuestTest.runScenarios(workspace, document, saved);
            testedContent = document.json(); testedTests = saved.deepCopy();
        });
        ImGui.endDisabled();
        ImGui.endDisabled();
        if (view.running() && ImGui.button("Stop live tests and restore")) EditorLiveQuestTest.cancelScenarios();
        ImGui.textWrapped(view.message());
        if (view.running()) ImGui.textWrapped(view.name() + ": step " + view.step() + " / " + view.steps());
        if (testedContent != null && (!testedContent.equals(document.json()) || !java.util.Objects.equals(testedTests, saved)))
            ImGui.textWrapped("Quest content or saved tests changed. Run again for current results.");
        if (!view.results().isEmpty()) {
            long passed = view.results().stream().filter(com.jvn.villagerretaliation.authoring.preview.QuestPlaytestScenarios.Result::passed).count();
            ImGui.textWrapped(passed + " passed, " + (view.results().size() - passed) + " failed");
            for (var result : view.results()) if (EditorWidgets.section((result.passed() ? "PASS " : "FAIL ") + result.name()))
                for (String detail : result.details()) ImGui.textWrapped(detail);
        }
    }

    private static String label(String id) {
        return switch (id) {
            case "prepare" -> "Prepare objective through setup";
            case "criterion" -> "Inject criterion event";
            case "next" -> "Advance a ready stage";
            case "experience_delta" -> "Experience gained";
            case "completion_delta" -> "Completions added";
            case "start_delta" -> "Starts added";
            default -> Character.toUpperCase(id.charAt(0)) + id.substring(1).replace('_', ' ');
        };
    }
}
