package com.jvn.villagerretaliation.authoring.preview;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.QuestV2Compiler;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import com.jvn.villagerretaliation.quest.schema.v3.QuestV3Format;
import com.jvn.villagerretaliation.quest.tracking.QuestStageReadiness;
import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public final class QuestPreviewSession {
    private final CompiledQuest compiled;
    private final JsonObject source;
    private final PreviewConditions conditions;
    private final Set<String> completed = new HashSet<>();
    private final List<String> log = new ArrayList<>();
    private final Set<String> visitedNodes = new HashSet<>();
    private final Set<String> chosenPaths = new HashSet<>();
    private String current;
    private String outcome = "";

    public QuestPreviewSession(AuthoringDocument document, PreviewConditions conditions) {
        if (document.kind() != DefinitionKind.QUEST) throw new IllegalArgumentException("Choose a quest");
        this.source = document.json();
        this.conditions = conditions;
        JsonObject runtime = QuestV3Format.runtime(source);
        ResourceLocation id = ResourceLocation.parse(document.id());
        var parsed = QuestV2Parser.parse(id, runtime).orElseThrow(() -> new IllegalArgumentException("Repair the quest before testing"));
        var envelope = QuestResourceEnvelope.read(id, runtime).orElseThrow(() -> new IllegalArgumentException("Unsupported quest format"));
        this.compiled = QuestV2Compiler.compile(parsed, envelope).orElseThrow(() -> new IllegalArgumentException("Quest compilation failed"));
        reset();
    }

    public void reset() { startAt(compiled.entryStage()); }

    public void startAt(String stage) {
        if (!compiled.stagesById().containsKey(stage)) throw new IllegalArgumentException("Missing start stage " + stage);
        completed.clear(); log.clear(); outcome = ""; current = stage;
        visitedNodes.add(stagePath());
        log.add("Started isolated quest simulation");
        log.add("Entry effects: " + stage().entryActions());
    }

    private String stagePath() {
        var stages = source.getAsJsonArray("stages");
        for (int i = 0; i < stages.size(); i++) if (current.equals(DialogueGraph.string(stages.get(i).getAsJsonObject(), "id"))) return "/stages/" + i;
        throw new IllegalStateException("Missing authored stage " + current);
    }

    public PreviewCoverage drainCoverage() {
        var result = new PreviewCoverage(visitedNodes, chosenPaths); visitedNodes.clear(); chosenPaths.clear(); return result;
    }

    public String currentStage() { return current; }
    public String outcome() { return outcome; }
    public List<String> log() { return List.copyOf(log); }
    public QuestDefinition.Stage stage() { return compiled.stagesById().get(current).definition(); }
    public List<String> objectives() { return stage().objectives(); }
    public boolean completed(String id) { return completed.contains(id); }
    public void completed(String id, boolean value) {
        if (!objectives().contains(id)) throw new IllegalArgumentException("Unknown objective " + id);
        if (value) completed.add(id); else completed.remove(id);
    }

    public boolean ready() {
        if (!outcome.isEmpty()) return false;
        var stage = stage();
        if (stage.completeWhen().isEmpty()) return stage.objectives().isEmpty();
        return QuestStageReadiness.compositionMet(compiled.asQuestDefinition(), stage.completeWhen(), stage.completionMode(), stage.completionCount(),
                objective -> completed.contains(objective.id()), values -> conditions.evaluate(values) == ConditionOutcome.MATCHED);
    }

    public List<String> blockers() {
        List<String> reasons = new ArrayList<>();
        if (!outcome.isEmpty()) return List.of("Simulation ended: " + outcome);
        for (var predicate : stage().completeWhen()) {
            if (!predicate.objective().isBlank() && !completed.contains(predicate.objective())) reasons.add("Objective incomplete: " + predicate.objective());
            var evaluated = conditions.evaluate(predicate.conditions());
            if (evaluated != ConditionOutcome.MATCHED) reasons.add("Condition " + evaluated.name().toLowerCase() + ": " + predicate.conditions());
        }
        if (!ready() && reasons.isEmpty()) reasons.add("Completion requirements are unmet");
        return reasons;
    }

    public record Transition(String id, String label, JsonObject data) { }

    public List<Transition> transitions() {
        JsonObject authored = source.getAsJsonArray("stages").asList().stream().map(value -> value.getAsJsonObject())
                .filter(value -> current.equals(DialogueGraph.string(value, "id"))).findFirst().orElseThrow();
        List<Transition> choices = new ArrayList<>();
        if (!outcome.isEmpty()) return choices;
        for (String field : List.of("responses", "events")) {
            int index = 0;
            for (var child : DialogueGraph.array(authored, field)) {
                if (child.isJsonObject() && child.getAsJsonObject().has("transition")) {
                    JsonObject value = child.getAsJsonObject();
                    String id = field + "/" + index;
                    String name = DialogueGraph.string(value, "id");
                    choices.add(new Transition(id, field.equals("events") ? "Trigger event: " + name : "Choose response: " + name, value.deepCopy()));
                }
                index++;
            }
        }
        return List.copyOf(choices);
    }

    public ConditionOutcome availability(Transition choice) { return conditions.evaluate(compiled.id(), choice.data()); }

    public void choose(String id) {
        var choice = transitions().stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
        if (availability(choice) != ConditionOutcome.MATCHED) throw new IllegalStateException("Transition conditions are unmet or need simulated context");
        log.add(choice.label());
        if (choice.data().has("actions")) log.add("Transition effects: " + choice.data().get("actions"));
        String path = stagePath() + "/" + id;
        applyTransition(choice.data().getAsJsonObject("transition"));
        chosenPaths.add(path);
    }

    public void advance() {
        if (!ready()) throw new IllegalStateException("Complete the stage requirements first");
        JsonObject authored = source.getAsJsonArray("stages").asList().stream().map(value -> value.getAsJsonObject())
                .filter(value -> current.equals(DialogueGraph.string(value, "id"))).findFirst().orElseThrow();
        if (!authored.has("transition")) throw new IllegalStateException("This stage needs an event or response transition. Add one in the editor.");
        String path = stagePath() + "/transition";
        applyTransition(authored.getAsJsonObject("transition"));
        chosenPaths.add(path);
    }

    private void applyTransition(JsonObject transition) {
        log.add("Exit effects: " + stage().exitActions());
        String next = DialogueGraph.string(transition, "stage");
        if (!next.isBlank()) {
            if (!compiled.stagesById().containsKey(next)) throw new IllegalStateException("Missing destination stage " + next);
            current = next;
            visitedNodes.add(stagePath());
            log.add("Entered " + current);
            log.add("Entry effects: " + stage().entryActions());
            return;
        }
        for (String terminal : List.of("complete", "fail", "abandon")) if (transition.has(terminal) && transition.get(terminal).getAsBoolean()) {
            outcome = terminal;
            log.add("Quest " + terminal);
            if (terminal.equals("complete")) log.add("Reward effects: " + compiled.rewards());
            return;
        }
        throw new IllegalStateException("No stage or terminal outcome configured");
    }
}
