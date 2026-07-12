package com.jvn.villagerretaliation.scene.runtime;

import com.jvn.villagerretaliation.scene.model.CompiledScene;

public final class SceneDefinitionReconciler {
    private SceneDefinitionReconciler() { }

    public static Result reconcile(SceneInstance instance, CompiledScene updated) {
        if (instance.definitionHash().equals(updated.definitionHash())) return new Result(true, "definition unchanged");
        var current = updated.steps().get(instance.currentStep());
        if (current == null) return new Result(false, "current stable step id no longer exists: " + instance.currentStep());
        for (SceneStepRecord record : instance.stepRecords().values()) {
            var step = updated.steps().get(record.stepId());
            if (step == null) return new Result(false, "executed stable step id no longer exists: " + record.stepId());
            if (!step.type().equals(record.stepType())) return new Result(false, "step type changed for executed id " + record.stepId());
        }
        instance.reconcileDefinition(updated);
        return new Result(true, "definition reconciled by stable step ids");
    }
    public record Result(boolean safe, String diagnostic) { }
}
