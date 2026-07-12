package com.jvn.villagerretaliation.scene.runtime;

import com.jvn.villagerretaliation.scene.model.CompiledScene;
import com.jvn.villagerretaliation.scene.model.SceneResource;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;

/** Applies authored scene outcomes through one persisted, idempotent policy boundary. */
public final class SceneTransitionService {
    private SceneTransitionService() {
    }

    public static SceneScheduler.ProcessResult fail(
            SceneSavedData data,
            SceneInstance instance,
            CompiledScene definition,
            String code,
            String diagnostic,
            long gameTime) {
        return apply(data, instance, definition, SceneInstance.TransitionIntent.FAILURE,
                code, diagnostic, gameTime);
    }

    public static SceneScheduler.ProcessResult cancel(
            SceneSavedData data,
            SceneInstance instance,
            CompiledScene definition,
            String code,
            String diagnostic,
            long gameTime) {
        return apply(data, instance, definition, SceneInstance.TransitionIntent.CANCELLATION,
                code, diagnostic, gameTime);
    }

    public static SceneScheduler.ProcessResult apply(
            SceneSavedData data,
            SceneInstance instance,
            CompiledScene definition,
            SceneInstance.TransitionIntent intent,
            String code,
            String diagnostic,
            long gameTime) {
        instance.preparePolicyTransition(intent, code, diagnostic);
        data.changed();
        return applyPrepared(data, instance, definition, gameTime);
    }

    public static SceneScheduler.ProcessResult applyPrepared(
            SceneSavedData data,
            SceneInstance instance,
            CompiledScene definition,
            long gameTime) {
        if (instance.transitionIntent() == SceneInstance.TransitionIntent.NONE
                || instance.policyApplied()) {
            return SceneScheduler.ProcessResult.idle();
        }
        if (definition == null) {
            instance.blockForRepair("transition_definition_missing",
                    "cannot apply the pending scene policy because its definition is unavailable", gameTime);
            instance.markPolicyApplied();
            data.changed();
            return SceneScheduler.ProcessResult.idle();
        }
        SceneResource.TransitionPolicy policy = instance.transitionIntent()
                == SceneInstance.TransitionIntent.CANCELLATION
                ? definition.cancellationPolicy()
                : definition.failurePolicy();
        String code = instance.transitionCode().isBlank() ? "scene_transition" : instance.transitionCode();
        String diagnostic = instance.transitionDiagnostic().isBlank()
                ? "scene transition policy applied" : instance.transitionDiagnostic();
        switch (policy) {
            case FAIL_SCENE -> terminalize(data, instance, SceneInstance.CompletionResult.FAILURE,
                    code, diagnostic, gameTime);
            case CANCEL_SCENE -> terminalize(data, instance, SceneInstance.CompletionResult.CANCELLED,
                    code, diagnostic, gameTime);
            case BLOCK_FOR_REPAIR -> instance.blockForRepair(code, diagnostic, gameTime);
            case RUN_FAILURE_STEP -> runFailureStep(instance, definition, code, diagnostic, gameTime);
        }
        instance.markPolicyApplied();
        if (policy == SceneResource.TransitionPolicy.RUN_FAILURE_STEP
                && instance.state() != SceneState.BLOCKED) {
            instance.finishPolicyTransitionForContinuation();
        }
        data.changed();
        return instance.state().terminal() || instance.state() == SceneState.BLOCKED
                ? SceneScheduler.ProcessResult.idle()
                : SceneScheduler.ProcessResult.now();
    }

    public static void complete(SceneSavedData data, SceneInstance instance, long gameTime) {
        if (instance.state().terminal()) return;
        terminalize(data, instance, SceneInstance.CompletionResult.SUCCESS,
                "", "scene completed", gameTime);
        data.changed();
    }

    private static void runFailureStep(
            SceneInstance instance,
            CompiledScene definition,
            String code,
            String diagnostic,
            long gameTime) {
        CompiledScene.CompiledStep current = definition.steps().get(instance.currentStep());
        String failureStep = current == null ? "" : current.failureStep();
        if (failureStep.isBlank() || !definition.steps().containsKey(failureStep)) {
            instance.blockForRepair(code + "_failure_step_missing",
                    diagnostic + "; the current step has no valid authored failure_step", gameTime);
            return;
        }
        instance.advance(failureStep, gameTime);
    }

    private static void terminalize(
            SceneSavedData data,
            SceneInstance instance,
            SceneInstance.CompletionResult result,
            String code,
            String diagnostic,
            long gameTime) {
        switch (result) {
            case SUCCESS -> instance.complete(gameTime);
            case FAILURE -> instance.fail(code, diagnostic, gameTime);
            case CANCELLED -> instance.cancel(code, diagnostic, gameTime);
            case NONE -> throw new IllegalArgumentException("terminal scene result cannot be NONE");
        }
        if (instance.cleanupStatus() != SceneInstance.CleanupStatus.COMPLETE) {
            instance.cleanupStatus(SceneInstance.CleanupStatus.RUNNING);
            data.requestCleanup(instance);
        }
    }
}
