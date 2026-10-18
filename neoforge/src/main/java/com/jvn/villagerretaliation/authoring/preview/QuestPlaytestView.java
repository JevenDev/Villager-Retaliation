package com.jvn.villagerretaliation.authoring.preview;

import java.util.List;

public record QuestPlaytestView(String stage, String state, int provider, boolean hold, boolean ready,
        String blocker, List<Objective> objectives, List<String> stages, List<Transition> transitions, List<String> log, java.util.Map<String, String> replacements) {
    public QuestPlaytestView {
        replacements = java.util.Map.copyOf(replacements);
        objectives = List.copyOf(objectives);
        stages = List.copyOf(stages);
        transitions = List.copyOf(transitions);
        log = List.copyOf(log);
    }
    public static QuestPlaytestView empty() { return new QuestPlaytestView("", "not started", -1, true, false, "Start a live quest first", List.of(), List.of(), List.of(), List.of(), java.util.Map.of()); }
    public record Transition(String id, String label, boolean available, String reason) { }
    public record Objective(String id, String type, boolean complete, float progress, String reason, String setup) { }
}
