package com.jvn.villagerretaliation.scene.runtime;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** Pure lifecycle boundary; world operations never occur here. */
public final class SceneStateMachine {
    private static final java.util.Map<SceneState, Set<SceneState>> ALLOWED = allowed();
    private SceneStateMachine() { }

    public static Transition transition(SceneState current, SceneState requested) {
        if (current == null || requested == null) return new Transition(false, SceneState.BLOCKED, "scene state is missing");
        if (current == requested) return new Transition(true, current, "state already applied");
        if (!ALLOWED.getOrDefault(current, Set.of()).contains(requested)) {
            return new Transition(false, current, "illegal scene transition " + current + " -> " + requested);
        }
        return new Transition(true, requested, "scene transitioned");
    }

    private static java.util.Map<SceneState, Set<SceneState>> allowed() {
        var map = new EnumMap<SceneState, Set<SceneState>>(SceneState.class);
        map.put(SceneState.PENDING, Set.of(SceneState.RUNNING, SceneState.BLOCKED, SceneState.CANCELLED));
        map.put(SceneState.RUNNING, Set.of(SceneState.WAITING, SceneState.BLOCKED, SceneState.COMPLETED, SceneState.FAILED, SceneState.CLEANING_UP, SceneState.CANCELLED));
        map.put(SceneState.WAITING, Set.of(SceneState.RUNNING, SceneState.BLOCKED, SceneState.FAILED, SceneState.CANCELLED));
        map.put(SceneState.BLOCKED, Set.of(SceneState.RUNNING, SceneState.FAILED, SceneState.CANCELLED, SceneState.CLEANING_UP));
        map.put(SceneState.CLEANING_UP, Set.of(SceneState.COMPLETED, SceneState.FAILED, SceneState.CANCELLED, SceneState.BLOCKED));
        map.put(SceneState.COMPLETED, Set.of()); map.put(SceneState.FAILED, Set.of(SceneState.CLEANING_UP));
        map.put(SceneState.CANCELLED, Set.of(SceneState.CLEANING_UP)); return Map.copyOf(map);
    }
    public record Transition(boolean accepted, SceneState state, String diagnostic) { }
}
