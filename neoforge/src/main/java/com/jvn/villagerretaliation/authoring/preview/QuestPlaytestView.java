package com.jvn.villagerretaliation.authoring.preview;

import java.util.List;

public record QuestPlaytestView(String stage, String state, int provider, boolean hold, boolean ready, boolean structure, boolean target,
        String blocker, List<Objective> objectives, List<String> stages, List<Transition> transitions, List<String> log, java.util.Map<String, String> replacements, boolean playerOnly, List<Discovery> discoveries) {
    public QuestPlaytestView {
        replacements = java.util.Map.copyOf(replacements);
        discoveries = List.copyOf(discoveries);
        objectives = List.copyOf(objectives);
        stages = List.copyOf(stages);
        transitions = List.copyOf(transitions);
        log = List.copyOf(log);
    }
    public static QuestPlaytestView empty() { return notStarted(false, List.of()); }
    public static QuestPlaytestView notStarted(boolean playerOnly, List<Discovery> discoveries) {
        return new QuestPlaytestView("", "not started", -1, false, false, false, false,
                playerOnly ? "Prepare a discovery test first" : "Prepare a quest offer first",
                List.of(), List.of(), List.of(), List.of(), java.util.Map.of(), playerOnly, discoveries);
    }
    public record Discovery(String id, String event, String match, boolean item, boolean location) { }
    public record Transition(String id, String label, boolean available, String reason) { }
    public record Objective(String id, String type, boolean complete, float progress, String reason, String setup, String guidance, boolean area, boolean bypassed) { }
}
